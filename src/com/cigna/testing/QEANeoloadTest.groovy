package com.cigna.testing

import com.cigna.base.Phase
import com.cigna.common.kubernetes.PodConfigGenerator
import com.cigna.common.kubernetes.PodTemplateCreator
import com.cigna.common.utils.QEATestingUtils
import com.cigna.common.utils.Utils
import com.cloudbees.groovy.cps.NonCPS

/**
 * Defines testing steps for Neoload CLI testing
 */
class QEANeoloadTest extends Testing {

    private final String nlWebUrl = 'https://neoload-api.saas.neotys.com'
    private final LinkedHashMap workspaceMap = [
        'Default Workspace'         : ['workspace_id': '5e3acde2e860a132744ca916', 'zone_id': 'FGvZh'],
        'Conduit Workspace'         : ['workspace_id': '647606e72c5022517014f4c0', 'zone_id': 'vekLt'],
        'Evicore Workspace'         : ['workspace_id': '64c7b64a9beca520851f34cd', 'zone_id': 'vTDmc'],
        'Performance Workspace'     : ['workspace_id': '64ef71104b2e8175813c1ffe', 'zone_id': 'ISesv'],
        'Special Purpose Workspace' : ['workspace_id': '64dfdda433aa727e268366c6', 'zone_id': 'jSZrD'],
        'HCPM IP Spoofing Workspace': ['workspace_id': '64ef7141947ea9228a3ab621', 'zone_id': 'VUOvh']
    ]
    private final String PY_VERSION = 3.11

    QEANeoloadTest() {
        testType = 'integration'
        additionalValidationItems = ['credentialsId', 'nlTestName', 'nlScenarioName', 'lgCount']
        containerImage = 'pdee/pvs-epf'
        containerVersion = 'latest'
    }

    @Override
    Boolean prePodConfig() {

        containerImage = testingConfiguration.container?.image ?: 'pdee/pvs-epf'
        containerVersion = testingConfiguration.container?.version ?: 'test'
        podTemplateContainerName = PodConfigGenerator.getContainerName("${containerImage}:${containerVersion}")
        List<Map> env = []

        PodTemplateCreator containerTemplate = PodTemplateCreator.newPodTemplateCreator()
        containerTemplate.addContainer(
            containerName,
            "${containerImage}:${containerVersion}",
            'IfNotPresent',
            1500,
            3000,
            1500,
            3000,
            env
        )

        additionalPodConfig = [
            volumes   : [],
            containers: [containerTemplate.getContainer(containerName)]
        ]

        super.prePodConfig()
    }

    @Override
    @NonCPS
    List validate(boolean requiresBranchPattern = false, Phase phase = this) {

        if (testingConfiguration?.reportOnly) {
            additionalValidationItems += [
                'testId'
            ]
        }
        List validationIssues = super.validate(requiresBranchPattern, phase)
        validationIssues
    }

    @Override
    void runImpl() {
        psc.podSelector.select(psc, podTemplateContainerName, Utils.cloud(config)) {
            QEATestingUtils.checkoutJinjaTemplates(script)
            createVirtualEnv()
            runPreTestScript()
            if (testingConfiguration.git) {
                QEATestingUtils.checkoutDependencies(script, testingConfiguration)
            }
            runNeoloadCLI()
            if (testingConfiguration.postCommands) {
                QEATestingUtils.runCommands(script, 'post', testingConfiguration)
            }
            runPostTestScript()

            if (testingConfiguration.smb) {
                QEATestingUtils.nasTransfer(script, testingConfiguration)
            }
        }
    }

    protected void createVirtualEnv() {
        String reqFile = testingConfiguration?.reqFile ?: 'requirements.txt'
        script.sh("python${PY_VERSION} -m venv venv/")
        def code = script.sh(returnStatus: true, script: "venv/bin/pip${PY_VERSION} install --no-cache-dir --extra-index-url https://cigna.jfrog.io/artifactory/api/pypi/pypi-repos/simple -r ${reqFile} &>> pip.log")
        if (code != 0) {
            script.sh('cat pip.log')
            script.error("Couldn't create virtual python environment. See log file.")
        }
    }

    protected void runPreTestScript() {
        if (testingConfiguration.containsKey('preTestScript')) {
            script.sh(testingConfiguration.preTestScript)
        }
    }

