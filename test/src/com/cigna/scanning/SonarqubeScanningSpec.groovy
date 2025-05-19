package com.cigna.scanning

import com.cigna.SinglePodTest
import com.cigna.common.exception.ErrorStepException
import com.cigna.common.request.CurlRequestor

class SonarqubeScanningSpec extends SinglePodTest {
    String branchResponse = '''{
    "branches": [
        {
        "name": "feature/foo",
        "isMain": false,
        "type": "SHORT",
        "mergeBranch": "master",
        "status": {
            "qualityGateStatus": "OK",
            "bugs": 1,
            "vulnerabilities": 0,
            "codeSmells": 0
        },
        "analysisDate": "2017-04-03T13:37:00+0100"
        },
        {
        "name": "master",
        "isMain": true,
        "type": "LONG",
        "status": {
            "qualityGateStatus": "ERROR"
        },
        "analysisDate": "2017-04-01T01:15:42+0100"
        }
    ]}'''

    String tagResponse = '{"tags": ["official","offshore","playoff"]}'

    def setup() {
        explicitlyMockPipelineVariable("sonarQubeToken")
        explicitlyMockPipelineStep('readProperties')
        getPipelineMock("sh")({ it ==~ /(?s).*curl.*/ }) >> "204"
        getPipelineMock("sh")({ it ==~ /(?s).*cat.*/ }) >> branchResponse
        initScriptAndPsc()
    }

    def "can't define config.sonarQube.scannerParams without config.sonarQube.credentialsId"() {
        when:
        explicitlyMockPipelineVariable("sonarQubeToken")
        SonarqubeScanning scan = new SonarqubeScanning(
            config: [sonarQube: [scannerParams: "test"]],
            script: script,
            psc: psc
        )
        scan.writePropertiesToSonarProjectPropertiesFile()

        then:
        def exception = thrown(Exception)
        exception.message == "Must have sonarQube.credentialsId configuration option if"\
                  + " sonarQube.scannerProperties configuration option is included "
    }

    def "withCredentials is used when calling writePropertiesToSonarProjectPropertiesFile method"() {
        when:
        explicitlyMockPipelineVariable("sonarQubeToken")
        SonarqubeScanning scan = new SonarqubeScanning(
            config: [sonarQube: [credentialsId: "test", sonarIncludesPattern: 'test']],
            script: script,
            psc: psc
        )
        scan.writePropertiesToSonarProjectPropertiesFile()

        then:
        1 * getPipelineMock("withCredentials")(*_)
        assert scan.sonarQubeWorkspace == '/home/jenkins/agent/sonar'
    }

    def """when the custom params are passed it reflects in the sonar execution"""() {
        when:
        SonarqubeScanning scan = new SonarqubeScanning(
            config: [
                sonarQube: [
                    credentialsId: 'fake'
                ],
                sonar    : [
                    customArgs: '-Xmx1536m -XX:MaxPermSize=512m -XX:ReservedCodeCacheSize=128'
                ]
            ],
            script: script,
            psc: psc
        )
        explicitlyMockPipelineVariable("MAVEN_SETTINGS")
        scan.scan()

        then:
        3 * getPipelineMock('readProperties')(*_) >> ['sonar.host.url': 'https://fake.com']
        1 * getPipelineMock("sh")({ it ==~ /sonar-scanner -Xmx1536m -XX:MaxPermSize=512m -XX:ReservedCodeCacheSize=128/ })
    }

    def """when the custom params are passed under sonarQube map, it reflects in the sonar execution"""() {
        when:
        SonarqubeScanning scan = new SonarqubeScanning(
            config: [
                sonarQube: [
                    credentialsId: 'fake',
                    customArgs   : '-Xmx1536m -XX:MaxPermSize=512m -XX:ReservedCodeCacheSize=128'
                ]
            ],
            script: script,
            psc: psc
        )
        explicitlyMockPipelineVariable("MAVEN_SETTINGS")
        scan.scan()

        then:
        3 * getPipelineMock('readProperties')(*_) >> ['sonar.host.url': 'https://fake.com']
        1 * getPipelineMock("sh")({ it ==~ /sonar-scanner -Xmx1536m -XX:MaxPermSize=512m -XX:ReservedCodeCacheSize=128/ })
    }

