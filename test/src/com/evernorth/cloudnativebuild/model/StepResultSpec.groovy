package com.evernorth.cloudnativebuild.model


import spock.lang.Specification

class StepResultSpec extends Specification {
    def "aggregate result when its empty"(){
        when:
        def result = StepResult.aggregateResults(null)
        then:
        result == StepResult.empty()
    }

    def "aggregate result failure when its any has failure"(){
        setup:
        List<StepResult> results = new ArrayList<StepResult>()
        results.add(0, new StepResult(commandResult: "SUCCESS", commandOutput: [message:"terraform is valid"]))
        results.add(1, new StepResult(commandResult: "FAILURE", commandOutput: [message:"unable to reach server"]))
        when:
        def result = StepResult.aggregateResults(results)
        then:
        result.commandResult==StepResult.FAILURE

    }

    def "aggregate shows 4 sub steps when 4 steps processed"(){
        setup:
        List<StepResult> results = new ArrayList<StepResult>()
        results.add(0, new StepResult(commandResult: "SUCCESS", commandOutput: [message:"terraform is valid"]))
        results.add(1, new StepResult(commandResult: "FAILURE", commandOutput: [message:"unable to reach server"]))
        results.add(2, new StepResult(commandResult: "SUCCESS", commandOutput: [message:"bad code"]))
        results.add(3, new StepResult(commandResult: "SUCCESS", commandOutput: [message:"worse code"]))
        when:
        def result = StepResult.aggregateResults(results)
        then:
        result.commandResult==StepResult.FAILURE
        result.subSteps==4

    }
}
