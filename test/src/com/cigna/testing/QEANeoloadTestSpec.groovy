package com.cigna.testing

import com.cigna.SinglePodTest


class QEANeoloadTestSpec extends SinglePodTest {

    def setup() {
        initScriptAndPsc()
    }

    def """When validate is called and required configuration items are checked and the missing field is noted """() {
        given:
        def qeaNeoloadTest = new QEANeoloadTest(
            script: script,
            psc: psc
        )

        qeaNeoloadTest.testingConfiguration = [
            testType      : 'required',
            credentialsId : 'test',
            nlTestName    : 'test',
            nlScenarioName: 'test',
            lgCount       : 'test'
        ]
        qeaNeoloadTest.testingConfiguration.remove(missingField)

        when:
        def issues = qeaNeoloadTest.validate()

        then:
        assert issues.size() == 1

        where:
        missingField << ['credentialsId', 'nlTestName', 'nlScenarioName', 'lgCount']
    }

    def """When reportOnly option is used but testId is missing the phase fails """() {
        given:
        def qeaNeoloadTest = new QEANeoloadTest(
            script: script,
            psc: psc
        )

        qeaNeoloadTest.testingConfiguration = [
            testType      : 'required',
            credentialsId : 'test',
            nlTestName    : 'test',
            nlScenarioName: 'test',
            lgCount       : 'test',
            reportOnly   : reportOnly
        ]
        when:
        def issues = qeaNeoloadTest.validate()

        then:
        assert issues.size() == numberOfIssues

        where:
        reportOnly << [false, true]
        numberOfIssues << [0, 1]
    }

    def """When nlWorkspace is missing it takes default workspace and proceeds with the test"""() {
        when:
        def qeaNeoloadTest = new QEANeoloadTest(
            script: script,
            psc: psc
        )

        qeaNeoloadTest.testingConfiguration = [

            nlTestName    : nlTestName,
            reportOnly   : false,
            nlProjectName : nlProjectName,
            nlScenarioName: nlScenarioName,
            projectPath   : projectPath,
            lgCount       : lgCount
        ]
        simulatePodTemplate(psc, qeaNeoloadTest)
        qeaNeoloadTest.run()

        then:
        1 * getPipelineMock("sh").call('neoload login --ssl-cert /etc/pki/ca-trust/extracted/pem/tls-ca-bundle.pem --workspace "Default Workspace" --url https://neoload-api.saas.neotys.com $secret')
        1 * getPipelineMock("sh").call('neoload test-settings --zone FGvZh --lgs 1 --scenario scenario1 createorpatch SampleProject')
        1 * getPipelineMock("sh").call('neoload project --path ./ upload cur')
        1 * getPipelineMock("sh").call('neoload validate --ssl-cert /etc/pki/ca-trust/extracted/pem/tls-ca-bundle.pem .')
        1 * getPipelineMock("sh").call('neoload run --detached --name Sample_Test --scenario scenario1 > result_id.json')
        1 * getPipelineMock("sh").call('venv/bin/neoload_pvs ept -t $secret -w 5e3acde2e860a132744ca916 -x null -r true')

        where:
        lgCount | nlScenarioName | nlTestName    | nlProjectName   | projectPath
        1       | 'scenario1'    | 'Sample_Test' | 'SampleProject' | './'
    }

    def """Invalid phase configuration fails the neoload test; when nlWorkspace is not valid it throws an error """() {
        given:
        def qeaNeoloadTest = new QEANeoloadTest(
            script: script,
            psc: psc
        )

        qeaNeoloadTest.testingConfiguration = [

            nlTestName    : nlTestName,
            nlProjectName : nlProjectName,
            nlScenarioName: nlScenarioName,
            nlWorkSpace   : nlWorkspace,
            projectPath   : projectPath,
            lgCount       : lgCount
        ]
        when:
        simulatePodTemplate(psc, qeaNeoloadTest)
        qeaNeoloadTest.run()

        then:
        1 * getPipelineMock("error").call('Invalid phase configuration: Incorrect nlWorkSpace parameter.')

        where:
        nlWorkspace       | zoneID  | lgCount | nlScenarioName | nlTestName    | nlProjectName   | projectPath
        'Other Workspace' | 'ISesv' | '1'     | 'scenario1'    | 'Sample_Test' | 'SampleProject' | './'
    }

    def """When gremlin enabled is set to true, the phase runs a gremlin test in parallel to a neoload test"""() {
        given:
        def qeaNeoloadTest = new QEANeoloadTest(
            script: script,
            psc: psc
        )

        qeaNeoloadTest.testingConfiguration = [
            nlTestName    : nlTestName,
            nlProjectName : nlProjectName,
            nlScenarioName: nlScenarioName,
            nlWorkSpace   : nlWorkspace,
            projectPath   : projectPath,
            lgCount       : lgCount,
            gremlin       : [
                enabled      : true,
                teamId      : gremlin_team_id,
                scenarioSuite: scenarioSuite,
                rampUp       : '2m',
                runParallel  : runParallel,
                customReport : customReport
            ]
        ]
        when:
        simulatePodTemplate(psc, qeaNeoloadTest)
        qeaNeoloadTest.run()

        then:
        1 * getPipelineMock("sh").call('neoload login --ssl-cert /etc/pki/ca-trust/extracted/pem/tls-ca-bundle.pem --workspace "Performance Workspace" --url https://neoload-api.saas.neotys.com $secret')
        1 * getPipelineMock("sh").call('neoload test-settings --zone ISesv --lgs 1 --scenario scenario1 createorpatch SampleProject')
        1 * getPipelineMock("sh").call('neoload project --path ./ upload cur')
        1 * getPipelineMock("sh").call('neoload run --detached --name Sample_Test --scenario scenario1 > result_id.json')
        1 * getPipelineMock("sh").call('venv/bin/gremlin_pvs -i https://api.gremlin.com -t $gremlin_team_id -c $gremlin_auth_token -s cpu_scenario -p true -ert true -ct true')
        1 * getPipelineMock("sh").call('neoload report --out-file ./temp.json')
        1 * getPipelineMock("sh").call('venv/bin/neoload_pvs ept -t $secret -w 64ef71104b2e8175813c1ffe -x null -r true')

        where:
        nlWorkspace             | lgCount | nlScenarioName | nlTestName    | nlProjectName   | projectPath | gremlin_team_id                        | scenarioSuite  | runParallel | customReport
        'Performance Workspace' | '1'     | 'scenario1'    | 'Sample_Test' | 'SampleProject' | './'        | '4d4c4543-158c-5610-8ba6-8b4af6635226' | 'cpu_scenario' | 'true'      | 'true'
    }
}
