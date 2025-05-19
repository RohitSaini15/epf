package com.cigna.deployment

import com.cigna.common.phases.PodSelector
import com.cigna.common.utils.Utils

/**
 * Class to provide an opinionated helm deployment
 */
class GenericOscpCmdDeployment extends HelmDeployment {
    GenericOscpCmdDeployment() {
        additionalValidationItems += [
            'helm.command',
        ]
        additionalValidationItems -= [
                'helm.values',
                'helm.deploymentName'
        ]
    }

    @Override
    void deploy() {
        if (config.containsKey('vault')) {
            extractVaultFiles()
        }
        psc.podSelector.select(psc,podTemplateContainerName, Utils.cloud(config)) {
            executeHelmCommand(config.helm.command)
        }
    }

}
