package com.cigna.remote

public class ValidRemote extends Remote {

    String containerName = 'test'
    Map<String, Object> additionalPodConfig = [
        'volumes': [],
        'containers': [
            [
                name: "${containerName}"
            ]
        ]
    ]

    @Override
    void run() {}

    List validatePhase() {
        []
    }
}