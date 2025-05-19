package com.evernorth.cloudnativebuild.pipeline.steps

import com.cigna.base.DockerPipelineLib
import com.cigna.common.utils.FeatureFlags
import com.cigna.state.PipelineStateContext
import com.cloudbees.groovy.cps.NonCPS
import com.evernorth.cloudnativebuild.config.ReleaseConstants
import com.evernorth.cloudnativebuild.data.PipelineConstants
import com.evernorth.cloudnativebuild.model.*
import com.evernorth.cloudnativebuild.model.logging.LogLevel
import com.evernorth.cloudnativebuild.pipeline.PipelineUtils
import com.evernorth.cloudnativebuild.service.Logger
import com.evernorth.cloudnativebuild.service.PipelineStateManager
import com.evernorth.cloudnativebuild.service.ResultParser
import groovy.json.JsonBuilder
import hudson.Functions

/**
 * Stores information on current pipeline configuration
 * and execution parameters into JSON file
 */
class StoreReleaseMetadataStep extends DockerPipelineLib {
    Logger logger

    StoreReleaseMetadataStep(PipelineStateContext psc, def script, def config = [:]) {
        this.script = script
        this.config = config
        logger = new Logger(script)
        this.psc = psc
    }
    /***
     * Creates a release file and stores it to artifactory
     * @param releaseStep ReleaseStep instance
     * @param releaseArguments - Properties passed to the release verb in the Jenkinsfile. Empty Map for preRelease verb
     * @return the URL to the metadata package
     */
    static String createAndStoreReleaseInfoFile(def script, def config, PipelineStateContext psc, Map releaseArguments = [:]) {
        ensureRepoInfoInReleaseArguments(script, releaseArguments)
        StoreReleaseMetadataStep storeReleaseMetadataStep = newReleaseMetadataStep(psc, script, config)

        boolean releaseArtifactExists = storeReleaseMetadataStep.ensureReleaseArgumentsContainsArtifact(releaseArguments)
        if (!releaseArtifactExists) {
            PipelineUtils.failPipelineIfStepResultFailed(script, StepResult.failed(), "No deployable artifact found for this build.")
            return ""
        }
        StepResult storeMetaDataResult = storeReleaseMetadataStep.execute(releaseArguments)
        PipelineUtils.failPipelineIfStepResultFailed(script, storeMetaDataResult, "Failed to publish Release Metadata to Artifactory")
        return storeMetaDataResult.commandOutput?.get(ReleaseConstants.PUBLISH_URL_PROPERTY_NAME)
    }

    @NonCPS
    private static StoreReleaseMetadataStep newReleaseMetadataStep(def psc, def script, def config) {
        return new StoreReleaseMetadataStep(psc, script, config)
    }

    /**
     * Ensures that the release arguments contain repoUrl and branchName properties
     * When called from release block, repo arguments should be coming from XLR,
     * when in prerelease we derive from current repo
     * @param script - Jenkins globals
     * @param stateManager - Object that represents state of multi-build workflow
     * @param releaseArguments - Set of release wide arguments that apply to all verbs in release or preRelease block
     * @return true when arguments can be added
     */
    static boolean ensureRepoInfoInReleaseArguments(def script, Map releaseArguments) {
        if (releaseArguments == null) return false
        // if values are set do nothing
        if (releaseArguments.get(ReleaseConstants.REPO_URL_PROPERTY_NAME)
                && releaseArguments.get(ReleaseConstants.BRANCH_NAME_PROPERTY_NAME)) return true

        // values missing get from current repo
        releaseArguments.putAll(PipelineUtils.getRepoInfo(script))
        return true
    }

    /**
     * MVP 1 limitation is we support only one release artifact. Monorepo with many artifacts are not supported
     * @param stateManager - Object that represents state of multi-build workflow
     * @param releaseArguments - Set of release wide arguments that apply to all verbs in release or preRelease block
     * @return true if release arguments could be added, false otherwise
     */
    boolean ensureReleaseArgumentsContainsArtifact(Map releaseArguments = [:]) {
        if (!releaseArguments) return false
        //artifact name passed as release arg, no need to add one
        if (releaseArguments.get(ReleaseConstants.ARTIFACT_PROPERTY_NAME)) return true

        // if we have no steps in stateManager we cannot proceed
        if (!psc.globalModuleManager.stateManager()?.pipelineSteps) return false
        def artifact = generateArtifactPath(psc.globalModuleManager.stateManager(), releaseArguments)
        logger.log("Artifact Path: ${artifact}", LogLevel.TRACE)
        if (artifact) {
            releaseArguments.put(ReleaseConstants.ARTIFACT_PROPERTY_NAME, artifact)
            return true
        }
        // if we are here developer did not pass and we can't figure it out
        // no release arguments found, return false
        return false
    }

