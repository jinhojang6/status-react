nix = load('ci/nix.groovy')
utils = load('ci/utils.groovy')

def plutil(name, value) {
  return """
  plutil -replace ${name} -string ${value} ios/StatusIm/Info.plist;
"""
}

def bundle() {
  def btype = utils.getBuildType()
  def target
  switch (btype) {
    case 'release':     target = 'release'; break;
    case 'testflight':  target = 'release'; break;
    case 'e2e':         target = 'e2e';     break;
    default:            target = 'nightly';
  }
  /* configure build metadata */
  nix.shell(plutil('CFBundleShortVersionString', utils.getVersion()), attr: 'shells.ios')
  nix.shell(plutil('CFBundleVersion', utils.genBuildNumber()), attr: 'shells.ios')
  nix.shell(plutil('CFBundleBuildUrl', currentBuild.absoluteUrl), attr: 'shells.ios')
  /* the dir might not exist */
  sh 'mkdir -p status-e2e'
  /* build the actual app */
  withCredentials([
    string(credentialsId: "slave-pass-${env.NODE_NAME}", variable: 'KEYCHAIN_PASSWORD'),
    string(credentialsId: 'fastlane-match-password', variable: 'MATCH_PASSWORD'),
    usernamePassword(
      credentialsId:  'fastlane-match-apple-id',
      usernameVariable: 'FASTLANE_APPLE_ID',
      passwordVariable: 'FASTLANE_PASSWORD'
    ),
  ]) {
    nix.shell(
      "bundle exec --gemfile=fastlane/Gemfile fastlane ios ${target}",
      keep: [
        'FASTLANE_DISABLE_COLORS',
        'FASTLANE_PASSWORD', 'KEYCHAIN_PASSWORD',
        'MATCH_PASSWORD', 'FASTLANE_APPLE_ID',
      ],
      attr: 'shells.ios'
    )
  }
  /* rename built file for uploads and archivization */
  def pkg = ''
  if (btype == 'release') {
    pkg = utils.pkgFilename('release', 'ipa')
    sh "cp status_appstore/StatusIm.ipa ${pkg}"
  } else if (btype == 'e2e') {
    pkg = utils.pkgFilename('e2e', 'app.zip')
    sh "cp status-e2e/StatusIm.app.zip ${pkg}"
  } else if (btype != 'testflight') {
    pkg = utils.pkgFilename(btype, 'ipa')
    sh "cp status-adhoc/StatusIm.ipa ${pkg}"
  }
  /* necessary for Diawi upload */
  env.DIAWI_IPA = pkg
  return pkg
}

def uploadToDiawi() {
  withCredentials([
    string(credentialsId: 'diawi-token', variable: 'DIAWI_TOKEN'),
  ]) {
    /* This can silently fail with 'File is not processed.' */
    nix.shell(
      'bundle exec --verbose --gemfile=fastlane/Gemfile fastlane ios upload_diawi',
      keep: ['FASTLANE_DISABLE_COLORS', 'DIAWI_TOKEN'],
      attr: 'shells.fastlane'
    )
  }
  diawiUrl = readFile "${env.WORKSPACE}/fastlane/diawi.out"
  /* Save the URL in the build description */
  currentBuild.description = "<a href=\"${diawiUrl}\">Diawi Link</a>"
  return diawiUrl
}

def uploadToSauceLabs() {
  def changeId = utils.getParentRunEnv('CHANGE_ID')
  if (changeId != null) {
    env.SAUCE_LABS_NAME = "${changeId}.app.zip"
  } else {
    env.SAUCE_LABS_NAME = "im.status.ethereum-e2e-${utils.gitCommit()}.app.zip"
  }
  withCredentials([
    usernamePassword(
      credentialsId:  'sauce-labs-api',
      usernameVariable: 'SAUCE_USERNAME',
      passwordVariable: 'SAUCE_ACCESS_KEY'
    ),
  ]) {
    nix.shell(
      'bundle exec --gemfile=fastlane/Gemfile fastlane ios saucelabs',
      keep: ['FASTLANE_DISABLE_COLORS', 'SAUCE_ACCESS_KEY', 'SAUCE_USERNAME'],
      attr: 'shells.fastlane'
    )
  }
  return env.SAUCE_LABS_NAME
}

return this
