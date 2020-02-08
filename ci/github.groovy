import groovy.json.JsonBuilder

/* I'm using utils from ghcmgr to avoid another load */
ghcmgr = load 'ci/ghcmgr.groovy'

/**
 * Methods for interacting with GitHub API and related tools.
 **/

/* Comments -------------------------------------------------------*/

def notify(message) {
  def githubIssuesUrl = 'https://api.github.com/repos/status-im/status-react/issues'
  def changeId = ghcmgr.utils.changeId() 
  if (changeId == null) { return }
  def msgObj = [body: message]
  def msgJson = new JsonBuilder(msgObj).toPrettyString()
  withCredentials([usernamePassword(
    credentialsId:  'status-im-auto',
    usernameVariable: 'GH_USER',
    passwordVariable: 'GH_PASS'
  )]) {
    sh """
      curl --silent \
        -u '${GH_USER}:${GH_PASS}' \
        --data '${msgJson}' \
        -H "Content-Type: application/json" \
        "${githubIssuesUrl}/${changeId}/comments"
    """.trim()
  }
}

def notifyFull(urls) {
  def msg = "#### :white_check_mark: "
  msg += "[${env.JOB_NAME}${currentBuild.displayName}](${currentBuild.absoluteUrl}) "
  msg += "CI BUILD SUCCESSFUL in ${ghcmgr.utils.buildDuration()} (${GIT_COMMIT.take(8)})\n"
  msg += '| | | | | |\n'
  msg += '|-|-|-|-|-|\n'
  msg += "| [Android](${urls.Apk}) ([e2e](${urls.Apke2e})) "
  msg += "| [iOS](${urls.iOS}) ([e2e](${urls.iOSe2e})) |"
  if (urls.Mac != null) {
    msg += " [MacOS](${urls.Mac}) | [AppImage](${urls.App}) | [Windows](${urls.Win}) |"
  } else {
    msg += " ~~MacOS~~ | ~~AppImage~~ | ~~Windows~~~ |"
  }
  notify(msg)
}

def notifyPRFailure() {
  def d = ":small_orange_diamond:"
  def msg = "#### :x: "
  msg += "[${env.JOB_NAME}${currentBuild.displayName}](${currentBuild.absoluteUrl}) ${d} "
  msg += "${ghcmgr.utils.buildDuration()} ${d} ${GIT_COMMIT.take(8)} ${d} "
  msg += "[:page_facing_up: build log](${currentBuild.absoluteUrl}/consoleText)"
  //msg += "Failed in stage: ${env.STAGE_NAME}\n"
  //msg += "```${currentBuild.rawBuild.getLog(5)}```"
  notify(msg)
}

def notifyPRSuccess() {
  def d = ":small_blue_diamond:"
  def msg = "#### :heavy_check_mark: "
  def type = ghcmgr.utils.isE2EBuild() ? ' e2e' : ''
  msg += "[${env.JOB_NAME}${currentBuild.displayName}](${currentBuild.absoluteUrl}) ${d} "
  msg += "${ghcmgr.utils.buildDuration()} ${d} ${GIT_COMMIT.take(8)} ${d} "
  msg += "[:package: ${env.TARGET}${type} package](${env.PKG_URL})"
  notify(msg)
}

/* Releases -------------------------------------------------------*/

def getPrevRelease() {
  return sh(returnStdout: true,
    script: "git for-each-ref --format '%(refname)' --sort=committerdate refs/remotes/origin/release"
  ).trim().split('\\r?\\n').last()
}

def getDiffUrl(prev, current) {
  prev = prev.replaceAll(/.*origin\//, '')
  return "https://github.com/status-im/status-react/compare/${prev}...${current}"
}

def getReleaseChanges() {
  def prevRelease = getPrevRelease()
  def curRelease = ghcmgr.utils.branchName()
  def changes = ''
  try {
    changes = sh(returnStdout: true,
      script: """
        git log \
          --cherry-pick \
          --right-only \
          --no-merges \
          --format='%h %s' \
          ${prevRelease}..HEAD
      """
    ).trim()
  } catch (Exception ex) {
    println 'ERROR: Failed to retrieve changes.'
    println ex.message
    return ':warning: __Please fill me in!__'
  }
  /* remove single quotes to not confuse formatting */
  changes = changes.replace('\'', '')
  return changes + '\nDiff:' + getDiffUrl(prevRelease, curRelease)
}

def releaseExists(Map args) {
  def output = sh(
    returnStdout: true,
    script: """
      github-release info --json \
        -u '${args.user}' \
        -r '${args.repo}' \
        | jq '.Releases[].tag_name'
    """
  )
  return output.contains(args.version)
}

def releaseDelete(Map args) {
  if (!releaseExists(args)) {
    return
  }
  sh """
    github-release delete \
      -u '${args.user}' \
      -r '${args.repo}' \
      -t '${args.version}' \
  """
}

def releaseUpload(Map args) {
  args.files.each {
    sh """
      github-release upload \
        -u '${args.user}' \
        -r '${args.repo}' \
        -t '${args.version}' \
        -n ${it} \
        -f ${it}
    """
  }
}

def releasePublish(Map args) {
  sh """
    github-release release \
      -u '${args.user}' \
      -r '${args.repo}' \
      -n '${args.version}' \
      -t '${args.version}' \
      -c '${args.branch}' \
      -d '${args.desc}' \
      ${args.draft ? '--draft' : ''}
  """
}

def publishRelease(Map args) {
  def config = [
    draft: true,
    user: 'status-im',
    repo: 'status-react',
    files: args.files,
    version: args.version,
    branch: ghcmgr.utils.branchName(),
    desc: getReleaseChanges(),
  ]
  /* we release only for mobile right now */
  withCredentials([usernamePassword(
    credentialsId:  'status-im-auto',
    usernameVariable: 'GITHUB_USER',
    passwordVariable: 'GITHUB_TOKEN'
  )]) {
    releaseDelete(config) /* so we can re-release it */
    releasePublish(config)
    releaseUpload(config)
  }
}

def publishReleaseMobile(path='pkg') {
  def found = findFiles(glob: "${path}/*")
  if (found.size() == 0) {
    sh "ls ${path}"
    error("No file to release in ${path}")
  }
  publishRelease(
    version: ghcmgr.utils.getVersion(),
    files: found.collect { it.path },
  )
}

def notifyPR(success) {
  if (ghcmgr.utils.changeId() == null) { return }
  try {
    ghcmgr.postBuild(success)
  } catch (ex) { /* fallback to posting directly to GitHub */
    println "Failed to use GHCMGR: ${ex}"
    switch (success) {
      case true:  notifyPRSuccess(); break
    }
  }
}

return this
