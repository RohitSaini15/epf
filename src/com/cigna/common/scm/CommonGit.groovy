package com.cigna.common.scm

import com.cigna.common.utils.FeatureFlags
import com.cloudbees.groovy.cps.NonCPS
import com.evernorth.cloudnativebuild.data.PipelineConstants
import groovy.json.JsonSlurper
import jenkins.plugins.http_request.HttpMode
import jenkins.plugins.http_request.MimeType
import jenkins.plugins.http_request.ResponseContentSupplier
import org.apache.commons.lang3.StringUtils

import static com.cigna.common.utils.Utils.ifNull

/**
 * Class designed to manage GitHub SCM activity and updates
 */
class CommonGit implements Serializable {

    protected String statusEndpoint = 'https://github.sys.cigna.com/api/v3/repos/'

    @NonCPS
    static Map<String, Object> defaultSCMSettings() {
        [
            repo: [
                urls    : [],
                branches: []
            ]
        ]
    }
    final Map<String, String> gitlabStatusTranslations =
        ['failure': 'failed',
         'pending': 'pending',
         'success': 'success']

    Map<String, Object> config
    def script
    List validStatusCodes = [200, 201]
    String localGitHubCredentialsId
    List<String> changedFiles
    String scmUrl
    String org
    String repo
    String scmHost

    /**
     * Sets commit status for GitHub repo
     *
     * @return code commit status for GitHub repo
     */
    void postCommitStatus(String name, String state, String allMessage, String contextName) {
        String sha = script.env.GIT_COMMIT
        String buildUrl = script.env.BUILD_URL
        String branch = script.env.BRANCH_NAME
        boolean branchIsPR = branch ==~ /PR-.*/
        String context = contextName ?: 'continuous-integration/jenkins'

        if (!branch || !sha) {
            return
        }

        if (branchIsPR) {
            String pullReq = branch - ('PR-')
            String prCommitStatusUrl = statusEndpoint + "${org}/${repo}/pulls/${pullReq}"

            script.withCredentials([
                script.string(
                    credentialsId: localGitHubCredentialsId,
                    variable: 'EPF_GITHUB_ACCESS_TOKEN')
            ]) {
                // gets head commit in PR
                script.echo("Making Status Update call to URL: ${prCommitStatusUrl} using '$localGitHubCredentialsId'")
                def response = script.httpRequest(
                    url: prCommitStatusUrl,
                    quiet: true,
                    httpMode: HttpMode.GET,
                    consoleLogResponseBody: FeatureFlags.debug,
                    contentType: MimeType.APPLICATION_JSON,
                    acceptType: MimeType.APPLICATION_JSON,
                    validResponseCodes: '100:599',
                    customHeaders: [
                        [
                            maskValue: true,
                            name     : 'Authorization',
                            value    : 'Bearer ' + script.EPF_GITHUB_ACCESS_TOKEN
                        ]
                    ])
                if (response.status > 399 || response.status < 0) {
                    script.echo("Response ${response.status} not in acceptable range: ${response.content} : ${response.headers}")
                    script.currentBuild.result = "UNSTABLE"
                    return
                }
                def content = response.getContent()

                JsonSlurper jsonSlurper = new JsonSlurper()
                Map<String, Object> parsedResponse = jsonSlurper.parseText(content)
                String headSha = parsedResponse?.head?.sha

                if (sha != headSha) {
                    script.echo("SHA: ${sha}")
                    script.echo("Head SHA: ${headSha}")
                    throw new FailedToPostStatus(
                        'Your merge commit and head commit do not match. '
                            + 'Please see if your feature branch needs rebasing or conflict resolution. More information '
                            + 'on how to rebase here: https://git-scm.com/book/en/v2/Git-Branching-Rebasing'
                    )
                }
            }
        }

        String commitStatusUrl = statusEndpoint + "${org}/${repo}/statuses/${sha}"
        script.withCredentials([
            script.string(
                credentialsId: localGitHubCredentialsId,
                variable: 'EPF_GITHUB_ACCESS_TOKEN')
        ]) {
            script.echo('Posting Commit Status...')
            ResponseContentSupplier response = script.httpRequest(
                url: commitStatusUrl,
                quiet: true,
                httpMode: HttpMode.POST,
                consoleLogResponseBody: FeatureFlags.debug,
                contentType: MimeType.APPLICATION_JSON,
                acceptType: MimeType.APPLICATION_JSON,
                customHeaders: [
                    [
                        maskValue: true,
                        name     : 'Authorization',
                        value    : 'Bearer ' + script.EPF_GITHUB_ACCESS_TOKEN
                    ]
                ],
                requestBody: """
                {
                    "state": "${state}",
                    "target_url": "${buildUrl}",
                    "description": "${name} ${allMessage ?: ''}",
                    "context": "${context}"
                }
                """,
                validResponseCodes: '100:599'
            )
            if (!validStatusCodes.contains(response?.status)) {
                script.echo(
                    "Failed to post GitHub commit status, returned ${response?.status} [${response?.content}]" +
                        '\ncontinuing...'
                )
            }
        }
    }

