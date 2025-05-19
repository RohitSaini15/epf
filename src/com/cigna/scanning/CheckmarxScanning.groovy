package com.cigna.scanning


import com.cigna.common.utils.FeatureFlags
import com.cigna.common.scm.CommonGit
import com.cigna.common.utils.Utils
import com.cloudbees.groovy.cps.NonCPS
import hudson.Functions

/**
 * Defined behavior for Checkmarx scanning
 */
class CheckmarxScanning extends Scanning {
    CheckmarxScanning() {

    }
    String cxServerAddress = 'https://cigna.checkmarx.net'
    protected List<String> supportedCheckmarxConfig = ['Multi-language Scan', 'Default Configuration']

    /**
     * Trigger a checkmarx code scan
     */
    void scan(String checkmarxContainer = containerName) {
        checkmarxScan(checkmarxContainer)
    }

    String determineScm() {
        String scmUrl = script.scm.userRemoteConfigs[0].url
        List segments = scmUrl.split('/')
        String org = segments[2]
        List scm = org.tokenize('.')
        String getScm = scm[0]
        String fullRepoName = ''

        if (getScm ==~ /github/) {
            String ghScm = script.scm.userRemoteConfigs[0].url
            List retrieveList = ghScm.tokenize('/')
            String gitHubRepoName = retrieveList[3]
            String repoName = gitHubRepoName - ('.git')
            script.echo("Retrieved Repository Name: ${repoName}")
            return fullRepoName + repoName
        } else if (getScm ==~ /git/) {
            String glScm = script.scm.userRemoteConfigs[0].url
            String parseScmUrl = glScm.replaceAll('/', '.') - 'https:..git.sys.cigna.com.' - ('.git')
            String longRepoName = parseScmUrl.toLowerCase()
            script.echo("Retrieved Repository Name: ${longRepoName}")
            return fullRepoName + longRepoName
        }
    }

    Map<Integer, String> scanResultCodes = [
        0  : 'Completed Successfully',
        1  : 'Failed to start scan (general error)',
        2  : 'Invalid license for SDLC',
        3  : 'Invalid license for OSA',
        4  : 'Login failed',
        5  : 'OSA scan requires an existing project on the server',
        6  : 'Failed to resolve dependencies for OSA scan',
        7  : 'No dependencies found for OSA scan',
        10 : 'Failed on threshold SAST HIGH',
        11 : 'Failed on threshold SAST Medium',
        12 : 'Failed on threshold SAST LOW',
        13 : 'Failed on threshold OSA HIGH',
        14 : 'Failed on threshold OSA Medium',
        15 : 'Failed on threshold OSA Low',
        18 : 'Policy is violated',
        19 : 'Generic threshold failure if both SAST and OSA fail',
        30 : 'Failed to resolve Maven dependencies for OSA scan',
        31 : 'Failed to resolve Gradle dependencies for OSA scan',
        32 : 'Failed to resolve NPM dependencies for OSA scan',
        33 : 'Failed to resolve Nuget/DotNet dependencies for OSA scan',
        130: 'Canceled by user (Ctrl-C)'
    ]

