package com.cigna.testing

import com.cigna.jenkins_spock.JenkinsPipelineSpecification

public class NodeTestSpec extends JenkinsPipelineSpecification {

    class Script {
        def env = [WORKSPACE: '/test']
        def JOB_NAME = 'job/name/here'
    }

    def setup() {
        def remoteConfigs =  [
                [
                        url: "https://git.sys.cigna.com/somecool_project/super_cool.git"
                ]
        ]
        explicitlyMockPipelineVariable("scm")
        getPipelineMock("scm.getProperty")('userRemoteConfigs') >> remoteConfigs

        
        explicitlyMockPipelineStep('updateGitStatus')
}

    def """When run is called then a sh step with an echo is called and
        another sh step is called with given command"""() {
        when:
            def script = new Script()
            def nodeTest = new NodeTest(
                script: script
            )

            nodeTest.testingConfiguration = [
                configFile: './test',
                args: '-test'
            ]
            nodeTest.run()

        then:
            1 * getPipelineMock("sh")({it ==~  "echo 'No prep, continuing...'"})
            1 * getPipelineMock("sh")({it ==~ 'cd ./ && node ./test -test'})
    }

    def """When run is called and prepCommands are given then a sh step is called
        with given commands"""() {
        when:
            def script = new Script()
            def nodeTest = new NodeTest(
                script: script
            )

            nodeTest.testingConfiguration = [
                prepCommands: "test && test"
            ]
            nodeTest.run()

        then:
            1 * getPipelineMock("sh")({it ==~ 'test && test'})
            1 * getPipelineMock("sh")({it ==~ 'cd ./ && node ./conf.js '})
    }

}