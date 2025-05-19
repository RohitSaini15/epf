package com.cigna.common.phases

import com.cigna.SinglePodTest
import com.cigna.base.ValidPhase
import com.cigna.builds.GradleBuild
import com.cigna.common.kubernetes.PodConfigGenerator
import com.cigna.common.utils.Utils
import com.cigna.linting.Linting

class PodSelectorSpec extends SinglePodTest {
    def cloudName = 'test-cloud'

    def setup() {
        initScriptAndPsc()
    }

    def '''Verify grouping lists of containers by container id returns the max mem and cpu for these container groups'''() {
        given:
        def listOfContainers = [
            [phaseInstance: [basePodConfig      : [containers: []],
                             additionalPodConfig: [containers: [image: 'container1:latest', resources: [limits: [cpu: 1000, memory: 5000, "ephemeral-storage": 200]]]]]],
            [phaseInstance: [basePodConfig      : [containers: []],
                             additionalPodConfig: [containers: [image: 'container1:1.0.0', resources: [limits: [cpu: 1400, memory: 1000, "ephemeral-storage": 2000]]]]]],
            [phaseInstance: [basePodConfig      : [containers: []],
                             additionalPodConfig: [containers: [image: 'container2:latest', resources: [limits: [cpu: 1100, memory: 2500, "ephemeral-storage": 400]]]]]],
            [phaseInstance: [basePodConfig      : [containers: []],
                             additionalPodConfig: [containers: [image: 'container3:latest', resources: [limits: [cpu: 1200, memory: 2100, "ephemeral-storage": 600]]]]]],
            [phaseInstance: [containerImage     : 'container1', containerVersion: 'latest', basePodConfig: [containers: []],
                             additionalPodConfig: [containers: [image: 'container1:latest', resources: [limits: [cpu: 1300, memory: 2000, "ephemeral-storage": 300]]]]]],
            [phaseInstance: [basePodConfig      : [containers: []],
                             additionalPodConfig: [containers: [image: 'container2:latest', resources: [limits: [cpu: 1400, memory: 2000, "ephemeral-storage": 2500]]]]]],
            [phaseInstance: [basePodConfig      : [containers: []],
                             additionalPodConfig: [containers: [image: 'container3:latest', resources: [limits: [cpu: 11400, memory: 2000]]]]]],
        ]

        def expectedResults = [
            'container1:latest': [cpu: 1300, memory: 5000, ephemeral: 300],
            'container1:1.0.0' : [cpu: 1400, memory: 1000, ephemeral: 2000],
            'container2:latest': [cpu: 1400, memory: 2500, ephemeral: 2500],
            'container3:latest': [cpu: 11400, memory: 2100, ephemeral: 600],
        ]

        def mapOfMaxes = PodSelector.findGroupedContainerResourceMaximums(listOfContainers)

        expect:
        mapOfMaxes == expectedResults
    }

    def '''Verify that ephemeral-storage is only included if it is defined'''() {
        given:
        def listOfContainers = [
            [image: 'container1:latest', cpu: 1000, memory: 5000],
            [image: 'container1:1.0.0', cpu: 1400, memory: 1000],
            [image: 'container2:latest', cpu: 1100, memory: 2500],
            [image: 'container3:latest', cpu: 1200, memory: 2100],
            [image: 'container1:latest', cpu: 1300, memory: 2000],
            [image: 'container2:latest', cpu: 1400, memory: 2000],
            [image: 'container3:latest', cpu: 11400, memory: 2000]
        ]

        def expandedContainers = listOfContainers.collect { c ->
            [phaseInstance:
                 [basePodConfig      : [containers: [
                     image    : c.image,
                     resources: [limits: [
                         cpu   : c.cpu,
                         memory: c.memory
                     ]]
                 ]],
                  additionalPodConfig: [containers: [[]]]]
            ]
        }


        def expectedResults = [
            'container1:latest': [cpu: 1300, memory: 5000, ephemeral: 0],
            'container1:1.0.0' : [cpu: 1400, memory: 1000, ephemeral: 0],
            'container2:latest': [cpu: 1400, memory: 2500, ephemeral: 0],
            'container3:latest': [cpu: 11400, memory: 2100, ephemeral: 0],
        ]
        when:
        def mapOfMaxes = PodSelector.findGroupedContainerResourceMaximums(expandedContainers)

        then:
        mapOfMaxes == expectedResults

    }

    def '''Verify that extracting a numeric with a size modifier is multiplied by the correct scaling factor for #scaledValue'''() {
        when:
        def actualValue = Utils.extractNumeric(scaledValue)
        then:
        actualValue == expectedValue
        where:
        scaledValue << ['2000Mi', '2Gi', '10Gi']
        expectedValue << [2000, 2000, 10000]
    }

