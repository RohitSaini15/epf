package com.evernorth.cloudnativebuild.model

import com.cloudbees.groovy.cps.NonCPS
import com.evernorth.cloudnativebuild.data.PipelineConstants

/**
 * This class is used record CNP pipeline steps that require deferred execution
 */
class StepInvocation implements Serializable, Comparable, Cloneable {
    /**
     * The Pipeline verb that invoked the Step
     */
    String verb

    /**
     * The Order the command was entered in the pipeline
     */
    int order

    /**
     * if more then one pod is required
     * number of pod assigned
     */
    int podIndex

    /**
     * The number of times the Step was attempted to be run
     */
    int retryCount

    /**
     * The properties passed to the verb in the Jenkinsfile
     */
    def arguments

    /**
     * The options map passed to the Jenkinsfile or appended via Pipeline library logic
     */
    def options

    /**
     * The result of the pipeline step on last run. Null if step was not executed. Will be overridden on each execution
     */
    StepResult result

    /**
     * True when step has been completed successfully
     */
    Boolean completed

    /**
     * Should be either CURRENT_BUILD or CALLBACK_BUILD.  When CURRENT_BUILD this step should be executed in this build.
     * Otherwise it is scheduled to be executed in a callback
     */
    String buildSchedule= PipelineConstants.CALLBACK_BUILD

    def withEnv = []

    @Override
    @NonCPS
    int compareTo(Object o) {
        StepInvocation step = (StepInvocation)o
        // spaceship operator <=> delegates to compareTo method
        return this.order <=> step.order
    }

    @Override
    @NonCPS
    String toString() {
        return "StepInvocation{" +
                "verb='" + verb + '\'' +
                ", order=" + order +
                ", podIndex=" + podIndex +
                ", retryCount=" + retryCount +
                ", arguments=" + arguments +
                ", options=" + options +
                ", withEnv=" + withEnv +
                ", result=" + result +
                ", completed=" + completed +
                ", buildSchedule='" + buildSchedule + '\'' +
                '}'
    }
}