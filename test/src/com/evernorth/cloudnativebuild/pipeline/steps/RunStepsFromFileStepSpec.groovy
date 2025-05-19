package com.evernorth.cloudnativebuild.pipeline.steps


import com.cigna.common.exception.ErrorStepException
import com.cigna.common.scm.CommonGit
import com.cigna.modules.Module
import com.cigna.modules.ModuleBridge
import com.cigna.state.PipelineStateContext
import com.evernorth.cloudnativebuild.data.PipelineConstants
import com.evernorth.cloudnativebuild.mocks.MockJenkins
import com.evernorth.cloudnativebuild.model.ReleaseInfo
import com.evernorth.cloudnativebuild.model.StepInvocation
import com.evernorth.cloudnativebuild.model.StepResult
import com.evernorth.cloudnativebuild.model.logging.LogLevel
import com.evernorth.cloudnativebuild.pipeline.PipelineUtils
import groovy.json.JsonBuilder
import spock.lang.Specification

class RunStepsFromFileStepSpec extends Specification {

    PipelineStateContext psc
    MockJenkins script
    def setup() {
        script = new MockJenkins()
        psc = new PipelineStateContext(script)
        CommonGit cg = Mock()
        psc.podSelector.commonGit = cg
    }

    def "when releaseInfo missing pipeline fails"() {
        given:

        script.env.changeValue("CNP_RELEASE_INFO_FILE_NAME", "releaseInfo-missing.json")
        RunStepsFromFileStep step = new RunStepsFromFileStep(psc, script)
        when:
        step.loadStepsFromFile()
        step.execute()
        then:
        script.currentBuild.result == PipelineConstants.BUILD_RESULT_FAILURE
    }

    def "when releaseInfo has no modules pipeline fails"() {
        given:
        script.env.changeValue("CNP_RELEASE_INFO_FILE_NAME", "releaseInfo-noModules.json")
        RunStepsFromFileStep step = new RunStepsFromFileStep(psc, script)

        when:
        step.loadStepsFromFile()
        step.execute()
        then:
        script.currentBuild.result == PipelineConstants.BUILD_RESULT_FAILURE
    }

    def "when releaseInfo has no steps pipeline fails"() {
        given:
        RunStepsFromFileStep step = new RunStepsFromFileStep(psc, script)
        script.env.mockEnvironment.CNP_RELEASE_INFO_FILE_NAME = "releaseInfo-NoSteps.json"
        when:
        step.loadStepsFromFile()
        step.execute()
        then:
        script.currentBuild.result == PipelineConstants.BUILD_RESULT_FAILURE
    }

    def "when releaseInfo has 6 modules 6 modules loaded"() {
        given:
        RunStepsFromFileStep step = new RunStepsFromFileStep(psc, script)
        script.env.mockEnvironment.CNP_RELEASE_INFO_FILE_NAME = "releaseInfo-2modules-phase1.json"
        int expected = 6
        when:
        step.loadStepsFromFile()
        step.execute()
        then:
        psc.globalModuleManager.stateManager().getContracts().size() == expected
        psc.globalModuleManager.stateManager().getContractsForContactType("DEPLOY").size() == 2
    }

    def "when releaseInfo has 5 modules, 5 modules loaded and one deploy step, so no options present"() {
        given:
        script.env.mockEnvironment.CNP_LOG_LEVEL = 'TRACE'
        script.env.mockEnvironment.CNP_RELEASE_INFO_FILE_NAME = "releaseInfo-1modules-phase1.json"
        RunStepsFromFileStep step = new RunStepsFromFileStep(psc, script)
        int expected = 5
        when:
        step.loadStepsFromFile()
        step.execute()
        then:
        psc.globalModuleManager.stateManager().getContracts().size() == expected
        psc.globalModuleManager.stateManager().getContractsForContactType("DEPLOY").size() == 1
    }

