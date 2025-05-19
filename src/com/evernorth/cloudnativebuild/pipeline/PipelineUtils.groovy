package com.evernorth.cloudnativebuild.pipeline

import com.cigna.common.exception.ErrorStepException
import com.cigna.common.utils.FeatureFlags
import com.cigna.common.utils.StashUtils
import com.cigna.state.PipelineStateContext
import com.cigna.common.utils.Utils
import com.cloudbees.groovy.cps.NonCPS
import com.evernorth.cloudnativebuild.config.ReleaseConstants
import com.evernorth.cloudnativebuild.data.PipelineConstants
import com.evernorth.cloudnativebuild.model.ModuleContract
import com.evernorth.cloudnativebuild.model.ModuleContractType
import com.evernorth.cloudnativebuild.model.StepInvocation
import com.evernorth.cloudnativebuild.model.StepResult
import com.evernorth.cloudnativebuild.model.logging.LogLevel
import com.evernorth.cloudnativebuild.pipeline.steps.ExecuteShellModuleStep
import com.evernorth.cloudnativebuild.pipeline.steps.LoadPipelineModulesStep
import com.evernorth.cloudnativebuild.service.Logger
import com.evernorth.cloudnativebuild.service.ModuleExecutionRules
import com.evernorth.cloudnativebuild.service.PipelineStateManager
import groovy.json.JsonSlurper
import hudson.Functions
import hudson.model.Cause
import org.apache.commons.lang.WordUtils

import static com.cigna.common.utils.Utils.ifNull

class PipelineUtils {
    /**
     * Attempts to determine what branch of pipeline library is being used.
     * If CNP_LIBRARY_NAME_OVERRIDE is set in the environment, use that. Otherwise, get
     * 'library.epf.version' from the environment, which is set by Jenkins (assuming the library is used by name 'epf').
     * @param scriptContext - Jenkins globals
     * @return returns the pipeline library and branch used
     */
    static String pipelineNameAndBranch(def scriptContext, PipelineStateManager manager = null) {
        Logger logger = new Logger(scriptContext)
        if (scriptContext.env.CNP_LIBRARY_NAME_OVERRIDE) {
            logger.log("WARNING: Using library name override. This setting should only be used for testing and may cause release to fail.", LogLevel.WARNING)
            return scriptContext.env.CNP_LIBRARY_NAME_OVERRIDE
        }
        
        def libraryName = 'epf'
        def version = scriptContext.env."library.${libraryName}.version"
        
        return version ? "$libraryName@$version" : libraryName
    }

    /**
     * Adds support for the withModule closure
     * @param scriptContext - Jenkins script context
     * @param filter - The name of the module
     * @param manager - Pipeline state manager
     * @param jenkinsFile - Code inside of closure
     */
    static void executeWithModule(def scriptContext, String filter, PipelineStateManager manager, Closure jenkinsFile) {
        Logger logger = new Logger(scriptContext)
        logger.log("Applying module filter: $filter")
        manager.enableGlobalModuleFilter(filter)
        jenkinsFile()
        logger.log("Removing filter: $filter")
        manager.disableGlobalModuleFilter()
    }


    /**
     * Called from the release verb, creates a marker that we can use to flag code
     * called from inside the release closure
     * @param releaseBlock
     */
    static void executeReleaseClosure(def scriptContext, def properties, PipelineStateManager manager, Closure releaseBlock) {
        if (ModuleExecutionRules.isReleasableBranch(scriptContext, [:]) || scriptContext.env.BRANCH_NAME == "develop") {
            releaseBlock()
            manager.releaseArguments.putAll(properties as Map)
            manager.addPipelineStep(CreateInvocation(verb: "release", arguments: properties, buildSchedule: PipelineConstants.CURRENT_BUILD))
        } else {
            Logger log = new Logger(scriptContext)
            log.log("Release skipped since not a release or develop branch")
        }
    }

    @NonCPS
    static def CreateInvocation(Map invocation = [:]) {
        new StepInvocation(
                verb: invocation.verb?.toLowerCase(),
                arguments: invocation.arguments,
                options: invocation.options,
                completed: ifNull(invocation.completed, false),
                buildSchedule: invocation.buildSchedule ?: PipelineConstants.CALLBACK_BUILD,
                result: invocation.result,
                order: ifNull(invocation.order, -1),
                podIndex: (int) (invocation.podIndex ?: 0),
                retryCount: (int) (invocation.retryCount ?: 0),
                withEnv: invocation.withEnv ?: [:]
        )
    }

    /**
     * Called from the release verb, creates a marker that we can use to flag code
     * called from inside the release closure
     * @param releaseBlock
     */
    static void executePreReleaseClosure(def scriptContext, PipelineStateManager manager, Closure preReleaseBlock) {
        if (ModuleExecutionRules.isReleasableBranch(scriptContext)) {
            preReleaseBlock()
            manager.addPipelineStep(CreateInvocation(verb: "preRelease", arguments: [:], buildSchedule: PipelineConstants.CURRENT_BUILD))
        }
    }


