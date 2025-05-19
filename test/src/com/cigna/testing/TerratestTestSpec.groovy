package com.cigna.testing

import com.cigna.SinglePodTest
import com.cigna.common.utils.FeatureFlags
import com.cigna.common.utils.GoEnvBuilder

class TerratestTestSpec extends SinglePodTest {

    String goEnv = GoEnvBuilder.buildGoEnv()

    def setup() {
        explicitlyMockPipelineStep('override')
        initScriptAndPsc()
    }

    def """When validate is called and required configuration items are missing, issues are raised """() {
        when:
        def terratestTest = new TerratestTest(script: script, psc: psc)

        terratestTest.config = [
            testType       : 'terratest',
            awsFed         : [
                credentialsId: awsFedCredentialsId,
                account      : awsFedAccount,
                rolename     : awsFedRolename
            ],
            testDirectories: testDirectories
        ]
        def issues = terratestTest.validate()

        then:
        issues.size() == numberOfIssues

        where:
        awsFedCredentialsId << [null, 'test', 'test', 'test']
        awsFedAccount << ['test', null, 'test', 'test']
        awsFedRolename << ['test', 'test', null, 'test']
        testDirectories << [['test'], ['test'], ['test'], null]
        numberOfIssues << [1, 1, 1, 1]
    }

    def """When validate is called and runInAWS is called, issues presented accordingly"""() {
        when:
        def terratestTest = new TerratestTest(script: script, psc: psc)

        terratestTest.config = [
            runInAWS       : whereRunInAWS,
            testType       : 'terratest',
            testDirectories: ['test']
        ]
        terratestTest.config += whereConfig
        def issues = terratestTest.validate()

        then:
        issues.size() == numberOfIssues

        where:
        whereConfig << [
            [awsFed: [credentialsId: 'test', account: 'test', rolename: 'test']],
            [:],
            [awsFed: [credentialsId: 'test', account: 'test', rolename: 'test']],
            [aws: [targetAccount: null, accountRoleName: null]],
            [aws: [targetAccount: null, accountRoleName: 'test']],
            [aws: [targetAccount: 'test', accountRoleName: null]],
            [aws: [targetAccount: 'test', accountRoleName: 'test']]
        ]
        whereRunInAWS << [null, true, true, true, true, true, true]
        numberOfIssues << [0, 0, 1, 0, 0, 0, 0]
    }

    def """When goTest is called dep is curled a directory for go dependencies is created, a symlink
        is created to the test directory, dep init, and ensure are called then go test, then the
        symlink and vendor directory are removed. """() {
        given:
        def terratestTest = new TerratestTest(script: script, psc: psc)

        when:
        terratestTest.config = [
            testType       : 'terratest',
            testDirectories: [
                'blah/blah'
            ]
        ]
        terratestTest.goTest()

        then:
        1 * getPipelineMock("sh")('go version')

        1 * getPipelineMock("sh")('cd blah/blah && GOPRIVATE=*.jfrog.io,*.sys.cigna.com ' +
            'GOPROXY=https://cigna.jfrog.io/artifactory/api/go/go-repos,direct GOSUMDB=off ' +
            'GONOSUMDB=*.jfrog.io,*.sys.cigna.com go test  -timeout 30m 2>&1')
    }

    def """When goTest is called and the go.testTimeout is defined, the test will specify the supplied timeout value. """() {

        when:
        def terratestTest = new TerratestTest(script: script, psc: psc)

        terratestTest.config = [
            testType       : 'terratest',
            go             : [
                testTimeout: '60m',
            ],
            testDirectories: [
                'blah/blah'
            ]
        ]
        terratestTest.goTest()

        then:
        1 * getPipelineMock("sh")('go version')
        1 * getPipelineMock("sh")('cd blah/blah && GOPRIVATE=*.jfrog.io,*.sys.cigna.com ' +
            'GOPROXY=https://cigna.jfrog.io/artifactory/api/go/go-repos,direct GOSUMDB=off ' +
            'GONOSUMDB=*.jfrog.io,*.sys.cigna.com go test  -timeout 60m 2>&1')
    }

    def """When goTest is called and the go.verbosityFlag is defined, the test will utilize the verbosity flag. """() {

        when:
        def terratestTest = new TerratestTest(script: script, psc: psc)

        terratestTest.config = [
            testType       : 'terratest',
            go             : [
                verbosityFlag: true,
            ],
            testDirectories: [
                'blah/blah'
            ]
        ]
        terratestTest.goTest()

        then:
        1 * getPipelineMock("sh")('go version')
        1 * getPipelineMock("sh")('cd blah/blah && ' + goEnv + 'go test -v -timeout 30m 2>&1')
    }


