package com.cigna.testing

import com.cigna.builds.MavenBuild
import com.cigna.common.phases.PodSelector
import com.cigna.common.kubernetes.PodTemplateCreator
import com.cigna.common.utils.QEATestingUtils
import com.cigna.common.utils.Utils
import static com.cigna.common.utils.Utils.ifNull


/**
 * Defines testing steps for Maven BDD tests.
 *
 * TODO: This class shares common functionality with the MavenBuild type; it should
 * TODOL be updated to share that code in a single parent class
 */
class QEAMavenTest extends Testing {
    String defaultRepoPath = '/var/maven/.m2/repository'

    QEAMavenTest() {
        containerCpu = '1000m'
        containerMemory = '2000Mi'
        containerName = 'mavenvv1'
        testType = 'integration'
        additionalValidationItems = ['commandString', 'build', 'frameworkType']
        containerImage = 'qea-coreops/maven'
        containerVersion = 'v1'
    }
    protected static final String TESTING_WORKSPACE_DIR = 'test-workspace'
    String reportDir
    String reportFile
    String message

    @Override
    Boolean prePodConfig() {
        if (config.containsKey('maven') && config.maven.containsKey('m2RepoPath')) {
            defaultRepoPath = config.maven.m2RepoPath
        }
        List<Map> env = []

        PodTemplateCreator containerTemplate = PodTemplateCreator.newPodTemplateCreator()
        containerTemplate.addContainer(
            containerName,
            "${containerImage}:${containerVersion}",
            'IfNotPresent',
            1000,
            2000,
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
        message = 'Execution completed'
        psc.podSelector.select(psc,podTemplateContainerName, Utils.cloud(config)) {
            reportDir = testingConfiguration.reportDir
            reportFile = testingConfiguration.reportFile
            if (testingConfiguration.git) {
                QEATestingUtils.checkoutDependencies(script, testingConfiguration)
            }
            runMavenTests()
            webexNotifications()
            if (testingConfiguration.junit) {
                QEATestingUtils.publishJunitReportRun(script, testingConfiguration)
            }
            if (testingConfiguration.smb) {
                QEATestingUtils.smbFileTransfer(script, testingConfiguration)
            }
        }
    }

    /*
     * Run Tests with appropriate arguments
     */

    protected void runMavenTests() {
        String commandStr = testingConfiguration.commandString
        String optionalParams = testingConfiguration.optionalParams
        Boolean useOrchestratorMavenSettings = ifNull(testingConfiguration.useOrchestratorMavenSettings, false)
        String commandExec
        script.echo('Running Maven Tests')
        script.configFileProvider([
                script.configFile(
                        fileId: MavenBuild.MAVEN_SETTINGS_CONFIG_FILE_ID,
                        variable: 'MAVEN_SETTINGS'
                )
        ]) {
            if (testingConfiguration.prepCommands) {
                QEATestingUtils.runCommands(script, 'prep', testingConfiguration)
            }

            def settingsStr = useOrchestratorMavenSettings ? " -s ${script.MAVEN_SETTINGS} " : ''
            if (optionalParams != null && !optionalParams.isEmpty()) {
                commandExec = "${commandStr} -Dmaven.repo.local='${defaultRepoPath}'${settingsStr} -B ${optionalParams}"
            } else {
                commandExec = "${commandStr} -Dmaven.repo.local='${defaultRepoPath}'${settingsStr}"
            }

            script.sh(commandExec)

            if (reportDir) {
                script.sh("zip -r ${reportFile} ${reportDir}")
            }

            if (testingConfiguration.postCommands) {
                QEATestingUtils.runCommands(script, 'post', testingConfiguration)
            }
        }
    }

    /**
     * Sending Webex Notifications
     */

    protected void webexNotifications() {
        String xmlPath = testingConfiguration.webex?.xmlPath
        message = 'Execution completed'
        if (xmlPath) {
            String failed = QEATestingUtils.searchXML(script, 'failures=', xmlPath).find(/\d+(?=\s)/).toInteger()
            String skipped = QEATestingUtils.searchXML(script, 'skipped=', xmlPath).find(/\d+(?=\s)/).toInteger()
            String total = QEATestingUtils.searchXML(script, 'tests=', xmlPath).find(/\d+(?=\s)/).toInteger()
            String passed = total.toInteger() - failed.toInteger() - skipped.toInteger()

            message = "TEST RESULTS: Total Tests = ${total}, Passed = ${passed}, Failed = ${failed}, Skipped = ${skipped}"
            script.echo(message)

            QEATestingUtils.sendWebexNotifications(script, testingConfiguration, message, true)
        } else {
            if (testingConfiguration.webex) {
                QEATestingUtils.sendWebexNotifications(script, testingConfiguration, message, true)
            }
        }
        config.email = config.email ?: QEATestingUtils.generateEmailConfig(script, testingConfiguration, message)
    }
}
