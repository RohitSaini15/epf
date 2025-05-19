package com.cigna.release

import com.cigna.base.Phase
import com.cigna.common.exception.ErrorStepException
import com.cigna.common.exception.PreReleaseException
import com.cigna.common.utils.FeatureFlags
import com.cigna.common.utils.Utils
import com.cigna.linting.Linting
import com.cigna.modules.Module
import com.cloudbees.groovy.cps.NonCPS
import com.evernorth.cloudnativebuild.data.PipelineConstants
import com.evernorth.cloudnativebuild.model.BaseDefaults
import com.evernorth.cloudnativebuild.model.ModuleContractType
import com.evernorth.cloudnativebuild.model.StepInvocation
import com.evernorth.cloudnativebuild.model.logging.LogLevel
import com.evernorth.cloudnativebuild.pipeline.PipelineUtils
import com.evernorth.cloudnativebuild.pipeline.steps.RunStepsFromFileStep
import com.evernorth.cloudnativebuild.pipeline.steps.StartPreReleaseStep
import com.evernorth.cloudnativebuild.pipeline.steps.StartReleaseStep
import com.evernorth.cloudnativebuild.service.CredentialManager
import com.evernorth.cloudnativebuild.service.Logger
import com.evernorth.cloudnativebuild.service.ModuleExecutionRules
import com.evernorth.cloudnativebuild.service.PipelineStateManager
import hudson.Functions

import static com.cigna.common.utils.Utils.mergeMaps
import static com.evernorth.cloudnativebuild.service.ModuleExecutionRules.findReleasePattern
import static com.evernorth.cloudnativebuild.service.ModuleExecutionRules.isReleasableBranch

class Release extends Module {
    def awaitApprovalContract = [
        moduleName  : 'awaitApproval',
        contractName: ModuleContractType.APPROVAL.name(),
        commandName : "awaitApproval",
        subCommand  : "awaitApproval",
        image       : BaseDefaults.defaultDockerImage
    ]

    Release() {
        groupID = 'release'
        accumulateSubphaseContainers = false
    }
    final String JIRASCAN_ENDPOINT = "https://jira-services-ea-1-prod.apps-2.hs-1-prod.openshift.cignacloud.com/v1/jiraCloud/project/fixVersion/issues"
    PipelineStateManager stateManager
    def moduleConfig = [:]

    @Override
    def displayName(def prefix = '') {
        def simpleName = super.displayName(prefix)
        "$prefix$simpleName(${config.releaseType})"
    }

    @Override
    protected Boolean configureModule() {
        config.phases.findAll {
            currentBranchMatches(it.branchPattern ?: config.branchPattern)
        }.each { Map phase ->
            if (phase.containsKey('phaseInstance') && phase.phaseInstance instanceof Module) {
                (phase.phaseInstance as Module).configureModule()
            } else {
                if (!phase.containsKey('lintingTypes')) {
                    // revisit this logic to allow nested phases to contain any type of phase
                    throwException("Cannot include non-module phase (${(phase as Phase).displayName()}) in a release or pre-release phase")
                }
            }
        }
        super.configureModule()
    }

    @Override
    Boolean postPodConfig() {
        // We do not support running release sub phases in different clouds or pod groups; they must be identical to the
        // parent or epf will not be able to schedule them correctly.
        config.phases = config.phases.collect {
            it.cloudName = config.cloudName
            it.podGroup = config.podGroup
            it
        }
        super.postPodConfig()
    }

    // We need to merge our wrapped config *before* validate() is called
    @Override
    void preValidate() {
        moduleConfig = [
            moduleType: 'release',
            moduleName: 'cnp-release-xlr',
            subCommand: 'release',
            withEnv   : [
                "CNP_XLR_TEMPLATE_URL=${script.env.CNP_XLR_TEMPLATE_URL}",
                "JIRASCAN_ENDPOINT=${script.env.JIRASCAN_ENDPOINT ?: JIRASCAN_ENDPOINT}",
                "CNP_ASN_VALIDATE_ENABLED=${script.env.CNP_ASN_VALIDATE_ENABLED}"
            ]
        ]
        mergeMaps(config, moduleConfig)
        super.preValidate()
    }

