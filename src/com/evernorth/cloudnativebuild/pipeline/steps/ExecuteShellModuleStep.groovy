package com.evernorth.cloudnativebuild.pipeline.steps

import com.cigna.base.DockerPipelineLib
import com.cigna.common.utils.FeatureFlags
import com.cigna.state.PipelineStateContext
import com.cloudbees.groovy.cps.NonCPS
import com.evernorth.cloudnativebuild.data.ErrorStrings
import com.evernorth.cloudnativebuild.model.Credential
import com.evernorth.cloudnativebuild.model.ModuleContract
import com.evernorth.cloudnativebuild.model.ModuleContractType
import com.evernorth.cloudnativebuild.model.StepResult
import com.evernorth.cloudnativebuild.model.logging.LogLevel
import com.evernorth.cloudnativebuild.pipeline.PipelineUtils
import com.evernorth.cloudnativebuild.service.Logger
import com.evernorth.cloudnativebuild.service.ModuleExecutionRules
import groovy.json.JsonBuilder
import hudson.Functions

class ExecuteShellModuleStep extends DockerPipelineLib {
    private def script
    private Logger logger

    PipelineStateContext psc

    ExecuteShellModuleStep(PipelineStateContext psc, def script, Map config = [:]) {
        this.script = script
        this.config = config
        logger = newLogger()
        this.psc = psc
    }

    @NonCPS
    private Logger newLogger() {
        return new Logger(this.script)
    }

    Map appendArguments(Map arguments) {
        if (!arguments) {
            return psc.globalModuleManager.stateManager().releaseArguments
        }
        arguments.putAll(psc.globalModuleManager.stateManager().releaseArguments)
        return arguments
    }

    /**
     * This would return true if module was configured to allow non-standard deploy or
     * if running in a call back job where branch logic is not possible
     * @return
     */
    boolean skipDeployableCheck(ModuleContractType moduleContractType) {
        def contracts = psc.globalModuleManager.stateManager().getContractsForContactType(moduleContractType.name())
        return contracts.findAll { it.allowNonStandardDeployment }?.size() > 0
    }
    /**
     * Execute - Executes all modules that match the module contract name and module name filter.
     * @param moduleContractType - The type of contract to execute
     * @param moduleName - Optional module name filter. This is useful for when you have many modules loaded for
     * a particular type but only wish to execute one
     * @param arguments - The arguments that will be passed to the module
     * @return StepResult
     */
    StepResult execute(ModuleContractType moduleContractType, String moduleName = "", Map arguments = [:], Map options = [:]) {
        logger.log("options in ExecuteShellModuleStep.execute: ${options}", LogLevel.TRACE)
        if (!moduleContractType) throw new IllegalArgumentException("No Module type specified")

        LoadConfigStep loadConfigStep = new LoadConfigStep(script)
        loadConfigStep.updateDefaultBuildConfigurationFromEnvironment(psc.globalModuleManager.stateManager().configuration)
        def envList = LoadConfigStep.createEnvListFromBuildConfiguration(psc.globalModuleManager.stateManager().configuration)
        // these add'l env vars are added in EPF module execution, so add them here for callback jobs to get the same env
        envList += ['HOME=/tmp', "CNP_DISABLE_JIRASCAN=${script.env.CNP_DISABLE_JIRASCAN ?: true}"]
        def stepResult = null
        script.withEnv(envList) {
            if (shouldNotExecuteStep(moduleContractType, skipDeployableCheck(moduleContractType))) {
                stepResult = returnedStopped(moduleContractType)
            } else {
                logger.log("getContractByType ${moduleContractType.name()} And ModuleName: ${moduleName}", LogLevel.TRACE)
                def contract = psc.globalModuleManager.stateManager().getContractByTypeAndModuleName(moduleContractType, moduleName)
                if (!contract) {
                    logger.log("No matching module found for contract type: ${moduleContractType.name()} and name: $moduleName", LogLevel.WARNING)
                    stepResult = StepResult.empty()
                } else {
                    doScmCheckOutIfRequired(contract)
                    stepResult = executeStep(contract, arguments, options)
                }
            }
        }
        stepResult
    }

