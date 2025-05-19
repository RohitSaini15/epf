package com.cigna.builds

import com.cigna.base.Phase
import com.cigna.common.kubernetes.PodConfigGenerator
import com.cigna.common.utils.FeatureFlags
import com.cigna.common.utils.Utils
import com.cigna.scanning.CheckmarxScanning
import com.cigna.scanning.SonarqubeScanning
import com.cloudbees.groovy.cps.NonCPS
import com.cigna.common.kubernetes.PodTemplateCreator


import static com.cigna.common.utils.Utils.calculateContainerName

/**
 * Abstract base class for all builds. All methods that can be used across all builds should be
 * included in this class. This also serves to guarantee that,
 * executeBuildAndTestStage and executePublishStage methods will be available for cignaBuildFlow.
 */
abstract class Build extends Phase {
    public static final String LOGICAL_SONAR_NAME = 'sonar'
    public static final String LOGICAL_CHECKMARX_NAME = 'checkmarx'
    public static final String sonarContainerImage = 'enterprise-devops/sonar'
    public static final String sonarContainerVersion = '4.8.1-v1'
    public String sonarContainerName = calculateContainerName(sonarContainerImage, sonarContainerVersion)
    public static final String checkmarxContainerImage = 'dev-sec-ops/checkmarx-toolshack'
    public static final String checkmarxContainerVersion = '1.2.1'
    public String checkmarxContainerName = calculateContainerName(checkmarxContainerImage, checkmarxContainerVersion)
    def typesNotSupportedForScanning = ['reltio', 'dynatrace', 'ace']

    Build() {
        basePodConfig = [
            volumes   : [],
            containers: []
        ]

        baseValidationItems = [
            'checkmarx.credentialsId',
            'checkmarx.settings.CX_PROJECT_TEAM_NAME',
            'sonarQube.credentialsId',
        ]
        groupID = 'build'
    }

    @Override
    Boolean prePodConfig() {

        List<Map> env = [
            [
                name : 'SONAR_USER_HOME',
                value: '/tmp/.cache/sonar'
            ],
            [
                name : 'NODE_PATH',
                value: '/tmp/.cache/npm/lib/node_modules'
            ],
            [
                name : 'HOME',
                value: '/tmp'
            ],
            [
                name : 'NPM_CONFIG_PREFIX',
                value: '/tmp/.cache/npm'
            ]
        ]

        PodTemplateCreator containerTemplate = PodTemplateCreator.newPodTemplateCreator()
        containerTemplate.addContainer(
            sonarContainerName,
            "${sonarContainerImage}:${sonarContainerVersion}",
            10,
            50,
            1000,
            2000,
            env
        )
        containerTemplate.addVolumeMount(sonarContainerName, 'sonar-workspace', '/home/jenkins/agent/sonar')
        containerTemplate.addVolumeMount(sonarContainerName, 'setup-sonar', '/home/jenkins/agent/setup-sonar')

        def sonarConfig = containerTemplate.getContainer(sonarContainerName)
        sonarConfig.put('origname', sonarContainerName)

        containerTemplate.addContainer(
            checkmarxContainerName,
            "${checkmarxContainerImage}:${checkmarxContainerVersion}",
            10,
            50,
            500,
            1000,
            env
        )

        def checkmarxConfig = containerTemplate.getContainer(checkmarxContainerName)
        checkmarxConfig.put('origname',checkmarxContainerName)

        if (isFlagEnabled('checkmarxEnabled')) {
            basePodConfig.containers += checkmarxConfig
        }
        if (isFlagEnabled('sonarEnabled')) {
            basePodConfig.containers += sonarConfig
        }

        super.prePodConfig()
    }

    protected boolean isFlagEnabled(String flagName) {
        config.buildType in typesNotSupportedForScanning ? false : config.containsKey(flagName) ? config."$flagName" : true
    }

