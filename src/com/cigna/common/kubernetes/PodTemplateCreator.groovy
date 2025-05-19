package com.cigna.common.kubernetes

import com.cloudbees.groovy.cps.NonCPS
import com.evernorth.cloudnativebuild.model.BaseDefaults

/**
 * Fluently generate Pod Template JSON to be passed to
 * the Kubernetes Plugin
 */

class PodTemplateCreator {
    private Map<String, Object> baseTemplate = [:]
    private Map volumes = [:]

    PodTemplateCreator() {
        this([:])
    }

    PodTemplateCreator(Map commonParams) {
        boolean tty = commonParams.get('tty', true)
        String workingDir = commonParams.get('workingDir', '/home/jenkins/agent')
        String containerName = commonParams.get('containerName', 'jnlp')
        String containerImage = commonParams.get('containerImage', BaseDefaults.podJnlpImage)
        String containerImagePullPolicy = commonParams.get('containerImagePullPolicy', 'Always')
        String reqCpu = commonParams.get('reqCpu', 500)
        String reqMemory = commonParams.get('reqMemory', 600)
        String limitCpu = commonParams.get('reqCpu', 500)
        String limitMemory = commonParams.get('reqMemory', 600)
        List<String> command = commonParams.get('command', ['/bin/sh', '-c', 'umask 0000; jenkins-agent'])
        List<Map> env = commonParams.get(
            'env', [[name : 'JENKINS_AGENT_WORKDIR',
                     value: "/home/jenkins/agent -internalDir remoting-${UUID.randomUUID().toString()[0..5]}"
                    ]
        ]
        )

        baseTemplate = [
            apiVersion: 'v1',
            kind      : 'Pod',
            metadata  : [
                name       : 'epf-pod',
                labels     : [
                    kind         : 'jenkins',
                    source       : 'epf',
                    epf_pod_group: ''
                ],
                annotations: [:]
            ],
            spec      : [
                securityContext   : [:],
                serviceAccountName: '',
                nodeSelector      : [
                    'beta.kubernetes.io/os': 'linux',
                ],
                volumes           : [
                    [
                        name    : 'sonar-workspace',
                        emptyDir: [
                            medium: ''
                        ],
                    ],
                    [
                        name    : 'setup-sonar',
                        emptyDir: [
                            medium: ''
                        ]
                    ]
                ],
                containers        : [
                    [
                        name           : containerName,
                        image          : containerImage,
                        imagePullPolicy: containerImagePullPolicy,
                        command        : command,
                        env            : env,
                        tty            : tty,
                        workingDir     : workingDir,
                        resources      : [
                            requests: [
                                cpu   : "${reqCpu}m",
                                memory: "${reqMemory}Mi"
                            ],
                            limits  : [
                                cpu   : "${limitCpu}m",
                                memory: "${limitMemory}Mi"
                            ]
                        ],
                        volumeMounts   : [
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
        ]
    }

    @NonCPS
    def addContainers(List<Map> containers) {
        baseTemplate.spec.containers = containers
    }

    @NonCPS
    def addContainer(Map container) {
        baseTemplate.spec.containers += container
    }

    @NonCPS
    def addContainer(String containerName,
                     String containerImage,
                     String containerImagePullPolicy = 'Always',
                     boolean tty = true,
                     String workingDir = '/home/jenkins/agent',
                     Integer reqCpu,
                     Integer reqMemory,
                     Integer limitCpu,
                     Integer limitMemory,
                     List<String> command = com.cigna.common.utils.Utils.defaultSidecarCommand,
                     List<Map> env) {
        Map container = [
            name           : containerName,
            image          : containerImage,
            imagePullPolicy: containerImagePullPolicy,
            command        : command,
            env            : env,
            tty            : tty,
            workingDir     : workingDir,
            resources      : [
                requests: [
                    cpu   : "${reqCpu}m",
                    memory: "${reqMemory}Mi"
                ],
                limits  : [
                    cpu   : "${limitCpu}m",
                    memory: "${limitMemory}Mi"
                ]
            ]
        ]
        baseTemplate.spec.containers += container
    }

    @NonCPS
    def addEnVars(String containerName, List<Map> enVars) {
        baseTemplate.spec.containers.each { Map it ->
            if (it.name == containerName) {
                it.env += enVars
            }
        }
    }

    @NonCPS
    def addEnVars(List<Map> enVars) {
        baseTemplate.spec.containers.each { Map it -> it.env += enVars }
    }

    @NonCPS
    def addVolume(String volumeName) {
        Map volume = [
            name    : volumeName,
            emptyDir: [
                medium: ''
            ]
        ]
        volumes << volume
        baseTemplate.spec.volumes << volumes
    }

    @NonCPS
    def addVolume(Map volume) {
        volumes << volume
        baseTemplate.spec.volumes << volumes
    }

    @NonCPS
    def addVolumeMount(containerName, String volumeMountName, volumeMountPath) {
        def volumeMount = [
            name     : volumeMountName,
            mountPath: volumeMountPath
        ]
        Map container = baseTemplate.spec.containers.find { it.name == containerName }
        if (container?.volumeMounts) {
            container.volumeMounts << volumeMount
        } else {
            container.put("volumeMounts", [volumeMount])
        }
    }

    @NonCPS
    def addResource(String containerName, Integer reqCpu, Integer reqMem, Integer limitCpu, Integer limitMem) {
        def resource = [
            requests: [
                cpu   : "${reqCpu}m",
                memory: "${reqMem}Mi"
            ],
            limits  : [
                cpu   : "${limitCpu}m",
                memory: "${limitMem}Mi"
            ]
        ]
        baseTemplate.spec.containers.find { it.name == containerName }.resources += resource
    }

    @NonCPS
    def getContainer(containerName) {
        baseTemplate.spec.containers.find { it.name == containerName }
    }

    @NonCPS
    Map<String, Object> getTemplate() {
        baseTemplate
    }

    @NonCPS
    String toString() {
        baseTemplate.toString()
    }

    @NonCPS
    static newPodTemplateCreator() {
        new PodTemplateCreator()
    }

    @NonCPS
    static newPodTemplateCreator(Map commonParams) {
        new PodTemplateCreator(commonParams)
    }

}