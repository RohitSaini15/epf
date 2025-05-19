package com.cigna.common.notification

import com.cigna.common.utils.FeatureFlags
import com.cigna.common.utils.Utils
import groovy.json.JsonBuilder
import groovy.json.JsonException

/**
* A class to encapsulate all notification methods to be used to send notifications to configured
* end points.
*/
class Notification implements Serializable {

    protected Map<String, Object> config
    protected Object script
    String webexTeamCredentials = config?.webexTeamCredentials ?: 'webexTeamCredentials'

    /**
    * Send an email, and post to mattermost
    *
    * @param message Any sort of message that should be included in all of the notification methods
    */
    void notifyWithAllMethods(String message, String subject = 'Pipeline event', String messageType = 'INFO',
                              Boolean isSimple = false) {
        Boolean sendMessage = false
        String messageFilter = this.config?.messageFilter ?: 'ALL'
        messageFilter = messageFilter.trim().toUpperCase()
        Map<String, List<String>> filterMap = [
            'ALL': ['COMPLETED', 'STARTED', 'FAILED', 'INFO'],
            'COMPLETED': ['COMPLETED', 'FAILED'],
            'STARTED': ['STARTED'],
            'FAILED': ['FAILED'],
        ]
        if (filterMap.containsKey(messageFilter)) {
            if (filterMap[messageFilter].contains(messageType)) {
                sendMessage = true
            }
        } else {
            // Unknown filter type default to sending all messages
            script.echo('WARNING: Unknown message filter type. Options: ALL, COMPLETED, STARTED, FAILED')
            sendMessage = true
        }
        if (sendMessage) {
            script.parallel(
                sendEmail: {
                    sendEmail(message, subject)
                },
                sendMattermost: {
                    sendMattermost(message, isSimple)
                },
                sendTeams: {
                    sendTeams(message)
                },
                sendWebex: {
                    sendWebex(message)
                }
            )
        }
    }

    /**
    * Supply the build status which is either the currentBuild.result or "Job not finished"
    *
    * @return Build status
    */
    String currentBuildStatus() {
        script.currentBuild.result ?: 'Job not finished'
    }

    /**
    * Supply the color hex code of build status"
    *
    * @param status The build status of the build
    *
    * @return color hex code as String
    */
    String getBuildStatusColor(String status) {
        final String GREEN = '#27E026'
        final String YELLOW = '#EFEF0B'
        final String RED = '#FF3333'
        final String GRAY = '#A9A9A9'
        Map<String, String> buildStatusColorMap = [
            SUCCESS: GREEN,
            UNSTABLE: YELLOW,
            FAILURE: RED,
        ]
        if (buildStatusColorMap.containsKey(status)) {
            return buildStatusColorMap[status]
        }
        GRAY
    }

    /**
    * Send a WebEx Teams channel notification. The message is the Job name with build number
    * and build status if available, otherwise "Job not finished".
    *
    * @param message Any sort of message that should be included in the webex teams message
    */
    void sendWebex(String message) {
        String roomId = config?.webexTeamsRoom
        String webexEndpoint = 'https://webexapis.com/v1/messages'
        String currentBuildStatus = currentBuildStatus()
        String webexColor
        if (currentBuildStatus == 'SUCCESS') {
            webexColor = 'Good'
        }
        else if (currentBuildStatus == 'FAILURE') {
            webexColor = 'Warning'
        }
        else {
            webexColor = 'Dark'
        }

        if (this.config?.webexTeamsRoom) {
             Map payload = [
                "roomId": "${roomId}",
                "text": "EPF Notification!",
                "attachments": [
                    [
                        "contentType": "application/vnd.microsoft.card.adaptive",
                        "content": [
                            "type": "AdaptiveCard",
                            "\$schema": "http://adaptivecards.io/schemas/adaptive-card.json",
                            "version": "1.2",
                            "body": [
                                [
                                    "type": "TextBlock",
                                    "text": "${Utils.removeAnsiColorCodes(message)}",
                                    "maxLines": 3,
                                    "wrap": true,
                                    "horizontalAlignment": "Center",
                                    "fontType": "Default",
                                    "size": "Medium",
                                    "weight": "Bolder"
                                ],
                                [
                                    "type": "TextBlock",
                                    "text": "Job '${script.env.JOB_NAME}'",
                                    "wrap": true,
                                    "fontType": "Default",
                                    "horizontalAlignment": "Center"
                                ],
                                [
                                    "type": "TextBlock",
                                    "text": "Job #: ${script.env.BUILD_NUMBER}",
                                    "wrap": true,
                                    "horizontalAlignment": "Center"
                                ],
                                [
                                    "type": "TextBlock",
                                    "text": "[Job Link](${script.currentBuild.absoluteUrl})",
                                    "wrap": true,
                                    "horizontalAlignment": "Center"
                                ],
                                [
                                    "type": "ColumnSet",
                                    "columns": [
                                        [
                                            "type": "Column",
                                            "width": "stretch",
                                            "items": [
                                                [
                                                    "type": "TextBlock",
                                                    "text": "Status:",
                                                    "wrap": true,
                                                    "horizontalAlignment": "Right"
                                                ]
                                            ]
                                        ],
                                        [
                                            "type": "Column",
                                            "width": "stretch",
                                            "items": [
                                                [
                                                    "type": "TextBlock",
                                                    "text": "${currentBuildStatus}",
                                                    "wrap": true,
                                                    "color": "${webexColor}"
                                                ]
                                            ]
                                        ]
                                    ]
                                ]
                            ]
                        ]
                    ]
                ]
            ]

            def sanitizedPayload = ''
            try {
                sanitizedPayload = new JsonBuilder(payload).toString()
                if (FeatureFlags.debug) {
                    script.echo("payload for ${webexEndpoint}: ${sanitizedPayload}")
                }
                script.withCredentials([
                    script.string(
                        credentialsId: webexTeamCredentials,
                        variable: 'WEBEX_TEAM_TOKEN')
                ]) {
                    script.httpRequest(
                        acceptType: 'APPLICATION_JSON',
                        contentType: 'APPLICATION_JSON',
                        httpMode: 'POST',
                        consoleLogResponseBody: FeatureFlags.debug,
                        requestBody: sanitizedPayload,
                        url: webexEndpoint,
                        customHeaders: [
                            [
                                maskValue: true,
                                name: 'Authorization',
                                value: 'Bearer ' + script.WEBEX_TEAM_TOKEN
                            ]
                        ]
                    )
                }
            } catch (JsonException e) {
                script.echo("Failed to serialize webex request to JSON: ${e.message}. Webex notifications will not be sent and the job will proceed")
            }catch (Exception e) {
                script.echo("Unable to send Webex notifications due to ${e.message} and the job will proceed")
            }
       }
    }

