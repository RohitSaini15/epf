package com.cigna.builds

import com.cigna.common.kubernetes.PodTemplateCreator
import com.cigna.common.utils.BarBuilder
import com.cigna.common.utils.Utils

import static com.cigna.common.utils.Utils.calculateContainerName

/**
 * For builds utilzing ace.
 */
class AceBuild extends Build {
    // cmd line to create bar files
    // ALERT: When updating the IBM ACE base version, ensure these values are updated accordingly.
    protected static final String ACE_VERSION = 'ace-12.0.12.0'
    protected static final String CREATE_BAR_COMMAND = "xvfb-run /opt/ibm/$ACE_VERSION/tools/mqsicreatebar"
    // cmd line to show bar file contents
    protected static final String SHOW_BAR_COMMAND = '/opt/ibm/show-bar.sh'
    // link to IBM command responses page
    protected static final String IBM_COMMAND_RESPONSES_URL = 'https://www.ibm.com/docs/en/app-connect/12.0'
    // BAR_FOLDER is relative to repo root folder
    protected static final String BAR_FOLDER = 'barfiles'

    protected String buildContainerImage = 'enterprise-devops/epf-ace'
    protected String buildContainerVersion = '1.0.0-v12.0.12.0'
    protected String buildContainerCpu = '3000m'
    protected String buildContainerMemory = '6000Mi' // ACE image is huge

    protected String publishContainerName = 'jfrogvprod'
    protected String publishContainerImage = 'enterprise-devops/jfrog'
    protected String publishContainerVersion = 'prod'

    // number of .bar files created
    int numBarFilesCreated = 0

    AceBuild() {
        containerImage = buildContainerImage
        containerVersion = buildContainerVersion
        containerCpu = buildContainerCpu
        containerMemory = buildContainerMemory
        containerName = calculateContainerName(buildContainerImage, buildContainerVersion)
    }

    @Override
    Boolean prePodConfig() {
           List<Map> env = []

            PodTemplateCreator containerTemplate = PodTemplateCreator.newPodTemplateCreator()
            containerTemplate.addContainer(
                containerName,
                "${buildContainerImage}:${buildContainerVersion}",
                100,
                1000,
                500,
                1000,
                env
            )

            env = [
                [
                    name : 'JFROG_CLI_HOME_DIR',
                    value: '/tmp'
                ]
            ]

            containerTemplate.addContainer(
                publishContainerName,
                "${publishContainerImage}:${publishContainerVersion}",
                100,
                1000,
                500,
                1000,
                env
            )
        containerTemplate.addVolumeMount(containerName, 'setup-sonar', '/home/jenkins/agent/setup-sonar')

       additionalPodConfig = [
            'volumes'   : [],
            'containers': [containerTemplate.getContainer(containerName),
                           containerTemplate.getContainer(publishContainerName)]
        ]

        super.prePodConfig()
    }


    // override from Build.groovy method, might need to add specific ace logic here
    // this sets up a git container and certs (pretty plain)
    @Override
    void executePreBuildStage() {
        commonPreBuildSteps()
    }

    // meat and potatoes of ace methods/logic
    @Override
    void executeBuildAndTestStage() {
        BarBuilder bb = new BarBuilder(psc, script, config, containerName,
                BAR_FOLDER, CREATE_BAR_COMMAND, SHOW_BAR_COMMAND, IBM_COMMAND_RESPONSES_URL)
        bb.showProperties()
        numBarFilesCreated = bb.createBarFiles()
    }

    // used to publish bar to artifactory
    @Override
    void executePublishStage() {
        if (numBarFilesCreated == 0) {
            script.echo('INFO: Skipping Publish Stage since no .bar files were created.')
            return
        }

        String tmpServerId = 'artifactory-server-id'
        //String applicationName = config?.artifactory?.applicationName

        psc.podSelector.select(psc,publishContainerName, Utils.cloud(config)) {
            script.withCredentials(
                    [
                            script.usernamePassword(
                                    credentialsId: "${config.artifactory.credentialsId}",
                                    usernameVariable: 'ARTIFACTORY_USER',
                                    passwordVariable: 'ARTIFACTORY_KEY'
                            )
                    ]
            ) {
                script.echo('Establishing the Artifactory server details')
                script.sh(
                        script: "jf c add ${tmpServerId} --url=${config.artifactory.host}"
                                + " --user=${script.env.ARTIFACTORY_USER} --password=${script.env.ARTIFACTORY_KEY}"
                                + ' --interactive=false --enc-password=false',
                )
                script.echo('Checking the Artifactory connection details')
                script.sh(
                        script: "jf c show ${tmpServerId}",
                )
                script.echo('Pinging the Artifactory server to ensure its up and running')
                script.sh(
                        script: "jf rt ping --server-id=${tmpServerId}",
                )
                List<Map<String, Object>> barfiles = script.findFiles(glob: "${BAR_FOLDER}/*.bar")
                for (bf in barfiles) {
                    String[] artifactoryApplication = bf.name.split('-')
                    String jfrogExtraargs = artifactoryApplication[1]
                    String[] jfrogFolderName = jfrogExtraargs.split('_')
                    String jfrogFolder = config?.extraArgs ?: "${script.env.GIT_BRANCH}_${jfrogFolderName[0]}_${jfrogFolderName[1]}"
                    script.echo("Uploading ${bf.path} to Artifactory")
                    script.sh(
                        script: 'jf rt u'
                        + " ${bf.path} ${config.artifactory.repoPath}/${artifactoryApplication[0]}/${jfrogFolder}/${bf.name}",
                    )
                }
            }
        }
    }
}