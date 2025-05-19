package com.cigna.common.utils


import spock.lang.Specification

class CheckpointUtilsSpec extends Specification {
    def '''verify checkpoint pattern matching'''() {
        when:
        def actualValue = CheckpointUtils.isCheckpointPhase(podGroupName)

        then:
        actualValue == expectedOutcome
        where:
        podGroupName << ['checkpoint-5', 'checkpoint-10000', 'checkpoint-id', 'myfancycheckpointfreestylephase']
        expectedOutcome << [true, true, false, false]
    }

    def '''check pod groups are updated based upon presence of checkpoints'''() {
        expect:
        CheckpointUtils.assignPodGroupsByCheckpointGrouping(input) == expected
        where:
        input << [
                [
                        [cloudName: 'cloud1', buildType: 'maven'],
                        [cloudName: 'cloud1', checkpointType: 'simple'],
                        [cloudName: 'cloud1', packageType: 'kaniko']
                ],
                [
                        [cloudName: 'cloud1', buildType: 'maven'],
                        [cloudName: 'cloud2', buildType: 'maven'],
                        [cloudName: 'cloud1', checkpointType: 'simple'],
                        [cloudName: 'cloud1', packageType: 'kaniko']
                ],
                [
                        [cloudName: 'cloud1', checkpointType: 'simple'],
                        [cloudName: 'cloud2', checkpointType: 'simple'],
                        [cloudName: 'cloud1', checkpointType: 'simple'],
                        [cloudName: 'cloud2', checkpointType: 'simple']
                ],
                [
                        [cloudName: 'cloud1', buildType: 'maven'],
                        [cloudName: 'cloud2', buildType: 'maven'],
                        [cloudName: 'cloud2', packageType: 'kaniko'],
                        [cloudName: 'cloud1', packageType: 'kaniko']
                ],
                [
                        [cloudName: 'cloud1', buildType: 'maven'],
                        [cloudName: 'cloud2', buildType: 'maven'],
                        [cloudName: 'cloud1', buildType: 'maven'],
                        [cloudName: 'cloud2', buildType: 'maven'],
                        [cloudName: 'cloud1', checkpointType: 'simple'],
                        [cloudName: 'cloud1', packageType: 'kaniko'],
                        [cloudName: 'cloud1', packageType: 'kaniko'],
                        [cloudName: 'cloud1', packageType: 'kaniko'],
                        [cloudName: 'cloud1', checkpointType: 'simple'],
                        [cloudName: 'cloud1', deploymentType: 'phases', phases : [
                                [ deploymentType: 'argo', ],
                                [ deploymentType: 'argo', ],
                        ] ],
                        [cloudName: 'cloud1', deploymentType: 'openshift'],
                        [cloudName: 'cloud1', deploymentType: 'helm'],
                ],

        ]

        expected << [
                [
                        [cloudName: 'cloud1', buildType: 'maven', podGroup: 'cloud1-1'],
                        [cloudName: 'cloud1', checkpointType: 'simple', podGroup: 'checkpoint-1'],
                        [cloudName: 'cloud1', packageType: 'kaniko', podGroup: 'cloud1-2'],
                ],
                [
                        [cloudName: 'cloud1', buildType: 'maven', podGroup: 'cloud1-1'],
                        [cloudName: 'cloud2', buildType: 'maven', podGroup: 'cloud2-1'],
                        [cloudName: 'cloud1', checkpointType: 'simple', podGroup: 'checkpoint-1'],
                        [cloudName: 'cloud1', packageType: 'kaniko', podGroup: 'cloud1-2'],
                ],
                [
                        [cloudName: 'cloud1', checkpointType: 'simple', podGroup: 'checkpoint-1'],
                        [cloudName: 'cloud2', checkpointType: 'simple', podGroup: 'checkpoint-2'],
                        [cloudName: 'cloud1', checkpointType: 'simple', podGroup: 'checkpoint-3'],
                        [cloudName: 'cloud2', checkpointType: 'simple', podGroup: 'checkpoint-4'],
                ],
                [
                        [cloudName: 'cloud1', buildType: 'maven', podGroup: 'cloud1-1'],
                        [cloudName: 'cloud2', buildType: 'maven', podGroup: 'cloud2-1'],
                        [cloudName: 'cloud2', packageType: 'kaniko', podGroup: 'cloud2-1'],
                        [cloudName: 'cloud1', packageType: 'kaniko', podGroup: 'cloud1-1']

                ],
                [
                        [cloudName: 'cloud1', buildType: 'maven', podGroup: 'cloud1-1'],
                        [cloudName: 'cloud2', buildType: 'maven', podGroup: 'cloud2-1'],
                        [cloudName: 'cloud1', buildType: 'maven', podGroup: 'cloud1-1'],
                        [cloudName: 'cloud2', buildType: 'maven', podGroup: 'cloud2-1'],
                        [cloudName: 'cloud1', checkpointType: 'simple', podGroup: 'checkpoint-1' ],
                        [cloudName: 'cloud1', packageType: 'kaniko', podGroup: 'cloud1-2'],
                        [cloudName: 'cloud1', packageType: 'kaniko', podGroup: 'cloud1-2'],
                        [cloudName: 'cloud1', packageType: 'kaniko', podGroup: 'cloud1-2'],
                        [cloudName: 'cloud1', checkpointType: 'simple', podGroup: 'checkpoint-2'],
                        [cloudName: 'cloud1', deploymentType: 'phases', phases : [
                                [ deploymentType: 'argo', cloudName: 'cloud1', podGroup: 'cloud1-3' ],
                                [ deploymentType: 'argo', cloudName: 'cloud1',podGroup: 'cloud1-3' ],
                        ], podGroup: 'cloud1-3' ],
                        [cloudName: 'cloud1', deploymentType: 'openshift', podGroup: 'cloud1-3' ],
                        [cloudName: 'cloud1', deploymentType: 'helm', podGroup: 'cloud1-3' ],
                ],

        ]
    }

