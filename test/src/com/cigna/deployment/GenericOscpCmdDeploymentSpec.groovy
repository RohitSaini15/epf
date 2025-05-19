package com.cigna.deployment

import com.cigna.SinglePodTest
import com.cigna.common.phases.PodSelector
import com.cigna.common.scm.CommonGit
import com.evernorth.cloudnativebuild.mocks.JenkinsEnv

class GenericOscpCmdDeploymentSpec extends SinglePodTest {
    def cloudName = 'test-cloud'

    def setup() {
        explicitlyMockPipelineStep('override')
        explicitlyMockPipelineStep('updateGitStatus')
        initScriptAndPsc()
    }

    def 'When deploy is called'() {
        when:
        Deployment helmDeployment = new GenericOscpCmdDeployment(config: [
                deploymentType : 'helm',
                branchPattern  : 'fake',
                sdlcEnvironment: 'fake',
                helm           : [
                        serverUrl    : 'fake.openshift.cigna.com',
                        oscpLDAPAuth : true,
                        credentialsId: 'fake-creds',
                        namespace    : 'fake-namespace',
                        command      : 'oc project fake'
                ],
        ],
                script: script,
                psc: psc)

        explicitlyMockPipelineVariable('OSCP_USER')
        explicitlyMockPipelineVariable('OSCP_PASS')

        simulatePodTemplate(psc, helmDeployment, cloudName)
        helmDeployment.deploy()
        then:
        1 * getPipelineMock('withCredentials')(*_)
        1 * getPipelineMock('usernamePassword.call')(*_)
        1 * getPipelineMock('sh')({ it ==~ /oc login fake.openshift.cigna.com .*/ })
        1 * getPipelineMock('sh')('oc project fake')
    }

    def "validate"() {
        when:
        Deployment genericOscpCmdDeployment = new GenericOscpCmdDeployment(config: [
                deploymentType : 'helm',
                branchPattern  : 'fake',
                sdlcEnvironment: 'fake',
                helm           : [
                        serverUrl    : surl,
                        oscpLDAPAuth : true,
                        credentialsId: credsId,
                        namespace    : ns,
                        command      : 'oc project fake',

                ],
        ],
                script: script,
                psc: psc)
        def issues = genericOscpCmdDeployment.validate()

        then:
        assert issues.size() == numberOfIssues

        where:
        surl << [null, 'fake.openshift.cigna.com', 'fake.openshift.cigna.com']
        credsId << ['fake-creds', null, 'fake-creds']
        ns << ['fake-namespace', 'fake-namespace', null]
        numberOfIssues << [1, 1, 1]
    }

}
