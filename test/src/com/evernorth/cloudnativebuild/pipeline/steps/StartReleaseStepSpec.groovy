package com.evernorth.cloudnativebuild.pipeline.steps

import com.cigna.SinglePodTest
import com.cigna.state.PipelineStateContext
import com.evernorth.cloudnativebuild.mocks.MockJenkins
import com.evernorth.cloudnativebuild.model.ModuleContract
import com.evernorth.cloudnativebuild.model.ModuleContractType
import com.evernorth.cloudnativebuild.model.StepResult
import com.evernorth.cloudnativebuild.pipeline.PipelineUtils
import spock.lang.Specification

class StartReleaseStepSpec extends Specification {
    PipelineStateContext psc
    MockJenkins script
    def setup() {
        script = new MockJenkins()
        psc = new PipelineStateContext(script)
    }
    def "when execute and build result success build success"() {
        given:
        script.env.BRANCH_NAME = "develop"
        psc.globalModuleManager.stateManager().add([contractName: String.valueOf(ModuleContractType.RELEASE),
                          commandName : "cnp-release-xlr.sh",
                          moduleName  : "cnp-release-xlr",
                          image       : "docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-core"])
        psc.globalModuleManager.stateManager().add([contractName: String.valueOf(ModuleContractType.DEPLOY),
                          commandName : "cnp-deploy-pcf.sh",
                          image       : "docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-pcf"])
        psc.globalModuleManager.stateManager().add([contractName: String.valueOf(ModuleContractType.PUBLISH),
                          commandName : "cnp-release-util.sh",
                          image       : "docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-core"])
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(completed: false, order: 1, verb: "deploy"))
        def startReleaseStep = new StartReleaseStep(script, psc)
        when:
        StepResult result = startReleaseStep.execute([platform : "EKS",
                                                      cluster  : "eks-control.esi-us-entity-dev.aws.cignacloud.com",
                                                      appName  : "greetings-1-qa",
                                                      namespace: "coreeng-dev"])
        then:
        result.commandResult == StepResult.SUCCESS
    }

    def "when execute and build result fail build fail"() {
        given:
        script.env.BRANCH_NAME = "develop"
        def startReleaseStep = new StartReleaseStep(script, psc)
        ModuleContract contract = new ModuleContract(
            contractName: String.valueOf(ModuleContractType.RELEASE),
            commandName: "cnp-release-xlr.sh",
            moduleName: "cnp-release-xlr",
            image: "docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-core",
            logFileName: "cnp-release-xlr-fail-results.json"
        )
        script.addReadFileResult("cnp-release-xlr-fail-results.json")
        psc.globalModuleManager.stateManager().add(contract)
        psc.globalModuleManager.stateManager().add([contractName: String.valueOf(ModuleContractType.DEPLOY),
                     commandName : "cnp-deploy-pcf.sh",
                     image       : "docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-pcf"])
        psc.globalModuleManager.stateManager().add([contractName: String.valueOf(ModuleContractType.PUBLISH),
                     commandName : "cnp-release-util.sh",
                     image       : "docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-core"])
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(completed: false, order: 1, verb: "deploy"))
        when:
        StepResult result = startReleaseStep.execute([platform : "EKS",
                                                      cluster  : "eks-control.esi-us-entity-dev.aws.cignacloud.com",
                                                      appName  : "greetings-1-qa",
                                                      namespace: "coreeng-dev"])
        then:
        result.commandResult == StepResult.FAILURE

    }

    def "when execute and no release module it will be added automatically"() {
        given:
        script.env.BRANCH_NAME = "release/foobar"
        psc.globalModuleManager.stateManager().add([contractName: String.valueOf(ModuleContractType.PUBLISH),
                     commandName : "cnp-release-util.sh",
                     image       : "docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-core"])
        def startReleaseStep = new StartReleaseStep(script, psc)
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(completed: false, order: 1, verb: "deploy"))
        when:
        StepResult result = startReleaseStep.execute([platform : "EKS",
                                                      cluster  : "eks-control.esi-us-entity-dev.aws.cignacloud.com",
                                                      appName  : "greetings-1-qa",
                                                      namespace: "coreeng-dev"])
        then:
        psc.globalModuleManager.stateManager().getFirstContractForContactType(ModuleContractType.RELEASE.name())
    }

    def "when execute and no deployment module then fail build"() {
        given:
        script.env.BRANCH_NAME = "release/foobar"
        psc.globalModuleManager.stateManager().add([contractName: String.valueOf(ModuleContractType.RELEASE),
                     commandName : "cnp-release-xlr.sh",
                     moduleName  : "cnp-release-xlr",
                     image       : "docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-core"])
        psc.globalModuleManager.stateManager().add([contractName: String.valueOf(ModuleContractType.PUBLISH),
                     commandName : "cnp-release-util.sh",
                     image       : "docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-core"])
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(completed: false, order: 1, verb: "deploy"))
        def startReleaseStep = new StartReleaseStep(script, psc)
        when:
        StepResult result = startReleaseStep.execute([platform : "EKS",
                                                      cluster  : "eks-control.esi-us-entity-dev.aws.cignacloud.com",
                                                      appName  : "greetings-1-qa",
                                                      namespace: "coreeng-dev"])
        then:
        result.commandResult == StepResult.FAILURE
    }

    def "when touchless skip adding modules"() {
        script.env.BRANCH_NAME = "release/assdsds"
        psc.globalModuleManager.stateManager().add([contractName: String.valueOf(ModuleContractType.RELEASE),
                     commandName : "cnp-release-xlr.sh",
                     moduleName  : "cnp-release-xlr",
                     image       : "docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-core"])
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(completed: false, order: 1, verb: "deploy"))
        def startReleaseStep = new StartReleaseStep(script, psc)
        when:
        StepResult result = startReleaseStep.execute([touchless: true])
        then:
        result.commandResult == StepResult.SUCCESS
        result.commandOutput.skipped == null
    }

    def "when not a release branch skip release stage"() {
        script.env.BRANCH_NAME = "feature/foobar"
        psc.globalModuleManager.stateManager().add([contractName: String.valueOf(ModuleContractType.RELEASE),
                     commandName : "cnp-release-xlr.sh",
                     moduleName  : "cnp-release-xlr",
                     image       : "docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-core"])
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(completed: false, order: 1, verb: "deploy"))
        def startReleaseStep = new StartReleaseStep(script, psc)
        when:
        StepResult result = startReleaseStep.execute([touchless: true])
        then:
        result.commandResult == StepResult.SUCCESS
        result.commandOutput.skipped == "true"
    }

    def "when no release steps branch skip release stage"() {
        psc.globalModuleManager.stateManager().add([contractName: String.valueOf(ModuleContractType.RELEASE),
                     commandName : "cnp-release-xlr.sh",
                     moduleName  : "cnp-release-xlr",
                     image       : "docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-core"])
        def startReleaseStep = new StartReleaseStep(script, psc)
        when:
        StepResult result = startReleaseStep.execute([touchless: true])
        then:
        result.commandResult == StepResult.SUCCESS
        result.commandOutput.skipped == "true"
    }
}