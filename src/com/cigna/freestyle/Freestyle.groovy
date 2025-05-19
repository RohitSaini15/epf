package com.cigna.freestyle

import com.cigna.base.Phase
import com.cigna.common.kubernetes.PodConfigGenerator
import com.cigna.common.utils.Utils
import com.evernorth.cloudnativebuild.data.DockerUri
import com.cigna.common.kubernetes.PodTemplateCreator

/**
 * Freestyle phase implementation that allows for running of ad-hoc commands.
 */
class Freestyle extends Phase {
    Freestyle() {
        baseValidationItems = ['container.image', 'script', 'freestyleType']
        containerCpu = '500m'
        containerMemory = '500Mi'
        groupID = 'freestyle'
        awsAllowedPhase = true
    }

    @Override
    Boolean prePodConfig() {
        if (config.containsKey('container') && !config.container.containsKey('version')) {
            Map details = DockerUri.imageAndTag(config.container.image)
            containerImage = details['image']
            containerVersion = details['tag']
        } else {
            containerImage = config.container.image
            containerVersion = config.container.version
        }
        containerName = PodConfigGenerator.getContainerName("$containerImage:$containerVersion")

        PodTemplateCreator containerTemplate = PodTemplateCreator.newPodTemplateCreator()
        containerTemplate.addContainer(
            'aws-d-cloudkitvplz-2',
            "${awscontainerImage}:${awscontainerVersion}".toString(),
            'Always',
            true,
            '/home/jenkins/agent',
            100,
            1000,
            500,
            1000,
            [
                '/usr/local/bin/adhoc-perms'
            ],
            awsEnv
        )

        def awsContainer = containerTemplate.getContainer('aws-d-cloudkitvplz-2')
        awsContainer.put('args',com.cigna.common.utils.Utils.defaultSidecarCommand)

        if (!containerImage?.contains('jnlp')) { // Do not add extra container if freestyle runs on jnlp
            List<Map> env = []
            containerTemplate.addContainer(
                containerName,
                "${containerImage}:${containerVersion}",
                100,
                1000,
                500,
                1000,
                env
            )

            additionalPodConfig = [
                'volumes'   : [],
                'containers': [containerTemplate.getContainer(containerName)]
            ]
        }

        def isAWS = config?.runInAWS || config?.dockerRegistry ==~ /.*\.ecr.*\.amazonaws.com/
        if (isAWS) {
            additionalPodConfig.containers += awsContainer
        }
        super.prePodConfig()
    }

    @Override
    def displayName(def prefix = '') {
        "${prefix}${config.freestyleType}"
    }

    String awscontainerImage = 'enterprise-devops/aws-d-cloudkit'
    String awscontainerVersion = 'plz-2'

    List<Map<String, String>> awsEnv = [
        [
            name : 'AWS_PROFILE',
            value: 'saml'
        ],
        [
            name : 'AWS_SHARED_CREDENTIALS_FILE',
            value: '/home/jenkins/.aws/credentials'
        ],
        [
            name : 'TF_IN_AUTOMATION',
            value: 'true'
        ],
        [
            name : 'GO111MODULE',
            value: 'auto'
        ],
        [
            name : 'GOPROXY',
            value: 'https://cigna.jfrog.io/artifactory/api/go/go-repos'
        ],
        [
            name : 'GOPRIVATE',
            value: 'git.sys.cigna.com, github.sys.cigna.com'
        ],
    ]

    /**
     * If readProperties cannot find a .properties file, an empty map is returned
     * Jenkins generates a warning: .properties files does not exist omitting from properties gathering
     */
    void addMetadataFromPropertiesFile() {
        String propertiesFile = config?.outputPropsFile
        if (propertiesFile) {
            def readProps = script.readProperties(file: "${propertiesFile}.properties")
            readProps.each { readProp ->
                psc.metadata.put(readProp.key, readProp.value, propertiesFile, false)
            }
        }
    }

    /**
     * Run the commands given in the container specified
     */
    void run(
        List credsList = [],
        List configsList = []
    ) {
        String custommsg = config?.customMessage ?: ''
        script.stage(generateStageName(config.freestyleType)) {
            psc.podSelector.select(psc, podTemplateContainerName, Utils.cloud(config)) {
                withPhaseConfigEnv() {
                    script.withCredentials(credsList) {
                        script.configFileProvider(configsList) {
                            moveFiles('begin')
                            script.dir(baseDirectory) {
                                try {
                                    awsLogin()
                                    script.sh(config.script)
                                    addMetadataFromPropertiesFile()
                                } finally {
                                    notification.notifyWithAllMethods(custommsg)
                                }
                            }
                            moveFiles('end')
                        }
                    }
                }
            }
        }
    }
}
