package com.cigna.builds

import com.cigna.mocks.MockScript
import com.cigna.jenkins_spock.JenkinsPipelineSpecification
import jenkins.plugins.http_request.ResponseContentSupplier

class NpmBuildSpec extends JenkinsPipelineSpecification {
    class Script extends MockScript {}

    def setup() {
        
        explicitlyMockPipelineStep('updateGitStatus')
        explicitlyMockPipelineVariable("npmArtifactoryRegistryAuthUrl")
        explicitlyMockPipelineStep('readJSON')
    }

    def """When executeBuildAndTestStage is called npm install is called and parallel
            is called with labels npmTest and checkmarx"""() {
        when:
            def script = new Script()
            def npmBuild = Spy (
                    NpmBuild,
                        constructorArgs: [
                        config: [
                            artifactory: [
                                credentialsId: 'required',
                                applicationName: 'test-2'
                            ],
                        ], script: script
                        ]
            )
            npmBuild.artifactoryApplicationNameScope() >> artifactoryApplicationNameScopeReturn
            npmBuild.executeBuildAndTestStage()

        then:
            1 * getPipelineMock("sh")("npm install")
            1 * getPipelineMock("sh")("npm test")
        where:
            artifactoryApplicationNameScopeReturn << [null, "cigna"]
    }

    def """When executePublishStage is called with config.artifactory.credentialsId npm config set
            registry is called, withCredentials is called, the npmrc file is configured
            if there is an artifcatory
            scope, npm config set scope is called, the application name is set in the package.json and npm publish is called"""() {
        when:
            def script = new Script()
            def npmBuild = Spy (
                    NpmBuild,
                        constructorArgs: [
                        config: [
                            artifactory: [
                                credentialsId: 'required',
                                applicationName: 'test-2'
                            ],
                        ], script: script
                        ]
            )
            npmBuild.checkIfApplicationNameAndVersionUsedInArtifactory(_) >> null
            npmBuild.artifactoryApplicationNameScope() >> artifactoryApplicationNameScopeReturn
            explicitlyMockPipelineVariable("artifactoryDeployerIdToken")
            npmBuild.executePublishStage()

        then:
            1 * getPipelineMock("sh")({it ==~ /npm config set registry .*/})
            1 * getPipelineMock("fileExists")(_) >> true
            1 * getPipelineMock("readJSON")(*_) >> [version: "stuff"]
            1 * getPipelineMock("withCredentials")(*_)
            1 * getPipelineMock("httpRequest")(*_)
            if (artifactoryApplicationNameScopeReturn) {
                1 * getPipelineMock("sh")("npm config set scope cigna")
            }
            1 * getPipelineMock("sh")("npm publish")

        where:
            artifactoryApplicationNameScopeReturn << [null, "cigna"]
    }

    def """When executePublishStage is called without config.artifactory.credentialsId it is caught in validation"""() {
        when:
            getPipelineMock('readJSON')(_) >> [name: 'test', version: '1.0.0']
            def npmBuild = new NpmBuild(
                config: [
                    checkmarx: [
                        settings: [
                            CX_PROJECT_TEAM_NAME: 'foo'
                        ],
                        credentialsId: 'bar'
                    ],
                    sonarQube: [
                        credentialsId: 'baz'
                    ],
                    artifactory: [:]
                ], script: {})
            def issues = npmBuild.validate()

        then:
            assert issues.size() >= 1
            assert issues.contains('Missing required Npm Build specification: artifactory.credentialsId')
    }

    def """When executeBuildAndTestStage is called npm install is called and parallel
            is called with labels npmTest and checkmarx and audit is run"""() {
        when:
            getPipelineMock('readJSON')(_) >> [name: 'test', version: '1.0.0']
            def script = new Script()
            def npmBuild = new NpmBuild(
                        config: [
                            runAudit: true,
                            artifactory: [
                                credentialsId: 'required',
                                applicationName: 'test-2'
                            ]
                        ], script: script)
            npmBuild.executeBuildAndTestStage()

        then:
            1 * getPipelineMock("sh")("npm install")
            1 * getPipelineMock("sh")("npm test")
            1 * getPipelineMock("sh")("npm audit")
    }