    void grabChangedFiles() {
        if (scmHost == 'git.sys.cigna.com') {
            return
        }
        ResponseContentSupplier response
        List<String> files = []
        Boolean allFilesFound = false
        String branch = script.env.BRANCH_NAME
        boolean isPR = branch ==~ /PR-.*/
        String branchName = isPR ? branch - 'PR-' : branch
        String pullReqUrl = statusEndpoint + "${org}/${repo}/pulls/${branchName}/files"
        String commitUrl = statusEndpoint + "${org}/${repo}/commits/${branchName}"
        String requestUrl = isPR ? pullReqUrl : commitUrl

        script.withCredentials([
            script.string(
                credentialsId: localGitHubCredentialsId,
                variable: 'EPF_GITHUB_ACCESS_TOKEN')
        ]) {
            script.echo('Assessing changed files...')
            while (!allFilesFound) {
                response = script.httpRequest(
                    url: requestUrl,
                    quiet: true,
                    consoleLogResponseBody: FeatureFlags.debug,
                    customHeaders: [
                        [
                            maskValue: true,
                            name     : 'Authorization',
                            value    : 'Bearer ' + script.EPF_GITHUB_ACCESS_TOKEN
                        ]
                    ],
                    validResponseCodes: '100:599'
                )
                if (!validStatusCodes.contains(response.status)) {
                    script.echo(
                        "Failed to get commit info, returned ${response.status} [${response.content}]" +
                            '\nSetting changed files to an empty list...'
                    )
                    files = []
                    break
                }
                JsonSlurper jsonSlurper = new JsonSlurper()

                if (isPR) {
                    List<Map> parsedResponse = jsonSlurper.parseText(response.content)
                    parsedResponse.each { file -> files += file.filename }
                } else {
                    Map<String, Object> parsedResponse = jsonSlurper.parseText(response.content)
                    parsedResponse.files.each { file -> files += file.filename }
                }

                // If current response has a Link header, indicates may contain more files to paginate
                if (response.headers.containsKey('Link')) {
                    // Link is a list of length one, containing a string of CSV links
                    // ex. Link: '<uri-reference>; rel="next", <uri-reference>; rel="first"'
                    // https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Link#specifying_multiple_links
                    def headerLinks = response.headers['Link'][0]
                    // Split the string into a list and find the "next" link
                    def nextLink = headerLinks.split(',').findAll { link -> link.contains('rel="next"') }
                    // If there is a "next" link, that is the URL of the next request
                    if (nextLink) {
                        // Parse the URL out of the "next" link and use for subsequent request
                        def nextRequestUrl = StringUtils.substringBetween(nextLink[0], "<", ">")
                        requestUrl = nextRequestUrl
                        // Else the links are refs like "prev" and "first"
                    } else {
                        allFilesFound = true
                    }
                    // No Link header exists, all files listed in one page
                } else {
                    allFilesFound = true
                }
            }
        }

        script.echo("Changed files: $files")

        this.changedFiles = files
    }

