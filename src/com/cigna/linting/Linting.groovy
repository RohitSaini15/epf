package com.cigna.linting

import com.cigna.base.Phase
import com.cigna.builds.GradleBuild
import com.cigna.builds.MavenBuild
import com.cigna.common.exception.EndPipelineException
import com.cigna.common.kubernetes.PodConfigGenerator
import com.cigna.common.notification.Notification
import com.cigna.common.request.CurlRequestor
import com.cigna.common.utils.AWSUtils
import com.cigna.common.utils.FeatureFlags
import com.cigna.common.utils.PlzUtils
import com.cigna.common.utils.Utils
import com.cigna.deployment.TerraformDeployment
import com.cloudbees.groovy.cps.NonCPS
import com.evernorth.cloudnativebuild.data.DockerUri
import hudson.Functions
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

import static com.cigna.common.utils.Utils.calculateContainerName

/**
 * Linting phase implementation. This class runs all specified linters in
 * parallel in a single pod. This also serves to guarantee that a
 * containerTemplates method that define the container templates needed for the
 * linting work.
 */
class Linting extends Phase {
    private static final String GOLANG = 'go'
    private static final String BANDIT = 'bandit'
    private static final String MAVEN = 'maven'
    private static final String GRADLE = 'gradle'
    private static final String SHELLCHECK = 'shellcheck'
    private static final String PLEASE = 'plz'
    private static final String TERRAGRUNT = 'terragrunt'
    private static final String SONARQG = 'sonarqg'
    private static final String YAMLLINT = 'yamllint'
    private static final String ANSIBLELINT = 'ansiblelint'
    private static final String APPROVALREQUEST = 'approvalrequest'
    private static final Integer SUCCESSCODE = 200
    protected static final String SONARQUBE_URL = 'https://sonarqube.sys.cigna.com'
    protected static final String GITLAB_URL = 'https://git.sys.cigna.com'

    private static final String BIG_CONTAINER_CPU = '1500m'
    private static final String BIG_CONTAINER_MEM = '3000Mi'
    private static final String MID_CONTAINER_CPU = '1000m'
    private static final String MID_CONTAINER_MEM = '2000Mi'
    private static final String FLEX_CONTAINER_CPU = '250m'
    private static final String FLEX_CONTAINER_MEM = '125Mi'

    Linting() {
        baseValidationItems = ['lintingTypes']
        containerName = 'jnlp'
        awsAllowedPhase = true
        containerImage = ''
        groupID = 'linting'
    }

    String pythonContainerImage = 'enterprise-devops/python'
    String pythonContainerVersion = 'python-39-ubi9-v2'
    String pythonContainerName = calculateContainerName(pythonContainerImage, pythonContainerVersion)
    String golangContainerImage = 'enterprise-devops/golang'
    String golangContainerVersion = 'ubi9-go-1.20'
    String golangContainerName = calculateContainerName(golangContainerImage, golangContainerVersion)
    String shellcheckContainerImage = 'enterprise-devops/shellcheck'
    String shellcheckContainerVersion = 'stable'
    String shellcheckContainerName = calculateContainerName(shellcheckContainerImage, shellcheckContainerVersion)
    String mavenContainerImage = 'enterprise-devops/maven'
    String mavenContainerVersion = '2.0.0-v3.9.9-8-eclipse-temurin'
    String mavenContainerName = calculateContainerName(mavenContainerImage, mavenContainerVersion)
    String gradleContainerImage = 'enterprise-devops/gradle'
    String gradleContainerVersion = 'latest'
    String gradleContainerName = calculateContainerName(gradleContainerImage, gradleContainerVersion)
    String awsDSlimImage = 'enterprise-devops/aws-d-cloudkit'
    String awsDSlimVersion = 'plz-2'
    String awsDSlimName = calculateContainerName(awsDSlimImage, awsDSlimVersion)
    String sonarqgContainerImage = 'enterprise-devops/epf-curl'
    String sonarqgContainerVersion = 'latest'
    String sonarqgContainerName = calculateContainerName(sonarqgContainerImage, sonarqgContainerVersion)
    String ansibleContainerImage = 'enterprise-devops/ansiblelint'
    String ansibleContainerVersion = 'latest'
    String ansibleContainerName = calculateContainerName(ansibleContainerImage, ansibleContainerVersion)