    void doScmCheckOutIfRequired(ModuleContract contract) {
        if (contract.requireCheckout) {
            CheckoutFromScmStep.executeLight(script, psc)
        }
    }

    /**
     * Use this method when you wish to execute a step in an existing Node block
     * @param moduleContractType - The type of contract to execute
     * @param moduleName - Optional module name filter. This is useful for when you have many modules loaded for
     * @param arguments - The arguments that will be passed to the module
     * @param stageFilter - Additional filter that is a Name of a build stage
     * @return
     */
    List<StepResult> executeSubStep(ModuleContractType moduleContractType, Map arguments = [:], Map options = [:]) {
        LoadConfigStep loadConfigStep = new LoadConfigStep(script)
        loadConfigStep.updateDefaultBuildConfigurationFromEnvironment(psc.globalModuleManager.stateManager().configuration)
        def envList = LoadConfigStep.createEnvListFromBuildConfiguration(psc.globalModuleManager.stateManager().configuration)
        script.withEnv(envList) {
            def modules = psc.globalModuleManager.stateManager().getContracts(String.valueOf(moduleContractType))
            logger.log("Modules to execute with contract: ${moduleContractType.name()}  : ${modules.size()}", LogLevel.INFO)
            def results = []
            modules.each {
                results.add(executeStep(it, arguments, options))
            }
            return results
        }

    }

    static StepResult returnedStopped(ModuleContractType moduleContractType) {
        StepResult result
        if (moduleContractType == ModuleContractType.TEST) {
            result = StepResult.stopped(StepResult.TEST_SKIPPED)
        } else {
            result = StepResult.stopped(StepResult.COULD_NOT_DEPLOY)
        }

        return result
    }

    boolean shouldNotExecuteStep(ModuleContractType moduleContractType, boolean allowNonStandardDeployment) {
        boolean isDeployableBranch = ModuleExecutionRules.isDeployableBranch(psc.globalModuleManager.stateManager().configuration.deployableBranches, script.env.BRANCH_NAME as String)
        logger.log("Current branch: ${script.env.BRANCH_NAME}", LogLevel.TRACE)
        logger.log("CNP_DEPLOYABLE_BRANCHES: ${psc.globalModuleManager.stateManager().configuration.deployableBranches}", LogLevel.TRACE)
        logger.log("shouldNotExecuteStep type: ${moduleContractType.name()}, allowNonStandardDeployment: ${allowNonStandardDeployment}, isDeployableBranch(${script.env.BRANCH_NAME}): ${isDeployableBranch}", LogLevel.TRACE)
        // if test and skip all tests true
        boolean skippedTest = moduleContractType == ModuleContractType.TEST && psc.globalModuleManager.stateManager().configuration.disableAllTests
        boolean skippedDeploy = (moduleContractType == ModuleContractType.DEPLOY || moduleContractType == ModuleContractType.CUTOVER) && (!allowNonStandardDeployment && !isDeployableBranch)

        return skippedDeploy || skippedTest
    }

    private StepResult executeStep(ModuleContract contract, Map arguments, Map options = [:]) {
        if (!contract) return StepResult.empty() // no contract to process so no error
        def modifiedArgs = appendArguments(arguments)
        logger.log("EXECUTING CONTRACT: ${contract} modifiedArgs: ${modifiedArgs} OPTIONS:${options}", LogLevel.TRACE)
        String command = generateCommandWithArguments(contract, modifiedArgs)
        logger.log("Executing... $command", LogLevel.INFO)
        unStash(contract)
        StepResult stepResult
        // if the step includes credentials, we want to merge them in (reusing the credential creation logic from elsewhere)

        def maybeOverriddenCreds = (arguments?.credentials != null) ? Credential.convertToArrayListOfCredentials(arguments?.credentials) : contract.credentials?.toList()
        if (maybeOverriddenCreds) {
            stepResult = executeShellWithCredential(command, contract, options, maybeOverriddenCreds)
        } else {
            stepResult = executeShell(command, contract)
        }
        return stepResult
    }

