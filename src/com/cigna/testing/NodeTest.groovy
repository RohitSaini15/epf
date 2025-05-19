package com.cigna.testing

/**
 * Defines testing steps for NodeJS Testing.
 */
import com.cigna.common.kubernetes.PodTemplateCreator

class NodeTest extends Testing {
    NodeTest() {
        testType = 'integration'
        containerName = 'nodetest'
        containerImage = 'enterprise-devops/node'
        containerVersion = '12-alpine'


        List<Map> env = [
            [
                name : 'HOME',
                value: '/tmp'
            ],
            [
                name : 'YARN_CACHE_FOLDER',
                value: '/tmp/.cache/yarn'
            ]
        ]

        PodTemplateCreator containerTemplate = PodTemplateCreator.newPodTemplateCreator()
        containerTemplate.addContainer(
            containerName,
            "${containerImage}:${containerVersion}",
            10,
            50,
            1500,
            3000,
            env
        )

        additionalPodConfig = [
            'volumes'   : [],
            'containers': [containerTemplate.getContainer(containerName)]
        ]
    }


    String installFolder

    @Override
    void runImpl() {
        script.container(containerName) {
            prep()
            nodeRun()
        }
    }

    /**
     * Prep steps to run prior to running Protractor Steps
     */
    protected void prep() {
        String prepCommands = testingConfiguration?.prepCommands ?: "echo 'No prep, continuing...'"

        script.sh("${prepCommands}")

        installFolder = testingConfiguration.get('qeBaseFolder', './')
    }

    /**
     * Run protractor
     */
    protected void nodeRun() {
        String configFile = testingConfiguration?.configFile ?: './conf.js'
        String args = testingConfiguration?.args ?: ''

        script.sh("cd ${installFolder} && node ${configFile} ${args}")
    }
}
