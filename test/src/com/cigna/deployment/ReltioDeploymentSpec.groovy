package com.cigna.deployment

import com.cigna.SinglePodTest

class ReltioDeploymentSpec extends SinglePodTest {

    def setup() {
        initScriptAndPsc()
        explicitlyMockPipelineStep('readJSON')
    }

    def "When deployMdmConfig is called"() {
        when:
        def reltioDeployment = Spy(ReltioDeployment, constructorArgs: [
            config: [
                deploymentType: 'reltio',
                branchPattern: 'fake',
                sdlcEnvironment: 'fake',
                reltio: [
                    tenantId: 'fake-tenantid',
                    environmentUrl: 'http://fake-url.com',
                    credentialsId: 'fake-credentilsId',
                    authCredentialsId: 'fake-authCredentialsId',
                    relioType: 'mdm',
                    validateMDM: true,
                    artifactory: [
                        credentialsId: 'fake-artifactory-creds',
                        repo: 'fake-artifactory-repo',
                        path: 'fake/artifactory/path',
                        name: 'fake-application-name',
                    ],
                    rdmReport: [
                        pathToCsvFile: 'fake-rdm-lookups.csv',
                        pathToMappingFile: 'fake-rdm-lookups-json.mapping',
                        threadCount: 1,
                    ],
                    statusWaitTime: 0.1
                ]
            ],
            script: script
        ])

        reltioDeployment.deployMdmConfig()

        then:
        2 * getPipelineMock('withCredentials')(*_)
        1 * getPipelineMock("readJSON")(*_) >> [access_token: 'token']
        1 * getPipelineMock("sh")({ it ==~ /(?s).*PUT.*/}) >> '{"cool": "json"}'
        1 * getPipelineMock("sh")({ it ==~ /(?s).*jq.*/}) >> '{"cool": "json"}'
        2 * getPipelineMock('usernamePassword.call')(*_)
    }

    def "When deployMdmConfig is called and contains configProperties"() {
        when:
        def reltioDeployment = Spy(ReltioDeployment, constructorArgs: [
            config: [
                deploymentType: 'reltio',
                branchPattern: 'fake',
                sdlcEnvironment: 'fake',
                reltio: [
                    tenantId: 'fake-tenantid',
                    environmentUrl: 'http://fake-url.com',
                    credentialsId: 'fake-credentilsId',
                    authCredentialsId: 'fake-authCredentialsId',
                    relioType: 'mdm',
                    validateMDM: true,
                    configProperties: [
                        rdmTenantId: 'fakerdmTanantId',
                        description: 'fake-description'
                    ],
                    rdmReport: [
                        pathToCsvFile: 'fake-rdm-lookups.csv',
                        pathToMappingFile: 'fake-rdm-lookups-json.mapping',
                        threadCount: 1,
                    ]
                ]
            ],
            script: script
        ])

        reltioDeployment.deployMdmConfig()

        then:
        2 * getPipelineMock('withCredentials')(*_)
        1 * getPipelineMock("readJSON")(*_) >> [access_token: 'token']
        1 * getPipelineMock("sh")({ it ==~ /(?s).*PUT.*/}) >> '{"cool": "json"}'
        _ * getPipelineMock("sh")({ it ==~ /(?s).*jq.*/}) >> '{"cool": "json"}'
        2 * getPipelineMock('usernamePassword.call')(*_)
    }

    def "When rebuildMatchTable is called"() {
        when:
            def reltioDeployment = Spy(ReltioDeployment, constructorArgs: [
                config: [
                    deploymentType: 'reltio',
                    branchPattern: 'fake',
                    sdlcEnvironment: 'fake',
                    reltio: [
                        tenantId: 'fake-tenantid',
                        environmentUrl: 'http://fake-url.com',
                        credentialsId: 'fake-credentilsId',
                        authCredentialsId: 'fake-authCredentialsId',
                        validateMDM: true,
                        rebuildMatchTable: true,
                        artifactory: [
                            credentialsId: 'fake-artifactory-creds',
                            repo: 'fake-artifactory-repo',
                            path: 'fake/artifactory/path',
                            name: 'fake-application-name',
                        ],
                        rdmReport: [
                            pathToCsvFile: 'fake-rdm-lookups.csv',
                            mmHook: 'http://fake-url.com/hooks/hookId',
                            pathToMappingFile: 'fake-rdm-lookups-json.mapping',
                            threadCount: 1,
                        ],
                        statusWaitTime: 0.1
                    ]
                ],
                script: script
            ])

            reltioDeployment.rebuildMatchTable()

        then:
            1 * getPipelineMock('withCredentials')(*_)
            1 * getPipelineMock("sh")(*_)
            3 * getPipelineMock("echo")(*_)
            1 * getPipelineMock("readJSON")(*_) >> [access_token: 'token']
            1 * getPipelineMock("sh")({ it ==~ /(?s).*rebuildmatchtable.*/}) >> '{"cool": "json"}'
    }

    def "When reindex is called"() {
        when:
            def reltioDeployment = Spy(ReltioDeployment, constructorArgs: [
                config: [
                    deploymentType: 'reltio',
                    branchPattern: 'fake',
                    sdlcEnvironment: 'fake',
                    reltio: [
                        tenantId: 'fake-tenantid',
                        environmentUrl: 'http://fake-url.com',
                        credentialsId: 'fake-credentilsId',
                        authCredentialsId: 'fake-authCredentialsId',
                        validateMDM: true,
                        rebuildMatchTable: true,
                        artifactory: [
                            credentialsId: 'fake-artifactory-creds',
                            repo: 'fake-artifactory-repo',
                            path: 'fake/artifactory/path',
                            name: 'fake-application-name',
                        ],
                        rdmReport: [
                            pathToCsvFile: 'fake-rdm-lookups.csv',
                            mmHook: 'http://fake-url.com/hooks/hookId',
                            pathToMappingFile: 'fake-rdm-lookups-json.mapping',
                            threadCount: 1,
                        ],
                        statusWaitTime: 0.1
                    ]
                ],
                script: script
            ])

            reltioDeployment.reindex()

        then:
            1 * getPipelineMock('withCredentials')(*_)
            1 * getPipelineMock("sh")(*_)
            3 * getPipelineMock("echo")(*_)
            1 * getPipelineMock("readJSON")(*_) >> [access_token: 'token']
            1 * getPipelineMock("sh")({ it ==~ /(?s).*reindex.*/}) >> '{"cool": "json"}'
    }
}