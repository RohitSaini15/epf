package com.cigna.deployment

import com.cigna.SinglePodTest
import com.cigna.common.phases.PodSelector
import com.cigna.common.scm.CommonGit
import com.evernorth.cloudnativebuild.mocks.JenkinsEnv

class HelmDeploymentSpec extends SinglePodTest {
    def setup() {
        explicitlyMockPipelineStep('override')
        explicitlyMockPipelineStep('updateGitStatus')
        explicitlyMockPipelineVariable("OSCP_API_TOKEN")
        explicitlyMockPipelineVariable("ARTIFACTORY_USER")
        explicitlyMockPipelineVariable("ARTIFACTORY_SECRET")
        explicitlyMockPipelineVariable("helmDeployment")
        initScriptAndPsc()
    }

    def 'When rollback is called, helm rollback is called with the api credentials'() {
        when:
        Deployment helmDeployment = new HelmDeployment(
                config: [
                        cloudName      : 'test',
                        deploymentType : 'helm',
                        branchPattern  : 'fake',
                        sdlcEnvironment: 'fake',
                        helm           : [
                                serverUrl     : 'fake.openshift.cigna.com',
                                deploymentName: 'fake',
                                namespace     : 'fake-namespace',
                                chart         : './my-chart',
                        ]
                ],
                script: script,
                psc: psc
        )

        explicitlyMockPipelineVariable('OSCP_API_TOKEN')

        this.simulatePodTemplate(psc, helmDeployment)
        helmDeployment.rollback()

        then:
        1 * getPipelineMock('withCredentials')(*_)
        1 * getPipelineMock('string.call')(*_)
        1 * getPipelineMock('sh')({ it ==~ /helm rollback \S+ -n \S+ --cleanup-on-fail --kube-apiserver \S+ --kube-token .+/ })
    }

    def "When deploy is called on a local chart, the download code is not run"() {
        when:
        HelmDeployment helmDeployment = Spy(HelmDeployment, constructorArgs: [
                config: [
                        deploymentType : 'helm',
                        branchPattern  : 'fake',
                        cloudName      : 'test',
                        vault          : [
                                credentialsId: 'rob-vault-test',
                                files        : [
                                        'helm/oscp4/secret.yaml.enc',
                                        'helm/oscp4/other-secret.yaml.enc'
                                ]
                        ],
                        sdlcEnvironment: 'fake',
                        helm           : [
                                namespace: 'fake-namespace',
                                chart    : './my-chart',

                        ]
                ],
                script: script,
                psc   : psc
        ]) {
            extractVaultFiles() >> null
            helmDeployChart(_) >> null
        }
        simulatePodTemplate(psc, helmDeployment)

        helmDeployment.deploy()

        then:
        0 * getPipelineMock('sh')({ it ==~ /helm quay pull.*/ })

    }

    def "When extractVaultFiles is called, it extracts all listed files with ansible-vault using the correct suffix"() {
        when:
        HelmDeployment deployment = new HelmDeployment(
                config: [
                        deploymentType : 'helm',
                        branchPattern  : 'fake',
                        sdlcEnvironment: 'fake',
                        cloudName      : 'test',
                        helm           : [
                                namespace: 'fake-namespace',
                                chart    : './my-chart',
                        ],
                        vault          : [
                                credentialsId: 'fake',
                                files        : vaultsToExtract,
                                suffix       : '.foo'
                        ]
                ],
                script: script,
                psc: psc
        )
        explicitlyMockPipelineVariable('VAULT_PASS')
        simulatePodTemplate(psc, deployment)
        deployment.extractVaultFiles()

        then:
        1 * getPipelineMock('withCredentials')(*_)
        1 * getPipelineMock('sh')({ it ==~ /set \+x; echo .+ > vaultPass/ })
        vaultFileCount * getPipelineMock('sh')(
                { it ==~ /ansible-vault decrypt \S+ --output \$\(dirname \S+\)\/\$\(basename \S+ .foo\) --vault-password-file vaultPass/ }
        )
        1 * getPipelineMock('sh')({ it ==~ 'rm vaultPass' })

        where:
        vaultsToExtract << [
                [
                        'file1'
                ],
                [
                        'file1',
                        'file2'
                ],
                [
                        'file1',
                        'file2',
                        'file3'
                ]
        ]
        vaultFileCount << [1, 2, 3]
    }

