package com.cigna.base

import com.cigna.common.notification.Notification
import com.cigna.common.utils.*
import com.cloudbees.groovy.cps.NonCPS

import static com.cigna.common.utils.Utils.isRunningInEKS

/**
 * Abstract Class to enable validation by polymorphic reference and allow verifying if the current
 * branch matches the configured branch
 */
abstract class Phase extends DockerPipelineLib {
    public static String commitStatusName = 'EPF'
    public static String baseDirectory = './'
    protected String customWorkspace

    protected String defaultJunitGlob = '*.xml'
    protected boolean defaultJunitAllowEmpty = false
    protected double defaultJunitHealthScaleFactor = 1.0
    protected boolean awsAllowedPhase = false

    protected Notification notification
    protected String phaseName = '' // If specified in the phase, will be used to identify start/end of phase
    protected String phaseLabel = '' // Used for named phases which run in parallel, otherwise empty
    protected String stashIncludePattern = ''
    protected String stashExcludePattern = '**/.git/*'

    // by default, add subphase containers to the pod template
    protected Boolean accumulateSubphaseContainers = true
    /**
     * Check if a Phase is Valid
     * @param requiresBranchPattern If true, adds the 'branchPattern' dotpath to validation check, defaults to false
     * @param phase The phase to retrieve validation items from, defaults to this
     * @return list of validation issues, successful validation returns empty list
     */
    @NonCPS
    List validate(boolean requiresBranchPattern = false, Phase phase = this) {
        List validationItems = phase.baseValidationItems + phase.additionalValidationItems
        List issues = []
        String className = ''.join(' ', this.getClass().simpleName.split('(?<=[a-z])(?=[A-Z])'))
        String missingConfigMessageBase = "Missing required ${className} specification: "
        if (requiresBranchPattern) {
            validationItems.add('branchPattern')
        }
        if (config?.junit) {
            if (!Map.isAssignableFrom(config.junit.getClass())) {
                issues.add('build\'s junit option must be a Map')
            }
        }
        if (config?.runInAWS && !phase.awsAllowedPhase) {
            issues.add("This ${className} phase type is currently not supported to run in AWS. Please " \
                                                                                + 'remove runInAWS from this phase configuration.')
        }
        if (config?.buildType in ['reltio', 'dynatrace', 'ace']) {
            validationItems.remove('checkmarx.credentialsId')
            validationItems.remove('checkmarx.settings.CX_PROJECT_TEAM_NAME')
            validationItems.remove('sonarQube.credentialsId')
        }
        if (config?.sonarEnabled == false) {
            validationItems.remove('sonarQube.credentialsId')
        }
        if (config?.checkmarxEnabled == false) {
            validationItems.remove('checkmarx.credentialsId')
            validationItems.remove('checkmarx.settings.CX_PROJECT_TEAM_NAME')
        }
        validationItems.each { validateItem ->
            String message
            if (validateItem.getClass() == ArrayList || validateItem.getClass() == List) {
                message = validateListItem(validateItem as List)
            } else if (validateItem.getClass() == LinkedHashMap || validateItem.getClass() == Map) {
                message = validateMapItem(validateItem as Map)
            } else {
                message = validateStringItem(validateItem as String)
            }
            if (!message?.empty) {
                issues.add(missingConfigMessageBase + message)
            }
        }
        // Make sure that the contents of the list are all java.lang.String.
        issues.eachWithIndex { string, idx ->
            issues[idx] = string.toString()
        }
        issues
    }

    /**
     * Since groovy doesn't yet support map navigation by dot notation as a string (Safely)
     * We must do it ourselves.
     * @param map goalConfig to be validated
     * @param dotPath The string dotpath to be evaluated e.g. 'checkmarx.settings'
     * @return value of the dotpath, otherwise an empty map.
     */
    @NonCPS
    String evalPath(Map map, String dotPath) {
        String seperator = '.'

        if (map == null || map.isEmpty()) {
            return null
        }
        if (!dotPath.contains(seperator)) {
            Object re = null
            Object mapItem = map.get(dotPath)
            if (mapItem) {
                re = mapItem
            }
            return re
        }
        String firstPropName = dotPath[0..dotPath.indexOf(seperator) - 1]
        String remainingPath = dotPath[dotPath.indexOf(seperator) + 1..-1]

        evalPath(map.get(firstPropName), remainingPath)
    }

