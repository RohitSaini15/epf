package com.evernorth.cloudnativebuild.pipeline.steps


import com.cigna.state.PipelineStateContext
import com.evernorth.cloudnativebuild.data.PipelineConstants
import com.evernorth.cloudnativebuild.mocks.MockJenkins
import com.evernorth.cloudnativebuild.model.BuildConfiguration
import com.evernorth.cloudnativebuild.model.ModuleContractType
import spock.lang.Specification

class LoadPipelineModulesStepSpec extends Specification {
    
    // because nothing here uses JenkinsPipelineSpec functionality, extending Spec instead of
    // SinglePodTest means the tests run much faster
    Object script
    PipelineStateContext psc
    def setup() {
        script = new MockJenkins()
        psc = new PipelineStateContext(script)
    }

    def "when module list is null throws IllegalArgumentException"() {
        given:
        LoadPipelineModulesStep step = new LoadPipelineModulesStep(psc, script)
        when:
        step.loadModuleContracts(null)
        then:
        thrown IllegalArgumentException
    }

    def "when module list is empty throws IllegalArgumentException"() {
        given:
        LoadPipelineModulesStep step = new LoadPipelineModulesStep(psc, script)
        when:
        step.loadModuleContracts([])
        then:
        thrown IllegalArgumentException
    }

    def "when module list contains invalid module template name throws IllegalArgumentException"() {
        given:
        LoadPipelineModulesStep step = new LoadPipelineModulesStep(psc, script)
        when:
        step.loadModuleContracts(["does not exist"])
        then:
        thrown IllegalArgumentException
    }

    def "when module env variables are not set uses default value"() {
        given:
        script.env.CNP_DEFAULT_DOCKER_IMAGE = ""
        // need to create a new PSC after changing env, because the repository is built at construction
        def localPsc = new PipelineStateContext(script)
        LoadPipelineModulesStep step = new LoadPipelineModulesStep(localPsc, script)
        def defaultConfiguration = new BuildConfiguration()
        when:
        def modules = step.loadModuleContracts([PipelineConstants.COMMON_MODULE_TEMPLATE])
        then:
        modules[0].image == defaultConfiguration.defaultDockerImage
    }

    def "when module list contains common returns List with common modules"() {
        given:
        LoadPipelineModulesStep step = new LoadPipelineModulesStep(psc, script)
        when:
        def modules = step.loadModuleContracts([PipelineConstants.COMMON_MODULE_TEMPLATE])
        then:
        modules.size() == 3
    }

    def "when module list contains common and k8s returns List with common and k8s modules"() {
        given:
        LoadPipelineModulesStep step = new LoadPipelineModulesStep(psc, script)
        when:
        def modules = step.loadModuleContracts([PipelineConstants.COMMON_MODULE_TEMPLATE, "k8s"])
        then:
        modules.size() == 8
    }

    def "when get default state writer called returns state writer from release template"() {
        given:
        when:
        def module = new LoadPipelineModulesStep(psc, script).getDefaultStateWriterModule()
        then:
        module != null
        module.contractName == String.valueOf(ModuleContractType.PIPELINE_STATE_WRITER)
    }

    def "when get default release called returns release from release template"() {
        given:
        when:
        def module = new LoadPipelineModulesStep(psc, script).getDefaultReleaseModule()
        then:
        module != null
        module.contractName == String.valueOf(ModuleContractType.RELEASE)
    }

    def "when digital is loaded ensure required modules are loaded"() {
        given:
        def modules = new LoadPipelineModulesStep(psc, script).loadModuleContracts(["digital"])
        psc.globalModuleManager.stateManager().addList(modules)
        when:
        def deployContracts = psc.globalModuleManager.stateManager().getContractsForContactType(ModuleContractType.DEPLOY.toString())
        def cutoverContracts = psc.globalModuleManager.stateManager().getContractsForContactType(ModuleContractType.CUTOVER.toString())
        def buildContacts = psc.globalModuleManager.stateManager().getContractsForContactType(ModuleContractType.BUILD.toString())
        then:
        deployContracts.size() == 3
        deployContracts.findAll { it.commandName == "cnp-deploy-memberweb-aws.sh" && it.moduleName == "cnp-deploy-aws" }.size() == 1
        deployContracts.findAll { it.commandName == "cnp-deploy-memberweb-aws.sh" && it.moduleName == "cnp-deploy-memberweb-aws" }.size() == 1
        deployContracts.findAll { it.commandName == "cnp-deploy-pcf.sh" }.size() == 1
        cutoverContracts.size() == 3
        cutoverContracts.findAll { it.commandName == "cnp-deploy-memberweb-aws.sh" && it.moduleName == "cnp-cutover-aws" }.size() == 1
        cutoverContracts.findAll { it.commandName == "cnp-deploy-memberweb-aws.sh" && it.moduleName == "cnp-deploy-memberweb-aws" }.size() == 1
        cutoverContracts.findAll { it.commandName == "cnp-deploy-pcf.sh" }.size() == 1
        buildContacts.size() == 1
        buildContacts[0].commandName == "cnp-build-npm-create.sh"
    }

    def "when module env variables are set use that"() {
        given:
        script.env.CNP_DEFAULT_DOCKER_IMAGE = "cnp/dummyimage:latest"
        // need to create a new PSC after changing env, because the repository is built at construction
        def localPsc = new PipelineStateContext(script)
        LoadPipelineModulesStep step = new LoadPipelineModulesStep(localPsc, script)

        when:
        def modules = step.loadModuleContracts([PipelineConstants.COMMON_MODULE_TEMPLATE])
        then:
        modules[0].image == script.env.CNP_DEFAULT_DOCKER_IMAGE
    }

    def "when module is loaded more then once duplicates are removed"() {
        given:
        when:
        def modules = new LoadPipelineModulesStep(psc, script).loadModuleContracts(["digital", "pcf"],)
        then:
        modules.size() == 10
    }

    def "when getDefaultPrereleaseModule returns TEST contract from common template"() {
        given:
        when:
        def modules = new LoadPipelineModulesStep(psc, script).getDefaultPrereleaseModule()
        then:
        modules != null
    }
}
