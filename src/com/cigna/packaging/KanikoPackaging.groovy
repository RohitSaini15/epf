package com.cigna.packaging

import com.cigna.common.kubernetes.PodConfigGenerator
import com.cigna.common.utils.AWSUtils
import com.cigna.common.utils.FeatureFlags
import com.cigna.common.utils.Utils
import com.cigna.common.kubernetes.PodTemplateCreator
import hudson.Functions

import static com.cigna.common.utils.Utils.calculateContainerName
import static com.cigna.common.utils.Utils.mergeByName

/**
 * Defines steps specific to building docker images using kaniko.
 */
class KanikoPackaging extends GenericContainerPackaging {
    protected static final String KANIKO_IMAGE = 'enterprise-devops/epf-kaniko:v1.16.0-debug-51325'
    static String DEPRECATION_MSG = 'WARNING: Kaniko packaging is deprecated, podman packaging recommended. For additional information https://confluence.sys.cigna.com/x/0yesUQ'

    private final String conftestContainerName = 'jenkins-generic-agentv0036'

    protected static final String LOGICAL_SAML2AWS_CONTAINER_NAME = 'saml2aws'
    public static final String SAML2AWS_CONTAINER_IMAGE = 'enterprise-devops/aws-d-cloudkit'
    public static final String SAML2AWS_CONTAINER_VERSION = 'plz-2-docker'

    String saml2awsContainerName = calculateContainerName(SAML2AWS_CONTAINER_IMAGE, SAML2AWS_CONTAINER_VERSION)
    String saml2awsImage = "${SAML2AWS_CONTAINER_IMAGE}:${SAML2AWS_CONTAINER_VERSION}"

    KanikoPackaging() {
        containerName = 'epf-kanikovv1160-debug-51325'
        containerCpu = '700m'
        containerMemory = '2000Mi'

        containerVersion = 'v1.16.0-debug-51325'
        containerImage = 'enterprise-devops/epf-kaniko'
    }

    Boolean cacheEnabled = false
    Boolean cacheDisabledViaFlag = false
    protected String cacheRepo = ''
    protected String cacheDir = ''
    protected Boolean cacheCopyLayers = false
    protected String cacheTTL = '24h'
    protected String conftestArticlesLoc = 'git::https://git.sys.cigna.com/cloud-sec-eng/conftest.git//rules?ref='

    def conftestContainer = [
        name     : conftestContainerName,
        image    : 'enterprise-devops/jenkins-generic-agent:0.0.36',
        tty      : true,
        command  : com.cigna.common.utils.Utils.defaultSidecarCommand,
        resources: [
            requests: [
                cpu   : '100m',
                memory: '200Mi',
            ],
            limits  : [
                cpu   : '500m',
                memory: '200Mi'
            ]
        ]
    ]

    String conftestVersion = 'v0.36'

    @Override
    Boolean prePodConfig() {
        conftestVersion = config?.conftestVersion ?: 'v0.36'

        configureKanikoCache()

        List<Map> env = AWSUtils.awsEnvironment()
        PodTemplateCreator containerTemplate = PodTemplateCreator.newPodTemplateCreator()
        containerTemplate.addContainer(
            saml2awsContainerName,
            saml2awsImage,
            'Always',
            true,
            '/home/jenkins/agent',
            100,
            1000,
            500,
            2000,
            [
                '/usr/local/bin/adhoc-perms'
            ],
            env
        )
        containerTemplate.addVolumeMount(saml2awsContainerName, 'aws-creds', '/home/jenkins/.aws')
        containerTemplate.addVolumeMount(saml2awsContainerName, 'ecr-creds', '/home/jenkins/.ecr')

        def awsInnerContainer = containerTemplate.getContainer(saml2awsContainerName)
        awsInnerContainer.put('args',com.cigna.common.utils.Utils.defaultSidecarCommand)
        awsInnerContainer.put('logicalName',LOGICAL_SAML2AWS_CONTAINER_NAME)
        awsInnerContainer.put('origname',saml2awsContainerName)

        def awsContainer = [awsInnerContainer]

        containerTemplate.addContainer(
            containerName,
            KANIKO_IMAGE.toString(),
            100,
            1000,
            500,
            2000,
            env
        )
        containerTemplate.addVolumeMount(containerName, 'aws-creds', '/home/jenkins/.aws')
        containerTemplate.addVolumeMount(containerName, 'ecr-creds', '/home/jenkins/.ecr')

        env = [
            [
                name : 'HOME',
                value: '/tmp'
            ]
        ]

        containerTemplate.addContainer(
            QUAY_CONTAINER,
            'cpe/quay-cli:v1.9.1',
            100,
            125,
            500,
            250,
            env
        )

        additionalPodConfig = [
            volumes   : AWSUtils.awsVolumes(),
            containers: [containerTemplate.getContainer(containerName),
                            containerTemplate.getContainer(QUAY_CONTAINER)]
        ]

        def isAWS = config.runInAWS || config.dockerRegistry ==~ /.*\.ecr.*\.amazonaws.com/
        if (isAWS) {
            script.echo("Adding AWS container ${awsContainer[0].name} to containers for kaniko")
            additionalPodConfig.containers = mergeByName(additionalPodConfig.containers, awsContainer)
        } else {
            script.echo("Not Adding AWS container for kaniko: runInAWS=${config.runInAWS}, ecr registry? ${config.dockerRegistry ==~ /.*\.ecr.*\.amazonaws.com/}")
        }
        if (config?.conftestValidation) {
            additionalPodConfig.containers += conftestContainer
        }

        super.prePodConfig()
    }

