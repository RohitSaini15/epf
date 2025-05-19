package com.cigna.deployment

import com.cigna.common.exception.InvalidInputException
import com.cigna.common.kubernetes.PodTemplateCreator
import com.cigna.common.utils.*
import com.cloudbees.groovy.cps.NonCPS
import hudson.Functions

/**
 * Deployment via Please Build (plz)
 */
class PlzDeployment extends Deployment {
    PlzDeployment() {
        containerImage = 'enterprise-devops/aws-d-cloudkit'
        containerVersion = 'plz-2'
        containerMemory = '2000Mi'
        containerCpu = '1000m'
        awsAllowedPhase = true
        containerName = 'aws-d-cloudkitvplz-2'
    }

    @Override
    Boolean prePodConfig() {
        List<Map> env = (GoEnvBuilder.buildGoDeploymentEnv([
            [
                name : 'TF_IN_AUTOMATION',
                value: 'true'
            ],
        ]) + AWSUtils.awsEnvironment().flatten())

        PodTemplateCreator containerTemplate = PodTemplateCreator.newPodTemplateCreator()
        containerTemplate.addContainer(
            containerName,
            "${containerImage}:${containerVersion}",
            'Always',
            true,
            '/home/jenkins/agent',
            1000,
            2000,
            1000,
            2000,
            [
                '/usr/local/bin/adhoc-perms'
            ],
            env
        )
        containerTemplate.addVolumeMount(containerName, 'aws-creds', '/home/jenkins/.aws')
        containerTemplate.addVolumeMount(containerName, 'ecr-creds', '/home/jenkins/.ecr')
        def plzContainer = containerTemplate.getContainer(containerName)
        plzContainer.put('args', com.cigna.common.utils.Utils.defaultSidecarCommand)

        additionalPodConfig = [
            'volumes'   : AWSUtils.awsVolumes(),
            'containers': [plzContainer]
        ]

        super.prePodConfig()
    }

    String deployStatus = 'Deploy'

    /*
     * Overriding validate to add awsFed specific configs
     */

    @Override
    @NonCPS
    List validate(boolean requiresBranchPattern = false) {
        List issues = []

        if (config?.awsFed) {
            additionalValidationItems += [
                'awsFed.credentialsId'
            ]
        }

        if (config?.aws) {
            additionalValidationItems += [
                'aws.targetAccount',
                'aws.accountRoleName',
            ]
        }

        if (config?.saml2aws) {
            additionalValidationItems += [
                'saml2aws.credentialsId',
                'saml2aws.saml2awsRoleArn',
            ]
        }

        List validationIssues = super.validate(requiresBranchPattern)
        validationIssues + issues
    }

    /**
     * Call plz with the given alias (deploy default) with the given extraArgs and verbosity flag
     */
    @Override
    void deploy() {
        boolean aws = config?.runInAWS ?: false
        List<String> modules = config?.modules
        String terraIam = (aws) ?
            'arn:aws:iam::' + config.aws.targetAccount + ':role/' + config.aws.accountRoleName : ''

        psc.podSelector.select(psc, podTemplateContainerName, Utils.cloud(config)) {
            PlzUtils.plzConfigureCache(script, config)
            PlzUtils.dependencyOverride(script, config)

            Closure codeToRunAfter = {
                withPhaseConfigEnv(["TERRAGRUNT_IAM_ROLE=${terraIam}"]) {
                    if (modules) {
                        script.echo("Detected a multi module deploy for modules: ${modules}")
                        script.sh(PlzUtils.constructMultiModuleDeployCommand(config))
                    } else if (modules?.isEmpty()) {
                        script.echo('Modules is specified, but the list is empty. Nothing to deploy.')
                    } else {
                        script.echo('Detected a full deploy - deploying all modules')
                        script.sh(
                            "plz ${config?.alias ?: 'deploy'}" +
                                "${PlzUtils.constructArgs(config?.extraArgs, config?.verbosityFlag)}"
                        )
                    }
                }
            }
            deployWithNotification(aws, codeToRunAfter)
        }
    }

    /**
     * Separate method for calling deployment script to reduce cyclomatic complexity of deploy() method
     */
    void deployWithNotification(boolean _, Closure codeToRunAfter) {
        try {
            awsLogin(codeToRunAfter)
            if (!config?.notifyFailuresOnly) {
                notification.notifyWithAllMethods("Plz ${config?.alias ?: 'deploy'} completed successfully.")
            }
        } catch (all) {
            notification.notifyWithAllMethods("Plz ${config?.alias ?: 'deploy'} failed: ${all.message}")
            if (FeatureFlags.showStackTraces) {
                script.echo(Functions.printThrowable(all))
            }
            throw all
        }
    }

    @NonCPS
    def newInvalidDeployConfiguration(String msg) {
        new InvalidInputException(msg)
    }
}
