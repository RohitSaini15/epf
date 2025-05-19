package com.evernorth.cloudnativebuild.pipeline.steps

import com.cigna.base.DockerPipelineLib
import com.cigna.common.utils.FeatureFlags
import com.cigna.common.utils.Utils
import com.cigna.common.phases.PodSelector
import com.cigna.state.PipelineStateContext
import com.cloudbees.groovy.cps.NonCPS
import com.evernorth.cloudnativebuild.config.ReleaseConstants
import com.evernorth.cloudnativebuild.data.PipelineConstants
import com.evernorth.cloudnativebuild.model.ModuleContractType
import com.evernorth.cloudnativebuild.model.StepResult
import com.evernorth.cloudnativebuild.model.logging.LogLevel
import com.evernorth.cloudnativebuild.pipeline.PipelineUtils
import com.evernorth.cloudnativebuild.service.Logger
import com.evernorth.cloudnativebuild.service.PipelineStateManager
import hudson.Functions

class StartReleaseStep extends DockerPipelineLib{
    Logger logger
    static final String RELEASE_MODULE_NAME = "cnp-release-xlr"

    StartReleaseStep(def script,  PipelineStateContext psc, def config=[:]){
        this.script=script
        this.config=config
        logger=new Logger(script)
        this.psc = psc
    }

    boolean shouldExecute(){
        boolean isDeployable = script.env.BRANCH_NAME ==~ '^develop.*'
        // possible that someone changed deployable and excluded release and hotfix
        boolean isRelease = script.env.BRANCH_NAME ==~ '^(release|hotfix).*'
        boolean hasReleaseSteps = !psc.globalModuleManager.stateManager().getPipelineSteps().findAll {it.buildSchedule==PipelineConstants.CALLBACK_BUILD}?.isEmpty()
        logger.log("isDeployable: ${isDeployable} isReleasable: ${isRelease} hasReleaseSteps: ${hasReleaseSteps}" +
                "\nbuild is releasable if it is either deployable or releasable and should have ReleaseSteps")
        return (isDeployable || (isRelease && hasReleaseSteps))
    }

    /**
     * Each invocation is for single deployment environment
     * @param properties - Should contain all properties required for deployment in addition to props required for release module
     * @return StepResult
     */
    StepResult execute(Map properties){
        if(!shouldExecute()){
            logger.log("Skipping release step since this build is not releaseable")
            def result = StepResult.empty()
            result.commandOutput = [skipped:"true"]
            return result
        }
        logger.log("STARTING RELEASE STEP...")
        ExecuteShellModuleStep releaseStep = new ExecuteShellModuleStep(psc, script, config)
        StepResult releaseStepResult
        try {
            if ((!properties || !properties.touchless) && script.env.BRANCH_NAME ==~ '^(release|hotfix).*') {
                validateReleaseConfiguration()
                // Create the release file
                String deploymentPackagePath = StoreReleaseMetadataStep.createAndStoreReleaseInfoFile(script, config, psc, properties)
                logger.log("deploymentPackagePath: ${deploymentPackagePath}", LogLevel.TRACE)
                logger.log(psc.globalModuleManager.stateManager().getContracts(), LogLevel.TRACE)

                // create the release variables and add to properties
                Map releaseVars = getReleaseVariables(psc.globalModuleManager.stateManager().getRollbackDeploymentPackagePath())
                releaseVars.putAll(psc.globalModuleManager.stateManager().releaseArguments)
                logger.log("releaseVars: ${releaseVars}}", LogLevel.TRACE)
                properties.putAll(releaseVars)
            }

            // execute the release step - this will create release branch and start release workflow in XL Release
            releaseStepResult = releaseStep.execute(ModuleContractType.RELEASE, RELEASE_MODULE_NAME, properties, [:])
            logger.log("Release Step Result: ${releaseStepResult}", LogLevel.TRACE)
            if (releaseStepResult?.commandOutput?.xlrReleaseID) {
                def xlrReleaseID = releaseStepResult?.commandOutput?.xlrReleaseID?.toString()?.replace("Applications/", "")?.replace("/", "-")
                logger.log("XLRELEASE_URL : ${script.env.XLRELEASE_URL}")
                def link = "${script.env.XLRELEASE_URL}#/releases"
                String descText = """<a href="${link}/${xlrReleaseID}" target="_blank">XLR Release Created: ${xlrReleaseID}</a>"""
                if (script?.currentBuild?.description){
                    script?.currentBuild?.description += "<br/>${descText}"
                } else{
                    script?.currentBuild?.description = "${descText}"
                }
            }
            
            if (!releaseStepResult || releaseStepResult.commandResult == StepResult.FAILURE) {
                PipelineUtils.failPipeline(script, "The Release module execution failed :  ${ releaseStepResult?.errors?.toString()}")
            }else if (releaseStepResult.commandResult == StepResult.UNSTABLE) {
                script.currentBuild.result = PipelineConstants.BUILD_RESULT_UNSTABLE
            }
        }
        catch(IllegalArgumentException ex){
            logger.log("${ex.stackTrace.toString()}", LogLevel.TRACE)
            PipelineUtils.failPipeline(script, "The Release module execution failed :  ${ex.message}")
            releaseStepResult = StepResult.failed()
        }
        PipelineUtils.failPipelineIfStepResultFailed(script,releaseStepResult, "Start release step failed : ${releaseStepResult?.errors?.toString()}")
        return releaseStepResult
    }



