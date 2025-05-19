package com.evernorth.cloudnativebuild.pipeline.steps

import com.evernorth.cloudnativebuild.mocks.MockJenkins
import com.evernorth.cloudnativebuild.model.StepInvocation
import com.evernorth.cloudnativebuild.model.StepResult
import spock.lang.Specification

class RunScriptStepSpec extends Specification{
    def "when no script argument fail step"(){
        setup:
        MockJenkins jenkins = new MockJenkins()
        StepInvocation stepInvocation = new  StepInvocation(verb: "runScript")
        when:
        def result  = RunScriptStep.execute(jenkins,stepInvocation)
        then:
        result.commandResult == StepResult.FAILURE
    }

    def "when script throws error step fails"(){
        setup:
        MockJenkins jenkins = new MockJenkins()
        StepInvocation stepInvocation = new  StepInvocation(verb: "runScript",arguments: [script:"cause-an-error '{}'"])
        when:
        def result  = RunScriptStep.execute(jenkins,stepInvocation)
        then:
        result.commandResult == StepResult.FAILURE
    }
}
