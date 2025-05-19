package com.evernorth.cloudnativebuild.pipeline.steps

import com.cigna.base.DockerPipelineLib
import com.cigna.common.kubernetes.PodConfigGenerator
import com.cigna.common.utils.FeatureFlags
import com.cigna.state.PipelineStateContext
import com.cloudbees.groovy.cps.NonCPS
import com.evernorth.cloudnativebuild.config.ReleaseConstants
import com.evernorth.cloudnativebuild.model.BuildConfiguration
import com.evernorth.cloudnativebuild.model.ModuleContractType
import com.evernorth.cloudnativebuild.model.StepResult
import com.evernorth.cloudnativebuild.model.logging.LogLevel
import com.evernorth.cloudnativebuild.pipeline.PipelineUtils
import com.evernorth.cloudnativebuild.pipeline.ShellModuleUtil
import com.evernorth.cloudnativebuild.service.Logger
import com.evernorth.cloudnativebuild.service.PipelineStateManager
import groovy.json.JsonSlurperClassic
import hudson.Functions

class CompleteReleaseStep extends DockerPipelineLib {
    Logger logger

    CompleteReleaseStep(PipelineStateContext psc, def script, def config = [:]) {
        this.script = script
        this.config = config
        logger = new Logger(script)
        this.psc = psc
    }

    /**
     * Executes a sequence of steps defined in a releaseInfo.json file stored in Artifactory
     * @param xlrPayload - A JSON payload stored inside the releaseTool and passed to job as a argument
     * @param rollbackDeploymentPackagePath - The path to rollback package that will be used instead of the path
     * @param xlrCallbackCommand
     * @return
     */
    StepResult execute(PipelineStateManager stateManager, String xlrPayload, String rollbackDeploymentPackagePath = "", String xlrCallbackCommand = "") {
        LoadConfigStep loadConfigStep = new LoadConfigStep(script)
        loadConfigStep.updateDefaultBuildConfigurationFromEnvironment(stateManager.configuration)
        // STEP 1 Parse Callback Params and validate
        logger.log("xlrPayload as string: ${xlrPayload}", LogLevel.INFO)
        Map releaseToolPayload = parseXlrPayload(xlrPayload) as Map
        logger.log("xlrPayload as Map: ${releaseToolPayload}", LogLevel.TRACE)
        boolean isReleasePayloadValid = validateReleaseToolPayload(releaseToolPayload)
        logger.log("isReleasePayloadValid: ${isReleasePayloadValid}", LogLevel.TRACE)
        if (!isReleasePayloadValid) return StepResult.failed("Release Payload missing artifact argument.")
        logger.log("rollbackDeploymentPackagePath: ${rollbackDeploymentPackagePath}", LogLevel.TRACE)
        logger.log("xlrCallbackCommand: ${xlrCallbackCommand}", LogLevel.TRACE)
        stateManager.configuration.podCloud = releaseToolPayload.podCloud

        String deploymentPath = xlrCallbackCommand == ReleaseConstants.ROLLBACK_SUBCOMMAND ? rollbackDeploymentPackagePath : "${releaseToolPayload?.archivePath}/${releaseToolPayload?.archiveName}"
        logger.log("deploymentPath: ${deploymentPath}", LogLevel.TRACE)
        def modules = new LoadPipelineModulesStep(psc, script).loadModuleContracts(['release'])
        stateManager.addList(modules)
        def releaseContract = stateManager.getFirstContractForContactType(ModuleContractType.RELEASE.name())
        def releaseContainerName = PodConfigGenerator.getContainerName(releaseContract.image)

        additionalPodConfig = PodConfigGenerator.addContainersToPodConfig(psc, additionalPodConfig)
        logger.log("Using additionalPodConfig: ${additionalPodConfig}")
        String yaml = getPodConfig(securityContext, config)

        RunStepsFromFileStep runStepsFromFileStep = new RunStepsFromFileStep(psc, script, config)
        ShellModuleUtil.executeModuleInCustomPod(psc, script, yaml, stateManager.configuration.podCloud, releaseContainerName) {
            // STEP 2 Get the Release Package from Artifactory and Unzip it
            StepResult retrieveResult = PipelineUtils.retrieveDeploymentPackage(psc, script, stateManager, deploymentPath, config)
            PipelineUtils.failPipelineIfStepResultFailed(script, retrieveResult, "Failed to get deployment package.")
            if (retrieveResult.commandResult == StepResult.FAILURE) return StepResult.failed("Could not pull deployment info.")
            // STEP 3 load the pipeline steps from the releaseInfo file then execute them
            runStepsFromFileStep.loadStepsFromFile()
        }
        StepResult stepResult = runStepsFromFileStep.execute(xlrCallbackCommand)
        PipelineUtils.failPipelineIfStepResultFailed(script, stepResult, "Release callback step has failed : ${stepResult?.errors?.toString()}")
        return stepResult
    }

    @NonCPS
    private def parseXlrPayload(String json) {
        JsonSlurperClassic jsonSlurperClassic = new JsonSlurperClassic()
        try {
            def parserResults = jsonSlurperClassic.parseText(json)
            return parserResults
        }
        catch (ignore) {
            if (FeatureFlags.showStackTraces) {
                logger.log(Functions.printThrowable(ignore))
            }

            return null
        }
    }

    /**
     * Performs an Scm Checkout for modules that require artifactless release
     * @param releaseArguments
     * @param isRollback
     */
    void callbackScmCheckout(Map releaseArguments, boolean isRollback) {
        String branchName = releaseArguments.branchName
        if (isRollback && releaseArguments.tagName != null) {
            logger.log("Rolling back from tag")
            branchName = releaseArguments.tagName
        }
        String appRepo = releaseArguments.repoUrl
        logger.log("Checking out source code from $appRepo from branch ${releaseArguments.branchName}")
        script.checkout([$class           : 'GitSCM',
                         branches         : [[name: branchName]],
                         userRemoteConfigs: [[url: appRepo, credentialsId: BuildConfiguration.gitCredentialFromRepo(appRepo)]]])
        script.sh("ls -l")
    }

    @NonCPS
    static boolean validateReleaseToolPayload(Map releaseToolPayload) {
        if (!releaseToolPayload) return false
        List requiredProperties = [
                "artifact"
        ]
        boolean isValid = true
        requiredProperties.each {
            if (releaseToolPayload.get(it) == null) {
                isValid = false
                return
            }
        }
        return isValid
    }
}
