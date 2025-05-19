package com.cigna.builds

import com.cigna.common.phases.PodSelector

import java.text.SimpleDateFormat
import com.cigna.common.kubernetes.PodTemplateCreator

/**
 * For builds utilizing Reltio MDM
 */
class ReltioBuild extends Build {
    ReltioBuild() {
        additionalValidationItems = []
        containerName = 'mavenv363-jq'
        containerImage = 'enterprise-devops/maven'
        containerVersion = '3.6.3-jq'
    }
    public static final String RELTIO_SETTINGS_CONFIG_FILE_ID = '2c5fdcc7-2376-4dbf-b252-a3a8e83603d9'
    public static final String RELTIO_AUTH_SETTINGS_CONFIG_ID = 'reltio-auth-settings'
    //TODO: Set to Prod Artifactory settings for GA release
    private String reltioRepoPrefix
    private String reltioSettingsId
    private Boolean reltioAuth


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
    String pattern = 'yyyy.MM.dd.HH.mm.ss';
    SimpleDateFormat simpleDateFormat =
            new SimpleDateFormat(pattern, Locale.ENGLISH);

    String date = simpleDateFormat.format(new Date());

    Closure<String> deriveAuthSettings = { ->
        reltioAuth ? "-Duserid=${script.ARTIFACTORY_USER} -Dapikey=${script.ARTIFACTORY_APIKEY}" : ''
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
            // The base class also checks if there is an extraConfigs element, but with the Reltio build, we have
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
                containers: [containerTemplate.getContainer(containerName)]
        ]
        super.prePodConfig()
    }

    @Override
    void executePreBuildStage() {
        commonPreBuildSteps()
        reltioRepoPrefix = ARTIFACTORY_URL - ('https://') - ('cigna.jfrog.io/artifactory')
        script.echo("Reltio Repo Prefix = ${reltioRepoPrefix}")
        pathToPom = "./${config.reltio?.pathToPom}" ?: pathToPom
        script.echo("Reltio Path to POM = ${pathToPom}")
        reltioAuth = config.reltio?.authSettings ?: false
        reltioSettingsId = reltioAuth ? RELTIO_AUTH_SETTINGS_CONFIG_ID : RELTIO_SETTINGS_CONFIG_FILE_ID
    }

    @Override
    void executeBuildAndTestStage() {
        if (config?.reltio?.debug) {
            reltioDebug()
        }

        if (config?.reltio?.enforcer == true) {
            executeEnforcer()
        }

        // Allow people to update pom versions
        // https://www.mojohaus.org/versions-maven-plugin/set-mojo.html
        if (config?.reltio?.alterVersions == true) {
            executeVersionUpdate()
        }

        if (config?.reltio?.releaseVersions == true) {
            executeReleaseVersionUpdate()
        }

        if (config?.reltio?.reltioType == 'rdm') {
            downloadJarFile()
            replaceVariables()
            convertCsvToJson()
        }

        script.configFileProvider(
                [
                        script.configFile(
                                fileId: reltioSettingsId,
                                variable: 'RELTIO_SETTINGS'
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
                        """mvn -B -s ${script.RELTIO_SETTINGS} \
                                           -f ${pathToPom} \
                                           -Drepo.mgr.env='${reltioRepoPrefix}' \
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
                                fileId: reltioSettingsId,
                                variable: 'RELTIO_SETTINGS'
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
                        """mvn -B -s ${script.RELTIO_SETTINGS} \
                                        -f ${pathToPom} deploy \
                                        -DskipTests \
                                        -Drepo.mgr.env='${reltioRepoPrefix}' \
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
     * This will allow for the triage and debugging of reltio pom file issues and dependency resolution
     */
    void reltioDebug() {
        script.configFileProvider(
                [
                        script.configFile(
                                fileId: reltioSettingsId,
                                variable: 'RELTIO_SETTINGS')
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
                script.echo('============ BEGIN RELTIO DEBUG ============')

                script.sh(
                        """mvn -B -s ${script.RELTIO_SETTINGS} \
                    help:effective-settings \
                    -Doutput=effective-settings.xml \
                    -Drepo.mgr.env='${reltioRepoPrefix}' \
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
                        """mvn -B -s ${script.RELTIO_SETTINGS} \
                    help:effective-pom \
                    -Doutput=effective-pom.xml \
                    -Drepo.mgr.env='${reltioRepoPrefix}' \
                    -Dmaven.repo.local='/tmp/.cache/m2/repository' \
                    """.stripMargin()
                )

                script.echo('Effective POM:')
                script.sh('cat effective-pom.xml')

                script.echo('============ END RELTIO DEBUG ============')
            }
        }
    }

    void executeEnforcer() {
        script.configFileProvider(
                [
                        script.configFile(
                                fileId: reltioSettingsId,
                                variable: 'RELTIO_SETTINGS')
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
                enforcerRules = config.reltio?.enforcerRules ?: enforcerRules

                script.echo('============ BEGIN RELTIO ENFORCER PLUGIN ============')

                script.sh(
                        """mvn -B -s ${script.RELTIO_SETTINGS} \
                    org.apache.maven.plugins:maven-enforcer-plugin:${enforcerPluginVersion}:enforce \
                    -f ${pathToPom} \
                    -Drules=${enforcerRules} -Dmaven.repo.local='/tmp/.cache/m2/repository'"""
                                .stripMargin()
                )

                script.echo('============ END RELTIO ENFORCER PLUGIN ============')
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
                                fileId: reltioSettingsId,
                                variable: 'RELTIO_SETTINGS')
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
//                versionChangeParams = config.reltio?.versionChangeParams ?: versionChangeParams

                script.echo('============ BEGIN RELTIO VERSION PLUGIN ============')

                script.sh(
                        """mvn -B -s ${script.RELTIO_SETTINGS} \
                    versions:set -DnewVersion=${date}-SNAPSHOT -f ${pathToPom}\
                     -Dmaven.repo.local='/tmp/.cache/m2/repository'"""
                                .stripMargin()
                )

                script.echo('============ END RELTIO VERSION PLUGIN ============')
            }
        }
    }

    void executeReleaseVersionUpdate() {
        script.configFileProvider(
                [
                        script.configFile(
                                fileId: reltioSettingsId,
                                variable: 'RELTIO_SETTINGS')
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
                script.echo('============ BEGIN RELTIO RELEASE VERSION PLUGIN ============')

                script.sh(
                        """mvn -B -s ${script.RELTIO_SETTINGS} \
                        versions:set -DnewVersion=${date}-RELEASE -f ${pathToPom}\
                        -Dmaven.repo.local='/tmp/.cache/m2/repository'"""
                                .stripMargin()
                )

                script.echo('============ END RELTIO VERSION PLUGIN ============')
            }
        }
    }

    // Replace Variables in rdm-lookups-json.mapping file
    void replaceVariables() {
        String tenantId = config.reltio.tenantId
        String mappingFilePath = config.reltio?.pathToMappingFile
        script.echo("replaceVariables: ${tenantId}")
        script.sh('set +x')
        script.sh('set -o errexit')
        // Replacing <tenantid> with the proper RDM Tenant ID
        script.sh(
                script: """
                sed -i "s/<tenantid>/$tenantId/g" ${mappingFilePath}
            """
        )
    }

    void downloadJarFile() {
        //  download jar file from artifactory
        script.echo('downloading jar file from artifactory...........')
        script.withCredentials([
                script.usernamePassword(
                        credentialsId: "${config.artifactory.credentialsId}",
                        usernameVariable: 'ARTIFACT_USER',
                        passwordVariable: 'ARTIFACT_PASS',
                )
        ]) {
            // hardcoded values for now, will tokenize in the future
            String repo = 'cigna-maven-snapshots'
            String path = 'com/cigna/im/devops/reltio/reltioD-rdm/resources/'
            String name = 'reltio-util-rdm-json-generator-3.3.1.jar'
            String artifactUrl = "${ARTIFACTORY_URL}/${repo}/${path}/${name}"

            script.echo("${artifactUrl}")

            String response = script.sh(
                    script: """
                    curl -s -u ${script.ARTIFACT_USER}:${script.ARTIFACT_PASS} ${artifactUrl} \
                    --output ${name}
                """,
                    returnStdout: true
            )
            script.echo("${response}")
        }
    }

    void convertCsvToJson() {
        // as in downloadJarFile(), this value will be tokenized in the future
        String jarFileName = 'reltio-util-rdm-json-generator-3.3.1.jar'
        String pathToPropertiesFile = config.reltio.pathToPropertiesFile

        script.sh('ls -ltr')

        script.echo("jarFileName: ${jarFileName}")
        script.echo("pathToPropertiesFile: ${pathToPropertiesFile}")

        script.sh(
                script: """
                nohup java -jar ${jarFileName} ${pathToPropertiesFile}
            """,
                returnStdout: false
        )
    }

}
