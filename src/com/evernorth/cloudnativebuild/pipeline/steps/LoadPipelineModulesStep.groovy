package com.evernorth.cloudnativebuild.pipeline.steps

import com.cigna.state.PipelineStateContext
import com.cloudbees.groovy.cps.NonCPS
import com.evernorth.cloudnativebuild.model.ModuleContract
import com.evernorth.cloudnativebuild.model.ModuleContractType
import com.evernorth.cloudnativebuild.service.Logger
import com.evernorth.cloudnativebuild.service.PipelineStateManager

/**
 * Loads a set of preconfigured modules
 */
class LoadPipelineModulesStep {
    def scriptContext
    Logger logger

    PipelineStateContext psc

    LoadPipelineModulesStep(PipelineStateContext psc, def scriptContext) {
        this.scriptContext = scriptContext
        this.logger = new Logger(scriptContext)
        this.psc = psc
    }

    @NonCPS
    List<ModuleContract> loadModuleContracts(List<String> moduleTemplates) {
        if (!moduleTemplates || moduleTemplates.size() == 0) throw new IllegalArgumentException("No module templates supplied.")

        return findModuleContracts(moduleTemplates)
    }



    @NonCPS
    private List<ModuleContract> findModuleContracts(List<String> moduleTemplates) {

        List<ModuleContract> contracts = []
        moduleTemplates.each { String templateName ->
            def list = this.psc.repository.defaultModules.findAll { it.key == templateName }.values()
            if (list?.size() == 0) throw new IllegalArgumentException("$templateName is not a valid module template name.")
            contracts.addAll(list[0])
        }

        return contracts.unique()
    }

    @NonCPS
    ModuleContract getDefaultStateWriterModule() {
        loadModuleContracts(["release"]).find({ it.contractName == String.valueOf(ModuleContractType.PIPELINE_STATE_WRITER) })
    }

    @NonCPS
    ModuleContract getDefaultStateReaderModule() {
        loadModuleContracts(["release"]).find({ it.contractName == String.valueOf(ModuleContractType.RETRIEVE) })
    }

    @NonCPS
    ModuleContract getDefaultReleaseModule() {
        loadModuleContracts(["release"]).find({ it.contractName == String.valueOf(ModuleContractType.RELEASE) })
    }

    @NonCPS
    ModuleContract getDefaultPrereleaseModule() {
        loadModuleContracts(["common"]).find({ it.contractName == String.valueOf(ModuleContractType.TEST) })
    }

}