    protected String qualityGateStatus
    protected String lintingStageName = 'Linting'
    Object curlRequestor


    @Override
    Boolean prePodConfig() {
        configureContainers()

        super.prePodConfig()
    }

    /**
     * Map containing podTemplate container and volume specifications for each
     * supported linting type.
     *
     * (Note: map keys must be literals for validation to work correctly)
     */
    protected Map<String, Object> containerSpecs = [
        bandit         : [
            name           : pythonContainerName,
            image          : "${pythonContainerImage}:${pythonContainerVersion}",
            tty            : true,
            imagePullPolicy: containerImagePullPolicy,
            command        : com.cigna.common.utils.Utils.defaultSidecarCommand,
            env            : [
                [
                    name : 'HOME',
                    value: '/tmp'
                ],
            ],
            resources      : [
                requests: [
                    cpu   : '100m',
                    memory: FLEX_CONTAINER_MEM
                ],
                limits  : [
                    cpu   : FLEX_CONTAINER_CPU,
                    memory: FLEX_CONTAINER_MEM
                ]
            ],
        ],
        go             : [
            name           : golangContainerName,
            image          : "${golangContainerImage}:${golangContainerVersion}",
            tty            : true,
            imagePullPolicy: containerImagePullPolicy,
            command        : com.cigna.common.utils.Utils.defaultSidecarCommand,
            env            : [
                [
                    name : 'HOME',
                    value: '/tmp'
                ],
            ],
            resources      : [
                requests: [
                    cpu   : '100m',
                    memory: MID_CONTAINER_MEM
                ],
                limits  : [
                    cpu   : MID_CONTAINER_CPU,
                    memory: MID_CONTAINER_MEM
                ]
            ],
        ],
        maven          : [
            name           : mavenContainerName,
            image          : "${mavenContainerImage}:${mavenContainerVersion}",
            tty            : true,
            workingDir     : '/home/jenkins/agent',
            imagePullPolicy: containerImagePullPolicy,
            command        : com.cigna.common.utils.Utils.defaultSidecarCommand,
            resources      : [
                requests: [
                    cpu   : '100m',
                    memory: BIG_CONTAINER_MEM
                ],
                limits  : [
                    cpu   : BIG_CONTAINER_CPU,
                    memory: BIG_CONTAINER_MEM
                ]
            ]
        ],
        gradle         : [
            name           : gradleContainerName,
            image          : "${gradleContainerImage}:${gradleContainerVersion}",
            tty            : true,
            workingDir     : '/home/jenkins/agent',
            imagePullPolicy: containerImagePullPolicy,
            command        : com.cigna.common.utils.Utils.defaultSidecarCommand,
            resources      : [
                requests: [
                    cpu   : '100m',
                    memory: BIG_CONTAINER_MEM
                ],
                limits  : [
                    cpu   : BIG_CONTAINER_CPU,
                    memory: BIG_CONTAINER_MEM
                ]
            ]
        ],
        shellcheck     : [
            name           : shellcheckContainerName,
            image          : "${shellcheckContainerImage}:${shellcheckContainerVersion}",
            tty            : true,
            imagePullPolicy: containerImagePullPolicy,
            command        : com.cigna.common.utils.Utils.defaultSidecarCommand,
            env            : [
                [
                    name : 'HOME',
                    value: '/tmp'
                ]
            ],
            resources      : [
                requests: [
                    cpu   : '100m',
                    memory: FLEX_CONTAINER_MEM
                ],
                limits  : [
                    cpu   : FLEX_CONTAINER_CPU,
                    memory: FLEX_CONTAINER_MEM
                ]
            ],
        ],
        plz            : [
            name           : awsDSlimName,
            image          : "${awsDSlimImage}:${awsDSlimVersion}",
            imagePullPolicy: containerImagePullPolicy,
            tty            : true,
            workingDir     : '/home/jenkins/agent',
            command        : [
                '/usr/local/bin/adhoc-perms'
            ],
            args           : com.cigna.common.utils.Utils.defaultSidecarCommand,
            resources      : [
                requests: [
                    cpu   : '100m',
                    memory: BIG_CONTAINER_MEM
                ],
                limits  : [
                    cpu   : BIG_CONTAINER_CPU,
                    memory: BIG_CONTAINER_MEM
                ]
            ],
            env            : AWSUtils.awsEnvironment(),
            volumeMounts   : AWSUtils.awsMounts()
        ],
        terragrunt     : [
            name           : awsDSlimName,
            image          : "${awsDSlimImage}:${awsDSlimVersion}",
            tty            : true,
            workingDir     : '/home/jenkins/agent',
            imagePullPolicy: containerImagePullPolicy,
            command        : [
                '/usr/local/bin/adhoc-perms'
            ],
            args           : com.cigna.common.utils.Utils.defaultSidecarCommand,
            resources      : [
                requests: [
                    cpu   : FLEX_CONTAINER_CPU,
                    memory: FLEX_CONTAINER_MEM
                ],
                limits  : [
                    cpu   : FLEX_CONTAINER_CPU,
                    memory: FLEX_CONTAINER_MEM
                ]
            ],
            env            : AWSUtils.awsEnvironment(),
            volumeMounts   : AWSUtils.awsMounts()
        ],
        sonarqg        : [
            name           : sonarqgContainerName,
            image          : "${sonarqgContainerImage}:${sonarqgContainerVersion}",
            imagePullPolicy: containerImagePullPolicy,
            tty            : true,
            workingDir     : '/home/jenkins/agent',
            command        : com.cigna.common.utils.Utils.defaultSidecarCommand,
            resources      : [
                requests: [
                    cpu   : '100m',
                    memory: FLEX_CONTAINER_MEM
                ],
                limits  : [
                    cpu   : BIG_CONTAINER_CPU,
                    memory: BIG_CONTAINER_MEM
                ]
            ],
        ],
        yamllint       : [
            name           : pythonContainerName,
            image          : "${pythonContainerImage}:${pythonContainerVersion}",
            tty            : true,
            imagePullPolicy: containerImagePullPolicy,
            command        : com.cigna.common.utils.Utils.defaultSidecarCommand,
            volumeMounts   : [],
            env            : [
                [
                    name : 'HOME',
                    value: '/tmp'
                ],
            ],
            resources      : [
                requests: [
                    cpu   : '100m',
                    memory: FLEX_CONTAINER_MEM
                ],
                limits  : [
                    cpu   : FLEX_CONTAINER_CPU,
                    memory: FLEX_CONTAINER_MEM
                ]
            ],
        ],
        ansiblelint    : [
            name           : ansibleContainerName,
            image          : "${ansibleContainerImage}:${ansibleContainerVersion}",
            imagePullPolicy: containerImagePullPolicy,
            tty            : true,
            workingDir     : '/home/jenkins/agent',
            command        : com.cigna.common.utils.Utils.defaultSidecarCommand,
            resources      : [
                requests: [
                    cpu   : '100m',
                    memory: FLEX_CONTAINER_MEM
                ],
                limits  : [
                    cpu   : FLEX_CONTAINER_CPU,
                    memory: FLEX_CONTAINER_MEM
                ]
            ],
        ],
        approvalrequest: [:],
    ]

