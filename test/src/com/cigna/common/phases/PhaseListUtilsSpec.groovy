package com.cigna.common.phases

import com.cigna.builds.MavenBuild
import com.cigna.builds.ValidBuild
import com.cigna.deployment.HelmDeployment
import spock.lang.Specification

class PhaseListUtilsSpec extends Specification {

    def '''sanitizeForJson returns desired values for #label'''() {
        expect:
        PhaseListUtils.sanitizeForJson(input) == output
        where:
        label      | input                                          | output
        null       | null                                           | null
        'strings'  | 'a string'                                     | 'a string'
        'closures' | { -> "a closure" }                             | '<instance of Closure>'
        'phases'   | new ValidBuild()                               | '<instance of ValidBuild>'
        'lists'    | [{ -> }, new ValidBuild()]                     | ['<instance of Closure>', '<instance of ValidBuild>']
        'maps'     | [a: 1, c: { d -> "xyz" }, p: new ValidBuild()] | [a: 1, c: '<instance of Closure>', p: '<instance of ValidBuild>']
    }

    def '''sanitizeForJson can cleans up closures and phase instances in a nested structure'''() {
        given:
        // a couple of instances of phase to sprinkle into the phase list
        def inst = [m: new MavenBuild(),
                    h: new HelmDeployment()]
        List<Map<String, Object>> original = [
            [
                buildType    : 'fake',
                phaseInstance: inst.m
            ],
            [
                parallelType : 'jenkins',
                phaseInstance: inst.m,
                capture      : { throw new Exception("this better not throw") },
                phases       : [
                    first : [
                        buildType    : 'fake',
                        phaseInstance: inst.h,
                        run          : {}
                    ],
                    second: [
                        buildType    : 'fake',
                        phaseInstance: inst.h
                    ]
                ]
            ],
            [
                deploymentType: 'unit testing',
                phaseInstance : inst.m,
                testing       : [
                    [
                        testType     : 'unit',
                        phaseInstance: inst.m,
                        afterPhase   : [closure: { throw new Exception("this better not throw") }]
                    ],
                    [
                        testType     : 'integration',
                        phaseInstance: inst.h
                    ]
                ],
                ticket        : [
                    ticketType   : 'testing',
                    phaseInstance: inst.m
                ],
                phases        : [
                    [
                        buildType    : 'fake 1',
                        phaseInstance: inst.m,
                        run          : {}
                    ],
                    [
                        buildType    : 'fake 2',
                        phaseInstance: inst.h
                    ]
                ]
            ]
        ]
        def phases = original.clone()

        when:
        def cleanedPhases = PhaseListUtils.sanitizeForJson(phases)
        then:
        // cleaned up map has no phase or closure instances
        cleanedPhases == [
            [
                buildType    : 'fake',
                phaseInstance: '<instance of MavenBuild>'
            ],
            [
                parallelType : 'jenkins',
                phaseInstance: '<instance of MavenBuild>',
                capture      : '<instance of Closure>',
                phases       : [
                    first : [
                        buildType    : 'fake',
                        phaseInstance: '<instance of HelmDeployment>',
                        run          : '<instance of Closure>'
                    ],
                    second: [
                        buildType    : 'fake',
                        phaseInstance: '<instance of HelmDeployment>'
                    ]
                ]
            ],
            [
                deploymentType: 'unit testing',
                phaseInstance : '<instance of MavenBuild>',
                testing       : [
                    [
                        testType     : 'unit',
                        phaseInstance: '<instance of MavenBuild>',
                        afterPhase   : [closure: '<instance of Closure>']
                    ],
                    [
                        testType     : 'integration',
                        phaseInstance: '<instance of HelmDeployment>'
                    ]
                ],
                ticket        : [
                    ticketType   : 'testing',
                    phaseInstance: '<instance of MavenBuild>'
                ],
                phases        : [
                    [
                        buildType    : 'fake 1',
                        phaseInstance: '<instance of MavenBuild>',
                        run          : '<instance of Closure>'
                    ],
                    [
                        buildType    : 'fake 2',
                        phaseInstance: '<instance of HelmDeployment>'
                    ]
                ]
            ]
        ]
        // original is untouched
        phases == original
    }
}