    def '''Verify that two phases with the same base container but different basePodConfig values result in the correct set of containers'''() {
        given:
        def cloudName = 'test-cloud'
        def gradleBuild = new GradleBuild(config: [cloudName: cloudName], script: script, psc: psc)
        def lintingPhase = new Linting(config: [cloudName: cloudName, lintingTypes: [gradle: []], maven: []], script: script, psc: psc)
        def gradlePhase = [
            phaseInstance: gradleBuild,
            cloudName    : cloudName
        ]
        def lintingMap = [
            phaseInstance: lintingPhase,
            cloudName    : cloudName
        ]
        def phases = [
            lintingMap,
            gradlePhase,
        ]

        when:
        psc.podSelector.calculatePodTemplates([cloudName: 'test-cloud'], phases)
        then:
        psc.podSelector.podTemplates['test-cloud'].contains('sonarv48')
        psc.podSelector.podTemplates['test-cloud'].contains('toolshack')
        psc.podSelector.podTemplates['test-cloud'].contains('gradlevlatest')
    }

    def '''grouping by pod group gives the correct set of phases for each pod and sets the baseCloudName correctly'''() {
        given:

        def g1 = new GradleBuild(config: [podGroup: 'gradle'], script: script, psc: psc)
        def g2 = new GradleBuild(config: [podGroup: 'gradle'], script: script, psc: psc)
        def l1 = new Linting(config: [podGroup: 'linting', lintingTypes: [gradle: []], maven: []], script: script, psc: psc)
        def l2 = new Linting(config: [podGroup: 'linting', lintingTypes: [gradle: []], maven: []], script: script, psc: psc)

        g1.config.phaseInstance = g1
        g2.config.phaseInstance = g2
        l1.config.phaseInstance = l1
        l2.config.phaseInstance = l2
        def phases = [
            g1.config,
            g2.config,
            l1.config,
            l2.config,
        ]

        when:
        psc.podSelector.calculatePodTemplates([cloudName: 'test-cloud'], phases)
        then:
        psc.podSelector.podTemplates['gradle'].contains('sonarv48')
        psc.podSelector.podTemplates['gradle'].contains('toolshack')
        psc.podSelector.podTemplates['gradle'].contains('gradlevlatest')
    }

    def '''Verify that pipeline metadata is converted to environment variables'''() {
        given:
        explicitlyMockPipelineStep('readYaml')

        def config = [
            cloudName    : 'test-cloud',
            testString   : 'lookup:{FIRST_SECOND_THIRD} there lookup:{FIRST_TITLE}',
            notYetString : 'lookup:imageName',
            notYetString1: 'lookup:{imageName}',
        ]
        def g1 = new GradleBuild(config: config, script: script, psc: psc)

        g1.config.phaseInstance = g1

        1 * getPipelineMock('fileExists')(_) >> true
        1 * getPipelineMock('readYaml')(_) >> [
            first: [
                second: [
                    third: 'Hello'
                ],
                title : 'Zaphod'
            ]
        ]

        psc.podSelector.calculatePodTemplates(config, [g1.config])
        psc.podSelector.pipelineMetadataFileName = '.productdata'
        when:
        def envToWrap = psc.podSelector.LoadPipelineMetadataInPod(psc)
        psc.podSelector.CheckAndUpdateLookupVariables(psc)
        then:
        // Verify that environment will be propagated to any shell script environments
        script.env.FIRST_SECOND_THIRD == 'Hello'
        script.env.FIRST_TITLE == 'Zaphod'

        // verify environment will be propagated to withEnv()
        envToWrap.FIRST_SECOND_THIRD == 'Hello'
        envToWrap.FIRST_TITLE == 'Zaphod'

        // Verify straight string expansions are supported in the config maps
        psc.podSelector.phaseConfigs[0].testString == 'Hello there Zaphod'
        psc.podSelector.phaseConfigs[0].notYetString == 'lookup:imageName'
        psc.podSelector.phaseConfigs[0].notYetString1 == 'lookup:{imageName}'
    }

    def '''env vars are added to metadata if and only if envInMetadata is truthy'''() {
        given:
        script.env.TEST_VARIABLE = "here I am"
        script.env.ANOTHER = "get me"
        def phase = new ValidPhase(config: phaseConfig,
            script: script, psc: psc)
        phase.config.phaseInstance = phase
        psc.podSelector.calculatePodTemplates(podTemplateConfig, [phase.config])
        when:
        psc.podSelector.LoadPipelineMetadataInPod(psc)
        psc.podSelector.CheckAndUpdateLookupVariables(psc)
        then:
        psc.podSelector.phaseConfigs[0].testArg == testArgValue
        where:
        phaseConfig                                                        | podTemplateConfig         | testArgValue
        [testArg: 'come and lookup:{ANOTHER}, lookup:{env:TEST_VARIABLE}'] | [envInMetadata: true]     | 'come and get me, here I am'
        [testArg: 'lookup:{env:TEST_VARIABLE}']                            | [envInMetadata: true]     | 'here I am'
        [testArg: 'lookup:{TEST_VARIABLE}']                                | [envInMetadata: 'truthy'] | 'here I am'
        [testArg: 'lookup:{env:TEST_VARIABLE}']                            | [envInMetadata: false]    | 'lookup:{env:TEST_VARIABLE}'
        [testArg: 'lookup:{env:TEST_VARIABLE}']                            | [:]                       | 'lookup:{env:TEST_VARIABLE}'
    }

