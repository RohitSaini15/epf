package com.cigna.deployment

import com.cigna.SinglePodTest
import com.cigna.common.request.CurlRequestor

class UcdDeploymentSpec extends SinglePodTest {

    def ucdDeployment

    static Map validBody = [
        application    : 'FakeAppication',
        credentialsId  : 'Creds',
        environment    : 'Dev',
        components     : [],
        deployProcesses: [],
        agent          : 'fakeAgent'
    ]
    CurlRequestor CurlRequestor = Mock()

    def setup() {
        initScriptAndPsc()
    }

    def setupDeploymentInstance() {
        ucdDeployment = new UcdDeployment(
            script: script,
            psc: psc,
            curlRequestor: CurlRequestor
        )
        ucdDeployment.deployStatus = 'Deploy'
        ucdDeployment.deploymentConfiguration = [
            deploymentType : 'ucd',
            branchPattern  : 'master',
            sdlcEnvironment: 'Dev',
        ]
        ucdDeployment.ucdConfiguration = [
            application    : 'FakeAppication',
            credentialsId  : 'Creds',
            environment    : 'Dev',
            components     : [],
            deployProcesses: [],
            statusWaitTime : 0.1
        ]
        ucdDeployment.urlBase = 'https://fake.udeploy.com:8443/cli'
    }

    def "versionImport"() {
        when:
        def component = [
            name   : 'FakeApplicationComponent',
            version: versionConfig
        ]
        setupDeploymentInstance()
        ucdDeployment.versionImport(component)

        then:
        1 * curlRequestor.requestJson(
            'https://fake.udeploy.com:8443/cli/component/integrate',
            'Creds',
            'PUT',
            [
                component : 'FakeApplicationComponent',
                properties: versionMap,
            ]
        ) >> [responseCode: 200, responseBody: [:]]
        where:
        versionMap << [[versionOrTag: 'git_short', versionName: ''], [version: '3.4.5'], [version: '']]
        versionConfig << [[type: 'Git', version: 'git_short'], [type: 'Maven', version: '3.4.5'], [type: 'Maven']]
    }

    def "retrieveApplicationProcessRequestStatus"() {
        when:
        def requestid = 'requestId'
        setupDeploymentInstance()
        ucdDeployment.retrieveApplicationProcessRequestStatus(requestid)

        then:
        1 * curlRequestor.requestJson(
            'https://fake.udeploy.com:8443/cli/applicationProcessRequest/requestStatus?request=requestId',
            'Creds',
            'GET',
            [:],
            'application/x-www-form-urlencoded'
        ) >> [responseCode: 200, responseBody: [:]]
    }

    def "waitForApplicationProcessRequest - No Exception"() {
        when:
        ucdDeployment = Spy(UcdDeployment) {
            retrieveApplicationProcessRequestStatus(*_) >>> returnStatuses
        }
        ucdDeployment.script = script
        ucdDeployment.ucdConfiguration = [
            application    : 'FakeAppication',
            credentialsId  : 'Creds',
            environment    : 'Dev',
            components     : [],
            deployProcesses: [],
            statusWaitTime : 0.1
        ]
        ucdDeployment.curlRequestor = curlRequestor
        ucdDeployment.urlBase = 'https://fake.udeploy.com:8443/cli'
        ucdDeployment.urlLogs = 'https://fake.udeploy.com:8443'
        def requestId = 'requestId'
        ucdDeployment.waitForApplicationProcessRequest(requestId)

        then:
        numResponses[0] * getPipelineMock('echo')({ it ==~ 'Application request status pending.\nStatus: PENDING\nResult: NONE' })
        numResponses[1] * getPipelineMock('echo')({ it ==~ 'Application request result completed.\nStatus: CLOSED\nResult: SUCCEEDED' })
        numResponses[2] * getPipelineMock('echo')({ it ==~ 'Application request result pending.\nStatus: INITIALIZED\nResult: AWAITING APPROVAL' })
        numResponses[3] * getPipelineMock('echo')({ it ==~ 'Application request result completed.\nStatus: CLOSED\nResult: SCHEDULED FOR FUTURE' })

        where:
        numResponses << [[1, 1, 0, 0], [0, 0, 1, 1]]
        returnStatuses << [
            [[status: 'PENDING', result: 'NONE'], [status: 'CLOSED', result: 'SUCCEEDED']],
            [[status: 'INITIALIZED', result: 'AWAITING APPROVAL'], [status: 'CLOSED', result: 'SCHEDULED FOR FUTURE']]
        ]
    }

