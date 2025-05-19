package com.cigna.deployment

import com.cigna.common.kubernetes.PodConfigGenerator
import com.cigna.common.naming.NameRegistry
import com.cigna.common.phases.PodSelector
import com.cigna.common.utils.Utils
import static com.cigna.common.utils.Utils.calculateContainerName

/**
 * Class to provide an opinionated helm deployment
 */
class HelmDeployment extends Deployment {

    protected static final String ENV_HOME = 'HOME'
    protected static final String VOL_HOME = 'shared-tmp-dir'

    public static final String LOGICAL_HELM_CONTAINER_NAME = 'helm'
    public static final String HELM_CONTAINER_NAME = 'helmv3133'
    public static final String HELM_CONTAINER_IMAGE = 'enterprise-devops/helm'
    public static final String HELM_CONTAINER_VERSION = '3.13.3'

    public static final String LOGICAL_OC_CONTAINER_NAME = 'oc-cli'
    public static final String OC_CONTAINER_NAME = 'oc-cliv101'
    public static final String OC_CONTAINER_IMAGE = 'enterprise-devops/oc-cli'
    public static final String OC_CONTAINER_VERSION = '1.0.1'
    HelmDeployment() {
        basePodConfig = [
                volumes: [
                        [
                                name    : VOL_HOME,
                                emptyDir: [
                                        medium: ''
                                ]
                        ]
                ],
                containers: []
        ]
        additionalValidationItems = [
                'helm.serverUrl',
                'helm.namespace',
                'helm.credentialsId',
                'helm.deploymentName',
                'helm.values',
        ]
        containerImage = HELM_CONTAINER_IMAGE
        containerVersion = HELM_CONTAINER_VERSION
        containerName = calculateContainerName(containerImage, containerVersion)

        deployStatus = 'Deploy'
    }

    @Override
    Boolean prePodConfig() {
        psc.nameRegistry.registerName(LOGICAL_HELM_CONTAINER_NAME, containerName)
        def helmContainer = [
                origname       : containerName,
                name           : containerName,
                image          : "${containerImage}:${containerVersion}",
                imagePullPolicy: containerImagePullPolicy,
                tty            : true,
                workingDir     : '/home/jenkins/agent',
                command        : com.cigna.common.utils.Utils.defaultSidecarCommand,
                env            : [
                        [
                                name : ENV_HOME,
                                value: '/tmp'
                        ],
                ],
                resources      : [
                        requests: [
                                cpu   : '100m',
                                memory: containerMemory
                        ],
                        limits  : [
                                cpu   : containerCpu,
                                memory: containerMemory
                        ]
                ],
                volumeMounts   : [
                        [
                                name     : VOL_HOME,
                                mountPath: '/tmp'
                        ]
                ]
        ]

        def ansibleContainer =  [
                                name      : vaultName,
                                image     : "${vaultImage}:${vaultVersion}",
                                tty       : true,
                                workingDir: '/home/jenkins/agent',
                                env       : [
                                        [
                                                name : ENV_HOME,
                                                value: '/home/jenkins/agent'
                                        ],
                                ],
                                resources : [
                                        requests: [
                                                cpu   : '100m',
                                                memory: '100Mi'
                                        ],
                                        limits  : [
                                                cpu   : '100m',
                                                memory: '100Mi'
                                        ]
                                ]
                        ]
        def ocContainerName = calculateContainerName(OC_CONTAINER_IMAGE, OC_CONTAINER_VERSION)
        psc.nameRegistry.registerName(LOGICAL_OC_CONTAINER_NAME, ocContainerName)
        def ocContainer = [
                origname    : ocContainerName,
                name        : ocContainerName,
                image       : "${OC_CONTAINER_IMAGE}:${OC_CONTAINER_VERSION}",
                tty         : true,
                workingDir  : '/home/jenkins/agent',
                env         : [
                        [
                                name : ENV_HOME,
                                value: '/tmp'
                        ]
                ],
                resources   : [
                        requests: [
                                cpu   : '25m',
                                memory: '25Mi'
                        ],
                        limits  : [
                                cpu   : '100m',
                                memory: '100Mi'
                        ]
                ],
                volumeMounts: [
                        [
                                name     : VOL_HOME,
                                mountPath: '/tmp'
                        ]
                ]
        ]
        basePodConfig.containers += helmContainer
        basePodConfig.containers += ansibleContainer //can be customized when required
        basePodConfig.containers += ocContainer

        super.prePodConfig()
    }
    Boolean useRollbackCode = true

