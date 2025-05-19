package com.cigna.common.utils

import com.cigna.SinglePodTest
import com.cigna.builds.ScanOnlyBuild
import com.cigna.deployment.TerraformDeployment
import com.cigna.parallel.Parallel
import com.cigna.ticket.ServicenowTicket
import spock.lang.Shared

import static com.cigna.common.utils.Utils.*

class UtilsSpec extends SinglePodTest {

    def setup() {
        initScriptAndPsc()
    }

    def """When 2 nested maps are merged, the resulting map contains all the keys from both maps, \
                but any keys that are present in both will be taken from the second map"""() {
        when:
        def mapOne = [
            name : 'sonar',
            image: 'cigna/sonar:3.3.0.1492',
            env  : [
                [
                    name : 'SONAR_SCANNER_OPTS',
                    value: '-Xmx1536m'
                ],
            ]
        ]
        def mapTwo = [
            env: [
                [
                    name : 'SONAR_SCANNER_OPTS',
                    value: '-Xmx3G -Xms1024m'
                ]
            ]
        ]

        then:
        def resultMap = merge(mapOne, mapTwo)
        assert resultMap == [
            name : 'sonar',
            image: 'cigna/sonar:3.3.0.1492',
            env  : [
                [
                    name : 'SONAR_SCANNER_OPTS',
                    value: '-Xmx3G -Xms1024m'
                ],
            ]
        ]
    }

    def "Attempting to merge from an empty secondary map does no harm"() {
        when:
        def mapOne = [
            name : 'sonar',
            image: 'cigna/sonar:3.3.0.1492',
            env  : [
                [
                    name : 'SONAR_SCANNER_OPTS',
                    value: '-Xmx1536m'
                ]
            ]
        ]
        def mapTwo = [:]
        then:
        def resultMap = merge(mapOne, mapTwo)
        assert resultMap == mapOne
    }

    def "Merging two identical maps results in no change to the primary map"() {
        when:
        def mapOne = [
            name : 'sonar',
            image: 'cigna/sonar:3.3.0.1492',
            env  : [
                [
                    name : 'SONAR_SCANNER_OPTS',
                    value: '-Xmx1536m'
                ]
            ]
        ]
        def mapTwo = [
            name : 'sonar',
            image: 'cigna/sonar:3.3.0.1492',
            env  : [
                [
                    name : 'SONAR_SCANNER_OPTS',
                    value: '-Xmx1536m'
                ]
            ]
        ]
        then:
        def resultMap = merge(mapOne, mapTwo)
        assert resultMap == mapOne
    }

    def "That mergeByName() can properly process a full set of containers"() {
        when:
        def firstConfig = [
            [
                name: 'git'
            ],
            [
                name        : 'sonar',
                image       : 'cigna/sonar:3.3.0.1492',
                tty         : true,
                workingDir  : '/home/jenkins/agent',
                volumeMounts: [],
                env         : [
                    [
                        name : 'SONAR_SCANNER_OPTS',
                        value: '-Xmx1536m'
                    ],
                ],
                command     : com.cigna.common.utils.Utils.defaultSidecarCommand,
                resources   : [
                    requests: [
                        cpu   : '1',
                        memory: '2Gi'
                    ],
                    limits  : [
                        cpu   : '1',
                        memory: '2Gi'
                    ]
                ],
            ],
            [
                name: 'checkmarx'
            ]
        ]
        def secondConfig = [
            [
                name: 'sonar',

                env : [
                    [
                        name : 'SONAR_SCANNER_OPTS',
                        value: '-Xmx3G -XX:MaxPerm=1024m'
                    ]
                ]
            ],
            [
                name     : 'extra',
                resources: [
                    memory: '1Gi'
                ]
            ]
        ]
        def resultantConfig = [
            [
                name: 'git'
            ],
            [
                name        : 'sonar',
                image       : 'cigna/sonar:3.3.0.1492',
                tty         : true,
                workingDir  : '/home/jenkins/agent',
                volumeMounts: [],
                env         : [
                    [
                        name : 'SONAR_SCANNER_OPTS',
                        value: '-Xmx3G -XX:MaxPerm=1024m'
                    ],
                ],
                command     : com.cigna.common.utils.Utils.defaultSidecarCommand,
                resources   : [
                    requests: [
                        cpu   : '1',
                        memory: '2Gi'
                    ],
                    limits  : [
                        cpu   : '1',
                        memory: '2Gi'
                    ]
                ],
            ],
            [
                name: 'checkmarx'
            ],
            [
                name     : 'extra',
                resources: [
                    memory: '1Gi'
                ]
            ]
        ]
        then:
        def resultMap = mergeByName(firstConfig, secondConfig)
        assert resultMap == resultantConfig
    }

