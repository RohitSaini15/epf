package com.evernorth.cloudnativebuild.pipeline.steps

import com.evernorth.cloudnativebuild.model.StepInvocation
import com.evernorth.cloudnativebuild.model.StepResult
import com.evernorth.cloudnativebuild.pipeline.PipelineUtils

class RunScriptStep {
    static StepResult execute(def scriptContext, StepInvocation stepInvocation){
        if(!stepInvocation?.arguments?.script){
            return StepResult.failed("No script to execute in runScript step.")
        }
        try{
            // NOTE this must be called inside a node block
            scriptContext.sh(script:stepInvocation?.arguments?.script)
            return StepResult.empty()
        }catch(Exception ignore){
            PipelineUtils.failPipeline(scriptContext,"Shell command failed")
            return StepResult.failed("Script execution failed.")
        }
    }
}