    def "waitForApplicationProcessRequest - ApplicationRequestStatusFailed"() {
        when:
        ucdDeployment = Spy(UcdDeployment) {
            retrieveApplicationProcessRequestStatus(*_) >> [status: 'FAULTED', result: 'NONE']
        }
        ucdDeployment.script = script
        ucdDeployment.ucdConfiguration = [
            application    : 'FakeAppication',
            credentialsId  : 'Creds',
            environment    : 'Dev',
            components     : [],
            deployProcesses: []
        ]
        ucdDeployment.curlRequestor = curlRequestor
        ucdDeployment.urlBase = 'https://fake.udeploy.com:8443/cli'
        def requestId = 'requestId'
        ucdDeployment.waitForApplicationProcessRequest(requestId)

        then:
        thrown(ApplicationRequestStatusFailed)
    }

    def "waitForApplicationProcessRequest - ApplicationRequestResultFailed"() {
        when:
        ucdDeployment = Spy(UcdDeployment) {
            retrieveApplicationProcessRequestStatus(*_) >> [status: 'CLOSED', result: 'FAILED TO START']
        }
        ucdDeployment.script = script
        ucdDeployment.ucdConfiguration = [
            application    : 'FakeAppication',
            credentialsId  : 'Creds',
            environment    : 'Dev',
            components     : [],
            deployProcesses: []
        ]
        ucdDeployment.curlRequestor = curlRequestor
        ucdDeployment.urlBase = 'https://fake.udeploy.com:8443/cli'
        def requestId = 'requestId'
        ucdDeployment.waitForApplicationProcessRequest(requestId)

        then:
        thrown(ApplicationRequestResultFailed)

    }

    def "waitForVersionImport"() {
        when:
        ucdDeployment = Spy(UcdDeployment) {
            retrieveComponentVersions(*_) >>> [
                [
                    [
                        name: '4.5.6.7'
                    ]
                ],
                [
                    [
                        name: '4.5.6.7'
                    ],
                    [
                        name: '1.2.3.4'
                    ]
                ]
            ]
        }
        ucdDeployment.script = script
        ucdDeployment.ucdConfiguration = [
            application    : 'FakeAppication',
            credentialsId  : 'Creds',
            environment    : 'Dev',
            components     : [],
            deployProcesses: [],
            statusWaitTime : 0.1
        ]
        ucdDeployment.curlRequestor = curlRequestor
        ucdDeployment.urlBase = 'https://fake.udeploy.com:8443/cli'
        def component = [
            name   : 'GitLab App',
            version: [
                type   : 'Git',
                version: '1.2.3.4'
            ]
        ]
        ucdDeployment.waitForVersionImport(component)

        then:
        1 * getPipelineMock("sh")([
            script      : 'echo "1.2.3.4"',
            returnStdout: true
        ]) >> '1.2.3.4\n'
        1 * getPipelineMock("echo")("Version Imported. Continuing...")
        1 * getPipelineMock("echo")("Awaiting version to be available...")
    }

    def "retrieveComponentVersions"() {
        when:
        def component = [
            name   : 'GitLab App',
            version: [
                type   : 'Git',
                version: '1.2.3.4'
            ]
        ]
        setupDeploymentInstance()
        ucdDeployment.retrieveComponentVersions(component)

        then:
        1 * curlRequestor.requestJson(
            'https://fake.udeploy.com:8443/cli/component/versions?component=' + URLEncoder.encode('GitLab App'),
            'Creds',
            'GET',
            [:],
            'application/x-www-form-urlencoded'
        ) >> [responseCode: 200, responseBody: []]
    }

    def "retrieveEnvironmentsInApplication"() {
        when:
        String application = 'Test Application'
        setupDeploymentInstance()
        ucdDeployment.retrieveEnvironmentsInApplication(application)

        then:
        1 * curlRequestor.requestJson(
            'https://fake.udeploy.com:8443/cli/application/'\
                 + 'environmentsInApplication?application=Test+Application',
            'Creds',
            'GET',
            [:],
            'application/x-www-form-urlencoded'
        ) >> [responseCode: 200, responseBody: []]
    }

    def "retrieveComponentsInEnvironment"() {
        when:
        String envUUID = 'AStringOfRandomChars'
        setupDeploymentInstance()
        ucdDeployment.retrieveComponentsInEnvironment(envUUID)

        then:
        1 * curlRequestor.requestJson(
            'https://fake.udeploy.com:8443/rest/deploy/environment/'\
                 + 'AStringOfRandomChars/latestDesiredInventory/true?rowsPerPage=10000&pageNumber=1'\
                 + '&orderField=name&sortType=desc',
            'Creds',
            'GET',
            [:],
            'application/x-www-form-urlencoded'
        ) >> [responseCode: 200, responseBody: []]
    }

