package com.evernorth.cloudnativebuild.service

import com.cloudbees.groovy.cps.NonCPS
import com.evernorth.cloudnativebuild.model.StepInvocation
import com.evernorth.cloudnativebuild.model.StepResult
import groovy.json.JsonSlurperClassic

/**
 * Takes the results from command output and parses it to create a results object
 * @param rawResults
 */
class ResultParser {
    public static final transient JsonSlurperClassic jsonSlurperClassic = new JsonSlurperClassic()

    static StepResult parseScriptResult(String rawResults) {
        if (!rawResults) {
            return StepResult.empty()
        }
        try {
            //noinspection GroovyAssignabilityCheck
            StepResult result = new StepResult(jsonSlurperClassic.parseText(rawResults))

            if (result && ( result.commandResult.toUpperCase() != StepResult.SUCCESS && result.commandResult.toUpperCase() != StepResult.FAILURE )) {
                result.commandResult = result.commandResult.toUpperCase() == StepResult.UNSTABLE ? StepResult.UNSTABLE : StepResult.SUCCESS
            }
            return result
        }
        catch (ignored) {
            def result = StepResult.failed()
            result.errors.add(StepResult.COULD_NOT_PARSE)
            return result
        }
    }
    @NonCPS
    static String CreateDeferredValuePointerFromStepIndex(String stepIndex, String propertyName){
        return "~${StepResult.DEFERRED}|$stepIndex|$propertyName~"
    }

    @NonCPS
    static String GetCommandResultValue(StepResult result, String propertyName) {
        if (result == null || result.commandResult == StepResult.FAILURE) {
            return ''
        }

        result.commandOutput[propertyName] ?: ''
    }

    /**
     * CreateDeferredValuePointer Allows results from step invocations that will be called in the future
     * to be passed as values to other step invocations
     * @param result - StepResult - from a deferred function
     * @param propertyName - The property name that will be searched for in the Command Result
     * @return Encoded value that consists of the stepIndex and propertyName
     */
    @NonCPS
    static String CreateDeferredValuePointer(StepResult result, String propertyName){
        if (result==null || result.commandResult!=StepResult.DEFERRED){
            return ""
        }
        // assumes that library added stepIndex argument
        String stepIndex = result.commandOutput["stepIndex"]
        return CreateDeferredValuePointerFromStepIndex(stepIndex,propertyName)
    }


    static final String INVALID_POINTER = "ERROR - VALUE NOT RESOLVED"
    @NonCPS
    static def resolveDeferredValuePointer(Object value,PipelineStateManager manager){
        if(!(value instanceof String || value instanceof GString)) return value
        String deferredValuePointer = value as String
        // if value matches pattern "~$StepResult.DEFERRED|$stepIndex|$propertyName~"
        // then this is a placeholder value
        if((deferredValuePointer)?.contains("~$StepResult.DEFERRED|")){
            // limits stepIndex between 1 and 999
            // property names must be alpha-numeric and have at least 2 chars max 30 chars
            String replaced = deferredValuePointer.replaceAll(/~DEFERRED\|([0-9]{1,5})\|([A-Za-z0-9]{2,30})~/) { all, stepIndex, propName ->
                // try and find an invocation with step id
                StepInvocation invocation = manager.getPipelineSteps()?.find { it.order == Integer.valueOf(stepIndex as String) }
                if (invocation) {
                    String foundValue = GetCommandResultValue(invocation.result, propName as String)
                    if (foundValue) {
                        foundValue
                    }else{
                        INVALID_POINTER
                    }
                }
            }

            if(replaced!="" && replaced!="null" && (!replaced.contains("~$StepResult.DEFERRED|"))){
                return replaced
            }

        }else {
            return deferredValuePointer
        }
    }
    @NonCPS
    static def updateArgument(Map.Entry<Object, Object>  argument, PipelineStateManager manager){
        return resolveDeferredValuePointer(argument.value,manager)
    }
}
