package com.cigna.builds

import com.cigna.common.phases.PodSelector
import com.cloudbees.groovy.cps.NonCPS
import com.cigna.common.kubernetes.PodTemplateCreator


import java.text.SimpleDateFormat

/**
 * For builds utilizing Dynatrace
 */
class DynatraceBuild extends Build {
    DynatraceBuild() {
        containerName = 'mavenv363-jq'
        containerImage = 'enterprise-devops/maven'
        containerVersion = '3.6.3-jq'
    }
    public static final String DYNATRACE_SETTINGS_CONFIG_FILE_ID = '2c5fdcc7-2376-4dbf-b252-a3a8e83603d9'
    public static final String DYNATRACE_AUTH_SETTINGS_CONFIG_ID = 'dynatrace-auth-settings'
    public static final String DEFAULT_MAX_CONTAINER_MEMORY = '2000Mi'
    //TODO: Set to Prod Artifactory settings for GA release
    private String dynatraceRepoPrefix
    private String dynatraceSettingsId
    private Boolean dynatraceAuth

    boolean runCheckmarx = false

    String stashIncludePattern = '**/target/*.tar.gz'
    String pathToPom = './pom.xml'
    String enforcerRules = (
        'banDuplicatePomDependencyVersions,bannedPlugins'
            + ',bannedDependencies,bannedRepositories,requireReleaseDeps'
    )
    String enforcerPluginVersion = '3.0.0-M3'

    // https://www.mojohaus.org/versions-maven-plugin/set-mojo.html
    String versionChangeParams = '-DnextSnapshot=true'
    String pattern = 'yyyy.MM.dd.HH.mm.ss'
    SimpleDateFormat simpleDateFormat =
        new SimpleDateFormat(pattern, Locale.ENGLISH)

    String date = simpleDateFormat.format(new Date())

    Closure<String> deriveAuthSettings = { ->
        dynatraceAuth ? "-Duserid=${script.ARTIFACTORY_USER} -Dapikey=${script.ARTIFACTORY_APIKEY}" : ''
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
            // The base class also checks if there is an extraConfigs element, but with the Dynatrace build, we have
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

        additionalPodConfig = [
            volumes   : [],
            containers: [containerTemplate.getContainer(containername)]
        ]

        super.prePodConfig()
    }

    @Override
    void executePreBuildStage() {
        commonPreBuildSteps()
        dynatraceRepoPrefix = ARTIFACTORY_URL - ( 'https://' ) - ( 'repo.sys.cigna.com/artifactory' )
        script.echo("Dynatrace Repo Prefix = ${dynatraceRepoPrefix}")
        pathToPom = "./${config.dynatrace?.pathToPom}" ?: pathToPom
        script.echo("Dynatrace Path to POM = ${pathToPom}")
        dynatraceAuth = config.dynatrace?.authSettings ?: false
        dynatraceSettingsId = dynatraceAuth ? DYNATRACE_AUTH_SETTINGS_CONFIG_ID : DYNATRACE_SETTINGS_CONFIG_FILE_ID
    }