    @Override
    def displayName(def prefix = '') {
        "${prefix}Linting(${(config.lintingTypes as Map).keySet()})"
    }

    /**
     * Adds container specifications for all lintingTypes provided to
     * the additionalPodConfig that is used to determine this phase's podTemplate
     */
    void configureContainers() {
        if (config.containsKey('container')) {
            throwException('******************************* MISCONFIGURED LINTING PHASE DETECTED *******************************\n' +
                'This linting phase is attempting to override the base container image which is probably not what you intended.\n' +
                'Move the container element into the linting configuration that owns the container you wish to change, for example:\n' +
                'Original Phase definition:\n' +
                '        [\n' +
                '            lintingTypes    : [\n' +
                '                \'plz\': [\n' +
                '                    verbosityFlag: \'-vvv\',\n' +
                '                ],\n' +
                '            ],\n' +
                '            container: [\n' +
                '                image  : "${containerImage}",\n' +
                '                version: "${containerVersion}",\n' +
                '                cpu    : 300,\n' +
                '                memory : 300,\n' +
                '            ],\n' +
                '            branchPattern   : \'.*\',\n' +
                '        ],\n' +
                '\nYou probably meant to say:\n' +
                '        [\n' +
                '            lintingTypes    : [\n' +
                '                \'plz\': [\n' +
                '                    verbosityFlag: \'-vvv\',\n' +
                '                    container: [\n' +
                '                        image  : "${containerImage}",\n' +
                '                        version: "${containerVersion}",\n' +
                '                        cpu    : 300,\n' +
                '                        memory : 300,\n' +
                '                    ],\n' +
                '                ]\n' +
                '            ],\n' +
                '            \n' +
                '            branchPattern   : \'.*\',\n' +
                '        ],\n' +
                '******************************* MISCONFIGURED LINTING PHASE DETECTED *******************************\\n\'')
        }


        lintingStageName = config?.stageName ?: lintingStageName
        config.lintingTypes.each { lintingType, opts ->
            if (lintingType != Linting.APPROVALREQUEST) {
                if (lintingType == SONARQG) {
                    lintingStageName = config?.stageName ?: 'Sonar QualityGate Check'
                    script.echo("lintingStageName: ${lintingStageName}")
                }
                Map<String, Object> containerSpec = containerSpecs.get(lintingType)

                String version = containerSpec.image.find(/:.*/) - ':'
                String imageName = containerSpec.image - version - ':'

                if (opts?.container?.image) {
                    containerSpec.image = containerSpec.image.replace(imageName, opts.container.image)
                }

                if (opts?.container?.version) {
                    containerSpec.image = containerSpec.image.replace(version, opts.container.version)
                }

                containerSpec.resources.limits.cpu =
                    (opts?.container?.cpu) ? "${opts?.container?.cpu}m" : containerSpec.resources.limits.cpu

                containerSpec.resources.limits.memory =
                    (opts?.container?.memory) ? "${opts?.container?.memory}Mi" : containerSpec.resources.limits.memory

                containerSpec.imagePullPolicy =
                    (opts?.container?.imagePullPolicy) ? opts?.container?.imagePullPolicy : containerSpec.imagePullPolicy


                if (containerImage == '') {
                    def vals = DockerUri.imageAndTag(containerSpec.image)
                    containerImage = vals['image']
                    containerVersion = vals['tag']
                }

                def newName = PodConfigGenerator.getContainerName(containerSpec.image)
                def oldName = PodConfigGenerator.getContainerName("$imageName:$version")
                updateMappedContainerNames(oldName, newName)
                containerName = newName

                podTemplateContainerName = containerName
                containerSpec.name = containerName
                additionalPodConfig.containers += containerSpec
            }
        }
        additionalPodConfig.volumes = AWSUtils.awsVolumes()
    }