    @Override
    protected void runImpl(def args) {
        if (isReleasePhase(config)) {
            script.echo("Pre-Release Phase detected, proceeding with pre-release steps")
            /**
             * 1. Generate the releaseinfo.json
             * 2. call cnp-release-util.sh publish
             * 3. Call candidate deployer using cnp-jenkins-job-build.sh build
             * 4. call cnp-release-util.sh publish // this includes callback step in the releaseinfo
             * 5. cnp-release-xlr.sh release*/
            addPipelineSteps()

            performTasks()
        } else {
            script.echo("Not a releasable branch, skipping preRelease steps")
        }
    }

    def createExecutionPlans(RunStepsFromFileStep runStepsFromFileStep) {
        try {
            List<StepInvocation> executionPlan = runStepsFromFileStep.createExecutionPlan(PipelineConstants.CURRENT_BUILD)
            List<StepInvocation> preReleaseExecutionPlan = runStepsFromFileStep.createExecutionPlan(PipelineConstants.PRERELEASE_BUILD)
            List<StepInvocation> releaseExecutionPlan = runStepsFromFileStep.createExecutionPlan(PipelineConstants.CALLBACK_BUILD)
            return [executionPlan, preReleaseExecutionPlan, releaseExecutionPlan]
        } catch (ErrorStepException ex) {
            if (FeatureFlags.showStackTraces) {
                script.echo(Functions.printThrowable(ex))
            }
            runStepsFromFileStep.failPipelineWhenNoModuleContract(ex.message)
            return [null, null, null]
        }
    }

    @NonCPS
    private Logger newLogger(scriptContext) {
        return new Logger(scriptContext)
    }

    private void performTasks() {
        def logger = newLogger(script)
        RunStepsFromFileStep runStepsFromFileStep = newRunSteps()
        def (executionPlan, preReleaseExecutionPlan, releaseExecutionPlan) = createExecutionPlans(runStepsFromFileStep)
        logger.log(this.stateManager.printModuleList(), LogLevel.INFO)
        CredentialManager.verifyCredentials(psc, script, executionPlan, preReleaseExecutionPlan, releaseExecutionPlan)
        switch (config.releaseType) {
            case 'release':
                StartReleaseStep releaseStep = newStartReleaseStep()
                releaseStep.execute(config.args as Map)

                break
            case 'preRelease':
                StartPreReleaseStep preReleaseStep = newStartPreReleaseStep()

                preReleaseStep.execute()
                break
        }

    }

    private RunStepsFromFileStep newRunSteps() {
        return new RunStepsFromFileStep(psc, script)
    }

    boolean isReleasePhase(def config) {
        def currentBranch = gitBranch()
        script.echo("Release Branch Pattern : '${findReleasePattern(config)}', isProductionDeployment: ${config.isProductionDeployment}")
        (config.containsKey('releaseType') &&
            config.releaseType in ['release', 'preRelease']) &&
            isReleasableBranch(script, config, currentBranch)
    }


    @NonCPS
    private StartPreReleaseStep newStartPreReleaseStep() {
        return new StartPreReleaseStep(script, psc, config)
    }

    @NonCPS
    private StartReleaseStep newStartReleaseStep() {
        return new StartReleaseStep(script, psc, config)
    }

    protected int addPipelineSteps() {
        if (!this.stateManager) {
            this.stateManager = psc.globalModuleManager.stateManager()
        }

        Logger logger = newLogger(script)
        logger.log("""Pipeline steps before phases added
                             |${this.stateManager.pipelineSteps.join("\n")}""".stripMargin(), LogLevel.TRACE)

        logger.log("""Phases to be added to pipeline steps:
                     |${config.phases.join("\n|")}""".stripMargin(), LogLevel.TRACE)

        def isRelease = config.releaseType == 'release'

        // the callback job won't know to not do things based on branch, so we'll skip the steps whose branchPattern
        // doesn't match
        config.phases.findAll {
            currentBranchMatches(it.branchPattern)
        }.each { Map phase ->
            processArguments(script, phase)
            this.stateManager.addPipelineStep(
                PipelineUtils.CreateInvocation(
                    verb: findVerbFromPhase(phase).toLowerCase(),
                    arguments: phase.args ? phase.args : [:],
                    options: (phase.options ? phase.options : [env: (phase?.args?.env ?: phase?.sdlcEnvironment ?: '')]) + [filter: phase.moduleName],
                    buildSchedule: isRelease ? PipelineConstants.CALLBACK_BUILD : PipelineConstants.PRERELEASE_BUILD,
                    withEnv: phase.withEnv
                ))
            if (phase.containsKey('releaseType') || phase.containsKey('moduleType')) {
                psc.globalModuleManager.stateManager().add(findModuleForPhase(phase))
            }
        }

        if (config.releaseType == 'release') {
            this.stateManager.releaseArguments.putAll(config.args as Map)
        }

        logger.log("""Pipeline steps after phases added:
                             |${this.stateManager.pipelineSteps.join("\n")}""".stripMargin(), LogLevel.TRACE)

        this.stateManager.addPipelineStep(PipelineUtils.CreateInvocation(
            verb: config.releaseType,
            arguments: [:], options: [:],
            buildSchedule: PipelineConstants.CURRENT_BUILD))
    }