    @Override
    void executeBuildAndTestStage() {
        if (config?.dynatrace?.debug) {
            dynatraceDebug()
        }

        if (config?.dynatrace?.enforcer == true) {
            executeEnforcer()
        }

        // Allow people to update pom versions
        // https://www.mojohaus.org/versions-maven-plugin/set-mojo.html
        if (config?.dynatrace?.alterVersions == true) {
            executeVersionUpdate()
        }

        if (config?.dynatrace?.releaseVersions == true) {
            executeReleaseVersionUpdate()
        }

        script.configFileProvider(
            [
                script.configFile(
                    fileId: dynatraceSettingsId,
                    variable: 'DYNATRACE_SETTINGS'
                )
            ]
        ) {
            script.withCredentials(
                [
                    script.usernamePassword(
                        credentialsId: "${config.artifactory.credentialsId}",
                        usernameVariable: 'ARTIFACTORY_USER',
                        passwordVariable: 'ARTIFACTORY_APIKEY'
                    )
                ]
            ) {
                script.sh('ls -ltr')
                script.sh('pwd')
                script.sh('cd /home/jenkins/agent/workspace')
                script.sh('ls -ltr')
                script.sh('pwd')
                script.sh("sed 's/COMMIT_ID/${script.env.GIT_COMMIT}/g' ${pathToPom} > " \
                      + "tmp.xml && mv tmp.xml ${pathToPom}")
                script.sh(
                    """mvn -B -s ${script.DYNATRACE_SETTINGS} \
                                           -f ${pathToPom} \
                                           -Drepo.mgr.env='${dynatraceRepoPrefix}' \
                                           -Dmaven.repo.local='/tmp/.cache/m2/repository' \
                                           ${deriveAuthSettings()} \
                                           clean verify""".stripIndent())
            }
        }
    }

    @Override
    void executePublishStage() {
        script.configFileProvider(
            [
                script.configFile(
                    fileId: dynatraceSettingsId,
                    variable: 'DYNATRACE_SETTINGS'
                )
            ]
        ) {
            script.withCredentials(
                [
                    script.usernamePassword(
                        credentialsId: "${config.artifactory.credentialsId}",
                        usernameVariable: 'ARTIFACTORY_USER',
                        passwordVariable: 'ARTIFACTORY_APIKEY'
                    )
                ]
            ) {
                script.sh(
                    """mvn -B -s ${script.DYNATRACE_SETTINGS} \
                                        -f ${pathToPom} deploy \
                                        -DskipTests \
                                        -Drepo.mgr.env='${dynatraceRepoPrefix}' \
                                        -Dmaven.repo.local='/tmp/.cache/m2/repository' \
                                        ${deriveAuthSettings()} \
                                        -Drelease-deployer-id=${script.ARTIFACTORY_USER} \
                                        -Drelease-deployer-pwd=${script.ARTIFACTORY_APIKEY} \
                                        -Dsnapshot-deployer-id=${script.ARTIFACTORY_USER} \
                                        -Dsnapshot-deployer-pwd=${script.ARTIFACTORY_APIKEY}"""
                        .stripIndent()
                )
            }
        }
    }
    /**
     * This will allow for the triage and debugging of dynatrace pom file issues and dependency resolution
     */
    void dynatraceDebug() {
        script.configFileProvider(
            [
                script.configFile(
                    fileId: dynatraceSettingsId,
                    variable: 'DYNATRACE_SETTINGS')
            ]
        ) {
            script.withCredentials(
                [
                    script.usernamePassword(
                        credentialsId: "${config.artifactory.credentialsId}",
                        usernameVariable: 'ARTIFACTORY_USER',
                        passwordVariable: 'ARTIFACTORY_APIKEY'
                    )
                ]
            ) {
                script.echo('============ BEGIN DYNATRACE DEBUG ============')

                script.sh(
                    """mvn -B -s ${script.DYNATRACE_SETTINGS} \
                    help:effective-settings \
                    -Doutput=effective-settings.xml \
                    -Drepo.mgr.env='${dynatraceRepoPrefix}' \
                    -Dmaven.repo.local='/tmp/.cache/m2/repository' \
                    ${deriveAuthSettings()} \
                    -Drelease-deployer-id=${script.ARTIFACTORY_USER} \
                    -Drelease-deployer-pwd=${script.ARTIFACTORY_APIKEY} \
                    -Dsnapshot-deployer-id=${script.ARTIFACTORY_USER} \
                    -Dsnapshot-deployer-pwd=${script.ARTIFACTORY_APIKEY}"""
                        .stripMargin()
                )

                script.echo('Effective Settings:')
                script.sh('cat effective-settings.xml')

                script.sh(
                    """mvn -B -s ${script.DYNATRACE_SETTINGS} \
                    help:effective-pom \
                    -Doutput=effective-pom.xml \
                    -Drepo.mgr.env='${dynatraceRepoPrefix}' \
                    -Dmaven.repo.local='/tmp/.cache/m2/repository' \
                    """.stripMargin()
                )

                script.echo('Effective POM:')
                script.sh('cat effective-pom.xml')

                script.echo('============ END DYNATRACE DEBUG ============')
            }
        }
    }