    /**
     * Shared Validation for all Linting Phases
     * @return List of validation issues (Strings)
     */
    @Override
    @NonCPS
    List validate(boolean requiresBranchPattern = true, Phase phase = this) {
        List versionIssues = []
        config?.lintingTypes?.each { lintingType, opts ->
            if (containerSpecs.containsKey(lintingType)) {
                if (opts && !(Map.isAssignableFrom(opts.getClass()))) {
                    versionIssues.add(lintingType + ' options must be a Map')
                }
                if (lintingType == BANDIT) {
                    if (opts?.junit) {
                        if (!Map.isAssignableFrom(opts.junit.getClass())) {
                            versionIssues.add('lintingType bandit\'s junit option must be a Map')
                        }
                    }
                }
                if (lintingType == MAVEN) {
                    if (opts?.junit) {
                        if (!Map.isAssignableFrom(opts.junit.getClass())) {
                            versionIssues.add('lintingType maven\'s junit option must be a Map')
                        } else if (!opts.junit?.testResults) {
                            versionIssues.add('lintingType maven\'s junit option requires the testResults parameter')
                        }
                    }
                    if (!opts?.commandArgs) {
                        versionIssues.add('Missing required field commandArgs for maven lintingType')
                    } else if (!List.isAssignableFrom(opts.commandArgs.getClass())) {
                        versionIssues.add('lintingType maven\'s commandArgs option must be a List')
                    }
                }
                if (lintingType == GRADLE) {
                    if (opts?.junit) {
                        if (!Map.isAssignableFrom(opts.junit.getClass())) {
                            versionIssues.add('lintingType gradle\'s junit option must be a Map')
                        } else if (!opts.junit?.testResults) {
                            versionIssues.add('lintingType gradle\'s junit option requires the testResults parameter')
                        }
                    }
                    //check if commandArgs is present and if so if it is a list. If it's not present it will default later
                    if (opts?.commandArgs && !List.isAssignableFrom(opts.commandArgs.getClass())) {
                        versionIssues.add('lintingType gradle\'s commandArgs option must be a List')
                    }
                }
            } else {
                versionIssues.add(lintingType + ' is not a valid lintingType')
            }
        }
        versionIssues.addAll(super.validate(requiresBranchPattern, phase))
        versionIssues
    }

