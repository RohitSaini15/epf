package com.evernorth.cloudnativebuild.pipeline

import com.cigna.state.PipelineStateContext
import com.evernorth.cloudnativebuild.data.PipelineConstants
import com.evernorth.cloudnativebuild.mocks.MockJenkins
import com.evernorth.cloudnativebuild.model.ModuleContract
import com.evernorth.cloudnativebuild.model.ModuleContractType
import com.evernorth.cloudnativebuild.model.StepResult
import com.evernorth.cloudnativebuild.pipeline.steps.StartReleaseStep
import spock.lang.Specification

class ShellModuleUtilSpec extends Specification {
    def "when executeShellModule null scriptContext throws exception"() {
        given:
        MockJenkins jenkins = new MockJenkins()
        def psc = new PipelineStateContext(jenkins)
        psc.globalModuleManager.stateManager().addList(jenkins.defaultModules)
        when:
        ShellModuleUtil.executeShellModule(psc, null, ModuleContractType.NOTIFY)
        then:
        thrown IllegalArgumentException

    }

    def "when no modules executeShellModule returns Empty StepResult"() {
        given:

        MockJenkins jenkins = new MockJenkins()
        def psc = new PipelineStateContext(jenkins)
        when:
        StepResult result = ShellModuleUtil.executeShellModule(psc, jenkins, ModuleContractType.NOTIFY)
        then:
        result == StepResult.empty()
    }

    def "when executeShellModule results in pipeline fail currentBuild result is FAILED"() {
        given:
        MockJenkins jenkins = new MockJenkins()
        def psc = new PipelineStateContext(jenkins)
        def modules = [[contractName: "PREFLIGHT_CHECK", commandName: "cnp-preflight-validatejenkinsfile", image: "cnp/cnp-docker-core:devel"]]
        psc.globalModuleManager.stateManager().addList(modules)
        jenkins.addReadFileResult("cnp-preflight-validatejenkinsfile-results.json", "cnp-jenkins-job-build-failure-results.json")
        when:
        ShellModuleUtil.executeShellModule(psc, jenkins, ModuleContractType.PREFLIGHT_CHECK)
        then:
        jenkins.currentBuild.result == 'FAILURE'

    }

    def "scheduleExecutionOfShellModule detects when called from withModule"() {
        given:
        MockJenkins jenkins = new MockJenkins()
        def psc = new PipelineStateContext(jenkins)
        psc.globalModuleManager.stateManager().add(new ModuleContract(contractName: ModuleContractType.DEPLOY, commandName: "cnp-deploy-pcf.sh", image: "cnp/cnp-docker-pcf"))
        jenkins.mockGlobalModuleManager = psc.globalModuleManager.stateManager()
        when:
        StepResult result
        jenkins.withModule("cnp-deploy-pcf") {
            result = jenkins.deploy()
        }
        then:
        result != null
        result.commandResult == StepResult.DEFERRED
    }

//    def "when deferShellModule called from release closure execution is deferred to release"(){
//        given:
//        MockJenkins jenkins = new MockJenkins()
//        def psc = new PipelineStateContext(jenkins)
//        jenkins.env.CNP_LOG_LEVEL="TRACE"
//        jenkins.mockGlobalModuleManager=manager
//        def modules = [
//                [contractName: "DEPLOY", commandName:"cnp-deploy-static-aws.sh", image: "cnp/cnp-docker-aws:devel"],
//                [contractName: "RELEASE", commandName:"cnp-release.sh", image: "cnp/cnp-docker-aws:devel", moduleName: StartReleaseStep.RELEASE_MODULE_NAME]
//        ]
//        when:
//        StepResult result = null
//        new CnpNodeStep(jenkins, manager).execute(modules){
//            jenkins.release {
//                result = jenkins.deploy()
//            }
//        }
//
//        then:
//        result?.commandResult == StepResult.DEFERRED
//        // preflight checks,release and deploy
//        manager.getPipelineSteps().size()==3
//        // index 1 should be deploy command
//        manager.getPipelineSteps().get(0).buildSchedule == PipelineConstants.CALLBACK_BUILD
//
//    }

    def "when verb called in executePreReleaseClosure getScheduleNameFromClosure returns prerelease"() {
        given:
        MockJenkins jenkins = new MockJenkins()
        def psc = new PipelineStateContext(jenkins)
        def modules = [
                [contractName: "DEPLOY", commandName: "cnp-deploy-pcf.sh", image: "cnp/cnp-docker-aws:devel"],
                [contractName: "RELEASE", commandName: "cnp-release.sh", image: "cnp/cnp-docker-aws:devel", moduleName: StartReleaseStep.RELEASE_MODULE_NAME]
        ]
        psc.globalModuleManager.stateManager().addList(modules)
        jenkins.mockGlobalModuleManager = psc.globalModuleManager.stateManager()
        Map properties = [appName     : "appName",
                          artifact    : 'https://artifactory.express-scripts.com/artifactory/api/npm/npm-local/@esi/greetings-ui/-/@esi/greetings-ui-1.0.0-beta.30.tgz',
                          awsAccountId: "305171387263",
                          awsRoleName : 'DEPLOYER',
                          awsUsername : "DAWS-ESI-MEMBERWEB",
                          deployPath  : 'esi-memberweb-static-dev-candidate-primary-305171387263/user-profile-ui,esi-memberweb-static-dev-candidate-failover-305171387263/user-profile-ui',
                          environment : "dev"]
        when:
        PipelineUtils.executePreReleaseClosure(jenkins, psc.globalModuleManager.stateManager()) {
            jenkins.deploy(properties,
                    [env: 'Dev', filter: 'cnp-deploy-static-aws'])
        }
        then:
        // deploy and preRelease
        psc.globalModuleManager.stateManager().getPipelineSteps().size() == 2
        psc.globalModuleManager.stateManager().getPipelineSteps()[0].buildSchedule == PipelineConstants.PRERELEASE_BUILD
    }

    def "when verb called in executeReleaseClosure getScheduleNameFromClosure returns release"() {
        given:
        MockJenkins jenkins = new MockJenkins()
        def psc = new PipelineStateContext(jenkins)
        def modules = [
                [contractName: "DEPLOY", commandName: "cnp-deploy-pcf.sh", image: "cnp/cnp-docker-aws:devel"],
                [contractName: "RELEASE", commandName: "cnp-release.sh", image: "cnp/cnp-docker-aws:devel", moduleName: StartReleaseStep.RELEASE_MODULE_NAME]
        ]
        psc.globalModuleManager.stateManager().addList(modules)
        jenkins.mockGlobalModuleManager = psc.globalModuleManager.stateManager()
        when:
        PipelineUtils.executeReleaseClosure(jenkins, [:], psc.globalModuleManager.stateManager()) {
            jenkins.deploy()
        }
        then:
        // deploy and release
        psc.globalModuleManager.stateManager().getPipelineSteps().size() == 2
        psc.globalModuleManager.stateManager().getPipelineSteps()[0].buildSchedule == PipelineConstants.CALLBACK_BUILD
    }
}
