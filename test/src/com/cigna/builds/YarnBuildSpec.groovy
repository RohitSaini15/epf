package com.cigna.builds

import com.cigna.SinglePodTest
import com.cigna.common.scm.CommonGit
import com.cigna.common.utils.FeatureFlags
import com.cigna.mocks.MockScript
import com.cigna.state.PipelineStateContext
import jenkins.plugins.http_request.ResponseContentSupplier

class YarnBuildSpec extends SinglePodTest {

    def cloudName = 'test-cloud'

    def setup() {
        explicitlyMockPipelineStep('updateGitStatus')
        explicitlyMockPipelineVariable("npmArtifactoryRegistryAuthUrl")
        explicitlyMockPipelineVariable("packageJson")
        explicitlyMockPipelineStep('readJSON')
        initScriptAndPsc()
    }

    def """When executeBuildAndTestStage is called yarn install is called and parallel
            is called with labels yarnTest and checkmarx"""() {
        when:
        def yarnBuild = new YarnBuild(
            config: [
                cloudName  : cloudName,
                artifactory: [
                    credentialsId  : 'required',
                    applicationName: 'test-2'
                ],
            ],
            script: script,
            psc: psc)
        simulatePodTemplate(psc, yarnBuild, cloudName)
        yarnBuild.executeBuildAndTestStage()

        then:
        1 * getPipelineMock("sh")("yarn install")
        1 * getPipelineMock("sh")("yarn test")
    }

    def """When executePublishStage is called with config.artifactory.credentialsId yarn config set
            registry is called, withCredentials is called, the yarnrc file is configured
            if there is an artifcatory
            scope, yarn config set scope is called, the application name is set in the package and yarn publish is called"""() {
        given:
        String appScope = artifactoryApplicationNameScope
        String appScopeRet = artifactoryApplicationNameScopeReturn
        def numCredentialCalls = 1
        if (FeatureFlags.podAutotuning.enabled) {
            numCredentialCalls = 2
        }
        ResponseContentSupplier response = new ResponseContentSupplier(
            '{ "results" : 0 }',
            200
        )
        when:
        def yarnBuild = new YarnBuild(
            config: [
                artifactory: [
                    credentialsId  : "test",
                    applicationName: appScope,
                ]
            ],
            script: script,
            psc: psc)
        simulatePodTemplate(psc, yarnBuild, cloudName)
        explicitlyMockPipelineVariable("artifactoryDeployerIdToken")
        yarnBuild.executePublishStage()

        then:
        1 * getPipelineMock('sh')({ it ==~ /yarn config set registry .*/ })
        1 * getPipelineMock('fileExists')(*_) >> true
        1 * getPipelineMock('readJSON')({ it.file ==~ /\.\/package.json/ }) >> [name: appScope, version: 'stuff']
        1 * getPipelineMock('readJSON')(*_) >> [results: 0]
        numCredentialCalls * getPipelineMock('withCredentials')(*_)
        2 * getPipelineMock('httpRequest')(*_) >> response
        if (appScopeRet) {
            1 * getPipelineMock("sh")("yarn config set scope cigna")
        }
        1 * getPipelineMock("sh")("yarn publish")

        where:
        artifactoryApplicationNameScope << ['test', "@cigna/test"]
        artifactoryApplicationNameScopeReturn << [null, "cigna"]
    }

    def """When executePublishStage is called without config.artifactory.credentialsId it is caught in validation"""() {
        when:
        def yarnBuild = new YarnBuild(
            config: [
                checkmarx  : [
                    settings     : [
                        CX_PROJECT_TEAM_NAME: 'foo'
                    ],
                    credentialsId: 'bar'
                ],
                sonarQube  : [
                    credentialsId: 'baz'
                ],
                artifactory: [:]
            ],
            script: {}
        )
        def issues = yarnBuild.validate()

        then:
        assert issues.size() >= 1
        assert issues.contains('Missing required Yarn Build specification: artifactory.credentialsId')
    }

    def """When executeBuildAndTestStage is called npm install is called and parallel
            is called with labels npmTest and checkmarx and audit is run"""() {
        when:
        def yarnBuild = new YarnBuild(
            config: [
                runAudit   : true,
                artifactory: [
                    credentialsId  : "test",
                    applicationName: "test",
                ]
            ],
            script: script,
            psc: psc)
        simulatePodTemplate(psc, yarnBuild, cloudName)
        yarnBuild.executeBuildAndTestStage()

        then:
        1 * getPipelineMock("sh")("yarn install")
        1 * getPipelineMock("sh")("yarn test")
        1 * getPipelineMock("sh")("yarn audit")
    }

    def """When package.json application name doesn't match artifactory config app name, throw"""() {
        when:
        getPipelineMock('fileExists')(_) >> true
        getPipelineMock('readJSON')(_) >> [name: 'test-1', version: "stuff"]
        def npmBuild = new YarnBuild(
            config: [
                artifactory: [
                    credentialsId  : 'required',
                    applicationName: 'test-2'
                ]
            ], script: script,
            psc: psc)
        simulatePodTemplate(psc, npmBuild, cloudName)
        npmBuild.executePublishStage()
        then:
        def exception = thrown(UnsupportedOperationException)
        assert exception.message.contains('does not match application name')
    }