    def "when 5 modules loaded and one deploy step, so no options present and rollback initiated"() {
        given:
        RunStepsFromFileStep step = new RunStepsFromFileStep(psc, script)
        script.env.mockEnvironment.CNP_RELEASE_INFO_FILE_NAME = "releaseInfo-1modules-phase1.json"
        int expected = 5
        when:
        step.loadStepsFromFile()
        step.execute("rollback")
        then:
        psc.globalModuleManager.stateManager().getContracts().size() == expected
        psc.globalModuleManager.stateManager().getContractsForContactType("DEPLOY").size() == 1
    }

    def "when 6 modules loaded and one deploy step & 2 deploy contracts, no options then 6 contracts and 2 steps"() {
        given:
        RunStepsFromFileStep step = new RunStepsFromFileStep(psc, script)
        script.env.mockEnvironment.CNP_RELEASE_INFO_FILE_NAME = "releaseInfo-2modules-phase1-nooptions.json"
        int expected = 6
        when:
        step.loadStepsFromFile()
        step.execute()
        then:
        psc.globalModuleManager.stateManager().getContracts().size() == expected
        psc.globalModuleManager.stateManager().getContractsForContactType("DEPLOY").size() == 2
    }

    def "when releaseInfo has 2 pre release steps and 9 modules loaded"() {
        given:
        RunStepsFromFileStep step = new RunStepsFromFileStep(psc, script)
        script.env.mockEnvironment.CNP_RELEASE_INFO_FILE_NAME = "greetings-pre-release-info.json"
        when:
        step.loadStepsFromFile()
        step.execute(PipelineConstants.PRERELEASE_BUILD)
        then:
        psc.globalModuleManager.stateManager().getContracts().size() == 9
        psc.globalModuleManager.stateManager().getContractsForContactType("DEPLOY").size() == 2
        psc.globalModuleManager.stateManager().getFirstContractForContactType("DEPLOY").allowNonStandardDeployment
    }

    def "when releaseInfo has 3 steps 3 steps are loaded"() {
        given:
        script.env.changeValue("CNP_RELEASE_INFO_FILE_NAME", "releaseInfo-2modules-phase1.json")
        RunStepsFromFileStep step = new RunStepsFromFileStep(psc, script)
        when:
        step.loadStepsFromFile()
        step.execute()
        then:
        psc.globalModuleManager.stateManager().getPipelineSteps().size() == 3
    }

    def "when releaseInfo has 3 steps with 2 completed execution plan has 1 step"() {
        given:
        RunStepsFromFileStep step = new RunStepsFromFileStep(psc, script)
        script.env.mockEnvironment.CNP_RELEASE_INFO_FILE_NAME = "releaseinfo-2modules-phase3.json"
        script.mockShellCommand.addModuleSimulation("cnp-deploy-aws.sh")
        when:
        step.loadStepsFromFile()
        StepResult result = step.execute()
        then:
        result.subSteps == 1
    }

    def "when no buildConfiguration use defaults"() {
        given:
        script.env.CNP_LOG_LEVEL = ""
        script.env.mockEnvironment.remove("CNP_LOG_LEVEL")
        script.env.mockEnvironment.CNP_RELEASE_INFO_FILE_NAME = "releaseInfo-no-build-config.json"
        RunStepsFromFileStep step = new RunStepsFromFileStep(psc, script)
        when:
        step.loadStepsFromFile()
        step.execute()
        then:
        psc.globalModuleManager.stateManager().configuration.logLevel == LogLevel.INFO
    }

    def "when buildConfiguration use value from file"() {
        given:
        script.env.CNP_LOG_LEVEL = ""
        script.env.mockEnvironment.remove("CNP_LOG_LEVEL")

        // the CNP_LOG_LEVEL is set to trace in the file
        script.env.mockEnvironment.CNP_RELEASE_INFO_FILE_NAME = "releaseInfo-2modules-phase1.json"
        RunStepsFromFileStep step = new RunStepsFromFileStep(psc, script)
        when:
        step.loadStepsFromFile()
        step.execute()
        then:
        psc.globalModuleManager.stateManager().configuration.logLevel == LogLevel.TRACE
    }

