package com.cigna.checkpoint

import com.cigna.jenkins_spock.JenkinsPipelineSpecification

class CheckpointTest extends JenkinsPipelineSpecification {
    class Script {
        def env = [
                JOB_NAME: 'my/cool/job'
        ]
    }
    def script = new Script()

    def setup() {
        
        explicitlyMockPipelineStep('updateGitStatus')
    }

    def """When validate is called and required name missing, an issue is raised """() {
        when:
        def simpleCheckpoint = new Checkpoint(script: script)

        simpleCheckpoint.config = [
            name: checkpointName
        ]
        def issues = simpleCheckpoint.validate()

        then:
        issues.size() == numberOfIssues

        where:
        checkpointName << [null, 'stuff', 'bluff']
        numberOfIssues << [1, 0, 0]
    }

    def """When checkpoint is called, the named checkpoint is created"""() {
        given:
        explicitlyMockPipelineStep('checkpoint')
        when:
        def simpleCheckpoint = new Checkpoint(script: script)

        simpleCheckpoint.config = [
            name: checkpointName
        ]
       simpleCheckpoint.run()

        then:
        1 * getPipelineMock('checkpoint')(checkpointName)

        where:
        checkpointName << [null, 'stuff', 'bluff']
        numberOfIssues << [1, 0, 0]
    }
}