    /**
     * Verify 1 or more deployment module and steps exists
     * @return
     */
    @NonCPS
    private boolean validateReleaseConfiguration() throws IllegalArgumentException{
        // if touchless, then no callback is needed and additional validations are not required
        def releaseContract = psc.globalModuleManager.stateManager().getContractByTypeAndModuleName(ModuleContractType.RELEASE, RELEASE_MODULE_NAME)
        if(!releaseContract){
            // this would only occur if common module loading is bypassed
            logger.log("No Release Module Configured. Using default.", LogLevel.WARNING)
            def loadModuleStep = new LoadPipelineModulesStep(psc, script)
            def module = loadModuleStep.getDefaultReleaseModule()
            psc.globalModuleManager.stateManager().add(module)
        }

        def deployContracts = psc.globalModuleManager.stateManager().getContractsForContactType(String.valueOf(ModuleContractType.DEPLOY))
        def cutOverContracts = psc.globalModuleManager.stateManager().getContractsForContactType(String.valueOf(ModuleContractType.CUTOVER))
        int moduleCount = cutOverContracts?.size() + deployContracts?.size()
        if(moduleCount<1){
            throw new IllegalArgumentException("Pipeline configuration error. You must have at least 1 deployment (deploy or cutover) contract configured in order for a release to be started.")
        }
        if(psc.globalModuleManager.stateManager().pipelineSteps?.size()==0){
            throw new IllegalArgumentException("No pipeline steps have been defined for your release process. Callback job will have no tasks.")
        }
        return true
    }


    /**
     * Creates a map with variables needed to release module
     * @param repoUrl
     * @return
     */
    Map getReleaseVariables(def rollbackDeploymentPackagePath){
        Map map = [:]
        map.put("Release_JenkinsMaster", PipelineUtils.getMasterName(script))
        map.put("Release_BuildUri", script.env.BUILD_URL)
        map.put("Release_${ReleaseConstants.ROLLBACK_PACKAGE_PATH_PROPERTY_NAME}" as String, rollbackDeploymentPackagePath)
        map.put((ReleaseConstants.PIPELINE_LIBRARY_PARAMETER_NAME),PipelineUtils.pipelineNameAndBranch(script, psc.globalModuleManager.stateManager()))
        map.put("podCloud", psc.podSelector.podClouds[Utils.cloud(config)])
        return map
    }

    /**
     * Gets the path to the rollback package stored in artifactory
     * @param repoUrl - the URL to the Artifactory repo
     * @return the URI of the package that will be used for rollback
     */
    String getRollbackPath(String repoUrl) {
        // TODO: This logic should be factored into the release module
        def releaseResponse
        try {
            releaseResponse = script.httpRequest(contentType: 'APPLICATION_JSON',
                    httpMode: 'GET',
                    validResponseCodes: "200,404",
                    url: "${script.env.ARTIFACTORY_ROOT_URL}/api/storage/${PipelineUtils.getDeploymentSourceRepoBase(script, logger, repoUrl, true)}?lastModified".replace(" ", "%20"))
            logger.log("${script.env.ARTIFACTORY_ROOT_URL}/api/storage/${PipelineUtils.getDeploymentSourceRepoBase(script, logger, repoUrl, true)}?lastModified".replace(" ", "%20"), LogLevel.TRACE)
            logger.log("releaseResponse.getStatus(): ${releaseResponse.getStatus()}", LogLevel.TRACE)
            if (releaseResponse.getStatus() == 200) {
                logger.log("Last modified artifact: ${releaseResponse.getContent()}")
                return (script.readJSON(text: releaseResponse.getContent()).uri)?.replace(" ", "%20") - "${script.env.ARTIFACTORY_ROOT_URL}/api/storage/"
            }
            if (releaseResponse.getStatus() == 404) {
                logger.log("***** No Rollback Information Present. You can ignore this error for a new application deployment *****", LogLevel.WARNING)
            }
        } catch (ex) {
            if (FeatureFlags.showStackTraces) {
                logger.log(Functions.printThrowable(ex))
            }
            logger.logError("***** Error while attempting to download rollback information. You can ignore if this error for a new application deployment *****",ex, LogLevel.WARNING)
        }
        return ""
    }
}
