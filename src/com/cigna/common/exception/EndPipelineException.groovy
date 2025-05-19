package com.cigna.common.exception

/**
 * An Exception class used to end a pipeline in failed or unstable or success statuses
 */
class EndPipelineException extends InterruptedException {
    static List<String> possibleOutcomes = ['SUCCESS', 'UNSTABLE', 'FAILURE', 'ABORTED', 'NOT_BUILT']
    String outcome = 'FAILURE'
    String result = 'ABORTED'

    /**
     * @param message the reason the pipeline is ending - < 140 characters is desirable for posting to GitHub
     * @param outcome the desired outcome of the pipeline - options are ['SUCCESS', 'UNSTABLE', 'FAILURE', 'ABORTED', 'NOT_BUILT']
     */
    EndPipelineException(String message, String outcome) {
        super(message)
        String uppercaseOutcome = outcome.toUpperCase()
        if (uppercaseOutcome in possibleOutcomes) {
            this.outcome = uppercaseOutcome
        }
    }
}