    /**
     * Check if a validate item Map is valid
     * @param validateMap The map item to test that contains the dotpath string and custom message
     * @return String with custom validation message, successful validation returns empty String
     */
    @NonCPS
    String validateMapItem(Map<String, String> validateMap) {
        String re
        String testItem = validateMap.testString
        String customMessage = validateMap.customMessage
        if (evalPath(config, testItem)) {
            re = ''
        } else {
            re = customMessage
        }
        re
    }

    /**
     * Check if a validate item List is valid
     * @param validateList A list of 2 dotpaths that either one or the other can be in the config but at least one
     * @return String with or list of validation items, successful validation returns empty String
     */
    @NonCPS
    String validateListItem(List<String> validateList) {
        String re
        List<String> orList = []
        validateList.each { orItem ->
            String value = evalPath(config, orItem)
            if (value) {
                orList += value
            }
        }
        if (orList.isEmpty()) {
            re = "\nMust contain one of: ${validateList.join(' or ')}"
        } else {
            re = ''
        }
        re
    }

    /**
     * Check if a validate item String is valid
     * @param validateString The dotpath string to validate in the config e.g. checkmarx.settings
     * @return String of validation item, successful validation returns empty String
     */
    @NonCPS
    String validateStringItem(String validateString) {
        String re
        if (evalPath(config, validateString)) {
            re = ''
        } else {
            re = validateString
        }
        re
    }

    /**
     * Return the branch for the current pipeline run
     *
     * @return The branch name for the current pipeline run
     */
    String gitBranch() {
        script.scm.branches[0].name
    }

    /**
     * Return the url for the current pipeline run
     *
     * @return The url for the current pipeline run
     */
    String gitUrl() {
        script.scm.userRemoteConfigs[0].url
    }


    /**
     * Determine if the current configuration uses JUnit
     * @return Boolean indicating if the config uses JUnit
     */
    boolean usesJunit(Map<String, Object> targetConfig = config) {
        targetConfig.containsKey('junit')
    }

    /**
     * Executes the junit step using class-based defaults.
     */
    void publishJunit(
        Map<String, Object> targetConfig = config,
        String glob = defaultJunitGlob,
        boolean allowEmpty = defaultJunitAllowEmpty,
        Double healthScaleFactor = defaultJunitHealthScaleFactor
    ) {
        script.junit(
            testResults: targetConfig.junit.get('testResults', glob),
            allowEmptyResults: targetConfig.junit.get('allowEmptyResults', allowEmpty),
            healthScaleFactor: targetConfig.junit.get('healthScaleFactor', healthScaleFactor)
        )
    }

    /**
     * Determine if the current configuration uses Warnings NG
     * @return Boolean indicating if the config uses Warnings NG
     */
    boolean usesWarningsNG(Map<String, Object> targetConfig = config) {
        targetConfig.containsKey('warningsNG')
    }

    /**
     * Executes the recordIssues step using class-based defaults.
     */
    void publishWarningsNG(Map<String, Object> targetConfig = config) {
        script.recordIssues(targetConfig.warningsNG)
    }


    Phase(Map<String, Object> config = [:]) {
        this.config = config
    }

    /*
     * Update container's aws-fed version to the version given in the configuration.
     *
     * @param awsFedVersion The version of awsFedVersion you would like to install
     */

    protected void updateAwsFed(String awsFedVersion) {
        if (awsFedVersion) {
            script.sh(
                'curl -L https://github.sys.cigna.com/cigna/aws-fed/releases/download/'
                    + "v${awsFedVersion}/aws-fed_${awsFedVersion}_linux.zip > aws-fed.zip "
                    + '&& unzip -o aws-fed.zip && mv ./aws-fed /usr/local/bin/aws-fed '
                    + '&& chmod +x /usr/local/bin/aws-fed'
            )
        }
    }

