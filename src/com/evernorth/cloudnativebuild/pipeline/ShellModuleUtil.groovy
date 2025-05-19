package com.evernorth.cloudnativebuild.pipeline


import com.cigna.state.PipelineStateContext
import com.cloudbees.groovy.cps.NonCPS
import com.evernorth.cloudnativebuild.data.PipelineConstants
import com.evernorth.cloudnativebuild.model.DeferredCommandResult
import com.evernorth.cloudnativebuild.model.ModuleContractType
import com.evernorth.cloudnativebuild.model.StepInvocation
import com.evernorth.cloudnativebuild.model.StepResult
import com.evernorth.cloudnativebuild.model.logging.LogLevel
import com.evernorth.cloudnativebuild.pipeline.steps.ExecuteShellModuleStep
import com.evernorth.cloudnativebuild.service.Logger
import com.evernorth.cloudnativebuild.service.PipelineStateManager
import org.codehaus.groovy.runtime.StackTraceUtils
/***
 * Contains set of static utility methods to simplify invoking Shell Modules
 */
class ShellModuleUtil {
    /**
     * This method is used to execute the shell module from an execution plan
     * @param scriptContext - The global Jenkins Script context
     * @param type - The module contract type
     * @param properties - Custom properties passed to the module
     * @param filter - Filter used to target specific module when more then one module is loaded to given Contract
     * @param manager - The module manager instance
     * @return StepResult that contains build results and other data that can be used in other steps
     */
    static StepResult executeShellModule(PipelineStateContext psc, def scriptContext, ModuleContractType type, Map config=[:], Map properties = [:], Map options = [:]) {
        validateExecuteShellArguments(scriptContext, psc.globalModuleManager.stateManager())
        // if no modules in config try and recover
        psc.globalModuleManager.stateManager().recoverStateFromFile(scriptContext)
        Logger logger = newLogger(scriptContext)
        logger.log("type: ${type}, filter: ${options?.get('filter') as String}, options: ${options}", LogLevel.TRACE)

        StepResult result = null
        scriptContext.stage(PipelineUtils.getStageNameFromModuleContract(psc.globalModuleManager.stateManager(), type, options?.get('filter') as String, options)) {
            ExecuteShellModuleStep step = newExecuteShell(psc, scriptContext, config)
            result = step.execute(type, options?.get('filter') as String, properties, options)
            PipelineUtils.failPipelineIfStepResultFailed(scriptContext, result, "One or more of the module execution failed : ${result?.errors?.toString()}")
        }
        logger.log("result: ${result}", LogLevel.TRACE)
        PipelineUtils.stopPipelineIfNotDeployable(scriptContext, result, "${result?.commandOutput?.get('status')}")
        return result

    }

    @NonCPS
    private static ExecuteShellModuleStep newExecuteShell(PipelineStateContext psc, scriptContext, Map config) {
        return new ExecuteShellModuleStep(psc, scriptContext, config)
    }

    @NonCPS
    private static Logger newLogger(scriptContext) {
        return new Logger(scriptContext)
    }

    @NonCPS
    private static void validateExecuteShellArguments(def scriptContext, PipelineStateManager manager) {
        if (!scriptContext) throw new IllegalArgumentException("The Jenkins context was not passed.")
        if (!manager) throw new IllegalArgumentException("The Pipeline Module Manager was not initialized.")
    }

    /**
     * This method is used by the Verbs defined in vars folder. Creates a ExecuteShellModuleStep and executes it
     * @param scriptContext - The global Jenkins Script context
     * @param type - The module contract type
     * @param properties - Custom properties passed to the module
     * @param filter - Filter used to target specific module when more then one module is loaded to given Contract
     * @param manager - The module manager instance
     * @return StepResult that contains build results and other data that can be used in other steps
     */
    @NonCPS
    static StepResult deferExecuteShellModule(def scriptContext, PipelineStateManager manager, Map properties = [:], Map options = [:], String scheduleName = PipelineConstants.CURRENT_BUILD) {
        validateExecuteShellArguments(scriptContext, manager)
        // if no modules in config try and recover
        PipelineUtils.addGlobalFilter(options, manager)
        Logger logger = new Logger(scriptContext)
        // All steps should be deferred
        String callingScriptName = scriptContext.inspect().substring(0, scriptContext.inspect().lastIndexOf('@'))
        logger.log("***********************  In $scheduleName Block Deferring execution of  $callingScriptName verb. *****************")
        return scheduleExecutionOfShellModule(callingScriptName, scheduleName, manager, properties, options)
    }

    @NonCPS
    static StepResult scheduleExecutionOfShellModule(String verb, String executionState, PipelineStateManager manager, Map properties = [:], Map options = [:]) {
        StepInvocation invocation = PipelineUtils.CreateInvocation(verb: verb, arguments: properties, options: options, completed: false, buildSchedule: executionState)
        int stepIndex = manager.addPipelineStep(invocation)
        return new StepResult(commandOutput: new DeferredCommandResult(executionState: executionState, stepIndex: "$stepIndex"), commandResult: StepResult.DEFERRED)
    }

    /**
     * Checks call stack for executeReleaseClosure
     * @return True if method is called from inside of release block, otherwise false
     */
    static String findScheduleNameFromClosure() { // note: Do not make NonCPS otherwise we loose visibility to cps transformed items in call stack
        def marker = new Throwable()
        String value = PipelineConstants.CURRENT_BUILD
        PipelineConstants.METHOD_SCHEDULE_MAP.each { methodName, scheduleName ->
            def searchResult = StackTraceUtils.sanitize(marker).stackTrace.find { it.methodName == methodName }
            if (searchResult) {
                value = PipelineConstants.METHOD_SCHEDULE_MAP.get(methodName)
            }
        }

        return value
    }

    /**
     * This function creates a wrapper for when we need to call shellModules
     * outside of a cnpNode block. It assumes all steps can be performed in one image
     * @param scriptContext - Jenkins context
     * @param podTemplateYaml - yaml that defines a pod template
     * @param image - The full Url to image in image repository
     * @param steps - closure of calls to be made inside container
     * @return
     */
    static def executeModuleInCustomPod(PipelineStateContext psc, def scriptContext, String podTemplateYaml, String cloudName, String containerName, def steps) {
        def podLabel = psc.globalModuleManager.stateManager().podLabel(scriptContext)
        Logger logger = newLogger(scriptContext)
        logger.log ("Launching Pod '${podLabel}' in cloud '${cloudName}' using container ${containerName}")
        scriptContext.podTemplate(label: podLabel,
            yaml: podTemplateYaml,
            workspaceVolume: scriptContext.emptyDirWorkspaceVolume(true),
            cloud: cloudName) {
            scriptContext.node(podLabel) {
                logger.log("Using container : ${containerName}")
                scriptContext.container(containerName) {
                    steps()
                }
            }
        }
    }
}
