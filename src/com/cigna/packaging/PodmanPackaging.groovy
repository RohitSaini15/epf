package com.cigna.packaging

import com.cigna.common.utils.AWSUtils
import com.cigna.common.utils.FeatureFlags
import com.cigna.common.utils.Utils
import com.cigna.common.kubernetes.PodTemplateCreator
import com.evernorth.cloudnativebuild.model.BaseDefaults
import hudson.Functions

import static com.cigna.common.utils.Utils.mergeByName
import static com.cigna.common.utils.Utils.calculateContainerName

/**
 * Defines container packaging. Assumes that a build of some sort has already been completed. We'll have stash stuff at
 * the end of the build and unstash it here.
 */
class PodmanPackaging extends GenericContainerPackaging {
    static final String awsContainerImage = 'enterprise-devops/pipeline-podman-aws'
    static final String awsContainerVersion = '4.9.4'
    List<String> podmanContainerParts = BaseDefaults.defaultDockerBuilderImage.split(':')

    public String awsContainerName = calculateContainerName(awsContainerImage, awsContainerVersion)

    PodmanPackaging() {
        containerImage = "${podmanContainerParts[0]}"
        containerVersion = podmanContainerParts[1]
        containerName = calculateContainerName(containerImage, containerVersion)
    }

    @Override
    Boolean prePodConfig() {
        List<Map> env = AWSUtils.awsEnvironment()

        PodTemplateCreator containerTemplate = PodTemplateCreator.newPodTemplateCreator()
        containerTemplate.addContainer(
            awsContainerName,
            "${awsContainerImage}:${awsContainerVersion}",
            10,
            60,
            100,
            500,
            env
        )
        containerTemplate.addVolumeMount(awsContainerName, 'aws-creds', '/home/jenkins/.aws')
        containerTemplate.addVolumeMount(awsContainerName, 'ecr-creds', '/home/jenkins/.ecr')

        def awsContainer = [containerTemplate.getContainer(awsContainerName)]


        containerTemplate.addContainer(
            containerName,
            "${containerImage}:${containerVersion}",
            100,
            3000,
            1500,
            3000,
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
            'volumes'   : AWSUtils.awsVolumes(),
            'containers': [containerTemplate.getContainer(containerName),
                            containerTemplate.getContainer(QUAY_CONTAINER)]
        ]

        def isAWS = config.runInAWS || config.dockerRegistry ==~ /.*\.ecr.*\.amazonaws.com/
        if (isAWS) {
            script.echo("Adding AWS container ${awsContainer[0].name} to containers for podman")
            additionalPodConfig.containers = mergeByName(additionalPodConfig.containers, awsContainer)
        } else {
            script.echo("Not Adding AWS container for podman: runInAWS=${config.runInAWS}, ecr registry? ${config.dockerRegistry ==~ /.*\.ecr.*\.amazonaws.com/}")
        }

        super.prePodConfig()
    }

    @Override
    protected void containerBuildAndPushImage() {
        try {
            if (config?.secretFiles) {
                // retrieve secret files for usage in image builds
                Utils.retrieveJenkinsSecretfiles(script, config)
            }

            String argString = constructBuildArgsString()

            // Obtain path for dockerfile, assuming default relative to the workspace if not provided
            String dockerfileArg = "-f ${script.env.WORKSPACE}/${config?.dockerfile ?: 'Dockerfile'}"

            // Obtain path for context, using env.WORKSPACE as default if not provided
            String contextArg = "${script.env.WORKSPACE}/${config?.buildContextPath ?: ''}"

            def tagArgs = imageTagMapList.collect { "--tag ${dockerRegistry}/${imageName}:${it.tag}" }.join(' ')
            script.echo("Building and pushing container image with build args: ${argString}")
            if (config?.quay) {
                script.withCredentials(
                    [
                        script.string(
                            credentialsId: "${config.quay.credentialsId}",
                            variable: 'quayToken'
                        )
                    ]
                ) {
                    script.sh(
                        """podman build ${dockerfileArg} $tagArgs \
                 ${argString} ${contextArg}""".replaceAll(/\s+/, ' ')
                    )
                    imageTagMapList.each {
                        script.sh(
                            """podman push \
                --creds \\\$oauthtoken:${script.quayToken} \
                ${dockerRegistry}/${imageName}:${it.tag}""".replaceAll(/\s+/, ' ')
                        )
                    }
                }
            } else if (config?.ecr) {
                String ecrAccount = dockerRegistry.find(/^\d*/)
                String domain = config?.ecr?.domain ?: 'INTERNAL'
                def region = Utils.regionFromEcrUrl(dockerRegistry)
                def awsConfig = [
                    credentialsId: config?.ecr?.credentialsId,
                    account      : ecrAccount,
                    rolename     : config?.ecr?.rolename,
                    region       : region,
                    domain       : domain.toUpperCase(),
                ]
                // XXX.cnm - Since this code flow only supports saml2aws, avoid the federate() refactoring
                saml2Aws(awsConfig, awsContainerName)

                def authFileArg = "--authfile=${script.env.WORKSPACE}/.containers/auth.json"
                script.sh(
                    """podman build $authFileArg ${dockerfileArg} $tagArgs \
            ${argString} ${contextArg}""".replaceAll(/\s+/, ' ')
                )
                imageTagMapList.each {
                    script.sh(
                        """podman push $authFileArg \
                ${dockerRegistry}/${imageName}:${it.tag}""".replaceAll(/\s+/, ' ')
                    )
                }
            }
            notification.notifyWithAllMethods("${custommsg}")
        } catch (all) {
            notification.notifyWithAllMethods("${custommsg}")
            script.error("Error '${all.message}' building image: ${imageName}")
            if (FeatureFlags.showStackTraces) {
                script.echo(Functions.printThrowable(all))
            }
        }
    }
}