    def '''Verify that when multiple properties files are defined, they are injected in to the environment'''() {
        given:
        def config = [
            envProperties: [
                'test/resources/env1.properties',
                'test/resources/env2.properties'
            ]
        ]
        explicitlyMockPipelineStep('readProperties')
        when:
        Utils.checkAndImportProperties(script, config)
        then:
        1 * getPipelineMock('readProperties')([file: 'test/resources/env1.properties']) >> [prop1: 'prop1']
        1 * getPipelineMock('readProperties')([file: 'test/resources/env2.properties']) >> [prop2: 'prop2']
        script.env.prop1 == 'prop1'
        script.env.prop2 == 'prop2'
    }


    def '''when a non us-east-1 region is used for a dockerRegistry, the correct region is extracted from the url'''() {
        when:
        String url = dockerRegistry
        def actualOutcome = Utils.regionFromEcrUrl(url)
        then:
        expectedOutcome == actualOutcome
        where:
        dockerRegistry << ['1234567890.dkr.ecr.us-east-1.amazonaws.com', '1234567890.dkr.ecr.us-east-2.amazonaws.com']
        expectedOutcome << ['us-east-1', 'us-east-2']
    }

    def '''Calculate optimal memory takes account of Mi & Gi scaling'''() {
        when:
        def actualMemory = Utils.calculateOptimalScannerOptions(requestedMemory)
        then:
        actualMemory == optimalMemory
        where:
        requestedMemory << ['2000Mi', '3Gi', '10Gi', '1111m', '4g', '40000mi', '10000']
        optimalMemory << [
            '-Xmx1500m -XX:+UseContainerSupport -XX:MinRAMPercentage=50 -XX:MaxRAMPercentage=75 ' +
                '-XX:NativeMemoryTracking=summary',
            '-Xmx2250m -XX:+UseContainerSupport -XX:MinRAMPercentage=50 -XX:MaxRAMPercentage=75 ' +
                '-XX:NativeMemoryTracking=summary',
            '-Xmx7500m -XX:+UseContainerSupport -XX:MinRAMPercentage=50 -XX:MaxRAMPercentage=75 ' +
                '-XX:NativeMemoryTracking=summary',
            '-Xmx834m -XX:+UseContainerSupport -XX:MinRAMPercentage=50 -XX:MaxRAMPercentage=75 ' +
                '-XX:NativeMemoryTracking=summary',
            '-Xmx3000m -XX:+UseContainerSupport -XX:MinRAMPercentage=50 -XX:MaxRAMPercentage=75 ' +
                '-XX:NativeMemoryTracking=summary',
            '-Xmx30000m -XX:+UseContainerSupport -XX:MinRAMPercentage=50 -XX:MaxRAMPercentage=75 ' +
                '-XX:NativeMemoryTracking=summary',
            '-Xmx7500m -XX:+UseContainerSupport -XX:MinRAMPercentage=50 -XX:MaxRAMPercentage=75 ' +
                '-XX:NativeMemoryTracking=summary',
        ]
    }

    def "ifNull only returns the replacement if the first arg is null"() {
        when:
        def actual = ifNull(candidate, 'candidate was null')
        then:
        actual == expected
        where:
        candidate << [null, '', [], [:], false, 'something']
        expected << ['candidate was null', '', [], [:], false, 'something']
    }