    @NonCPS
    static String createStageNameFromModuleContractType(ModuleContractType type) {
        return WordUtils.capitalize(type?.name()?.toLowerCase()?.replace('_', ' '))
    }

    static void failPipelineIfStepResultFailed(def scriptContext, StepResult result, String message) {
        if (result.commandResult == StepResult.FAILURE) {
            failPipeline(scriptContext, message)
        }
    }

    static void stopPipelineIfNotDeployable(def scriptContext, StepResult result, String message) {
        if (result.commandResult == StepResult.SUCCESS && message == StepResult.COULD_NOT_DEPLOY) {
            scriptContext.echo("""PIPELINE HALTED SINCE DEPLOY CALLED ON NON-DEPLOYABLE BRANCH.
                    If you wish to deploy from a feature branch you need to add or change the value of
                    the CNP_DEPLOYABLE_BRANCHES in your Jenkins job configuration:
                    for example
                    CNP_DEPLOYABLE_BRANCHES: ^develop\$,^release.*,^hotfix.*,^feature.*
                    see the following documentation for how to change your configuration:
                    https://confluence.sys.cigna.com/display/DvOp/EPF+FAQ#expand-EPFPipelineConfiguration
                    """)

            throw new ErrorStepException(message)
        }
    }

    @NonCPS
    static void failPipeline(def scriptContext, String message) {
        scriptContext.currentBuild.result = PipelineConstants.BUILD_RESULT_FAILURE
        scriptContext.error(message)
    }

    static void abortPipeline(def scriptContext, String message) {
        scriptContext.echo("PIPELINE ABORTED")
        throw new ErrorStepException(message)
    }
    /**
     * Uses data in module contract to dynamically determine the stage name for a step
     * @param stateManager - Pipeline stage manager
     * @param type - Module contract type
     * @param moduleName - The name of the module
     * @param options - The options argument from the verb
     * @return String
     */
    @NonCPS
    static String getStageNameFromModuleContract(PipelineStateManager stateManager, ModuleContractType type, String moduleName, Map options = [:]) {
        StringBuffer stageNameBuffer = new StringBuffer()
        stageNameBuffer.append(createStageNameFromModuleContractType(type))
        if (moduleName != null) {
            ModuleContract contract = stateManager.getContractByTypeAndModuleName(type, moduleName)
            stageNameBuffer.append(contract?.stageName ? " ${contract?.stageName}" : "")
        }
        stageNameBuffer.append(options?.get("env") ? " ${options?.get("env")}" : "")
        return stageNameBuffer.toString()
    }

    // This will be used from XLR Callback Handler
    static String getUserId(def scriptContext) {
        return scriptContext.currentBuild.rawBuild.getCause(Cause.UserIdCause)?.getUserId()
    }
    // This will be used from XLR Callback Handler
    static String getUpstreamDescription(def scriptContext) {
        scriptContext.currentBuild.rawBuild.getCause(Cause.UpstreamCause)?.shortDescription
    }

    static String getMasterName(def scriptContext) {
        Logger logger = new Logger(scriptContext)
        String masterName
        try {
            masterName = scriptContext.env.JENKINS_URL.tokenize('/').last()
            if (masterName && masterName.toLowerCase().startsWith("teams-")) {
                masterName = masterName.substring("teams-".length())
            }
        } catch (e) {
            logger.log("Unable to determine master name: ${e.message}", LogLevel.INFO)
            if (FeatureFlags.showStackTraces) {
                logger.log(Functions.printThrowable(e))
            }

            return null
        }

        return masterName
    }

    @NonCPS
    static String urlEncodeString(def value) {
        URLEncoder.encode(value.toString()?.trim(), "UTF-8").replace("+", "%20")
    }

    // This is used from XLR Callback Handler
    static String getField(String deploymentInfo, String fieldName) {
        def jsonSlurper = new JsonSlurper()
        try {
            def object = jsonSlurper.parseText(deploymentInfo)
            if (object instanceof Map)
                return object.get(fieldName)
        }
        catch (ignore) {

        }
        return null
    }

    /**
     * Gets the name of the repo based on URL assuming it is git URL
     * @param gitRepoUrl - example https://git.express-scripts.com/expressScripts/repoName.git
     * @return The name of the repo per example would be repoName
     */
    @NonCPS
    static String getJobRepositoryFromURL(String gitRepoUrl) {
        if (gitRepoUrl == null || gitRepoUrl.trim() == '') return ''
        return gitRepoUrl.substring(gitRepoUrl.lastIndexOf("/") + 1) - '.git'
    }

