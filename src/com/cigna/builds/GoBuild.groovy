package com.cigna.builds

import com.cigna.common.utils.GoEnvBuilder
import com.cigna.common.kubernetes.PodTemplateCreator

/**
 * For builds utilzing Go.  This build type uses a tool call goreleaser to compile
 * and deploy a binary to GitHub, GitLab, and Artifactory.  There is logic contained
 * here to initialize and configure goreleaser if the consuming team has not already.
 *
 * NOTE: goreleaser will only publish on a tag.
 */
class GoBuild extends Build {
    GoBuild() {
        containerImage = 'enterprise-devops/golang'
        containerVersion = 'ubi9-go-1.20'
        containerName = 'golangvubi9-go-120'
        containerMemory = '2500Mi'
        containerCpu = '700m'

        additionalValidationItems = ['golang.apiTokenId', 'golang.tokenType']
    }

    @Override
    Boolean prePodConfig() {

        List<Map> env =  [
           [ name : 'HOME',
            value: '/tmp' ]
        ]

        PodTemplateCreator containerTemplate = PodTemplateCreator.newPodTemplateCreator()
        containerTemplate.addContainer(
            containerName,
            "${containerImage}:${containerVersion}",
            100,
            1000,
            700,
            2500,
            env
        )
        containerTemplate.addVolumeMount(containerName, 'setup-sonar', '/home/jenkins/agent/setup-sonar')

        additionalPodConfig = [
            'volumes'   : [],
            'containers': [containerTemplate.getContainer(containerName)]
        ]

        super.prePodConfig()
    }

    public static final String GITLAB_BASE_URL = 'https://git.sys.cigna.com/'
    public static final String GITLAB_API = 'api/v4/'

    public static final String GITHUB_BASE_URL = 'https://github.sys.cigna.com/'
    public static final String GITHUB_API = 'api/v3/'

    String extraArgs = ''

    /**
     * This stage will run go test
     */
    @Override
    void executeBuildAndTestStage() {
        String opts = ''
        String command = 'test'
        if (config.golang?.buildMode) {
            List<String> supportedModes = ['plugin', 'pie', 'shared', 'c-shared', 'archive', 'c-shared', 'module']
            if (!( config.golang?.buildMode in supportedModes )) {
                throw new InvalidGoBuildModeException("The '${config.golang.buildMode}' buildMode is not supported " +
                    "(valid values are $supportedModes)")
            }
            if (config?.golang?.buildMode != 'module') {
                opts = " -buildmode=${config.golang.buildMode}"
                command = 'build'
            }
        }

        goBuild(command, opts, configurePackaging())
    }

    @Override
    void executePublishStage() {
        String packageName = configurePackaging()

        //goreleaser uses env variable to control release to GitLab or GitHub
        String tokenEnvName = 'GITLAB_TOKEN'
        if (config.golang.tokenType == 'GitHub') {
            tokenEnvName = 'GITHUB_TOKEN'
        }

        script.withCredentials([
            script.string(
                credentialsId: "${config.golang.apiTokenId}",
                variable: 'apiToken')
        ]) {
            String mainName = ''
            if (config.golang?.mainName) {
                mainName = config.golang.mainName
            }

            String goRepoName = 'go-repos'
            if (config.golang?.repoName) {
                goRepoName = config.golang.repoName
            }

            withPhaseConfigEnv(["${tokenEnvName}=${script.apiToken}"]) {
                if (packageName != null && !packageName.empty) {
                    initMultiGoReleaser(packageName, mainName, goRepoName, config?.golang?.buildMode == 'plugin')
                } else {
                    initGoReleaser()
                }

                if (config?.artifactory?.credentialsId) {
                    // These are the env. variable names required by the goreleaser tool.
                    // SEE: https://goreleaser.com/customization/artifactory/#password-api-key
                    script.withCredentials(
                            [
                                    script.usernamePassword(
                                            credentialsId: "${config.artifactory.credentialsId}",
                                            usernameVariable: 'ARTIFACTORY_PRD_USERNAME',
                                            passwordVariable: 'ARTIFACTORY_PRD_SECRET'
                                    )
                            ]
                    ) {
                        goRelease()
                    }
                } else {
                    goRelease()
                }
            }
        }
    }



    private void initGoReleaser() {
        String releaseSettings = """
gitlab_urls:
    api: ${GITLAB_BASE_URL}${GITLAB_API}
    download: ${GITLAB_BASE_URL}
"""

        if (config.golang.tokenType == 'GitHub') {
            releaseSettings = """
github_urls:
    api: ${GITHUB_BASE_URL}${GITHUB_API}
    upload: ${GITHUB_BASE_URL}api/uploads/
"""
        }

        maybeGenerateGoReleaser(releaseSettings)
    }

    private String configurePackaging() {
        String packageName = null
        if (config.golang?.packageName) {
            packageName = config.golang?.packageName
        }

        packageName
    }