    /*
     * run aws-fed to federate with the given credentials, account, and rolename
     *
     * @param awsConfig Map of the awsFed options from the user provided config
     *        Should include credentialsId (the username and password Jenkins credentials ID to use),
     *        account (the AWS account to federate into), and rolename (the AWS role to federate into)
     */

    protected void awsFed(Map<String, String> awsConfig, String containerToUse = '') {
        if (awsConfig?.credentialsId && awsConfig?.account && awsConfig?.rolename) {
            String region = awsConfig?.region ?: 'us-east-1'
            script.withCredentials(
                [script.usernamePassword(
                    credentialsId: awsConfig.credentialsId,
                    usernameVariable: 'AWS_FED_USERNAME',
                    passwordVariable: 'AWS_FED_PASSWORD'
                )]
            ) {
                Closure fedSteps = {
                    String exportEnv = """\
                        |export AWS_FED_PASSWORD=${script.AWS_FED_PASSWORD}
                        |export AWS_FED_USERNAME=${script.AWS_FED_USERNAME}
                        |export AWS_REGION=${region}
                    """.stripMargin()
                    script.sh(exportEnv)
                    script.sh(
                        "aws-fed add --nickname ci --account ${awsConfig.account} --role "
                            + "${awsConfig.rolename}"
                    )
                    script.sh('aws-fed login ci'.stripMargin())
                }
                if (containerToUse) {
                    psc.podSelector.select(psc, containerToUse, Utils.cloud(config)) {
                        fedSteps()
                    }
                } else {
                    fedSteps()
                }
            }
        }
    }

    def awsLogin(Closure codeToRunAfter = {}) {
        if (isRunningInEKS(config.cloudName) && !config.runInAWS) {
            config.runInAWS = true
        }

        boolean aws = config.containsKey('aws')
        boolean awsSaml = (config.containsKey('aws') && config.aws.get('saml', false)) ||
            config.containsKey('saml2aws')

        // XXX.cnm - awsFed is deprecated but until we officially announce it has been removed
        // we need to preserve the current functionality
        if (!awsSaml && !config.runInAWS && !aws && (!config.containsKey('awsFed') || config.awsFed == null)) {
            codeToRunAfter()
            return
        }

        if (awsSaml) {
            Map<String, String> samlConfig = config.containsKey('saml2aws') ? [
                credentialsId: config?.saml2aws?.credentialsId,
                arn          : config?.saml2aws?.saml2awsRoleArn
            ] :
                [
                    credentialsId: config?.aws?.credentialsId,
                    account      : config?.aws?.account,
                    rolename     : config?.aws?.rolename,
                    region       : config?.aws?.region,
                ]
            saml2Aws(samlConfig, containerName, codeToRunAfter)
        } else if (aws) {
            awsAssumeRole(config?.aws as Map<String, String>)
            codeToRunAfter()
        } else if (config.containsKey('awsFed')) {
            script.echo("+-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=+")
            script.echo("| YOU ARE USING DEPRECATED PIPELINE FEATURES.                                                                            |")
            script.echo("| THIS SERVES AS YOUR NOTICE THAT YOUR PIPELINE WILL FAIL ONCE THE CODE IS REMOVED FROM THE LIBRARY.                     |")
            script.echo("| aws Fed has been phased out. In order to use saml2aws, please enable Okta federation by following these instructions:  |")
            script.echo("| https://confluence.sys.cigna.com/display/CLOUD/Service+Accounts+-+USM#ServiceAccountsUSM-ExistingServiceAccount        |")
            script.echo("+-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=+")
            updateAwsFed(config?.awsFed?.version)
            boolean callPlzFed = config.awsFed.get('callPlzFed', true)
            // pains me to have such phase specific logic here, but we will be removing all support for aws/plz fed soon
            if (callPlzFed && !config.containsKey('terraform')) {
                PlzUtils.plzAwsFed(script, config, codeToRunAfter)
            } else {
                awsFed(config.awsFed)
                codeToRunAfter()
            }

        }
    }
    /*
     * run saml2aws to federate with the given account, and rolename
     *
     * @param awsConfig Map of the aws map options from the user provided config
     *        Should include the target account role arn (which comes from the account within the aws-fed.toml
     *        which is the account number and what AWS will use to federate), and rolename
     *        (the AWS role to federate into)
     */