    /**
     * Derives the base address from the gitRepoUrl and the Jenkins Job name
     * @param scriptContext - Jenkins globals
     * @param gitRepoUrl - The git repo
     * @return
     */
    static String getDeploymentSourceRepoBase(def scriptContext, Logger logger, def gitRepoUrl, boolean isRollbackLocation = false) {
        // always deployment info is published in snapshot repo from the library except from finalize module, which is handled in the module
        // library always looks for rollback package from the release repo
        def artifactoryRepoName = isRollbackLocation ? ReleaseConstants.ARTIFACTORY_DEPLOY_RELEASE_REPO : ReleaseConstants.ARTIFACTORY_DEPLOY_SNAPSHOT_REPO
        String jobRepo = getJobRepositoryFromURL(gitRepoUrl)
        logger.log("artifactoryRepoName for deploymentpackage: ${artifactoryRepoName}")
        def artifactoryRepoPath = "${artifactoryRepoName}/${Utils.calculateRepoPath(scriptContext.env.JOB_NAME)}/${jobRepo}"
        logger.log("artifactoryRepoPath: ${artifactoryRepoPath}")
        return artifactoryRepoPath
    }

    static Map getRepoInfo(def scriptContext) {
        // add scm info to map
        Map repoInfo = [:]
        def repoUrl = scriptContext?.scm?.userRemoteConfigs[0]?.url
        repoInfo.put("repoUrl", repoUrl)
        def branchName = scriptContext?.env?.BRANCH_NAME
        repoInfo.put("branchName", branchName)
        return repoInfo
    }


    static StepResult retrieveDeploymentPackage(PipelineStateContext psc, def scriptContext, PipelineStateManager stateManager, def deploymentPackagePath, def config) {
        ExecuteShellModuleStep executeShellModuleStep = newExecuteShell(psc, scriptContext, config)
        def retrieveContract = stateManager.getFirstContractForContactType(ModuleContractType.RETRIEVE.name())
        Logger logger = newLogger(scriptContext)
        if (!retrieveContract) {
            logger.log("No RETRIEVE Module loaded. Using default.", LogLevel.WARNING)
            def loadModuleStep = newPipelineModulesStep(psc, scriptContext)
            def module = loadModuleStep.getDefaultStateReaderModule()
            stateManager.add(module)
        }
        Map properties = ["archiveUrl": deploymentPackagePath?.replace(' ', '%20'), "expand": true]
        logger.log("archiveUrl: ${properties.get("archiveUrl")}", LogLevel.TRACE)
        StepResult retrieveResult = executeShellModuleStep?.execute(ModuleContractType.RETRIEVE, retrieveContract?.moduleName, properties)
        return retrieveResult
    }

    @NonCPS
    private static ExecuteShellModuleStep newExecuteShell(PipelineStateContext psc, scriptContext, config) {
        return new ExecuteShellModuleStep(psc, scriptContext, config)
    }

    @NonCPS
    private static LoadPipelineModulesStep newPipelineModulesStep(PipelineStateContext psc, scriptContext) {
        return new LoadPipelineModulesStep(psc, scriptContext)
    }

    @NonCPS
    private static Logger newLogger(scriptContext) {
        return new Logger(scriptContext)
    }

    @NonCPS
    static void addGlobalFilter(Map options, PipelineStateManager manager) {
        if (manager.globalModuleFilter) {
            if (options.containsKey("filter")) {
                throw new IllegalArgumentException("Syntax error in your Jenkinsfile: verbs called inside a withModule block cannot have a filter option.")
            }
            options.put("filter", manager.globalModuleFilter)
        }
    }

    static void unStash(def scriptContext, String stashName, Logger logger = new Logger(scriptContext)) {
        def normalizedStashName = StashUtils.normalize(stashName)
        try {
            logger.log("Attempting unstashing $normalizedStashName")
            scriptContext.unstash(name: normalizedStashName)
            logger.logShellCommand("ls -la", LogLevel.TRACE)
        } catch (ex) {
            if (FeatureFlags.showStackTraces) {
                logger.log(Functions.printThrowable(ex))
            }

            logger.logError("failed to unstash ${normalizedStashName}", ex)
        }
    }

    static void stash(def scriptContext, String stashName, String stashPattern, String excludesPattern = "", Logger logger = new Logger(scriptContext)) {
        def normalizedStashName = StashUtils.normalize(stashName)
        try {
            logger.log("Creating stash for ${normalizedStashName} with includes: ${stashPattern} and excludes: ${excludesPattern}", LogLevel.TRACE)
            scriptContext.stash(name: normalizedStashName, includes: stashPattern, excludes: excludesPattern)
        }
        catch (ex) {
            if (FeatureFlags.showStackTraces) {
                logger.log(Functions.printThrowable(ex))
            }

            logger.logError("failed to create stash for ${normalizedStashName}", ex)
        }
    }
}