    /**
     * Creates a release file and stores it to artifactory
     * @param pipelineStateManager - stores pipeline state
     * @param releaseArguments - arguments passed to release verb, defaults to empty Map for preRelease
     * @return StepResult indicating success of write operation
     */
    StepResult execute(Map releaseArguments) {
        if (!releaseArguments) return StepResult.failed("No Release Arguments found.")
        getStateWriter()
        String repoUrl = releaseArguments.get("repoUrl")
        def artifactPath = releaseArguments.get(ReleaseConstants.ARTIFACT_PROPERTY_NAME)
        logger.log("artifactPath from releaseArguments: ${releaseArguments} is ${releaseArguments.get(ReleaseConstants.ARTIFACT_PROPERTY_NAME)}", LogLevel.TRACE)

        ExecuteShellModuleStep step = newShellModule()

        StepResult shellExecutionResult
        String archiveName = generateZipFileName(getRepositoryName(repoUrl),
                getReleaseVersionFromArtifact(artifactPath))
        def deploymentZipLocation = PipelineUtils.getDeploymentSourceRepoBase(this.script, logger, repoUrl)
        String archivePath = deploymentZipLocation.replace(' ', '%20')
        psc.globalModuleManager.stateManager().setDeploymentPackagePath("${archivePath}/${archiveName}")

        writePipelineConfigToJsonFile( releaseArguments, artifactPath, archiveName, repoUrl, "")
        releaseArguments.put("archivePattern", script.env.DEFAULT_INCLUSION_PATTERN)
        releaseArguments.put("archiveName", archiveName)
        releaseArguments.put("archivePath", archivePath)
        shellExecutionResult = StepResult.aggregateResults(step.executeSubStep(ModuleContractType.PIPELINE_STATE_WRITER, releaseArguments))

        logger.log("StoreReleaseMetadataStep result: ${shellExecutionResult}", LogLevel.TRACE)

        // verify that the publishUrl was added to the results object, if missing fail step
        if (!shellExecutionResult?.commandOutput?.get(ReleaseConstants.PUBLISH_URL_PROPERTY_NAME)) {
            return StepResult.failed("The Pipeline State Writer reported success but did not return a Publish URL")
        }
        return shellExecutionResult
    }

    @NonCPS
    private ExecuteShellModuleStep newShellModule() {
        return new ExecuteShellModuleStep(psc, script, config)
    }

    private static String getRepositoryName(String repoUrl) {
        return repoUrl.substring(repoUrl.lastIndexOf('/') + 1, repoUrl.lastIndexOf('.'))
    }

    @NonCPS
    private static String getReleaseVersionFromArtifact(def artifact) {
        if (!artifact)
            return artifact
        if (artifact instanceof List<String>) {
            artifact = artifact.get(0)
        }
        // return version if artifact looks like a release branch
        if (artifact ==~ '^release/v.*')
            return artifact.substring(artifact.lastIndexOf('/v') + 1)
        // return version if we can infer it using last hyphen to last dot substring
        def hyphenPosition = artifact.lastIndexOf('-')
        def dotPosition = artifact.lastIndexOf('.')
        def usPosition = artifact.lastIndexOf('_')
        def colPosition = artifact.lastIndexOf(':')
        if (colPosition > -1 && usPosition > -1 && colPosition < usPosition)
            return artifact.substring(colPosition + 1, usPosition)
        if (hyphenPosition > -1 && dotPosition > -1 && hyphenPosition < dotPosition)
            return artifact.substring(hyphenPosition + 1, dotPosition)
        // any other case, just return the input
        return artifact
    }

