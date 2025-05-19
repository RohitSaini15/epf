package com.cigna.deployment

import com.cigna.jenkins_spock.JenkinsPipelineSpecification

class DynatraceDeploymentSpec extends JenkinsPipelineSpecification {

    class Script {
        String ARTIFACT_USER = 'user'
        String ARTIFACT_PASS = 'pass'
        def env = [
            GIT_BRANCH: 'stuff',
            GIT_COMMIT: 'stuff',
            GIT_PREVIOUS_COMMIT: 'stuff',
            GIT_PREVIOUS_SUCCESSFUL_COMMIT: 'stuff'
        ]
    }
    def script = new Script()

    def setup() {
        explicitlyMockPipelineStep('findFiles')
        explicitlyMockPipelineStep('override')
    }

    def "When printWithNoTrace is called"() {
        when:
            def dynatraceDeployment = Spy(DynatraceDeployment, constructorArgs: [
                config: [
                    deploymentType: 'dynatrace',
                    branchPattern: 'fake',
                    sdlcEnvironment: 'fake',
                    dynatrace: [
                        tenantId: 'fake-tenantid',
                        apiToken: 'fake-apitoken', 
                        artifactory: [
                            credentialsId: 'fake-artifactory-creds',
                            repo: 'fake-artifactory-repo',
                            path: 'fake/artifactory/path',
                            name: 'fake-application-name',
                        ],
                    ]
                ],
                script: script
            ])

            dynatraceDeployment.printWithNoTrace('test')

        then:
            1 * getPipelineMock('sh')(*_)
    }

    def "When collectJSONMappings is called"() {
        when:
            def dynatraceDeployment = Spy(DynatraceDeployment, constructorArgs: [
                config: [
                    deploymentType: 'dynatrace',
                    branchPattern: 'fake',
                    sdlcEnvironment: 'fake',
                    dynatrace: [
                        tenantId: 'fake-tenantid',
                        apiToken: 'fake-apitoken', 
                        artifactory: [
                            credentialsId: 'fake-artifactory-creds',
                            repo: 'fake-artifactory-repo',
                            path: 'fake/artifactory/path',
                            name: 'fake-application-name',
                        ],
                    ]
                ],
                script: script
            ])

            dynatraceDeployment.collectJSONMappings()

        then:
            2 * getPipelineMock('echo')(*_)
    }

    def "When configDeployToDynatrace is called"() {
        when:
            def dynatraceDeployment = Spy(DynatraceDeployment, constructorArgs: [
                config: [
                    deploymentType: 'dynatrace',
                    branchPattern: 'fake',
                    sdlcEnvironment: 'fake',
                    dynatrace: [
                        tenantId: 'fake-tenantid',
                        apiToken: 'fake-apitoken', 
                        artifactory: [
                            credentialsId: 'fake-artifactory-creds',
                            repo: 'fake-artifactory-repo',
                            path: 'fake/artifactory/path',
                            name: 'fake-application-name',
                        ],
                        
                    ]
                ],
                script: script
            ])

            dynatraceDeployment.configDeployToDynatrace()

        then:
            _ * getPipelineMock('echo')(*_)
            1 * getPipelineMock("sh")({ it ==~ /(?s).*dynatrace.*/}) >> '{"cool": "json"}'
    }
    
    def "When downloadArtifact is called"() {
        when:
            def dynatraceDeployment = Spy(DynatraceDeployment, constructorArgs: [
                config: [
                    deploymentType: 'dynatrace',
                    branchPattern: 'fake',
                    sdlcEnvironment: 'fake',
                    dynatrace: [
                        tenantId: 'fake-tenantid',
                        apiToken: 'fake-apitoken', 
                        artifactory: [
                            credentialsId: 'fake-artifactory-creds',
                            repo: 'fake-artifactory-repo',
                            path: 'fake/artifactory/path',
                            name: 'fake-application-name',
                        ],
                        
                    ]
                ],
                script: script
            ])

            dynatraceDeployment.downloadArtifact()

        then:
            1 * getPipelineMock('withCredentials')(*_)
            1 * getPipelineMock('usernamePassword.call')(*_)
            1 * getPipelineMock('echo')(*_)
            1 * getPipelineMock("sh")({ it ==~ /(?s).*latest.*/}) >> '123'
            1 * getPipelineMock("sh")({ it ==~ /(?s).*value.*/}) >> '456'
            3 * getPipelineMock("sh")(*_)
    }
}