    private List<String> find_packages(String packageName) {
        List<String> plugins = []
        script.dir("${script.env.WORKSPACE}/$packageName") {
            script.findFiles().each {
                if (it.directory) {
                    plugins << it.name
                }
            }
        }
        script.echo("Go Build: Processing $plugins")
        plugins
    }

    private void checkAndCleanWorkspace() {
        boolean hasGitignore = script.fileExists('.gitignore') ?: false
        if (hasGitignore) {
            String contents = script.readFile '.gitignore'
            if (!contents.contains('.gitignore')) {
                script.writeFile(file: '.gitignore', text: contents + '\n.gitignore')
            }
        }
        script.sh('git clean -fd')
    }

    private void goRelease() {
        //goreleaser checks git status before running
        //adding git clean because the working directory is polluted with
        //cigna_certs.pem and sonar-project.properties.
        checkAndCleanWorkspace()

        //goreleaser only publishes on tags, add --snapshot when we are not building a tag
        boolean isBuildingTag =
            script.sh(returnStdout: true, script: 'git tag --contains')?.trim()?.length() > 0

        if (!isBuildingTag) {
            extraArgs = extraArgs + ' --snapshot'
        }
        if (config?.golang?.debug) {
            extraArgs += ' --debug'
        }

        if (config?.golang?.args) {
            config?.golang?.args.each {
                extraArgs += " ${it}"
            }
        }

        def cmdline = GoEnvBuilder.buildGoEnv(config?.golang?.proxy) + "goreleaser release ${extraArgs}"

        if (config?.golang?.debug) {
            script.echo("Building Go Project with cmd line '${cmdline}'")
        }

        script.sh(cmdline)
    }

    private void goBuild(String command, String opts, String packageName) {
        String pkgName = packageName ?: './...'
        script.sh(GoEnvBuilder.buildGoEnv(config?.golang?.proxy) + "go ${command}${opts} $pkgName")
    }

    private void maybeGenerateGoReleaser(String releaseSettings, boolean shouldInit = true) {
        boolean releaserFileExists = script.fileExists('./.goreleaser.yml')
        //create a goreleaser file if none exists, then append settings for release environment
        if (!releaserFileExists) {
            if (shouldInit) {
                script.sh(GoEnvBuilder.buildGoEnv(config?.golang?.proxy) + 'goreleaser init')
            } else {
                script.sh('echo \'#Creating Multi Build GoReleaser\' > .goreleaser.yml')
            }
            script.sh("echo '${releaseSettings}' >> .goreleaser.yml")
            //goreleaser checks git status before running, so if we are configuring on the fly,
            //we need to move the file to a temporary location.
            script.sh('mkdir -p /tmp/goreleaser && mv .goreleaser.yml /tmp/goreleaser')
            if (config?.golang?.debug == true) {
                script.sh('cat /tmp/goreleaser/.goreleaser.yml')
            }
            extraArgs = '-f /tmp/goreleaser/.goreleaser.yml'
        }
    }

    private String generateFlags() {
        String flags = ''
        config?.golang?.flags.each {
            flags = flags << "      - $it\n"
        }
        flags
    }

    private void initMultiGoReleaser(String packageName, String mainName, String goRepoName, Boolean isIndividual = true) {
        String segments = ''

        if (isIndividual) {
            List<String> individualPackages = find_packages(packageName)
            individualPackages.each {
                segments = segments <<
                    """
                |  - main: ./$packageName/$it$mainName
                |    id: "$it"
                |    binary: ${it}.so
                |    flags:
                 |      - -v
                |${generateFlags()}
                |    ldflags:
                |      - -E
                |      - -pluginpath=$it
                |    goos:
                |      - linux
                |    goarch:
                |      - amd64
                |""".stripMargin()
            }
        } else {
            segments = """
  - main: ${packageName}/${mainName}
"""
        }

        String moduleCfg = ''
        if (config?.golang?.module == true) {
            moduleCfg = '\ngomod:\n  proxy: true'
        }
        maybeGenerateGoReleaser(
            """
artifactories:
  - name: prd
    mode: binary
    checksum: true
    target: ${artifactoryByEnv()}/$goRepoName/{{ .ProjectName }}/{{ .Version }}/{{ .Os }}/{{ .Arch }}
release:
  disable: true
archives:
  - format: binary${moduleCfg}
builds:
$segments
""", false)
    }

    private String artifactoryByEnv() {
        config.artifactory?.url ? config.artifactory.url : ARTIFACTORY_URL
    }
}

class InvalidGoBuildModeException extends Exception {
    InvalidGoBuildModeException(String message) {
        super(message)
    }
}

class InvalidGoPackagingTypeException extends Exception {
    InvalidGoPackagingTypeException(String message) {
        super(message)
    }
}
