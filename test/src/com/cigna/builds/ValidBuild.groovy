package com.cigna.builds

import com.cigna.common.request.CurlRequestor

class ValidBuild extends Build {
    ValidBuild() {
        containerName = 'build-this'
        containerImage = 'enterprise-devops/slick-image'
        containerVersion = 'latest'
        containerMemory = '100Mi'
        containerCpu = '250m'
    }
    String gitBranchValue = 'master'
    

    @Override
    Boolean prePodConfig() {
        additionalPodConfig = [
                'volumes'   : [],
                'containers': [
                        [
                                name     : "${containerName}",
                                image    : "${containerImage}:${containerVersion}",
                                resources: [
                                        requests: [
                                                cpu   : containerCpu,
                                                memory: containerMemory
                                        ],
                                        limits  : [
                                                cpu   : containerCpu,
                                                memory: containerMemory
                                        ]
                                ]
                        ]
                ]
        ]
        super.prePodConfig()
    }

    @Override
    String gitBranch() {
        return gitBranchValue
    }

    @Override
    void executePreBuildStage() {}

    @Override
    void executeBuildAndTestStage() { 0 }

    @Override
    void executePublishStage() {}

    List validatePhase() {
        []
    }

    void setMockCurlRequestor(CurlRequestor myCurlRequestor) {
        this.curlRequestor = myCurlRequestor
    }

}
