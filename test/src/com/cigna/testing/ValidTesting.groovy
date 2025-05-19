package com.cigna.testing

public class ValidTesting extends Testing {

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
    void runImpl() {}

    List validatePhase() {
        []
    }
}