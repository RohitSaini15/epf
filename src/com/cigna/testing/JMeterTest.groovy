package com.cigna.testing

import com.cloudbees.groovy.cps.NonCPS
import com.cigna.common.kubernetes.PodTemplateCreator

/**
 * Defines testing steps for JMeter testing.
 */

class JMeterTest extends Testing {
    JMeterTest() {
        additionalValidationItems = ['planPath']
        testType = 'performance'
        containerName = 'jmeterv564'
        containerImage = 'enterprise-devops/jmeter'
        containerVersion = '5.6.4'
    }
    int failureCount = 0

    boolean hasFailures(String resultsFile, boolean debug = false) {
        failureCount = 0
        int successCount = 0
        int lineNumber = 0
        int successColumn = 0
        String[] lines = script.readFile(resultsFile)?.split('\n') ?: []
        lines.each { line ->
            if (debug) {
                script.echo("Line #${lineNumber} = $line")
            }
            if (lineNumber == 0) {
                StringTokenizer tokenizer = tok(line)

                while (tokenizer.hasMoreTokens()) {
                    String token = tokenizer.nextToken()
                    if (debug) {
                        script.echo("   Line #${lineNumber} token $successColumn : $token")
                    }
                    if (token == 'success') {
                        break
                    }
                    successColumn++
                }
            } else {
                String[] tokens = line.split(',')
                if (tokens[successColumn] == 'false') {
                    failureCount++
                } else {
                    successCount++
                }
            }
            lineNumber++
        }
        if (debug) {
            script.echo("JMeter Test Results ${resultsFile}, Successful $successCount, Failed $failureCount")
        }

        failureCount > 0
    }

    @NonCPS
    StringTokenizer tok(String line) {
        StringTokenizer tokenizer = new StringTokenizer(line, ',')
        tokenizer
    }

    @Override
    Boolean prePodConfig() {

        List<Map> env = []

        PodTemplateCreator containerTemplate = PodTemplateCreator.newPodTemplateCreator()
        containerTemplate.addContainer(
            containerName,
            "${containerImage}:${containerVersion}",
            10,
            50,
            1000,
            2000,
            env
        )

        additionalPodConfig = [
            'volumes'   : [],
            'containers': [containerTemplate.getContainer(containerName)]
        ]
        super.prePodConfig()
    }

    @Override
    void runImpl() {
        script.container(containerName) {
            String args = testingConfiguration.args ?: ''
            boolean debug = testingConfiguration.debug ?: false
            String resultsFile = testingConfiguration?.resultsFile ?: 'jmeter-test-results.jtl'
            try {
                script.sh(
                    "jmeter -f -n -t ${testingConfiguration.planPath} -l " + resultsFile + ' ' + args
                )
                checkForFailures(resultsFile, debug)
            } finally {
                failureCount = 0
            }
        }
    }

    void checkForFailures(String resultsFile, boolean debug = false)
        throws IOException, JMeterTestFailuresException {

        if (hasFailures(resultsFile, debug)) {
            throwEx("'${failureCount}' JMeter test failures")
        }
    }

    @NonCPS
    private void throwEx(String msg) {
        throw new JMeterTestFailuresException(msg)
    }
}

class JMeterTestFailuresException extends Exception {
    JMeterTestFailuresException(String message) {
        super(message)
    }
}