    @Override
    Boolean postPodConfig() {
        def awsContainer = additionalPodConfig.containers.find { it.logicalName == LOGICAL_SAML2AWS_CONTAINER_NAME }

        if (awsContainer) {
            saml2awsImage = awsContainer.image
            saml2awsContainerName = PodConfigGenerator.getContainerName(saml2awsImage)
        }

        // if container is not found, it is not added, therefore no overrides are needed
        if (additionalPodConfig.containers.find { it.logicalName == LOGICAL_SAML2AWS_CONTAINER_NAME }) {
            additionalPodConfig.containers.remove('logicalName')
        }

        if (additionalPodConfig.containers.find { it.origname == saml2awsContainerName }) {
            additionalPodConfig.containers.remove('origname')
            awsContainer.remove('origname')
            awsContainer.remove('logicalName')
        }

        super.postPodConfig()
    }

    private void configureKanikoCache() {
        if (config.containsKey('cache')) {
            cacheEnabled = true
            if (config.cache instanceof Map) {
                cacheRepo = config.cache.get('repo', '')
                cacheDir = config.cache.get('dir', '')
                cacheCopyLayers = config.cache.get('copyLayers', false)
                cacheTTL = config.cache.get('ttl', cacheTTL)
            } else {
                cacheDisabledViaFlag = !config.get('cache', true)
            }
        }
    }

    protected void configureKanikoAuth(String registryUrl = 'registry-dev.cigna.com') {
        if (config?.quay) {
            script.withCredentials([script.string(
                credentialsId: "${config.quay.credentialsId}",
                variable: 'QUAY_TOKEN'
            )]) {
                script.sh(
                    """#!/busybox/sh -ex
                    |echo "{\
                        \\"auths\\":{\
                            \\"https://${registryUrl}/v2\\":{\
                                \\"username\\":\\"\\\$oauthtoken\\",\
                                \\"password\\":\\"${script.QUAY_TOKEN}\\"\
                            }\
                        }\
                    }" > /kaniko/.docker/config.json
                    """.stripMargin().replaceAll('\\s\\s', '')
                )
            }
        } else if (registryUrl ==~ /.*\.ecr.*\.amazonaws.com/) {
            String ecrAccount = registryUrl.find(/^\d*/)
            String domain = config?.ecr?.domain ?: 'INTERNAL'
            def region = Utils.regionFromEcrUrl(registryUrl)
            def awsConfig = [
                credentialsId: config?.ecr?.credentialsId,
                account      : ecrAccount,
                rolename     : config?.ecr?.rolename,
                region       : region,
                domain       : domain.toUpperCase(),
            ]

            boolean saml = config?.ecr?.saml ?: true
            // XXX.cnm - Since this code flow only supports saml2aws, avoid the awsLogin() refactoring
            if (saml) {
                saml2Aws(awsConfig, saml2awsContainerName)

                script.echo('Adding ECR secret to Kaniko credentials store:')
                script.sh('mv /home/jenkins/.ecr/credentials /kaniko/.docker/config.json')
            } else {
                throw new UnsupportedOperationException(
                    'awsFed is no longer supported for Kaniko packaging. Please use saml2aws. '
                        + 'For more information visit: https://confluence.sys.cigna.com/display/CLOUD/Service+Accounts+-+USM#ServiceAccountsUSM-ExistingServiceAccount')

            }
        }
    }

    @Override
    protected void containerBuildAndPushImage() {
        try {
            if (config?.secretFiles) {
                Utils.retrieveJenkinsSecretfiles(script, config)
            }
            if (config?.conftestValidation) {
                psc.podSelector.select(psc, conftestContainerName, Utils.cloud(config)) {
                    script.sh(
                        "conftest test --update \"${conftestArticlesLoc}${conftestVersion}\" "
                            + "${config?.dockerfile ?: 'Dockerfile'}"
                    )
                }
            }
            String destinationString = '--destination ' + imageUrls.join(' --destination ')
            configureKanikoAuth("${config?.dockerRegistry}")
            script.sh(
                """\
                /kaniko/executor --context ${script.env.WORKSPACE}/${config?.buildContextPath ?: ''} \
                --ignore-path /busybox \
                --ignore-path=/usr/bin/newgidmap \
                --ignore-path=/usr/bin/newuidmap \
                --dockerfile ${script.env.WORKSPACE}/${config?.dockerfile ?: 'Dockerfile'} \
                ${destinationString}${constructCacheArguments()} ${constructBuildArgsString()}${config?.image?.extraParams ?: ''} \
                """.replaceAll(/\s\s/, '')
            )
            notification.notifyWithAllMethods("${custommsg}")
        }
        catch (all) {
            notification.notifyWithAllMethods("${custommsg}")
            script.error("Error '${all.message}' building image: ${imageUrls[0]}")
            if (FeatureFlags.showStackTraces) {
                script.echo(Functions.printThrowable(all))
            }
        }
    }

    String constructCacheArguments() {
        script.echo("Kaniko Cache Configuration: cache Flags Provided: $cacheEnabled caching explicitly disabled: $cacheDisabledViaFlag")
        if (cacheEnabled && !cacheDisabledViaFlag) {
            " --cache=true --cache-copy-layers=${cacheCopyLayers}\
${cacheRepo == '' ? '' : " --cache-repo=${cacheRepo}"}${cacheDir == '' ? '' : " --cache-dir=${cacheDir}"} \
--cache-ttl=${cacheTTL}"
        } else {
            cacheDisabledViaFlag ? ' --cache=false ' : ''
        }
    }
}