    def "When pullChart is called, it extracts the chart from quay"() {
        when:
        HelmDeployment deployment = new HelmDeployment(
                config: [
                        deploymentType : 'helm',
                        branchPattern  : 'fake',
                        sdlcEnvironment: 'fake',
                        cloudName      : 'test',
                        helm           : [
                                namespace   : 'fake-namespace',
                                chart       : 'fake.com/fake/my-chart',
                                chartVersion: 'latest'
                        ]

                ],
                script: script,
                psc: psc
        )
        explicitlyMockPipelineVariable('VAULT_PASS')
        deployment.pullChart()

        then:
        1 * getPipelineMock('sh')({ it ==~ /helm quay pull -k fake\.com\/fake\/my-chart:latest --tarball/ })
        1 * getPipelineMock('sh')(*_) >> 'fake_my-chart_1.0.0.tar.gz'
        1 * getPipelineMock('sh')({ it ==~ /tar xfz fake_my-chart_1\.0\.0\.tar\.gz/ })
    }

    def "When helmDeployChart is called, it deploys the helm chart"() {
        when:
        HelmDeployment deployment = new HelmDeployment(
                config: [
                        deploymentType : 'helm',
                        branchPattern  : 'fake',
                        sdlcEnvironment: 'fake',
                        cloudName      : 'test',
                        helm           : [
                                serverUrl     : 'fake',
                                namespace     : 'fake-namespace',
                                deploymentName: 'fake',
                                chart         : 'fake.com/fake/my-chart',
                                chartVersion  : 'latest',
                                values        : [
                                        'file1',
                                        'file2',
                                        'file3'
                                ],
                                setValues     : [
                                        'k1': 'v1',
                                        'k2': 'v2'
                                ]
                        ]

                ],
                script: script,
                psc: psc
        )
        explicitlyMockPipelineVariable('OSCP_API_TOKEN')
        simulatePodTemplate(psc, deployment)
        deployment.helmDeployChart(deployment.config.helm.chart.tokenize('/')[2])

        then:
        1 * getPipelineMock('withCredentials')(*_)
        1 * getPipelineMock('sh')(
                { it ==~ /helm upgrade fake my-chart -f file1 -f file2 -f file3 --set k1=v1 --set k2=v2 -n fake-namespace --install --wait --cleanup-on-fail --history-max 3 --kube-apiserver fake --kube-token .+/ }
        )
    }

    def """When users provide the nonBlockingDeployment configuration,
        the helm command will not use the wait command"""() {
        when:
        HelmDeployment deployment = new HelmDeployment(
                config: [
                        deploymentType : 'helm',
                        branchPattern  : 'fake',
                        sdlcEnvironment: 'fake',
                        cloudName      : 'test',
                        helm           : [
                                serverUrl     : 'fake',
                                namespace     : 'fake-namespace',
                                deploymentName: 'fake',
                                chart         : 'fake.com/fake/my-chart',
                                chartVersion  : 'latest',
                                values        : [
                                        'file1',
                                        'file2',
                                        'file3'
                                ],
                                setValues     : [
                                        'k1': 'v1',
                                        'k2': 'v2'
                                ]
                        ],

                ],
                script: script,
                psc: psc
        )
        deployment.config.helm = deployment.config.helm + nonBlockingDeploymentParam
        explicitlyMockPipelineVariable('OSCP_API_TOKEN')
        simulatePodTemplate(psc, deployment)
        deployment.helmDeployChart(deployment.config.helm.chart.tokenize('/')[2])

        then:
        1 * getPipelineMock('withCredentials')(*_)
        1 * getPipelineMock('sh')(
                { it ==~ /helm upgrade fake my-chart -f file1 -f file2 -f file3 --set k1=v1 --set k2=v2 -n fake-namespace --install ${injectedParam} --history-max 3 --kube-apiserver fake --kube-token .+/ }
        )

        where:
        nonBlockingDeploymentParam << [[nonBlockingDeployment: true], [nonBlockingDeployment: false], [:]]
        injectedParam << ['--cleanup-on-fail', '--wait --cleanup-on-fail', '--wait --cleanup-on-fail']
    }

    def """When users provide the oscpLDAPAuth configuration, authentication is done via oc login instead of vanilla helm"""() {
        when:
        HelmDeployment deployment = new HelmDeployment(
                config: [
                        deploymentType : 'helm',
                        branchPattern  : 'fake',
                        sdlcEnvironment: 'fake',
                        cloudName      : 'test',
                        helm           : [
                                serverUrl     : 'fake',
                                namespace     : 'fake-namespace',
                                deploymentName: 'fake',
                                oscpLDAPAuth  : true,
                                chart         : 'fake/fake/my-chart',
                                chartVersion  : 'latest',
                                values        : [
                                        'file1',
                                        'file2',
                                        'file3'
                                ],
                                setValues     : [
                                        'k1': 'v1',
                                        'k2': 'v2'
                                ]
                        ],

                ],
                script: script,
                psc: psc
        )
        explicitlyMockPipelineVariable('OSCP_USER')
        explicitlyMockPipelineVariable('OSCP_PASS')
        simulatePodTemplate(psc, deployment)
        deployment.helmDeployChart(deployment.config.helm.chart)
        deployment.rollback()

        then:
        2 * getPipelineMock('withCredentials')(*_)
        2 * getPipelineMock('sh')({ it ==~ /oc login fake -u .+ -p .+/ })
        1 * getPipelineMock('sh')(
                { it == 'helm upgrade fake fake/fake/my-chart -f file1 -f file2 -f file3 --set k1=v1 --set k2=v2 -n fake-namespace --install --wait --cleanup-on-fail --history-max 3 ' }
        )
        1 * getPipelineMock('sh')({ it ==~ /helm rollback fake -n fake-namespace --cleanup-on-fail / })
    }

