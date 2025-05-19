package com.cigna.common.utils

/**
 * Common plz utilities used across multiple phases
 */
class PlzUtils {

    private static final String DEFAULT_MODULE_DEPLOY_TARGET = 'deploy'

    /**
     * Create a plz cache directory and a plz configuration that points to that cache,
     * depending on if config.cacheEnabled is true. Set to false by default because according
     * to plz docs it might not be a good idea to share a cache with different builds
     *
     * @param script The script object from cignaBuildFlow's 'this'
     * @param config The configuration for the Phase (config)
     */
    static void plzConfigureCache(Object script, Map<String, Object> config) {
        final String PLZ_CONFIG_PATH = '~/.config/please'
        String plzCacheConfig = '''
            [cache]
            dir = /tmp/.cache/plz-cache
        '''
        boolean plzCacheEnabled = config?.cacheEnabled ?: false

        if (plzCacheEnabled) {
            script.sh("""
                mkdir -p ${PLZ_CONFIG_PATH}
                echo "${plzCacheConfig.stripIndent()}" > ${PLZ_CONFIG_PATH}/plzconfig
                """)
        }
    }

    /**
     * call plz fed alias unless callPlzFed is false. Always set AWS_FED_PASSWORD and
     * AWS_FED_USERNAME
     *
     * @param script The script object from cignaBuildFlow's 'this'
     * @param targetConfig The configuration for the Phase (config)
     * @param codeToRunAfter optional closure to run after exporting creds and calling plz fed. It is
     * run even if there is no awsFed configuration (in which case all the method does is run that closure)
     */
    static void plzAwsFed(Object script, Map<String, Object> targetConfig, Closure codeToRunAfter = { } ) {
        if (targetConfig?.awsFed) {
            Boolean callPlzFed = false
            if (targetConfig.awsFed?.callPlzFed == null || targetConfig.awsFed?.callPlzFed) {
                callPlzFed = true
            }
            if (callPlzFed) {
                script.echo('Fed configuration found, trying to call fed plz alias.')
            } else {
                script.echo(
                    'Setting AWS_FED_PASSWORD and AWS_FED_USERNAME environment variables'
                )
            }
            script.withCredentials(
                [script.usernamePassword(
                    credentialsId: "${targetConfig.awsFed.credentialsId}",
                    usernameVariable: 'AWS_FED_USERNAME',
                    passwordVariable: 'AWS_FED_PASSWORD'
                )]
            ) {
                script.sh(
                    "export AWS_FED_PASSWORD='${script.AWS_FED_PASSWORD}' && export AWS_FED_USERNAME='${script.AWS_FED_USERNAME}'"
                )
                if (callPlzFed) {
                    script.sh("plz fed ${targetConfig.sdlcEnvironment}")
                }

                codeToRunAfter()
            }
        } else {
            codeToRunAfter()
        }
    }

    /**
     * if saml2aws config exists, export SAML2AWS_USERNAME and SAML2AWS_PASSWORD from given credential id
     *
     * @param script The script object from cignaBuildFlow's 'this'
     * @param targetConfig The configuration for the Phase (config)
     * @param codeToRunAfter optional closure to run after exporting creds and calling saml2aws. It is
     * run even if there is no additional configuration (in which case all the method does is run that closure)
     */

    static void plzSaml2Aws(Object script, Map<String, Object> targetConfig, Closure codeToRunAfter = { } ) {
        if (targetConfig?.saml2aws) {
            String region = targetConfig?.saml2aws?.region ?: 'us-east-1'
            script.echo(
                'Setting SAML2AWS_PASSWORD and SAML2AWS_USERNAME environment variables'
            )
            script.withCredentials(
                [script.usernamePassword(
                    credentialsId: "${targetConfig.saml2aws.credentialsId}",
                    usernameVariable: 'SAML2AWS_USERNAME',
                    passwordVariable: 'SAML2AWS_PASSWORD'
                )]
            ) {
                script.sh(
                    "unset SAML2AWS_PROFILE && "
                    + "export SAML2AWS_PASSWORD='${script.SAML2AWS_PASSWORD}' && "
                    + "export SAML2AWS_USERNAME='${script.SAML2AWS_USERNAME}' && "
                    + "export SAML2AWS_ROLE='${targetConfig.saml2aws.saml2awsRoleArn}' && "
                    + "export SAML2AWS_REGION=${region} && "
                    + "export SAML2AWS_PROFILE=saml && "
                    + "saml2aws login --skip-prompt --force"
                )
            codeToRunAfter()
            }
        }
    }