    StepResult createStepResult(ModuleContract contract, boolean stepSuccess) {
        StepResult result = StepResult.empty()
        if (stepSuccess) {
            stash(contract)
            if (!this.psc.globalModuleManager.stateManager().configuration.simulateOnly) result = new CreateStepResultFromFileStep(script).execute(contract)
            return result
        } else {
            result = StepResult.failed()
            result.errors.add(ErrorStrings.UNEXPECTED_MODULE_FAILURE)
            result.errors.add("The module: ${contract.moduleName} failed.")
            return result
        }
    }

    /**
     * Executes shell command wrapped in a Jenkins Credential and passes the
     * credentials as Environment Variables
     * @param command - The bash Command to be executed
     * @param contract - The Module contract
     * @return - true if the shell command is executed otherwise false
     */
    private StepResult executeShellWithCredential(String command, ModuleContract contract, Map options = [:], List<Credential> mergedCreds) {
        logger.log("executeShellWithCredential called with ${mergedCreds.size()}", LogLevel.TRACE)
        def credList = []
        def envMap = [:]
        createCredentialsList(contract, credList, envMap, options, mergedCreds)
        logger.log("credList: ${credList}", LogLevel.TRACE)
        logger.log("envMap: ${envMap}", LogLevel.TRACE)
        def stepResult = null
        script.withCredentials(credList) {
            def envList = generateEnvironmentVariables(envMap)
            if (envList.size() > 0)
                script.withEnv(envList) {
                    stepResult = executeShell(command, contract)
                }
           else stepResult = executeShell(command, contract)
        }
        return stepResult
    }
    // Helper to create arraylist of credentials from contract
    private void createCredentialsList(ModuleContract contract, def credList, def envMap, Map options = [:], List<Credential> mergedCreds) {
        def envVarPrefix = "${contract.moduleName.replace('-', '_').toUpperCase()}_"
        logger.log("envVarPrefix: $envVarPrefix", LogLevel.TRACE)
        logger.log("options: $options", LogLevel.TRACE)
        mergedCreds.each {
            String environment = options?.get('env') ?: ""
            if (it.env == null || it.env == "" || it.env?.equalsIgnoreCase(environment)) {
                def credId = it.id
                logger.log("credId: $credId", LogLevel.TRACE)
                def envVarPrefixTemp = "${envVarPrefix}"
                if (it.prefix) {
                    envVarPrefixTemp = "${envVarPrefix}${it.prefix}_"
                }
                logger.log("credId after update: $credId", LogLevel.TRACE)
                logger.log("current cred: $it.id-$it.env-$it.scope-$it.type", LogLevel.TRACE)
                String credEnv = it.env
                String credScope = it.scope

                if (shouldAddToCredsList(credEnv, credScope, options)) {
                    logger.log("$it.id will be added to credList", LogLevel.TRACE)
                    // custom variable name configured in cred will be propagated to callback jobs
                    if (it.type == 'usernamePassword') {
                        String usernameVariable = it.usernameVariable ?: "${envVarPrefixTemp}MODULE_USER"
                        String passwordVariable = it.passwordVariable ?: "${envVarPrefixTemp}MODULE_PASSWORD"
                        credList.add(script.usernamePassword(credentialsId: credId,
                            passwordVariable: passwordVariable,
                            usernameVariable: usernameVariable))
                        if (it.passwordVariable && it.usernameVariable) {
                            envMap.put(usernameVariable, usernameVariable)
                            envMap.put(passwordVariable, passwordVariable)
                        }
                        else {
                            envMap.put("${envVarPrefixTemp}USER".toString(), usernameVariable)
                            envMap.put("${envVarPrefixTemp}CRED".toString(), passwordVariable)
                        }
                    }
                    if (it.type == 'string') {
                        String tokenVariable = it.variable ?: "${envVarPrefixTemp}MODULE_TOKEN"
                        credList.add(script.string(credentialsId: credId,
                            variable: tokenVariable))
                        if (it.variable){
                            envMap.put(tokenVariable, tokenVariable)
                        }
                        else {
                            envMap.put("${envVarPrefixTemp}TOKEN".toString(), tokenVariable)
                        }
                    }
                }
                logger.log("Translating ${it.id} to ${credId}", LogLevel.TRACE)
                it.id = credId
            }
            logger.log("credList in createCredentialsList: $credList", LogLevel.TRACE)
            logger.log("envMap in createCredentialsList: $envMap", LogLevel.TRACE)
        }
    }

