package com.cigna.common.utils

/**
 * This contains the common functions that can be used across the QEATesting Phases
 */
class QEATestingUtils {

    /**
     * Sending New Webex Notifications
     */
    static void sendWebexNotifications(Object script, Map<String, Object> testingConfiguration, String message, Boolean attachmentRequired) {
        String roomId = testingConfiguration.webex?.roomId
        String webexTeamCredentials = testingConfiguration?.webexTeamCredentials ?: 'webexTeamCredentials'
        String webexEndpoint = 'https://webexapis.com/v1/messages'
        String currentBuildStatus = script.currentBuild.result ?: 'Job In-Progress'

        String stage = 'Executed ' + testingConfiguration.testType
        if (testingConfiguration?.stage) {
            stage = 'Executed ' + testingConfiguration?.stage
        }

        String webexColor
        if (currentBuildStatus == 'SUCCESS') {
            webexColor = 'Good'
        } else if (currentBuildStatus == 'FAILURE') {
            webexColor = 'Warning'
        } else {
            webexColor = 'Dark'
        }

        String payloadBody = """{
                "roomId": "${roomId}",
                "text": "QEA WebEx Notification!",
                "attachments": [
                        {
                        "contentType": "application/vnd.microsoft.card.adaptive",
                        "content": {
                            "type": "AdaptiveCard",
                            "\$schema": "http://adaptivecards.io/schemas/adaptive-card.json",
                            "version": "1.2",
                            "body": [
                                {
                                    "type": "TextBlock",
                                    "text": "${stage}",
                                    "wrap": true,
                                    "horizontalAlignment": "Center",
                                    "fontType": "Default",
                                    "size": "Medium",
                                    "weight": "Bolder"
                                },
                                {
                                    "type": "TextBlock",
                                    "text": "${message}",
                                    "wrap": true,
                                    "horizontalAlignment": "Center",
                                    "fontType": "Default",
                                    "size": "Medium",
                                    "weight": "Bolder"
                                },
                                {
                                    "type": "TextBlock",
                                    "text": "Job '${script.env.JOB_NAME}'",
                                    "wrap": true,
                                    "fontType": "Default",
                                    "horizontalAlignment": "Center"
                                },
                                {
                                    "type": "TextBlock",
                                    "text": "Job #: ${script.env.BUILD_NUMBER}",
                                    "wrap": true,
                                    "horizontalAlignment": "Center"
                                }                                                                
                            ]
                        }
                    }
                ]
            }"""

        script.withCredentials([
            script.string(
                credentialsId: webexTeamCredentials,
                variable: 'WEBEX_TEAM_TOKEN')
        ]) {
            script.httpRequest(
                acceptType: 'APPLICATION_JSON',
                contentType: 'APPLICATION_JSON',
                httpMode: 'POST',
                consoleLogResponseBody: true,
                requestBody: payloadBody,
                url: webexEndpoint,
                customHeaders: [
                    [
                        maskValue: true,
                        name     : 'Authorization',
                        value    : 'Bearer ' + script.WEBEX_TEAM_TOKEN
                    ]
                ]
            )
        }

        if (testingConfiguration.webex?.fileName && attachmentRequired) {
            String webexReportFile = testingConfiguration.webex?.fileName
            String text = testingConfiguration.testType + ' Phase: Please find the reports attached for Build ' + script.env.BUILD_NUMBER
            // TODO: insecure interpolation of WEBEX_TEAM_TOKEN
            script.withCredentials([
                script.string(credentialsId: 'webexTeamCredentials', variable: 'WEBEX_TEAM_TOKEN')]) {
                //String token = "${script.WEBEX_TEAM_TOKEN}"
                script.sh("curl https://webexapis.com/v1/messages -X POST -H \
                \'Authorization: Bearer \'${script.WEBEX_TEAM_TOKEN}\' \' \
                -F \'roomId=\'${roomId}\' \' -F \'text=${text}\' -F files=@${webexReportFile} ")
            }
        }

    }


    /**
     * Checking out EPT Utilities
     */
    static void checkoutJinjaTemplates(Object script) {
        checkoutDependencies(script, ['git': ['url': 'https://github.sys.cigna.com/cigna/EPT_Utilities', 'branch': 'main']])
        script.sh("mv test-workspace/* .")
    }


    /**
     * Checking out dependencies
     */
    static void checkoutDependencies(Object script, Map<String, Object> testingConfiguration) {
        String url = testingConfiguration?.git?.url
        String branch = testingConfiguration?.git?.branch
        String TESTING_WORKSPACE_DIR = 'test-workspace'

        script.checkout(changelog: false, poll: false,
            scm: [$class                           : 'GitSCM',
                  branches                         : [[name: "*/${branch}"]],
                  doGenerateSubmoduleConfigurations: false,
                  extensions                       : [
                      [
                          $class           : 'RelativeTargetDirectory',
                          relativeTargetDir: TESTING_WORKSPACE_DIR
                      ]
                  ],
                  submoduleCfg                     : [],
                  userRemoteConfigs                : [
                      [
                          credentialsId: "${script.scm.userRemoteConfigs[0].credentialsId}",
                          url          : url
                      ]
                  ],
            ])
    }

