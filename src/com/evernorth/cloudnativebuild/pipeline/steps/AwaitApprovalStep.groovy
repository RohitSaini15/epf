package com.evernorth.cloudnativebuild.pipeline.steps

import com.cigna.common.utils.FeatureFlags
import com.cloudbees.groovy.cps.NonCPS
import com.evernorth.cloudnativebuild.model.StepResult
import com.evernorth.cloudnativebuild.model.logging.LogLevel
import com.evernorth.cloudnativebuild.pipeline.PipelineUtils
import com.evernorth.cloudnativebuild.service.Logger
import com.evernorth.cloudnativebuild.service.ModuleExecutionRules
import hudson.Functions

class AwaitApprovalStep {
    def scriptContext
    Logger logger

    AwaitApprovalStep(def scriptContext){
        this.scriptContext=scriptContext
        logger=new Logger(scriptContext)

    }

    StepResult execute(Map properties=[unit:"MINUTES"]){
        scriptContext.stage("Awaiting Approval") {
            logger.log("Executing AwaitApprovalStep...", LogLevel.TRACE)
            def unit = properties.get('unit') ?: "MINUTES"
            def time = properties.get('time') ?: 1
            String message = properties.get('message') ?: 'Want to continue?'
            //unit: NANOSECONDS, MICROSECONDS, MILLISECONDS, SECONDS, MINUTES(default), HOURS, DAYS
            scriptContext.timeout(time: time, unit: unit) {
                try {
                    scriptContext.input(message: message, id: 'cnpApprove', submitter: properties.get('approvers'))
                } catch (ignore) {
                    if (FeatureFlags.showStackTraces) {
                        logger.log(Functions.printThrowable(ignore))
                    }

                    logger.logError(StepResult.ABORT_REASON, ignore, LogLevel.ERROR)
                    PipelineUtils.abortPipeline(scriptContext, StepResult.ABORT_REASON)
                }
            }
            return StepResult.empty()
        }
    }
}