    private static boolean shouldAddToCredsList(String credEnv, String credScope, Map options) {
        String environment = options?.get('env') ?: ""
        String scope = options?.get('scope') ?: ""

        if (!environment.isEmpty() && !credEnv.isEmpty() && !credEnv.equalsIgnoreCase(environment.toLowerCase())) {
            return false
        }
        if (!scope.isEmpty() && !credScope.isEmpty() && !credScope.equalsIgnoreCase(scope.toLowerCase())) {
            return false
        }

        return true
    }
    // Helper to create map of credential environment variables to set in containers
    private def generateEnvironmentVariables(def envMap) {
        def envList = []
        envMap.each {
            logger.log("envMap element: ${it.value}", LogLevel.TRACE)
            def exportVariable = script.env.getProperty(it.value as String)
            logger.log("exportVariable: ${exportVariable}", LogLevel.TRACE)
            envList.add("${it.key}=${exportVariable}")
        }
        logger.log("Propagated environment variables: ${envList}", LogLevel.INFO)
        return envList
    }
    /**
     * executeShell Executes a shell command and logs the details in the command invocation log
     * @param command - The command to be executed
     * @param contract - The Module contract to be executed
     * @return true if command executed successfully
     */
    private StepResult executeShell(String command, ModuleContract contract) {
        boolean executeModuleSuccess = true
        try {
            logger.logShellCommand("env | sort", LogLevel.TRACE)
            if (psc.globalModuleManager.stateManager().configuration.simulateOnly) {
                logger.log("***************** SIMULATE ONLY ENABLED: Simulated commands below ***************")
                logger.log("$command")
            } else {
                script.echo("executeShell($command) - starting")
                script.sh(script: command)
                script.echo("executeShell($command) completed")
            }
        } catch (ex) {
            // if the log file is available (meaning the module purposely wrote
            // a results file), ignore the nonzero exit code which would
            // otherwise halt the pipeline. the contents of the log file will
            // take precedence to represent command status.
            if (FeatureFlags.showStackTraces) {
                script.echo(Functions.printThrowable(ex))
            }

            if (isLogFileAvailable(contract)) {
                logger.log("Ignoring nonzero exit code because logfile found", LogLevel.TRACE)
            } else {
                logger.log("${ex.message}", LogLevel.TRACE)
                logger.logError("Error invoking contract $command", ex, LogLevel.ERROR)
                executeModuleSuccess = false
            }
        }
        return createStepResult(contract, executeModuleSuccess)
    }

    private boolean isLogFileAvailable(ModuleContract contract) {
        return script.fileExists(contract.logFileName)
    }

    private static final String shebang = "#!/bin/bash -e\n"

    static String generateCommand(ModuleContract contract, boolean suppressOutput = false) {
        String prefix = suppressOutput ? shebang : ""
        String commandPrefix = contract.commandPrefix ? contract.commandPrefix.trim() + " " : ""

        return "${prefix}${commandPrefix}${contract.commandName} ${contract.subCommand}".trim().toLowerCase()
    }

    static String generateCommandArgs(Map arguments) {
        return new JsonBuilder(arguments).toString()
    }

    static String generateCommandWithArguments(ModuleContract contract, Map arguments, boolean suppressOutput = false) {
        String args = generateCommandArgs(arguments)
        if (args != "{}") {
            return generateCommand(contract, suppressOutput) + " '${args}'"
        }

        return generateCommand(contract, suppressOutput)
    }

    private void unStash(ModuleContract moduleContract) {
        if (moduleContract.requiresUnStash) {
            PipelineUtils.unStash(script, moduleContract.unStashName, logger)
        }
    }

    private void stash(ModuleContract moduleContract) {
        if (moduleContract.requiresStash) {
            PipelineUtils.stash(script, moduleContract.stashName, moduleContract.stashPattern, moduleContract.excludesPattern, logger)
        }
    }
}