    def """When a list of commands is specified, they all execute in sequence"""() {
        when:
            getPipelineMock('readJSON')(_) >> [name: 'test', version: '1.0.0']
            def script = new Script()
            def npmBuild = new NpmBuild(
                        config: [
                            runAudit: true,
                            commands: [
                                    'install',
                                    'tsc',
                                    'build'
                            ] ,
                            artifactory: [
                                credentialsId: 'required',
                                applicationName: 'test-2'
                            ]
                        ], script: script)
            npmBuild.executeBuildAndTestStage()

        then:
            1 * getPipelineMock("sh")("npm install")
            1 * getPipelineMock("sh")("npm tsc")
            1 * getPipelineMock("sh")("npm build")
            1 * getPipelineMock("sh")("npm audit")
    }

    def """When package.json application name doesn't match artifactory config app name, throw"""() {
        when:
            def script = new Script()
            def npmBuild = new NpmBuild(
                        config: [
                            artifactory: [
                                credentialsId: 'required',
                                applicationName: 'test-2'
                            ]
                        ], script: script)
            npmBuild.executePublishStage()
        then:
            1 * getPipelineMock("fileExists")(_) >> true
            1 * getPipelineMock('readJSON')(*_) >> [name: 'test-1']
            def exception = thrown(UnsupportedOperationException)
            assert exception.message.contains('does not match application name')
    }

    def """without enableScopedAuth setting, executePublishStage uses scoped authentication API 
           call, whether scope is in artifact name or not"""() {
        given:
        explicitlyMockPipelineVariable("artifactoryDeployerIdToken")
        def script = new Script()
        def npmBuild = new NpmBuild(
                config: [
                        artifactory: [
                                credentialsId: 'required',
                                applicationName: appName
                        ]
                ], script: script)
        when:
        npmBuild.executePublishStage()

        then:
        1 * getPipelineMock("fileExists")(_) >> true
        // reading package.json
        1 * getPipelineMock('readJSON')(*_) >> [name: appName, version: '1.0.0']
        // artifactory search returns no existing artifacts
        1 * getPipelineMock("httpRequest")({it.url.contains('search/artifact?name')}) >> 
                new ResponseContentSupplier("", 200)
        // parsing mock AF response
        1 * getPipelineMock('readJSON')([text: '']) >> [:]
        // assert that we're calling the scoped auth endpoint
        1 * getPipelineMock("httpRequest")({it.url.endsWith("auth/$scopeForAuth")})
        
        where:
        appName << ['test-app', '@cigna/test-app', '@someOtherScope/test-app']
        scopeForAuth << ['cigna', 'cigna', 'someOtherScope']
    }

    def """with enableScopedAuth specifically disabled, executePublishStage uses non-scoped 
           authentication API call, whether scope is in artifact name or not"""() {
        given:
        explicitlyMockPipelineVariable("artifactoryDeployerIdToken")
        def script = new Script()
        def npmBuild = new NpmBuild(
                config: [
                        artifactory: [
                                credentialsId: 'required',
                                applicationName: appName
                        ],
                        enableScopedAuth: false
                ], script: script)
        when:
        npmBuild.executePublishStage()

        then:
        1 * getPipelineMock("fileExists")(_) >> true
        // reading package.json
        1 * getPipelineMock('readJSON')(*_) >> [name: appName, version: '1.0.0']
        // artifactory search returns no existing artifacts
        1 * getPipelineMock("httpRequest")({it.url.contains('search/artifact?name')}) >>
                new ResponseContentSupplier("", 200)
        // parsing mock AF response
        1 * getPipelineMock('readJSON')([text: '']) >> [:]
        // assert that we're calling the scoped auth endpoint
        1 * getPipelineMock("httpRequest")({it.url.endsWith("npm/auth")})

        where:
        appName << ['test-app', '@cigna/test-app', '@someOtherScope/test-app']
        scopeForAuth << ['cigna', 'cigna', 'someOtherScope']
    }
}
