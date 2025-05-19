package com.cigna.builds

import com.cigna.common.utils.AWSUtils
import com.cigna.common.utils.FeatureFlags
import com.cigna.common.utils.PlzUtils
import com.cloudbees.groovy.cps.NonCPS
import hudson.Functions
import com.cigna.common.kubernetes.PodTemplateCreator

/**
 * For terraform module builds utilizing Please.
 */
class PlzBuild extends Build {
    PlzBuild() {
        containerName = 'aws-d-cloudkitvplz-2'
        containerImage = 'enterprise-devops/aws-d-cloudkit'
        containerVersion = 'plz-2'
        awsAllowedPhase = true
        containerCpu = '1000m'
        containerMemory = '1000Mi'
    }

    String plzVerbosity
    List<String> modules

    // Look for good docs on this: https://github.com/jenkinsci/kubernetes-plugin#container-configuration
    @Override
    Boolean prePodConfig() {

        List<Map> env = ([
            [
                name : 'PIP_CACHE_DIR',
                value: '/tmp/.cache/pip'
            ]
        ] + AWSUtils.awsEnvironment()).flatten()

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
        containerTemplate.addVolumeMount(containerName, 'setup-sonar', '/home/jenkins/agent/setup-sonar')
        containerTemplate.addVolumeMount(containerName, 'aws-creds', '/home/jenkins/.aws')
        containerTemplate.addVolumeMount(containerName, 'ecr-creds', '/home/jenkins/.ecr')
        def plzContainer = containerTemplate.getContainer(containerName)
        plzContainer.put('args', com.cigna.common.utils.Utils.defaultSidecarCommand)

        additionalPodConfig = [
            'volumes'   : AWSUtils.awsVolumes(),
            'containers': [plzContainer]
        ]
        if (config.sonarQube?.scannerOptions) {
            additionalPodConfig.containers[0].env = [
                [
                    name : 'SONAR_SCANNER_OPTS',
                    value: config.sonarQube.scannerOptions
                ]
            ]
        }
        super.prePodConfig()
    }

    /**
     * This stage will run an optional aws fed alias, plz build with a compile label, and plz test
     * with a unit label
     */
    @Override
    void executeBuildAndTestStage() {
        List<String> modules = config?.modules

        awsLogin()

        PlzUtils.plzConfigureCache(script, config)
        PlzUtils.dependencyOverride(script, config)

        try {
            if (modules) {
                script.echo("Detected a module build for the following modules: ${modules}")
                script.sh(PlzUtils.constructMultiModuleBuildCommand(config, ['compile']))
                script.sh(PlzUtils.constructMultiModuleTestCommand(config, ['unit']))
            } else if (modules?.empty) {
                script.echo('Modules is specified, but the list is empty. Nothing to build.')
            } else {
                script.echo('Detected a full build. Building all modules.')
                script.sh(
                    "plz build //... -i compile${PlzUtils.constructArgs(config?.extraBuildArgs, config?.verbosityFlag)}"
                )
                script.sh(
                    "plz test //... -i unit${PlzUtils.constructArgs(config?.extraTestArgs, config?.verbosityFlag)}"
                )
            }
            notifySuccess('Plz Build - Compile + Unit completed successfully.')
        } catch (all) {
            notification.notifyWithAllMethods('Plz Build - Compile + Unit failed.')
            if (FeatureFlags.showStackTraces) {
                script.echo(Functions.printThrowable(all))
            }

            throw all
        }
    }

    /**
     * This stage will run an plz build with a publish label, and an optional git tag block
     */
    @Override
    void executePublishStage() {
        List<String> modules = config?.modules

        if (modules) {
            script.echo("Detected a multi module publish for the following modules: ${modules}")
            script.sh(PlzUtils.constructMultiModuleBuildCommand(config, ['publish']))
        } else if (modules?.isEmpty()) {
            script.echo('Modules is specified, but the list is empty. Nothing to publish.')
        } else if (config?.usePublishAlias) {
            // Grab Deployer ID + Token to pass to shell script
            Map<String, String> artifactoryConfig = config.artifactory
            script.withCredentials([script.usernamePassword(
                credentialsId: "${artifactoryConfig.credentialsId}",
                passwordVariable: 'deployerIdToken',
                usernameVariable: 'deployerIdName'
            )]) {
                // Set version string from either tagDetails or artifactory.version

                String version
                if (config?.tagDetails) {
                    // No version set but tagDetails is available, use version specified in file
                    version = script.readFile("${config.tagDetails.tagFile}").trim()
                } else {
                    // Set version as specified in Artifactory config
                    version = artifactoryConfig.version
                }

                // Invoke the publish alias with the credentials passed as arguments
                // TODO: consider whether or not to allow people to override alias

                String publishCmd

                if (config?.extraBuildArgs || config?.verbosityFlag) {
                    String additionalArgs = PlzUtils.constructArgs(config?.extraBuildArgs, config?.verbosityFlag).trim()
                    publishCmd = "plz ${additionalArgs} " +     \
                                     "publish ${version} ${script.deployerIdName} ${script.deployerIdToken}"
                } else {
                    publishCmd = "plz publish ${version} ${script.deployerIdName} ${script.deployerIdToken}"
                }

                script.echo('Publishing modules with aliased command defined under "publish"')
                try {
                    script.sh(publishCmd)
                    notifySuccess('Plz Build - Publish completed successfully.')
                } catch (all) {
                    notification.notifyWithAllMethods('Plz Build - Publish failed.')
                    if (FeatureFlags.showStackTraces) {
                        script.echo(Functions.printThrowable(all))
                    }

                    throw all
                }
            }
        } else {
            script.echo('Detected a full build. Publishing all modules.')
            try {
                script.sh(
                    "plz build //... -i publish${PlzUtils.constructArgs(config?.extraBuildArgs, config?.verbosityFlag)}"
                )
                notifySuccess('Plz Build - Publish completed successfully.')
            } catch (all) {
                notification.notifyWithAllMethods('Plz Build - Publish failed.')
                if (FeatureFlags.showStackTraces) {
                    script.echo(Functions.printThrowable(all))
                }

                throw all
            }
        }
    }

    /*
     * Overriding validate to add addition config items based on config items.
     */

    @Override
    @NonCPS
    List validate(boolean requiresBranchPattern = false) {
        if (config?.awsFed) {
            additionalValidationItems += [
                [testString: 'awsFed.credentialsId', customMessage: 'Missing AWS Fed Credentials']
            ]
        }
        if (config?.aws) {
            additionalValidationItems += [
                'aws.targetAccount',
                'aws.accountRoleName',
            ]
        }
        if (config?.runInAWS && config?.awsFed) {
            additionalValidationItems += [
                [testString: 'aws', customMessage: 'awsFed cannot be used for this phase.'],
            ]
        }

        if (config?.usePublishAlias && !config?.tagDetails) {
            // Validate a version string is defined in the artifactory config. if  tagDetails is not defined
            additionalValidationItems += [
                [
                    testString   : 'artifactory.version',
                    customMessage: 'Version string not specified. ' +     \
                                       'Either set artifactory.version or provide tagDetails'
                ],
            ]
        }

        super.validate(requiresBranchPattern)
    }

    void notifySuccess(String message) {
        if (!config?.notifyFailuresOnly) {
            notification.notifyWithAllMethods(message)
        }
    }
}