    /**
     * This version is used when we only have a single module and single production  deployment
     * @param releaseModule - The Release module configured in pipeline
     * @param releaseArguments - The Map passed to the release verb
     * @return true if file written successfully
     */
    String writePipelineConfigToJsonFile(Map releaseArguments, def artifactPath, String zipFileName, def repoUrl, String releaseId) {
        logger.log("zipFileName: ${zipFileName}", LogLevel.TRACE)
        if (!zipFileName) {
            PipelineUtils.failPipeline(script, "WARNING: Build Archive cannot be built because we are not able to derive the artifact name.")
            return ""
        }
        if (script.fileExists(zipFileName)) {
            script.sh("rm ${zipFileName}")
        }
        def rollbackPackagePath = newStartRelease(repoUrl).getRollbackPath(repoUrl as String)
        psc.globalModuleManager.stateManager().setRollbackDeploymentPackagePath(rollbackPackagePath)

        // the podCloud value never gets set by EPF machinery, so change it before writing the release file so we'll use
        // the same cloud in the callback jobs
        if (config?.cloudName) {
            logger.log("Changing cloud name from '${psc.globalModuleManager.stateManager().configuration.podCloud}' to '${config.cloudName}' in build configuration", LogLevel.TRACE)
            psc.globalModuleManager.stateManager().configuration.podCloud = config.cloudName
        }

        logger.log("rollbackPackagePath: ${rollbackPackagePath}", LogLevel.TRACE)
        ReleaseInfo releaseConfiguration = createReleaseConfiguration(
                script,
                psc.globalModuleManager.stateManager(),
                releaseArguments,
                releaseId as String,
                artifactPath,
                rollbackPackagePath as String,
                repoUrl as String)

        def jsonDataString = newJsonBuilder(releaseConfiguration).toPrettyString()
        logger.log(jsonDataString, LogLevel.TRACE)
        try {
            logger.logShellCommand("ls -la", LogLevel.TRACE)
            script.writeFile(file: "releaseInfo.json", text: jsonDataString)
            logger.logShellCommand("cat releaseInfo.json", LogLevel.TRACE)
        }
        catch (e) {
            if (FeatureFlags.showStackTraces) {
                logger.log(Functions.printThrowable(e))
            }
            logger.logError("Failed writing zip file. Release failed.", e, LogLevel.ERROR)
            PipelineUtils.failPipeline(script, "Release halted because zip file containing configuration and artifacts could not be created.")
            return ""
        }

        return zipFileName
    }

    @NonCPS
    private JsonBuilder newJsonBuilder(ReleaseInfo releaseConfiguration) {
        return new JsonBuilder(releaseConfiguration)
    }

    @NonCPS
    private StartReleaseStep newStartRelease(repoUrl) {
        return new StartReleaseStep(script, psc, config)
    }


    static final String AUTOMATIC_ARTIFACT_PATH_NOT_POSSIBLE = """
***********
Unable to determine artifact path because artifact was not passed 
as a release argument and we could not find determine the artifact name from build step. 

This issue can be caused when you have more then one build module configured. 
When more then one build module is configured we cannot automatically inject the artifact information into the deploy and release steps because
we do not know which module's output to use. If you are using more then one build module
you must capture the output for each and then pass the information into the release step.

For example:
def npmStepResult = build([publish:true],"npm")
def javaStepResult = build([goals:"test install", publish:true],"java")
release{
    deploy([artifact:npmStepResult.commandOutput.publishUrl]),"deploy-aws-static") 
    deploy([artifact:javaStepResult.commandOutput.publishUrl]),"deploy-pcf")
}
***********
"""

    private String getArtifactPathFromScm( Map releaseArguments) {
        boolean artifactlessModule = hasArtifactlessDeployStep(psc.globalModuleManager.stateManager())
        String artifactVersion = createArtifactVersionFromBranchName( releaseArguments)
        if (artifactlessModule) {
            return "release/v${artifactVersion}"
        }
        return ""
    }

    private String createArtifactVersionFromBranchName( Map releaseArguments) {
        String repoUrl = releaseArguments.get(ReleaseConstants.REPO_URL_PROPERTY_NAME)
        String branchName = releaseArguments.get(ReleaseConstants.BRANCH_NAME_PROPERTY_NAME)
        String artifactVersion = getReleaseArtifactVersion(repoUrl, branchName)
        return artifactVersion
    }

    private String createVersionNumberFromBuildResult() {
        def publishUrl = psc.metadata.get("publishUrl")
        if (publishUrl) {
            return publishUrl
        }
        // find a completed successful build step
        List<StepInvocation> buildContracts = psc.globalModuleManager.stateManager().pipelineSteps.findAll {
            step -> step.verb == PipelineConstants.VERB_BUILD && step.completed && step.result?.commandResult == StepResult.SUCCESS && step.result.commandOutput.containsKey(ReleaseConstants.PUBLISH_URL_PROPERTY_NAME)
        }
        // if we have more then one build step or no build steps then we need developer to pass in correct value
        if (buildContracts?.size() != 1) {
            logger.log(AUTOMATIC_ARTIFACT_PATH_NOT_POSSIBLE, LogLevel.WARNING)
            return ""
        }
        // if not found in verb args look in step results from a build step
        // The pipeline should not fail if the file is not found, instead this method should return "", so module will fail
        if (buildContracts[0]) {
            StepResult result = buildContracts[0].result
            // sometimes commandOutput is list of artifacts rather then string
            // in this case, we cannot do automatic assignment
            if (isAString(result)) {
                return result.commandOutput[ReleaseConstants.PUBLISH_URL_PROPERTY_NAME]
            } else {
                logger.log(AUTOMATIC_ARTIFACT_PATH_NOT_POSSIBLE, LogLevel.WARNING)
            }
        }
        return ""
    }

