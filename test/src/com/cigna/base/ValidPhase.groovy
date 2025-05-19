package com.cigna.base

import com.cloudbees.groovy.cps.NonCPS

class ValidPhase extends Phase {
    ValidPhase() {
        containerName = 'validPhase'
        containerMemory = '25Mi'
        containerCpu = '25m'
        containerImage = 'enterprise-devops/validImage'
        containerVersion = 'validVersion'
    }

    @Override
    Boolean prePodConfig() {
        basePodConfig = [
            volumes   : [],
            containers: [
                [
                    name           : 'sonar',
                    image          : "enterprise-devops/defaultSonarImage:defaultVersion",
                    imagePullPolicy: containerImagePullPolicy,
                    resources      : [
                        requests: [
                            cpu   : '100m',
                            memory: '100Mi'
                        ],
                        limits  : [
                            cpu   : '250m',
                            memory: '250Mi'
                        ]
                    ]
                ],
                [
                    name           : 'checkmarx',
                    image          : 'enterprise-devops/defaultScanImage:defaultVersion',
                    imagePullPolicy: containerImagePullPolicy,
                    resources      : [
                        requests: [
                            cpu   : '25m',
                            memory: '25Mi'
                        ],
                        limits  : [
                            cpu   : '50m',
                            memory: '50Mi'
                        ]
                    ]
                ]
            ]
        ]
        additionalPodConfig = [
            volumes   : [],
            containers: [
                [
                    name           : containerName,
                    image          : "${containerImage}:${containerVersion}",
                    imagePullPolicy: containerImagePullPolicy,
                    resources      : [
                        requests: [
                            cpu   : containerCpu,
                            memory: containerMemory
                        ],
                        limits  : [
                            cpu   : containerCpu,
                            memory: containerMemory
                        ]
                    ]
                ],
                [
                    name           : 'secondPhase',
                    image          : "${containerImage}:${containerVersion}",
                    imagePullPolicy: containerImagePullPolicy,
                    resources      : [
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
}