    /**
     * Encompasses the steps to take when deploying the given lint type
     */
    void run(
        List credsList = [],
        List configsList = []
    ) {
        Map parallelLints = [:]
        config.lintingTypes.each { lintingType, opts ->
            Closure<String> lintClosure = {
                psc.podSelector.select(psc, containerFromLintingType(lintingType), Utils.cloud(config)) {
                    switch (lintingType) {
                        case GOLANG:
                            runGolang()
                            break
                        case BANDIT:
                            runBandit()
                            break
                        case SHELLCHECK:
                            runShellcheck()
                            break
                        case MAVEN:
                            runMaven()
                            break
                        case GRADLE:
                            runGradle()
                            break
                        case PLEASE:
                            runPlease()
                            break
                        case TERRAGRUNT:
                            runTerragrunt()
                            break
                        case SONARQG:
                            runSonarQualityGate(sonarqgContainerName)
                            break
                        case YAMLLINT:
                            runYAMLLint()
                            break
                        case ANSIBLELINT:
                            runAnsibleLint()
                            break
                        case APPROVALREQUEST:
                            runApprovalRequest()
                            break
                    }
                }
            }
            parallelLints[lintingType] = lintClosure
        }

        script.stage(lintingStageName) {
            script.withCredentials(credsList) {
                script.configFileProvider(configsList) {
                    script.dir(baseDirectory) {
                        moveFiles('begin')
                        script.parallel(parallelLints)
                        moveFiles('end')
                    }
                }
            }
        }
    }

    /**
     * Run shellcheck on all shell files
     */
    void runShellcheck() {
        Map<String, Object> shellcheckConfig = config.lintingTypes.get(SHELLCHECK)
        List commandArgs = shellcheckConfig.get('commandArgs', ['--color=never'])
        String excludePaths = shellcheckConfig.get('excludePaths', []).inject('') { str, path ->
            [str, '-not', '-path', '"' + path + '"'].join(' ')
        }
        String outputFilename = 'shellcheck_results.xml'

        if (usesWarningsNG(shellcheckConfig)) {
            commandArgs += ['--format=checkstyle']
        }
        try {
            script.sh("shellcheck ${commandArgs.join(' ')} \$(find . -name '*.*sh' -type f${excludePaths}) " +
                "| tee ${outputFilename}")
        } finally {
            if (usesWarningsNG(shellcheckConfig)) {
                shellcheckConfig.warningsNG.tools = shellcheckConfig.warningsNG.get('tools', []) \
                                        + script.checkStyle(name: 'shellcheck', pattern: outputFilename)
                publishWarningsNG(shellcheckConfig)
            }
        }
    }

    /*
     * Run `golangci-lint` on all specified golang files
     */

    void runGolang() {
        Map<String, Object> golangConfig = config.lintingTypes.get(GOLANG)
        String targets = golangConfig?.get('targets')?.join(' ') ?: './...'
        String timeOut = golangConfig?.get('timeout') ?: '2m'
        String fromRev = golangConfig?.get('fromRev') ?: ''
        boolean vendor = golangConfig?.get('vendor', false) ?: false
        String newFromRev = ''
        if (fromRev != '') {
            newFromRev = " --new-from-rev ${fromRev}"
        }
        script.sh('pwd && ls -l')
        if (vendor) {
            script.sh('go mod vendor -v')
        }
        if (FeatureFlags.verbose) {
            script.sh('$(go env GOPATH)/bin/golangci-lint help linters')
        }
        script.sh('$(go env GOPATH)/bin/golangci-lint ' +
            "run --timeout ${timeOut}${newFromRev} ${targets}")
    }
    /**
     * Run bandit on all specified python files
     */
    void runBandit() {
        Map<String, Object> banditConfig = config.lintingTypes.get(BANDIT)
        List commandArgs = banditConfig.get('commandArgs', [])
        String targets = banditConfig.get('targets', ['-r .']).join(' ')
        String versionSpec = banditConfig.get('versionSpec', '')
        String junitFilename = 'junit-report.xml'

        if (usesJunit(banditConfig)) {
            commandArgs += ['--format', 'xml', '--output', junitFilename]
        }

        script.sh("pip install --upgrade pip && pip install bandit${versionSpec}")

        try {
            script.sh("bandit ${commandArgs.join(' ')} ${targets}")
        } finally {
            if (usesJunit(banditConfig)) {
                publishJunit(banditConfig, junitFilename)
            }
        }
    }

