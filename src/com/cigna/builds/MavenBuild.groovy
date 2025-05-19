package com.cigna.builds

import com.cigna.common.kubernetes.PodTemplateCreator

/**
 * For builds utilizing JDK/Maven
 */
class MavenBuild extends Build {
    public static final String MAVEN_SETTINGS_CONFIG_FILE_ID = '2c5fdcc7-2376-4dbf-b252-a3a8e83603d9'
    public static final String MAVEN_SETTINGS_CONFIG_FILE_ID_ESI = '164ccd50-bd09-47bf-a2ad-c59f6acb903c'
    public static final String MAVEN_AUTH_SETTINGS_CONFIG_ID = 'maven-auth-settings'
    public static final String DEFAULT_MAX_CONTAINER_MEMORY = '2000Mi'
    public static final String MAVEN_MODULE_SCOPE = 'epf-build-maven'
    //TODO: Set to Prod Artifactory settings for GA release
    private String mavenRepoPrefix
    private String mavenSettingsId
    private String extraArgs
    private String publishArgs
    private Boolean mavenAuth
    private Boolean configSettings

    MavenBuild() {
        containerName = 'mavenv200-v399-8-eclipse-temurin'
        containerImage = 'enterprise-devops/maven'
        containerVersion = '2.0.0-v3.9.9-8-eclipse-temurin'
        additionalValidationItems = ['artifactory.credentialsId']
        stashIncludePattern = '**/target/*.jar,**/target/*.war,**/target/*.ear'
    }

    String pathToPom = './pom.xml'
    // Reference https://maven.apache.org/enforcer/enforcer-rules/index.html
    String enforcerRules = (
        'banDuplicatePomDependencyVersions,bannedPlugins'
            + ',bannedDependencies,bannedRepositories,requireReleaseDeps'
    )
    String enforcerPluginVersion = '3.0.0-M3'
    String deployArgs = 'deploy'
    String cvssScore = '9.0'
    // https://www.mojohaus.org/versions-maven-plugin/set-mojo.html
    String versionChangeParams = '-DnextSnapshot=true'
    Closure<String> deriveAuthSettings = { ->
        mavenAuth ? "-Duserid='${script.ARTIFACTORY_USER}' -Dapikey='${script.ARTIFACTORY_APIKEY}'" : ''
    }

    String defaultRepoPath = '/var/maven/.m2/repository'

    // BUG # - test classpath not resolved if `-f ./pom.xml` is used
    Closure<String> derivePomPathSettings = { ->
        pathToPom == './pom.xml' ? '' : "-f '${pathToPom}' "
    }

    @Override
    Boolean postPodConfig() {
        /**
         * Defect CNPT-948
         *
         * For a full description of this defect and why this fix mitigates it, please see the comment in the
         * DockerPipelineLib base postPodConfig() method.
         *
         * This specific override is to avoid having logic in the base class that alters the decision based upon
         * detecting if the child class is one of the known instances of a phase that cannot run in the same pod
         * as a phase that requires a serviceAccount.
         */
        if (!config.containsKey('podGroup')) {
            // The base class also checks if there is an extraConfigs element, but with the Maven build, we have
            // the settings.xml file injected from Jenkins managed files and so we must move ourselves to a separate
            // pod, regardless of whether we have extraConfigs or not.
            if (psc.podSelector.RunsWithPrivilegedContainer(config)) {
                script.echo("Phase requires access to managed files and is running in a privileged container - " +
                    "moving to 'managed' pod group")
                config.podGroup = 'managed'
            }
        }
        super.postPodConfig()
    }

    @Override
    Boolean prePodConfig() {
        if (config.containsKey('maven') && config.maven.containsKey('m2RepoPath')) {
            defaultRepoPath = config.maven.m2RepoPath
        }

        List<Map> env = []

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
        containerTemplate.addVolumeMount(containerName,'setup-sonar', '/home/jenkins/agent/setup-sonar')
        additionalPodConfig = [
            volumes   : [],
            containers: [containerTemplate.getContainer(containerName)]
        ]

        super.prePodConfig()
    }

    @Override
    void executePreBuildStage() {
        commonPreBuildSteps()
        mavenAuth = config.maven?.authSettings ?: false
        mavenRepoPrefix = ARTIFACTORY_URL - ('https://') - (ARTIFACTORY_URL.contains('cigna.jfrog')
            ? 'cigna.jfrog.io/artifactory' : 'repo.sys.cigna.com/artifactory')
        mavenSettingsId = mavenAuth ? MAVEN_AUTH_SETTINGS_CONFIG_ID : MAVEN_SETTINGS_CONFIG_FILE_ID
        script.echo("Maven Repo Prefix = ${mavenRepoPrefix}")
        pathToPom = config.maven?.pathToPom ?: pathToPom
        script.echo("Maven Path to POM = ${pathToPom}")
        deployArgs = config.maven?.deployArgs ?: deployArgs
        extraArgs = config.maven?.containsKey('extraArgs') ? " ${config.maven.extraArgs}" : ''
        script.echo("extraArgs =${extraArgs}")
        publishArgs = config.maven?.containsKey('publishArgs') ? " ${config.maven.publishArgs}" : ''
        script.echo("publishArgs =${publishArgs}")
    }