    protected void saml2Aws(Map<String, String> awsConfig, String containerToUse = '', codeToRunAfter = {}) {
        def accountValue = Utils.ifNull(awsConfig.targetAccount, awsConfig.account)
        def roleValue = Utils.ifNull(awsConfig.accountRolename, awsConfig.rolename)
        def arn = awsConfig?.arn ?: "arn:aws:iam::${accountValue}:role/${roleValue}"
        def region = awsConfig?.region ?: 'us-east-1'
        def domain = awsConfig?.domain ?: 'INTERNAL'
        if (isValidConfiguration(awsConfig, accountValue, roleValue)) {
            script.echo('Using saml2aws...')
            script.withCredentials(
                [script.usernamePassword(
                    credentialsId: awsConfig.credentialsId,
                    usernameVariable: 'AWS_USERNAME',
                    passwordVariable: 'AWS_PASSWORD'
                )]
            ) {
                String awsUsername = script.AWS_USERNAME
                String username = awsUsername.contains('\\') ? awsUsername : "${domain}\\${awsUsername}"

                if (FeatureFlags.debug) {
                    script.echo("--> saml2aws debug: account: $accountValue, role: $roleValue, region: $region, domain: $domain, arn:$arn")
                }
                Closure samlSteps = {
                    String exportEnv = """\
                        |export SAML2AWS_PASSWORD='${script.AWS_PASSWORD}'
                        |export SAML2AWS_USERNAME='$username'
                        |export SAML2AWS_ROLE='$arn'
                        |export SAML2AWS_PROFILE=saml
                        |export SAML2AWS_MFA=PUSH
                        |export SAML2AWS_IDP_PROVIDER=Okta
                        |export SAML2AWS_REGION='${region}'
                        |export SAML2AWS_OKTA_DISABLE_SESSIONS=true
                        |export SAML2AWS_URL=https://cigna.okta.com/home/cigna_awsaccountfederation_1/0oa73k6r3qgarO3uY4x7/aln73kc6ziVIaLcRj4x7
                        |export AWS_PROFILE=saml
                        |export AWS_DEFAULT_PROFILE=saml
                    """.stripMargin()
                    String samlLogin = 'saml2aws login --skip-prompt --force'
                    if (config.containsKey('ecr')) {
                        if (config?.packagingType in ['podman']) {
                            script.sh(exportEnv
                                + "\n${samlLogin}"
                                + "\naws ecr get-login-password --region ${region} --profile saml | podman login -u AWS --password-stdin ${accountValue}.dkr.ecr.${region}.amazonaws.com --authfile=${script.env.WORKSPACE}/.containers/auth.json"
                            )
                            // the cred file might be created by a user that other containers don't have, so make the cred file world-readable
                            script.sh("chmod +x ${script.env.WORKSPACE}/.containers")
                            script.sh("chmod -R +r ${script.env.WORKSPACE}/.containers")
                        } else {
                            // ecr saves password to home/jenkins/.docker/config.json then move into mount to be avaiable to kaniko container from cloudkit
                            script.sh(exportEnv
                                + "\n${samlLogin}"
                                + "\naws ecr get-login-password --region ${region} --profile saml | docker login -u AWS --password-stdin ${accountValue}.dkr.ecr.${region}.amazonaws.com"
                                + '\nmv /home/jenkins/.docker/config.json /home/jenkins/.ecr/credentials'
                            )
                        }
                    } else {
                        // all other phases but packaging use the common .aws/credentials file to authenticate via saml
                        script.sh(exportEnv
                            + "\n${samlLogin}"
                        )
                    }

                    codeToRunAfter()
                }

                if (containerToUse) {
                    psc.podSelector.select(psc, containerToUse, Utils.cloud(config)) {
                        samlSteps()
                    }
                } else {
                    samlSteps()
                }
            }
        } else {
            throwException("Unable to perform saml2aws due to missing configuration: ${awsConfig}")
        }
    }

    protected boolean isValidConfiguration(def awsConfig, def accountValue, def roleValue) {
        awsConfig.credentialsId && (accountValue || !config.containsKey('ecr')) && (roleValue || awsConfig.arn)
    }