    /**
     * Run yamllint on all yaml/yml files
     */
    void runYAMLLint() {
        Map<String, Object> yamlConfig = config.lintingTypes.get(YAMLLINT)
        List commandArgs = yamlConfig.get('commandArgs', ['-d relaxed'])
        String location = yamlConfig.get('location', '.')
        String junitFilename = 'junit-report.xml'
        String installCmd = 'pip install --upgrade pip && pip install yamllint'
        String findFiles = script.sh(script: "find ${location} -type f -name '*.yaml' -o -name '*.yml'",
            returnStdout: true).trim()

        if (findFiles.length() <= 0) {
            script.sh("echo 'No files found'")
            return
        }

        String runCmd = "yamllint ${commandArgs.join(' ')} ${findFiles}"

        if (usesJunit(yamlConfig)) {
            commandArgs += ['-f', 'parsable']
            installCmd += ' && pip install yamllint-junit'
            runCmd = "yamllint ${commandArgs.join(' ')} ${findFiles} | yamllint-junit -o ${junitFilename}"
        }

        script.sh(installCmd)

        try {
            script.sh(runCmd)
        } finally {
            if (usesJunit(yamlConfig)) {
                publishJunit(yamlConfig, junitFilename)
            }
        }
    }

    /**
     * Run yamllint on all ansible files
     */
    void runAnsibleLint() {
        Map<String, Object> ansibleConfig = config.lintingTypes.get(ANSIBLELINT)

        String projectDir = ansibleConfig.get('projectDir')
        String warningList = ansibleConfig.get('WARN_LIST') ?: ''
        String ignoreFile = ansibleConfig.get('IGNORE_FILE') ?: ''
        String excludePaths = ansibleConfig.get('EXCLUDE_PATHS') ?: ''
        String skipList = ansibleConfig.get('SKIP_LIST') ?: ''

        String constructedWarningList = ''
        String constructedIgnoreFile = ''
        String constructedExcludePaths = ''
        String constructedSkipList = ''

        if (warningList != '' && warningList != null) {
            constructedWarningList = '-w ' + warningList
        }
        if (ignoreFile != '' && ignoreFile != null) {
            constructedIgnoreFile = '-i ' + ignoreFile
        }
        if (excludePaths != '' && excludePaths != null) {
            constructedExcludePaths = '--exclude ' + excludePaths
        }
        if (skipList != '' && skipList != null) {
            constructedSkipList = '-x ' + skipList
        }

        String runCmd = "ansible-lint --offline -p ${projectDir} ${constructedWarningList} " +
            "${constructedIgnoreFile} ${constructedExcludePaths} ${constructedSkipList}"
        script.sh(runCmd)
    }

    /**
     * Run mvn with the provided args
     */
    void runMaven() {
        Map<String, Object> mavenConfig = config.lintingTypes.get(MAVEN)
        String commandArgs = mavenConfig.get('commandArgs', []).join(' ')

        try {
            script.configFileProvider(
                [
                    script.configFile(
                        fileId: MavenBuild.MAVEN_SETTINGS_CONFIG_FILE_ID,
                        variable: 'MAVEN_SETTINGS'
                    )
                ]
            ) {
                script.sh(
                    "mvn -B -Dmaven.repo.local='/tmp/.cache/m2/repository' -gs ${script.MAVEN_SETTINGS} ${commandArgs}"
                )
            }
        } finally {
            if (usesJunit(mavenConfig)) {
                publishJunit(mavenConfig, mavenConfig.junit.testResults)
            }
        }
    }

