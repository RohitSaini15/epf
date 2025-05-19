package com.cigna.common.kubernetes


import com.cigna.jenkins_spock.JenkinsPipelineSpecification
import groovy.json.JsonOutput

class PodConfigGeneratorSpec extends JenkinsPipelineSpecification {

    PodConfigGenerator podConfigGenerator

    String serviceAccountToAdd
    Map<String, String> securityContextToAdd
    List<Map> validContainersToAdd
    List<Map> validVolumeToAdd = [
            [
                    name    : 'volumeMap',
                    emptyDir: [
                            medium: ' '
                    ],
            ]
    ]

    class Script {
        def env = [
                JOB_NAME: 'my/cool/job'
        ]
    }
    def script = new Script()

    def setup() {

        podConfigGenerator = new PodConfigGenerator(script: script)

        serviceAccountToAdd = 'test'

        securityContextToAdd = [
                runAsUser: 1001
        ]

        validContainersToAdd = [
                [
                        name      : 'sonar',
                        image     : 'cigna/sonar:3.3.0.1492',
                        tty       : true,
                        workingDir: '/home/jenkins/agent',
                        command   : com.cigna.common.utils.Utils.defaultSidecarCommand,
                        resources : [
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
                        name           : 'checkmarx',
                        image          : 'dev-sec-ops/checkmarx-toolshack:1.2.1',
                        imagePullPolicy: 'IfNotPresent',
                        tty            : true,
                        workingDir     : '/home/jenkins/agent',
                        command        : com.cigna.common.utils.Utils.defaultSidecarCommand,
                        resources      : [
                                requests: [
                                        cpu   : '125m',
                                        memory: '1Gi'
                                ],
                                limits  : [
                                        cpu   : '500m',
                                        memory: '1Gi'
                                ]
                        ],
                ]
        ]
    }


    def "When `addContainers()` is called, new container maps are appended to the Pod Template Spec"() {
        when:
        podConfigGenerator.addContainers(validContainersToAdd)

        then:
        podConfigGenerator.podTemplate.spec.containers.containsAll(validContainersToAdd)

    }

    def '''When 2 semantic versions are compared, it properly takes in to account major greater than minor greater than patch'''() {
        when:
        def result = PodConfigGenerator.getVersionValue(v1) > PodConfigGenerator.getVersionValue(v2)
        then:
        result == outcome
        where:
        v1 << ['2.0.1', '4.0.0', '0.9.1', '9.0.16', '1.0.3-dev-ov2']
        v2 << ['1.4.0', '0.9.200', '3.0.0', '9.1.15', '1.0.2-dev-ov2']
        outcome << [true, true, false, false, true]
    }

    def "When `addContainers()` is called and the container is missing its name, the method will fail"() {
        given:
        def podConfigGenerator = new PodConfigGenerator()
        def invalidContainerNoName = [
                [
                        image     : 'enterprise-devops/maven-alpine:3-jdk-8-alpine',
                        tty       : true,
                        workingDir: '/home/jenkins/agent',
                        command   : com.cigna.common.utils.Utils.defaultSidecarCommand,
                        resources : [
                                requests: [
                                        cpu   : '1000m',
                                        memory: '2Gi'
                                ],
                                limits  : [
                                        cpu   : '1000m',
                                        memory: '2Gi'
                                ]
                        ]
                ],
        ]
        when:
        podConfigGenerator.addContainers(validContainersToAdd)
                .addContainers(invalidContainerNoName)
        then:
        PodConfigGenerator.MissingPodTemplatePropertyException ex = thrown()
        ex.message == "container.name missing in container 0"

    }

    def "When `addContainers()` is called and the container is missing its image, the method will fail"() {
        given:
        def invalidContainerNoImage = [
                [
                        name      : "maven",
                        tty       : true,
                        workingDir: '/home/jenkins/agent',
                        command   : com.cigna.common.utils.Utils.defaultSidecarCommand,
                        resources : [
                                requests: [
                                        cpu   : '1000m',
                                        memory: '2Gi'
                                ],
                                limits  : [
                                        cpu   : '1000m',
                                        memory: '2Gi'
                                ]
                        ]
                ],
        ]
        when:
        podConfigGenerator.addContainers(validContainersToAdd)
                .addContainers(invalidContainerNoImage)
        then:
        PodConfigGenerator.MissingPodTemplatePropertyException ex = thrown()
        ex.message == "container.image missing in container maven (index: 0)"

    }

    def "When `addContainers()` is called and the container is missing tty, the method will fail"() {
        given:
        def invalidContainerNoTty = [
                [
                        name      : "maven",
                        image     : 'enterprise-devops/maven-alpine:3-jdk-8-alpine',
                        workingDir: '/home/jenkins/agent',
                        command   : com.cigna.common.utils.Utils.defaultSidecarCommand,
                        resources : [
                                requests: [
                                        cpu   : '1000m',
                                        memory: '2Gi'
                                ],
                                limits  : [
                                        cpu   : '1000m',
                                        memory: '2Gi'
                                ]
                        ]
                ],
        ]
        when:
        podConfigGenerator.addContainers(validContainersToAdd)
                .addContainers(invalidContainerNoTty)
        then:
        PodConfigGenerator.MissingPodTemplatePropertyException ex = thrown()
        ex.message == "container.tty missing in container maven (index: 0)"

    }

    def "When `addContainers()` is called and the container is missing its cpu request, the method will fail"() {
        given:
        def invalidContainerNoCpuRequest = [
                [
                        name      : "maven",
                        image     : 'enterprise-devops/maven-alpine:3-jdk-8-alpine',
                        tty       : true,
                        workingDir: '/home/jenkins/agent',
                        command   : com.cigna.common.utils.Utils.defaultSidecarCommand,
                        resources : [
                                requests: [
                                        memory: '2Gi'
                                ],
                                limits  : [
                                        cpu   : '1000m',
                                        memory: '2Gi'
                                ]
                        ]
                ],
        ]
        when:
        podConfigGenerator.addContainers(validContainersToAdd)
                .addContainers(invalidContainerNoCpuRequest)
        then:
        PodConfigGenerator.MissingPodTemplatePropertyException ex = thrown()
        ex.message == "container.resources.requests.cpu missing in container maven (index: 0)"

    }

    def "When `addContainers()` is called and the container is missing its cpu limit, the method will fail"() {
        given:
        def invalidContainerNoCpuLimit = [
                [
                        name      : "maven",
                        image     : 'enterprise-devops/maven-alpine:3-jdk-8-alpine',
                        tty       : true,
                        workingDir: '/home/jenkins/agent',
                        command   : com.cigna.common.utils.Utils.defaultSidecarCommand,
                        resources : [
                                requests: [
                                        cpu   : '1000m',
                                        memory: '2Gi'
                                ],
                                limits  : [
                                        memory: '2Gi'
                                ]
                        ]
                ],
        ]
        when:
        podConfigGenerator.addContainers(validContainersToAdd)
                .addContainers(invalidContainerNoCpuLimit)
        then:
        PodConfigGenerator.MissingPodTemplatePropertyException ex = thrown()
        ex.message == "container.resources.limits.cpu missing in container maven (index: 0)"

    }

    def "When `addContainers()` is called and the container is missing its memory request, the method will fail"() {
        given:
        def invalidContainerNoMemRequest = [
                [
                        name      : "maven",
                        image     : 'enterprise-devops/maven-alpine:3-jdk-8-alpine',
                        tty       : true,
                        workingDir: '/home/jenkins/agent',
                        command   : com.cigna.common.utils.Utils.defaultSidecarCommand,
                        resources : [
                                requests: [
                                        cpu: '1000m'
                                ],
                                limits  : [
                                        cpu   : '1000m',
                                        memory: '2Gi'
                                ]
                        ]
                ],
        ]
        when:
        podConfigGenerator.addContainers(validContainersToAdd)
                .addContainers(invalidContainerNoMemRequest)
        then:
        PodConfigGenerator.MissingPodTemplatePropertyException ex = thrown()
        ex.message == "container.resources.requests.memory missing in container maven (index: 0)"

    }

    def "When `addContainers()` is called and the container's workingDir is not /home/jenkins/agent, the method will fail"() {
        given:
        def invalidContainerWrongWorkingDir = [
                [
                        name      : "maven",
                        image     : "imageName",
                        tty       : true,
                        workingDir: '/not/jenkins',
                        command   : com.cigna.common.utils.Utils.defaultSidecarCommand,
                        resources : [
                                requests: [
                                        cpu   : '1000m',
                                        memory: '2Gi'
                                ],
                                limits  : [
                                        cpu   : '1000m',
                                        memory: '2Gi'
                                ]
                        ]
                ]
        ]

        when:
        podConfigGenerator.addContainers(validContainersToAdd)
                .addContainers(invalidContainerWrongWorkingDir)

        then:
        PodConfigGenerator.InvalidPodTemplatePropertyException ex = thrown()
        ex.message == "workingDir not set to '/home/jenkins/agent', this will break pathing within the script if changed."
    }

    def "When `addContainers()` is called and the container is missing its memory limit, the method will fail"() {
        given:
        def invalidContainerNoMemLimit = [
                [
                        name      : "maven",
                        image     : 'enterprise-devops/maven-alpine:3-jdk-8-alpine',
                        tty       : true,
                        workingDir: '/home/jenkins/agent',
                        command   : com.cigna.common.utils.Utils.defaultSidecarCommand,
                        resources : [
                                requests: [
                                        cpu   : '1000m',
                                        memory: '2Gi'
                                ],
                                limits  : [
                                        cpu: '1000m'
                                ]
                        ]
                ],
        ]

        when:
        podConfigGenerator.addContainers(validContainersToAdd)
                .addContainers(invalidContainerNoMemLimit)

        then:
        PodConfigGenerator.MissingPodTemplatePropertyException ex = thrown()
        ex.message == "container.resources.limits.memory missing in container maven (index: 0)"

    }

    def "When `addVolumes()` is called, new volume maps are appended to the volume list within the Pod Template"() {
        when:
        podConfigGenerator.addVolumes(validVolumeToAdd)
        then:
        podConfigGenerator.podTemplate.spec.volumes.containsAll(validVolumeToAdd)

    }

    def "When `toJson()` is called, `JsonOutput.toJson()` is called"() {
        given:
        GroovyMock(JsonOutput, global: true)
        JsonOutput.toJson(_) >> "output string"
        when:
        podConfigGenerator.toJson()

        then:
        1 * JsonOutput.toJson(_)
    }

    def "When `addServiceAccount` is called the provided string is input into the podTemplate"() {
        when:
        Map<String, Object> phaseConfig = [
                runInAWS: whereAWS
        ] + whereAwsConfig
        podConfigGenerator.addServiceAccount(phaseConfig, serviceAccountToAdd)
        then:
        assert podConfigGenerator.podTemplate.spec.serviceAccountName == whereServiceAccount
        where:
        whereAWS << [false, true]
        whereServiceAccount << ['test', 'aws-test']
        whereAwsConfig << [[:], [aws: [cloudServiceAccountName: 'aws-test']]]
    }

    def "When `addSecurityContext` is called the provided map is input into the podTemplate"() {
        when:
        def phaseConfig = [
                runInAWS: whereAWS
        ]
        if (whereGroup != null) {
            phaseConfig.awsGroup = whereGroup
        }
        podConfigGenerator.addSecurityContext(phaseConfig, securityContextToAdd)
        then:
        assert podConfigGenerator.podTemplate.spec.securityContext == whereSecurityContext
        where:
        whereAWS << [false, true, true]
        whereGroup << [null, null, 3210]
        whereSecurityContext << [
                [runAsUser: 1001],
                [runAsUser: 42531, runAsNonRoot: true],
                [runAsUser: 42531, runAsGroup: 3210, fsGroup: 3210, runAsNonRoot: true]
        ]
    }

    def """When `addMinioContainer` is called, minio container is added depending on phaseConfig provided"""() {
        when:
        def phaseConfig = [
                runInAWS: whereAWS
        ]
        podConfigGenerator.addMinioContainer(phaseConfig)
        then:
        def minioContainer = [
                name           : 'mc',
                image          : 'enterprise-devops/mc',
                imagePullPolicy: 'IfNotPresent',
                workingDir     : '/home/jenkins/agent',
                tty            : true,
                command        : com.cigna.common.utils.Utils.defaultSidecarCommand,
                env            : [
                        [
                                name : 'HOME',
                                value: '/tmp'
                        ],
                        [
                                name     : 'ACCESS_KEY',
                                valueFrom: [
                                        secretKeyRef: [
                                                name: 'mccreds-secret',
                                                key : 'access_key'
                                        ]
                                ]
                        ],
                        [
                                name     : 'SECRET_KEY',
                                valueFrom: [
                                        secretKeyRef: [
                                                name: 'mccreds-secret',
                                                key : 'secret_key'
                                        ]
                                ]
                        ],
                        [
                                name     : 'BUCKET_URL',
                                valueFrom: [
                                        configMapKeyRef: [
                                                name: 'mccreds-conf',
                                                key : 'mccreds-bucketurl'
                                        ]
                                ]
                        ],
                        [
                                name     : 'BUCKET_NAME',
                                valueFrom: [
                                        configMapKeyRef: [
                                                name: 'mccreds-conf',
                                                key : 'mccreds-bucketname'
                                        ]
                                ]
                        ],
                        [
                                name : 'MC_HOST_minio',
                                value: 'https://$(ACCESS_KEY):$(SECRET_KEY)@$(BUCKET_URL)'
                        ]
                ],
                resources      : [
                        requests: [
                                cpu   : '25m',
                                memory: '25Mi'
                        ],
                        limits  : [
                                cpu   : '1000m',
                                memory: '1000Mi'
                        ]
                ]
        ]
        if (!whereAWS) {
            assert podConfigGenerator.podTemplate.spec.containers.addAll(minioContainer)
        } else {
            assert podConfigGenerator.podTemplate.spec.containers.contains(minioContainer) == false

        }
        where:
        whereAWS << [true, false]
    }

    def "When a default container is overridden, the new template contains the merged values"() {
        when:
        List<Map> configs = [
                [
                        name     : 'jnlp',
                        env      : [
                                [
                                        name : 'JENKINS_AGENT_WORKDIR',
                                        value: "test-value"
                                ]
                        ],
                        resources: [
                                requests: [
                                        cpu   : '25m',
                                        memory: '50Mi'
                                ],
                                limits  : [
                                        cpu   : '25m',
                                        memory: '50Mi'
                                ]
                        ],
                ]
        ]

        Map podTemplate = podConfigGenerator.cloneAndMergeTemplate(configs)
        then:
        assert podTemplate.spec.containers == mergedContainer
        assert !podTemplate.is(podConfigGenerator.podTemplate)
        where:
        mergedContainer << [
                [
                        [
                                name        : 'jnlp',
                                image       : PodConfigGenerator.JNLP_CONTAINER_IMAGE,
                                imagePullPolicy: 'Always',
                                command     : [
                                        '/bin/sh',
                                        '-c',
                                        'umask 0000; jenkins-agent',
                                ],
                                env         : [
                                        [
                                                name : 'JENKINS_AGENT_WORKDIR',
                                                value: "test-value"
                                        ]
                                ],
                                tty         : true,
                                workingDir  : '/home/jenkins/agent',
                                resources   : [
                                        requests: [
                                                cpu   : '25m',
                                                memory: '50Mi'
                                        ],
                                        limits  : [
                                                cpu   : '25m',
                                                memory: '50Mi'
                                        ]
                                ],
                                volumeMounts: [
                                        [
                                                name     : 'sonar-workspace',
                                                mountPath: '/home/jenkins/agent/sonar'
                                        ],
                                        [
                                                name     : 'setup-sonar',
                                                mountPath: '/home/jenkins/agent/setup-sonar'
                                        ]
                                ]
                        ]
                ]
        ]
    }

    def """When `addContainers()` is called and assessResources is called the provided strategy
        updates resources accordingly"""() {
        when:
        List<Map> testContainers = [
                [
                        name     : 'test1',
                        image    : 'cnp/test1:latest',
                        tty      : true,
                        resources: [
                                requests: [
                                        cpu   : '100m',
                                        memory: '100Mi'
                                ],
                                limits  : [
                                        cpu   : '100m',
                                        memory: '100Mi'
                                ]
                        ]
                ],
                [
                        name     : 'test2',
                        image    : 'cnp/test1:latest',
                        tty      : true,
                        resources: [
                                requests: [
                                        cpu   : '100m',
                                        memory: '100Mi'
                                ],
                                limits  : [
                                        cpu   : '100m',
                                        memory: '100Mi'
                                ]
                        ]
                ]
        ]
        podConfigGenerator.addContainers(testContainers)
                .assessResources(givenStrategy)

        then:
        podConfigGenerator.podTemplate.spec.containers[1].resources.requests.cpu == '150m'
        podConfigGenerator.podTemplate.spec.containers[1].resources.requests.memory == '150Mi'
        podConfigGenerator.podTemplate.spec.containers[1].resources.limits.cpu == '150m'
        podConfigGenerator.podTemplate.spec.containers[1].resources.limits.memory == '150Mi'
        podConfigGenerator.podTemplate.spec.containers[2].resources.requests.cpu == '150m'
        podConfigGenerator.podTemplate.spec.containers[2].resources.requests.memory == '150Mi'
        podConfigGenerator.podTemplate.spec.containers[2].resources.limits.cpu == '150m'
        podConfigGenerator.podTemplate.spec.containers[2].resources.limits.memory == '150Mi'
        where:
        givenStrategy << ['High Performance']
    }

    def '''When an unsupported character (according to RFC 1123) is encountered in a container name, it is normalized to be valid'''() {
        when:
        def actualName = PodConfigGenerator.getContainerName(whereContainerName)
        then:
        assert actualName == whereExpectedName
        where:
        whereContainerName << ['edp/mwaa-local:2_2', 'edp/mwaa-local:2.2']
        whereExpectedName << ['mwaa-localv22', 'mwaa-localv22']
    }

    def '''ensure resourceTotals can calculate the correct values for cpu & memory'''() {
        when:
        def actualAnswer = podConfigGenerator.findIntegerPart(number)
        then:
        actualAnswer == expectedAnswer
        where:
        number << [
                '3000.0000Mi',
                '3000Mi',
                '5.0000m',
                '5m'
        ]
        expectedAnswer << [
                '3000',
                '3000',
                '5',
                '5',
        ]
    }
}