    /*
     * Run script to assume the aws role provided
     *
     * @param awsConfig Map of the aws options from the user provided config
     *        Should include targetAccount (the aws account in which the targeted resources are located)
     *        and accountRoleName (the AWS role to assume that has access to the resources in the targetAccount)
     */

    protected void awsAssumeRole(Map<String, String> awsConfig) {
        if (!awsConfig) {
            return
        }

        String targetAccountRoleARN = AWSUtils.getTargetAccountRoleARN(awsConfig)
        if (targetAccountRoleARN) {
            String timestamp = java.time.LocalDateTime.now()
            String awsCredentialsFilePath = "\$HOME/.aws/credentials"
            String awsRegion = awsConfig?.region ?: 'us-east-1'
            String exportScript = """#!/bin/sh -e${awsConfig?.debug ? 'x' : ''}
                unset AWS_DEFAULT_PROFILE
                unset AWS_PROFILE
                echo 'Initial Role:'
                aws sts get-caller-identity
                aws sts assume-role \
                --role-arn ${targetAccountRoleARN} \
                --role-session-name "${script.env.GIT_COMMIT_SHORT}.${timestamp.replaceAll(':', '.')}" > data.json

                export AWS_ACCESS_KEY_ID="`cat data.json | jq ".Credentials.AccessKeyId" -r`"
                export AWS_SECRET_ACCESS_KEY="`cat data.json | jq ".Credentials.SecretAccessKey" -r`"
                export AWS_SESSION_TOKEN="`cat data.json | jq ".Credentials.SessionToken" -r`"
                echo 'Assumed Role:'
                aws sts get-caller-identity
            """

            script.sh(exportScript
                + '\nmkdir -p $HOME/.aws'
                + "\necho '[saml]' > ${awsCredentialsFilePath}"
                + "\necho 'ouput = json' >> ${awsCredentialsFilePath}"
                + "\necho 'region = ${awsRegion}' >> ${awsCredentialsFilePath}"
                + '\necho "aws_access_key_id = $AWS_ACCESS_KEY_ID" >> ' + "${awsCredentialsFilePath}"
                + '\necho "aws_secret_access_key = $AWS_SECRET_ACCESS_KEY" >> ' + "${awsCredentialsFilePath}"
                + '\necho "aws_session_token = $AWS_SESSION_TOKEN" >> ' + "${awsCredentialsFilePath}"
            )
        }
    }

    protected void moveFiles(String phaseTime) {
        boolean phaseCache = config?.phaseCache ?: false
        if (phaseCache) {
            switch (phaseTime) {
                case 'begin':
                    StashUtils.unstash(script, psc)
                    break
                case 'end':
                    script.env.STASH_ID = StashUtils.stash(script, config, psc, stashIncludePattern, stashExcludePattern)
                    break
            }
        } else {
            script.echo("Phase caching is disabled, not stashing files.")
        }
    }

    def currentBranchMatches(def targetBranch) {
        Utils.branchMatchesPattern(gitBranch(), targetBranch)
    }

    /**
     * Send customized email. This step is optional for users to send the test execution reports/custom emails if emailrecipient is provided.
     */
    void sendTailoredEmail() {
        if (config.email && config.email.recipients && currentBranchMatches(config.email.branchPattern)) {
            String emailBody = config.email?.body ?: "Hi, \n\nPlease find the EPF pipeline execution details.\n\n"
            String emailSubject = config.email?.subject ?: "EPF pipeline execution JOB: ${script.env.JOB_BASE_NAME}; BUILD_NUMBER: [${script.env.BUILD_NUMBER}];"

            def hasZip = config.email.containsKey('zip')
            if (hasZip) {
                if (!config.email.zip.containsKey('folder') || !config.email.zip.containsKey('name')) {
                    script.echo("WARNING: Cannot construct report zipfile as the 'email.zip' element needs both 'folder' and 'name' elements")
                    hasZip = false
                } else {
                    zipFiles(config.email.zip)
                }
            }
            script.emailext(
                to: config.email.recipients,
                subject: emailSubject,
                attachmentsPattern: hasZip ? "**/${config.email.zip.name}" : config.email?.attachmentFile,
                body: "${emailBody}\n\nBuild URL: ${script.env.BUILD_URL}".replace('\n', '<br>'),
                mimeType: 'text/html'
            )
        } else {
            if (FeatureFlags.verbose && config.email) {
                script.echo("Not sending email (recipients=${config.email.recipients}, " +
                    "currentBranchMatches=${currentBranchMatches(config.email.branchPattern)})")
            }
        }
    }

