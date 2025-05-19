package com.evernorth.cloudnativebuild.pipeline.steps

import com.cigna.SinglePodTest
import com.cigna.state.PipelineStateContext
import com.evernorth.cloudnativebuild.mocks.MockJenkins
import com.evernorth.cloudnativebuild.model.StepResult
import spock.lang.Specification

class FinalizeReleaseStepSpec extends Specification {
    PipelineStateContext psc
    MockJenkins script

    def setup() {
        script = new MockJenkins()
        psc = new PipelineStateContext(script)
    }

    def "when finalize module passes step passes"() {
        setup:
        script.addReadFileResult("releaseInfo.json", "releaseInfo-finalize.json")
        def modules = new LoadPipelineModulesStep(psc, script).loadModuleContracts(["release", "npm"])
        psc.globalModuleManager.stateManager().addList(modules)
        FinalizeReleaseStep step = new FinalizeReleaseStep(psc, script)
        when:
        StepResult result = step.execute([:], [:])
        then:
        result.commandResult == StepResult.SUCCESS

    }


}
