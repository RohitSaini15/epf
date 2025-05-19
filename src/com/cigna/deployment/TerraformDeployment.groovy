package com.cigna.deployment


import com.cigna.common.utils.GoEnvBuilder
import com.cigna.common.utils.AWSUtils
import com.cigna.common.utils.Utils
import com.cloudbees.groovy.cps.NonCPS
import com.cigna.common.kubernetes.PodTemplateCreator

/**
 * Deployment via Terraform/Terragrunt
 */
class TerraformDeployment extends Deployment {
    TerraformDeployment() {
        containerName = 'aws-d-cloudkitvplz-2'
        containerImage = 'enterprise-devops/aws-d-cloudkit'
        containerVersion = 'plz-2'
        containerMemory = '1000Mi'
        awsAllowedPhase = true
        deployStatus = 'Deploy'
        containerImagePullPolicy = 'IfNotPresent'
    }

    /*
     * Update Terragrunt to the version specified
     *
     * @param script The script object to use to call steps
     * @param version The version of Terragrunt to download
     */

    static void updateTerragruntVersion(Object script, String version) {
        if (version) {
            script.sh(
                "tgswitch -s ${version}"
            )
        }
    }

    /*
     * Update Terrform to the version specified
     *
     * @param script The script object to use to call steps
     * @param version The version of Terraform to download
     */

    static void updateTerraformVersion(Object script, String version) {
        if (version) {
            script.sh(
                "tfswitch ${version}"
            )
        }
    }

    static String getTerragruntInitCommand(
        String tfVars,
        String terragruntVersion,
        Boolean terragruntUseAll,
        String initExtraArgs,
        String terragruntExtraArgs
    ) {
        String command = ''
        String initExtraArgsFmt = initExtraArgs ? " ${initExtraArgs}" : ''
        String terragruntExtraArgsFmt = terragruntExtraArgs ? " ${terragruntExtraArgs}" : ''
        if (terragruntUseAll) {
            String initCommand = getTerragruntCommand(terragruntVersion, terragruntUseAll, 'init')
            command = "${tfVars}terragrunt ${initCommand}${initExtraArgsFmt}${terragruntExtraArgsFmt}" +
                ' --terragrunt-parallelism 4'
        } else {
            String initCommand = getTerragruntCommand(terragruntVersion, terragruntUseAll, 'init')
            command = "${tfVars}terragrunt ${initCommand}${initExtraArgsFmt}${terragruntExtraArgsFmt}"
        }

        command
    }

    static String getTerragruntPlanCommand(
        String tfVars,
        String terragruntVersion,
        Boolean terragruntUseAll,
        String planExtraArgs,
        String terragruntExtraArgs
    ) {
        String command = ''
        String planExtraArgsFmt = planExtraArgs ? " ${planExtraArgs}" : ''
        String terragruntExtraArgsFmt = terragruntExtraArgs ? " ${terragruntExtraArgs}" : ''
        if (terragruntUseAll) {
            String planCommand = getTerragruntCommand(terragruntVersion, true, 'plan')
            command = "${tfVars}terragrunt ${planCommand}${planExtraArgsFmt}${terragruntExtraArgsFmt}" +
                ' --terragrunt-parallelism 4'
        } else {
            String planCommand = getTerragruntCommand(terragruntVersion, false, 'plan')
            command = "${tfVars}terragrunt ${planCommand}${planExtraArgsFmt}${terragruntExtraArgsFmt} -out=tfplan"
        }

        command
    }

    static String getTerragruntApplyCommand(
        String tfVars,
        String terragruntVersion,
        Boolean terragruntUseAll,
        String applyExtraArgs,
        String terragruntExtraArgs
    ) {
        String command = ''
        String applyExtraArgsFmt = applyExtraArgs ? " ${applyExtraArgs}" : ''
        String terragruntExtraArgsFmt = terragruntExtraArgs ? " ${terragruntExtraArgs}" : ''
        if (terragruntUseAll) {
            String applyCommand = getTerragruntCommand(terragruntVersion, true, 'apply')
            command = "${tfVars}terragrunt ${applyCommand}${applyExtraArgsFmt}" +
                "${terragruntExtraArgsFmt} --terragrunt-parallelism 4 -auto-approve"
        } else {
            String applyCommand = getTerragruntCommand(terragruntVersion, false, 'apply')
            command = "${tfVars}terragrunt ${applyCommand}${applyExtraArgsFmt}" +
                "${terragruntExtraArgsFmt} -auto-approve tfplan"
        }

        command
    }

