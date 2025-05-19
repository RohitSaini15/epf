package com.evernorth.cloudnativebuild.pipeline.steps


import com.cigna.state.PipelineStateContext
import com.evernorth.cloudnativebuild.data.PipelineConstants
import com.evernorth.cloudnativebuild.mocks.MockJenkins
import com.evernorth.cloudnativebuild.model.ModuleContract
import com.evernorth.cloudnativebuild.model.ModuleContractType
import com.evernorth.cloudnativebuild.model.StepResult
import com.evernorth.cloudnativebuild.pipeline.PipelineUtils
import spock.lang.Specification

class StartPreReleaseStepSpec extends Specification {
    PipelineStateContext psc
    MockJenkins script
    def setup() {
        script = new MockJenkins()
        psc = new PipelineStateContext(script)
    }
    def "when external job is built command success"() {
        setup:
        script.env.changeValue("BRANCH_NAME", "release/v123")
        ModuleContract contract = new ModuleContract(
            contractName: String.valueOf(ModuleContractType.TEST),
            commandName: "cnp-success.sh",
            moduleName: "cnp-success",
            image: "docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-core",
            logFileName: "cnp-success-results.json"
        )
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(verb: "deploy", buildSchedule: PipelineConstants.PRERELEASE_BUILD))
        psc.globalModuleManager.stateManager().add(contract)
        def startPreReleaseStep = new StartPreReleaseStep(script, psc)

        when:
        StepResult result = startPreReleaseStep.execute()
        then:
        result.commandResult == StepResult.SUCCESS
    }

    def "when external job is built command fails pipeline fails"() {
        setup:

        ModuleContract contract = new ModuleContract(
            contractName: String.valueOf(ModuleContractType.TEST),
            commandName: "cnp-jenkins-job-build-failed.sh",
            moduleName: "cnp-jenkins-job-build",
            image: "docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-core",
            logFileName: "cnp-jenkins-job-build-failure-results.json"
        )
        script.addReadFileResult("cnp-jenkins-job-build-failure-results.json")
        psc.globalModuleManager.stateManager().add(contract)
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(verb: "deploy", buildSchedule: PipelineConstants.PRERELEASE_BUILD))
        def startPreReleaseStep = new StartPreReleaseStep(script, psc)
        when:
        StepResult result = startPreReleaseStep.execute()
        then:
        result.commandResult == StepResult.FAILURE
    }

    def "when not release or hot fix prerelease is skipped"() {
        setup:
        script.env.changeValue("BRANCH_NAME", "feature/v123")
        ModuleContract contract = new ModuleContract(
            contractName: String.valueOf(ModuleContractType.TEST),
            commandName: "cnp-success.sh",
            moduleName: "cnp-success",
            image: "docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-core",
            logFileName: "cnp-success-results.json"
        )
        psc.globalModuleManager.stateManager().add(contract)
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(verb: "deploy", buildSchedule: PipelineConstants.PRERELEASE_BUILD))
        def startPreReleaseStep = new StartPreReleaseStep(script, psc)

        when:
        StepResult result = startPreReleaseStep.execute()
        then:
        result.commandResult == StepResult.SUCCESS
        result.commandOutput.skipped == true
    }
}