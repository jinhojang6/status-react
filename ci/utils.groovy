def getVersion() {
  def path = "${env.WORKSPACE}/VERSION"
  return readFile(path).trim()
}

def branchName() {
  return env.GIT_BRANCH.replaceAll(/.*origin\//, '')
}

def parentOrCurrentBuild() {
  def c = currentBuild.rawBuild.getCause(hudson.model.Cause$UpstreamCause)
  if (c == null) { return currentBuild }
  return c.getUpstreamRun()
}

def timestamp() {
  /* we use parent if available to make timestmaps consistent */
  def now = new Date(parentOrCurrentBuild().timeInMillis)
  return now.format('yyMMdd-HHmmss', TimeZone.getTimeZone('UTC'))
}

def gitCommit() {
  return GIT_COMMIT.take(6)
}

def pkgFilename(type, ext, arch=null) {
  /* the grep removes the null arch */
  return [
    "StatusIm", timestamp(), gitCommit(), type, arch,
  ].grep().join('-') + ".${ext}"
}

def doGitRebase() {
  if (!isPRBuild()) {
    println 'Skipping rebase due for non-PR build...'
    return
  }
  def rebaseBranch = 'develop'
  if (env.CHANGE_TARGET) { /* This is available for PR builds */
    rebaseBranch = env.CHANGE_TARGET
  }
  sh 'git status'
  sh "git fetch --force origin ${rebaseBranch}:${rebaseBranch}"
  try {
    sh "git rebase ${rebaseBranch}"
  } catch (e) {
    sh 'git rebase --abort'
    throw e
  }
}

def genBuildNumber() {
  def number = sh(
    returnStdout: true,
    script: "${env.WORKSPACE}/scripts/version/gen_build_no.sh"
  ).trim()
  println "Build Number: ${number}"
  return number
}

def readBuildNumber() {
  def number = sh(
    returnStdout: true,
    script: "${env.WORKSPACE}/scripts/version/build_no.sh"
  ).trim()
  return number
}

def getDirPath(path) {
  return path.tokenize('/')[0..-2].join('/')
}

def getFilename(path) {
  return path.tokenize('/')[-1]
}

def getEnv(build, envvar) {
  return build.getBuildVariables().get(envvar)
}

def buildDuration() {
  def duration = currentBuild.durationString
  return '~' + duration.take(duration.lastIndexOf(' and counting'))
}

def pkgFind(glob) {
  def fullGlob =  "pkg/*${glob}"
  def found = findFiles(glob: fullGlob)
  if (found.size() == 0) {
    sh 'ls -l pkg/'
    error("File not found via glob: ${fullGlob} ${found.size()}")
  }
  return found[0].path
}

def isPRBuild() {
  return env.JOB_NAME.startsWith('status-react/prs')
}

def isE2EBuild() {
  return env.JOB_NAME.contains('e2e')
}

def getBuildType() {
  def jobName = env.JOB_NAME
  if (isE2EBuild()) {
      return 'e2e'
  }
  if (isPRBuild()) {
      return 'pr'
  }
  if (jobName.startsWith('status-react/nightly')) {
      return 'nightly'
  }
  if (jobName.startsWith('status-react/release')) {
      return 'release'
  }
  return params.BUILD_TYPE
}

def getParentRunEnv(name) {
  def c = currentBuild.rawBuild.getCause(hudson.model.Cause$UpstreamCause)
  if (c == null) { return null }
  return c.getUpstreamRun().getEnvironment()[name]
}

def changeId() {
  /* CHANGE_ID can be provided via the build parameters or from parent */
  def changeId = env.CHANGE_ID
  changeId = params.CHANGE_ID ? params.CHANGE_ID : changeId
  changeId = getParentRunEnv('CHANGE_ID') ? getParentRunEnv('CHANGE_ID') : changeId
  if (!changeId) {
    println('This build is not related to a PR, CHANGE_ID missing.')
    println('GitHub notification impossible, skipping...')
    return null
  }
  return changeId
}

def updateEnv(type) {
  def envFile = "${env.WORKSPACE}/.env.jenkins"
  /* select .env based on type of build */
  if (['nightly', 'release', 'e2e'].contains(type)) {
    envFile = "${env.WORKSPACE}/.env.${type}"
  }
  sh "ln -sf ${envFile} .env"
  /* show contents for debugging purposes */
  sh "cat ${envFile}"
}

return this
