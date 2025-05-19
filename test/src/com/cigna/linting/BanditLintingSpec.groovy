package com.cigna.linting

import com.cigna.jenkins_spock.JenkinsPipelineSpecification

public class BanditLintingSpec extends JenkinsPipelineSpecification {

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

    def """When bandit is called, parameters are inserted correctly for invocation"""() {
        when:
            def build = new Linting(
                config: [
                    branchPattern: 'stuff',
                    lintingTypes: [
                        bandit: banditConfig
                    ]
                ],
                script: script
            )
            build.runBandit()
        then:
            1 * getPipelineMock("sh")({it =~ expectedBuildCommand})
            1 * getPipelineMock("sh")({it =~ expectedRunCommand})
        where:
            banditConfig << [
                [:],
                [commandArgs: ['--1', '--2']],
                [targets: ['pattern1', 'pattern2']],
                [commandArgs: ['--1', '--2'], targets: ['pattern']],
                [versionSpec: '==1.2.3'],
                [versionSpec: '==1.2.3', commandArgs: ['--1', '--2']],
                [versionSpec: '==1.2.3', targets: ['pattern1', 'pattern2']],
                [versionSpec: '==1.2.3', commandArgs: ['--1', '--2'], targets: ['pattern']],
            ]
            expectedBuildCommand << [
                'pip install --upgrade pip && pip install bandit',
                'pip install --upgrade pip && pip install bandit',
                'pip install --upgrade pip && pip install bandit',
                'pip install --upgrade pip && pip install bandit',
                'pip install --upgrade pip && pip install bandit==1.2.3',
                'pip install --upgrade pip && pip install bandit==1.2.3',
                'pip install --upgrade pip && pip install bandit==1.2.3',
                'pip install --upgrade pip && pip install bandit==1.2.3',
            ]
            expectedRunCommand << [
                'bandit  -r .',
                'bandit --1 --2 -r .',
                'bandit  pattern1 pattern2',
                'bandit --1 --2 pattern',
                'bandit  -r .',
                'bandit --1 --2 -r .',
                'bandit  pattern1 pattern2',
                'bandit --1 --2 pattern',
            ]
    }

    def """When bandit.junit is not a Map, produce a validation error"""() {
        when:
            def build = new Linting(
                config: [
                    branchPattern: 'stuff',
                    lintingTypes: [
                        bandit: [
                            junit: junitConfig
                        ]
                    ]
                ],
                script: script
            )
            def issues = build.validate()
        then:
            assert issues.size() == numberOfIssues
        where:
            junitConfig << [
                [config: 'is a map'],
                ['config', 'is', 'a', 'list'],
            ]
            numberOfIssues << [0, 1]
    }

    def """When junit is specified for bandit, commands are executed appropriately"""() {
        when:
            def build = new Linting(
                config: [
                    branchPattern: 'stuff',
                    lintingTypes: [
                        bandit: [
                            junit: junitConfig
                        ]
                    ]
                ],
                script: script
            )
            build.runBandit()
        then:
            1 * getPipelineMock("sh")('bandit --format xml --output junit-report.xml -r .')
            1 * getPipelineMock("junit")(expectedJunitCommand)
        where:
            junitConfig << [
                [:],
                [allowEmptyResults: true],
                [healthScaleFactor: 0.69],
                [allowEmptyResults: true, healthScaleFactor: 0.69],
            ]
            expectedJunitCommand << [
                ['testResults': 'junit-report.xml', 'allowEmptyResults': false, 'healthScaleFactor': 1.0],
                ['testResults': 'junit-report.xml', 'allowEmptyResults': true, 'healthScaleFactor': 1.0],
                ['testResults': 'junit-report.xml', 'allowEmptyResults': false, 'healthScaleFactor': 0.69],
                ['testResults': 'junit-report.xml', 'allowEmptyResults': true, 'healthScaleFactor': 0.69],
            ]
    }
}