    def "saveCurrentProcessDeployState"() {
        when:
        ucdDeployment = Spy(UcdDeployment) {
            retrieveComponentsInEnvironment(_) >> [
                [
                    component: [
                        name: 'FakeApplicationComponent'
                    ],
                    version  : [
                        name: '4.5.6'
                    ]
                ]
            ]
            retrieveEnvironmentsInApplication(_) >> [
                [
                    name: 'PVS',
                    id  : 'randomChars'
                ]
            ]
        }
        ucdDeployment.ucdConfiguration = [
            application    : 'FakeAppication',
            credentialsId  : 'Creds',
            environment    : 'Dev',
            components     : [],
            deployProcesses: []
        ]
        ucdDeployment.curlRequestor = curlRequestor
        ucdDeployment.urlBase = 'https://fake.udeploy.com:8443/cli'
        Map process = [
            application       : 'FakeApplication',
            applicationProcess: 'FakeApplicationProcess',
            date              : '2020-03-01 12:30',
            environment       : 'PVS',
            versions          : [
                [
                    component: 'FakeApplicationComponent',
                    version  : '1.2.3'
                ]
            ]
        ]
        ucdDeployment.saveCurrentProcessDeployState(process)

        then:
        assert ucdDeployment.previousEnvVersions == [
            'FakeApplication': [
                'PVS': [
                    'FakeApplicationComponent': '4.5.6'
                ]
            ]
        ]
    }

    def "requestApplicationProcess"() {
        when:
        ucdDeployment = Spy(UcdDeployment) {
            saveCurrentProcessDeployState(_) >> null
        }
        ucdDeployment.ucdConfiguration = [
            application    : 'FakeAppication',
            credentialsId  : 'Creds',
            environment    : 'Dev',
            components     : [],
            deployProcesses: []
        ]
        ucdDeployment.curlRequestor = curlRequestor
        ucdDeployment.urlBase = 'https://fake.udeploy.com:8443/cli'
        List processes = [
            [
                application       : 'FakeApplication',
                applicationProcess: 'FakeApplicationProcess',
                date              : '2020-03-01 12:30',
                environment       : 'PVS',
                versions          : [
                    [
                        component: 'FakeApplicationComponent',
                        version  : '1.2.3'
                    ]
                ]
            ]
        ]
        ucdDeployment.requestApplicationProcess(processes)

        then:
        1 * curlRequestor.requestJson(
            'https://fake.udeploy.com:8443/cli/applicationProcessRequest/request',
            'Creds',
            'PUT',
            [
                application       : 'FakeApplication',
                applicationProcess: 'FakeApplicationProcess',
                date              : '2020-03-01 12:30',
                environment       : 'PVS',
                versions          : [
                    [
                        component: 'FakeApplicationComponent',
                        version  : '1.2.3'
                    ]
                ]
            ]
        ) >> [responseCode: 200, responseBody: [:]]
    }

    def "validate"() {
        when:
        ucdDeployment = new UcdDeployment()
        ucdDeployment.deploymentConfiguration = [
            deploymentType : 'ucd',
            branchPattern  : 'master',
            sdlcEnvironment: 'Dev',
            ucd            : [
                application    : app,
                credentialsId  : credsId,
                ucdEnvironment : env,
                components     : [],
                deployProcesses: []
            ]
        ]
        def issues = ucdDeployment.validate()

        then:
        assert issues.size() == numberOfIssues

        where:
        app << [null, 'application', 'application']
        credsId << ['Creds', null, 'Creds']
        env << ['environment', 'environment', null]
        numberOfIssues << [1, 1, 1]
    }

    def """retrieveAgentStatus"""() {
        given:
        def String agentId = 'agent-linux-conduit-dev-cilsdbxd51003'
        ucdDeployment = new UcdDeployment()
        ucdDeployment.deployStatus = 'Deploy'
        ucdDeployment.deploymentConfiguration = [
            deploymentType : 'ucd',
            branchPattern  : 'master',
            sdlcEnvironment: 'Dev',
        ]
        ucdDeployment.ucdConfiguration = [
            application    : 'FakeAppication',
            agent          : 'FakeAgent',
            credentialsId  : 'Creds',
            environment    : 'Dev',
            components     : [],
            deployProcesses: []
        ]
        curlRequestor.requestJson(
            'https://fake.udeploy.com:8443/agent/agentCLI/' \
                 + 'info?agent=agent-linux-conduit-dev-cilsdbxd51003',
            'auth',
            'GET',
            [:],
            'application/x-www-form-urlencoded'
        ) >> [
            responseCode: 200,
            responseBody: [
                [status: "ONLINE"]
            ]
        ]

        when:
        ucdDeployment = new UcdDeployment(
            script: script
        )
        ucdDeployment.ucdConfiguration = [
            deploymentType: 'ucd',
            branchPattern : 'master',
            environment   : 'Dev',
        ] << validBody
        ucdDeployment.curlRequestor = curlRequestor

        then:
        assert ucdDeployment.ucdConfiguration.agent == 'fakeAgent'
    }
}
