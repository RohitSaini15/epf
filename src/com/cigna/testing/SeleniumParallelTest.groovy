package com.cigna.testing

import com.cigna.common.kubernetes.PodConfigGenerator
import com.cigna.common.kubernetes.PodTemplateCreator
import com.cigna.common.utils.Utils

/**
 * Automated parallel test execution in batches. This class runs all specified test executions in
 * parallel in multiple pods.
 */
class SeleniumParallelTest extends Testing {
    SeleniumParallelTest() {
        testType = 'integration'
        containerImage = 'enterprise-devops/maven'
        containerVersion = 'latest'
        containerName = 'mavenvlatest'
    }
    public static final String MAVEN_SETTINGS_CONFIG_FILE_ID = '2c5fdcc7-2376-4dbf-b252-a3a8e83603d9'
    public static final String MAVEN_AUTH_SETTINGS_CONFIG_ID = 'maven-auth-settings'
    protected String githubUrl = 'https://github.sys.cigna.com/cigna/'

    private String mavenSettingsId
    private Boolean mavenAuth
    protected List batches = []
    protected int maxBatches = 10
    private String featureFilePath = '/src/test/resources/parallel/'

    @Override
    Boolean prePodConfig() {
        podTemplateContainerName = PodConfigGenerator.getContainerName("${containerImage}:${containerVersion}")
        List<Map> env = []

        PodTemplateCreator containerTemplate = PodTemplateCreator.newPodTemplateCreator()
        containerTemplate.addContainer(
            containerName,
            "${containerImage}:${containerVersion}",
            'IfNotPresent',
            100,
            500,
            1000,
            2000,
            env
        )

        additionalPodConfig = [
                volumes   : [],
                containers: [containerTemplate.getContainer(containerName)]
        ]

        super.prePodConfig()
    }

    @Override
    void runImpl() {
        List credsList = []
        List configsList = []
        Map parallelTests = [:]
        checkoutProject()
        featureFileCreation()
        configureContainers()

        batches.each { batch ->
            parallelTests[batch] = {
                script.container(batch) {
                    script.ws(batch) {
                        prepBatchExecution(batch)
                        runMaven()
                    }
                }
            }
        }
        // XXX.cnm - TODO - REFACTOR Target - Not yet properly migrated to single pod as this pod is adding multi-batch containers

        script.stage('Executing Test Cases in Parallel') {
            def podLabel = Utils.randomPodLabel(script.env.JOB_NAME)
            script.podTemplate(
                    label: podLabel,
                    yaml: getPodConfig(securityContext, config),
                    workspaceVolume: script.emptyDirWorkspaceVolume(true),
                    cloud: config?.cloudName,
            ) {
                script.node(podLabel) {
                    script.withCredentials(credsList) {
                        script.configFileProvider(configsList) {
                            script.dir(baseDirectory) {
                                moveFiles('begin')
                                script.parallel(parallelTests)
                                moveFiles('end')
                            }
                        }
                    }
                }
            }
        }
    }

    void configureContainers() {
        List<Map<String, Object>> list = []
        batches.each { batch ->
            Map<String, Object> containerSpec = [
                    name        : batch,
                    image       : "${containerImage}:${containerVersion}",
                    tty         : true,
                    workingDir  : '/home/jenkins/agent',
                    command     : com.cigna.common.utils.Utils.defaultSidecarCommand,
                    volumeMounts: [],
                    resources   : [
                            requests    : [
                                    cpu   : '10m',
                                    memory: '50Mi'
                            ],
                            limits      : [
                                    cpu   : containerCpu,
                                    memory: containerMemory
                            ],
                            name        : 'containerName',
                            image       : "${containerImage}:${containerVersion}",
                            tty         : true,
                            workingDir  : '/home/jenkins/agent',
                            command     : com.cigna.common.utils.Utils.defaultSidecarCommand,
                            volumeMounts: [],

                    ]
            ]
            list.add(containerSpec)
        }
        additionalPodConfig.put('containers', list)
    }

