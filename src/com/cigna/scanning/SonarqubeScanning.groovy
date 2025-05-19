package com.cigna.scanning

import com.cigna.builds.DotnetcoreBuild
import com.cigna.common.request.CurlRequestor
import com.cigna.common.utils.FeatureFlags
import com.cigna.common.utils.Utils
import hudson.Functions

/**
 * Defined behavior for Sonarqube scanning
 */
class SonarqubeScanning extends Scanning {

    protected static final String SONARQUBE_URL = 'https://sonarqube.sys.cigna.com'
    protected String sonarQubeWorkspace = './'
    protected boolean checkForTypeScript = true
    protected Map<String, String> sonarPropsFile = [file: 'sonar-project.properties']
    protected int successCode = 204
    String gitBranch = ''
    Closure executeBuildAndTestStage = {}

    /**
     * Run a SonarQube scan. If scan fails, try without sonar.branch.name parameter
     */
    void scan(String activeContainer = containerName) {
        this.curlRequestor = new CurlRequestor(psc, script)
        this.curlRequestor.containerName = activeContainer
        // Move sonar map entries into sonarqube map
        if (config.containsKey('sonar')) {
            config.sonarQube = config.sonarQube + config.sonar
        }

        if (!config.sonarQube || (config.sonarQube as Map).size() == 0) {
            def msg = "Sonar scanning enabled, but no SonarQube configuration detected."
            script.echo(msg)
            script.currentBuild.result = 'FAILED'
            throwErrorStepException(msg)
        }

        writePropertiesToSonarProjectPropertiesFile()
        // Extract sonar url from properties, or resort to default
        Map<String, String> sonarProperties = script.readProperties(sonarPropsFile)

        if (isDotNetScannable()) {
            executeDotNetScan(sonarProperties)
        } else {
            executeSonarScan(sonarProperties)
        }
    }

    private void executeSonarScan(Map<String, String> sonarProperties) {
        script.echo('Sonar project configured, executing normal scan...')

        String sonarCustomArgsCommand = config?.sonarQube?.containsKey('customArgs') ?
            config['sonarQube']['customArgs'] : ''

        moveSonarFiles()
        addSonarProjectTags(sonarProperties['sonar.login'])
        addBranchProtection(sonarProperties['sonar.login'])

        script.dir(this.sonarQubeWorkspace) {
            if (config?.sonarQube?.installTypescript) {
                installTypeScript()
            }

            script.sh("sonar-scanner ${sonarCustomArgsCommand}")
        }
    }