    String LOGICAL_VAULT_NAME = 'ansible'
    String vaultName = 'ansiblevlatest'
    String vaultImage = 'cigna-digital/ansible'
    String vaultVersion = 'latest'

    @Override
    Boolean postPodConfig() {
        def finalizedImage = basePodConfig.containers.find { it.origname == HELM_CONTAINER_NAME }.image
        def helmContainerName = PodConfigGenerator.getContainerName(finalizedImage)
        psc.nameRegistry.registerName(LOGICAL_HELM_CONTAINER_NAME, helmContainerName)
        finalizedImage = basePodConfig.containers.find { it.origname == OC_CONTAINER_NAME }.image
        def ocContainerName = PodConfigGenerator.getContainerName(finalizedImage)
        psc.nameRegistry.registerName(LOGICAL_OC_CONTAINER_NAME, ocContainerName)
        psc.nameRegistry.registerName(LOGICAL_VAULT_NAME, vaultName)
        basePodConfig.containers.each { Map container -> container.remove('origname') }
        super.postPodConfig()
    }
    @Override
    void deploy() {
        psc.podSelector.select(psc,psc.nameRegistry.nameFor(LOGICAL_HELM_CONTAINER_NAME), Utils.cloud(config)) {
            if (config.helm?.chart) {
                String chartPrefix = config.helm.chart.tokenize('/')[0]

                if (config.containsKey('vault')) {
                    extractVaultFiles()
                }
                if (config.containsKey(preClean)) {
                    executePreClean()
                }
                if (chartPrefix.contains('cigna.com')) {
                    // Remote chart, need to pull
                    String chartName = pullChart()
                    helmDeployChart(chartName)
                } else {
                    // chart is local, run directly
                    helmDeployChart(config.helm.chart)
                }
            } else {
                String helmChartPath = config.helm?.helmChartPath
                String helmCommand = script.sh(
                        script: "helm package ${helmChartPath}",
                        returnStdout: true
                ).trim()
                String getChart = helmCommand.tokenize('/')[-1]
                String artifactoryCreds = config.helm?.artifactory?.credentialsId
                String artifactoryRepo = config.helm?.artifactoryRepo
                String artifactoryFolder = config.helm?.artifactoryFolder
                pushHelmChartToArtifactory(getChart, artifactoryCreds, artifactoryRepo, artifactoryFolder)
                pullChartAndDeployToOscp(getChart, artifactoryRepo, artifactoryFolder)
                helmDeployChart(helmChartPath)
            }
        }
    }

    /**
     * Run helm rollback on test failure
     */
    void rollback() {
        psc.podSelector.select(psc,psc.nameRegistry.nameFor(LOGICAL_HELM_CONTAINER_NAME), Utils.cloud(config)) {
            String helmCommand = "helm rollback ${config.helm.deploymentName} " \
                 + "-n ${config.helm.namespace} " \
                 + '--cleanup-on-fail '
            executeHelmCommand(helmCommand)
        }
        if (this.ticket) {
            updateTicket('deployRollback', 'Helm Deployment failed, attempting rollback')
        }
    }

    void extractVaultFiles() {
        if (config.containsKey('vault') && config.vault.containsKey('files')) {
            psc.podSelector.select(psc,vaultName, Utils.cloud(config)) {
                script.withCredentials(
                        [script.string(
                                credentialsId: config.vault.credentialsId,
                                variable: 'VAULT_PASS')
                        ]) {
                    // print vault pass to file because vault is just like that
                    script.sh("set +x; echo \'${script.VAULT_PASS}\' > vaultPass")
                    config.vault.files.each {
                        String suffix = config.vault.suffix ?: '.enc'
                        script.sh('ansible-vault --version')
                        script.sh(
                                "ansible-vault decrypt ${it} " +
                                        "--output \$(dirname ${it})/\$(basename ${it} ${suffix}) " +
                                        '--vault-password-file vaultPass'
                        )
                        script.sh("chmod +rx \$(dirname ${it})/\$(basename ${it} ${suffix})")
                    }
                    script.sh('rm vaultPass')
                }
            }
        }
    }

