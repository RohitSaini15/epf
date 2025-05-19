package com.cigna.linting

import com.cigna.jenkins_spock.JenkinsPipelineSpecification

public class TerragruntLintingSpec extends JenkinsPipelineSpecification {

    class Script {
        def env = [
                JOB_NAME: 'my/cool/job'
        ]
    }
    def script = new Script()

    def setup() {
        
        explicitlyMockPipelineStep('updateGitStatus')
    }

    def """Terragrunt linting runs terragrunt hclfmt, terraform fmt, and conftest on modules"""() {

        when:
            def lintingPhase = new Linting(
                config: [
                    branchPattern: 'stuff',
                    lintingTypes: [
                        'terragrunt': [:],
                    ],
                ],
                script: script)
            
            lintingPhase.runTerragrunt()

        then:
            1 * getPipelineMock("sh")({it == 'terragrunt hclfmt --terragrunt-check'})
            1 * getPipelineMock("sh")({it == 'terraform fmt -recursive -check'})
    }
}