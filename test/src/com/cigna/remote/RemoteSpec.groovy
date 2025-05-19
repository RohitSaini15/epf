package com.cigna.remote

import com.cigna.jenkins_spock.JenkinsPipelineSpecification

public class RemoteSpec extends JenkinsPipelineSpecification {

    class Script {
        def env = [
                JOB_NAME: 'my/cool/job'
        ]
    }
    def script = new Script()

    def setup() {
        
        explicitlyMockPipelineStep('updateGitStatus')
}

    def """When remoteType or branchPattern is missing from the config
        a validation error is generated"""() {
    when:
        def remote = new ValidRemote(
            config: configToVerify,
            script: script
        )
        def issues = remote.validate()

        then:
        assert issues.size() == numberOfIssues

        where:
        configToVerify << [
            [
                other: "stuff"
            ],
            [
                remoteType: 'test',
                branchPattern: null
            ],
            [
                remoteType: null,
                branchPattern: 'test'
            ]
        ]
        numberOfIssues << [2, 1, 1]
    }

}