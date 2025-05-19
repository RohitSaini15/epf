package com.evernorth.cloudnativebuild.pipeline.steps

import com.cigna.base.DockerPipelineLib
import com.cigna.common.exception.ErrorStepException
import com.cigna.common.kubernetes.PodConfigGenerator
import com.cigna.common.utils.FeatureFlags
import com.cigna.state.PipelineStateContext
import com.cloudbees.groovy.cps.NonCPS
import com.evernorth.cloudnativebuild.config.ReleaseConstants
import com.evernorth.cloudnativebuild.data.PipelineConstants
import com.evernorth.cloudnativebuild.model.*
import com.evernorth.cloudnativebuild.model.logging.LogLevel
import com.evernorth.cloudnativebuild.pipeline.PipelineUtils
import com.evernorth.cloudnativebuild.pipeline.ShellModuleUtil
import com.evernorth.cloudnativebuild.service.*
import hudson.Functions

/***
 * Loads steps from a metadata file and adds them to the stateManager
 */
class RunStepsFromFileStep extends DockerPipelineLib {
    RunStepsFromFileStep(PipelineStateContext psc, def script, def config = [:]) {
        this.script = script
        this.config = config
        this.psc = psc
    }


    /**
     * Assumes that metadata file has been pulled from Artifactory and unzipped into root of repository
     * loads the file, creates module configuration and then loads steps
     * @param script
     * @return
     */
    boolean loadStepsFromFile() {
        Logger logger = newLogger()
        LoadConfigStep loadConfigStep = newConfig()
        loadConfigStep.updateDefaultBuildConfigurationFromEnvironment(psc.globalModuleManager.stateManager().configuration)
        try {
            def data = script.readFile(psc.globalModuleManager.stateManager().configuration.releaseInfoFileName)
            logger.log("Contents of ${psc.globalModuleManager.stateManager().configuration.releaseInfoFileName} file: ${data}", LogLevel.TRACE)
            psc.globalModuleManager.stateManager().loadConfigurationFromFile(data)
            logger.log(psc.globalModuleManager.stateManager().printModuleList(), LogLevel.TRACE)
            return true
        }
        catch (ex) {
            if (FeatureFlags.showStackTraces) {
                logger.log(Functions.printThrowable(ex))
            }
            logger.logError("Could not read release metadata file.", ex, LogLevel.CRITICAL)
            PipelineUtils.failPipeline(script, ex.message)
        }
        return false
    }

    @NonCPS
    private LoadConfigStep newConfig() {
        return new LoadConfigStep(script)
    }

    @NonCPS
    private Logger newLogger() {
        return new Logger(script)
    }


    @NonCPS
    void ensureReleaseModulesLoadedWhenRequired(StepInvocation stepInvocation) {
        // the release steps are only added when on release, hotfix, and develop branches
        boolean hasReleaseSteps = stepInvocation.verb == PipelineConstants.VERB_RELEASE || stepInvocation.verb == PipelineConstants.VERB_PRERELEASE
        if (!hasReleaseSteps) {
            return
        }
        LoadPipelineModulesStep loadPipelineModulesStep = new LoadPipelineModulesStep(psc, script)
        def modules = loadPipelineModulesStep.loadModuleContracts(["common", "release"])
        psc.globalModuleManager.stateManager().addList(modules)
        stepInvocation.options.filter = ReleaseConstants.RELEASE_MODULE_NAME
    }

    @NonCPS
    void updateConfigForGroovyStep(StepInvocation stepInvocation) {
        if (stepInvocation.options == null) {
            stepInvocation.options = [:]
        }
        ensureReleaseModulesLoadedWhenRequired(stepInvocation)
        if (stepInvocation.verb == PipelineConstants.VERB_RUN_SCRIPT) {
            ModuleContract contract = createModuleContractForScriptStep(stepInvocation)
            psc.globalModuleManager.stateManager().add(contract)
        }
    }

