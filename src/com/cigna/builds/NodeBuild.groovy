package com.cigna.builds

import com.cigna.common.utils.FeatureFlags
import hudson.Functions
import com.cigna.common.kubernetes.PodTemplateCreator

/**
 * For builds utilizing NPM (Javascript).
 */
class NodeBuild extends Build {
    String utility = 'node'
    String configDir = '/tmp/node_modules'
    String symLinkDir = '$WORKSPACE/node_modules'

    NodeBuild() {
        additionalValidationItems += [
                'artifactory.credentialsId',
        ]
        containerName = 'nodevlts-alpine'
        containerImage = 'enterprise-devops/node'
        containerVersion = 'lts-alpine'
        containerMemory = '1000Mi'
        containerCpu = '1000m'
        stashIncludePattern = ''
    }

    @Override
    Boolean prePodConfig() {

        List<Map> env = [
            [
                name : 'HOME',
                value: '/tmp'
            ],
            [
                name : 'YARN_CACHE_FOLDER',
                value: '/tmp/.cache/yarn'
            ],
            [
                name : 'npm_config_cache',
                value: '/tmp/.cache/npm'
            ],
            [
                name : 'NODE_PATH',
                value: configDir
            ]
        ]

        PodTemplateCreator containerTemplate = PodTemplateCreator.newPodTemplateCreator()
        containerTemplate.addContainer(
            containerName,
            "${containerImage}:${containerVersion}",
            100,
            1000,
            500,
            1000,
            env
        )
        containerTemplate.addVolumeMount(containerName, 'setup-sonar', '/home/jenkins/agent/setup-sonar')

        additionalPodConfig = [
                'volumes'   : [],
                'containers': [containerTemplate.getContainer(containerName)]
        ]
        super.prePodConfig()
    }

    final String defaultPath = '/npm/npm-repos'
    final String nxRepoPath = '/npm/npm-nx-repos'
    final String filesToDeleteBeforePublish = '*.md .gitlab-ci.yml Jenkinsfile Dockerfile'
    boolean checkForTypeScript = true

    List defaultCommands = [
            'install',
            'test'
    ]
    /**
     * Runs npm install then calls npm test with SONAR_PARAMS environment variable set that is the
     * string of all SonarQube scanner options while in parallel running a checkmarx scan
     * and if the runAudit is somevalue then run the audit step
     */
    @Override
    void executeBuildAndTestStage() {
        def useLocalStorage = config.get('useLocalStorage', false)
        if (useLocalStorage) {
            initializeNodeModulesConfig()
        }
        initializeRegistryAndScope(defaultPath)

        (config.containsKey('commands') ? config.commands : defaultCommands).each { cmd ->
            script.sh("$utility $cmd")
        }

        // Yes we audit after everything runs but this way the lockfile is always generated
        if (config?.runAudit) {
            script.sh("${utility} audit")
        }
    }

