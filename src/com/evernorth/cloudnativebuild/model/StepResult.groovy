package com.evernorth.cloudnativebuild.model

import com.cloudbees.groovy.cps.NonCPS

/**
 * StepResult
 */
@SuppressWarnings(['EqualsAndHashCode'])
class StepResult implements Serializable {

    /**
     * commandOutput - Additional row output data that may be useful to other pipeline steps
     */
    def commandOutput = [:]

    /**
     * Should be set to the value SUCCESS when successful, or FAILURE when failed.
     */
    String commandResult

    /**
     * List of Strings that show errors
     */
    def errors = []

    int subSteps

    @NonCPS
    static StepResult empty() {
        new StepResult(commandResult: SUCCESS)
    }

    @NonCPS
    static StepResult stopped(def message) {
        new StepResult(commandResult: SUCCESS, commandOutput: [status: message])
    }

    @NonCPS
    static StepResult failed(String reason = '') {
        StepResult failedResult = new StepResult(commandResult: FAILURE)
        if (reason) {
            failedResult.errors.add(reason)
        }
        failedResult
    }

    public static final String SUCCESS = "SUCCESS"
    public static final String FAILURE = "FAILURE"
    public static final String UNSTABLE = "UNSTABLE"
    public static final String DEFERRED = "DEFERRED"
    public static final String COULD_NOT_PARSE = "CNP could not parse the results file. Please reach out to the module developer"
    public static final String COULD_NOT_DEPLOY = "CNP could not continue the pipeline as the branch is not deployable"
    public static final String ABORT_REASON = "Aborting approval. Exited without approving configurations."
    public static final String TEST_SKIPPED="Test was skipped because environment variable CNP_DISABLE_ALL_TESTS was set to true"
    /***
     * Combines one or more StepResults into a single result. This is useful when more then one
     * module is executed in a single Verb
     * @param listOfResults
     */
    @NonCPS
    static StepResult aggregateResults(List<StepResult> listOfResults) {
        if (!listOfResults || listOfResults.size() == 0 || ( listOfResults.size() == 1 && listOfResults[0] == empty() )) {
            return empty()
        }
        StepResult aggregatedResult = empty()

        listOfResults.each {
            if (it.errors?.size() > 0) {
                aggregatedResult.errors.addAll(it.errors)
            }
            if (it.commandOutput?.size() > 0) {
                aggregatedResult.commandOutput.putAll(it.commandOutput)
            }
            if (it.commandResult == FAILURE) {
                aggregatedResult.commandResult = FAILURE
            }

            aggregatedResult.subSteps++
        }
        aggregatedResult
    }

    @Override
    @NonCPS
    boolean equals(Object o) {
        if (o == null) {
            return false
        }
        StepResult result = o as StepResult
        commandResult == result.commandResult && errors == result.errors && commandOutput == result.commandOutput && subSteps == result.subSteps
    }

    @NonCPS
    @Override
    String toString() {
        'StepResult{' +
            'commandOutput=' + commandOutput +
            ', commandResult=\'' + commandResult + '\'' +
            ', errors=' + errors +
            ', subSteps=' + subSteps +
            '}'
    }
}