    def """When run is called, If awsFed.version, terragrunt.version, or go.version is defined a curl call is made.
        If terraform.version is defined tfswitch is called. If
        awsFed is defined awsFed method is called. If config.testDirectories is defined,
        goTest method and checkout are always called"""() {

        when:
        explicitlyMockPipelineVariable("AWS_FED_USERNAME")
        explicitlyMockPipelineVariable("AWS_FED_PASSWORD")

        def terratestTest = new TerratestTest(script: script, psc: psc)

        terratestTest.config = [
            testType       : 'terratest',
            terraform      : [
                version: terraformVersion
            ],
            terragrunt     : [
                version: terragruntVersion
            ],
            go             : [
                version: goVersion
            ],
            awsFed         : awsFed,
            testDirectories: [
                './stuff'
            ]
        ]
        simulatePodTemplate(psc, terratestTest)
        terratestTest.run()

        then:
        timesCalled * getPipelineMock("sh")('tfswitch 1')
        timesCalled * getPipelineMock("sh")({ it ==~ /curl -L https:\/\/dl.google.com.*/ })
        timesCalled * getPipelineMock("sh")({ it ==~ /curl -L https:\/\/github.sys.cigna.com\/cigna\/aws-fed\/releases\/download\/v1.*/ })
        timesCalled * getPipelineMock("sh")({ it ==~ /aws-fed add .*/ })
        1 * getPipelineMock("sh")('cd ./stuff && GOPRIVATE=*.jfrog.io,*.sys.cigna.com ' +
            'GOPROXY=https://cigna.jfrog.io/artifactory/api/go/go-repos,direct GOSUMDB=off ' +
            'GONOSUMDB=*.jfrog.io,*.sys.cigna.com go test  -timeout 30m 2>&1')

        where:
        terraformVersion << [null, '1']
        terragruntVersion << [null, '1']
        awsFed << [null, [version: '1', credentialsId: 'a', account: 'a', rolename: 'a']]
        goVersion << [null, '1']
        timesCalled << [0, 1]
    }

    def """If aws.saml is defined saml2aws method is called. If config.testDirectories is defined,
        goTest method and checkout are always called"""() {
        given:
        def numCredentialCalls = 1
        if (FeatureFlags.podAutotuning.enabled) {
            numCredentialCalls = 2
        }
        when:
        explicitlyMockPipelineVariable("AWS_USERNAME")
        explicitlyMockPipelineVariable("AWS_PASSWORD")

        def terratestTest = new TerratestTest(script: script, psc: psc)

        terratestTest.config = [
            testType       : 'terratest',
            terraform      : [
                version: 'version'
            ],
            terragrunt     : [
                version: 'version'
            ],
            go             : [
                version: goVersion
            ],
            aws            : [
                saml         : true,
                credentialsId: 'a',
                account      : 'a',
                rolename     : 'a',
            ],
            testDirectories: [
                './stuff'
            ]
        ]
        simulatePodTemplate(psc)
        terratestTest.run()


        then:
        1 * getPipelineMock("withEnv").call(*_)
        numCredentialCalls * getPipelineMock("withCredentials").call(*_)
        1 * getPipelineMock("sh").call({
            it ==~ /(?s).*saml2aws login --skip-prompt --force/
        })

        where:
        goVersion << [null, '1.20']

    }

    def """When withEnv is configured in the phase, validate if it is propagated to the execution environment"""() {
        when:
        explicitlyMockPipelineVariable("AWS_USERNAME")
        explicitlyMockPipelineVariable("AWS_PASSWORD")

        def terratestTest = new TerratestTest(script: script, psc: psc)

        terratestTest.config = [
            testType       : 'terratest',
            terraform      : [
                version : 'version',
                logLevel: 2
            ],
            terragrunt     : [
                version: 'version'
            ],
            testDirectories: [
                './stuff'
            ]
        ]
        terratestTest.config += whereConfig
        simulatePodTemplate(psc)
        terratestTest.run()

        then:
        1 * getPipelineMock("withEnv").call(actualEnv, _)

        where:
        whereConfig << [
            [withEnv: ['somevar=somevalue']],
            [:]
        ]
        actualEnv << [
            ['TF_LOG=2', 'somevar=somevalue'],
            ['TF_LOG=2']
        ]
    }
}
