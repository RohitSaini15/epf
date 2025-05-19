package com.evernorth.cloudnativebuild.pipeline.steps

import com.cigna.common.utils.FeatureFlags
import com.evernorth.cloudnativebuild.model.ModuleContract
import com.evernorth.cloudnativebuild.model.StepResult
import com.evernorth.cloudnativebuild.model.logging.LogLevel
import com.evernorth.cloudnativebuild.service.Logger
import com.evernorth.cloudnativebuild.service.ResultParser
import hudson.Functions

class CreateStepResultFromFileStep {

    def scriptContext
    Logger logger

    CreateStepResultFromFileStep(def scriptContext){
        this.scriptContext=scriptContext
        logger=new Logger(scriptContext)
    }

    StepResult execute(ModuleContract contract) {
        createResult(contract)
    }

    def createResult(ModuleContract contract){
        String filename="${contract.logFileName}"
        try{
            if(scriptContext.fileExists(filename)){
                def logfileContents = scriptContext.readFile(filename)
                logger.log(logfileContents as String, LogLevel.INFO)
                scriptContext.sh(script: "rm ${filename} || true")
                return ResultParser.parseScriptResult(logfileContents as String)
            }
            else{
                logger.log("File ${filename} not found")
                return StepResult.empty()
            }
        }
        catch (ex) {
            if (FeatureFlags.showStackTraces) {
                logger.log(Functions.printThrowable(ex))
            }

            logger.logError("Error parsing module result log, ${filename}", ex, LogLevel.INFO)
            return StepResult.failed()
        }
    }
}