    def 'When atomicParams is called, it output the correct blocking and cleanup parameters'() {
        when:
        HelmDeployment deployment = new HelmDeployment(
                config: [
                        deploymentType : 'helm',
                        branchPattern  : 'fake',
                        sdlcEnvironment: 'fake',
                        cloudName      : 'test',
                        helm           : [
                                serverUrl     : 'fake',
                                namespace     : 'fake-namespace',
                                deploymentName: 'fake',
                                chart         : 'fake.com/fake/my-chart',
                                chartVersion  : 'latest',
                                values        : [
                                        'file1',
                                        'file2',
                                        'file3'
                                ],
                                setValues     : [
                                        'k1': 'v1',
                                        'k2': 'v2'
                                ]
                        ],

                ],
                script: script,
                psc: psc
        )
        deployment.config.helm = deployment.config.helm + inputParams
        String actualResultParams = deployment.atomicParam()

        then:
        assert expectedResultParams ==~ actualResultParams

        where:
        inputParams << [
                [:],
                [nonBlockingDeployment: true],
                [cleanupOnFail: false],
                [nonBlockingDeployment: true, cleanupOnFail: true],
                [nonBlockingDeployment: true, cleanupOnFail: false],
                [nonBlockingDeployment: false, cleanupOnFail: true],
                [nonBlockingDeployment: false, cleanupOnFail: false],
        ]
        expectedResultParams << [
                '--wait --cleanup-on-fail',
                '--cleanup-on-fail',
                '--wait',
                '--cleanup-on-fail',
                '',
                '--wait --cleanup-on-fail',
                '--wait'
        ]
    }

    def '''When extraParams is given, helm command is run with provided extraParams'''() {
        when:
        HelmDeployment deployment = Spy(HelmDeployment,
                constructorArgs: [
                        config   : [
                                deploymentType : 'helm',
                                branchPattern  : 'fake',
                                sdlcEnvironment: 'fake',
                                helm           : [
                                        serverUrl     : 'fake',
                                        namespace     : 'fake-namespace',
                                        deploymentName: 'fake',
                                        chart         : 'fake.com/fake/my-chart',
                                        extraParams   : '--arg matey'
                                ],
                        ], script: script,
                        psc      : psc
                ],
        ) {
            extractVaultFiles() >> null
        }
        simulatePodTemplate(psc, deployment)
        deployment.deploy()

        then:
        1 * getPipelineMock("sh")({ it ==~ /helm upgrade.* --arg matey.*/ })
    }

    def '''When helm image is overridden'''() {
        when:
        HelmDeployment deployment = Spy(HelmDeployment,
                constructorArgs: [
                        config   : [
                                deploymentType : 'helm',
                                branchPattern  : 'fake',
                                sdlcEnvironment: 'fake',
                                helm           : [
                                        serverUrl     : 'fake',
                                        namespace     : 'fake-namespace',
                                        deploymentName: 'fake'
                                ],
                                containers     : [
                                        [name: 'oc-cli', image: 'enterprise-devops/oc-cli:1.0.1'],
                                        [name: 'helm', image: 'enterprise-devops/helm:3.2.5']
                                ],
                        ], script: script,
                        psc      : psc
                ],
        ) {
            extractVaultFiles() >> null
        }
        simulatePodTemplate(psc, deployment)
        then:
        psc.podSelector.podTemplates.toString().contains("helm")
        psc.podSelector.podTemplates.toString().contains("enterprise-devops/helm:3.2.5")
        psc.podSelector.podTemplates.toString().contains("oc-cli")
        psc.podSelector.podTemplates.toString().contains("enterprise-devops/oc-cli:1.0.1")
        psc.nameRegistry.nameFor(HelmDeployment.LOGICAL_HELM_CONTAINER_NAME) == "helmv325"
        psc.nameRegistry.nameFor(HelmDeployment.LOGICAL_OC_CONTAINER_NAME) != "oc-clivv44"
    }
}