    String getDefaultBranchName() {
        if (scmHost == 'git.sys.cigna.com') {
            return
        }
        ResponseContentSupplier response
        String requestUrl = statusEndpoint + "${org}/${repo}"
        script.withCredentials([
            script.string(
                credentialsId: localGitHubCredentialsId,
                variable: 'EPF_GITHUB_ACCESS_TOKEN')
        ]) {
            script.echo("Getting repository details using $requestUrl ...")
            response = script.httpRequest(
                url: requestUrl,
                quiet: true,
                consoleLogResponseBody: FeatureFlags.debug,
                customHeaders: [
                    [
                        maskValue: true,
                        name     : 'Authorization',
                        value    : 'Bearer ' + script.EPF_GITHUB_ACCESS_TOKEN

                    ],
                    [ // required for GHC
                        name     : 'X-GitHub-Api-Version',
                        value    : '2022-11-28'
                    ]
                ],
                validResponseCodes: '100:599'
            )
        }

        try {
            JsonSlurper jsonSlurper = new JsonSlurper()
            Map<String, Object> parsedResponse = jsonSlurper.parseText(response.content)
            if (parsedResponse.containsKey("default_branch")) {
                script.echo("Default branch: $parsedResponse.default_branch")
                return parsedResponse.default_branch
            } else {
                /**
                 *  The JSON was parsed successfully, but the default_branch property isn't found in the resulting map.
                 *  We throw an exception to move to the catch block, print the error, and return " "
                 */
                throw new FailedToRetrieveDefaultBranch(
                    "There was a failure retrieving your repository's default branch. Please see the following message for more context: " +
                        parsedResponse
                )
            }
        } catch (all) {
            script.echo("$all")
            return " "
        }

    }

    /**
     * Side-effecting - use the appropriate API call to update the git provider with a commit status
     * valid state values (to bridge the gap between github and gitlab statuses) are 'pending', 'success', 'failure'
     */
    void updateGitStatus(Map<String, String> map) {
        updateGitStatusImpl(map['name'], map['state'], map['allMessage'], map['contextName'])
    }

    void updateGitStatus(String name, String state, String allMessage, String contextName) {
        updateGitStatusImpl(name, state, allMessage, contextName)
    }

    void updateGitStatusImpl(String name, String state, String allMessage, String contextName) {
        String localName = name ?: 'EPF'

        switch (scmHost) {
            case ['github.sys.cigna.com',
                  'git.express-scripts.com']:
                script.echo(
                    "Using GitHubSCM provider for name: $localName, state: $state, " +
                        "phase: ${script.env.PHASE_NAME ?: 'Status Check'}, as: $contextName")
                postCommitStatus(localName, state, allMessage, contextName)
                break
            case 'git.sys.cigna.com':
                script.echo("Continuing with GitSCM provider for name: $localName, state: $state.")
                script.updateGitlabCommitStatus(name: localName, state: gitlabStatusTranslations[state.toLowerCase()] ?: state)
                break
        }
    }

    CommonGit(Map<String, Object> config, def script) {
        this.config = config
        this.script = script
        this.scmUrl = script.scm.userRemoteConfigs[0].url
        List segments = scmUrl.split('/')
        this.org = segments[3]
        this.repo = segments[4] - '.git'
        this.scmHost = segments[2]
        this.localGitHubCredentialsId = config?.gitHubCredentialsId ?: config?.githubCredentialsId ?: scmHost?.startsWith("github.com") ? PipelineConstants.CLOUD_GIT_TOKEN : 'prd-github-access-token'
        this.statusEndpoint = scmHost?.startsWith("github.com") ? "https://api.$scmHost/repos/" : "https://$scmHost/api/v3/repos/"
    }

    class FailedToPostStatus extends Exception {
        FailedToPostStatus(String message) {
            super(message)
        }

    }

    class FailedToRetrieveDefaultBranch extends Exception {
        FailedToRetrieveDefaultBranch(String message) {
            super(message)
        }

    }


    /**
     * Set scmVars to jenkins environment variables
     */
    void gitCommitEnvVars(Map<String, String> scmVars) {
        String gitBranch = scmVars?.GIT_BRANCH
        String gitCommit = scmVars?.GIT_COMMIT
        String gitCommitShort = gitCommit ? gitCommit[0..7] : 'noCommit'
        String gitPreviousCommit = scmVars?.GIT_PREVIOUS_COMMIT
        String gitPreviousCommitShort = gitPreviousCommit ? gitPreviousCommit[0..7] : 'noPreviousCommit'
        String gitPreviousSuccessfulCommit = scmVars?.GIT_PREVIOUS_SUCCESSFUL_COMMIT
        String gitPreviousSuccessfulCommitShort =
            gitPreviousSuccessfulCommit ? gitPreviousSuccessfulCommit[0..7] : 'noPreviousCommit'
        String gitUrl = scmVars?.GIT_URL

        script.env.GIT_BRANCH = "${gitBranch}"
        script.env.GIT_COMMIT = "${gitCommit}"
        script.env.GIT_COMMIT_SHORT = "${gitCommitShort}"
        script.env.GIT_PREVIOUS_COMMIT = "${gitPreviousCommit}"
        script.env.GIT_PREVIOUS_COMMIT_SHORT = "${gitPreviousCommitShort}"
        script.env.GIT_PREVIOUS_SUCCESSFUL_COMMIT = "${gitPreviousSuccessfulCommit}"
        script.env.GIT_PREVIOUS_SUCCESSFUL_COMMIT_SHORT = "${gitPreviousSuccessfulCommitShort}"
        script.env.GIT_URL = "${gitUrl}"
        script.env.scmVars = true
    }

