package com.cigna.common.compliance

/**
 *  Calculates git commit sha as correlation ID for compliance checks
 */
class GitCommitStrategy extends CorrelationStrategy {
    Map<String, String> scmVars
    Object script
    Map<String, Object> config
    Boolean prodDeploy

    protected GitCommitStrategy(Object script, Map<String, Object> config, Map<String, String> scmVars,
                                Boolean prodDeploy) {
        this.scmVars = scmVars
        this.script = script
        this.config = config
        this.prodDeploy = prodDeploy
    }

    @Override
    String getCorrelationID() {
        String commit
        if (prodDeploy) {
            String commitHistory = script.sh(script: "git rev-list --parents -n 1 ${scmVars?.GIT_COMMIT}",
                    returnStdout: true)
            commit = commitHistory.split(' ').last().trim()
        } else {
            commit = scmVars?.GIT_COMMIT
        }
        commit
    }
}

