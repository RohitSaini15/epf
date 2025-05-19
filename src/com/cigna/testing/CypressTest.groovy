package com.cigna.testing

import com.cigna.base.Phase
import com.cigna.common.phases.PodSelector
import com.cigna.common.kubernetes.PodTemplateCreator
import com.cigna.common.utils.Utils
import com.cloudbees.groovy.cps.NonCPS

/**
 * Defines testing steps for Cypress Testing.
 */
class CypressTest extends Testing {
    String installFolder

    CypressTest() {
        testType = 'integration'
        containerName = 'conduit-cypressv13133-v2'
        containerImage = 'enterprise-devops/conduit-cypress'
        containerVersion = '13.13.3-v2'
        additionalValidationItems = ['configFile']
    }

    @Override
    Boolean prePodConfig() {
        List<Map> env = []

        PodTemplateCreator containerTemplate = PodTemplateCreator.newPodTemplateCreator()
        containerTemplate.addContainer(
            containerName,
            "${containerImage}:${containerVersion}",
            'IfNotPresent',
            100,
            1000,
            700,
            1000,
            env
        )

        additionalPodConfig = [
                volumes   : [],
                containers: [containerTemplate.getContainer(containerName)]
        ]
        super.prePodConfig()
    }
    /*
     * Overriding validate to support either basicAuth or credentialsId
     */

    @Override
    @NonCPS
    List validate(boolean requiresBranchPattern = false, Phase phase = this) {
        if (!testingConfiguration?.configFile) {
            additionalValidationItems += [
                    'configFile'
            ]
        }

        List validationIssues = super.validate(requiresBranchPattern, phase)
        validationIssues
    }

    @Override
    void runImpl() {
        psc.podSelector.select(psc,podTemplateContainerName, Utils.cloud(config)) {
            prep()
            cypressRun()
        }
    }
    /**
     * Run Cypress Test
     */
    protected void cypressRun() {
        String configFile = testingConfiguration?.configFile ?: 'e2e'
        //script.sh("npx cypress open")
        script.sh("cd ${configFile} && npm install typescript && npx cypress run")
    }

    /**
     * Prep steps to run prior to running Cypress Steps
     */
    protected void prep() {
        String prepCommands = testingConfiguration?.prepCommands ?: "echo 'No prep, continuing...'"

        script.sh("${prepCommands}")
        // these directories will be the same for all users
        installFolder = testingConfiguration.get('qeBaseFolder', './frontend')
    }
}