    def '''If a jnlp override is specified at global level, the correct settings are applied to the pod templates'''() {
        given:
        def config = [
            cloudName: 'test-cloud',
            jnlp     : [
                memory: requiredMemory,
                cpu   : requiredCpu
            ],
        ]
        when:
        psc.podSelector.calculatePodTemplates(config, [])
        then:
        def expectedCpu = Utils.extractNumeric(requiredCpu ?: PodConfigGenerator.DEFAULT_JNLP_CPU)
        def expectedMemory = Utils.extractNumeric(requiredMemory ?: PodConfigGenerator.DEFAULT_JNLP_MEMORY)

        def containerDef =
            psc.podSelector.podGenerators['test-cloud'].podTemplate.spec.containers.find {
                it.name == 'jnlp'
            }
        containerDef.resources.limits.cpu == "${expectedCpu}m"
        containerDef.resources.limits.memory == "${expectedMemory}Mi"
        def jsonTemplate = psc.podSelector.podTemplates['test-cloud']
        jsonTemplate ==~ /.*"resources":\{"requests":\{"cpu":"${expectedCpu}m","memory":"${expectedMemory}Mi"},"limits":\{"cpu":"${expectedCpu}m","memory":"${expectedMemory}Mi"}}.*/
        where:
        requiredCpu << [1000, '1000', '1g', '1000m', null, 1000,]
        requiredMemory << [4000, '4000', '4g', '4000Mi', 4000, null,]
        containerName << ['jnlp', 'jnlp', 'jnlp', 'jnlp', 'jnlp', 'jnlp',]
    }

    def '''If a jnlp container is specified at global level, the correct settings are applied to the pod templates'''() {
        given:

        def config = [
            cloudName: 'test-cloud',
            container: [
                memory: requiredMemory,
                cpu   : requiredCpu
            ],
        ]
        when:
        psc.podSelector.calculatePodTemplates(config, [])
        then:
        def expectedCpu = Utils.extractNumeric(requiredCpu ?: PodConfigGenerator.DEFAULT_JNLP_CPU)
        def expectedMemory = Utils.extractNumeric(requiredMemory ?: PodConfigGenerator.DEFAULT_JNLP_MEMORY)

        def containerDef =
            psc.podSelector.podGenerators['test-cloud'].podTemplate.spec.containers.find {
                it.name == 'jnlp'
            }
        containerDef.resources.limits.cpu == "${expectedCpu}m"
        containerDef.resources.limits.memory == "${expectedMemory}Mi"
        def jsonTemplate = psc.podSelector.podTemplates['test-cloud']
        jsonTemplate ==~ /.*"resources":\{"requests":\{"cpu":"${expectedCpu}m","memory":"${expectedMemory}Mi"},"limits":\{"cpu":"${expectedCpu}m","memory":"${expectedMemory}Mi"}}.*/
        where:
        requiredCpu << [1000, '1000', '1g', '1000m', null, 1000,]
        requiredMemory << [4000, '4000', '4g', '4000Mi', 4000, null,]
        containerName << ['container', 'container', 'container', 'container', 'container', 'container',]
    }

    def '''If a jnlp container is specified at global level in containers, the correct settings are applied to the pod templates'''() {
        given:

        def config = [
            cloudName : 'test-cloud',
            containers: [
                [
                    name  : 'jnlp',
                    memory: requiredMemory,
                    cpu   : requiredCpu
                ]
            ],
        ]
        when:
        psc.podSelector.calculatePodTemplates(config, [])
        then:
        def expectedCpu = Utils.extractNumeric(requiredCpu ?: PodConfigGenerator.DEFAULT_JNLP_CPU)
        def expectedMemory = Utils.extractNumeric(requiredMemory ?: PodConfigGenerator.DEFAULT_JNLP_MEMORY)

        def containerDef =
            psc.podSelector.podGenerators['test-cloud'].podTemplate.spec.containers.find {
                it.name == 'jnlp'
            }
        containerDef.resources.limits.cpu == "${expectedCpu}m"
        containerDef.resources.limits.memory == "${expectedMemory}Mi"
        def jsonTemplate = psc.podSelector.podTemplates['test-cloud']
        jsonTemplate ==~ /.*"resources":\{"requests":\{"cpu":"${expectedCpu}m","memory":"${expectedMemory}Mi"},"limits":\{"cpu":"${expectedCpu}m","memory":"${expectedMemory}Mi"}}.*/
        where:
        requiredCpu << [1000, '1000', '1g', '1000m', null, 1000,]
        requiredMemory << [4000, '4000', '4g', '4000Mi', 4000, null,]
        containerName << ['container', 'container', 'container', 'container', 'container', 'container',]
    }

