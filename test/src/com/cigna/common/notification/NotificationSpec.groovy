package com.cigna.common.notification


import com.cigna.jenkins_spock.JenkinsPipelineSpecification

class NotificationSpec extends JenkinsPipelineSpecification {
    class Script {
        def env = [JOB_NAME: "test job", BUILD_NUMBER: "1"]
        def currentBuild = [result: "SUCCESS", absoluteUrl: "http://build.com"]
    }
    def config = [:]

    def setup() {
        
        explicitlyMockPipelineVariable("WEBEX_TEAM_TOKEN")
        explicitlyMockPipelineStep('updateGitStatus')
        explicitlyMockPipelineStep('mattermostSend')
        explicitlyMockPipelineStep('emailext')
    }

    def "notifyWithAllMethods in parallel step has: sendEmail, sendMattermost, and sendTeams keys"() {
        when:
        def config = [emailRecipients  : "a@b.com",
                      mattermostWebhook: 'some-url',
                      teamsWebhook     : 'teams-url',
                      webexTeamsRoom   : 'webex-room']
        def script = new Script()
        def notification = new Notification(config: config, script: script)
        notification.notifyWithAllMethods("test message")

        then:
        // jenkins-spock executes all the parallel closures, so check the mocks of each parallel outcome
        // email
        1 * getPipelineMock('mail')(*_)
        // mattermost (echos it's not supported)
        1 * getPipelineMock('echo')({ it.startsWith('Mattermost') })
        // teams
        1 * getPipelineMock('httpRequest')({ it.url == 'teams-url'})
        // webex
        1 * getPipelineMock('httpRequest')({ it.url == 'https://webexapis.com/v1/messages'})
    }

    def "currentBuildStatus returns the currentBuild.result if truthy otherwise 'Job not finished'"() {
        when:
            def script = new Script()
            script.currentBuild.result = buildResult
            def notification = new Notification(config: config, script: script)
            def currentBuildStatus = notification.currentBuildStatus()

        then:
            if (buildResult) {
                currentBuildStatus == buildResult
            } else {
                currentBuildStatus == "Job not finished"
            }

        where:
            buildResult << ["SUCCESS", null]
    }

    def "getBuildStatusColor returns the build status color hex code if in color map otherwise hex code for 'GRAY'"() {
        when:
            def script = new Script()
            def notification = new Notification(config: config, script: script)
            def colorHexCode = notification.getBuildStatusColor(status)

        then:
            if (status) {
                colorHexCode == '#27E026'
            } else {
                colorHexCode == '#A9A9A9'
            }

        where:
            status << ["SUCCESS", null]
    }

    def """sendTeams calls httpRequest with the appropriate information when
            config.teamsWebhook is truthy"""() {
        when:
            def script = new Script()
            config.teamsWebhook = teamsWebhook
            def notification = new Notification(config: config, script: script)
            notification.sendTeams("test message")

        then:
            if (teamsWebhook) {
                1 * getPipelineMock("httpRequest")(
                    [
                        acceptType: 'APPLICATION_JSON',
                        contentType: 'APPLICATION_JSON',
                        httpMode: 'POST',
                        requestBody: '{"@type": "MessageCard", ' +
                            '"@context": "http://schema.org/extensions", ' +
                            '"themeColor": "#27E026", ' +
                            '"summary": "EPF Teams Card", ' +
                            '"sections": [' +
                                '{' +
                                    '"title": "test message", ' +
                                    '"text": "http://build.com"' +
                                '}' +
                            '], ' +
                            '"potentialAction": [' +
                                '{' +
                                    '"@type": "OpenUri", ' +
                                    '"name": "View", ' +
                                    '"targets": [' +
                                        '{' +
                                            '"os": "default", ' +
                                            '"uri": "http://build.com"' +
                                        '}' +
                                    ']' +
                                '}' +
                            ']' +
                        '}',
                        url: "http://test.com"
                    ]
                )
            } else {
                0 * getPipelineMock("httpRequest")(*_)
            }

        where:
            teamsWebhook << ["http://test.com", null]
    }

    def """sendWebex calls httpRequest with the appropriate information when
            config.webexTeamsRoom is truthy"""() {
        when:
            def script = new Script()
            config.webexTeamsRoom = webexTeamsRoom
            def notification = new Notification(config: config, script: script)
            notification.sendWebex("test message")

        then:
            if (webexTeamsRoom) {
                1 * getPipelineMock("httpRequest").call(*_)
                1 * getPipelineMock("string.call").call(['credentialsId':'webexTeamCredentials', 'variable':'WEBEX_TEAM_TOKEN'])
            } else {
                0 * getPipelineMock("httpRequest")(*_)
            }

        where:
            webexTeamsRoom << ["https://webexapis.com/v1/messages", null]
    }

    def """sendMattermost calls mattermostSend with the appropriate information when
            config.mattermostWebhook is truthy"""() {
        when:
            def script = new Script()
            config.mattermostWebhook = mattermostWebhook
            def notification = new Notification(config: config, script: script)
            notification.sendMattermost("test message")

        then:
            if (mattermostWebhook) {
                getPipelineMock("echo").call('Mattermost notification is not supported anymore')
            } else {
                0 * getPipelineMock("mattermostSend")(*_)
            }

        where:
            mattermostWebhook << ["http://test.com", null]
    }

    def """sendEmail calls emailext with the appropriate information when
            config.emailRecipients is truthy"""() {
        when:
            def script = new Script()
            config.emailRecipients = emailRecipients
            config.emailbody = emailbody
            def notification = new Notification(config: config, script: script)
            notification.sendEmail("test message", "Job 'test job' (1) - SUCCESS")

        then:
            if (emailbody && emailRecipients) {
                1 * getPipelineMock("emailext")(
                    [
                        to: "person@cigna.com",
                        subject: "Job 'test job' (1) - SUCCESS",
                        body: "Html Email Template",
                        mimeType: "text/html"
                    ]
                )
            } else if (emailRecipients) {
                1 * getPipelineMock("mail")(*_)
            }

        where:
            emailRecipients << ["person@cigna.com", null]
            emailbody << ["Html Email Template", null]
    }
}