    static String getTerragruntDestroyCommand(
        String tfVars,
        String terragruntVersion,
        Boolean terragruntUseAll,
        String destroyExtraArgs,
        String terragruntExtraArgs
    ) {
        String command = ''
        String destroyExtraArgsFmt = destroyExtraArgs ? " ${destroyExtraArgs}" : ''
        String terragruntExtraArgsFmt = terragruntExtraArgs ? " ${terragruntExtraArgs}" : ''
        if (terragruntUseAll) {
            String destroyCommand = getTerragruntCommand(terragruntVersion, true, 'destroy')
            command = "${tfVars}terragrunt ${destroyCommand}${destroyExtraArgsFmt}" +
                "${terragruntExtraArgsFmt} --terragrunt-parallelism 4 -auto-approve"
        } else {
            String destroyCommand = getTerragruntCommand(terragruntVersion, false, 'destroy')
            command = "${tfVars}terragrunt ${destroyCommand}${destroyExtraArgsFmt}" +
                "${terragruntExtraArgsFmt} -auto-approve"
        }

        command
    }

    static String getTerragruntCommand(String terragruntVersion, Boolean terragruntUseAll, String command) {
        String terragruntCommand = command

        if (terragruntUseAll) {
            terragruntCommand = useTerragruntRunAllSyntax(terragruntVersion) ? "run-all ${command}" : "${command}-all"
        }

        terragruntCommand
    }

    static Boolean useTerragruntRunAllSyntax(String terragruntVersion) {
        Boolean useRunAllSyntax = false

        if (terragruntVersion) {
            String[] versionParts = terragruntVersion.split('\\.')

            if (versionParts.size() < 3) {
                throw new IllegalArgumentException('The version of terragrunt must follow the pattern x.y.z')
            }

            Integer minorVersion = versionParts[1].toInteger()
            Integer patchVersion = versionParts[2].toInteger()
            Integer minorVersionCheck = 28
            Integer patchVersionCheck = 1

            useRunAllSyntax = minorVersion > minorVersionCheck ||
                (minorVersion == minorVersionCheck && patchVersion >= patchVersionCheck)
        }

        useRunAllSyntax
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
            100,
            1000,
            500,
            1000,
            [
                '/usr/local/bin/adhoc-perms'
            ],
            env
        )
        containerTemplate.addVolumeMount(containerName, 'aws-creds', '/home/jenkins/.aws')
        containerTemplate.addVolumeMount(containerName, 'ecr-creds', '/home/jenkins/.ecr')
        def transformedContainer = containerTemplate.getContainer(containerName)
        transformedContainer.put('args', com.cigna.common.utils.Utils.defaultSidecarCommand)

