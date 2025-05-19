package com.cigna.testing


import com.cigna.common.utils.GoEnvBuilder
import com.cigna.common.utils.Utils
import com.cigna.deployment.TerraformDeployment
import com.cloudbees.groovy.cps.NonCPS
import com.cigna.common.kubernetes.PodTemplateCreator

/**
 * Defines testing steps for Terratest Testing.
 */

class TerratestTest extends Testing {

    TerratestTest() {
        containerName = 'aws-d-cloudkitvplz-2-golang'
        awsAllowedPhase = true
        testType = 'integration'
        containerImage = 'enterprise-devops/aws-d-cloudkit'
        containerVersion = 'plz-2-golang'
    }

    @Override
    Boolean prePodConfig() {
        additionalValidationItems = ['testDirectories']
        List<Map> env = GoEnvBuilder.buildGoDeploymentEnv([])

        PodTemplateCreator containerTemplate = PodTemplateCreator.newPodTemplateCreator()
        containerTemplate.addContainer(
            containerName,
            "${containerImage}:${containerVersion}",
            'Always',
            true,
            '/home/jenkins/agent',
            10,
            50,
            500,
            500,
            [
                '/usr/local/bin/adhoc-perms'
            ],
            env
        )

        def terraContainer = containerTemplate.getContainer(containerName)
        terraContainer.put('args', com.cigna.common.utils.Utils.defaultSidecarCommand)

        additionalPodConfig = [
            'volumes'   : [],
            'containers': [terraContainer]
        ]
        if ("${script.env.AUTH_TYPE}" != 'gatekeeper') {
            additionalPodConfig.containers[0].env += [name: 'AWS_PROFILE', value: 'saml']
        }

        super.prePodConfig()
    }
    /*
     * Overriding validate to add awsFed specific configs, and testDirectories as a requirement
     */

    @Override
    @NonCPS
    List validate(boolean requiresBranchPattern = false) {
        List issues = []
        boolean saml = config?.aws?.saml ? true : false

        if (config?.awsFed) {
            additionalValidationItems += [
                'awsFed.credentialsId',
                'awsFed.account',
                'awsFed.rolename',
            ]
        }

        if (config?.aws) {
            if (saml) {
                additionalValidationItems += [
                    'aws.credentialsId',
                    'aws.account',
                    'aws.rolename',
                ]
            } else if (config?.aws?.targetAccountRoleARN) {
                additionalValidationItems += [
                    'aws.targetAccountRoleARN'
                ]
            } else if (config?.aws?.targetAccount && config?.aws?.accountRoleName) {
                additionalValidationItems += [
                    'aws.targetAccount',
                    'aws.accountRoleName',
                ]
            }
        }

        String testDirectoriesErrorMessage = 'Terraform testDirectories must be a list of strings'
        if (config?.testDirectories) {
            if (config.testDirectories instanceof List) {
                config.testDirectories.each { testDirectory ->
                    if (!(testDirectory instanceof String)) {
                        issues.add(testDirectoriesErrorMessage)
                    }
                }
            } else {
                issues.add(testDirectoriesErrorMessage)
            }
        } else {
            issues.add(testDirectoriesErrorMessage)
        }

        List validationIssues = super.validate(requiresBranchPattern)
        validationIssues + issues
    }

    @Override
    void runImpl() {
        psc.podSelector.select(psc, containerName, Utils.cloud(config)) {
            if (!config?.runInAWS) {
                // required for dep later, doing now so it's ready by time dep gets called
                script.sh 'curl -s "http://wdccipmon01.internal.cigna.com/ticket/index.php"'
            }

            TerraformDeployment.updateTerraformVersion(script, config?.terraform?.version)
            TerraformDeployment.updateTerragruntVersion(script, config?.terragrunt?.version)

            if (config?.go?.version) {
                script.sh(
                    "curl -L https://dl.google.com/go/go${config.go.version}.linux-amd64.tar.gz > gobin "
                        + '&& tar -xzf gobin && chmod +x go/bin/go && mv go/bin/go /usr/local/bin'
                )
            }

            awsLogin()

            goTest()
        }
    }

    /*
     * Download dep, and iteratate through all testDirectories link to default go dependency directory
     * so go dep works, run dep init and ensure then go test and finally delete the vendor folder
     * created since it causes issues with Terragrunt later
     */

    private void goTest() {
        withPhaseConfigEnv(["TF_LOG=${config?.terraform?.logLevel ?: ''}"]) {
            script.sh 'go version'
            String goEnv = GoEnvBuilder.buildGoEnv(config?.go?.proxy, config?.go?.private)

            config.testDirectories.each { testDirectory ->
                if (config?.go?.verbosityFlag) {
                    script.sh(
                        "cd ${testDirectory} && ${goEnv}go test -v -timeout ${config?.go?.testTimeout ?: '30m'} 2>&1"
                    )
                } else {
                    script.sh(
                        "cd ${testDirectory} && ${goEnv}go test ${config?.go?.verbosityFlag ?: ''}" +
                            " -timeout ${config?.go?.testTimeout ?: '30m'} 2>&1"
                    )
                }
            }
        }
    }
}