    /**
     * Run a dotnet sonar scan when sonarScanner param is called in sonar map
     */
    protected void executeDotNetScan(Map<String, String> sonarProperties) {
        String sonarProjectKey = sonarProperties['sonar.projectKey']
        String sonarToken = sonarProperties['sonar.login']
        String exclusions = sonarProperties['sonar.exclusions'] ?: ''

        String solutionFilePath = config.dotnet?.project ?: config.sonarQube.solutionFile
        String commonArgs = config.dotnet?.get('commonArgs', '')
        script.echo('Executing DotNet Scanner ...')
        psc.podSelector.select(psc,
            psc.nameRegistry.nameFor(DotnetcoreBuild.LOGICAL_CONTAINER_NAME),
            Utils.cloud(config)
        ) {
            def coverageEnabled = config.sonarQube.get('coverage', true)
            script.sh(
                script: "dotnet-sonarscanner begin /k:'${sonarProjectKey}'"
                    + " /d:sonar.host.url='${SONARQUBE_URL}' /d:sonar.login='${sonarToken}'${exclusions ? " /d:sonar.exclusions=\"$exclusions\"" : ''}" +
                    "${coverageEnabled ? ' /d:sonar.cs.vscoveragexml.reportsPaths="coverage.xml"' : ""}" +
                    "${FeatureFlags.verbose ? ' /d:sonar.verbose=true' : ''}",
                returnStdout: true
            )

            script.sh(
                script: "dotnet build ${solutionFilePath} ${commonArgs}",
                returnStdout: true
            )

            if (coverageEnabled) {
                script.sh("dotnet-coverage collect \"dotnet test ${solutionFilePath} $commonArgs\" -f xml -o \"coverage.xml\"")
            }

            // The dotnet-sonarscanner end operation will fail if there is a sonar-project.properties file
            // however, code that executes when this function returns, requires the sonar-project.properties file
            // to be in the expected location. temporarily move it so that the end call completes and then restore
            // it for the sonar-scanner call.
            script.sh("mv ${script.env.WORKSPACE}/sonar-project.properties /tmp/disabled")
            script.sh("dotnet-sonarscanner end /d:sonar.login='${sonarToken}'")
            script.sh("mv /tmp/disabled ${script.env.WORKSPACE}/sonar-project.properties")
        }
    }

    /**
     * sonarQube.sonarScanner is deprecated, useDotNetScanner should be favored instead.
     * @return
     */
    private boolean isDotNetScannable() {
        config.sonarQube?.useDotNetScanner ?: config.sonarQube?.sonarScanner ?: false
    }

    /**
     * If there are Typescript files in the repository install the typescript version in the
     * package.json
     */
    protected void installTypeScript() {
        if (checkForTypeScript) {
            String findTSFilesOutput = script.sh(
                script: 'find ./ -type f -iname "*.ts" -print',
                returnStdout: true
            )
            if (findTSFilesOutput) {
                String cdCommand = (
                    'cd "$(dirname "$(find ./ -type f -path \'**/node_modules/*\' '
                        + '-prune -o -name package.json -print | tail -1)")"; '
                )
                try {
                    script.sh(
                        cdCommand + 'npm install typescript@`node -p ' +
                            '-e "require(\'./package.json\').dependencies.typescript"`'
                    )
                } catch (all) {
                    if (FeatureFlags.showStackTraces) {
                        script.echo(Functions.printThrowable(all))
                    }

                    script.echo(
                        "failed to install typescript using package.json dependencies: '${all.message}'. Trying devDependencies"
                    )
                    script.sh(
                        cdCommand + 'npm install typescript@`node -p ' +
                            '-e "require(\'./package.json\').devDependencies.typescript"`'
                    )
                }

                script.echo('TypeScript has been installed.')
            }
        }
    }

    /**
     * If there are sonarQube.tags set in sonarQube config by the user
     * add list of tags to the project based on the project key
     */
    protected void addSonarProjectTags(String sonarToken) {
        Map<String, String> sonarProperties = script.readProperties(sonarPropsFile)
        List<String> tags = config?.sonarQube?.tags
        String sonarProjectKey = sonarProperties['sonar.projectKey']
        String allTags = ''

        if (tags) {
            script.dir(this.sonarQubeWorkspace) {
                for (int i = 0; i < tags.size(); i++) {
                    if (i != 0) {
                        allTags += ','
                    }
                    allTags += tags[i]
                }

                Map<String, String> response = this.curlRequestor.requestJsonWithLiteralCred(
                    "${SONARQUBE_URL}/api/project_tags/set?" \
                          + "project=${sonarProjectKey}" \
                          + "&tags=$allTags",
                    "${sonarToken}:",
                    'POST'
                )

                if (response.responseCode != successCode) {
                    script.error('Error setting project tags for SonarQube project')
                }
            }
        }
    }

    /**
     * If sonarQube.branchProtection is set in sonarQube config by the user
     * add protection to specified branch to keep branch active forever, else branch expires
     * after 30 days
     *
     * Master branch is protected by default
     *
     * @param sonarToken
     */
    protected void addBranchProtection(String sonarToken) {
        Map<String, String> sonarProperties = script.readProperties(sonarPropsFile)
        String sonarProjectKey = sonarProperties['sonar.projectKey']
        String branchToProtect = config?.sonarQube?.branchToProtect
        String enableBranchProtection = 'true'

        if (branchToProtect) {
            script.dir(this.sonarQubeWorkspace) {
                Map<String, String> response = this.curlRequestor.requestJsonWithLiteralCred(
                    "${SONARQUBE_URL}/api/project_branches/set_automatic_deletion_protection?" \
                          + "project=${sonarProjectKey}&branch=${branchToProtect}" \
                          + "&value=${enableBranchProtection}",
                    "${sonarToken}:",
                    'POST'
                )

                if (response.responseCode != successCode) {
                    script.error("Error setting branch protection for branch: ${branchToProtect}")
                }
            }
        }
    }

    /**
     * When utilizing the sonarqube workspace workaround, move specified files into sonarqube workspace
     */
    protected void moveSonarFiles() {
        if (
            config?.sonarQube?.sonarIncludesPattern &&
                this.sonarQubeWorkspace == '/home/jenkins/agent/sonar'
        ) {
            script.echo(
                'Moving files matching the following regex pattern to sonarqube workspace: ' +
                    config.sonarQube.sonarIncludesPattern
            )
            script.sh("""
                find ./ -type f -regex \'${config.sonarQube.sonarIncludesPattern}\' \
                -exec cp -v \'{}\' ${this.sonarQubeWorkspace}/ \';\'
            """)
        }
    }

    /**
     * Write a sonar-project.properties file which sonar-scanner users for SonarQube scans. This
     * method adds sonar.host.url, sonar.branch.name, sonar.login, and sonar.exclusions properties
     * to the given properties and appends them to the the sonar-project.properties file.
     */
    protected void writePropertiesToSonarProjectPropertiesFile() {
        Map<String, String> sonarQubeConfig = config.sonarQube
        if (sonarQubeConfig?.credentialsId) {
            gitBranch = gitBranch()
            script.withCredentials([
                script.string(
                    credentialsId: "${sonarQubeConfig.credentialsId}", variable: 'sonarQubeToken'
                )
            ]) {
                String[] combinedProperties = [
                    "sonar.branch.name=${gitBranch}",
                    "sonar.branch.target=${gitBranch}",
                    'sonar.exclusions=node_modules/**,data.json',
                ]
                // Stop putting null in sonar-project.properties
                if (sonarQubeConfig.containsKey('scannerProperties')) {
                    combinedProperties = combinedProperties + sonarQubeConfig.scannerProperties
                }

                String combinedPropertiesText = '\n' + combinedProperties.join('\n')
                script.sh("printf \"${combinedPropertiesText}\" >> sonar-project.properties")
                script.sh(
                    "printf \"\nsonar.login=${script.sonarQubeToken}\" >> sonar-project.properties"
                )
                if (sonarQubeConfig?.sonarIncludesPattern) {
                    this.sonarQubeWorkspace = '/home/jenkins/agent/sonar'
                    script.sh("cp sonar-project.properties ${this.sonarQubeWorkspace}/")
                }
            }
        } else if (sonarQubeConfig?.scannerParams && !sonarQubeConfig?.credentialsId) {
            throw new UnsupportedOperationException(
                'Must have sonarQube.credentialsId configuration option if' +
                    ' sonarQube.scannerProperties configuration option is included '
            )
        }
    }
}
