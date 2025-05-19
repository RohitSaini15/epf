package com.evernorth.cloudnativebuild.pipeline.steps

import com.cigna.base.DockerPipelineLib
import com.cigna.common.kubernetes.PodConfigGenerator
import com.cigna.state.PipelineStateContext
import com.cloudbees.groovy.cps.NonCPS
import com.evernorth.cloudnativebuild.model.ModuleContractType
import com.evernorth.cloudnativebuild.model.StepResult
import com.evernorth.cloudnativebuild.model.logging.LogLevel
import com.evernorth.cloudnativebuild.pipeline.PipelineUtils
import com.evernorth.cloudnativebuild.pipeline.ShellModuleUtil
import com.evernorth.cloudnativebuild.service.Logger
import com.evernorth.cloudnativebuild.service.PipelineStateManager

class FinalizeReleaseStep extends DockerPipelineLib {
    Logger logger
    // we're going to make two orthogonal pods in here, so this is a blank base to which to add containers
    private Map<String, List<Map>> blankPodConfig = [volumes: [], containers: []]

    FinalizeReleaseStep(PipelineStateContext psc, def script) {
        this.script = script
        this.psc = psc
        logger = new Logger(script)
    }

    @NonCPS
    private RunStepsFromFileStep newRunStepsFromFile() {
        return new RunStepsFromFileStep( psc, script, config)
    }

    /**
     * MPV 1 Limitations - Supports a single module called cnp-release-xlr
     * Each invocation is for single deployment environment
     * @param verbArguments - Should contain all verbArguments required for deployment in addition to props required for release module
     * @return StepResult
     */
    StepResult execute(Map verbArguments, Map options = [:]) {
        LoadConfigStep loadConfigStep = new LoadConfigStep(script)
        loadConfigStep.updateDefaultBuildConfigurationFromEnvironment(psc.globalModuleManager.stateManager().configuration)
        StepResult finalizeModuleResult = null
        logger.log("ContractList before loading: ${psc.globalModuleManager.stateManager().getContracts()}", LogLevel.TRACE)
        def modules = new LoadPipelineModulesStep(psc, script).loadModuleContracts(['release'])
        psc.globalModuleManager.stateManager().addList(modules)
        logger.log("ContractList after loading: ${psc.globalModuleManager.stateManager().getContracts()}", LogLevel.TRACE)
        additionalPodConfig = PodConfigGenerator.addContainersToPodConfig(psc, blankPodConfig)
        logger.log("Using additionalPodConfig: ${additionalPodConfig}")
        String yaml = getPodConfig(securityContext, config)
        psc.globalModuleManager.stateManager().configuration.podCloud = verbArguments.podCloud
        logger.log(yaml, LogLevel.TRACE)

        // step 1 - setup the retrieve module (in a pod built from the state available to the callback job)
        def retrieveContract = psc.globalModuleManager.stateManager().getFirstContractForContactType(ModuleContractType.RETRIEVE.name())

        ShellModuleUtil.executeModuleInCustomPod(psc, script, yaml, psc.globalModuleManager.stateManager().configuration.podCloud, PodConfigGenerator.getContainerName(retrieveContract.image)) {
            if (!retrieveContract) return StepResult.failed("RETRIEVE contract not loaded")
            // STEP 2 Get the Release Package from Artifactory and Unzip it
            StepResult retrieveResult = PipelineUtils.retrieveDeploymentPackage(psc, script, psc.globalModuleManager.stateManager(), verbArguments.deploymentPackagePath, config)
            PipelineUtils.failPipelineIfStepResultFailed(script, retrieveResult, "Failed to get deployment package.")
            def runStepsFromFileStep = newRunStepsFromFile()
            logger.log("FinalizeReleaseStep retrieveResult: ${retrieveResult}", LogLevel.TRACE)
            // step 3 - update state manager with contracts from release state file
            runStepsFromFileStep.loadStepsFromFile()
        }

        // step 4 - run finalize module in a pod built based on state from release state file
        additionalPodConfig = PodConfigGenerator.addContainersToPodConfig(psc, blankPodConfig)
        logger.log("Using additionalPodConfig: ${additionalPodConfig}")
        String finalizeYaml = getPodConfig(securityContext, config)
        def finalizeContract = psc.globalModuleManager.stateManager().getFirstContractForContactType(ModuleContractType.FINALIZE_RELEASE.name())
        ShellModuleUtil.executeModuleInCustomPod(psc, script, finalizeYaml, psc.globalModuleManager.stateManager().configuration.podCloud, PodConfigGenerator.getContainerName(finalizeContract.image)) {
            logger.log("verbArguments: ${verbArguments}", LogLevel.TRACE)
            logger.log("options: ${options}", LogLevel.TRACE)
            logger.log("finalizeContract: ${finalizeContract}", LogLevel.TRACE)

            ExecuteShellModuleStep shellModuleStep = new ExecuteShellModuleStep(psc, script)
            finalizeModuleResult = shellModuleStep.execute(ModuleContractType.FINALIZE_RELEASE, finalizeContract.moduleName, verbArguments, options)
            logger.log("releaseModuleResult: ${finalizeModuleResult}", LogLevel.TRACE)
            PipelineUtils.failPipelineIfStepResultFailed(script, finalizeModuleResult, "The Release module execution failed :  ${finalizeModuleResult?.errors?.toString()}")

        }
        return finalizeModuleResult
    }
}