    @NonCPS
    private boolean isAString(StepResult result) {
        return result.commandOutput[ReleaseConstants.PUBLISH_URL_PROPERTY_NAME] instanceof String
    }

    /**
     * This method is for cases where artifact path is not passed to any of the verb arguments and
     * in Jenkins file. In this case we will attempt to derive this from other data available in the build
     * 1. Look if exists in deferred steps verb arguments
     * 2. See if we have a build step and try and get from StepResult
     * 3. if archive type is git then get repourl and tag (treated as artifactversion)
     * @param pipelineStateManager -  tracks state of build and release workflow across builds
     * @param releaseArguments - release wide arguments
     * @return path to artifact
     */
    private def generateArtifactPath(PipelineStateManager pipelineStateManager, Map releaseArguments) {
        logger.log("No artifact was passes a release argument, checking deferred steps.", LogLevel.TRACE)
        StepInvocation deployStep = findDeployStepWithArtifact(pipelineStateManager)
        if (deployStep) {
            return ResultParser.resolveDeferredValuePointer(deployStep.arguments.get(ReleaseConstants.ARTIFACT_PROPERTY_NAME), pipelineStateManager)
        }

        logger.log("No artifact found in deferred steps. Checking if artifactless release", LogLevel.TRACE)
        // this applies to terraform type of deployments
        String gitTagVersion = getArtifactPathFromScm(releaseArguments)
        if (gitTagVersion) {
            return gitTagVersion
        }

        logger.log("No artifact found in build results. Checking if artifactless release.", LogLevel.TRACE)
        return createVersionNumberFromBuildResult()
    }

    @NonCPS
    private static boolean hasArtifactlessDeployStep(PipelineStateManager pipelineStateManager) {
        def artifactlessModule = pipelineStateManager.getContracts()?.find {
            it.artifactType == PipelineConstants.ARTIFACT_TYPE_GIT_TAG || it.artifactType == PipelineConstants.getARTIFACT_TYPE_CONTAINER()
        }
        return artifactlessModule != null
    }

    @NonCPS
    private static StepInvocation findDeployStepWithArtifact(PipelineStateManager pipelineStateManager) {
        StepInvocation deployStep = pipelineStateManager.pipelineSteps.find { StepInvocation step ->
            step.verb == PipelineConstants.VERB_DEPLOY && (step.arguments as Map)?.containsKey(ReleaseConstants.ARTIFACT_PROPERTY_NAME)
        }
        return deployStep
    }

    private static String generateZipFileName(String repositoryName, String artifactVersion) {
        return "${ReleaseConstants.DEPLOYMENT_PACKAGE_PROPERTY_NAME}_${repositoryName}_${artifactVersion}.zip"
    }


    @NonCPS
    private static BuildInfo createBuildInfo(def script, PipelineStateManager pipelineStateManager, def repoUrl) {
        BuildInfo buildInfo = new BuildInfo()
        buildInfo.buildType = "Cloud Native Modular Build"
        buildInfo.cloudNativePipelineModuleConfiguration = pipelineStateManager.printModuleList()
        buildInfo.buildNumber = script.env.BUILD_NUMBER
        buildInfo.buildName = script.env.BUILD_NAME ?: script.env.BUILD_TAG
        buildInfo.buildUrl = script.env.BUILD_URL
        buildInfo.buildDisplayName = script.env.BUILD_DISPLAY_NAME ?: script.env.BUILD_TAG
        buildInfo.jobName = script.env.JOB_NAME
        buildInfo.commitId = pipelineStateManager.releaseArguments.get("commitId")
        buildInfo.branchName = pipelineStateManager.releaseArguments.get("branchName")
        buildInfo.buildDescription = script.currentBuild.description
        buildInfo.buildTag = script.env.BUILD_TAG
        buildInfo.jobUrl = script.env.JOB_URL
        buildInfo.workspace = script.env.WORKSPACE
        buildInfo.jobRepository = PipelineUtils.getJobRepositoryFromURL(repoUrl as String)
        buildInfo.artifactoryDockerUrl = script.env.CNP_IMAGE_REPO
        buildInfo.artifactoryRootUrl = script.env.ARTIFACTORY_ROOT_URL
        buildInfo.artifactoryCredential = script.env.ARTIFACTORY_CREDENTIAL
        buildInfo.npmCredentialsId = script.env.NPM_CREDENTIALS_ID
        buildInfo.gitCredential = script.env.GIT_CREDENTIAL
        buildInfo.jobBuildCred = script.env.JOB_BUILD_CRED
        buildInfo.xlReleaseUrlAPI = script.env.CX_SERVER_ADDRESS
        return buildInfo
    }