    @Override
    void executeBuildAndTestStage() {
        if (config?.maven?.debug) {
            mavenDebug()
        }

        if (config?.maven?.enforcer == true) {
            executeEnforcer()
        }

        // Allow people to update pom versions
        // https://www.mojohaus.org/versions-maven-plugin/set-mojo.html
        if (config?.maven?.alterVersions == true) {
            executeVersionUpdate()
        }

        script.configFileProvider([
            script.configFile(
                fileId: mavenSettingsId,
                variable: 'MAVEN_SETTINGS'
            )
        ]) {
            script.withCredentials([
                script.usernamePassword(
                    credentialsId: "${config.artifactory.credentialsId}",
                    usernameVariable: 'ARTIFACTORY_USER',
                    passwordVariable: 'ARTIFACTORY_APIKEY'
                )
            ]) {
                script.sh(
                    """mvn -B -s ${script.MAVEN_SETTINGS} \
                        ${derivePomPathSettings()} \
                        -Drepo.mgr.env='${mavenRepoPrefix}' \
                        -Dmaven.repo.local='${defaultRepoPath}' \
                        ${deriveAuthSettings()}${extraArgs} \
                        clean verify""".stripIndent()
                )


                def artifactPath = calculateArtifactPath()
                if (artifactPath && !artifactPath.isEmpty()) {
                    psc.metadata.put('artifact', artifactPath, MAVEN_MODULE_SCOPE, false)
                    psc.metadata.put('publishUrl', artifactPath, MAVEN_MODULE_SCOPE, false)
                }
            }
        }
    }

    @Override
    boolean shouldRunBuild() {
        return config.get('mavenBuildEnabled', config.get('buildEnabled', true))
    }

    @Override
    boolean shouldRunPublish() {
        if (!(config.get('mavenPublishEnabled', config.get('publishEnabled', true)))) {
            return false
        } else if (currentBranchIsReleaseBranch() || config?.hasSnapshotVersion) {
            return true
        } else {
            script.echo("releaseBranchPattern ${config.releaseBranchPattern ?: config.branchPattern} does not match " +
                "current branch ${gitBranch()} and hasSnapshotVersion is not true, so no publish steps are being taken.")
            return false
        }
    }

    @Override
    void executePublishStage() {
        String mavenReleaseSettings = ''
        if (config?.hasSnapshotVersion && this.currentBranchIsReleaseBranch()) {
            mavenReleaseSettings = 'release:prepare release:perform '
        } else {
            mavenReleaseSettings = 'deploy '
        }
        script.configFileProvider([
            script.configFile(
                fileId: mavenSettingsId,
                variable: 'MAVEN_SETTINGS'
            )
        ]) {
            script.withCredentials([
                script.usernamePassword(
                    credentialsId: "${config.artifactory.credentialsId}",
                    usernameVariable: 'ARTIFACTORY_USER',
                    passwordVariable: 'ARTIFACTORY_APIKEY'
                )
            ]) {
                script.sh(
                    """mvn -B -s ${script.MAVEN_SETTINGS} \
                        ${derivePomPathSettings()} ${mavenReleaseSettings} \
                        -DskipTests${publishArgs} \
                        -Drepo.mgr.env='${mavenRepoPrefix}' \
                        -Dmaven.repo.local='${defaultRepoPath}' \
                        ${deriveAuthSettings()} \
                        -Drelease-deployer-id='${script.ARTIFACTORY_USER}' \
                        -Drelease-deployer-pwd='${script.ARTIFACTORY_APIKEY}' \
                        -Dsnapshot-deployer-id='${script.ARTIFACTORY_USER}' \
                        -Dsnapshot-deployer-pwd='${script.ARTIFACTORY_APIKEY}'"""
                        .stripIndent()
                )

            }
        }
    }
    /**
     * This will allow for the triage and debugging of maven pom file issues and dependency resolution
     */
    void mavenDebug() {
        script.configFileProvider([
            script.configFile(
                fileId: mavenSettingsId,
                variable: 'MAVEN_SETTINGS'
            )
        ]) {
            script.withCredentials([
                script.usernamePassword(
                    credentialsId: "${config.artifactory.credentialsId}",
                    usernameVariable: 'ARTIFACTORY_USER',
                    passwordVariable: 'ARTIFACTORY_APIKEY'
                )
            ]) {
                script.echo('============ BEGIN MAVEN DEBUG ============')

                script.sh(
                    """mvn -B -s ${script.MAVEN_SETTINGS} \
                    ${derivePomPathSettings()} help:effective-settings \
                    -Doutput=effective-settings.xml \
                    -Drepo.mgr.env='${mavenRepoPrefix}' \
                    -Dmaven.repo.local='${defaultRepoPath}' \
                    ${deriveAuthSettings()} \
                    -Drelease-deployer-id='${script.ARTIFACTORY_USER}' \
                    -Drelease-deployer-pwd='${script.ARTIFACTORY_APIKEY}' \
                    -Dsnapshot-deployer-id='${script.ARTIFACTORY_USER}' \
                    -Dsnapshot-deployer-pwd='${script.ARTIFACTORY_APIKEY}'"""
                        .stripMargin()
                )

                script.echo('Effective Settings:')
                script.sh('cat effective-settings.xml')

                script.sh(
                    """mvn -B -s ${script.MAVEN_SETTINGS} \
                    ${derivePomPathSettings()} help:effective-pom \
                    -Doutput=effective-pom.xml \
                    -Drepo.mgr.env='${mavenRepoPrefix}' \
                    -Dmaven.repo.local='${defaultRepoPath}' \
                    """.stripMargin()
                )

                script.echo('Effective POM:')
                script.sh('cat effective-pom.xml')

                script.echo('============ END MAVEN DEBUG ============')
            }
        }
    }