    String generateStageName(defaultStageName = '') {
        return config?.stageName ?: defaultStageName
    }

    /**
     * If `addonEnv` + `config.withEnv` is non-empty, call script.withEnv with that env list and `closure`,
     * otherwise (no extra env required), just call `closure`
     * @param addonEnv a list of strings describing env vars (['MY_VAR=MY_VALUE'])
     * @param closure a closure to be executed with `addonEnv + config.withEnv` present in the environment
     */
    void withPhaseConfigEnv(List addonEnv = [], Closure closure) {
        List completeEnvList = (addonEnv ?: []) + (config.withEnv ?: [])
        if (completeEnvList)
            script.withEnv(completeEnvList) {
                closure()
            }
        else
            closure()
    }


    @NonCPS
    static UnsupportedOperationException error(String msg) {
        return new UnsupportedOperationException(msg)
    }

    void tagCommit() {
        // TAG the commit if required
        if (config?.tagDetails) {
            String gitCurrentBranch = gitBranch()
            String tagBranch = "${config.tagDetails.branch}"
            String gitURL = gitUrl()
            String tagVersion = config.tagDetails.containsKey('tagFile') && !config.tagDetails.tagFile?.isEmpty() ?
                script.readFile(config.tagDetails.tagFile) : config.tagDetails.tag
            tagVersion = tagVersion.trim()
            String message = config.tagDetails.message ?: "Version ${tagVersion}"
            Boolean isTracing = config.tagDetails.containsKey('trace') ? config.tagDetails.trace : false
            Boolean forceFlag = config.tagDetails.containsKey('force') ? config.tagDetails.force : false

            if (gitURL.contains('https://')) {
                script.withCredentials([
                    script.gitUsernamePassword(
                        credentialsId: "${config.tagDetails.gitTagCredKey}",
                        gitToolName: 'git-tool')
                ]) {
                    if (gitCurrentBranch ==~ /$tagBranch/) {
                        script.echo("Branch '${gitCurrentBranch}', Matched a branch to tag '${tagVersion}'.")
                        script.sh(
                            "git config --worktree user.email '${script.GIT_USERNAME}@cigna.com' "
                                + "&& git config --worktree user.name '${script.GIT_USERNAME}'"
                        )
                        String prefix = config.tagDetails.get('prefix', '')
                        if (!prefix.empty) {
                            script.echo("Using tag prefix '${prefix}'")
                            tagVersion = prefix + tagVersion
                        }

                        script.sh(
                            "${isTracing ? 'GIT_TRACE=1 ' : ''}git tag ${forceFlag ? '-f ' : ''}-a '${tagVersion}' -m '${message}'"
                        )
                        script.echo("Pushing tag ${tagVersion} to repository ${gitURL}")
                        script.sh("${isTracing ? 'GIT_TRACE=1 ' : ''}git push ${forceFlag ? '-f ' : ''}-v '${gitURL}' --tags")
                    } else {
                        script.echo('Build of a non tag matching branch, not making a git release tag..')
                    }
                }
            } else {
                script.echo('Git URL doesnt appear to be https.')
                throw error("Git URL ${gitURL} does not use https protocol")
            }
        }
    }

    void zipFiles(Map zipConfig) {
        script.zip(
            zipFile: zipConfig.name,
            dir: zipConfig.folder,
            defaultIncludes: zipConfig.get('includes', '**/*'),
            defaultExcludes: zipConfig.get('excludes', ''),
            archive: zipConfig.get('archive', false),
            overwrite: zipConfig.get('overwrite', true),
        )
        if (FeatureFlags.verbose) {
            script.echo("Zip file ${zipConfig.name} created with files from ${zipConfig.folder}")
        }
    }
}