    def "when buildConfiguration has simulateOnly true print commandline but dont execute"() {
        given:
        script.env.CNP_SIMULATE_ONLY = ""
        script.env.mockEnvironment.remove("CNP_SIMULATE_ONLY")

        // the CNP_SIMULATE_ONLY is set to trace in the file
        script.env.mockEnvironment.CNP_RELEASE_INFO_FILE_NAME = "releaseinfo-2modules-phase3.json"
        RunStepsFromFileStep step = new RunStepsFromFileStep(psc, script)
        when:
        step.loadStepsFromFile()
        step.execute()
        then:
        psc.globalModuleManager.stateManager().configuration.simulateOnly
    }

    def "when stepInvocation has 3 awaitApprovals three pods"() {
        given:
        script.env.setProperty("BRANCH_NAME", "develop")
        LoadPipelineModulesStep loadModStep = new LoadPipelineModulesStep(psc, script)
        def contacts = loadModStep.loadModuleContracts(["maven", "pcf"])
        psc.globalModuleManager.stateManager().addList(contacts)
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(verb: "build", buildSchedule: PipelineConstants.CURRENT_BUILD))
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(verb: "awaitApproval", buildSchedule: PipelineConstants.CURRENT_BUILD))
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(verb: "deploy", buildSchedule: PipelineConstants.CURRENT_BUILD))
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(verb: "awaitApproval", buildSchedule: PipelineConstants.CURRENT_BUILD))
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(verb: "deploy", buildSchedule: PipelineConstants.CURRENT_BUILD))
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(verb: "awaitApproval", buildSchedule: PipelineConstants.CURRENT_BUILD))
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(verb: "deploy", buildSchedule: PipelineConstants.CURRENT_BUILD))
        RunStepsFromFileStep step = new RunStepsFromFileStep(psc, script)
        when:
        List<StepInvocation> steps = step.createExecutionPlan(PipelineConstants.CURRENT_BUILD)
        then:
        // all steps in plan
        steps.size() == 7
        // pod added for each awaitApproval
        steps.max { it.podIndex }.podIndex == 3
        // steps after each await assign correct podIndex
        steps[6].podIndex == 3

    }

    def "when not deployable branch deploy and cutover steps removed"() {
        given:
        script.env.setProperty("BRANCH_NAME", "feature/somefeature")
        LoadPipelineModulesStep loadModStep = new LoadPipelineModulesStep(psc, script)
        def contacts = loadModStep.loadModuleContracts(["maven", "pcf", "quality"])
        psc.globalModuleManager.stateManager().addList(contacts)
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(verb: "build", buildSchedule: PipelineConstants.CURRENT_BUILD))
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(verb: "runSecurityScan", buildSchedule: PipelineConstants.CURRENT_BUILD))
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(verb: "runQualityCheck", buildSchedule: PipelineConstants.CURRENT_BUILD))
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(verb: "awaitApproval", buildSchedule: PipelineConstants.CURRENT_BUILD))
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(verb: "deploy", buildSchedule: PipelineConstants.CURRENT_BUILD))
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(verb: "awaitApproval", buildSchedule: PipelineConstants.CURRENT_BUILD))
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(verb: "cutover", buildSchedule: PipelineConstants.CURRENT_BUILD))
        RunStepsFromFileStep step = new RunStepsFromFileStep(psc, script)
        when:
        List<StepInvocation> steps = step.createExecutionPlan(PipelineConstants.CURRENT_BUILD)
        then:
        // all deploy and cutover steps removed
        steps.size() == 6
    }

    def "when 2 preflight checks then execution plan has 2 preflight check steps"() {
        given:
        RunStepsFromFileStep step = new RunStepsFromFileStep(psc, script)
        LoadPipelineModulesStep loadModStep = new LoadPipelineModulesStep(psc, script)
        def contacts = loadModStep.loadModuleContracts(["common"])
        psc.globalModuleManager.stateManager().addList(contacts)
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(verb: "preflightCheckVerification", buildSchedule: PipelineConstants.CURRENT_BUILD))
        when:
        List<StepInvocation> steps = step.createExecutionPlan(PipelineConstants.CURRENT_BUILD)
        then:
        steps.size() == 2
    }

    def "when module declares post execution event step is added to execution plan"() {
        given:
        RunStepsFromFileStep step = new RunStepsFromFileStep(psc, script)
        LoadPipelineModulesStep loadModStep = new LoadPipelineModulesStep(psc, script)
        def contacts = loadModStep.loadModuleContracts(["pcf", "newRelic"])
        psc.globalModuleManager.stateManager().addList(contacts)
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(verb: "deploy", buildSchedule: PipelineConstants.CURRENT_BUILD))
        when:
        List<StepInvocation> steps = step.createExecutionPlan(PipelineConstants.CURRENT_BUILD)
        then:
        steps.size() == 1
    }

    def "when step uses value pointer it can resolve from result of step"() {
        script.env.setProperty("BRANCH_NAME", "feature/somefeature")
        psc.globalModuleManager.stateManager().addPipelineStep(
                PipelineUtils.CreateInvocation(
                        verb: "build",
                        buildSchedule: PipelineConstants.CURRENT_BUILD,
                        result: new StepResult(
                                commandOutput: [
                                        foo       : "value",
                                        bar       : "value2",
                                        publishUrl: ["https://artifactory.express-scripts.com/artifactory/libs-snapshot-local/com/esrx/devops-testapps-SpringPcf/1.3.44-SNAPSHOT/devops-testapps-SpringPcf-1.3.44-SNAPSHOT.jar", "https://artifactory.express-scripts.com/artifactory/libs-snapshot-local/com/esrx/devops-testapps-SpringPcf/1.3.44-SNAPSHOT/devops-testapps-SpringPcf-1.3.44-SNAPSHOT.pom"]
                                ]
                        )
                )
        )
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(verb: "securityScan", buildSchedule: PipelineConstants.CURRENT_BUILD))
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(verb: "qualityCheck", buildSchedule: PipelineConstants.CURRENT_BUILD))
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(verb: "awaitApproval", buildSchedule: PipelineConstants.CURRENT_BUILD))
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(verb: "deploy", buildSchedule: PipelineConstants.CURRENT_BUILD))
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(verb: "awaitApproval", buildSchedule: PipelineConstants.CURRENT_BUILD))
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(verb: "cutover", buildSchedule: PipelineConstants.CURRENT_BUILD))
        RunStepsFromFileStep step = new RunStepsFromFileStep(psc, script)
        // this tests for a well formed value pointer being found
        // normal value is preserved
        // 2 forms of malformed value pointers (edge cases)
        // when no invocation with step index exists
        // no matching value in command output
        Map arguments = [
                "pointerArg": "~$StepResult.DEFERRED|1|foo~",// gets value from command result of matching invocation
                arg         : "somevalue",// keep value
                "artifact"  : "~$StepResult.DEFERRED|1|publishUrl~",// added array to string
                "combined"  : "~$StepResult.DEFERRED|1|foo~~$StepResult.DEFERRED|1|bar~",// added values combined
                "combined2" : "othercontent/~$StepResult.DEFERRED|1|foo~",// added values combined,
                emptyString : "",// keep
                falseValue  : false,//keep
        ]
        when:
        Map output = step.updateArguments(arguments)
        then:
        output.size() == 7
        output["emptyString"] == ""
        output["falseValue"] == false
        output["pointerArg"] == "value"
        output["combined"] == "valuevalue2"
        output["combined2"] == "othercontent/value"
        output["arg"] == "somevalue"
        output["artifact"] == """[https://artifactory.express-scripts.com/artifactory/libs-snapshot-local/com/esrx/devops-testapps-SpringPcf/1.3.44-SNAPSHOT/devops-testapps-SpringPcf-1.3.44-SNAPSHOT.jar, https://artifactory.express-scripts.com/artifactory/libs-snapshot-local/com/esrx/devops-testapps-SpringPcf/1.3.44-SNAPSHOT/devops-testapps-SpringPcf-1.3.44-SNAPSHOT.pom]"""
    }

    def "when step uses value pointer and but cant resolve throw exception"() {
        script.env.setProperty("BRANCH_NAME", "feature/somefeature")
        psc.globalModuleManager.stateManager().addPipelineStep(
                PipelineUtils.CreateInvocation(
                        verb: "build",
                        buildSchedule: PipelineConstants.CURRENT_BUILD,
                        result: new StepResult(
                                commandOutput: [
                                        foo: "value",
                                        bar: "value2",
                                ]
                        )
                )
        )
        RunStepsFromFileStep step = new RunStepsFromFileStep(psc, script)
        Map arguments = [
                notfound : "~$StepResult.DEFERRED|30|bar~",// not added
                "noMatch": "~$StepResult.DEFERRED|1|doesnotexist~",// not added
        ]
        when:
        step.updateArguments(arguments)
        then:
        thrown ErrorStepException
    }

    def "when step invocations are split into sub-plans they are placed in correct order"() {
        given:
        script.env.setProperty("BRANCH_NAME", "feature/somefeature")
        LoadPipelineModulesStep loadModStep = new LoadPipelineModulesStep(psc, script)
        def contacts = loadModStep.loadModuleContracts(["k8s", "cnpbundle"])
        psc.globalModuleManager.stateManager().addList(contacts)
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(verb: "createPackages", buildSchedule: PipelineConstants.CURRENT_BUILD, arguments: [packageRoot: "Modules/cnp-build-fake", skipIfExists: true]))
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(verb: "createPackages", buildSchedule: PipelineConstants.CURRENT_BUILD, arguments: [packageRoot: "Modules/cnp-deploy-fake", skipIfExists: true]))
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(verb: "createPackages", buildSchedule: PipelineConstants.CURRENT_BUILD, arguments: [packageRoot: "Modules/cnp-cutover-fake", skipIfExists: true]))
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(verb: "createPackages", buildSchedule: PipelineConstants.CURRENT_BUILD, arguments: [packageRoot: "Modules/cnp-post-deploy-fake", skipIfExists: true]))
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(verb: "awaitApproval", buildSchedule: PipelineConstants.CURRENT_BUILD, arguments: [time: 10, unit: 'MINUTES', message: 'Do you want to build an image?']))
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(verb: "buildImage", buildSchedule: PipelineConstants.CURRENT_BUILD, arguments: [contextPath               : "Dockerfiles/cnp-docker-fake",
                                                                                                                                                                              buildOnNonDeployableBranch: true,
                                                                                                                                                                              registry                  : "docker-dev.artifactory.express-scripts.com",
                                                                                                                                                                              skipIfExists              : true
        ]))
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(verb: "publishImage", buildSchedule: PipelineConstants.PRERELEASE_BUILD, arguments: [
                sourceImage    : "DEFERRED|4|publishUrl",
                registry       : "docker-prod.artifactory.express-scripts.com",
                addCandidateTag: true
        ]))
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(verb: "awaitApproval", buildSchedule: PipelineConstants.CURRENT_BUILD, arguments: [time: 10, unit: 'MINUTES', message: 'Do you want to create release branch?', scope: "develop"]))
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(verb: "awaitApproval", buildSchedule: PipelineConstants.CURRENT_BUILD, arguments: [time: 10, unit: 'MINUTES', message: 'Do you want start production release?', scope: "release"]))
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(verb: "publishImage", buildSchedule: PipelineConstants.CALLBACK_BUILD, arguments: [
                sourceImage : "DEFERRED|4|publishUrl",
                registry    : "docker-prod.artifactory.express-scripts.com",
                addLatestTag: true
        ]))
        when:
        RunStepsFromFileStep step = new RunStepsFromFileStep(psc, script)
        def executionPlan = step.createExecutionPlan(PipelineConstants.CURRENT_BUILD)
        List<List<StepInvocation>> subPlans = RunStepsFromFileStep.createSubPlansByPodIndex(executionPlan)
        then:
        subPlans != null
        subPlans.size() == 4
        subPlans[0].get(0).podIndex == 0
    }

    def "when step can be completed by more then one module execution plan is expanded"() {
        given:
        LoadPipelineModulesStep loadModStep = new LoadPipelineModulesStep(psc, script)
        def contacts = loadModStep.loadModuleContracts(["common"])
        psc.globalModuleManager.stateManager().addList(contacts)
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(verb: "preflightCheckVerification", buildSchedule: PipelineConstants.CURRENT_BUILD))
        when:
        RunStepsFromFileStep step = new RunStepsFromFileStep(psc, script)
        def executionPlan = step.createExecutionPlan(PipelineConstants.CURRENT_BUILD)
        then:
        executionPlan.size() == 2
        executionPlan[1].options?.filter == "cnp-preflight-check-codeowners"
        executionPlan[0].options?.filter == "cnp-preflight-check-git-settings"
    }

    def "when build was last step before approval checkpoint name is build completed"() {
        given:
        def steps = [
                PipelineUtils.CreateInvocation(verb: "preflightCheckVerification"),
                PipelineUtils.CreateInvocation(verb: "build"),
                PipelineUtils.CreateInvocation(verb: PipelineConstants.VERB_AWAIT)
        ]
        when:
        String name = RunStepsFromFileStep.createCheckPointName(steps)
        then:
        name == "Build Completed"

    }

    def "when deploy was last step before approval checkpoint name is build dev completed"() {
        given:
        def steps = [
                PipelineUtils.CreateInvocation(verb: "preflightCheckVerification"),
                PipelineUtils.CreateInvocation(verb: "build"),
                PipelineUtils.CreateInvocation(verb: "deploy", options: [env: "dev"]),
                PipelineUtils.CreateInvocation(verb: PipelineConstants.VERB_AWAIT)
        ]
        when:
        String name = RunStepsFromFileStep.createCheckPointName(steps)
        then:
        name == "Deploy Dev Completed"

    }

    def "when only approvals checkpoint name is empty"() {
        given:
        def steps = [
                PipelineUtils.CreateInvocation(verb: PipelineConstants.VERB_AWAIT),
                PipelineUtils.CreateInvocation(verb: PipelineConstants.VERB_AWAIT)
        ]
        when:
        String name = RunStepsFromFileStep.createCheckPointName(steps)
        then:
        name == ""

    }

    def "when runScript verb execute script"() {
        given:
        LoadPipelineModulesStep loadModStep = new LoadPipelineModulesStep(psc, script)
        String imageUrl = psc.globalModuleManager.stateManager().configuration.defaultPcfImage
        def contacts = loadModStep.loadModuleContracts(["pcf"])
        psc.globalModuleManager.stateManager().addList(contacts)
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(verb: "runScript", arguments: [dockerImage: imageUrl, script: "ls -la"], buildSchedule: PipelineConstants.CURRENT_BUILD))
        when:
        RunStepsFromFileStep step = new RunStepsFromFileStep(psc, script)
        def executionPlan = step.createExecutionPlan(PipelineConstants.CURRENT_BUILD)
        then:
        executionPlan.size() == 1
    }

    def "when pipeline contains preRelease closure candidate deployer job is executed"() {
        given:
        LoadPipelineModulesStep loadModStep = new LoadPipelineModulesStep(psc, script)
        def contacts = loadModStep.loadModuleContracts(["pcf"])
        psc.globalModuleManager.stateManager().addList(contacts)
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(verb: "preRelease", buildSchedule: PipelineConstants.CURRENT_BUILD))
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(verb: "deploy", buildSchedule: PipelineConstants.PRERELEASE_BUILD))
        when:
        RunStepsFromFileStep step = new RunStepsFromFileStep(psc, script)
        def currentBuildExecutionPlan = step.createExecutionPlan(PipelineConstants.CURRENT_BUILD)
        def preReleaseBuildExecutionPlan = step.createExecutionPlan(PipelineConstants.PRERELEASE_BUILD)
        def result = step.execute("", currentBuildExecutionPlan)
        then:
        // current build should have preRelease Step
        currentBuildExecutionPlan.size() == 1
        // preRelease should have deploy step
        preReleaseBuildExecutionPlan.size() == 1
        result.commandResult == StepResult.SUCCESS
        // the spy should show we have one command recorded that calls candidate deployer job
        script.shellCommands.findAll {
            it.startsWith("cnp-jenkins-job-build.sh")
        }.size() == 1
    }

    def "withEnv object is saved and available when loading steps from a file"() {
        given:
        // build up the 'base pipeline' state
        def contracts = new LoadPipelineModulesStep(psc, script).loadModuleContracts(["k8s"])
        psc.globalModuleManager.stateManager().addList(contracts)
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(verb: "deploy", buildSchedule: PipelineConstants.PRERELEASE_BUILD, arguments: [
                appName     : "www-frontend-greetings-cnp",
                artifact    : "https://artifactory-dev.express-scripts.com/artifactory/api/npm/npm-local/@esi/greetings-ui/-/@esi/greetings-ui-1.0.5.tgz",
                awsAccountId: "305171387263",
                awsRoleName : "DEPLOYER",
                awsUsername : "DAWS-ESI-MEMBERWEB",
                deployPath  : "esi-memberweb-static-dev-candidate-primary-305171387263/greetings-ui,esi-memberweb-static-dev-candidate-failover-305171387263/greetings-ui",
                environment : "dev"
        ], withEnv: ["VAR_1=value1_for_candidate_job"]))
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(verb: "cutover", buildSchedule: PipelineConstants.CALLBACK_BUILD, arguments: [
                appName     : "www-frontend-greetings-cnp",
                awsAccountId: "305171387263",
                awsRoleName : "DEPLOYER",
                awsUsername : "DAWS-ESI-MEMBERWEB",
                sourcePath  : "esi-memberweb-static-dev-candidate-primary-305171387263/greetings-ui",
                deployPath  : "esi-memberweb-static-dev-primary-305171387263/greetings-ui,esi-memberweb-static-dev-failover-305171387263/greetings-ui",
                environment : "dev"
        ], withEnv: ["VAR_1=value1_for_callback_job"]))
        // create release config using the built-up state
        ReleaseInfo releaseConfiguration = StoreReleaseMetadataStep.createReleaseConfiguration(
                script,
                psc.globalModuleManager.stateManager(),
                [:],
                "releaseId",
                'artifactPath',
                'rollbackPackagePath',
                "repoUrl" as String)
        // add the json to the Mockscript so when callback tries to read it, it's there
        script.readFileResults.put("releaseInfo.json", new JsonBuilder(releaseConfiguration).toPrettyString())
        when:
        // make a 'fresh' pipeline state manager to simulate being in a callback job with no pipeline state
        RunStepsFromFileStep step = new RunStepsFromFileStep(psc, script)
        step.loadStepsFromFile()
        then:
        psc.globalModuleManager.stateManager().pipelineSteps.find { it.verb == 'deploy' && it.buildSchedule == PipelineConstants.PRERELEASE_BUILD }.withEnv == ["VAR_1=value1_for_candidate_job"]
        psc.globalModuleManager.stateManager().pipelineSteps.find { it.verb == 'cutover' && it.buildSchedule == PipelineConstants.CALLBACK_BUILD }.withEnv == ["VAR_1=value1_for_callback_job"]
    }
    def "processArguments based on metadataInArgs"() {
        given:
        HashMap metadata = [
                     Release_JenkinsMaster: "orchestrator18.orchestrator-v2.sys.cigna.com"]
        metadata.each {
            psc.metadata.put(it.key, it.value)
        }
        def config = [:]
        config.args = [
            "cluster": "hs-6-prod"
        ]
        config["metadataInArgs"] = metadataInArgs
        Module module = new Module(psc:psc)
        module.bridge = new ModuleBridge(script, config)
        when:
        module.processArguments(script, config)
        then:
        config.args['Release_JenkinsMaster'] == hasParam // will be available based in metadataInArgs
        config.args['cluster'] == "hs-6-prod" // in all cases this argument should be processed
        where:
        metadataInArgs << [true, false, '', null]
        hasParam << ["orchestrator18.orchestrator-v2.sys.cigna.com", null, null, null]
    }
}