    /**
    * Send a Microsoft Teams channel notification. The message is the Job name with build number
    * and build status if available, otherwise "Job not finished".
    *
    * @param message Any sort of message that should be included in the mattermost message
    */
    void sendTeams(String message) {
        if (this.config?.teamsWebhook) {
            String requestBody = '{' +
                '"@type": "MessageCard", ' +
                '"@context": "http://schema.org/extensions", ' +
                "\"themeColor\": \"${getBuildStatusColor(currentBuildStatus())}\", " +
                '"summary": "EPF Teams Card", ' +
                '"sections": [' +
                    '{' +
                        "\"title\": \"${message}\", " +
                        "\"text\": \"${script.currentBuild.absoluteUrl}\"" +
                    '}' +
                '], ' +
                '"potentialAction": [' +
                    '{' +
                        '"@type": "OpenUri", ' +
                        '"name": "View", ' +
                        '"targets": [' +
                            '{' +
                                '"os": "default", ' +
                                "\"uri\": \"${script.currentBuild.absoluteUrl}\"" +
                            '}' +
                        ']' +
                    '}' +
                ']' +
            '}'
            script.httpRequest(
                acceptType: 'APPLICATION_JSON',
                contentType: 'APPLICATION_JSON',
                httpMode: 'POST',
                requestBody: requestBody,
                url: config.teamsWebhook
            )
        }
    }

    /**
    * Send a mattermost notification. The message is the Job name with build number
    * and build status if available, otherwise "Job not finished".
    *
    * @param message Any sort of message that should be included in the mattermost message
    */
    void sendMattermost(String message, Boolean simple = false) {
        String messageText = "Job '${script.env.JOB_NAME}' (${script.env.BUILD_NUMBER})"
        if (this.config?.mattermostWebhook) {
            String currentBuildStatus = currentBuildStatus()
            if (simple) {
                messageText += ''
            } else {
                messageText += " - ${currentBuildStatus}"
            }
            script.echo("Mattermost notification is not supported anymore")
        }
    }

    /**
    * Send an email notification. The subject is the Job name with build number and build status if
    * available, otherwise "Job not finished".
    *
    * @param message Any sort of message that should be included in the email
    */
    void sendEmail(String message, String subject = 'Pipeline event') {
        String messageSubject = subject
        String currentBuildStatus = currentBuildStatus()
        if (config?.emailRecipients && config?.emailbody) {
            if ( "${currentBuildStatus}" == 'SUCCESS') {
                String emailBody = config.emailbody
                script.emailext(
                    to: config.emailRecipients,
                    subject: messageSubject,
                    body: "${emailBody}",
                    mimeType: 'text/html'
                )
            } else {
                script.mail(
                    to: config.emailRecipients,
                    subject: messageSubject,
                    body: "${message}<br><br>Build URL: ${script.currentBuild.absoluteUrl}",
                    mimeType: 'text/html'
                )
            }
        } else if (config?.emailRecipients) {
            script.mail(
                to: config.emailRecipients,
                subject: messageSubject,
                body: "${message}<br><br>Build URL: ${script.currentBuild.absoluteUrl}",
                mimeType: 'text/html'
            )
        }
    }
}