    @NonCPS
    static ReleaseInfo createReleaseConfiguration(def script, PipelineStateManager manager, Map releaseArguments, String releaseId, def artifactPath, String rollbackPackagePath, String repoUrl) {
        // make sure the config is synced from the environment
        new LoadConfigStep(script).updateDefaultBuildConfigurationFromEnvironment(manager.configuration)

        // Explore the option of filtering at source pipelineStateManager.releaseArguments
        // This list is from the module supported args and the ones used internally
        List permittedArgs = ["Release_JenkinsMaster", "Release_BuildUri", "repoUrl", "branchName", "Release_rollbackDeploymentPackagePath", "touchless", "candidateImageNames", "commitId", "artifact", "archivePattern", "archiveName", "archivePath", "pipelineLibraryName", "podCloud"]
        ReleaseInfo releaseInfo = new ReleaseInfo()
        releaseInfo.executionDateTime = new Date()
        releaseInfo.moduleContractList = manager.getContracts()
        releaseInfo.buildConfiguration = manager.configuration
        releaseInfo.buildInfo = createBuildInfo(script, manager, repoUrl)
        releaseInfo.releaseArguments = releaseArguments
        releaseInfo.releaseArguments.putAll(manager.releaseArguments.subMap(permittedArgs))
        releaseInfo.releaseId = releaseId
        releaseInfo.artifact = artifactPath
        releaseInfo.rollbackPackagePath = rollbackPackagePath
        releaseInfo.steps = manager.pipelineSteps
        releaseInfo.deploymentPackagePath = manager.getDeploymentPackagePath()
        return releaseInfo
    }

    /**
     * Calls script that reads files in the repo to attempt to determine version number
     * @param stateManager - pipeline state manager
     * @param repoUrl - The URL of the git repo for the project we are building
     * @param branchName - The git branch being built
     * @return artifact path
     */
    String getReleaseArtifactVersion( def repoUrl, def branchName) {
        ModuleContract publishContract = getStateWriter()
        def artifactVersion = ""
        logger.log("Executing in StoreReleaseMetadataStep::getReleaseArtifactVersion (${script.env.GIT_CREDENTIAL})")
        script.container(config.containerName) {
            logger.log("Container: ${config.containerName}", LogLevel.INFO)
            String command = 'cnp-release-util-get-artifact-version.sh'
            script.withCredentials([
                    script.usernamePassword(
                            credentialsId: script.env.GIT_CREDENTIAL,
                            passwordVariable: 'gitpass',
                            usernameVariable: 'gituser')
            ]) {
                script.echo("export CNP_RELEASE_UTIL_GIT_USER=***** && export CNP_RELEASE_UTIL_GIT_CRED=***** && ${command} ${repoUrl} ${branchName}")
                artifactVersion = script.sh(returnStdout: true, script: "export CNP_RELEASE_UTIL_GIT_USER=${script.gituser} && export CNP_RELEASE_UTIL_GIT_CRED=${script.gitpass} && ${command} ${repoUrl} ${branchName}")?.trim()
                logger.log("Executing... $command and got artifactVersion: ${artifactVersion}", LogLevel.INFO)
            }

        }
        return artifactVersion
    }

    ModuleContract getStateWriter() {
        def publishContract = psc.globalModuleManager.stateManager().getFirstContractForContactType(ModuleContractType.PIPELINE_STATE_WRITER.name())
        if (!publishContract) {
            logger.log("No PIPELINE_STATE_WRITER Module Configured. Using default.", LogLevel.WARNING)
            def loadModuleStep = newLoadPipeline()
            publishContract = loadModuleStep.getDefaultStateWriterModule()
            psc.globalModuleManager.stateManager().add(publishContract)
        }
        publishContract
    }

    @NonCPS
    private LoadPipelineModulesStep newLoadPipeline() {
        return new LoadPipelineModulesStep(psc, script)
    }
}
