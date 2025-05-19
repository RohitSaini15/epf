package com.evernorth.cloudnativebuild.model

import com.cloudbees.groovy.cps.NonCPS
import com.evernorth.cloudnativebuild.service.ResultParser

class DeferredCommandResult implements Serializable{
    String executionState
    String stepIndex
    Map deferredValuePointers=[:]

    @NonCPS
    def propertyMissing(String propertyName){
        if(deferredValuePointers.containsKey(propertyName)){
            return deferredValuePointers.get(propertyName)
        }

        return ResultParser.CreateDeferredValuePointerFromStepIndex(stepIndex,propertyName)
    }

    @NonCPS
    void setProperty(String name, Object value) {
        if(name == "stepIndex"){
            stepIndex=value as String
        }
        if(deferredValuePointers.containsKey(name)){
            deferredValuePointers.name=value
        }
        deferredValuePointers.put(name,value)
    }
}
