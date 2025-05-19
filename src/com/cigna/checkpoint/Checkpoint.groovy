package com.cigna.checkpoint

import com.cigna.base.Phase
import com.cloudbees.groovy.cps.NonCPS

/**
 * Triggers a Jenkins Checkpoint named by the name parameter.
 */
class Checkpoint extends Phase {
    Checkpoint() {
        groupID = 'checkpoint'
    }

    @Override
    @NonCPS
    List validate(boolean requiresBranchPattern = false) {
        List issues = []

        additionalValidationItems += [
                'name'
        ]

        List validationIssues = super.validate(requiresBranchPattern)
        validationIssues + issues
    }

    void run(List credsList = [], List configsList = []) {
        script.echo("Creating '${config.name}' checkpoint")
        script.stage("Checkpoint: ${config.name}") {
            script.checkpoint(config.name)
            sendTailoredEmail()
        }

    }

}
