package com.evernorth.cloudnativebuild.pipeline.steps

import com.cigna.state.PipelineStateContext
import com.evernorth.cloudnativebuild.mocks.MockJenkins
import com.evernorth.cloudnativebuild.model.StepResult
import com.evernorth.cloudnativebuild.service.PipelineStateManager
import spock.lang.Specification

class CompletePreReleaseStepSpec extends Specification{
    def "executes the steps from file successfully"(){
        setup:
        MockJenkins jenkins = new MockJenkins()
        PipelineStateContext psc = new PipelineStateContext(jenkins)
        jenkins.env.mockEnvironment.CNP_RELEASE_INFO_FILE_NAME="prerelease-release-info.json"
        CompletePreReleaseStep step = new CompletePreReleaseStep(jenkins, psc)
        PipelineStateManager manager = psc.globalModuleManager.stateManager()

        when:
        StepResult release = step.execute(manager, '{"deploymentPackagePath": "https://artifactory.express-scripts.com/artifactory/ci-snapshot-local/Cloud%20Native%20Pipeline/greetings-ui/deploymentPackage_greetings-ui-1.0.0-beta.30.zip"}')
        then:
        release.commandResult == StepResult.SUCCESS
        jenkins.shellCommands.size()==3
    }
    def "loads buildconfig from file successfully"(){
        setup:
        MockJenkins jenkins = new MockJenkins()
        PipelineStateContext psc = new PipelineStateContext(jenkins)
        PipelineStateManager manager = psc.globalModuleManager.stateManager()

        when:
        manager.loadConfigurationFromFile(new String(new File('test/resources/prerelease-release-info.json').readBytes()))
        then:
        manager.configuration.podCloud == 'kubernetes' //bcos its not available in the json, so defaulted
    }

    def "loads buildconfig from file successfully for customer"(){
        setup:
        MockJenkins jenkins = new MockJenkins()
        PipelineStateContext psc = new PipelineStateContext(jenkins)
        PipelineStateManager manager = psc.globalModuleManager.stateManager()
        when:
        String fileContents = new String(new File('test/resources/pbm-claim-cost-releaseinfo.json').readBytes())
        manager.loadConfigurationFromFile(fileContents)
        then:
        manager.configuration.podCloud == "pbm-claim-cost-openshift-devops1"
    }
}
