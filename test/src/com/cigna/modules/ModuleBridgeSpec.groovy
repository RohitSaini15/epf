package com.cigna.modules

import com.cigna.base.PipelineMetadata
import com.evernorth.cloudnativebuild.model.ModuleContract
import com.cigna.jenkins_spock.JenkinsPipelineSpecification

class ModuleBridgeSpec extends JenkinsPipelineSpecification {
    ModuleContract contract() {
        [
            image       : 'image',
            commandName : 'commandName',
            subCommand  : 'subCommand',
            contractName: 'contractName',
            stageName   : 'stageName',
            moduleName  : 'moduleName',
        ]
    }

    class Script {
        String JOB_NAME = 'orchestrators-folders/adjudicator/job/Adjudicator/job/main/job/10'
        def env = [
            JOB_NAME   : JOB_NAME,
            BRANCH_NAME: 'stuff',
            BUILD_URL  : 'https://orchestrator1.orchestrator-v2.sys.cigna.com/job/orchestrators-folders/job/adjudicator/job/Adjudicator/job/main/37',
            JENKINS_URL: 'https://orchestrator1.orchestrator-v2.sys.cigna.com/'
        ]
    }
    def script = new Script()

    def setup() {
        PipelineMetadata.Reset()
        
        explicitlyMockPipelineStep('updateGitStatus')

        explicitlyMockPipelineVariable("EPF_GITHUB_ACCESS_TOKEN")
    }

    def """verify credential parse"""() {
        when:
        ModuleBridge bridge = new ModuleBridge(script)
        script.env.STASH_ID = 'test-stash'

        def moduleConfig = [
            requiresUnStash: true,
            stashName      : 'test-stash',
            stashPattern   : '.*.groovy',
            stashExcludes  : 'target',
            credentials    : [
                [
                    type  : 'secretText',
                    id    : 'test-cred',
                    prefix: 'ARTIFACTORY',
                    env   : 'dev',
                    scope : 'OC',
                ],
                [
                    type  : 'secretFile',
                    id    : 'test-cred-file',
                    prefix: 'ARTIFACTORY',
                    env   : 'dev',
                    scope : 'EKS',
                ],
                [
                    id    : 'test-cred-default',
                    prefix: 'OC',
                    env   : 'stage',
                    scope : 'AWS',
                ],
            ],
        ]
        bridge.parse(contract(), moduleConfig)

        then:
        bridge.moduleContract.moduleName == 'moduleName'
        !bridge.moduleContract.requiresStash
        bridge.moduleContract.requiresUnStash
        bridge.moduleContract.credentials.size() == 3
        bridge.moduleContract.credentials[0].id == 'test-cred'
        bridge.moduleContract.credentials[2].type == 'usernamePassword'
    }
}