        additionalPodConfig = [
            'volumes'   : AWSUtils.awsVolumes(),
            'containers': [transformedContainer]
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
    @SuppressWarnings(['CyclomaticComplexity'])
    List validate(boolean requiresBranchPattern = false) {
        additionalValidationItems += ['directories']
        List issues = []
        boolean saml = config?.aws?.saml ? true : false

        if (config?.directories) {
            String directoryMissingMessage = (
                'All items in the directories list must contain a directory key.'
            )
            config.directories.each { directoryMap ->
                if (!directoryMap?.directory && !issues.contains(directoryMissingMessage)) {
                    issues.add(directoryMissingMessage)
                }
            }
        }

        if (config?.awsFed) {
            additionalValidationItems += [
                'awsFed.credentialsId',
                'awsFed.account',
                'awsFed.rolename',
            ]
        }

        if (config?.aws) {
            if (saml == true) {
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

        if (config?.terraform?.logLevel) {
            String logLevel = config.terraform.logLevel.toUpperCase()
            final List<String> VALID_LOG_LEVELS = [
                'TRACE',
                'DEBUG',
                'INFO',
                'WARN',
                'ERROR',
            ]
            Boolean invalidLogLevel = !VALID_LOG_LEVELS.contains(logLevel)
            if (invalidLogLevel) {
                issues.add("Valid Terraform LogLevel.groovy levels are ${VALID_LOG_LEVELS.join(', ')}")
            } else {
                config.terraform.logLevel = logLevel
            }
        }

        List validationIssues = super.validate(requiresBranchPattern)
        validationIssues + issues
    }

    /**
     * Federate into AWS if specified and run Terragrunt commands. If terraform.version is defined
     * tfswitch is called, if terragrun.version, or awsFed.version is called that specified
     * version is installed. If awsFed is defined awsFed method is called, and terragrunt method is always called
     */
    @SuppressWarnings('CyclomaticComplexity')
    @Override
    void deploy() {
        psc.podSelector.select(psc, podTemplateContainerName, Utils.cloud(config)) {
            updateTerraformVersion(script, config?.terraform?.version)
            updateTerragruntVersion(script, config?.terragrunt?.version)

            awsLogin()
            terragrunt()
        }
    }

    /*
     * Return the extraArgs to apply to init, plan, apply, and destroy
     *
     * @param directoryMap The map for the directory to grap extraArgs from
     *
     * @returns The extra args strings to use for init, plan, apply, and destroy
     */

    private List<String> getExtraArgs(Map directoryMap) {
        String initExtraArgs = directoryMap?.extraArgs?.init ?: ''
        String planExtraArgs = directoryMap?.extraArgs?.plan ?: ''
        String applyExtraArgs = directoryMap?.extraArgs?.apply ?: ''
        String destroyExtraArgs = directoryMap?.extraArgs?.destroy ?: ''

        [initExtraArgs, planExtraArgs, applyExtraArgs, destroyExtraArgs]
    }

    /*
     * Loop through all directories provided, run terragrunt init with any extra parameters included,
     * change to the direcotry specified, run terragrunt destroy OR plan then apply optionally with -all if
     * configured. This also creates a terragrunt.hcl file if it doesn't exist in order to allow
     * terragrunt to be used with regular terraform
     */

    private void terragrunt() {
        String terragruntVersion = config?.terragrunt?.version

        config.directories.each { directoryMap ->
            String tfVarsCommand = ''
            Map<String, String> tfVars = directoryMap.tfVars

            if (tfVars) {
                tfVarsCommand = tfVars.inject('') { command, tfVarKey, tfVarValue ->
                    "${command}TF_VAR_${tfVarKey}=${tfVarValue} "
                }
            }

            String initExtraArgs
            String planExtraArgs
            String applyExtraArgs
            String destroyExtraArgs
            (initExtraArgs, planExtraArgs, applyExtraArgs, destroyExtraArgs) = getExtraArgs(directoryMap)
            String terragruntExtraArgs = '--terragrunt-non-interactive ' +
                '--terragrunt-include-external-dependencies'

            withPhaseConfigEnv(["TF_LOG=${config?.terraform?.logLevel ?: ''}"]) {
                script.dir(directoryMap.directory ?: './') {
                    Boolean terragruntUseAll = directoryMap.useAll ?: false

                    script.sh(
                        getTerragruntInitCommand(
                            tfVarsCommand,
                            terragruntVersion,
                            terragruntUseAll,
                            initExtraArgs,
                            terragruntExtraArgs
                        )
                    )

                    if (directoryMap?.destroy) {
                        script.sh(
                            getTerragruntDestroyCommand(
                                tfVarsCommand,
                                terragruntVersion,
                                terragruntUseAll,
                                destroyExtraArgs,
                                terragruntExtraArgs
                            )
                        )
                        return
                    }
                    if (!directoryMap?.applyOnly) {
                        script.sh(
                            getTerragruntPlanCommand(
                                tfVarsCommand,
                                terragruntVersion,
                                terragruntUseAll,
                                planExtraArgs,
                                terragruntExtraArgs
                            )
                        )
                    }

                    if (directoryMap?.planOnly) {
                        script.echo('Plan only set to true - skipping apply')
                    } else {
                        script.sh(
                            getTerragruntApplyCommand(
                                tfVarsCommand,
                                terragruntVersion,
                                terragruntUseAll,
                                applyExtraArgs,
                                terragruntExtraArgs
                            )
                        )
                    }
                }
            }
        }
    }
}