    // helper function to avoid scoping issues
    private def makeBuildPhase(script, psc) {
        new GradleBuild(config: [podGroup: 'gradle'], script: script, psc: psc)
    }

    def "repo settings can override defaults"() {
        given:

        def config = [
            repo: inputRepo
        ]
        when:
        psc.podSelector.calculatePodTemplates(config, phases)
        then:
        def actualConfig = psc.podSelector.checkoutConfiguration
        actualConfig.repo.shallow == expected.shallow
        actualConfig.repo.noTags == expected.noTags
        actualConfig.repo.lfs == expected.lfs
        where:
        inputRepo << [[:],
                      [shallow: false, noTags: false],
                      [shallow: true, noTags: true],
                      [shallow: false, noTags: false, lfs: false]]
        phases << [[],
                   [[phaseInstance: makeBuildPhase(script, psc), repo: [lfs: true]]],
                   [[phaseInstance: makeBuildPhase(script, psc), repo: [shallow: false]]],
                   [[phaseInstance: makeBuildPhase(script, psc), repo: [shallow: true]]]]
        expected << [[shallow: false, noTags: true],
                     [shallow: false, noTags: false, lfs: true],
                     // phase setting of false doesn't override any prior trues
                     [shallow: true, noTags: true],
                     // lfs can't be set at the top-level config
                     [shallow: true, noTags: false]]
    }

    def '''test bounds logic works for container recommendations with recommendations #recommendations'''() {
        when:
        def actualRecommendationsCPU = psc.podSelector.calculateWithBounds(recommendations, 'cpu')
        def actualRecommendationsMemory = psc.podSelector.calculateWithBounds(recommendations, 'memory')
        then:
        [cpu: actualRecommendationsCPU, memory: actualRecommendationsMemory] == expectedRecommendations
        where:
        recommendations << [
            [
                target    : [cpu: '100m', memory: '1000Mi'],
                lowerBound: [cpu: '40m', memory: '900Mi'],
                upperBound: [cpu: '200m', memory: '2000Mi'],
            ],
            [
                target    : [cpu: '2100m', memory: '11000Mi'],
                lowerBound: [cpu: '40m', memory: '900Mi'],
                upperBound: [cpu: '200m', memory: '2000Mi'],
            ],
            [
                target    : [cpu: '50m', memory: '10Mi'],
                lowerBound: [cpu: '140m', memory: '900Mi'],
                upperBound: [cpu: '200m', memory: '2000Mi'],
            ],
            [
                target    : [cpu: '50m', memory: '50000Mi'],
                lowerBound: [cpu: '140m', memory: '900Mi'],
                upperBound: [cpu: '200m', memory: '2000Mi'],
            ],
            [
                target    : [cpu: null, memory: null],
                lowerBound: [cpu: '140m', memory: '900Mi'],
                upperBound: [cpu: '200m', memory: '2000Mi'],
            ],
            [
                target    : [cpu: '50m', memory: '50000Mi'],
                lowerBound: [cpu: null, memory: '900Mi'],
                upperBound: null,
            ],
            [
                target    : [cpu: '50m', memory: '50000Mi'],
                lowerBound: null,
                upperBound: null,
            ],
        ]
        expectedRecommendations << [
            [cpu: '100m', memory: '1000Mi'],
            [cpu: '200m', memory: '2000Mi'],
            [cpu: '140m', memory: '900Mi'],
            [cpu: '140m', memory: '2000Mi'],
            [cpu: null, memory: null],
            [cpu: null, memory: null],
            [cpu: null, memory: null],
        ]
    }

    def '''verify that epf_pod_group is generated or suppressed according to configuration'''() {
        given:
        def phase = new ValidPhase(config: phaseConfig, script: script, psc: psc)
        phase.config.phaseInstance = phase

        when:
        psc.podSelector.calculatePodTemplates([:], [phase.config])
        then:
        psc.podSelector.podGenerators.values()[0].podTemplate.metadata.labels.epf_pod_group == resultingLabel
        where:
        phaseConfig                                      | resultingLabel
        [podGroup: 'test']                               | 'test.test.test'
        [podGroup: 'another-test', groupBy: 'nonunique'] | 'test.test.another-test'
        [groupBy: 'unique']                              | null
    }
}