    private def findModuleForPhase(Map phase) {
        def moduleToLoad
        if (phase.moduleType == 'awaitApproval') {
            moduleToLoad = awaitApprovalContract
        } else {
            moduleToLoad = findModuleInRepository(phase.moduleType, phase.moduleName, phase.subCommand ?: '') ?: parseModuleContract(phase)
        }
        moduleToLoad
    }


    /**
     * Still not fully accounted for verbs for the deferred pipeline steps:
     * provision
     * publishImage
     * runScript
     * parallelType
     *
     * @param phase
     * @return a single verb describing the type of phase
     */
    protected String findVerbFromPhase(Map phase) {
        Boolean isModule = phase.containsKey('moduleType')
        Boolean isBuildType = phase.containsKey('buildType')
        if (phase.containsKey('lintingTypes') && phase.lintingTypes) {
            if (phase.lintingTypes.any { it.key != Linting.APPROVALREQUEST }) {
                throwErrorStepException('Cannot support non approval request linting types in release/preRelease blocks')
            }
            def lintingPhase = phase.lintingTypes.find { it.key == Linting.APPROVALREQUEST }
            if (lintingPhase) {
                // transform approvalrequest linting type to awaitapproval module config
                deriveArgs(phase, lintingPhase.value)
                return 'awaitApproval'
            }
        }

        if (isModule && phase.moduleType == 'test') {
            return 'runTest'
        }

        if (isBuildType || (isModule && phase?.subCommand == 'build')) {
            if (phase.containsKey('buildType') && phase.buildType == 'scanOnly') {
                return phase?.checkmarxEnabled == true ? 'runSecurityScan' : 'runQualityScan'
            }
            return 'build'
        }

        if (isModule && phase.moduleType == 'package') {
            return 'createPackages'
        }

        if (isModule && phase.subCommand == 'promote') {
            return 'cutover'
        }

        if (isModule && phase.containsKey('subCommand')) {
            return phase.subCommand
        }
        if (isModule && phase.containsKey('module') && phase.module.containsKey('subCommand')) {
            return phase.module.subCommand
        }
        if (phase.containsKey('moduleType')) {
            return phase.moduleType
        }
        if (phase.containsKey('packagingType')) {
            return 'publishImage'
        }
        if (phase.containsKey('deploymentType')) {
            return 'deploy'
        }
        throw raiseError("Unable to determine step execution verb for pre-release '$phase'")
    }

    @NonCPS
    Exception raiseError(String msg) {
        new PreReleaseException(msg)
    }

    // awaitApproval doesn't have `args` so needs to be injected directly in to phase otherwise release/preRelease won't
    // be able to configure approval according to settings.
    protected Map deriveArgs(Map phase, def lintingPhase) {
        def phaseArgs = phase.args
        phaseArgs['id'] = lintingPhase.id
        phaseArgs['message'] = lintingPhase.message
        phaseArgs['unit'] = 'MINUTES'
        phaseArgs['time'] = lintingPhase.timeOut ?: 120
        phaseArgs['approvers'] = lintingPhase.submitter
        phaseArgs['failedStatus'] = lintingPhase.failedStatus ?: 'FAILURE'
        phaseArgs['moduleType'] = 'awaitApproval'
        phaseArgs['moduleName'] = 'awaitApproval'
        phaseArgs['subCommand'] = 'awaitApproval'
        phaseArgs['scope'] = 'release'

        phaseArgs
    }
}