    /**
     * Tar up Helm chart and push chart to Artifactory
     */
    void pushHelmChartToArtifactory(String chartBinary, String artifactoryCred, String repo, String folder) {
        script.withCredentials([script.usernamePassword(
                credentialsId: artifactoryCred,
                passwordVariable: 'ARTIFACTORY_SECRET',
                usernameVariable: 'ARTIFACTORY_USER')]
        ) {
            script.sh(
                    script: """
                    curl --basic -u ${script.ARTIFACTORY_USER}:${script.ARTIFACTORY_SECRET} \
                    -X PUT ${repo}/${folder}/${chartBinary} -T ${chartBinary}
                """,
                    returnStdout: true
            ).trim()
        }
    }

    /**
     * Pull Helm chart from Artifactory
     */
    void pullChartAndDeployToOscp(String chartBinary, String repo, String folder) {
        script.sh(
                script: """
                curl -O '${repo}/${folder}/${chartBinary}'
            """,
                returnStdout: true
        ).trim()
    }

    String pullChart() {
        String chartVersion = config.helm?.chartVersion ?: 'latest'
        script.sh("helm quay pull -k ${config.helm.chart}:${chartVersion} --tarball")

        List chartUrlTokens = config.helm.chart.tokenize('/')

        String tarball = script.sh(
                script: "find . -iname '${chartUrlTokens[1]}_${chartUrlTokens[2]}*.tar.gz'",
                returnStdout: true
        ).trim()
        script.sh("tar xfz ${tarball}")
        chartUrlTokens[2]
    }

    void helmDeployChart(String chartPath) {
        String valuesParam = config.helm.values.collect { "-f ${it}" }.join(' ')
        String setParam = config.helm.setValues.collect { key, value -> "--set ${key}=${value}" }.join(' ')
        Integer historyMax = config.helm.historyMax ?: 3

        String helmCommand = "helm upgrade ${config.helm.deploymentName} " \
                 + "${chartPath} " \
                 + "${valuesParam} " \
                 + "${setParam} " \
                 + "-n ${config.helm.namespace} " \
                 + '--install ' \
                 + "${atomicParam()} "\
                 + "--history-max ${historyMax} "\
                 + "${config.helm?.extraParams ?: ''}"
        executeHelmCommand(helmCommand)
    }

    String atomicParam() {
        if (!config.helm.containsKey('nonBlockingDeployment')) {
            config.helm.nonBlockingDeployment = false
        }
        if (!config.helm.containsKey('cleanupOnFail')) {
            config.helm.cleanupOnFail = true
        }

        List<String> atomicParams = []

        if (!config.helm.nonBlockingDeployment) {
            atomicParams.add('--wait')
        }
        if (config.helm.cleanupOnFail) {
            atomicParams.add('--cleanup-on-fail')
        }
        String.join(' ', atomicParams)
    }

    /**
     * Run helm command
     */
    void executeHelmCommand(String inputHelmCommand) {
        if (config.helm?.oscpLDAPAuth) {
            psc.podSelector.select(psc,psc.nameRegistry.nameFor(LOGICAL_OC_CONTAINER_NAME), Utils.cloud(config)) {
                script.withCredentials([
                        script.usernamePassword(
                                credentialsId: config.helm.credentialsId,
                                passwordVariable: 'OSCP_PASS',
                                usernameVariable: 'OSCP_USER'
                        )
                ]) {
                    script.sh("oc login ${config.helm.serverUrl} -u ${script.OSCP_USER} -p ${script.OSCP_PASS}")
                    if (!config.containsKey('chart')) {
                        script.sh "oc project ${config.helm.namespace}"
                    }
                }
            }
            script.container(containerName) {
                script.sh(inputHelmCommand)
            }

        } else {
            psc.podSelector.select(psc,podTemplateContainerName, Utils.cloud(config)) {
                script.withCredentials([
                        script.string(
                                credentialsId: config.helm.credentialsId,
                                variable: 'OSCP_API_TOKEN'
                        )
                ]) {
                    script.sh(
                            inputHelmCommand
                                    + "--kube-apiserver ${config.helm.serverUrl} "
                                    + "--kube-token ${script.OSCP_API_TOKEN} "
                    )
                }
            }
        }
    }
    /**
     * Run helm preClean and yes the || true is a hack but the not found is an error in this situation
     */
    void executePreClean() {
        String helmCommand = "helm uninstall ${config.helm.deploymentName} " \
                 + "-n ${config.helm.namespace} || true"
        executeHelmCommand(helmCommand)
    }
}