    /**
     * Copying the report folders to shared network path
     */
    static void smbFileTransfer(Object script, Map<String, Object> testingConfiguration) {
        script.withCredentials([
            script.usernamePassword(
                credentialsId: testingConfiguration.smb.credentialsId,
                usernameVariable: 'userIdSMB',
                passwordVariable: 'passwordSMB'
            )]) {

            String cmd
            if (testingConfiguration.smb.navigateToPath) {
                cmd = "cd ${testingConfiguration.smb.navigateToPath} && smbclient"
            } else {
                cmd = "smbclient"
            }

            script.sh(
                script: """${cmd} \\\\\\\\${testingConfiguration.smb.ip}\\\\CHCDATA \
                            -U internal\\\\${script.userIdSMB}%${script.passwordSMB} << Commands
                            cd ${testingConfiguration.smb.nasPath}
                            put ${testingConfiguration.smb.fileName}
                            exit
                            Commands""")
        }
    }

    /**
     * Custom NAS Transfer
     */
    static void nasTransfer(Object script, Map<String, Object> testingConfiguration) {
        script.withCredentials([
            script.usernamePassword(
                credentialsId: "${testingConfiguration.smb.credentialsId}",
                usernameVariable: 'userIdSMB',
                passwordVariable: 'passwordSMB'
            )
        ]) {
            try {
                script.sh(returnStatus: false, script: Utils.buildCommandArgs(["venv/bin/nas_mkd_dir", "${script.userIdSMB}", "\$passwordSMB", "--dest_dir", "\"${testingConfiguration.smb.nasPath}\\\\${testingConfiguration.application}\\\\${testingConfiguration.application}_${script.env.BUILD_NUMBER}\""]))
                script.sh(Utils.buildCommandArgs(["venv/bin/nas_upload_files", "${script.userIdSMB}", "\$passwordSMB", "--source", "reports.zip", "--destination", "\"${testingConfiguration.smb.nasPath}\\\\${testingConfiguration.application}\\\\${testingConfiguration.application}_${script.env.BUILD_NUMBER}\""]))
            }
            catch (Exception e) {
                script.echo('Error with NAS Transfer')
                script.error(e.getMessage())
            }
        }
    }

    /**
     * Publish junit report
     */
    static void publishJunitReportRun(Object script, Map<String, Object> testingConfiguration) {
        script.junit(
            testResults: testingConfiguration.junit?.testResults,
            allowEmptyResults: testingConfiguration.junit?.allowEmptyResults,
            healthScaleFactor: testingConfiguration.junit?.healthScaleFactor,
        )
    }

    /**
     * Transform a QEA-specific email configuration into a general phase-level email configuration
     */
    static Map generateEmailConfig(def script, Map testingConfiguration, String message = 'Execution Completed') {
        def emailConfig = [:]
        if (testingConfiguration.emailRecipients && testingConfiguration.reportFile && testingConfiguration.emailBody) {
            String region = script.env.Region ?: script.env.ENVIRONMENT
            emailConfig = [
                attachmentFile: testingConfiguration.reportFile,
                recipients    : testingConfiguration.emailRecipients,
                body          : "${testingConfiguration.emailBody}<br><br>${message}",
                subject       : "Test Execution: ${script.env.JOB_BASE_NAME}; BUILD_NUMBER:[${script.env.BUILD_NUMBER}]; REGION : ${region}",
            ]
        }
        return emailConfig
    }

    /**
     * Run prep/post commands
     */
    static void runCommands(Object script, String type, Map<String, Object> testingConfiguration) {
        List commands
        if (type == 'prep') {
            script.echo('Running Prep Commands')
            commands = testingConfiguration.prepCommands
        } else {
            script.echo('Running Post Commands')
            commands = testingConfiguration.postCommands
        }

        if (commands instanceof List && commands.every { it instanceof Map }) {
            for (cmdmap in commands) {
                String command = cmdmap['command']
                String timeout = cmdmap['timeout']
                String units = cmdmap['units']
                if (timeout != null && units != null) {
                    script.timeout(time: timeout, unit: units) {
                        try {
                            script.sh(command)
                        } catch (all) {
                            script.echo('Timeout occured')
                            if (testingConfiguration.webex) {
                                sendWebexNotifications(script, testingConfiguration, 'Timeout occured during execution', testingConfiguration.webex.attachmentRequired)
                            }
                        }
                    }
                } else {
                    script.sh(command)
                }
            }
        } else {
            for (String cmd in commands) {
                script.sh(cmd)
            }
        }
    }

    /**
     * Search for a string in XML files
     */
    static String searchXML(Object script, String expr, String path) {
        String val = script.sh(script: "sed -n 's/.*${expr}\"\\([0-9]*\\)\".*/\\1/p' ${path}", returnStdout: true)
        return val ?: ''
    }
}
