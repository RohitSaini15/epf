package com.cigna.builds

import com.cigna.common.kubernetes.PodConfigGenerator

/**
 * A scan only build for checkmarx and sonarqube
 */
import com.cigna.common.kubernetes.PodTemplateCreator

class ScanOnlyBuild extends Build {

    ScanOnlyBuild() {
        containerName = 'maven-alpinev3-jdk-8-alpine-1'
        containerImage = 'enterprise-devops/maven-alpine'
        containerVersion = '3-jdk-8-alpine-1'
        containerMemory = '2000Mi'
        containerCpu = '1000m'
        stashIncludePattern = ''
    }

    @Override
    Boolean prePodConfig() {
        List<Map> env = []

        PodTemplateCreator containerTemplate = PodTemplateCreator.newPodTemplateCreator()
        def mappedImage = "${containerImage}:${containerVersion}"
        containerName = PodConfigGenerator.getContainerName(mappedImage)
        containerTemplate.addContainer(
            containerName,
            mappedImage,
            500,
            1000,
            1000,
            2000,
            env
        )
        containerTemplate.addVolumeMount(containerName, 'setup-sonar', '/home/jenkins/agent/setup-sonar')

        additionalPodConfig = [
                'volumes'   : [],
                'containers': [
                        containerTemplate.getContainer(containerName)
                ]
        ]
        super.prePodConfig()
    }

    @Override
    void executeBuildAndTestStage() {
        script.echo('no build and test steps for this build type -> executeBuildAndTestStage')
    }

    @Override
    void executePublishStage() {
        script.echo('no build and test steps for this build type -> executePublishStage')
    }
}