    /**
     * Run gradle with the provided args
     */
    void runGradle() {
        Map<String, Object> gradleConfig = config.lintingTypes.get(GRADLE)
        String commandArgs = gradleConfig.get('commandArgs', ['clean', 'processResources']).findAll().join(' ')
        try {
            //check the 18 different places we might get a gradle user home dir from
            String gradleUserHome = GradleBuild.findGradleUserHome(script, gradleConfig)

            //put together extra args that might be configured
            String extraArgs = Utils.buildCommandArgs(
                "--info", ['-g', gradleUserHome], gradleConfig.get('extraArgs'))

            //if using gradle wrapper, change command and make the script executable.
            String gradleCommand = GradleBuild.setupGradleCommand(script, gradleConfig.get('gradleWrapperScript'))

            script.sh("${gradleCommand} ${extraArgs} ${commandArgs}")
        } finally {
            if (usesJunit(gradleConfig)) {
                publishJunit(gradleConfig, gradleConfig.junit.testResults)
            }
        }
    }

    /**
     * Run plz lint label
     */
    void runPlease() {
        Map<String, Object> plzConfig = config.lintingTypes.get(PLEASE)
        List<String> modules = plzConfig?.modules
        boolean isTest = plzConfig?.runLintAsTest

        try {
            if (modules) {
                script.echo("Detected a module build for modules ${modules}")
                script.sh(isTest ? PlzUtils.constructMultiModuleTestCommand(plzConfig, ['lint']) :
                    PlzUtils.constructMultiModuleBuildCommand(plzConfig, ['lint']))
            } else if (modules?.isEmpty()) {
                script.echo('Modules is specified, but the list is empty. Nothing to lint.')
            } else {
                script.echo('Detected a full build - executing all lint targets.')
                script.sh(
                    "plz ${isTest ? 'test' : 'build'} //..." +
                        " -i lint${PlzUtils.constructArgs(plzConfig?.extraArgs, plzConfig?.verbosityFlag)}")
            }
            if (!config?.notifyFailuresOnly) {
                notifyLintingStatus('Plz Lint - Completed successfully.')
            }
        } catch (all) {
            notifyLintingStatus('Plz Lint - Failed.')
            if (FeatureFlags.showStackTraces) {
                script.echo(Functions.printThrowable(all))
            }
            throw all
        }
    }

    /**
     * Run Terragrunt lint
     */
    void runTerragrunt() {
        Map<String, Object> terragruntConfig = config.lintingTypes.get(TERRAGRUNT)

        String terragruntVersion = terragruntConfig?.terragruntVersion
        String terraformVersion = terragruntConfig?.terraformVersion

        TerraformDeployment.updateTerraformVersion(script, terraformVersion)
        TerraformDeployment.updateTerragruntVersion(script, terragruntVersion)

        script.echo('Checking terragrunt formatting')
        script.sh('terragrunt hclfmt --terragrunt-check')

        script.echo('Checking terraform formatting')
        script.sh('terraform fmt -recursive -check')
    }

