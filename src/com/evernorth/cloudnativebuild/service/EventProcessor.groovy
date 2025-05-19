package com.evernorth.cloudnativebuild.service

import com.cloudbees.groovy.cps.NonCPS
import com.evernorth.cloudnativebuild.data.PipelineEventType
import com.evernorth.cloudnativebuild.model.ModuleContract
import com.evernorth.cloudnativebuild.model.StepInvocation
import com.evernorth.cloudnativebuild.model.StepResult
import com.evernorth.cloudnativebuild.pipeline.PipelineUtils

class EventProcessor {
    PipelineStateManager manager

    EventProcessor(PipelineStateManager manager) {
        this.manager = manager
    }

    /**
     * Gets list of modules registered in the pipeline state manager that listen for the given event type
     * @param eventType -  The event that was just fired
     * @return list of module contracts that are listening for this event
     */
    @NonCPS
    List<ModuleContract> GetEvents(PipelineEventType eventType) {
        if (eventType) {
            return this.manager.getContracts()?.findAll { it?.triggeredByEvent?.contains(eventType) }
        }
        return []
    }


    /**
     * Returns list of StepInvocations that contain a triggeredByEvent with the specified eventType
     * Error in module are ignored and will not fail the pipeline
     * Uses arguments passed to module invocation that generated the event
     * @param scriptContext - Global Jenkins object
     * @param eventType -  The type of event that was triggered
     * @param arguments - Map of arguments passed from the Jenkinsfile to the module invocation
     * @param options - Map of arguments passed from the Jenkinsfile that include information such as the environment
     * @param releaseArguments - Map of arguments passed to release closure and appended to by release modules
     * @param eventSource - The Module that generated the original event
     * @param eventSourceResult - If the eventSource was executed, the results of the execution
     * @return int - number of events processed
     */
    @NonCPS
    List<StepInvocation> getEventSteps(
            PipelineEventType eventType,
            StepInvocation sourceStep,
            def releaseArguments = [:],
            ModuleContract eventSource = null) {
        List<StepInvocation> steps = []
        if (eventType) {
            def list = GetEvents(eventType)
            list.each {
                def expandedArguments = appendArguments(sourceStep.arguments, sourceStep.options, releaseArguments, eventSource?.moduleName, sourceStep.result)
                StepInvocation eventStep = PipelineUtils.CreateInvocation(
                        verb: "event", options: sourceStep.options ?: [:], arguments: expandedArguments
                )
                eventStep.options.put("filter", it.moduleName)

                steps.add(eventStep)
            }

        }
        return steps
    }

    @NonCPS
    def static appendArguments(Map arguments, Map options, Map releaseArguments, String moduleName, StepResult result) {
        if (arguments == null) {
            arguments = [:]
        }
        if (options == null) {
            options = [:]
        }
        arguments.putAll(options)
        arguments.putAll(releaseArguments)
        arguments.put("eventSource", moduleName)
        if (result) {
            arguments.put("commandResult", result.commandResult)
            if (result.commandOutput) {
                arguments.putAll(result.commandOutput)
            }
            if (result.errors) {
                arguments.put("errors", result.errors.toString())
            }
        }
        return arguments
    }

}
