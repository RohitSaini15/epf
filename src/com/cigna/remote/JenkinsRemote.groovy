package com.cigna.remote

/**
 * Defines steps for Jenkins remote run.
 */

class JenkinsRemote extends Remote {

    JenkinsRemote() {
        additionalValidationItems = ['jobPath']
    }
    /**
     * Loop through each parameter given and add it to a params
     * list in the way the build step is expecting
     *
     * @return List of parameters
     */
    List jenkinsBuildStepParams() {
        List stepParams = []
        config.params?.each { paramName, paramValue ->
            stepParams.add(
                $class: 'StringParameterValue',
                name: "${paramName}",
                value: "${paramValue}"
            )
        }
        stepParams
    }

    void run(
        List credsList = [],
        List configsList = []
    ) {
        String remoteJobPath = "${config.jobPath}"
        String wait = config.wait ?: false
        String propagate = config.propagate ?: false

        try {
            script.stage('Call External Job') {
                script.build(
                    job: remoteJobPath,
                    parameters: jenkinsBuildStepParams(),
                    wait: wait,
                    propagate: propagate
                )
            }
        } catch (all) {
            script.error("Error: '${all.message}'")
        }
        sendTailoredEmail()
    }
}