    /**
     * During prePodConfig() any container overrides including sidecar container overrides, will be applied, this means
     * that if a customer uses, for example,
     * `containers: [ name: 'sonarv48', image: 'devops/sonar:4.9']`
     * in their build phase, the container names that are stored in the build class will be out of sync with the new
     * container.
     * This code will execute immediately after all containers have been updated but before the pod templates are
     * constructed and so any new container names will be correctly synced here.
     * @return
     */
    @Override
    Boolean postPodConfig() {
        def finalizedImage
        def hasCheckmarx = isFlagEnabled('checkmarxEnabled')
        def hasSonarQube = isFlagEnabled('sonarEnabled')
        if (hasCheckmarx) {
            finalizedImage = basePodConfig.containers.find { it.origname == checkmarxContainerName }.image
            checkmarxContainerName = PodConfigGenerator.getContainerName(finalizedImage)
            psc.nameRegistry.registerName(LOGICAL_CHECKMARX_NAME, checkmarxContainerName)
        }
        if (hasSonarQube) {
            finalizedImage = basePodConfig.containers.find { it.origname == sonarContainerName }.image
            sonarContainerName = PodConfigGenerator.getContainerName(finalizedImage)
            updateSonarOptions(config.sonarQube)
            psc.nameRegistry.registerName(LOGICAL_SONAR_NAME, sonarContainerName)
        }

        // origname exists because the Build phase caches the container names and so we can't reliably look up
        // containers by just their name as there is no reliable way to "know" which container was original sonar
        // as a completely different image could potentially be used to override the container.
        basePodConfig.containers.each { Map container -> container.remove('origname') }

        super.postPodConfig()
    }

    protected static final String PREBUILD_STAGE = 'Pre Build Stage'
    protected static final String BUILD_AND_TEST_STAGE = 'Build and Test Stage'
    protected static final String PUBLISH_STAGE = 'Publish to Artifactory Stage'

    protected String ARTIFACTORY_URL = 'https://cigna.jfrog.io/artifactory'
    protected String ARTIFACTORY_ROOT = 'cigna-maven-releases/'
    protected String ARTIFACTORY_API_URL = ARTIFACTORY_URL + '/api'

    protected static final String CONTAINER_VERSION_REGEX = /^[\w][\w\.\-]{0,128}$/
    String userEmail
    protected String cignaCertsLocation

    protected int successCode = 204
    protected boolean runSonar
    protected boolean runCheckmarx
    protected Map scans = [:]
    protected Map<String, String> scmVars
    List credsList

    abstract void executeBuildAndTestStage()

    abstract void executePublishStage()

    void run(
        List credsList = [],
        List configsList = []
    ) {
        this.credsList = credsList
        configureArtifactory()
        runScanningTools()

        psc.complianceValidator.setState(this, 'state', 'Running')
        script.dir(baseDirectory) {
            script.configFileProvider(configsList) {
                if (customWorkspace) {
                    script.ws(customWorkspace) {
                        workflow()
                    }
                } else {
                    workflow()
                }
            }
        }
    }

    @NonCPS
    @Override
    List validate(boolean requiresBranchPattern = false, Phase phase = this) {
        if (config?.tagDetails) {
            additionalValidationItems += [
                [testString: 'tagDetails.gitTagCredKey', customMessage: 'Git API token for tag push not specified.'],
                [testString: 'tagDetails.branch', customMessage: 'Branch not specified for tagging.'],
                ['tagDetails.tagFile', 'tagDetails.tag'],
            ]
        }
        super.validate(requiresBranchPattern, phase)
    }
/**
 * This validates a builds container version matches the regular expression for compatible  version
 * numbers
 *
 * @param config The config map taken from the closure body in the call method
 * @param buildInstance The Build instance to use to get information for validation
 *
 * @return any issues found.
 */
    @NonCPS
    List<String> validateBuildConfigContainerVersion() {
        List versionIssues = []
        if (config?.containerVersion
            && !(config?.containerVersion =~ CONTAINER_VERSION_REGEX)) {
            versionIssues.add('Invalid build containerVersion. Regex for containerVersion is '
                + "${CONTAINER_VERSION_REGEX}"
            )
        }
        versionIssues
    }

/**
 * Pre build stage that executes commonPreBuildSteps method
 */
    void executePreBuildStage() {
        script.stage(PREBUILD_STAGE) {
            commonPreBuildSteps()
        }
    }

