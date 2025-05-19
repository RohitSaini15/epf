package com.cigna.deployment

import com.cigna.SinglePodTest

class OpenshiftDeploymentSpec extends SinglePodTest {
    def cloudName = 'test-cloud'

    def setup() {
        explicitlyMockPipelineStep('override')
        initScriptAndPsc()
    }

    def """When validate is called additionalPodConfig is set with the
    appropriate items"""() {
        when:
        def openshiftDeployment = new OpenshiftDeployment(
            config: [
                deploymentType : 'openshift',
                branchPattern  : 'stuff',
                sdlcEnvironment: 'thing',
                openshift      : [
                    credentialsId: 'foo',
                    project      : 'bar',
                ],
                helm           : [
                    command: 'helmCommand',
                    image  : 'test',
                    version: 'test1'
                ],
                container      : [
                    image  : 'test',
                    version: 'test1',
                ],
            ],
            script: script,
            psc: psc
        )
        openshiftDeployment.init()
        openshiftDeployment.prePodConfig()
        openshiftDeployment.validate()

        then:
        openshiftDeployment.additionalPodConfig.containers.find { it.name == 'testvtest1' } == [
            name      : 'testvtest1',
            image     : "test:test1",
            imagePullPolicy: 'Always',
            tty       : true,
            workingDir: '/home/jenkins/agent',
            command   : com.cigna.common.utils.Utils.defaultSidecarCommand,
            env       : [
                [
                    name : 'HOME',
                    value: '/tmp'
                ],
            ],
            resources : [
                requests: [
                    cpu   : '100m',
                    memory: '500Mi'
                ],
                limits  : [
                    cpu   : '500m',
                    memory: '500Mi'
                ]
            ]
        ]
    }

    def """When validate is called and required configuration items are missing, issues are raised """() {
        when:
        def openshiftDeployment = new OpenshiftDeployment(script: script, psc: psc)

        openshiftDeployment.deploymentConfiguration = [
            deploymentType : 'openshift',
            branchPattern  : 'stuff',
            sdlcEnvironment: 'thing',
            openshift      : [
                credentialsId: openshiftTokenCredentialsId,
                project      : openshiftProject,
            ],
            deployScript   : deployScript
        ]
        def issues = openshiftDeployment.validate()

        then:
        assert issues.size() == numberOfIssues

        where:
        openshiftTokenCredentialsId << [null, 'test', 'test']
        openshiftProject << ['test', null, 'test']
        deployScript << ['test', 'test', null]
        numberOfIssues << [1, 1, 1]
    }

    def """When validate is called with helm command, it is successfully assigned to deployScript"""() {
        when:
        def openshiftDeployment = new OpenshiftDeployment(script: script, psc: psc)

        openshiftDeployment.deploymentConfiguration = [
            deploymentType : 'openshift',
            branchPattern  : 'stuff',
            sdlcEnvironment: 'thing',
            openshift      : [
                credentialsId: 'foo',
                project      : 'bar',
            ],
            helm           : [
                command: 'helmCommand'
            ]
        ]
        def issues = openshiftDeployment.validate()

        then:
        assert issues.size() == 0
    }

    def """When deploy is called a container step is called then, withCredentails step is called
        then oc login, oc project and the given deploy command"""() {
        when:
        explicitlyMockPipelineVariable("openshiftSecret")

        def openshiftDeployment = new OpenshiftDeployment(
            config: [
                cloudName: cloudName
            ],
            script: script,
            psc: psc
        )

        openshiftDeployment.deploymentConfiguration = [
            openshift   : [
                credentialsId: 'testId',
                project      : 'testProject',
            ],
            deployScript: 'Testing Deploy Command'
        ]
        simulatePodTemplate(psc, openshiftDeployment, cloudName)
        openshiftDeployment.deploy()

        then:
        1 * getPipelineMock("string.call")(['credentialsId': 'testId', 'variable': 'openshiftSecret'])
        1 * getPipelineMock("sh")({ it ==~ /oc login --token=.*/ })
        1 * getPipelineMock("sh")('oc project testProject')
        1 * getPipelineMock("sh")('Testing Deploy Command')
    }

    def """When deploy is called with a user defined in deploymentConfiguration.openshift, withCredentials
        will use a username and password string instead of a token string"""() {
        when:
        explicitlyMockPipelineVariable("openshiftSecret")

        def openshiftDeployment = new OpenshiftDeployment(
            config: [
                cloudName: cloudName
            ],
            script: script,
            psc: psc
        )

        openshiftDeployment.deploymentConfiguration = [
            openshift   : [
                credentialsId: 'testId',
                project      : 'testProject',
                user         : 'testUser'
            ],
            deployScript: 'Testing Deploy Command'
        ]
        simulatePodTemplate(psc, openshiftDeployment, cloudName)
        openshiftDeployment.deploy()

        then:
        1 * getPipelineMock("string.call")(['credentialsId': 'testId', 'variable': 'openshiftSecret'])
        1 * getPipelineMock("sh")({ it ==~ /oc login -u=testUser -p=.*/ })
        1 * getPipelineMock("sh")('oc project testProject')
        1 * getPipelineMock("sh")('Testing Deploy Command')
    }

    def """deployment phases contain embedded epf-curl container"""() {
        when:
        def openshiftDeployment = new OpenshiftDeployment(
            config: [
                deploymentType : 'openshift',
                branchPattern  : 'stuff',
                sdlcEnvironment: 'thing',
                openshift      : [
                    credentialsId: 'foo',
                    project      : 'bar',
                ],
                helm           : [
                    command: 'helmCommand',
                    image  : 'test',
                    version: 'test1'
                ]
            ],
            script: script,
            psc: psc
        )
        openshiftDeployment.init()
        openshiftDeployment.prePodConfig()
        openshiftDeployment.validate()

        then:
        openshiftDeployment.additionalPodConfig.containers.findAll { it.name == 'epf-curlvlatest' }.size() == 1
    }
}
