package com.evernorth.cloudnativebuild.service

import com.evernorth.cloudnativebuild.model.StepResult
import spock.lang.Specification

class ResultParserSpec extends Specification {

    def "when results file is missing or empty create empty StepResult"() {
        setup:
        String rawResults = ""
        when:
        StepResult result = ResultParser.parseScriptResult(rawResults)
        then:
        result == StepResult.empty()
    }

    def "When result is not success or failure set commandResult to SUCCESS"() {
        setup:
        String rawResults = """{
"commandResult":"SUCCESS1",
"commandOutput": [
]}"""
        when:
        StepResult result = ResultParser.parseScriptResult(rawResults)
        then:
        result.commandResult == StepResult.SUCCESS

    }

    def "When result is UNSTABLE"() {
        setup:
        String rawResults = """{
"commandResult":"UNSTABLE",
"commandOutput": [
]}"""
        when:
        StepResult result = ResultParser.parseScriptResult(rawResults)
        then:
        result.commandResult == StepResult.UNSTABLE

    }

    def "When raw results wrong JSON format return fail"() {
        setup:
        String rawResults = "{\"commandOutput\":\"\",\"commandSuccess\":\"true\",\"errors\":[\"null\"],\"artifactPath\":\"/tmp\"}"
        when:
        StepResult result = ResultParser.parseScriptResult(rawResults)
        then:
        result.commandResult == StepResult.FAILURE
        result.errors[0] == StepResult.COULD_NOT_PARSE
    }

    def "When raw results not JSON return fail"() {
        setup:
        String rawResults = "Hi i am not JSON"
        when:
        StepResult result = ResultParser.parseScriptResult(rawResults)
        then:
        result.commandResult == StepResult.FAILURE
        result.errors[0] == StepResult.COULD_NOT_PARSE
    }

    def "When result is success and has custom output"() {
        setup:
        String rawResults = """{
"commandResult":"SUCCESS",
"commandOutput": {
"var1":"value1"
,
"var2":"value2"
,
"var3":"value3 with more text"
}}"""
        when:
        StepResult result = ResultParser.parseScriptResult(rawResults)
        then:
        result.commandResult == StepResult.SUCCESS
        result.commandOutput.size() == 3
        result.commandOutput.var1 == "value1"

    }

    def "GetCommandResultValue(null,foo) returns empty string"(){
        when:
        def result = ResultParser.GetCommandResultValue(null,"foo")
        then:
        result==""
    }

    def "GetCommandResultValue(StepResult Failed,foo) returns empty string"(){
        setup:
        def stepResult = StepResult.failed("jenkins is a smoking pile of garbage")
        when:
        def result = ResultParser.GetCommandResultValue(stepResult,"foo")
        then:
        result==""
    }

    def "GetCommandResultValue(StepResult,foo) returns empty string when foo does not exist"(){
        setup:
        def stepResult = StepResult.empty()
        stepResult.commandOutput = ["notFoo":"cake"]
        when:
        def result = ResultParser.GetCommandResultValue(stepResult,"foo")
        then:
        result==""
    }

    def "GetCommandResultValue(StepResult,foo) returns string when foo does exist"(){
        setup:
        def stepResult = StepResult.empty()
        stepResult.commandOutput = ["foo":"cake"]
        when:
        def result = ResultParser.GetCommandResultValue(stepResult,"foo")
        then:
        result=="cake"
    }

    def "GetCommandResultValue(StepResult Failed,foo) returns string when foo as string when not string"(){
        setup:
        def stepResult = StepResult.empty()
        stepResult.commandOutput = ["foo":["foo", "bar"]]
        when:
        def result = ResultParser.GetCommandResultValue(stepResult,"foo")
        then:
        result=="[foo, bar]"
    }

    def "CreateDeferredValuePointer returns empty string when result is null"(){
        when:
        def valuePointer = ResultParser.CreateDeferredValuePointer(null,"foo")
        then:
        valuePointer==""
    }
    def "CreateDeferredValuePointer returns empty string when result.commandResult is SUCCESS"(){
        setup:
        def stepResult = StepResult.empty()
        when:
        def valuePointer = ResultParser.CreateDeferredValuePointer(stepResult,"foo")
        then:
        valuePointer==""
    }

    def "CreateDeferredValuePointer returns expected string with result.commandResult is DEFERRED"(){
        setup:
        def stepResult = new StepResult(commandResult: StepResult.DEFERRED, commandOutput: [stepIndex:"1"])
        String expected = "~$StepResult.DEFERRED|1|foo~"
        when:
        def valuePointer = ResultParser.CreateDeferredValuePointer(stepResult,"foo")
        then:
        valuePointer==expected
    }

    def "resolveDeferredValuePointer preserves values when not string"(){
        setup:
        def myMap = [
                pomPath:"pom.xml",
                generateDockerFile:true,
        ]
        PipelineStateManager manager = new PipelineStateManager()
        when:
        def actual = ResultParser.resolveDeferredValuePointer(true,manager)
        then:
        actual==true
    }
}