    boolean currentBranchIsReleaseBranch() {
        //if releaseBranchPattern is not specified branchPattern is assumed to be the release branch pattern
        currentBranchMatches(config.releaseBranchPattern ?: config.branchPattern)
    }

/**
 * Add directories and files created during build process to gitignore file
 */
    protected void addGeneratedFilesToGitignore() {
        script.sh(
            'printf "\n.npm\n?/.sonar\nnode_modules\n.scannerwork" >> .gitignore'
        )
    }

/**
 * Returns the Artifactory Application name scope (@scope) if there is one
 *
 * @return The Artifactory Application name scope (@scope) if there is one
 */
    protected String artifactoryApplicationNameScope() {
        Map<String, String> artifactoryConfig = config.artifactory
        if (artifactoryConfig?.applicationName?.trim()?.contains('@')) {
            String artifactoryScopeWithAtSign = artifactoryConfig.applicationName.split('/')[0]
            String artifactoryScope = artifactoryScopeWithAtSign.replace('@', '')
            return artifactoryScope
        }

        ''
    }

/**
 * Creates a URL for search Artifactory with the given applicationVersion
 * added to config.artifactory.applicationName to search if the artifact already exist.
 * If so an exception is thrown.
 *
 * @param applicationVersion The version number of the application
 */
    protected void checkIfApplicationNameAndVersionUsedInArtifactory(String applicationVersion) {
        String[] applicationNameParts = config.artifactory.applicationName.split('/')
        String baseApplicationName = applicationNameParts.last()
        String artifactoryApiSearchUrl = "${ARTIFACTORY_API_URL}/search/artifact?name" +
            "=${baseApplicationName}-${applicationVersion}"
        String urlsWithSameNameAndVersionText = script.httpRequest(
            url: "${artifactoryApiSearchUrl}"
        ).getContent()

        script.echo(
            'Checking this Application and Version already in Artifactory ' +
                "${baseApplicationName}-${applicationVersion}"
        )
        Map<String, Object> urlsWithSameNameAndVersion = script.readJSON(
            text: urlsWithSameNameAndVersionText
        )
        script.echo("${urlsWithSameNameAndVersion.results}")

        if (urlsWithSameNameAndVersion.results) {
            throw error('An Artifactory package already exists with the same name, and version '
                + 'as your target. Please increment the version, or change the'
                + ' application name')
        }
    }

/**
 * Downloads Internal Cigna certificates to CIGNA_CERTS_LOCATION
 */
    protected void downloadCignaCerts() {
        cignaCertsLocation = "/tmp/cigna_certs.pem"
        script.httpRequest(
            url: 'https://raw.github.sys.cigna.com/cigna/certfix/main/certs/generated/combined.pem',
            outputFile: cignaCertsLocation
        )
    }

/**
 * Steps that are common to all builds. Currently includes clearing
 * out the workspace, checking out the git branch, appending to or creating the
 * sonar-project.properties file and updating Gitlab since the update has to
 * happen after checking out the branch so the plugin knows with branch to update
 */
    protected void commonPreBuildSteps() {
        script.echo('Started common prebuild steps.')
        if (FeatureFlags.debug) {
            script.echo(config.inspect())
        }
        if (FeatureFlags.verbose) {
            script.sh('printenv | sort')
        }
        downloadCignaCerts()
        tagCommit()
    }

