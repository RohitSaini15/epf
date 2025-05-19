package com.cigna.deployment

import com.cigna.SinglePodTest
import com.cigna.common.phases.PodSelector
import com.cigna.common.scm.CommonGit
import com.evernorth.cloudnativebuild.mocks.JenkinsEnv

public class PatternDeploymentSpec extends SinglePodTest {
    def cloudName = 'test-cloud'
    def setup() {
        explicitlyMockPipelineStep('updateGitStatus')
        initScriptAndPsc()
    }

    def """When pattern configurations are set in the phase, the appropriate flags are set"""() {

        when:
        explicitlyMockPipelineVariable('AWS_FED_USERNAME')
        explicitlyMockPipelineVariable('AWS_FED_PASSWORD')
        explicitlyMockPipelineVariable('githubtoken')
        explicitlyMockPipelineVariable('plzArgs')
        def patternDeployment = new PatternDeployment(script: script, psc: psc)

        patternDeployment.config = [
                deploymentType : 'pattern',
                patternName    : patternName,
                patternVersion : patternVersion,
                action         : 'deploy',
                branchPattern  : 'stuff',
                sdlcEnvironment: 'thing',
                cloudName      : cloudName,
                terraform      :
                        [
                                version: terraformVersion
                        ],
                terragrunt     : [
                        version: terragruntVersion,
                        args   : [
                                init : "-no-color",
                                plan : "-no-color -var-file=pattern_config.tfvars",
                                apply: "-no-color -auto-approve -var-file=pattern_config.tfvars"
                        ]
                ],
                conftest       : [
                        policyVersion: conftestVersion
                ],
                awsFed         : [
                        credentialsID: 'stuff',
                ]
        ]
        simulatePodTemplate(psc, patternDeployment, cloudName)
        patternDeployment.parseConfiguration()

        then:
        assert patternDeployment.patternConfig.contains("-n ${patternName}")
        assert patternDeployment.patternConfig.contains("-v ${patternVersion}")
        assert patternDeployment.planConfig.contains("-f ${terraformVersion}")
        assert patternDeployment.planConfig.contains("-g ${terragruntVersion}")
        assert patternDeployment.planConfig.contains("-c ${conftestVersion}")

        where:
        patternName << ['pattern_a', 'pattern_b']
        patternVersion << ['1.0', '2.0']
        conftestVersion << ['3.0', '4.0']
        terraformVersion << ['5.0', '6.0']
        terragruntVersion << ['7.0', '8.0']

    }

    def """When the action parameter is toggled, the correct stages are run """() {

        when:
        explicitlyMockPipelineVariable('AWS_FED_USERNAME')
        explicitlyMockPipelineVariable('AWS_FED_PASSWORD')
        explicitlyMockPipelineVariable('githubtoken')
        explicitlyMockPipelineVariable('plzArgs')
        def patternDeployment = new PatternDeployment(script: script, psc: psc)

        patternDeployment.config = [
                deploymentType : 'pattern',
                patternName    : 'some_pattern',
                patternVersion : '1.0.0',
                action         : action,
                branchPattern  : 'stuff',
                sdlcEnvironment: 'thing',
                cloudName: cloudName,
                terraform      :
                        [
                                version: "1.0.3"
                        ],
                terragrunt     : [
                        version: "0.31.6",
                        args   : [
                                init : "-no-color",
                                plan : "-no-color -var-file=pattern_config.tfvars",
                                apply: "-no-color -auto-approve -var-file=pattern_config.tfvars"
                        ]
                ],
                conftest       : [
                        policyVersion: "0.48"
                ]
        ]
        patternDeployment.config += whereConfig
        simulatePodTemplate(psc, patternDeployment, cloudName)
        patternDeployment.deploy()

        then:
        if (action.equals("deploy")) {
            5 * getPipelineMock("stage")(*_)
        } else if (action.equals("plan")) {
            4 * getPipelineMock("stage")(*_)
        } else if (action.equals("destroy")) {
            1 * getPipelineMock("stage")(*_)
        }

        where:
        action << ['deploy', 'plan', 'deploy', 'destroy']
        whereConfig << [
                [awsFed: [credentialsId: 'test']],
                [:],
                [aws: [targetAccount: 'test', accountRoleName: 'test']],
                [aws: [targetAccount: 'test', accountRoleName: 'test']]
        ]
    }

    def """When withEnv is configured in the phase, validate if it is propagated to the execution environment"""() {
        when:
        explicitlyMockPipelineVariable('AWS_FED_USERNAME')
        explicitlyMockPipelineVariable('AWS_FED_PASSWORD')
        explicitlyMockPipelineVariable('githubtoken')
        explicitlyMockPipelineVariable('plzArgs')
        def patternDeployment = new PatternDeployment(script: script, psc: psc)

        patternDeployment.config = [
                deploymentType : 'pattern',
                patternName    : 'some_pattern',
                patternVersion : '1.0.0',
                action         : action,
                branchPattern  : 'stuff',
                sdlcEnvironment: 'thing',
                cloudName: cloudName,
                terraform      :
                        [
                                version: "1.0.3"
                        ],
                terragrunt     : [
                        version: "0.31.6",
                        args   : [
                                init : "-no-color",
                                plan : "-no-color -var-file=pattern_config.tfvars",
                                apply: "-no-color -auto-approve -var-file=pattern_config.tfvars"
                        ]
                ],
                conftest       : [
                        policyVersion: "0.48"
                ]
        ]
        patternDeployment.config += whereConfig
        simulatePodTemplate(psc, patternDeployment, cloudName)
        patternDeployment.deploy()

        then:
        if (action.equals("deploy"))  // deploy calls plan & deploy, so 2 calls
            2 * getPipelineMock("withEnv").call(actualEnv, _)
        else
            1 * getPipelineMock("withEnv").call(actualEnv, _)

        where:
        action << ['plan', 'deploy', 'clean', 'destroy']
        whereConfig << [
                [withEnv        : ['planvar=somevalue']],
                [:],
                [withEnv        : ['cleanvar=somevalue']],
                [:]
        ]
        actualEnv << [
              ['TERRAGRUNT_IAM_ROLE=', 'TF_LOG=ERROR', 'planvar=somevalue'],
              ['TERRAGRUNT_IAM_ROLE=', 'TF_LOG=ERROR'],
              ['TERRAGRUNT_IAM_ROLE=', 'TF_LOG=ERROR', 'cleanvar=somevalue'],
              ['TERRAGRUNT_IAM_ROLE=', 'TF_LOG=ERROR']
        ]
    }

}