    String gitUrl() {
        script.scm.userRemoteConfigs[0].url
    }

    /**
     * Check out the git code and sets scmVars property to the Jenkins scmVars. Also updates gitlab
     * commit status since otherwise Jenkins will not know which git repo to update without an initial
     * checkout for multibranch pipelines. See
     * https://github.com/jenkinsci/gitlab-plugin#scripted-or-declarative-pipeline-jobs for details
     * on why this timing is needed.
     *
     * XXX.cnm - TODO - Now that we only perform the checkout operation once per pod instead of once
     *         - TODO - phase/module, we need to support checkouts of multiple branches and multiple
     *         - TODO - urls. MVP is only to support one of each, but this is a tech debt gap
     */
    void checkout(String branch = '', String gitWorkspace = script.env.WORKSPACE) {
        boolean shallow = ifNull(config.repo?.shallow, true)
        boolean noTags = ifNull(config.repo?.noTags, true)
        boolean lfs = ifNull(config.repo?.lfs, false)
        Boolean generateSubmoduleConfigs = ifNull(config?.repo?.doGenerateSubmoduleConfigurations, false)
        boolean checkoutCode = false

        if (config?.phaseCache) {
            checkoutCode = script?.env?.CHECKED_OUT ? false : true
        } else {
            boolean checkedOut = script.fileExists('./.git/index')
            checkoutCode = !checkedOut
        }

        def userRemoteConfigs = new ArrayList()
        if (config?.repo?.urls?.size() > 0) {
            userRemoteConfigs.add([url: "${config?.repo?.urls[0]}"])
        } else {
            userRemoteConfigs.addAll(script?.scm?.userRemoteConfigs)
        }

        def branches = new ArrayList()

        if (config.containsKey('repo') && config.repo.containsKey('noTags')) {
            noTags = config.repo.noTags
        }

        if (branch) {
            branches.add([name: "*/${branch}"])
            userRemoteConfigs.add([
                url          : "${gitUrl()}",
                credentialsId: "${script.scm.userRemoteConfigs[0].credentialsId}"
            ])
        } else {
            if (config?.repo?.branches?.size() > 0) {
                branches.add([name: "*/${config?.repo?.branches[0]}"])
            } else {
                branches.addAll(script?.scm?.branches)
            }
        }

        def localExtensions = new ArrayList()
        if (script?.scm?.extensions?.size() > 0) {
            localExtensions.addAll(script?.scm?.extensions)
        }
        if (!noTags) {
            localExtensions = script.scm?.extensions?.findAll {
                script.echo("Evaluating '${it.class.simpleName}' for extensions filtering")
                it.class.simpleName != 'GitSCMSourceDefaults'
            }
        }
        def extensions = new ArrayList()
        extensions.add([
            $class : 'CloneOption',
            shallow: shallow,
            noTags : noTags,
        ])
        extensions.addAll(localExtensions)

        if (lfs) {
            extensions.addAll([$class: 'GitLFSPull'], [$class: 'CheckoutOption', timeout: 60])
        }
        script.dir(gitWorkspace) {
            boolean suppressSafeDirectory = ifNull(config?.suppressWorkspaceSafeDirectory, true)

            if (checkoutCode) {
                script.echo("Branch going to checkout: ${branches} : ${extensions}")
                Map<String, String> scmVars = script.checkout([
                    $class                           : 'GitSCM',
                    branches                         : branches,
                    extensions                       : extensions,
                    doGenerateSubmoduleConfigurations: generateSubmoduleConfigs,
                    submoduleCfg                     : new ArrayList(),
                    userRemoteConfigs                : userRemoteConfigs,
                ])

                gitCommitEnvVars(scmVars)
                script.echo("Branch checked out: ${script.env.GIT_BRANCH}")

                script.env.CHECKED_OUT = true
            } else {
                script.echo('Already checked out code, moving on...')
            }

            if (!suppressSafeDirectory) {
                script.sh("git config --global --add safe.directory \"$gitWorkspace\"")
            }
        }
    }
}
