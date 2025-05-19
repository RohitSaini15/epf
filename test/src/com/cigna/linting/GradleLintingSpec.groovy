package com.cigna.linting

import com.cigna.jenkins_spock.JenkinsPipelineSpecification

class GradleLintingSpec extends JenkinsPipelineSpecification {

    class Script {
        def env = [
            gradleUserHome: '.gradle'
        ]
    }

    def script = new Script()
    def setup() {
        
        explicitlyMockPipelineStep('updateGitStatus')
        explicitlyMockPipelineVariable("scm")
        explicitlyMockPipelineStep('junit')
    }

    def """When gradle is called, parameters are inserted correctly for invocation"""() {
        given:
            def build = new Linting(
                config: [
                    branchPattern: 'stuff',
                    lintingTypes: [
                        gradle: gradleConfig
                    ]
                ],
                script: script,
            )
            script.env.gradleUserHome = userHome
        when:    
            build.runGradle()
        then:
            1 * getPipelineMock("sh")("gradle --info${userHomePart}${commandPart}")
        where:
            gradleConfig << [
                [commandArgs: ['check'], gradleUserHome: 'override'],
                [:],
                [commandArgs: ['doStuff']]
            ]
            commandPart << [' check', ' clean processResources', ' doStuff']
            userHome << ['','.gradle', '']
            userHomePart << [' -g override', ' -g .gradle', '']
    }

    def """When gradle is called with wrapper enabled, parameters are inserted correctly for invocation and correct command is called"""() {

        given:
            def build = new Linting(
                    config: [
                            branchPattern: 'stuff',
                            lintingTypes: [
                                    gradle: gradleConfig
                            ]
                    ],
                    script: script,
            )
        when:
            build.runGradle()
        then:
            assert script.env.gradleUserHome == '.gradle'
            1 * getPipelineMock("sh")("chmod +x ./gradlew")
            1 * getPipelineMock("sh")("./gradlew --info -g .gradle check")
        where:
            gradleConfig << [
                    [commandArgs: ['check'], gradleUserHome: '.gradle', gradleWrapperScript: './gradlew']
            ]
    }

    def """When junit is specified for gradle, commands are executed appropriately"""() {
        given:
            def build = new Linting(
                config: [
                    branchPattern: 'stuff',
                    lintingTypes: [
                        gradle: [
                            commandArgs: ['--1'],
                            gradleUserHome: '.gradle',
                            junit: junitConfig
                        ]
                    ]
                ],
                script: script,
            )
        when:
            build.runGradle()
        then:
            1 * getPipelineMock("sh")(
                'gradle --info -g .gradle --1')
            1 * getPipelineMock("junit")(expectedJunitCommand)
        where:
            junitConfig << [
                [testResults: 'junit-report.xml'],
                [testResults: 'junit-report.xml', allowEmptyResults: true],
                [testResults: 'junit-report.xml', healthScaleFactor: 0.69],
                [testResults: 'junit-report.xml', allowEmptyResults: true, healthScaleFactor: 0.69],
            ]
            expectedJunitCommand << [
                ['testResults': 'junit-report.xml', 'allowEmptyResults': false, 'healthScaleFactor': 1.0],
                ['testResults': 'junit-report.xml', 'allowEmptyResults': true, 'healthScaleFactor': 1.0],
                ['testResults': 'junit-report.xml', 'allowEmptyResults': false, 'healthScaleFactor': 0.69],
                ['testResults': 'junit-report.xml', 'allowEmptyResults': true, 'healthScaleFactor': 0.69],
            ]
    }

    def """When gradle.junit is incorrect, produce the right validation errors"""() {
        given:
            def build = new Linting(
                config: [
                    branchPattern: 'stuff',
                    lintingTypes: [
                        gradle: [
                            commandArgs: ['check'],
                            gradleUserHome: '.gradle',
                            junit: junitConfig
                        ]
                    ]
                ],
                script: script,
            )
        when:
            def issues = build.validate()
        then:
            assert issues.size() == numberOfIssues
        where:
            junitConfig << [
                [config: 'is a map', but: 'is missing the required opt'],
                ['config', 'is', 'a', 'list'],
                [testResults: 'this is a correctly formed map']
            ]
            numberOfIssues << [1, 1, 0]
    }
}
