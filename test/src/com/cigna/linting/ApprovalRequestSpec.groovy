package com.cigna.linting

import com.cigna.jenkins_spock.JenkinsPipelineSpecification

public class ApprovalRequestSpec extends JenkinsPipelineSpecification {

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
        def approvalrequestLinting = new Linting(
            config: configToVerify,
            script: script,
        )

        def issues = approvalrequestLinting.validate()

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
                    approvalrequest: [:]    
                ],
            ],
            [
                branchPattern: 'stuff',
                lintingTypes: [
                    approvalrequest: [
                        id: 'approval',
                        message: ''
                    ],
                ],
            ],

        ]
        numberOfIssues << [2, 1, 1, 0]
    }
}