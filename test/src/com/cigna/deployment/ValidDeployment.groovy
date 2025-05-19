package com.cigna.deployment

public class ValidDeployment extends Deployment {
    ValidDeployment() {
        containerName = 'test'
        containerImage = 'enterprise-devops/amazing'
        containerVersion = 'latest'
    }

    @Override
    Boolean prePodConfig() {
        additionalPodConfig = [
                volumes   : [],
                containers: [
                        [
                                image  : "$containerImage:$containerVersion",
                                name   : "${containerName}",
                        ]
                ]
        ]
    }

    @Override
    void deploy() {
        script.echo("deploying...")
    }

    List validatePhase() {
        []
    }
}