    protected void runNeoloadCLI() {
        String nlWorkSpace = testingConfiguration.nlWorkSpace ?: 'Default Workspace'
        String zoneID, workspaceID
        def workspaceDetails = workspaceMap[nlWorkSpace]
        // TODO: this could be handled in the validate method
        if (workspaceDetails == null) {
            script.error('Invalid phase configuration: Incorrect nlWorkSpace parameter.')
        } else {
            zoneID = workspaceDetails['zone_id']
            workspaceID = workspaceDetails['workspace_id']
        }
        String nlTestName = testingConfiguration.nlTestName ?: ''
        String nlTestFile = testingConfiguration.nlTestFile
        String projectPath = testingConfiguration.git ? './test-workspace/' : './'
        String nlScenarioName = testingConfiguration.nlScenarioName ?: ''
        String lgCount = testingConfiguration.lgCount ?: '1'
        String nlProjectName = testingConfiguration.nlProjectName
        String validateWorkspace = testingConfiguration.git ? "\'${script.env.WORKSPACE}/test-workspace\'" : "."
        String compareAgainstLast = testingConfiguration?.reportConfig?.compareAgainstLast ?: '1'
        String testId = testingConfiguration.testId

        script.withCredentials([
            script.string(
                credentialsId: testingConfiguration.credentialsId, variable: 'secret'
            )
        ]) {
            script.sh("neoload login --ssl-cert /etc/pki/ca-trust/extracted/pem/tls-ca-bundle.pem --workspace \"${nlWorkSpace}\" --url ${nlWebUrl} \$secret")
            if (testingConfiguration.reportOnly) {
                // use testId when reportOnly option is used.
                script.sh("neoload test-results use ${testingConfiguration.testId}")
            } else {
                script.sh("neoload test-settings --zone ${zoneID} --lgs ${lgCount} --scenario ${nlScenarioName} createorpatch ${nlProjectName}")
                script.sh("neoload validate --ssl-cert /etc/pki/ca-trust/extracted/pem/tls-ca-bundle.pem ${validateWorkspace}")
                script.sh("neoload project --path ${projectPath} upload cur")
                if (nlTestFile) {
                    script.sh("neoload run --detached --as-code ${nlTestFile} --name ${nlTestName} --scenario ${nlScenarioName}  > result_id.json")
                } else {
                    script.sh("neoload run --detached --name ${nlTestName} --scenario ${nlScenarioName} > result_id.json")
                }
                try {
                    // Get testId from result_id.json
                    testId = script.sh(script: 'jq -r \'.resultId\' result_id.json', returnStdout: true).trim()
                }
                catch (e) {
                    script.error('Error: testId is not available as it seems to be an issue in triggering the test.')
                }
            }
            if (testingConfiguration.gremlin && testingConfiguration.gremlin.enabled) {
                script.withCredentials([
                    script.string(
                        credentialsId: testingConfiguration.gremlin.token, variable: 'gremlin_auth_token'
                    ),
                    script.string(
                        credentialsId: testingConfiguration.gremlin.teamId, variable: 'gremlin_team_id'
                    )
                ]) {
                    script.sh(Utils.buildCommandArgs(["venv/bin/gremlin_pvs", "-i", "https://api.gremlin.com", "-t", "\$gremlin_team_id", "-c", "\$gremlin_auth_token", "-s", "${testingConfiguration.gremlin.scenarioSuite}", "-p", "${testingConfiguration.gremlin.runParallel}", "-ert", "true", "-ct", "${testingConfiguration.gremlin.customReport}"]))
                }
            }
            if (!testingConfiguration.reportOnly) {
                script.sh("neoload wait --return-0 ${testId}")
            }
            script.sh('neoload test-results junitsla')
            script.sh("neoload report --template=builtin:transactions-json > transactions.json")
            script.sh("neoload report --out-file ./temp.json")
            try {
                script.sh("neoload report --type=trends --filter=\"results=-${compareAgainstLast}\" --out-file ./trends.json")
            }
            catch (e) {
                script.sh('Skipping trends report due to data pulling issues.')
            }
            script.sh(Utils.buildCommandArgs(["venv/bin/neoload_pvs", "ept", "-t", "\$secret", "-w", "${workspaceID}", "-x", "${testId}", "-r", "true"]))

            script.sh("zip -r \"reports.zip\" \"./performance-reports\"")
        }
    }

    protected void runPostTestScript() {
        String testType = testingConfiguration?.gremlin?.enabled ? "Resiliency" : 'Performance'
        String message = "Early ${testType} Test has passed!"
        if (testingConfiguration.webex) {
            message = webexNotifications(message)
        }
        config.email = config.email ?: QEATestingUtils.generateEmailConfig(script, testingConfiguration, message)
    }

    protected String webexNotifications(String message) {
        String xmlPath = testingConfiguration.webex.xmlPath
        String failed = QEATestingUtils.searchXML(script, 'failures=', xmlPath).find(/\d+(?=\s)/).toInteger()
        String skipped = QEATestingUtils.searchXML(script, 'skipped=', xmlPath).find(/\d+(?=\s)/).toInteger()
        String total = QEATestingUtils.searchXML(script, 'tests=', xmlPath).find(/\d+(?=\s)/).toInteger()
        String passed = total.toInteger() - failed.toInteger() - skipped.toInteger()

        message += " TEST RESULTS: Total Tests = ${total}, Passed = ${passed}, Failed = ${failed}, Skipped = ${skipped}"

        QEATestingUtils.sendWebexNotifications(script, testingConfiguration, message, true)

        return message
    }
}
