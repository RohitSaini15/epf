package com.cigna.testing

import com.cigna.SinglePodTest
import com.cigna.common.notification.Notification
import com.cigna.common.request.CurlRequestor

class ZephyrTestSpec extends SinglePodTest {

    class HttpMock {
        def getContent() {
            '[{"id": 1055, "status": "new", "jobScheduleDate": 1618940196733, "zblastAgentMachine": "allitest ( 170.48.19.247 )", "cycleDuration": 0}]'
        }
    }

    ZephyrTest zephyrTest
    CurlRequestor curlRequestor = Stub()
    Notification notification = Mock()

    def setup() {
        initScriptAndPsc()
        explicitlyMockPipelineVariable("bearerToken")
        explicitlyMockPipelineVariable("ZEPHYR_ACCESS_TOKEN")
        zephyrTest = new ZephyrTest(script: script,
            psc: psc,
            notification: notification,
            curlRequestor: curlRequestor)
    }

    def """When run method is called, a Vortex test is created"""() {
        given:
            curlRequestor.requestJsonWithToken(*_) >> [
                responseCode: 200,
                responseBody: [
                    id: 543,
                ]
            ]

        when:
            zephyrTest.testingConfiguration = [:]
            zephyrTest.testingConfiguration['credentialsId'] = 'auth'
            zephyrTest.urlBase = 'https://cigna.yourzephyr.com/flex/services/rest/v3/'
            zephyrTest.vortexTestId = 543
            zephyrTest.vortexTest()
    
        then:
            1 * getPipelineMock("echo").call('Vortex Test ID: 543')
    }

    def """After Vortex test is created, a Vortex test is scheduled"""() {
        when:
            zephyrTest = new ZephyrTest(
                script: script
            )
            zephyrTest.testingConfiguration = [:]
            zephyrTest.scheduleVortexJob(543)

        then:
            1 * getPipelineMock("string.call").call(['credentialsId': null, 'variable':'ZEPHYR_ACCESS_TOKEN'])
            1 * getPipelineMock("withCredentials").call(*_)
            1 * getPipelineMock("httpRequest")(*_) >> new HttpMock()
    }

    def """After Vortex test is run and scheduled, a Vortex test is deleted"""() {
        when:
            zephyrTest.testingConfiguration = [:]
            zephyrTest.deleteVortexJob(543)

        then:
            1 * getPipelineMock("string.call").call(['credentialsId': null, 'variable':'ZEPHYR_ACCESS_TOKEN'])
            1 * getPipelineMock("withCredentials").call(*_)
            1 * getPipelineMock("httpRequest")(*_) >> new HttpMock()
    }
}