package com.cigna.builds

import com.cigna.SinglePodTest
import com.cigna.common.scm.CommonGit
import com.cigna.common.utils.FailedAce
import com.cigna.mocks.MockScript
import com.cigna.state.PipelineStateContext

class AceBuildSpec extends SinglePodTest {
    def cloudName = 'test-cloud'

    def remoteConfigs = [
            [
                    url: "https://github.sys.cigna.com/somecool_project/super_cool.git"
            ]
    ]

    def setup() {
        getPipelineMock("scm.getProperty")('userRemoteConfigs') >> remoteConfigs
        explicitlyMockPipelineStep('findFiles')
        explicitlyMockPipelineStep('override')
        initScriptAndPsc()
    }

    def """When executeBuildAndTestStage is called with no config, then an exception is thrown."""() {
        given:
        def aceBuild = new AceBuild(
                config: [:],
                script: script,
                psc: psc
        )

        when:
        simulatePodTemplate(psc, aceBuild, cloudName)
        aceBuild.executeBuildAndTestStage()

        then:
        thrown(FailedAce)
    }

    def """When executePublishStage is called, then resources are published to Artifactory"""() {
        setup:
        def aceBuild = new AceBuild(
                config: [
                        artifactory: [
                                credentialsId: 'ARTIFACTORY_ACE_CREDS',
                        ],
                ],
                script: script,
                psc: psc

        )
        def barFiles = [[
                                path: '/hello/world/CommonTraceLogger_SharedLib-4274b6365ae1cf61bb875cb09fd1931fcf61e173_20230731181053_40.bar',
                                name: 'CommonTraceLogger_SharedLib-4274b6365ae1cf61bb875cb09fd1931fcf61e173_20230731181053_40.bar',
                        ], [
                                path: '/hello/world/CommonTraceLogger_SharedLib-4274b6365ae1cf61bb875cb09fd1931fcf61e173_20230731181053_41.bar',
                                name: 'CommonTraceLogger_SharedLib-4274b6365ae1cf61bb875cb09fd1931fcf61e173_20230731181053_41.bar',
                        ]]

        when:
        aceBuild.numBarFilesCreated = 2
        simulatePodTemplate(psc, aceBuild, cloudName)
        aceBuild.executePublishStage()

        then:
        1 * getPipelineMock("echo")('Establishing the Artifactory server details')
        1 * getPipelineMock("echo")('Checking the Artifactory connection details')
        1 * getPipelineMock("echo")('Pinging the Artifactory server to ensure its up and running')
        1 * getPipelineMock("findFiles")(*_) >> barFiles
        for (bf in barFiles) {
            1 * getPipelineMock("echo")("Uploading ${bf.path} to Artifactory")
        }
    }
}