    def """When executeBuildAndTestStage NULL applicationName check"""() {
        when:
        def yarnBuild = new YarnBuild(
            config: [
                runAudit   : true,
                artifactory: [
                    credentialsId  : "test",
                    applicationName: null
                ]
            ],
            script: script,
            psc: psc
        )
        simulatePodTemplate(psc, yarnBuild, cloudName)
        yarnBuild.executeBuildAndTestStage()

        then:
        1 * getPipelineMock("sh")("yarn install")
        1 * getPipelineMock("sh")("yarn test")
        1 * getPipelineMock("sh")("yarn audit")
    }

    def """When executeBuildAndTestStage NO applicationName check"""() {
        when:
        getPipelineMock('readJSON')(_) >> [name: 'test-1', version: "stuff"]
        def yarnBuild = new YarnBuild(
            config: [
                runAudit   : true,
                artifactory: [
                    credentialsId: "test"
                ]
            ],
            script: script,
            psc: psc
        )
        simulatePodTemplate(psc, yarnBuild, cloudName)
        yarnBuild.executeBuildAndTestStage()

        then:
        1 * getPipelineMock("sh")("yarn install")
        1 * getPipelineMock("sh")("yarn test")
        1 * getPipelineMock("sh")("yarn audit")
    }

    def """When packageJsonPath specified, correct name is used during readJSON execution"""() {
        when:
        def yarnBuild = new YarnBuild(
            config: [
                runAudit       : true,
                packageJsonPath: './subfolder',
                artifactory    : [
                    credentialsId: "test"
                ]
            ],
            script: script,
            psc: psc
        )
        simulatePodTemplate(psc, yarnBuild, cloudName)
        yarnBuild.checkIfApplicationNameAndVersionUsedInArtifactory(_) >> null
        yarnBuild.artifactoryApplicationNameScope() >> 'cigna'
        explicitlyMockPipelineVariable("artifactoryDeployerIdToken")
        yarnBuild.executePublishStage()

        then:
        1 * getPipelineMock("fileExists")(_) >> true
        1 * getPipelineMock("readJSON")({
            it.file == './subfolder/package.json'
        }) >> [name: 'test-1', version: "stuff"]
    }

    def """executePublishStage NO application name"""() {
        String appScope = artifactoryApplicationNameScope
        given:
        def numCredentialCalls = 1
        if (FeatureFlags.podAutotuning.enabled) {
            numCredentialCalls = 2
        }
        ResponseContentSupplier response = new ResponseContentSupplier(
            '{ "results" : 0 }',
            200
        )
        when:
        def yarnBuild = new YarnBuild(
            config: [
                artifactory: [
                    credentialsId: "test"
                ]
            ],
            script: script,
            psc: psc
        )
        simulatePodTemplate(psc, yarnBuild, cloudName)
        explicitlyMockPipelineVariable("artifactoryDeployerIdToken")
        yarnBuild.executePublishStage()

        then:
        1 * getPipelineMock("sh")({ it ==~ /yarn config set registry .*/ })
        1 * getPipelineMock("fileExists")(*_) >> true
        1 * getPipelineMock("readJSON")({
            it.file ==~ /\.\/package.json/
        }) >> [name: appScope, version: 'stuff']
        1 * getPipelineMock("readJSON")(_) >> [results: 0]
        numCredentialCalls * getPipelineMock("withCredentials")(*_)
        2 * getPipelineMock("httpRequest")(*_) >> response
        if (appScope.contains('@')) {
            1 * getPipelineMock("sh")("yarn config set scope cigna")
        }
        1 * getPipelineMock("sh")("yarn publish")

        where:
        artifactoryApplicationNameScope << ['test', "@cigna/test"]
    }

    def """When publishPath is specified, correct path is used during publish step"""() {
        given:
        ResponseContentSupplier response = new ResponseContentSupplier(
            '{ "results" : 0 }',
            200
        )
        when:
        def yarnBuild = new YarnBuild(
            config: [
                runAudit       : true,
                packageJsonPath: './subfolder',
                artifactory    : [
                    credentialsId: "test"
                ],
                publishPath    : 'mypublishpath'

            ],
            script: script,
            psc: psc
        )
        simulatePodTemplate(psc, yarnBuild, cloudName)
        yarnBuild.checkIfApplicationNameAndVersionUsedInArtifactory(_) >> null
        yarnBuild.artifactoryApplicationNameScope() >> 'cigna'
        explicitlyMockPipelineVariable("artifactoryDeployerIdToken")
        yarnBuild.executePublishStage()

        then:
        2 * getPipelineMock("httpRequest")(*_) >> response
        1 * getPipelineMock("fileExists")(_) >> true
        1 * getPipelineMock("readJSON")({
            it.file == './subfolder/package.json'
        }) >> [name: 'test-1', version: "stuff"]
        1 * getPipelineMock("readJSON")({
            it.text == '{ "results" : 0 }'
        }) >> [results: 0]
        1 * getPipelineMock('sh')({ it ==~ /yarn --cwd mypublishpath publish/ })
    }
}
