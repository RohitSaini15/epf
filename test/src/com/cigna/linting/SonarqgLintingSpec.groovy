package com.cigna.linting

import com.cigna.common.request.CurlRequestor
import com.cigna.jenkins_spock.JenkinsPipelineSpecification

public class SonarqgLintingSpec extends JenkinsPipelineSpecification {

    class Script {
        def env = [
                JOB_NAME: 'my/cool/job'
        ]
    }
    def script = new Script()
    CurlRequestor curlRequestor = Mock()

    def setup() {
        explicitlyMockPipelineVariable("sonarToken")
        explicitlyMockPipelineStep('updateGitStatus')
        explicitlyMockPipelineStep('recordIssues')
    }

    def """When branchPattern or lintingTypes are missing from the config, an exception is thrown"""() {
        when:
            def sonarqgLinting = new Linting(
                config: configToVerify,
                script: script,
            )
            def issues = sonarqgLinting.validate()

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
                        sonarqg: [:]
                    ],
                ],
                [
                    branchPattern: 'stuff',
                    lintingTypes: [
                        sonarqg: [
                            sonarQube: [
				                apiCredentialId: 'Creds',
                                evaluateQg: 'true',
			                    sonarProjectKey: '',
                                sonarBranch: ''
                            ],
				            warningsNG: [
					            tools: [],
					            enabledForFailure: false,
					            qualityGates: []
				            ],
                        ],
                    ],
                ],
            ]
            numberOfIssues << [2, 1, 1, 0]
    }

    def """When linting type with sonarqg params, it triggers API call through curlRequestor"""() {
        given:
            curlRequestor.requestJsonWithLiteralCred(*_) >> [responseCode: 200, responseBody: []]

        when:
            String sonarContainer
            def sonarqgLinting = Spy(
                Linting,
                constructorArgs: [
                    [
                        config: [
                            branchPattern: 'stuff',
                            lintingTypes: [
                                sonarqg: [
                                    sonarQube: [
				                        apiCredentialId: 'Creds',
                                        evaluateQg: 'true',
			                            sonarProjectKey: 'sonarProjectKey',
                                        sonarBranch: 'sonarBranch'
                                    ],
			                        warningsNG: [
				                        tools: [],
				                        enabledForFailure: false,
				                        qualityGates: []
			                        ],
                                ],
                            ],
                        ],
                        script: script
                    ]
                ]
            )
            sonarqgLinting.curlRequestor = curlRequestor
            sonarqgLinting.usesWarningsNG() >> null
            sonarqgLinting.publishWarningsNG() >> null
            sonarqgLinting.runSonarQualityGate(sonarContainer)

        then:
            1 * curlRequestor.requestJsonWithLiteralCred(*_)  >> [responseCode: 200, responseBody: [:]]
    }
}
