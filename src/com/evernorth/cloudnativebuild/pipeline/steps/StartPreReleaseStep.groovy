package com.evernorth.cloudnativebuild.pipeline.steps

import com.cigna.base.DockerPipelineLib
import com.cigna.common.utils.FeatureFlags
import com.cigna.common.utils.Utils
import com.cigna.state.PipelineStateContext
import com.cloudbees.groovy.cps.NonCPS
import com.evernorth.cloudnativebuild.config.ReleaseConstants
import com.evernorth.cloudnativebuild.data.PipelineConstants
import com.evernorth.cloudnativebuild.model.ModuleContractType
import com.evernorth.cloudnativebuild.model.StepResult
import com.evernorth.cloudnativebuild.model.logging.LogLevel
import com.evernorth.cloudnativebuild.pipeline.PipelineUtils
import com.evernorth.cloudnativebuild.service.Logger
import com.evernorth.cloudnativebuild.service.ModuleExecutionRules
import hudson.Functions

class StartPreReleaseStep extends DockerPipelineLib {
    Logger logger
    static final String BUILD_JOB_MODULE_NAME = "cnp-jenkins-job-build"

    StartPreReleaseStep(def script, PipelineStateContext psc, def config = [:]) {
        this.script = script
        this.config = config
        logger = newLogger()
        this.psc = psc
    }

    @NonCPS
    private Logger newLogger() {
        return new Logger(this.script)
    }

    /**
     * MPV 1 Limitations - Supports a single module called cnp-release-xlr
     * Each invocation deploys series of steps
     * @return StepResult
     */
    StepResult execute() {
        // skip if not branch is not release or hotfix
        if (shouldSkip(config)) {
            StepResult result = StepResult.empty()
            result.commandOutput = [skipped: true]
            return result
        }
        logger.log("STARTING PRE-RELEASE STAGE...")
        ExecuteShellModuleStep preReleaseStep = newShellModuleStep()
        StepResult preReleaseStepResult = null
        Map properties = [:]
        try {
            logger.log("STEP 1 - Create Release File and Store in Artifactory")
            String deploymentPackagePath = createReleaseFileAndStoreInArtifactory()
            logger.log("STEP 2 - create variables needed to call job and adding to")
            createArgumentsForExternalJob(deploymentPackagePath, properties, PipelineUtils.pipelineNameAndBranch(script, psc.globalModuleManager.stateManager()))
            logger.log("STEP 3 executing DEVOPS Job")
            preReleaseStepResult = executeDevopsJob(preReleaseStep, properties, preReleaseStepResult)
        }
        catch (IllegalArgumentException ex) {
            if (FeatureFlags.showStackTraces) {
                logger.log(Functions.printThrowable(ex))
            }
            PipelineUtils.failPipeline(script, "The Prerelease module execution failed :  ${ex.message}")
            preReleaseStepResult = StepResult.failed()
        }
        return preReleaseStepResult
    }

    @NonCPS
    private ExecuteShellModuleStep newShellModuleStep() {
        return new ExecuteShellModuleStep(psc, script)
    }

    private boolean shouldSkip(def config) {
        boolean hasNoPreReleaseSteps = psc.globalModuleManager.stateManager().getPipelineSteps().findAll { it.buildSchedule == PipelineConstants.PRERELEASE_BUILD }?.isEmpty()
        boolean isNotReleaseBranch = !ModuleExecutionRules.isReleasableBranch(script, config)
        if (isNotReleaseBranch || hasNoPreReleaseSteps) {
            logger.log("Skipping preRelease. Steps in preRelease closure are only executed on release or hotfix branches.")
            return true
        }
        return false
    }

    private StepResult executeDevopsJob(preReleaseStep, Map properties, StepResult preReleaseStepResult) {
        logger.log("Calling build job using test module", LogLevel.TRACE)
        addPrereleaseModuleToStateManager()
        script.stage("PreRelease Steps") {
            preReleaseStepResult = preReleaseStep.execute(ModuleContractType.TEST, BUILD_JOB_MODULE_NAME, properties, [:])

            logger.log("preReleaseStep result: ${preReleaseStepResult}", LogLevel.TRACE)
            if (preReleaseStepResult.commandResult == StepResult.FAILURE) {
                PipelineUtils.failPipeline(script, "The Prerelease module execution failed :  ${preReleaseStepResult?.errors?.toString()}")
            } else if (preReleaseStepResult.commandResult == StepResult.UNSTABLE) {
                script.currentBuild.result = PipelineConstants.BUILD_RESULT_UNSTABLE
            }
        }
        return preReleaseStepResult
    }

    private void addPrereleaseModuleToStateManager() {
        def prereleaseContract = psc.globalModuleManager.stateManager().getFirstContractForContactType(ModuleContractType.TEST.name())
        if (!prereleaseContract) {
            logger.log("No PreRelease Module Configured. Using default.", LogLevel.WARNING)
            def loadModuleStep = newLoadPipeline()
            def module = loadModuleStep.getDefaultPrereleaseModule()
            psc.globalModuleManager.stateManager().add(module)
        }
    }

    @NonCPS
    private LoadPipelineModulesStep newLoadPipeline() {
        return new LoadPipelineModulesStep(psc, script)
    }

    private void createArgumentsForExternalJob(String deploymentPackagePath, Map properties, String libraryName) {
        Map jobVars = getJobVariables(deploymentPackagePath, libraryName)
        logger.log("jobVars: ${jobVars}", LogLevel.TRACE)
        properties.putAll(jobVars)
        logger.log("properties with jobVars: ${properties}", LogLevel.TRACE)
    }

    private String createReleaseFileAndStoreInArtifactory() {
        def deploymentPackagePath = StoreReleaseMetadataStep.createAndStoreReleaseInfoFile(script, config,  psc)
        logger.log("deploymentPackagePath: ${deploymentPackagePath}", LogLevel.TRACE)
        return deploymentPackagePath
    }

    /**
     * Creates variables required for calling DevOps Job
     * @return
     */
    Map getJobVariables(String deploymentPackagePath, String libraryName) {
        Map map = [:]
        def candidateJob = psc.globalModuleManager.configuration().candidateJob
        def customCandidateJob = script.env.CNP_CANDIDATE_JOB ?: candidateJob
        String podCloudName = psc.podSelector.podClouds[Utils.cloud(config)]
        script.echo "customCandidateJob: $customCandidateJob"
        map.put("jobName", customCandidateJob)
        map.put("jenkinsURL", "${script.env.JENKINS_URL}")

        def deploymentInfo = PipelineUtils.urlEncodeString("{\"podCloud\": \"${podCloudName}\",\"deploymentPackagePath\":\"${deploymentPackagePath}\",\"${ReleaseConstants.PIPELINE_LIBRARY_PARAMETER_NAME}\":\"${libraryName}\"}")
        map.put("buildParams", "deploymentInfo=${deploymentInfo}&isCandidate=true")
        return map
    }
}
