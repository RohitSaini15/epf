package com.cigna.testing

import com.cigna.SinglePodTest
import com.cigna.common.utils.GoEnvBuilder

class CypressTestSpec extends SinglePodTest {

    def testConfig = [
            testType       : 'Cypress',
            configFile     : 'frontend',
            branchPattern  : '.*',
            sdlcEnvironment: "dev",
            container      : [
                    image  : "enterprise-devops/conduit-cypress",
                    version: '12.16.0',
                    cpu    : 5000,
                    memory : 11000
            ],
    ]

    String goEnv = GoEnvBuilder.buildGoEnv()

    def setup() {
        explicitlyMockPipelineStep('override')
        explicitlyMockPipelineStep('updateGitStatus')
        initScriptAndPsc()
    }

    def """When run is called then a sh step with an echo is called and
        another sh step is called with given command"""() {
        when:
         def cypressTest = new CypressTest(
             script: script,
             config: testConfig,
             testingConfiguration: testConfig,
             psc: psc
         )

        simulatePodTemplate(psc, cypressTest)
        cypressTest.runImpl()

        then:
        1 * getPipelineMock("sh")({ it ==~ "echo 'No prep, continuing...'" })
    }
}
