package com.evernorth.cloudnativebuild.pipeline.steps


import com.cigna.state.PipelineStateContext
import com.evernorth.cloudnativebuild.config.ReleaseConstants
import com.evernorth.cloudnativebuild.mocks.MockJenkins
import com.evernorth.cloudnativebuild.model.StepResult
import spock.lang.Specification

class CompleteReleaseStepSpec extends Specification {
    PipelineStateContext psc
    MockJenkins script
    def setup() {
        script = new MockJenkins()
        psc = new PipelineStateContext(script)
    }

    def "when XLR sends empty JSON fail pipeline"() {
        setup:
        String jsonPayload = new File("test/resources/xlr-callback-empty.json").text
        CompleteReleaseStep step = new CompleteReleaseStep(psc, script)
        when:
        StepResult release = step.execute(psc.globalModuleManager.stateManager(), jsonPayload)
        then:
        release.commandResult == StepResult.FAILURE
    }

    def "when XLR sends malformed JSON fail pipeline"() {
        setup:
        String jsonPayload = "this is not JSON, this is just text"
        CompleteReleaseStep step = new CompleteReleaseStep(psc, script)
        when:
        StepResult release = step.execute(psc.globalModuleManager.stateManager(), jsonPayload)
        then:
        release.commandResult == StepResult.FAILURE
    }


    def "when cut over success pipeline success"() {
        setup:
        String jsonPayload = new File("test/resources/xlr-callback-cutover-module.json").text
        MockJenkins jenkins = new MockJenkins()
        jenkins.env.mockEnvironment.CNP_RELEASE_INFO_FILE_NAME = "releaseInfo-2modules-phase1.json"
        CompleteReleaseStep step = new CompleteReleaseStep(psc, jenkins)
        // in thd sample data structure we use same results file name
        jenkins.addReadFileResult("cnp-deploy-pcf-results.json", "cnp-success-results.json")
        when:
        StepResult release = step.execute(psc.globalModuleManager.stateManager(), jsonPayload)
        then:
        release.commandResult == StepResult.SUCCESS
    }

    def "when release tool payload missing information fail step"() {
        setup:
        String jsonPayload = new File("test/resources/xlr-callback-missing-artifact.json").text
        script.env.mockEnvironment.CNP_RELEASE_INFO_FILE_NAME = "releaseInfo-2modules-phase1.json"
        CompleteReleaseStep step = new CompleteReleaseStep(psc, script)
        when:
        StepResult release = step.execute(psc.globalModuleManager.stateManager(), jsonPayload)
        then:
        release.commandResult == StepResult.FAILURE
    }

    def "when param isRollback perform rollback"() {
        setup:
        String jsonPayload = new File("test/resources/xlr-callback-cutover-module.json").text
        script.mockGlobalModuleManager = psc.globalModuleManager.stateManager()
        script.env.CNP_RELEASE_INFO_FILE_NAME = "releaseInfo-2modules-phase1.json"
        // in thd sample data structure we use same results file name
        script.addReadFileResult("cnp-deploy-pcf-results.json", "cnp-success-results.json")
        when:
        StepResult release = new CompleteReleaseStep(psc, script).execute(psc.globalModuleManager.stateManager(),
                jsonPayload,
                "https://artifactory.express-scripts.com/foo/bar/myzip.zip",
                "rollback")

        then:
        // subcommand should be rollback
        psc.globalModuleManager.stateManager().getContracts("DEPLOY", "cnp-deploy-pcf")[0].subCommand == ReleaseConstants.ROLLBACK_SUBCOMMAND
        release.commandResult == StepResult.SUCCESS

    }

}