    @NonCPS
    ModuleContract createModuleContractForScriptStep(StepInvocation stepInvocation) {
        ModuleContract contract = new ModuleContract()
        contract.image = (stepInvocation.arguments.dockerImage) ? stepInvocation.arguments.dockerImage : psc.globalModuleManager.stateManager().configuration.defaultDockerImage
        contract.moduleName = "scriptStep${stepInvocation.order}"
        contract.contractName = ModuleContractType.SCRIPT
        stepInvocation.options.filter = contract.moduleName
        return contract
    }

    /**
     * Sorts and Filters steps and adds additional data required for execution
     * @param script
     * @return
     */

    List<StepInvocation> createExecutionPlan(String schedule = PipelineConstants.CALLBACK_BUILD) {
        // get steps for schedule
        List<StepInvocation> steps = psc.globalModuleManager.stateManager().getPipelineSteps().findAll { (!it.completed) && it.buildSchedule == schedule }
        List<StepInvocation> expandedSteps = []
        EventProcessor processor = newEventProcessor()

        steps.each { StepInvocation stepInvocation ->
            if (ModuleUtil.isNotShellModule(stepInvocation.verb)) {
                updateConfigForGroovyStep(stepInvocation)
                expandedSteps.add(stepInvocation)
            } else {

                String contractName = ModuleUtil.getContractTypeForVerb(
                        stepInvocation.verb == 'prerelease' ? 'RELEASE' : stepInvocation.verb
                )?.name()
                List<ModuleContract> contractList = psc.globalModuleManager.stateManager().getContracts(contractName, stepInvocation.options?.filter as String)
                // if no matching contract throw an error
                if (contractList == null || contractList.isEmpty()) {
                    throw new ErrorStepException(stepInvocation.verb)
                }
                contractList.each { contract ->
                    def preExecutionSteps = processor.getEventSteps(contract.preExecuteEvent, stepInvocation, [:], contract)
                    if (preExecutionSteps) {
                        expandedSteps.addAll(preExecutionSteps)
                    }
                    StepInvocation contractStepInvocation = stepInvocation.clone() as StepInvocation
                    if (contractStepInvocation.options == null) {
                        contractStepInvocation.options = [:]
                    }
                    contractStepInvocation.options.put("filter", contract.moduleName)
                    expandedSteps.add(contractStepInvocation)
                    def postExecutionSteps = processor.getEventSteps(contract.postExecuteEvent, stepInvocation, [:], contract)
                    if (postExecutionSteps) {
                        expandedSteps.addAll(postExecutionSteps)
                    }

                }
            }
        }
        // remove steps that cannot be executed in current branch
        if ((schedule == PipelineConstants.CURRENT_BUILD) && (!ModuleExecutionRules.isDeployableBranch(psc.globalModuleManager.stateManager().configuration.deployableBranches, script.env.BRANCH_NAME as String))) {
            expandedSteps.removeAll { it.verb == "deploy" || it.verb == "cutover" || it.verb == "publishImage" || it.verb == "event" }
        }

        int podIndex = 0
        // each time we have awaitApproval we need a new pod
        expandedSteps.collect {
            if (it.verb == PipelineConstants.VERB_AWAIT) {
                podIndex++
            }
            it.podIndex = podIndex
            it.buildSchedule = schedule
        }
        return expandedSteps
    }

    @NonCPS
    private EventProcessor newEventProcessor() {
        return new EventProcessor(psc.globalModuleManager.stateManager())
    }

    @NonCPS
    StepResult failPipelineWhenNoModuleContract(String verb) {
        def errorMessage = """

-------------  Jenkinsfile Syntax Error: ------------------
You have a mistake in your Jenkinsfile!
You have included one or more calls to the $verb verb but do not have a module configured to execute it.
Please verify the modules defined in your Jenkinsfile.
Please see the following confluence page for more information on module templates
https://confluence.sys.cigna.com/display/DvOp/Module+Type
---------------------------------------------

"""
        PipelineUtils.failPipeline(script, errorMessage)
        // note this is not reachable when running on Jenkins
        return StepResult.failed("Jenkinsfile Syntax Error")
    }