    /**
     * Evaluate SonarQube quality gates for a project since leak period
     */
    void runSonarQualityGate(String sonarContainer) {
        Map<String, Object> sonarConfigMap = config.lintingTypes.get(SONARQG)
        String apiCredentialId = sonarConfigMap.sonarQube.apiCredentialId
        String sonarProjectKey = sonarConfigMap.sonarQube.sonarProjectKey
        String sonarBranch = sonarConfigMap.sonarQube.sonarbranchname ?: script.env.CHANGE_BRANCH ?: script.env.BRANCH_NAME
        String urlBasePath
        String sonarToken
        boolean evaluateQg = sonarConfigMap.sonarQube.evaluateQg

        curlRequestor = curlRequestor ?: new CurlRequestor(psc, script)
        curlRequestor.containerName = sonarContainer

        String urlIssuesApi = "${SONARQUBE_URL}/api/issues/search?resolved=" \
                              + "false&sinceLeakPeriod=true&componentKeys=${sonarProjectKey}" \
                              + "&branch=${sonarBranch}"
        String urlStatusApi = "${SONARQUBE_URL}/api/qualitygates/project_status?" \
                              + "projectKey=${sonarProjectKey}&branch=${sonarBranch}"

        if (apiCredentialId && sonarProjectKey) {
            urlBasePath = evaluateQg ? urlIssuesApi : urlStatusApi
        } else {
            throw new UnsupportedOperationException(
                'Must have sonarQube API credential Id and a project key, if sonarqube'
                    + ' configuration option is included in linting type'
            )
        }
        script.withCredentials([
            script.string(
                credentialsId: "${apiCredentialId}", variable: 'sonarToken'
            )
        ]) {
            Map<String, ?> response = this.curlRequestor.requestJsonWithLiteralCred(
                urlBasePath,
                "${script.sonarToken}:",
                'GET'
            )
            if (response.responseCode == SUCCESSCODE) {
                if (usesWarningsNG(sonarConfigMap)) {
                    publishWarningsNG(sonarConfigMap)
                } else {
                    if (evaluateQg) {
                        def total = response.responseBody.total
                        if (total == 0) {
                            script.echo("No results returned")
                        } else {
                            script.echo("Got following response: ${response.responseBody}")
                        }
                    } else {
                        qualityGateStatus = response.responseBody.projectStatus.status
                        if ("${qualityGateStatus}" == 'OK') {
                            script.echo("Branch: ${sonarBranch}, QG Status: ${qualityGateStatus}")
                        } else {
                            throw new FailedToEvaluateQualityGate(
                                "\n  Branch: ${sonarBranch}\n QualityStatus: ${qualityGateStatus}\n"
                            )
                        }
                    }

                }
            } else {
                throw new FailedToEvaluateQualityGate(
                    "Failed to evaluate the quality gate for the project ${sonarProjectKey}\n"
                        + "Response Code: ${response.responseCode}\n Response Body: ${response.responseBody}\n"
                )
            }
        }
    }

    /**
     * Send an email and post to mattermost
     */
    void notifyLintingStatus(String notifyMessage) {
        Notification notification = new Notification(config: config, script: script)
        notification.notifyWithAllMethods("${notifyMessage}")
    }

    /**
     * Input to approval for deployments
     */
    def runApprovalRequest() {
        Map<String, Object> approvalrequestConfig = config.lintingTypes.get(APPROVALREQUEST)
        String message = approvalrequestConfig.get('message', '')
        String id = approvalrequestConfig.get('id', '')
        int timeOut = approvalrequestConfig.get('timeOut', 5)
        String submitter = approvalrequestConfig.get('submitter', '')
        String exceptionOutcome = approvalrequestConfig.get('failedStatus')

        notification.notifyWithAllMethods("Approval Link: ${script.currentBuild.absoluteUrl}input/")
        script.echo('Give input to Jenkins request')
        script.echo("Submitter: ${submitter}")
        /*
         * this try block is to catch a timeout/abort exception and rethrow an exception that lets us control the pipeline
         * outcome
         */
        try {
            script.timeout(time: "$timeOut", unit: 'MINUTES') {
                script.input(
                    message: message,
                    id: id,
                    submitter: "$submitter"
                )
            }
        } catch (FlowInterruptedException ignored) {
            throw new EndPipelineException('Approval request was not approved', exceptionOutcome ?: 'FAILURE')
        }
    }

    Map<String, String> containerMap = [
        go             : golangContainerName,
        golang         : golangContainerName,
        bandit         : pythonContainerName,
        python         : pythonContainerName,
        shellcheck     : shellcheckContainerName,
        maven          : mavenContainerName,
        gradle         : gradleContainerName,
        plz            : awsDSlimName,
        terragrunt     : awsDSlimName,
        sonarqg        : sonarqgContainerName,
        yamllint       : pythonContainerName,
        yaml           : pythonContainerName,
        ansiblelint    : ansibleContainerName,
        ansible        : ansibleContainerName,
        approvalrequest: 'jnlp'
    ]

    def void containerFromLintingType(def lintingType) {
        containerMap[lintingType]
    }

    def void updateMappedContainerNames(String oldName, String newName) {
        containerMap.each { container ->
            if (container.value == oldName) {
                containerMap[container.key] = newName
            }
        }
    }
}

class FailedToEvaluateQualityGate extends Exception {
    FailedToEvaluateQualityGate(String message) {
        super(message)
    }
}
