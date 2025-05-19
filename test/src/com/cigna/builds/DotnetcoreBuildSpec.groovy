package com.cigna.builds

import com.cigna.SinglePodTest

class DotnetcoreBuildSpec extends SinglePodTest {

    def setup() {
        explicitlyMockPipelineStep('updateGitStatus')
        initScriptAndPsc()
    }

    def """When the phase is run without optional params, the defaults are used"""() {
        when:
        DotnetcoreBuild dotnetcoreBuild = new DotnetcoreBuild(config: [
            artifactory: [
                credentialsId: 'test-creds'
            ]
        ],
            script: script,
            psc: psc)

        dotnetcoreBuild.executeBuildAndTestStage()
        then:
        1 * getPipelineMock("sh")({ it ==~ 'dotnet restore ' })
        1 * getPipelineMock('sh')({ it ==~ 'dotnet publish  -c Debug -o output-debug' })
        1 * getPipelineMock('sh')({ it ==~ 'dotnet publish  -c Release -o output' })
    }

    def """When the phase is run with optional params, the provided values are used"""() {
        when:
        DotnetcoreBuild dotnetcoreBuild = new DotnetcoreBuild(config: [
            artifactory: [
                credentialsId: 'test-creds'
            ],
            dotnet     : [
                project   : 'my/fake/path',
                outputDir : 'target',
                testLogger: 'nunit',
                testParams: '-p foobar'
            ]
        ],
            script: script,
            psc: psc)

        dotnetcoreBuild.executeBuildAndTestStage()

        then:
        1 * getPipelineMock("sh")({ it ==~ 'dotnet restore my/fake/path' })
        1 * getPipelineMock("sh")({ it ==~ 'dotnet test my/fake/path -p foobar --logger:nunit -p:CollectCoverage=true -p:CoverletOutputFormat=opencover' })
        1 * getPipelineMock('sh')({ it ==~ 'dotnet publish my/fake/path -c Debug -o target-debug' })
        1 * getPipelineMock('sh')({ it ==~ 'dotnet publish my/fake/path -c Release -o target' })
    }

    def """When the artifactory push method is called without a nugetPath, it does not package or push"""() {
        when:
        DotnetcoreBuild dotnetcoreBuild = new DotnetcoreBuild(config: [
            artifactory: [
                credentialsId: 'test-creds'
            ],
            dotnet     : [
                // This will get set by executeBuildAndTest()
                outputDir: 'output'
            ]
        ],
            script: script,
            psc: psc)

        dotnetcoreBuild.executePublishStage()

        then:
        1 * getPipelineMock('echo')(*_)
        1 * getPipelineMock('sh')({ it ==~ 'rm -rf output-debug' })
    }

    def """When the artifactory push method is called with a nugetPath, it packages and pushes to Artifactory"""() {
        when:
        DotnetcoreBuild dotnetcoreBuild = new DotnetcoreBuild(config: [
            nugetPath    : 'Cigna/ValueStream/Product/Component',
            artifactory: [
                credentialsId: 'test-creds',
            ],
            dotnet     : [
                // This will get set by executeBuildAndTest()
                outputDir: 'output'
            ]
        ],
            script: script,
            psc: psc)

        explicitlyMockPipelineVariable("ARTIFACTORY_USER")
        explicitlyMockPipelineVariable("ARTIFACTORY_APIKEY")

        dotnetcoreBuild.executePublishStage()

        then:
        1 * getPipelineMock("sh").call('dotnet pack -o nuget -c Release')
        1 * getPipelineMock("sh").call({
            it =~ /.$artifactoryUrl./
        })
        1 * getPipelineMock("usernamePassword.call").call(*_)

        where:
        artifactoryUrl << ["https://cigna.jfrog.io/artifactory/api"]
    }
}