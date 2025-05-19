package com.cigna.testing

import com.cigna.common.utils.AWSUtils
import com.cigna.common.utils.GoEnvBuilder
import com.cigna.common.utils.PlzUtils
import com.cloudbees.groovy.cps.NonCPS
import com.cigna.common.kubernetes.PodTemplateCreator

/**
 * Defines testing steps for Please build test (plz test).
 */

class PlzTest extends Testing {
    PlzTest() {
        containerCpu = '1000m'
        containerMemory = '1000Mi'
        containerName = 'aws-d-cloudkitvplz-2'
        testType = 'integration'
        additionalValidationItems = ['labels']
        containerImage = 'enterprise-devops/aws-d-cloudkit'
        containerVersion = 'plz-2'
        awsAllowedPhase = true
    }

    @Override
    Boolean prePodConfig() {

        List<Map> env = AWSUtils.awsEnvironment()

        PodTemplateCreator containerTemplate = PodTemplateCreator.newPodTemplateCreator()
        containerTemplate.addContainer(
            containerName,
            "${containerImage}:${containerVersion}",
            'Always',
            true,
            '/home/jenkins/agent',
            1000,
            1000,
            1000,
            1000,
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

        if ("${script.env.AUTH_TYPE}" != 'gatekeeper') {
            additionalPodConfig.containers[0].env += [name: 'AWS_PROFILE', value: 'saml']
        }

        super.prePodConfig()
    }

    /*
     * Overriding validate to add awsFed specific configs, and directories as a requirement
     */

    @Override
    @NonCPS
    List validate(boolean requiresBranchPattern = false) {
        List issues = []

        if (config?.labels && !( config.labels instanceof List )) {
            issues.add('Plz test type labels must be a list of strings')
        }

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

        List validationIssues = super.validate(requiresBranchPattern)
        validationIssues + issues
    }

    @Override
    void runImpl() {
        script.container(containerName) {
            boolean aws = config?.runInAWS ?: false
            List<String> modules = config?.modules
            List<String> labels = config?.labels
            String terraIam = ( aws ) ?
                'arn:aws:iam::' + config.aws.targetAccount + ':role/' + config.aws.accountRoleName : ''

            PlzUtils.plzConfigureCache(script, config)
            PlzUtils.dependencyOverride(script, config)

            awsLogin()

            withPhaseConfigEnv(["TERRAGRUNT_IAM_ROLE=${terraIam}"]) {
                if (modules) {
                    script.echo("Detected a mutli module test. Testing modules: ${modules}")
                    script.sh(PlzUtils.constructMultiModuleTestCommand(config, labels))
                } else if (modules?.isEmpty()) {
                    script.echo('Modules is specified, but the list is empty. Nothing to test.')
                } else {
                    script.sh(
                        'plz test //...' +
                            "${PlzUtils.constructLabelString(labels)}" +
                            "${PlzUtils.constructArgs(config?.extraTestArgs, config?.verbosityFlag)}"
                    )
                }
            }
        }
    }
}