    void executeEnforcer() {
        script.configFileProvider(
            [
                script.configFile(
                    fileId: dynatraceSettingsId,
                    variable: 'DYNATRACE_SETTINGS')
            ]
        ) {
            script.withCredentials(
                [
                    script.usernamePassword(
                        credentialsId: "${config.artifactory.credentialsId}",
                        usernameVariable: 'ARTIFACTORY_USER',
                        passwordVariable: 'ARTIFACTORY_APIKEY'
                    )
                ]
            ) {
                enforcerRules = config.dynatrace?.enforcerRules ?: enforcerRules

                script.echo('============ BEGIN DYNATRACE ENFORCER PLUGIN ============')

                script.sh(
                    """mvn -B -s ${script.DYNATRACE_SETTINGS} \
                    org.apache.maven.plugins:maven-enforcer-plugin:${enforcerPluginVersion}:enforce \
                    -f ${pathToPom} \
                    -Drules=${enforcerRules} -Dmaven.repo.local='/tmp/.cache/m2/repository'"""
                        .stripMargin()
                )

                script.echo('============ END DYNATRACE ENFORCER PLUGIN ============')
            }
        }
    }
    /**
     * Allows a user to execute version change commands based on
     * https://www.mojohaus.org/versions-maven-plugin/set-mojo.html
     */
    void executeVersionUpdate() {
        script.configFileProvider(
            [
                script.configFile(
                    fileId: dynatraceSettingsId,
                    variable: 'DYNATRACE_SETTINGS')
            ]
        ) {
            script.withCredentials(
                [
                    script.usernamePassword(
                        credentialsId: "${config.artifactory.credentialsId}",
                        usernameVariable: 'ARTIFACTORY_USER',
                        passwordVariable: 'ARTIFACTORY_APIKEY'
                    )
                ]
            ) {
//                versionChangeParams = config.dynatrace?.versionChangeParams ?: versionChangeParams

                script.echo('============ BEGIN DYNATRACE VERSION PLUGIN ============')

                script.sh(
                    """mvn -B -s ${script.DYNATRACE_SETTINGS} \
                    versions:set -DnewVersion=${date}-SNAPSHOT -f ${pathToPom}\
                     -Dmaven.repo.local='/tmp/.cache/m2/repository'"""
                        .stripMargin()
                )

                script.echo('============ END DYNATRACE VERSION PLUGIN ============')
            }
        }
    }

    void executeReleaseVersionUpdate() {
        script.configFileProvider(
            [
                script.configFile(
                    fileId: dynatraceSettingsId,
                    variable: 'DYNATRACE_SETTINGS')
            ]
        ) {
            script.withCredentials(
                [
                    script.usernamePassword(
                        credentialsId: "${config.artifactory.credentialsId}",
                        usernameVariable: 'ARTIFACTORY_USER',
                        passwordVariable: 'ARTIFACTORY_APIKEY'
                    )
                ]
            ) {
                script.echo('============ BEGIN DYNATRACE RELEASE VERSION PLUGIN ============')

                script.sh(
                    """mvn -B -s ${script.DYNATRACE_SETTINGS} \
                        versions:set -DnewVersion=${date}-RELEASE -f ${pathToPom}\
                        -Dmaven.repo.local='/tmp/.cache/m2/repository'"""
                        .stripMargin()
                )

                script.echo('============ END DYNATRACE VERSION PLUGIN ============')
            }
        }
    }

}