    protected void runScanningTools() {
        if (!(config?.buildType in typesNotSupportedForScanning)) {
            if (psc.complianceValidator.isProdDeploy == true) {
                runSonar = psc.complianceValidator.sonarEnabled
                runCheckmarx = psc.complianceValidator.checkmarxEnabled
            } else {
                runSonar = config.get('sonarEnabled', true)
                runCheckmarx = config.get('checkmarxEnabled', true)
            }
        } else {
            config.sonarEnabled = false
            config.checkmarxEnabled = false
        }

        if (runSonar) {
            scans += [
                sonar: {
                    script.stage('SonarQube Scan') {
                        psc.podSelector.select(psc, sonarContainerName, Utils.cloud(config)) {
                            SonarqubeScanning sonarScan = new SonarqubeScanning(
                                script: script,config: config,psc: psc,
                                containerName: this.containerName,
                                executeBuildAndTestStage: { this.executeBuildAndTestStage() }
                            )
                            sonarScan.scan(sonarContainerName)
                        }
                    }
                }
            ]
        }

        if (runCheckmarx) {
            scans += [
                checkmarx: {
                    script.stage('Checkmarx Scan') {
                        psc.podSelector.select(psc, checkmarxContainerName, Utils.cloud(config)) {
                            CheckmarxScanning checkmarxScan = new CheckmarxScanning(
                                script: script,config: config,psc: psc)
                            checkmarxScan.scan(checkmarxContainerName)
                        }
                    }
                }
            ]
        }
    }

    protected boolean shouldRunBuild() {
        return config.get('buildEnabled', true)
    }

    protected boolean shouldRunPublish() {
        if (!config.get('publishEnabled', true)) {
            return false
        } else if (!currentBranchIsReleaseBranch()) {
            script.echo("releaseBranchPattern ${config.releaseBranchPattern ?: config.branchPattern} does not match " +
                "current branch ${gitBranch()}, so no publish steps are being taken.")
            return false
        } else {
            return true
        }
    }

    protected void executeBuildAndTest() {
        if (shouldRunBuild()) {
            script.stage(BUILD_AND_TEST_STAGE) {
                psc.podSelector.select(psc, podTemplateContainerName, Utils.cloud(config)) {
                    try {
                        executeBuildAndTestStage()
                        script.sh(
                            script: "chmod -R 777 ./ 2>&1 || true",
                            returnStdout: true
                        )
                    } finally {
                        if (usesJunit()) {
                            publishJunit()
                        }
                        if (usesWarningsNG()) {
                            publishWarningsNG()
                        }
                    }
                }
            }
            script.parallel(scans)
        }
    }

    protected void publish() {
        if (shouldRunPublish()) {
            script.stage(PUBLISH_STAGE) {
                executePublishStage()
            }
        }
    }

    protected void workflow() {
        script.withCredentials(credsList) {
            moveFiles('begin')
            executePreBuildStage()
            executeBuildAndTest()
            publish()
            moveFiles('end')
        }
    }

    void configureArtifactory() {
        if (config.containsKey('artifactory') && config.artifactory.containsKey('url')) {
            ARTIFACTORY_URL = config.artifactory.url
            ARTIFACTORY_API_URL = ARTIFACTORY_URL + '/api'
            script.echo("ARTIFACTORY_URL = $ARTIFACTORY_URL")
        }
        if (config.containsKey('artifactory') && config.artifactory.containsKey('root')) {
            ARTIFACTORY_ROOT = "${config.artifactory.root}/"
            script.echo("ARTIFACTORY_ROOT = $ARTIFACTORY_ROOT")
        }
    }

    public static final String DEFAULT_MAX_CONTAINER_MEMORY = '2000Mi'


    def updateSonarOptions(def sonarQube) {
        String sonarMaxContainerMemory = sonarQube?.containerMaxMemory ?: null

        def sonarConfig = basePodConfig.containers.find { it.name == sonarContainerName }
        if (sonarConfig) {
            if (sonarMaxContainerMemory) {
                script.echo("Overriding Sonar Max Container Memory from " +
                    "${sonarConfig.resources.limits.memory} to ${sonarMaxContainerMemory}")
                sonarConfig.resources.limits.memory = sonarMaxContainerMemory
            }
            String scannerOpts = sonarQube?.scannerOptions ?: Utils.calculateOptimalScannerOptions(sonarConfig.resources.limits.memory)
            sonarConfig.env += [name: 'SONAR_SCANNER_OPTS', value: scannerOpts]
        }
    }
}