    @NonCPS
    static List<List<StepInvocation>> createSubPlansByPodIndex(List<StepInvocation> steps) {
        int maxPodIndex = steps?.max { it.podIndex }?.podIndex
        List[] subPlans = new List<StepInvocation>[maxPodIndex + 1]

        if (!steps || steps.isEmpty() || maxPodIndex == 0) {
            subPlans[0] = steps
            return subPlans
        }
        steps.each {
            if (subPlans[it.podIndex] == null) {
                subPlans[it.podIndex] = []
            }
            subPlans[it.podIndex].add(it)
        }
        return subPlans
    }

    @NonCPS
    private static List<StepInvocation> findAllAwaitInvocations(List<StepInvocation> stepInvocationList) {
        return stepInvocationList.findAll { it.verb == PipelineConstants.VERB_AWAIT }
    }

    @NonCPS
    static String createCheckPointName(List<StepInvocation> steps) {
        def nonAwaitSteps = steps.findAll() { it.verb != PipelineConstants.VERB_AWAIT }
        if (nonAwaitSteps.isEmpty()) return ""
        def lastInvocation = nonAwaitSteps.last()
        String env = ""
        if (lastInvocation.options != null && lastInvocation.options.containsKey("env")) {
            env = " ${lastInvocation.options.env.capitalize()}"
        }
        return "${lastInvocation.verb?.capitalize()}$env Completed"
    }

    void createCheckPoint(List<StepInvocation> steps) {
        String checkPointName = createCheckPointName(steps)
        if (checkPointName != "") {
            script.checkpoint(checkPointName)
        }
    }

    private StepResult executePlans(List<StepInvocation> steps, String callbackCommand = "") {
        StepResult result
        List<StepResult> results = []
        // create step lists for each new pod number
        def subSchedules = createSubPlansByPodIndex(steps)

        def logger = newLogger()
        subSchedules.each { List<StepInvocation> stepInvocationList ->
            if (stepInvocationList && !stepInvocationList.isEmpty()) {
                // execute the await step
                def awaitInvocations = findAllAwaitInvocations(stepInvocationList)
                executeAwaitInvocations(awaitInvocations, results, stepInvocationList)
                if (!stepInvocationList.isEmpty()) {
                    String podLabel = psc.globalModuleManager.stateManager().podLabel(script)
                    additionalPodConfig = PodConfigGenerator.addContainersToPodConfig(psc, additionalPodConfig)
                    logger.log("Using additionalPodConfig: ${additionalPodConfig}", LogLevel.TRACE)

                    String yaml = getPodConfig(securityContext, config)
                    script.podTemplate(label: podLabel,
                            yaml: yaml,
                            workspaceVolume: script.emptyDirWorkspaceVolume(true),
                            cloud: psc.globalModuleManager.stateManager().configuration.podCloud) {
                        script.node(podLabel) {
                            // determine the version and branch of pipeline we are running on for use later
                            psc.globalModuleManager.stateManager().releaseArguments.put(ReleaseConstants.PIPELINE_LIBRARY_PARAMETER_NAME, PipelineUtils.pipelineNameAndBranch(script))
                            // save the state in case we need to recover from master restart
                            psc.globalModuleManager.stateManager().saveStateToFile(script)
                            if (isReleaseSchedule(stepInvocationList)) {
                                prepareForRelease(stepInvocationList, callbackCommand)
                            }
                            StepResult planResult = executePlan(stepInvocationList, config)
                            results.add(planResult)
                        }
                    }
                    // create a check point
                    createCheckPoint(stepInvocationList)
                }
            }
        }
        result = StepResult.aggregateResults(results)
        return result
    }

    private void executeAwaitInvocations(List<StepInvocation> awaitInvocations, def results, stepInvocationList) {
        if (awaitInvocations) {
            awaitInvocations.each { awaitInvocation ->
                StepResult awaitResult = new AwaitApprovalStep(script).execute(awaitInvocation.arguments as Map)
                removeAwaitInvocations(results, awaitResult, stepInvocationList, awaitInvocation)
            }
        }
    }

    @NonCPS
    private static void removeAwaitInvocations(results, StepResult awaitResult, stepInvocationList, StepInvocation awaitInvocation) {
        results.add(awaitResult)
        stepInvocationList.remove(awaitInvocation)
    }

    @NonCPS
    static boolean isReleaseSchedule(List<StepInvocation> steps) {
        return (steps != null && !steps.isEmpty() && steps[0]?.buildSchedule in [PipelineConstants.PRERELEASE_BUILD, PipelineConstants.CALLBACK_BUILD])
    }

