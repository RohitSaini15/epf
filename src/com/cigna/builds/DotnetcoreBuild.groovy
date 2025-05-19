package com.cigna.builds

import com.cigna.common.naming.NameRegistry
import static com.cigna.common.utils.Utils.calculateContainerName
import com.cigna.common.kubernetes.PodTemplateCreator

/**
 * Class to handle .NET Core builds in EPF
 */
class DotnetcoreBuild extends Build {
    // We will need to make these default values available so that the SonarScanner will be able to properly reference
    // the image name in the event that the scanner is invoked before (or without) a dotnetcore phase
    public static final String LOGICAL_CONTAINER_NAME = 'dotnetcore'
    public static final String DEFAULT_CONTAINER_IMAGE = 'enterprise-devops/dotnetcore-sdk'
    public static final String DEFAULT_CONTAINER_VERSION = '1.0.1-v6.0'

    DotnetcoreBuild() {
        containerImage = DEFAULT_CONTAINER_IMAGE
        containerVersion = DEFAULT_CONTAINER_VERSION
        containerName = calculateContainerName(containerImage, containerVersion)
        containerImagePullPolicy = 'Always'
    }

    @Override
    Boolean prePodConfig() {
        List<Map> env = [
            [
                name : 'HOME',
                value: '/tmp'
            ]
        ]
        PodTemplateCreator containerTemplate = PodTemplateCreator.newPodTemplateCreator()
        containerTemplate.addContainer(
            containerName, "${containerImage}:${containerVersion}",
            25, 2000, 1000, 2000, env
        )
        containerTemplate.addVolumeMount(containerName, 'setup-sonar', '/home/jenkins/agent/setup-sonar')

        additionalPodConfig = [
            'volumes'   : [],
            'containers': [containerTemplate.getContainer(containerName)]
        ]

        super.prePodConfig()
    }

    @Override
    Boolean postPodConfig() {
        // it may get updated during prePodConfig() processing
        containerName = calculateContainerName(containerImage, containerVersion)
        psc.nameRegistry.registerName(LOGICAL_CONTAINER_NAME, containerName)
        super.postPodConfig()
    }

    // XXX.cnm - TODO : this code needs deeper cleanup and simplification
    //           TODO : Step 1. Introduce `commonArgs` and assign to each cmd (DONE)
    //           TODO : Step 2. Deprecate existing phase arguments `noRestore`, `configFile`, `project`
    //           TODO : Step 3. Announce deprecation to small set of existing DotNetCore users (~3 projects)
    //           TODO : Step 4. Replace existing phase args with commonArgs and remove deprecated args
    @Override
    void executeBuildAndTestStage() {
        config.dotnet = config.dotnet ?: [:]
        config.dotnet.commonArgs = config.dotnet.get('commonArgs', '')
        config.dotnet.configFile = config.dotnet.containsKey('configFile') ?
            "${config.dotnet.configFile}" : ''
        config.dotnet.noRestore = config.dotnet.containsKey('noRestore') ?
            "${config.dotnet.noRestore}" : false
        config.dotnet.project = config.dotnet.containsKey('project') ?
            "${config.dotnet.project}" : ''
        config.dotnet.outputDir = config.dotnet.containsKey('outputDir') ?
            "${config.dotnet.outputDir}" : 'output'
        if (config.dotnet.testLogger) {
            config.dotnet.testLogger = config.dotnet.containsKey('testLogger') ?
                "${config.dotnet.testLogger}" : 'xunit'

            config.dotnet.testParams = config.dotnet.containsKey('testParams') ?
                "${config.dotnet.testParams}" : ''
        }

        String resCommand = 'dotnet restore'
        String testCommand = 'dotnet test'
        String pubCommand = 'dotnet publish'

        if (config.dotnet.configFile) {
            resCommand += " --configfile ${config.dotnet.configFile}"
            testCommand += " --configfile ${config.dotnet.configFile}"
            pubCommand += " --configfile ${config.dotnet.configFile}"
        }
        if (config.dotnet.version) {
            pubCommand += " -p:Version=${config.dotnet.version}"
        }
        if (config.dotnet.noRestore) {
            testCommand += ' --no-restore'
            pubCommand += ' --no-restore'
        }
        resCommand += " ${config.dotnet.project}"
        testCommand += " ${config.dotnet.project}"
        pubCommand += " ${config.dotnet.project}"
        if (config.dotnet.commonArgs) {
            resCommand += " ${config.dotnet.commonArgs}"
            testCommand += " ${config.dotnet.commonArgs}"
            pubCommand += " ${config.dotnet.commonArgs}"
        }
        script.sh(resCommand)
        if (config.dotnet.testLogger) {
            script.sh(testCommand + " ${config.dotnet.testParams} --logger:${config.dotnet.testLogger} " +
                "-p:CollectCoverage=true -p:CoverletOutputFormat=opencover")
        }
        script.sh(pubCommand + " -c Debug -o ${config.dotnet.outputDir}-debug")
        script.sh(pubCommand + " -c Release -o ${config.dotnet.outputDir}")
    }

    @Override
    void executePublishStage() {
        if (config.containsKey('nugetPath')) {
            def nugetRepo = config.nugetRepo ?: 'cigna-nuget-lower'
            def artifactoryUploadUrl = "${ARTIFACTORY_API_URL}/nuget/$nugetRepo/${config.nugetPath}"
            script.withCredentials(
                [
                    script.usernamePassword(
                        credentialsId: "${config.artifactory.credentialsId}",
                        usernameVariable: 'ARTIFACTORY_USER',
                        passwordVariable: 'ARTIFACTORY_APIKEY'
                    )
                ]
            ) {
                String packCommand = 'dotnet pack -o nuget -c Release'
                if (config.containsKey('dotnet') && config.dotnet.containsKey('version')) {
                    packCommand += " -p:Version=${config.dotnet.version}"
                }

                script.sh("${packCommand}")
                script.sh(
                    """
                    for file in ./nuget/*.nupkg
                    do
                        dotnet nuget push \
                        \$file \
                        --source ${artifactoryUploadUrl} \
                        --api-key ${script.ARTIFACTORY_USER}:${script.ARTIFACTORY_APIKEY}
                    done
                """.stripIndent()
                )
            }
        } else {
            script.echo('No Nuget path provided, EPF will not upload')
        }
        // Leave this in so when we move to Cache the debug assemblies are removed
        script.sh("rm -rf ${config.dotnet.outputDir}-debug")
    }
}
