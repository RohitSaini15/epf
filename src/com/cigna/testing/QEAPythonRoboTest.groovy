package com.cigna.testing

import com.cigna.common.utils.QEATestingUtils
import com.cigna.common.utils.Utils

/**
 * Defines testing steps for Python Robot Framework tests.
 */
class QEAPythonRoboTest extends Testing {
    QEAPythonRoboTest() {
        containerCpu = '1000m'
        containerMemory = '1000Mi'
        testType = 'integration'
        additionalValidationItems = ['commandString', 'frameworkType', 'build']
    }
    String reportDir
    def imageMap = [
        WEB       : 'qea-coreops/qea-pythonrobot-web',
        MAINFRAME : 'qea-coreops/qea-pythonrobot-mainframe',
        PLAYWRIGHT: 'qea-coreops/qea-pythonrobot-playwright',
        ROBOT     : 'qea-coreops/python-robot'
    ]

    @Override
    Boolean prePodConfig() {
        String coreContainerMemory, coreContainerCpu
        String zephyrContainerImage = 'qea-coreops/qea-pythonrobotzbot'
        String zephyrContainerVersion = 'latest'
        String frameworkType = testingConfiguration.frameworkType.toUpperCase()
        containerVersion = testingConfiguration.container?.version ?: 'latest'
        coreContainerMemory = testingConfiguration.container?.memory ?: containerMemory
        coreContainerCpu = testingConfiguration.container?.cpu ?: containerCpu
        containerImage = testingConfiguration.container?.image ?: imageMap[frameworkType] ?: imageMap['ROBOT']
        containerName = Utils.calculateContainerName(containerImage, containerVersion)

        Map coreContainerConfig = [
            name           : containerName,
            image          : "$containerImage:$containerVersion",
            imagePullPolicy: 'IfNotPresent',
            tty            : true,
            workingDir     : '/home/jenkins/agent',
            command        : Utils.defaultSidecarCommand,
            resources      : [
                requests: [
                    cpu   : '100m',
                    memory: coreContainerMemory
                ],
                limits  : [
                    cpu   : coreContainerCpu,
                    memory: coreContainerMemory
                ]
            ]
        ]

        Map zephyrContainerConfig = [
            name           : "qea-pythonrobot-zbotvlatest",
            image          : "$zephyrContainerImage:$zephyrContainerVersion",
            imagePullPolicy: 'IfNotPresent',
            tty            : true,
            workingDir     : '/home/jenkins/agent',
            command        : Utils.defaultSidecarCommand,
            resources      : [
                requests: [
                    cpu   : '100m',
                    memory: containerMemory
                ],
                limits  : [
                    cpu   : containerCpu,
                    memory: containerMemory
                ]
            ]
        ]

        basePodConfig.containers += coreContainerConfig
        if (testingConfiguration.zephyrCommandString) {
            basePodConfig.containers += zephyrContainerConfig
        }
        super.prePodConfig()
    }

    @Override
    void runImpl() {
        reportDir = testingConfiguration.reportDir
        String failedCount
        String countMessage
        psc.podSelector.select(psc, podTemplateContainerName, Utils.cloud(config)) {
            if (testingConfiguration.git) {
                QEATestingUtils.checkoutDependencies(script, testingConfiguration)
            }
            runRobotTests()
            failedCount = QEATestingUtils.searchXML(script, 'fail=', "${reportDir}/output.xml").find(/\d+/)
            if (testingConfiguration.junit) {
                QEATestingUtils.publishJunitReportRun(script, testingConfiguration)
            }
            if (testingConfiguration.webex) {
                countMessage = webexNotifications()
            }
            config.email = config.email ?: QEATestingUtils.generateEmailConfig(script, testingConfiguration, countMessage)
        }
        if (testingConfiguration.zephyrCommandString) {
            psc.podSelector.select(psc, 'qea-pythonrobot-zbotvlatest', Utils.cloud(config)) {
                if (testingConfiguration.zephyrPrepCommands) {
                    for (command in testingConfiguration.zephyrPrepCommands) {
                        script.echo("Executing zephyr prep commands")
                        script.sh(command)
                    }
                }
                String commandStr = testingConfiguration.zephyrCommandString
                try {
                    def robotFilePattern = /[\S]+\.robot/
                    def matcher = (commandStr =~ robotFilePattern)
                    if (matcher.find()) {
                        def robotFilePath = matcher.group()
                        def firstPart = commandStr.substring(0, matcher.start()).trim()
                        def secondPart = commandStr.substring(matcher.start(), matcher.end()).trim()
                        script.sh(firstPart + ' --nostatusrc ' + secondPart)
                    } else {
                        script.echo("No .robot file path found in the command.")
                    }
                } catch (all) {
                    script.echo("FAIL - Zephyr command string should be in the below format")
                    script.echo("python3 -m robot --pythonpath . tests/zephyr_integration/zephyr_integration.robot")
                }
            }
        }

        if (testingConfiguration.continueOnTestFailure == false) {
            failPhaseOnTestFailure(failedCount)
        }
    }

    /**
     * Run prep commands, robot Tests and post commands, and zip the reports
     */
    @SuppressWarnings(['AbcMetric', 'CyclomaticComplexity', 'NestedBlockDepth', 'MethodSize'])
    protected void runRobotTests() {
        String commandStr = testingConfiguration.commandString
        String optionalParams = testingConfiguration.optionalParams
        String robotFilePath = testingConfiguration.robotFilePath
        reportDir = testingConfiguration.reportDir
        String commandExec = ''
        script.echo('Running Python Robot Tests')

        if (testingConfiguration.installRequirements) {
            script.sh('pip3 install -r requirements.txt --user')
        }

        try {
            if (testingConfiguration.prepCommands) {
                QEATestingUtils.runCommands(script, 'prep', testingConfiguration)
            }
            if (optionalParams != null && !(optionalParams.isEmpty())) {
                commandExec = "${commandStr} -d ${reportDir} ${optionalParams} --nostatusrc ${robotFilePath}"
            } else {
                commandExec = "${commandStr} -d ${reportDir} --nostatusrc ${robotFilePath}"
            }
            script.sh(commandExec)
            script.sh("zip -r ${reportDir}.zip ${reportDir}")
            if (testingConfiguration.postCommands) {
                QEATestingUtils.runCommands(script, 'post', testingConfiguration)
            }
        } catch (all) {
            script.echo('Exceptions in Robot Framework Execution, please check the logs for more details')
        }
    }

    /**
     * Sending Webex Notifications
     */
    protected String webexNotifications() {
        reportDir = testingConfiguration.reportDir
        String failed = QEATestingUtils.searchXML(script, 'fail=', "${reportDir}/output.xml").find(/\d+/)
        String skipped = QEATestingUtils.searchXML(script, 'skip=', "${reportDir}/output.xml").find(/\d+/)
        String passed = QEATestingUtils.searchXML(script, 'pass=', "${reportDir}/output.xml").find(/\d+/)

        String total = passed.toInteger() + failed.toInteger() + skipped.toInteger()

        String message = "TEST RESULTS: Total Tests = ${total}, Passed = ${passed}, Failed = ${failed}, Skipped = ${skipped}"
        script.echo(message)

        QEATestingUtils.sendWebexNotifications(script, testingConfiguration, message, true)
        return message
    }

    protected void failPhaseOnTestFailure(String failedCount) {
        script.echo("Number of failed test cases: ${failedCount}")
        if (failedCount != '0') {
            throw new Exception("There are test failures in the test suite")
        }
    }
}