    /**
     * Run mvn with the provided args
     */
    void runMaven() {
        mavenAuth = config.maven?.authSettings ?: false
        mavenSettingsId = mavenAuth ? MAVEN_AUTH_SETTINGS_CONFIG_ID : MAVEN_SETTINGS_CONFIG_FILE_ID
        if (!testingConfiguration?.zephyrReleaseId) {
            throw new UnsupportedOperationException('Please provide a valid Zephyr release id')
        }

        script.configFileProvider([
                script.configFile(
                        fileId: mavenSettingsId,
                        variable: 'MAVEN_SETTINGS'
                )
        ]) {
            script.withCredentials([
                    script.string(
                            credentialsId: testingConfiguration?.zephyrCredentialId,
                            variable: 'ZEPHYR_API_TOKEN'
                    )
            ]) {
                script.sh(
                        """mvn -B -q -s ${script.MAVEN_SETTINGS} \
                   -Dmaven.repo.local='/tmp/.cache/m2/repository' \
                   -Dtarget='grid' \
                   -Dheadless='true' \
                   -Drelease.id=${testingConfiguration?.zephyrReleaseId} \
                   -Dzephyr.token=${script.ZEPHYR_API_TOKEN} clean verify""".stripIndent()
                )
            }
        }
    }

    /**
     * Checkout Selenium project needed for executing test cases
     */
    protected void checkoutProject() {
        if (!testingConfiguration?.gitRepo) {
            throw new UnsupportedOperationException('Please provide a valid Git Repo')
        }
        if (!testingConfiguration?.gitcredentialID) {
            throw new UnsupportedOperationException('Please provide a valid Git Credential Id')
        }
        githubUrl += testingConfiguration?.gitRepo + '.git'
        script.checkout(changelog: false, poll: false, scm: [$class                           : 'GitSCM',
                                                                   branches                         : [[name: "*/${script.env.BRANCH_NAME}"]],
                                                                   doGenerateSubmoduleConfigurations: false,
                                                                   submoduleCfg                     : [],
                                                                   userRemoteConfigs                : [[
                                                                                                               credentialsId: testingConfiguration?.gitcredentialID,
                                                                                                               url          : "${githubUrl}"
                                                                                                       ]],
        ])
    }

    /**
     * Create feature files by running dataTable shell script
     */
    protected void featureFileCreation() {
        String testCaseIds = testingConfiguration?.testCaseIds ?: ''
        if (!testCaseIds) {
            throw new UnsupportedOperationException('Please provide a valid Test Case id ranges')
        }
        int testCaseCount = testCaseIds.split(':')[1].toInteger() -
                testCaseIds.split(':')[0].toInteger()
        int recommendedCount = Math.ceil(testCaseCount / maxBatches)
        int batchCount = testingConfiguration?.batchCount ?: recommendedCount

        int batchIteration = Math.ceil(testCaseCount / batchCount)
        if (batchIteration > maxBatches) {
            throw new UnsupportedOperationException("Batch count is too low, recommended value is $recommendedCount")
        }

        if (testingConfiguration?.featureFilePath) {
            featureFilePath = testingConfiguration?.featureFilePath
        }

        String dataTableScript = testingConfiguration?.dataTableScript ?: ''
        if (!dataTableScript) {
            throw new UnsupportedOperationException('Please provide a data table creation shell script')
        }
        script.sh(script: "chmod +x ${dataTableScript}")
        int batchIterationcnt = script.sh(script:
                "./${dataTableScript} ${testCaseIds} ${batchCount} ${featureFilePath}", returnStdout: true).toInteger()
        script.echo("batches are $batchIterationcnt")
        for (int i in 1..batchIterationcnt) {
            batches.add("batch$i")
        }
        script.stash('checkout-project')
    }
    /**
     * Adds container specifications for all batches provided to
     * the additionalPodConfig that is used to determine this phase's podTemplate
     *
     * Map containing podTemplate container and volume specifications for each
     * batch of test execution.
     */
    /**
     * Prep for batch execution of test cases
     */
    protected void prepBatchExecution(String batchId) {
        script.unstash('checkout-project')
        script.sh(script: "rm -f -- $featureFilePath/scenario.feature")
        script.sh(script: "mv ${batchId}.feature $featureFilePath/${batchId}.feature")
    }
}