    /*
     * Construct plz build command for multiple modules
     *
     * @param config The configuration
     * @param labels A list of labels to apply to build
     * @return the full command to build modules
     *         e.g. plz build //module1 -i compile && plz build //module2 -i compile
     */
    static String constructMultiModuleBuildCommand(Map<String, Object> config, List<String> labels) {
        List<String> modules = config?.modules
        String extraArgs = config?.extraBuildArgs

        List<String> plzTargets = modules.collect { moduleName ->
            "plz build ${moduleName}/..."
        }

        PlzUtils.constructMultiModuleCommand(config, plzTargets, extraArgs, labels ?: [])
    }

    /*
     * Construct a test command for multiple modules
     *
     * @param config The configuration
     * @param labels A list of labels to apply to testing
     * @returns the full test command
     *          e.g. plz test //module1/... -i unit && plz test //module2/... -i unit
     */
    static String constructMultiModuleTestCommand(Map<String, Object> config, List<String> labels) {
        List<String> modules = config?.modules
        String extraArgs = config?.extraTestArgs

        List<String> plzTargets = modules.collect { moduleName ->
            "plz test ${moduleName}/..."
        }

        PlzUtils.constructMultiModuleCommand(config, plzTargets, extraArgs, labels ?: [])
    }

    /*
     * Construct a deploy command for multiple modules
     *
     * @param config The configuration
     * @return The command to deploy all modules
     *         e.g. plz run //module/aws/module1:deploy && plz run //module/aws/module2:deploy
     */
    static String constructMultiModuleDeployCommand(Map<String, Object> config) {
        List<String> modules = config?.modules
        String moduleDeployTarget = config?.moduleDeployTarget ?: DEFAULT_MODULE_DEPLOY_TARGET
        String extraArgs = config?.extraArgs

        List<String> plzTargets = modules.collect { moduleName ->
            "plz run ${moduleName}:${moduleDeployTarget}"
        }

        PlzUtils.constructMultiModuleCommand(config, plzTargets, extraArgs)
    }

    /*
     * Construct extra agrs portion of build and test commands
     *
     * @param extraArgs Extra arguments to add to the command
     * @param verbosityFlag The verbosity flag to use e.g. -vvv
     * @return The formatted extra args e.g. ' dev -vvv'
     */
    static String constructArgs(String extraArgs='', String verbosityFlag='') {
        String fmtExtraArgs = extraArgs ? " ${extraArgs}" : ''
        String fmtVerbosityFlag = verbosityFlag ? " ${verbosityFlag}" : ''

        " --show_all_output${fmtExtraArgs}${fmtVerbosityFlag}"
    }

    /*
     * Properly construct the label string form a list of labels
     *
     * @param labels The list of labels
     * @return A single string of labels e.g. -i unit -i lint
     */
    static String constructLabelString(List<String> labels) {
        String labelString = labels.inject('') { labelString, label ->
            "${labelString} -i ${label}"
        }

        labelString ?: ''
    }

    /*
     * Utilize the web gateway ticketing function to pull dependency that may be blocked
     *
     * @param script The script object from cignaBuildFlow's 'this'
     * @param config The user provided configuration
     */
    static void dependencyOverride(Object script, Map<String, Object> config) {
        if (config?.dependencyOverride) {
            script.sh 'curl -s "http://wdccipmon01.internal.cigna.com/ticket/index.php" && sleep 20'
        }
    }

    /*
     * Construct any multi module command
     *
     * @param config The configuration
     * @param plzTargets A list of plz targets for each module
     * @param extraArgs Extra args to pass to the command
     * @param labels A list of labels to add to the command
     * @return The full command
     *         e.g. plz build //module/aws/module1/... -i compile --show_all_output dev -vvv
     */
    static private String constructMultiModuleCommand(
        Map<String, Object> config,
        List<String> plzTargets,
        String extraArgs,
        List<String> labels = []
    ) {
        String verbosityFlag = config?.verbosityFlag

        List<String> plzCommands = plzTargets.collect { plzTarget ->
            "${plzTarget}" +
            "${PlzUtils.constructLabelString(labels)}" +
            "${PlzUtils.constructArgs(extraArgs, verbosityFlag)}"
        }

        plzCommands.inject { fullCommand, plzCommand ->
            "${fullCommand} && ${plzCommand}"
        }
    }
}
