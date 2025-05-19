package com.cigna.linting

import com.cigna.jenkins_spock.JenkinsPipelineSpecification

public class MavenLintingSpec extends JenkinsPipelineSpecification {

    class Script {
        def env = [
                JOB_NAME: 'my/cool/job'
        ]
    }
    def script = new Script()

    def setup() {
        
        explicitlyMockPipelineStep('updateGitStatus')
        explicitlyMockPipelineStep('junit')
    }

    def """When maven is called, parameters are inserted correctly for invocation"""() {
        when:
            explicitlyMockPipelineVariable("MAVEN_SETTINGS")
            def build = new Linting(
                config: [
                    branchPattern: 'stuff',
                    lintingTypes: [
                        maven: mavenConfig
                    ]
                ],
                script: script,
            )
            build.runMaven()
        then:
            1 * getPipelineMock("sh")(expectedCommand)
            1 * getPipelineMock("configFileProvider.call")(*_)
            1 * getPipelineMock("configFile.call")(*_)
        where:
            mavenConfig << [
                [commandArgs: ['--1']],
                [commandArgs: ['--1', '--2']],
            ]
            expectedCommand << [
                "mvn -B -Dmaven.repo.local='/tmp/.cache/m2/repository' -gs Mock Generator for [MAVEN_SETTINGS] --1",
                "mvn -B -Dmaven.repo.local='/tmp/.cache/m2/repository' -gs Mock Generator for [MAVEN_SETTINGS] --1 --2",
            ]
    }

    def """When maven is called without commandArgs, an exception is thrown"""() {
        when:
            def build = new Linting(
                config: [
                    branchPattern: 'stuff',
                    lintingTypes: [
                        maven: [:]
                    ]
                ],
                script: script,
            )
            def issues = build.validate()
        then:
            assert issues == ['Missing required field commandArgs for maven lintingType']
    }

    def """When junit is specified for maven, commands are executed appropriately"""() {
        when:
            explicitlyMockPipelineVariable("MAVEN_SETTINGS")
            def build = new Linting(
                config: [
                    branchPattern: 'stuff',
                    lintingTypes: [
                        maven: [
                            commandArgs: ['--1'],
                            junit: junitConfig
                        ]
                    ]
                ],
                script: script,
            )
            build.runMaven()
        then:
            1 * getPipelineMock("sh")(
                "mvn -B -Dmaven.repo.local='/tmp/.cache/m2/repository' -gs Mock Generator for [MAVEN_SETTINGS] --1")
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

    def """When maven.junit is incorrect, produce the right validation errors"""() {
        when:
            def build = new Linting(
                config: [
                    branchPattern: 'stuff',
                    lintingTypes: [
                        maven: [
                            commandArgs: ['--1'],
                            junit: junitConfig
                        ]
                    ]
                ],
                script: script,
            )
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