    /**
     * If config.branchPattern matches the current branch set the Cigna NPM Artifcatory
     * registry URL, get the token from artifactory and add that to the .npmrc file, set the
     * scope if there is an @ sign un config.artifactory.applicationName, update the name of
     * the application to match config.artifactory.applicationName, and publish via npm publish
     */
    @Override
    void executePublishStage() {
        String applicationVersion = checkNames(config.artifactory)

        try {
            checkIfApplicationNameAndVersionUsedInArtifactory(applicationVersion)
        } catch (all) {
            script.echo(
                    'Artifactory package already exists, skipping upload to Artifactory ' + "${applicationVersion}"
            )
            if (FeatureFlags.showStackTraces) {
                script.echo(Functions.printThrowable(all))
            }

            return
        }
        // Execute a publish per path for the two repos
        try {
            runPublishForPath(nxRepoPath, config.artifactory)
            return
        } catch (all) {
            script.echo(
                    'Your deployer ID does not have access to ' + nxRepoPath + '. Proceeding to use default.'
            )
            if (FeatureFlags.showStackTraces) {
                script.echo(Functions.printThrowable(all))
            }

        }
        runPublishForPath(defaultPath, config.artifactory)
    }
    /**
     * Sets and runs the utility config set registry value for the code execution to use
     */
    String initializeRegistryAndScope(String path = defaultPath) {
        String npmArtifactoryRegistryUrl = ARTIFACTORY_API_URL + path

        script.sh(utility + ' config set strict-ssl false')
        script.sh(utility + " config set registry ${npmArtifactoryRegistryUrl}")
        script.echo(
                "Setting registry to be ${npmArtifactoryRegistryUrl}"
        )
        String artifactoryScope = artifactoryApplicationNameScope()
        if (artifactoryScope) {
            script.sh(utility + " config set scope ${artifactoryScope}")
            script.sh(utility + " config set ${artifactoryScope}:registry ${npmArtifactoryRegistryUrl}")
            script.echo(
                    "Setting scope to be ${artifactoryScope} And scope registry to be ${npmArtifactoryRegistryUrl}"
            )
        }
        artifactoryScope
    }
    /**
     * Based on the scope passed in change the authenticating url
     */
    void authenticate(String scope = '', Map<String, String> artifactoryConfig) {
        scope = scope ?: 'cigna'
        String npmArtifactoryRegistryAuthUrl = ARTIFACTORY_API_URL + '/npm/auth'
        if (scope && config?.enableScopedAuth != false) {
            npmArtifactoryRegistryAuthUrl = ARTIFACTORY_API_URL + "/npm/npm-repos/auth/$scope"
        }
        script.echo('Authenticating to  ' + "${npmArtifactoryRegistryAuthUrl}")
        script.withCredentials([script.usernamePassword(
                credentialsId: "${artifactoryConfig.credentialsId}",
                passwordVariable: 'artifactoryDeployerIdToken',
                usernameVariable: 'artifactoryDeployerIdName')]
        ) {
            // Use .npmrc regardless of packaging tool as yarn will use .npmrc if one is found in cwd.
            // the reason for this is that yarn uses a json format and the auth details returned by Artifactory
            // are in .npmrc format.
            script.httpRequest(
                    url: "${npmArtifactoryRegistryAuthUrl}",
                    customHeaders: [
                            [
                                    maskValue: true,
                                    name     : 'X-JFrog-Art-Api',
                                    value    : "${script.artifactoryDeployerIdToken}"
                            ]
                    ],
                    outputFile: "./.npmrc"
            )
        }
    }
    /**
     * Runs the publish process for a specific artifactory npm path repo
     */
    void runPublishForPath(String path = defaultPath, Map<String, String> artifactoryConfig) {
        script.echo('runPublishForPath ' + "${path}")

        String artifactoryScope = initializeRegistryAndScope(path)

        authenticate(artifactoryScope, artifactoryConfig)

        addGeneratedFilesToGitignore()

        script.sh("rm -f ${filesToDeleteBeforePublish}")


        def publishPath = config.publishPath ? "--cwd ${config.publishPath} " : (config.packageJsonPath ? "--cwd ${config.packageJsonPath} " : '')
        script.sh("${utility} ${publishPath}publish")
    }
    /**
     * Checks the application name inside the Jenkinsfile against the application name in the package.json
     */
    String checkNames(Map<String, String> artifactoryConfig) throws UnsupportedOperationException {
        def packageJsonPath = config.packageJsonPath ?: '.'
        if (!artifactoryConfig.credentialsId) {
            throw new UnsupportedOperationException(
                    'artifactory.credentialsId configuration option is mandatory'
            )
        }

        def filePath = "$packageJsonPath/package.json"
        def packageJson = [:]
        if (script.fileExists(filePath)) {
            packageJson = script.readJSON(file: filePath)
        }
        // if we found the package.json file and it contains a name
        if (packageJson.containsKey('name') && packageJson?.name?.trim()) {
            if (!(artifactoryConfig?.applicationName?.trim()) || artifactoryConfig?.applicationName?.trim() == null) {
                artifactoryConfig['applicationName'] = packageJson.name
            }

            if (!artifactoryConfig?.applicationName?.trim()?.equalsIgnoreCase(packageJson?.name?.trim())) {
                throw new UnsupportedOperationException(
                        "artifactory.applicationName (${artifactoryConfig.applicationName}) " +
                                "does not match application name in package.json (${packageJson?.name})"
                )
            }
        } else {
            if (!(artifactoryConfig?.applicationName?.trim()) || artifactoryConfig?.applicationName?.trim() == null) {
                throw new UnsupportedOperationException(
                        'Must have either artifactory.applicationName or a package.json name configured'
                )
            }
        }

        packageJson?.version
    }

    void initializeNodeModulesConfig() {
        script.sh("""
            if [ -h "$symLinkDir" ]; then
                rm "$symLinkDir"
            fi
            if [ ! -d $configDir ]; then
                mkdir -p $configDir
            fi
            cd \$WORKSPACE && ln -s $configDir node_modules
            """.stripMargin())
    }
}