    void executeEnforcer() {
        script.configFileProvider([
            script.configFile(
                fileId: mavenSettingsId,
                variable: 'MAVEN_SETTINGS'
            )
        ]) {
            script.withCredentials([
                script.usernamePassword(
                    credentialsId: "${config.artifactory.credentialsId}",
                    usernameVariable: 'ARTIFACTORY_USER',
                    passwordVariable: 'ARTIFACTORY_APIKEY'
                )
            ]) {
                enforcerRules = config.maven?.enforcerRules ?: enforcerRules

                script.echo('============ BEGIN MAVEN ENFORCER PLUGIN ============')

                script.sh(
                    """mvn -B -s ${script.MAVEN_SETTINGS} \
                    org.apache.maven.plugins:maven-enforcer-plugin:${enforcerPluginVersion}:enforce \
                    ${derivePomPathSettings()} \
                    -Drules=${enforcerRules} -Dmaven.repo.local='${defaultRepoPath}'"""
                        .stripMargin()
                )

                script.echo('============ END MAVEN ENFORCER PLUGIN ============')
            }
        }
    }
    /**
     * Allows a user to execute version change commands based on
     * https://www.mojohaus.org/versions-maven-plugin/set-mojo.html
     */
    void executeVersionUpdate() {
        script.configFileProvider([
            script.configFile(
                fileId: mavenSettingsId,
                variable: 'MAVEN_SETTINGS'
            )
        ]) {
            script.withCredentials([
                script.usernamePassword(
                    credentialsId: "${config.artifactory.credentialsId}",
                    usernameVariable: 'ARTIFACTORY_USER',
                    passwordVariable: 'ARTIFACTORY_APIKEY'
                )
            ]) {
                versionChangeParams = config.maven?.versionChangeParams ?: versionChangeParams

                script.echo('============ BEGIN MAVEN VERSION PLUGIN ============')

                script.sh(
                    """mvn -B -s ${script.MAVEN_SETTINGS} \
                    versions:set ${versionChangeParams} ${derivePomPathSettings()}\
                     -Dmaven.repo.local='${defaultRepoPath}'"""
                        .stripMargin()
                )

                script.echo('============ END MAVEN VERSION PLUGIN ============')
            }
        }
    }

    private String calculateArtifactPath() {
        def prefix = "mvn ${derivePomPathSettings()} -s \$MAVEN_SETTINGS -Dmaven.repo.local='${defaultRepoPath}' org.apache.maven.plugins:maven-help-plugin:3.2.0:evaluate"
        String groupId = script.sh(returnStdout: true, script: prefix + ' -Dexpression=project.groupId -q -DforceStdout')
        String artifactId = script.sh(returnStdout: true, script: prefix + ' -Dexpression=project.artifactId -q -DforceStdout')
        String version = script.sh(returnStdout: true, script: prefix + ' -Dexpression=project.version -q -DforceStdout')
        String packaging = script.sh(returnStdout: true, script: prefix + ' -Dexpression=project.packaging -q -DforceStdout')

        psc.metadata.put('version', version, MAVEN_MODULE_SCOPE, false)
        psc.metadata.put('groupId', groupId, MAVEN_MODULE_SCOPE, false)
        psc.metadata.put('artifactId', artifactId, MAVEN_MODULE_SCOPE, false)
        psc.metadata.put('packaging', packaging, MAVEN_MODULE_SCOPE, false)

        if (groupId?.isEmpty() == false) {
            "${ARTIFACTORY_URL}/${ARTIFACTORY_ROOT}${groupId.replaceAll('\\.', '/')}/$artifactId/$version/$artifactId-$version${packaging ? ".${packaging}" : ''}"
        } else {
            ''
        }
    }
}
