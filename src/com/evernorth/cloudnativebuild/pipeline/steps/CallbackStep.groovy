package com.evernorth.cloudnativebuild.pipeline.steps

import com.cigna.base.DockerPipelineLib
import com.cigna.common.exception.ErrorStepException
import com.cigna.common.utils.FeatureFlags
import com.cigna.state.PipelineStateContext
import com.cloudbees.groovy.cps.NonCPS
import com.evernorth.cloudnativebuild.data.PipelineConstants
import com.evernorth.cloudnativebuild.model.StepResult
import com.evernorth.cloudnativebuild.pipeline.PipelineUtils
import com.evernorth.cloudnativebuild.service.Logger
import com.evernorth.cloudnativebuild.service.PipelineStateManager
import hudson.Functions

/**
 * This class contains methods called by external Jenkins jobs or 3rd party release managers
 * to perform steps in the production release process. These jobs are typically called from a
 * special folder in Jenkins that contains production credentials not available in normal folders.
 * The class supports 4 job types:
 *   Stage: - In Blue / Green Deployments where code is deployed to staging route but not exposed to production users
 *   Release: Release new version into production. This may be promotion of a blue / green deployment or deploys new version of application.
 *   Rollback: Revert to previous version of the application
 *   Finalize: Close a release and create version tags
 */
class CallbackStep extends DockerPipelineLib {

    Logger logger

    PipelineStateContext psc

    CallbackStep(PipelineStateContext psc, def script, def config = [:]) {
        this.script = script
        this.config = config
        this.logger = new Logger(script)
        this.psc = psc
    }
    static final String NOT_AUTHORIZED = "You do not have access to build a Production Job. Reach out to Jenkins-Pipeline or Release Management Team for assistance"
    static final String ROLL_BACK_JOB = "Executing Rollback"
    static final String FINALIZE_JOB = "Completing Release"
    static final String RELEASE_JOB = "Promoting new version of application to production"
    static final String STAGE_JOB = "Deploying new release candidate to staging route"

    StepResult execute() {
        createJobParams()
        try {
            //prod release can be done only by upstream or xlr
            String upstreamDescription = PipelineUtils.getUpstreamDescription(script)
            logger.log("upstreamDescription: ${upstreamDescription}")

            if (upstreamDescription == null && !isAuthorizedUser()) {
                script.error(NOT_AUTHORIZED)
                return StepResult.failed(NOT_AUTHORIZED)
            }

            logger.log("Incoming job params: ${script.params}")

            if (script.params.isCandidate) {
                stageRelease()
            } else if (script.params.isRollback) {
                rollbackRelease()
            } else if (script.params.isFinalize) {
                finalizeRelease()
            } else {
                releaseToProduction()
            }
        } catch (ex) {
            // We don't want to fail the build in case we need to rollback.
            // We still want to return values to 3rd party release manager
            logger.log("Build Failed During Deployment ${ex}")
            if (FeatureFlags.showStackTraces) {
                logger.log(Functions.printThrowable(ex))
            }

            script.currentBuild.result = PipelineConstants.BUILD_RESULT_UNSTABLE
            StepResult result = StepResult.empty()
            result.commandResult = PipelineConstants.BUILD_RESULT_UNSTABLE
            return result
        }

        return StepResult.empty()
    }

    private void releaseToProduction() {
        logger.log(RELEASE_JOB)
        StepResult result = newCompleteRelease().execute (psc.globalModuleManager.stateManager(),
            script.params.deploymentInfo as String)
        if (result?.commandResult == StepResult.FAILURE) {
            // we throw the exception here so that we can execute common exception logic
            throw error()
        }
    }

    @NonCPS
    private ErrorStepException error() {
        return new ErrorStepException("Release step failed.")
    }

    private void stageRelease() {
        logger.log(STAGE_JOB)
        newCompletePreRelease().execute(psc.globalModuleManager.stateManager(),
            script.params.deploymentInfo as String)
    }

    @NonCPS
    private CompletePreReleaseStep newCompletePreRelease() {
        return new CompletePreReleaseStep(script, psc)
    }

    private void finalizeRelease() {
        logger.log(FINALIZE_JOB)
        newFinalizeRelease().execute(
            [jiraProject          : script.params.JiraProject,
             jiraFixVersion       : script.params.JiraFixVersion,
             podCloud             : PipelineUtils.getField(script.params.deploymentInfo as String, 'podCloud'),
             repoUrl              : PipelineUtils.getField(script.params.deploymentInfo as String, 'repoUrl'),
             branchName           : PipelineUtils.getField(script.params.deploymentInfo as String, "branchName"),
             xlReleaseId          : script.params.xlReleaseId,
             deploymentPackagePath: PipelineUtils.getField(script.params.deploymentInfo as String, 'archivePath') + '/' + PipelineUtils.getField(script.params.deploymentInfo as String, 'archiveName')],
            [:])
    }

    @NonCPS
    private FinalizeReleaseStep newFinalizeRelease() {
        return new FinalizeReleaseStep(psc, script)
    }

    private void rollbackRelease() {
        logger.log(ROLL_BACK_JOB)
        newCompleteRelease().execute(psc.globalModuleManager.stateManager(),
            script.params.deploymentInfo as String,
            script.params.deploymentPackagePath as String,
            "rollback")
    }

    @NonCPS
    private CompleteReleaseStep newCompleteRelease() {
        return new CompleteReleaseStep(psc, script, config)
    }


    /**
     * Creates Job Params that can be used when executing the job
     * from the Jenkins web application
     */
    def createJobParams() {
        /**
         In case of prerelease, the deploymentInfo param will be having value like,
         {"deploymentPackagePath"="https://artifactory.express-scripts.com/artifactory/ci-snapshot-local/Cloud%20Native%20Pipeline/greetings-ui/deploymentPackage_greetings-ui-1.0.0-beta.30.zip"}
         and isCandidate will be true and the build will be initiated by the library
         In case of release, the build will be initiated by XLR
         */
        def params = script.properties([script.parameters([
            script.string(defaultValue: '', description: 'XLRelease Id for this build', name: 'xlReleaseId', trim: false),
            script.string(defaultValue: '', description: 'Jira Project Name', name: 'JiraProject', trim: false),
            script.string(defaultValue: '', description: 'Jira Fix Version', name: 'JiraFixVersion', trim: false),
            script.string(defaultValue: '',
                description: 'Json containing details about deployment, eg, Path of the deployment package in artifactory which contains release information, in json element name, deploymentPackagePath, isCandidate',
                name: 'deploymentInfo',
                trim: true),
            script.string(defaultValue: '', description: 'Rollback package deployment path from XLR', name: 'deploymentPackagePath', trim: false),
            script.booleanParam(defaultValue: false, description: 'Is this rollback deployment?', name: 'isRollback'),
            script.booleanParam(defaultValue: false, description: 'Is this finalize?', name: 'isFinalize'),
            script.booleanParam(defaultValue: false, description: 'Is this Candidate?', name: 'isCandidate'),
        ])])
        return params
    }

    /**
     * verifies that current user is authorized to execute a production
     * deploy or cutover
     * @return
     */
    boolean isAuthorizedUser() {
        def userId = PipelineUtils.getUserId(script)

        def authorizedUsersList = script.env.AUTHORIZED_USERS ? script.env.AUTHORIZED_USERS.toLowerCase().replaceAll("\\s", "").split(',') : ['internal\\svp_pipeline_admin']
        logger.log("Build started by userId: ${userId} [AUTHORIZED_USERS=${authorizedUsersList}]")
        return userId?.toLowerCase() in authorizedUsersList
    }


}
