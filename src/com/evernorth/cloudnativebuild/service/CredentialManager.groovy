package com.evernorth.cloudnativebuild.service

import com.cigna.common.exception.ErrorStepException
import com.cigna.common.utils.FeatureFlags
import com.cigna.state.PipelineStateContext
import com.cloudbees.groovy.cps.NonCPS
import com.evernorth.cloudnativebuild.config.GlobalModuleManager
import com.evernorth.cloudnativebuild.model.StepInvocation
import com.evernorth.cloudnativebuild.model.logging.LogLevel
import com.evernorth.cloudnativebuild.pipeline.PipelineUtils
import hudson.Functions

class CredentialManager {
    @NonCPS
    private static Logger newLogger(def script) {
        return new Logger(script)
    }

    static void verifyCredentials(PipelineStateContext psc, def script, def executionPlan, def preRelease=[], def release=[]) {
        def logger = newLogger(script)
        try {
            logger.log("Verifying Credentials")
            removeUnusedCredentials(psc.globalModuleManager.stateManager(), [executionPlan, preRelease, release])
            UpdateCredentials(script, psc.globalModuleManager.stateManager())
        }
        catch (ErrorStepException ex) {
            if (FeatureFlags.showStackTraces) {
                logger.log(Functions.printThrowable(ex))
            }
            String errorMessage = """
-------------  Jenkinsfile Syntax Error: ------------------
You have made a mistake in you Jenkinsfile or have not defined required
environment variables on your Jenkins Folder.
${ex.message}
Please see the following confluence page for details on how to correct this issue:
https://confluence.sys.cigna.com/display/DvOp/EPF+FAQ#expand-Credentialsandenvironmentvariablesrequiredformodules
------------------------------------------------------------
"""
            PipelineUtils.failPipeline(script, errorMessage)
        }
    }

    @NonCPS
    private static ErrorStepException raiseError(String msg) {
        new ErrorStepException(msg)
    }
    /**
     * Update Module Configurations for all Modules configured in the state manager
     * so that any credentials that use environment variables are updated with actual values
     */
    static void UpdateCredentials(def scriptContext, PipelineStateManager stateManager) {
        Logger logger = newLogger(scriptContext)
        stateManager.getContracts().each { moduleContract ->
            moduleContract.credentials.collect { credential ->
                if (!credential.id) {
                    def ex = raiseError('Credential ID cannot be null')
                    logger.logError('PIPELINE MODULE CONFIGURATION ERROR:\n Credential ID property was not set.', ex)
                    throw ex
                }
                if (credential.id.startsWith('env.')) {
                    def idFromContract = credential.id.replace("env.", '')
                    logger.log("idFromContract: ${idFromContract}", LogLevel.TRACE)
                    def credId = scriptContext.env.getProperty(idFromContract)
                    logger.log("credId: ${credId}", LogLevel.TRACE)
                    if (!credId) {
                        def ex = raiseError("The module ${moduleContract.moduleName} was configured to use environment variable ${credential.id} but no matching environment variable was configured in Jenkins.")
                        throw ex
                    }
                    credential.id = credId
                }
                credential.id = credential.id
            }
        }
    }

    /**
     * Removes credentials from Module configuration not used in the pipeline.
     * @param scriptContext
     * @param stateManager
     */
    @NonCPS
    static void removeUnusedCredentials(PipelineStateManager stateManager, List<List<StepInvocation>> executionPlans) {
        Set<List> requiredModulesAndEnv = new HashSet<>()

        executionPlans.each { executionPlan ->
            executionPlan.each { step ->
                // non-shell modules steps will not have options.filter and env
                if (step.options && step.options.filter && step.options.env) {
                    requiredModulesAndEnv.add([step.options.filter, step.options.env.toLowerCase()])
                }
            }
        }
        stateManager.getContracts().each { contract ->
            if (contract.credentials) {
                def credentials = contract.credentials.toList()
                credentials.removeAll { it.env && ( !requiredModulesAndEnv.contains([contract.moduleName, it.env.toLowerCase()]) ) }
                contract.credentials = credentials
            }
        }
    }
}
