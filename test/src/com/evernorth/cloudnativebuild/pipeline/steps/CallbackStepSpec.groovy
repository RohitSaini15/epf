package com.evernorth.cloudnativebuild.pipeline.steps

import com.cigna.state.PipelineStateContext
import com.evernorth.cloudnativebuild.mocks.MockJenkins
import com.evernorth.cloudnativebuild.model.StepResult
import spock.lang.Specification

class CallbackStepSpec extends Specification {

    MockJenkins script
    PipelineStateContext psc

    def setup() {
        script = new MockJenkins()
        psc = new PipelineStateContext(script)
    }

    def "Callback is started by unauthorized user fails pipeline"() {
        given:
        script.causeShortDescription = null
        CallbackStep step = new CallbackStep(psc, script)
        when:
        StepResult result = step.execute()
        then:
        result.commandResult == StepResult.FAILURE
    }

    def "Callback started by upstream and isCandidate executes candidate"() {
        setup:
        script.causeShortDescription = "upstream"
        script.env.changeValue("CNP_LOG_LEVEL", "INFO")
        script.params.isCandidate = true
        script.params.deploymentInfo = """{}"""
        CallbackStep step = new CallbackStep(psc, script)
        when:
        StepResult result = step.execute()
        then:
        result.commandResult == StepResult.SUCCESS
        script.consoleMessages.contains("${CallbackStep.STAGE_JOB}")
    }

    def "Callback is rollback executes rollback step successfully"() {
        setup:
        script.causeUserId = "xlrUser"
        script.env.changeValue("CNP_LOG_LEVEL", "INFO")
        CallbackStep step = new CallbackStep(psc, script)

        script.params.isRollback = true
        when:
        StepResult result = step.execute()
        then:
        result.commandResult == StepResult.SUCCESS
        // needs to be gString for compare to work
        script.consoleMessages.contains("${CallbackStep.ROLL_BACK_JOB}")
    }

    def "Callback is finalize executes finalize step successfully"() {
        given:
        script.addReadFileResult("releaseInfo.json", "releaseInfo-finalize.json")
        script.causeUserId = "xlrUser"
        script.env.changeValue("CNP_LOG_LEVEL", "INFO")
        CallbackStep step = new CallbackStep(psc, script)
        script.params.isFinalize = true
        when:
        StepResult result = step.execute()
        then:
        result.commandResult == StepResult.SUCCESS
        // needs to be gString for compare to work
        script.consoleMessages.contains("${CallbackStep.FINALIZE_JOB}")
    }

    def "Callback is release executes release step successfully"() {
        setup:
        script.causeUserId = "xlrUser"
        script.params.deploymentInfo = """{"artifact":"https://artifactory.express-scripts.com/foo/bar"}"""
        script.env.changeValue("CNP_LOG_LEVEL", "INFO")
        CallbackStep step = new CallbackStep(psc, script)
        when:
        StepResult result = step.execute()
        then:
        // needs to be gString for compare to work
        script.consoleMessages.contains("${CallbackStep.RELEASE_JOB}")
    }

    def "Callback is release and release step fails results in unstable build"() {
        setup:
        script.causeUserId = "xlrUser"
        script.env.changeValue("CNP_LOG_LEVEL", "INFO")
        CallbackStep step = new CallbackStep(psc, script)
        when:
        StepResult result = step.execute()
        then:
        result.commandResult == StepResult.UNSTABLE
        // needs to be gString for compare to work
        script.consoleMessages.contains("${CallbackStep.RELEASE_JOB}")
    }

    def "test authorized user"() {
        setup:
        script.causeUserId = "accounts\\ei0733"
        CallbackStep step = new CallbackStep(psc, script)
        when:
        def flag = step.isAuthorizedUser()
        then:
        flag
    }

    def '''that callback job can deserialize and schedule await verbs'''() {
        given:
        def fileContents = new File("test/resources/preRelease-withAwait.json").text
        // inject job params expected by callback handler
        script.params = [deploymentInfo: fileContents, isCandidate: true]
        CallbackStep step = new CallbackStep(psc, script)
        script.with {
            addReadFileResultFromString('cnp-release-util-results.json',
                '{ "commandResult": "SUCCESS", "commandOutput" : { "publishUrl" : "aurl" , "archiveUrl" : "anotherurl"} }')
            addReadFileResultFromString('releaseInfo.json', fileContents)
        }

        when:
        step.execute()
        then:
        psc.globalModuleManager.stateManager().pipelineSteps.size() == 3
        psc.globalModuleManager.stateManager().pipelineSteps[0].verb == 'awaitapproval'
    }
}