    def '''test for execution plan'''() {
        given:
        def cloudName = 'cloud1'
        def plan = expectedPlans
        def groups = currentGroups
        def dynamicConfig = currentConfig
        def phase = createPhase(script, dynamicConfig, psc)

        dynamicConfig.phaseInstance = phase
        phase.init()
        phase.prePodConfig()
        def parallelConfig = [
            parallelType: 'jenkins',
            cloudName   : cloudName,
            phases      : [
                dynamicConfig
            ]
        ]
        def parallelPhase = new Parallel(script: script, config: parallelConfig, psc: psc)
        def phases = [
            parallelConfig
        ]
        when:
        simulatePodTemplate(psc, parallelPhase, cloudName)
        def result = explainExecutionPlan(psc, groups, phases)
        then:
        result == plan

        where:
        currentGroups << [
            ['cloud1'],
            ['cloud1', 'cloud2']
        ]
        createPhase << [
            { script, config, psc -> new ScanOnlyBuild(script: script, config: config, psc: psc) },
            { script, config, psc ->
                config.ticket.phaseInstance = new ServicenowTicket(script: script, config: config.ticket, psc: psc)
                new TerraformDeployment(script: script, config: config, psc: psc)
            },


        ]
        currentConfig << [
            [
                buildType          : 'scanOnly',
                branchPattern      : '.*',
                checkmarxEnabled   : false,
                sonarEnabled       : true,
                phaseLabel         : 'RunQualityCheckStep',
                sdlcEnvironment    : 'dev',
                resourceScaleFactor: 4,
                cloudName          : 'cloud1',
            ],
            [
                deploymentType        : 'terraform',
                isProductionDeployment: false,
                branchPattern         : '.*',
                sdlcEnvironment       : 'dev',
                terraform             : 't',
                terragrunt            : 't',
                aws                   : [
                    saml         : true,
                    credentialsId: 'svpOnboardServiceAccount',
                    account      : '535306282211',
                    rolename     : 'PIPELINETEST',
                    region       : 'us-east-1',
                ],
                directories           : [
                    [
                        directory: './module/aws/lambda',
                        extraArgs: [
                            init : '',
                            plan : '',
                            apply: ''
                        ],
                        tfVars   : [
                            env           : 'dev',
                            region        : 'us-east-1',
                            account_number: '535306282211',
                        ],
                        useAll   : true,
                    ]
                ],
                ticket                : [
                    cloudName                 : 'cloud2',
                    branchPattern             : '.*',
                    ticketType                : 'Servicenow',
                    changeEnvironment         : 'prod',
                    credentialsId             : 'SERVICE_NOW_TOKEN',
                    title                     : 'SCT0000142',
                    cmdb_ci                   : 'Enterprise Pipeline Framework',
                    requested_by              : 'me',
                    assigned_to               : 'me',
                    plannedDuration           : 60,
                    u_emergency_contact_person: 'me',
                    u_emergency_contact_number: 1234567890
                ]
            ],
        ]
        expectedPlans << [
            '''================================================== EXECUTION PLAN ==================================================
                |Processing pod groups in this order: [cloud1]
                |    PHASE GROUP 0: cloud1 # PHASES 1
                |      PHASE 0 [Parallel](cloud1):
                |        -> container jnlp, cpu: 500m, memory: 600Mi
                |        -> phases:
                |              PHASE 0 [Scan Only Build](cloud1):
                |                -> container jnlp, cpu: 500m, memory: 600Mi
                |                -> container maven-alpinev3-jdk-8-alpine-1, cpu: 1000m, memory: 2000Mi
                |                -> container sonarv481-v1, cpu: 1000m, memory: 2000Mi
                |===================================================================================================================='''.stripMargin('|'),
            '''================================================== EXECUTION PLAN ==================================================
                |Processing pod groups in this order: [cloud1, cloud2]
                |    PHASE GROUP 0: cloud1 # PHASES 1
                |      PHASE 0 [Parallel](cloud1):
                |        -> container jnlp, cpu: 500m, memory: 600Mi
                |        -> phases:
                |              PHASE 0 [Terraform Deployment](cloud1):
                |                -> container jnlp, cpu: 500m, memory: 600Mi
                |                -> container aws-d-cloudkitvplz-2, cpu: 500m, memory: 1000Mi
                |                -> container epf-curlvlatest, cpu: 50m, memory: 70Mi
                |                -> ticket:
                |                      PHASE 0 [Servicenow Ticket](cloud2):
                |                        -> container jnlp, cpu: 500m, memory: 600Mi
                |===================================================================================================================='''.stripMargin('|')

        ]
    }


    def '''wrapIfTruthy wraps its arg if the arg is truthy'''() {
        expect:
        wrapIfTruthy(wrapValue, *wrapArgs) == expected

        where:
        wrapArgs   | wrapValue | expected
        ['[', ']'] | 'abc'     | '[abc]'
        ['[', ']'] | ''        | ''
        ['[', ']'] | null      | ''
        ["'"]      | 'def'     | "'def'"
        ["'"]      | ''        | ''
        ["'"]      | null      | ''
        ['"']      | [1, 2]    | '"[1, 2]"'
    }

    def '''sQuote wraps truthy values in single quotes'''() {
        expect:
        sQuote(input) == expected

        where:
        input                  | expected
        'abc'                  | "'abc'"
        'a string with spaces' | "'a string with spaces'"
        ''                     | ''
        null                   | ''
        0                      | ''
        1                      | "'1'"
        []                     | ''
        [1]                    | "'[1]'"
    }

