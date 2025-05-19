package com.cigna.linting

import com.cigna.jenkins_spock.JenkinsPipelineSpecification

public class YAMLLintingSpec extends JenkinsPipelineSpecification {

    class Script {
        def env = [
                JOB_NAME: 'my/cool/job'
        ]
    }
    def script = new Script()

    def setup() {
        
        explicitlyMockPipelineStep('updateGitStatus')
    }

    def """When branchPattern or lintingTypes are missing from the config, an exception is thrown"""() {
        when:
        def yamlLinting = new Linting(
                config: configToVerify,
                script: script,
        )
        def issues = yamlLinting.validate()

        then:
        assert issues.size() == numberOfIssues

        where:
        configToVerify << [
            [:],
            [
                branchPattern: 'stuff',
            ],
            [
                lintingTypes: [
                    yamllint: [:]
                ],
            ],
            [
                branchPattern: 'stuff',
                lintingTypes: [
                    yamllint: [
                        commandArgs: ['-d relaxed'],
                        junit: [
                            allowEmptyResults: true
                        ]
                    ],
                ],
            ],
        ]
        numberOfIssues << [2, 1, 1, 0]
    }
}