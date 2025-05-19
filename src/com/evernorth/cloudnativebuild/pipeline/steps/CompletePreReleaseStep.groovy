package com.evernorth.cloudnativebuild.pipeline.steps

import com.cigna.base.DockerPipelineLib
import com.cigna.common.exception.ErrorStepException
import com.cigna.common.kubernetes.PodConfigGenerator
import com.cigna.state.PipelineStateContext
import com.cloudbees.groovy.cps.NonCPS
import com.evernorth.cloudnativebuild.data.PipelineConstants
import com.evernorth.cloudnativebuild.model.ModuleContractType
import com.evernorth.cloudnativebuild.model.StepInvocation
import com.evernorth.cloudnativebuild.model.StepResult
import com.evernorth.cloudnativebuild.model.logging.LogLevel
import com.evernorth.cloudnativebuild.pipeline.PipelineUtils
import com.evernorth.cloudnativebuild.pipeline.ShellModuleUtil
import com.evernorth.cloudnativebuild.service.CredentialManager
import com.evernorth.cloudnativebuild.service.Logger
import com.evernorth.cloudnativebuild.service.PipelineStateManager
import groovy.json.JsonSlurperClassic

class CompletePreReleaseStep extends DockerPipelineLib {
    Logger logger
    PipelineStateContext psc

    CompletePreReleaseStep(def script, PipelineStateContext psc) {
        this.script = script
        this.config = [:]
        logger = newLogger(script)
        this.psc = psc
    }

    @NonCPS
    private Logger newLogger(script) {
        return new Logger(script)
    }
    String containerName = 'cnp-release-xlr'

    StepResult execute(PipelineStateManager stateManager, String deploymentInfo) {
        logger.log("Executing CompletePreReleaseStep.execute", LogLevel.TRACE)
        logger.log("Incoming deploymentInfo: ${deploymentInfo}", LogLevel.TRACE)
        def deploymentInfojson = newJsonSlurper().parseText(deploymentInfo)
        String deploymentPackagePath = deploymentInfojson.deploymentPackagePath
        stateManager.configuration.podCloud = deploymentInfojson.podCloud
        logger.log("Running PreRelease steps in cloud: ${stateManager.configuration.podCloud}", LogLevel.TRACE)
        logger.log("deploymentPackagePath: ${deploymentPackagePath}", LogLevel.TRACE)
        def modules = newPipelineLoader().loadModuleContracts(['release'])
        stateManager.addList(modules)
        def releaseContract = stateManager.getFirstContractForContactType(ModuleContractType.RELEASE.name())
        def releaseContainerName = PodConfigGenerator.getContainerName(releaseContract.image)

        LoadConfigStep loadConfigStep = newLoadConfig()
        loadConfigStep.updateDefaultBuildConfigurationFromEnvironment(stateManager.configuration)
        additionalPodConfig = PodConfigGenerator.addContainersToPodConfig(psc, additionalPodConfig)
        logger.log("Using additionalPodConfig: ${additionalPodConfig}")
        String yaml = getPodConfig(securityContext, config)
        logger.log(yaml, LogLevel.TRACE)
        logger.log(stateManager.printModuleList(), LogLevel.INFO)

        try {
            def runStepsFromFileStep = newRunStepsFromFile()
            ShellModuleUtil.executeModuleInCustomPod(psc, script, yaml, stateManager.configuration.podCloud, releaseContainerName) {
                logger.log("Executing module in custom pod: deploymentPackagePath = ${deploymentPackagePath}")
                StepResult retrieveResult = PipelineUtils.retrieveDeploymentPackage(psc, script, stateManager, deploymentPackagePath, config)
                PipelineUtils.failPipelineIfStepResultFailed(script, retrieveResult, "Failed to get deployment package.")
                if (retrieveResult.commandResult == StepResult.FAILURE) return StepResult.failed("Could not pull deployment info.")

                logger.log("CompletePreReleaseStep retrieveResult: ${retrieveResult}", LogLevel.TRACE)
                runStepsFromFileStep.loadStepsFromFile()
            }
            List<StepInvocation> steps = runStepsFromFileStep.createExecutionPlan(PipelineConstants.PRERELEASE_BUILD)
            CredentialManager.verifyCredentials(psc, script, steps)
            StepResult stepResult = runStepsFromFileStep.execute( "", steps)
            logger.log("CompletePreReleaseStep RunStepsFromFileStep result ${stepResult}", LogLevel.TRACE)
            PipelineUtils.failPipelineIfStepResultFailed(script, stepResult, "Pre Release callback step has failed : ${stepResult?.errors?.toString()}")
            return stepResult
        }
        catch (ErrorStepException ignore) {
            // if we are here, we are ending pipeline on purpose
            // but want to have a success status
        }

        StepResult.empty()
    }

    @NonCPS
    private RunStepsFromFileStep newRunStepsFromFile() {
        return new RunStepsFromFileStep( psc, script, config)
    }

    @NonCPS
    private LoadConfigStep newLoadConfig() {
        return new LoadConfigStep(script)
    }

    @NonCPS
    private LoadPipelineModulesStep newPipelineLoader() {
        return new LoadPipelineModulesStep(psc, script)
    }

    @NonCPS
    private JsonSlurperClassic newJsonSlurper() {
        return new JsonSlurperClassic()
    }
}