    /**
     * Executes list of steps
     * @param script
     * @param List
     * @return
     */
    private StepResult executePlan(List<StepInvocation> steps, def config = [:]) {
        StepResult result
        List<StepResult> results = []

        // if we are executing in current build, do checkout
        if (steps[0].buildSchedule == PipelineConstants.CURRENT_BUILD && steps[0].podIndex == 0) {
            CheckoutFromScmStep step = new CheckoutFromScmStep(script, psc)
            step.execute()
        }
        List<ContainerGroup> containerGroups = ContainerGroup.CreateContainerGroupsFromSubPlan(psc.globalModuleManager.stateManager(), steps)
        containerGroups.each {
            results.add(executeContainerGroup(it.containerName, it.steps, config))
        }
        result = StepResult.aggregateResults(results)

        return result
    }


    private StepResult executeContainerGroup(String containerName, List<StepInvocation> steps, def config = [:]) {
        int stepsExecuted = 0
        List<StepResult> results = []
        script.container(containerName) {
            steps.each { StepInvocation stepInvocation ->
                // update the arguments to pull in values from past steps
                stepInvocation.arguments = updateArguments(stepInvocation.arguments as Map)
                StepResult newResult = executeModule(stepInvocation, config)
                stepInvocation.result = newResult
                stepInvocation.completed = newResult.commandResult == StepResult.SUCCESS
                stepInvocation.retryCount++
                psc.globalModuleManager.stateManager().updatePipelineStep(stepInvocation)
                results.add(newResult)
                stepsExecuted++
            }
        }
        def result = StepResult.aggregateResults(results)
        result.subSteps = stepsExecuted
        return result
    }

    StepResult executeModule(StepInvocation step, def config = [:]) {
        if (ModuleUtil.isNotShellModule(step.verb)) {
            return executeGroovyModule(step, config)
        } else {
            if (step.withEnv.size() > 0) {
                script.withEnv(step.withEnv) {
                    return ShellModuleUtil.executeShellModule(psc, script, ModuleUtil.getContractTypeForVerb(step.verb), config, step.arguments as Map, step.options as Map)
                }
            } else
                return ShellModuleUtil.executeShellModule(psc, script, ModuleUtil.getContractTypeForVerb(step.verb), config, step.arguments as Map, step.options as Map)
        }
    }

    @NonCPS
    static String printExecutionPlan(List<StepInvocation> steps) {
        if (!steps || steps.size() == 0) {
            return "Execution Plan Has No Steps"
        }
        StringBuilder builder = new StringBuilder()
        builder.append("\n----- Pipeline Execution Plan -------")
        steps.each {
            builder.append("\n\nBuild Schedule : ${it.buildSchedule}")
            builder.append("\nOrder           : ${it.order}")
            builder.append("\nPod Number      : ${it.podIndex}")
            builder.append("\nCommand         : ${it.verb}")
            builder.append("\nModule          : ${it.options?.filter}")
        }
        return builder.toString()
    }

    /**
     * Attempts to load module configuration, pipeline configuration, and steps from a releaseInfo.json file
     * then executes the steps against the module list
     * @return Aggregated StepResults from all executed steps
     */
    StepResult execute(String xlrCallbackCommand = "", List<StepInvocation> steps = null) {
        Logger logger = new Logger(script)
        try {
            if (steps == null) {
                steps = createExecutionPlan()
            }

            if (!steps) {
                logger.log("No steps found in execution plan.")
                return StepResult.empty()
            }

            logger.log(printExecutionPlan(steps), LogLevel.INFO)
            LoadConfigStep loadConfigStep = new LoadConfigStep(script)
            loadConfigStep.updateDefaultBuildConfigurationFromEnvironment(psc.globalModuleManager.stateManager().configuration)
            return executePlans(steps, xlrCallbackCommand)
        } catch (ErrorStepException ex) {
            if (FeatureFlags.showStackTraces) {
                logger.log(Functions.printThrowable(ex))
            }
            failPipelineWhenNoModuleContract(ex.message)
        }

    }