    protected void checkmarxScan(def checkmarxContainer) {
        Map<String, String> checkmarxConfig = config.checkmarx
        String buildType = config.buildType
        script.withCredentials([script.usernamePassword(
            credentialsId: "${checkmarxConfig?.credentialsId}",
            passwordVariable: 'CX_PSW',
            usernameVariable: 'CX_USR')]
        ) {
            try {
                // If set, a full Checkmarx CLI log is created.
                Boolean checkmarxLogEnabled = checkmarxConfig.settings.checkmarxLogEnabled ?: false
                String checkmarxIncludeStr = checkmarxLogEnabled ? '-Log cxcli.log ' : ''
                // If there are issues with performing third-party dependency scans, users can set this flag to disable SCA temporarily.
                String disableSCIncludeStr = checkmarxConfig.settings.disableSCA ? '-disablesca ' : ''

                String verboseIncludeStr = checkmarxConfig.settings.verboseEnabled ? '-v ' : ''
                String forceScanStr = checkmarxConfig.settings.forceScan ? '-ForceScan ' : ''
                String userConfig = checkmarxConfig.settings.configuration ?: 'Multi-language Scan'
                String projectName = checkmarxConfig.settings.CX_PROJECT_NAME ?: determineScm()
                String productionBranch = Utils.getCheckmarxProdBranch(script, checkmarxConfig.settings as Map<String, Object>)
                String projectAS = checkmarxConfig.settings.CX_PROJECT_APP_SERVICE_ID ?: ''
                String projectASData = projectAS ? " -projectAS '${projectAS}' " : ''
                String scanComment = checkmarxConfig.settings.CX_SCAN_COMMENT ?: "EPF Pipeline Number: ${script.env.BUILD_NUMBER}, Commit Short: ${script.env.GIT_COMMIT_SHORT}, Branch name: ${script.env.GIT_BRANCH}"
                // dynamically exclude folder based on build type, and added user specified folders to the exclude list
                String excludeFolderList = checkmarxConfig.settings.CX_EXCLUDE_FOLDER_LIST ?: ''
                String folderExcludeData = " -LocationPathExclude '${prepareFolderExcludeList(buildType, excludeFolderList)}' "
                // User has provided includes/excludes file list which needs to be handled by CLI command
                String includeExcludeFileList = checkmarxConfig.settings.CX_INCL_EXCL_FILE_LIST ?: ''
                String fileIncludeExcludeData = includeExcludeFileList ? " -IncludeExcludePattern '${includeExcludeFileList}' " : ''

                String scanType = checkmarxConfig.settings.CX_SCAN_TYPE ?: 'Scan'
                // User has provided specific "breaking build" thresholds. If for example user provided
                // a highThreshold value of 1 and then one or more high issues were found during a Sync
                // scan type, the pipeline would fail. NOTE thresholds are ONLY valid on sync scan types
                String highThreshold = checkmarxConfig.settings.CX_HIGH_THRESHOLD ?: ''
                String mediumThreshold = checkmarxConfig.settings.CX_MEDIUM_THRESHOLD ?: ''
                String lowThreshold = checkmarxConfig.settings.CX_LOW_THRESHOLD ?: ''
                String highThresholdData = highThreshold ? " -SASTHigh '${highThreshold}' " : ''
                String mediumThresholdData = mediumThreshold ? " -SASTMedium '${mediumThreshold}' " : ''
                String lowThresholdData = lowThreshold ? " -SASTLow '${lowThreshold}' " : ''

                String addLocationPath = checkmarxConfig.settings.CX_LOCATION_PATH ?: '.'
                String scanPreset = checkmarxConfig.settings.CX_PRESET ?: 'Checkmarx Default'

                script.echo('Starting Checkmarx Scan...')
// XXX.cnm - leave for now; scale -Xmx based on container stats
//                def podGroup = Utils.cloud(config)
//                def containerDetails = psc.podSelector.podGenerators[podGroup].
//                        podTemplate.spec.containers.find { it.name == checkmarxConfig }
//                ${Utils.calculateOptimalScannerOptions(containerDetails.resources.limits.memory)}
                def statusCode = script.sh(
                    script: "dso-cli checkmarx ${scanType} ${verboseIncludeStr} "
                        + "${forceScanStr} "
                        + "-CxProductionBranch '${productionBranch}' "
                        + "-config /cxtools/CLI/config/cx_console.properties "
                        + "${checkmarxIncludeStr} "
                        + "-CxServer '${cxServerAddress}' "
                        + "-ProjectName 'CxServer/Cigna/${checkmarxConfig.settings.CX_PROJECT_TEAM_NAME}/${projectName}' "
                        + "-LocationPath ${addLocationPath} "
                        + "-Configuration '${userConfig}' "
                        + "-Preset '${scanPreset}' "
                        + "-Comment '${scanComment}' "
                        + "${folderExcludeData}${fileIncludeExcludeData} "
                        + "${highThresholdData}${mediumThresholdData}${lowThresholdData} "
                        + "${disableSCIncludeStr} "
                        + "${projectASData} ",
                    returnStatus: true
                )

                if (checkmarxLogEnabled) {
                    def checkmarxLogOutput = script.sh(
                        script: 'cat cxcli.log',
                        returnStdout: true
                    )
                    script.echo('Outputting Checkmarx CLI Log...')
                    script.echo(checkmarxLogOutput)
                }
                if (statusCode != 0) {
                    def reason = "Unknown Checkmarx Status Code, Are you using a newer toolshack image?"
                    if (scanResultCodes.containsKey(statusCode)) {
                        reason = scanResultCodes[statusCode]
                    }

                    def msg = "Checkmarx Scan failed: ${reason} (${statusCode})"
                    script.echo(msg)
                    script.currentBuild.result = 'FAILURE'
                    throw scanFailedException(msg)
                }

            } catch (all) {
                script.error("Failed Checkmarx Scan: '${all.localizedMessage}'")
                if (FeatureFlags.showStackTraces) {
                    script.echo(Functions.printThrowable(all))
                }

                throw all
            }
        }
    }

    /**
     * this method determines the folder exclude dynamically based on the build type
     * and add the user folder list aswell.
     *
     * @param buildType
     * @param excludeFolderList
     * @return folderExcludeData
     */
    protected String prepareFolderExcludeList(String buildType, String excludeFolderList) {
        String folderExcludeData = ''
        if (buildType?.equalsIgnoreCase('python')) {
            if (excludeFolderList) {
                folderExcludeData = "${excludeFolderList}, .tox, site-packages"
            } else {
                folderExcludeData = '.tox, site-packages'
            }
        } else if (buildType?.equalsIgnoreCase('maven')) {
            if (excludeFolderList) {
                folderExcludeData = "${excludeFolderList}, .m2, target"
            } else {
                folderExcludeData = '.m2, target'
            }
        } else if (buildType?.equalsIgnoreCase('plz')) {
            if (excludeFolderList) {
                folderExcludeData = "${excludeFolderList}, plz-out, data.json"
            } else {
                folderExcludeData = 'plz-out, data.json'
            }
        } else if (buildType?.equalsIgnoreCase('gradle')) {
            def gradleUserHome = config?.gradle?.gradleUserHome ?: '.gradle'
            if (excludeFolderList) {
                folderExcludeData = "${excludeFolderList}, ${gradleUserHome}, build"
            } else {
                folderExcludeData = "${gradleUserHome}, build"
            }
        } else {
            if (excludeFolderList) {
                folderExcludeData = "${excludeFolderList}"
            }
        }
        folderExcludeData
    }

    class ScanFailedException extends Exception {
        ScanFailedException(String message) {
            super(message)
        }
    }

    @NonCPS
    def Throwable scanFailedException(String msg) {
        throw new ScanFailedException(msg)
    }
}
