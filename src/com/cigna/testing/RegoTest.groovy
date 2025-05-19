package com.cigna.testing

import com.cigna.common.kubernetes.PodTemplateCreator

/**
 * Defines testing steps for Rego testing.
 */

class RegoTest extends Testing {

    RegoTest() {
        testType = 'integration' // check with anthony
        containerName = 'opavv0700-v2'
        containerImage = 'enterprise-devops/opa'
        containerVersion = 'v0.70.0-v2'
    }

    @Override
    Boolean prePodConfig() {
        List<Map> env = []

        PodTemplateCreator containerTemplate = PodTemplateCreator.newPodTemplateCreator()
        containerTemplate.addContainer(
            containerName,
            "${containerImage}:${containerVersion}",
            10,
            500,
            1000,
            1000,
            env
        )

        additionalPodConfig = [
            'volumes'   : [],
            'containers': [containerTemplate.getContainer(containerName)]
        ]

        super.prePodConfig()
    }

    void runRego(String rootPath, String bundlePath, String args) {
        String regoTestOutcome = script.sh(
            script: "opa test -b $bundlePath $args $rootPath",
            returnStatus: true
        )

        if (regoTestOutcome?.toInteger() == 1) {
            throw new FailedToRunRegoTests('Rego tests contain errors, please review.')
        } else if (regoTestOutcome?.toInteger() == 0) {
            script.echo('Rego tests successful.')
        } else {
            throw new FailedToRunRegoTests('There was an error running Rego tests, please review.')
        }
    }

    @Override
    void runImpl() {
        List<String> listArgs = testingConfiguration?.additionalArgs
        String cleanArgs = listArgs.join(',')
        String additionalArgs = cleanArgs.replace(',', ' ')

        String rootPath = testingConfiguration?.rootPath ?: ''
        String bundlePath = testingConfiguration?.containsKey('bundlePath') ? testingConfiguration?.bundlePath : '.'
        script.container(containerName) {
            runRego(rootPath, bundlePath, additionalArgs)
        }
    }
}

class FailedToRunRegoTests extends Exception {
    FailedToRunRegoTests(String message) {
        super(message)
    }
}
