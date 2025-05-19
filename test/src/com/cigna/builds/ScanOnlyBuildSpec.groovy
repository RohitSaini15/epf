package com.cigna.builds

import com.cigna.SinglePodTest

class ScanOnlyBuildSpec extends SinglePodTest {

    def setup() {
        explicitlyMockPipelineStep('updateGitStatus')
        initScriptAndPsc()
    }

    def """Testing that it does nothing"""() {
        when:

        def noopBuild = new ScanOnlyBuild(
            config: [:],
            script: script

        )
        noopBuild.init()
        noopBuild.prePodConfig()
        noopBuild.executeBuildAndTestStage()
        noopBuild.executePublishStage()

        then:
        1 * getPipelineMock("echo")("no build and test steps for this build type -> executeBuildAndTestStage")
        1 * getPipelineMock("echo")("no build and test steps for this build type -> executePublishStage")
    }
}