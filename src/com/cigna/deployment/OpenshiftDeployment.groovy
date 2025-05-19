package com.cigna.deployment

import com.cigna.base.Phase
import com.cigna.common.phases.PodSelector
import com.cigna.common.utils.Utils
import com.cloudbees.groovy.cps.NonCPS
import com.cigna.common.kubernetes.PodTemplateCreator

/**
 * Deployment to a Generic Openshift Container
 */
class OpenshiftDeployment extends Deployment {
    private static final String OPENSHIFT_URL_DEFAULT = 'https://openshift.silver.com:8443'

    OpenshiftDeployment() {
        additionalValidationItems = ['openshift.project', 'openshift.credentialsId', 'deployScript']
        containerName = 'helm-clivv354'
        containerImage = 'enterprise-devops/helm-cli'
        containerVersion = 'v3.5.4'
    }

    @Override
    Boolean prePodConfig() {
        List<Map> env =  [
            [
                name : 'HOME',
                value: '/tmp'
            ]
        ]

        PodTemplateCreator containerTemplate = PodTemplateCreator.newPodTemplateCreator()
        containerTemplate.addContainer(
            containerName,
            "${containerImage}:${containerVersion}",
            100,
            500,
            500,
            500,
            env
        )


        additionalPodConfig = [
                'volumes'   : [],
                'containers': [containerTemplate.getContainer(containerName)]
        ]
        configureHelm()
        super.prePodConfig()
    }

    private void configureHelm() {
        String image = config.helm?.image ?: containerImage
        String version = containerVersion

        if (config.helm?.version) {
            version = "v${config.helm.version}"
        }

        this.additionalPodConfig.'containers'[0].image = "${image}:${version}"
    }

    String deployStatus = 'Deploy'

    @Override
    @NonCPS
    List validate(boolean requiresBranchPattern = false, Phase phase = this) {
        config.deployScript = config?.helm?.command ?: config?.deployScript
        super.validate(requiresBranchPattern, phase)
    }

    /**
     * Log into openshift, switch to the project configured then run the deploy/rollback/post deploy command configured
     */
    @Override
    void deploy() {
        String openshiftUrl = deploymentConfiguration?.openshift?.url ?: OPENSHIFT_URL_DEFAULT
        psc.podSelector.select(psc,podTemplateContainerName, Utils.cloud(config)) {
            script.withCredentials(
                    [
                            script.string(
                                    credentialsId: "${deploymentConfiguration.openshift.credentialsId}",
                                    variable: 'openshiftSecret'
                            )
                    ]
            ) {
                String authString
                if (deploymentConfiguration?.openshift?.user) {
                    authString = "-u=${deploymentConfiguration.openshift.user} -p=${script.openshiftSecret}"
                } else {
                    authString = "--token=${script.openshiftSecret}"
                }
                script.sh "oc login ${authString} ${openshiftUrl}"
            }
            script.sh "oc project ${deploymentConfiguration.openshift.project}"
            script.sh "${deploymentConfiguration.deployScript}"
        }
    }
}