    def '''joinIfAllPresent joins lists where all elements are truthy in Iterable args, 
           and returns a String arg'''() {
        expect:
        joinIfAllPresent(*args) == expected

        where:
        args                              | expected
        // just strings
        [(String) null]                   | ''
        ['']                              | ''
        ['abc']                           | 'abc'
        // list of strings with default separator
        [[null]]                          | ''
        [['abc', 'def', 'ghi']]           | 'abc def ghi'
        [['', 'abc']]                     | ''
        [[null, 'abc']]                   | ''
        [['abc', '']]                     | ''
        [['abc', null]]                   | ''
        [['', 'abc', null]]               | ''
        // list of strings with different separators
        [['', 'abc', null], '/']          | ''
        [['some', 'folder', 'path'], '/'] | 'some/folder/path'
    }

    def '''buildCommandArgs joins truthy results of joinIfAllPresent'''() {
        expect:
        buildCommandArgs(*input) == expected

        where:
        input                                           | expected
        null                                            | ''
        []                                              | ''
        ['']                                            | ''
        ['mvn', 'build']                                | 'mvn build'
        ['mvn', null]                                   | 'mvn'
        ['mvn', '']                                     | 'mvn'
        ['mvn', '', 'build']                            | 'mvn build'
        ['mvn', ['--arg', '']]                          | 'mvn'
        ['mvn', ['--arg', 'someValue']]                 | 'mvn --arg someValue'
        ['mvn', ['--arg', sQuote(null)]]                | 'mvn'
        ['mvn', ['--arg', sQuote('')]]                  | 'mvn'
        ['mvn', ['--arg', sQuote('value with spaces')]] | "mvn --arg 'value with spaces'"
        ['mvn', ['', 'someValue']]                      | 'mvn'
        [['', 'hi'], 'and then bye']                    | 'and then bye'

    }

    @Shared
    def longName = 'epf-with-a-very-very-long-name-that-needs-to-be-truncated-but-not-lose-the-group-name'

    def '''calculating pod autotuning name excludes specific base folder names'''() {
        given:
        when:
        def actualName = calculateAutotuningGroupName(jobNames, podGroupName)
        then:
        actualName == expectedName
        where:
        expectedName << [
            'epf.pod-group-1', 'epf.pod-group-1', 'ba-123456.epf.pod-group-1',
            'ba-123456.epf-with-a-very-very-long-name-that-needs.pod-group-1',
            'ba-123456.epf-with-a-ver.a-longer-pod-group-1-name-for-eks-pods',
            'snowflake-account.snowflake-acct-infra-eks-jenkins-cluster-prod',
            'snowflake-acct-infra-eks-jenkins-cluster-prod-with-xmore-chars',
            'lake-acct-infra-eks-jenkins-cluster-prod-with-toomanymore-chars',
            'epf.pod-group-1-upper'
        ]
        jobNames << [
            'orchestrators-folders/epf/epf/PR-296', 'pilot-folders/epf/epf/PR-296', 'ba-123456/epf/epf/PR-296',
            "ba-123456/$longName/$longName/PR-296", "ba-123456/$longName/$longName/PR-296",
            'orchestrators-folders/snowflake-acct-infra/snowflake-account-infra/develop',
            'orchestrators-folders/snowflake-acct-infra/snowflake-account-infra/develop',
            'orchestrators-folders/snowflake-acct-infra/snowflake-account-infra/develop',
            'orchestrators-folders/epf/epf/PR-296'
        ]
        podGroupName << [
            'pod-group-1', 'pod-group-1', 'pod-group-1', 'pod-group-1', 'a-longer-pod-group-1-name-for-eks-pods',
            'snowflake-acct-infra-eks-jenkins-cluster-prod',
            'snowflake-acct-infra-eks-jenkins-cluster-prod-with-xmore-chars',
            'snowflake-acct-infra-eks-jenkins-cluster-prod-with-toomanymore-chars', 'POD-GROUP-1-UPPER'
        ]
    }

    def "removeAnsiColorCodes should remove ANSI color codes from text"() {
        given:
        def textWithAnsiCodes = "\u001B[31mThis is red text\u001B[0m and this is normal text."

        when:
        def cleanedText = removeAnsiColorCodes(textWithAnsiCodes)

        then:
        cleanedText == "This is red text and this is normal text."
    }
}