    /**
     * This method is used for rollback. The assumption is that all modules
     * include a rollback subcommand named rollback. This method will update all
     * contracts used in the execution plan so that they have subCommand of rollback
     * @param steps - Steps in the execution plan
     * @param callbackCommand - The command from release orchestration tool such as XL Release
     */
    void prepareForRelease(List<StepInvocation> steps, String callbackCommand) {
        boolean requiresCheckout = false
        boolean isRollback = callbackCommand == ReleaseConstants.ROLLBACK_SUBCOMMAND
        steps.each { StepInvocation stepInvocation ->
            ModuleContract contract = psc.globalModuleManager.stateManager().contracts.find
                    { moduleContract ->
                        moduleContract.moduleName?.toLowerCase()?.trim() == stepInvocation.options?.get("filter")?.toLowerCase()?.trim()
                    }
            requiresCheckout = contract.artifactType == PipelineConstants.ARTIFACT_TYPE_GIT_TAG
            if (isRollback) {
                addPropertiesForRollback(stepInvocation, contract)
            }
        }
        if (requiresCheckout) {
            new CompleteReleaseStep(psc, script, config).callbackScmCheckout(psc.globalModuleManager.stateManager().releaseArguments, isRollback)
        }
    }

    /**
     * Updates step invocation and contract to support a rollback
     * @param stepInvocation - Step in a release process that needs to be rolled back
     * @param contract - Contract such as Deploy or Cut over contact that needs to be rolled back
     */
    @NonCPS
    void addPropertiesForRollback(StepInvocation stepInvocation, ModuleContract contract) {
        Logger logger = new Logger(script)
        logger.log("contract: ${contract}", LogLevel.TRACE)
        logger.log("manager.releaseArguments: ${psc.globalModuleManager.stateManager().releaseArguments}", LogLevel.TRACE)
        contract.subCommand = ReleaseConstants.ROLLBACK_SUBCOMMAND
        stepInvocation.properties.put("artifact", psc.globalModuleManager.stateManager().releaseArguments.get("artifact"))
        stepInvocation.properties.put("repoUrl", psc.globalModuleManager.stateManager().releaseArguments.get("repoUrl"))
        stepInvocation.properties.put("branchName", psc.globalModuleManager.stateManager().releaseArguments.get("branchName"))
        logger.log("contract executed: ${contract} with updated manager.releaseArguments: ${psc.globalModuleManager.stateManager().releaseArguments}", LogLevel.TRACE)
    }

    /**
     * Looks at each argument and when it matches the pattern created when getVal is called
     * @param arguments
     * @return
     */
    @NonCPS
    Map updateArguments(Map arguments) {
        if (!arguments || arguments.isEmpty()) return arguments
        Map transformedMap = [:]
        arguments.each { Map.Entry<Object, Object> argument ->
            def transformedValue = ResultParser.updateArgument(argument, psc.globalModuleManager.stateManager())
            // if pointer cannot be resolved throw an exception
            if (transformedValue == ResultParser.INVALID_POINTER) {
                throw new ErrorStepException("Pipeline could not resolve value for ${argument.key}. This is likely due to a typo in your Jenkinsfile.")
            }
            transformedMap.put(argument.key, transformedValue)

        }

        return transformedMap
    }


    StepResult executeGroovyModule(StepInvocation stepInvocation, def config = [:]) {
        switch (stepInvocation.verb) {
            case PipelineConstants.VERB_RUN_SCRIPT:
                return RunScriptStep.execute(script, stepInvocation)
            case PipelineConstants.VERB_PRERELEASE:
                return newStartPreRelease(config, psc).execute()
            case PipelineConstants.VERB_RELEASE:
                return newStartReleaseStep(config).execute(psc.globalModuleManager.stateManager().releaseArguments)
            default:
                return StepResult.failed("Unsupported Verb: ${stepInvocation.verb}")
        }
    }

    @NonCPS
    private StartReleaseStep newStartReleaseStep(config) {
        return new StartReleaseStep(script, psc, config)
    }

    @NonCPS
    private StartPreReleaseStep newStartPreRelease(config, psc) {
        return new StartPreReleaseStep(script, psc, config)
    }
}