    def """when the custom params are NOT passed it reflects in the sonar execution"""() {
        when:
        explicitlyMockPipelineVariable("MAVEN_SETTINGS")
        SonarqubeScanning scan = new SonarqubeScanning(
            config: [
                sonarQube: [
                    stuff: 'foo'
                ]
            ],
            script: script,
            psc: psc
        )
        script.env.GIT_BRANCH = 'fake'
        scan.scan()

        then:
        3 * getPipelineMock('readProperties')(*_) >> [
            'sonar.host.url': 'https://fake.com',
            'sonar.login'   : 'fake'
        ]
        1 * getPipelineMock("sh")('sonar-scanner ')
    }

    def """Checking for TypeScript is performed based on scan's checkForTypeScript property"""() {
        when:
        SonarqubeScanning scan = new SonarqubeScanning(
            checkForTypeScript: whereTypeScriptCheck,
            config: [
                sonarQube: [
                    stuff            : 'foo',
                    installTypescript: true,
                ]
            ],
            script: script,
            psc: psc
        )

        explicitlyMockPipelineVariable("MAVEN_SETTINGS")
        scan.scan()

        then:
        3 * getPipelineMock('readProperties')(*_) >> [
            'sonar.host.url'  : 'https://fake.com',
            'sonar.projectKey': 'fake',
            'sonar.login'     : 'fake'
        ]
        1 * getPipelineMock('sh')({ it ==~ 'sonar-scanner .*' })
        whereFindFilesCalled * getPipelineMock('sh')({ it ==~ /.*find.*\.ts.*/ })

        where:
        whereTypeScriptCheck << [true, false]
        whereFindFilesCalled << [1, 0]
    }

    def """When sonarIncludesPattern is provided and scan is ran, the sonarqube workspace is updated and shell step is invoked"""() {
        when:
        SonarqubeScanning scan = new SonarqubeScanning(
            sonarQubeWorkspace: '/home/jenkins/agent/sonar',
            config: [
                sonarQube: [
                    sonarIncludesPattern: whereSonarIncludesPattern
                ]
            ], 
            script: script,
            psc: psc
        )

        explicitlyMockPipelineVariable("MAVEN_SETTINGS")
        scan.scan()

        then:
        3 * getPipelineMock('readProperties')(*_) >> [
            'sonar.host.url'  : 'https://fake.com',
            'sonar.projectKey': 'fake',
            'sonar.login'     : 'fake'
        ]
        timesFindShellCalled * getPipelineMock('sh')({ it ==~ /(?s).*find.*-exec cp.*/ })

        where:
        whereSonarIncludesPattern << ['test', null]
        timesFindShellCalled << [1, 0]
    }

    def """When addBranchProtection is called, branch specified is marked as protected forever"""() {
        when:
        CurlRequestor cr = Mock(CurlRequestor)
        cr.requestJsonWithLiteralCred(*_) >> [
            responseCode: 204,
            responseBody: [:]
        ]
        SonarqubeScanning scan = new SonarqubeScanning(
            sonarQubeWorkspace: '/home/jenkins/agent/sonar',
            config: [
                sonarQube: [
                    stuff          : 'foo',
                    branchToProtect: 'feature'
                ]
            ],
            script: script,
            psc: psc
        )
        scan.curlRequestor = cr
        scan.addBranchProtection('token')

        then:
        1 * getPipelineMock('readProperties')(*_) >> [
            'sonar.projectKey': 'fake',
        ]
    }

    def '''If sonar phase not correctly configured, throw an exception'''() {
        given:
        explicitlyMockPipelineVariable('currentBuild.result')
        when:
        SonarqubeScanning scan = new SonarqubeScanning(
            config: [:],
            script: script,
            psc: psc
        )
        scan.scan()
        then:
        Exception e = thrown ErrorStepException
        e.message.contains("Sonar scanning enabled, but no SonarQube configuration detected.")
    }
}