    def '''nested references to the same object end up with the right pod group'''() {
        given:
        def deployPhase = [deploymentType: 'argo']
        def deployPhase2 = deployPhase.clone()
        def phaseConfigs = [
            [cloudName: 'cloud1', buildType: 'maven'],
            [cloudName: 'cloud1', checkpointType: 'simple'],
            [cloudName: 'cloud1', packageType: 'kaniko'],
            [cloudName: 'cloud1', checkpointType: 'simple'],
            [cloudName: 'cloud1', deploymentType: 'phases', phases: [
                deployPhase,
                deployPhase2
            ]],
            deployPhase,
            deployPhase2,
            [cloudName: 'cloud1', deploymentType: 'openshift'],
            [cloudName: 'cloud1', deploymentType: 'helm'],

        ]
        expect:
        CheckpointUtils.assignPodGroupsByCheckpointGrouping(phaseConfigs) == [
            [cloudName: 'cloud1', buildType: 'maven', podGroup: 'cloud1-1'],
            [cloudName: 'cloud1', checkpointType: 'simple', podGroup: 'checkpoint-1'],
            [cloudName: 'cloud1', packageType: 'kaniko', podGroup: 'cloud1-2'],
            [cloudName: 'cloud1', checkpointType: 'simple', podGroup: 'checkpoint-2'],
            [cloudName : 'cloud1', deploymentType: 'phases', phases: [
                [deploymentType: 'argo', podGroup: 'cloud1-3', cloudName: 'cloud1'],
                [deploymentType: 'argo', podGroup: 'cloud1-3', cloudName: 'cloud1'],
            ], podGroup: 'cloud1-3'],
            [deploymentType: 'argo', podGroup: 'cloud1-3', cloudName: 'cloud1'],
            [deploymentType: 'argo', podGroup: 'cloud1-3', cloudName: 'cloud1'],
            [cloudName: 'cloud1', deploymentType: 'openshift', podGroup: 'cloud1-3'],
            [cloudName: 'cloud1', deploymentType: 'helm', podGroup: 'cloud1-3'],
        ]
    }
}
