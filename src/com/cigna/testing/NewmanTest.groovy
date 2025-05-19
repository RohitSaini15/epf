package com.cigna.testing

import com.cigna.base.Phase
import com.cigna.common.kubernetes.PodConfigGenerator
import com.cigna.common.kubernetes.PodTemplateCreator
import com.cloudbees.groovy.cps.NonCPS

/**
 * Defines testing steps for Newman testing.
 */

class NewmanTest extends Testing {

    NewmanTest() {
        additionalValidationItems = ['collectionsPath']
        testType = 'integration'
        containerName = 'newmanvalpine-v1'
        containerImage = 'enterprise-devops/newman'
        containerVersion = 'alpine-v1'
    }

    @Override
    Boolean prePodConfig() {
        podTemplateContainerName = PodConfigGenerator.getContainerName("${containerImage}:${containerVersion}")

        List<Map> env = []

        PodTemplateCreator containerTemplate = PodTemplateCreator.newPodTemplateCreator()
        containerTemplate.addContainer(
            containerName,
            "${containerImage}:${containerVersion}",
            10,
            50,
            1000,
            2000,
            env
        )

        additionalPodConfig = [
                volumes   : [],
                containers: [containerTemplate.getContainer(containerName)]
        ]
        super.prePodConfig()
    }

    List collectionPathsList() {
        String collectionsPath = testingConfiguration?.collectionsPath
        String output = script.sh(
                script: "ls -l ${script.env.WORKSPACE}/${collectionsPath} | grep ^d | awk '{print \$9}'",
                returnStdout: true
        )
        output?.tokenize('\n') ?: []
    }

    /*
     * Overriding validate to support either basicAuth or credentialsId
     */

    @Override
    @NonCPS
    List validate(boolean requiresBranchPattern = false, Phase phase = this) {
        if (!testingConfiguration?.basicAuth && !testingConfiguration?.credentialsId) {
            additionalValidationItems += [
                    [
                            testString   : 'newman',
                            customMessage: 'Please specify either \'credentialsId\' or \'basicAuth\' ' +
                                    'to configure newman authentication'
                    ],
            ]
        }

        List validationIssues = super.validate(requiresBranchPattern, phase)
        validationIssues
    }

    @Override
    void runImpl() {
        script.container(containerName) {
            List collectionPaths = collectionPathsList()
            String collectionsBasePath = "${script.env.WORKSPACE}/${testingConfiguration?.collectionsPath}"
            for (int i = 0; i < collectionPaths.size(); i++) {
                String collectionPath = "${collectionsBasePath}/${collectionPaths[i]}"
                String runCommand = "newman run ${collectionPath}/run.json"
                if (testingConfiguration?.junitOutput == true) {
                    runCommand += ' --reporters junit'
                    if (testingConfiguration?.junitExportFilename != '') {
                        runCommand += ' --reporter-junit-export ' +
                                "${script.env.WORKSPACE}/${testingConfiguration?.junitExportFilename}"
                    }
                }
                String envPath = "${collectionPath}/${deploymentConfiguration?.sdlcEnvironment}-environment.json"
                if (testingConfiguration.dataFile) {
                    String dataFilePath = "${collectionPath}/${testingConfiguration.dataFile}"
                    runCommand += " -d ${dataFilePath}"
                }
                if (script.sh(
                        script: "[ -e ${envPath} ]",
                        returnStatus: true
                ) == 0
                ) {
                    runCommand += " -e ${envPath}"
                }
                if (testingConfiguration?.credentialsId) {
                    script.withCredentials(
                            [
                                    script.string(
                                            credentialsId: "${testingConfiguration?.credentialsId}",
                                            variable: 'authString'
                                    )
                            ]
                    ) {
                        runCommand += " -k --env-var 'BASIC_AUTH'='${script.authString}'"
                        script.sh(runCommand)
                        if (testingConfiguration?.junitExportFilename) {
                            script.sh("cat ${script.env.WORKSPACE}/${testingConfiguration?.junitExportFilename}")
                        }
                    }
                } else if (testingConfiguration?.basicAuth) {
                    script.withCredentials(
                            [script.usernamePassword(
                                    credentialsId: testingConfiguration.basicAuth,
                                    usernameVariable: 'NEWMAN_TEST_USERNAME',
                                    passwordVariable: 'NEWMAN_TEST_PASSWORD'
                            )]
                    ) {
                        runCommand += " -k --env-var 'NEWMAN_TEST_USERNAME'='${script.NEWMAN_TEST_USERNAME}'  " +
                                "--env-var 'NEWMAN_TEST_PASSWORD'='${script.NEWMAN_TEST_PASSWORD}'"
                        script.sh(runCommand)
                        if (testingConfiguration?.junitExportFilename) {
                            script.sh("cat ${script.env.WORKSPACE}/${testingConfiguration?.junitExportFilename}")
                        }
                    }
                } else {
                    throw new AuthenticationConfigurationException("Please define either 'basicAuth' or " +
                            "'credentialsId' in the newman configuration section")
                }
            }
        }
    }

    class AuthenticationConfigurationException extends Exception {
        AuthenticationConfigurationException(String message) {
            super(message)
        }
    }
}
