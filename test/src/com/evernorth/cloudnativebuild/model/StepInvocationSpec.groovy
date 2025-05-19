package com.evernorth.cloudnativebuild.model

import com.evernorth.cloudnativebuild.pipeline.PipelineUtils
import spock.lang.Specification

class StepInvocationSpec extends Specification{
    def "StepInvocation toString returns string"(){
        setup:
        StepInvocation stepInvocation = PipelineUtils.CreateInvocation(verb: "findme")
        when:
        String result = stepInvocation.toString()
        then:
        result.indexOf("findme")> -1
    }

    def "When compare 2 steps the one with highest order is greater"(){
        setup:
        StepInvocation step1 = PipelineUtils.CreateInvocation(order: 1, verb: "build")
        StepInvocation step2 = PipelineUtils.CreateInvocation(order: 2, verb: "build")
        when:
        int val = step2 <=> step1
        int val2 = step1 <=> step2
        boolean isGreater = step1<step2
        then:
        val==1
        val2==-1
        isGreater
    }

    def "When sort called on list of StepInvocations sorted by order low to high"(){
        setup:
        StepInvocation step1 = PipelineUtils.CreateInvocation(order: 1, verb: "build")
        StepInvocation step2 = PipelineUtils.CreateInvocation(order: 2, verb: "build")
        StepInvocation step3 = PipelineUtils.CreateInvocation(order: 3, verb: "build")
        StepInvocation step4 = PipelineUtils.CreateInvocation(order: 4, verb: "build")
        def stepList = [step3,step2,step1,step4]
        when:
        def sortedList = stepList.sort()
        then:
        sortedList[0].order==1
        sortedList[3].order==4
    }
}
