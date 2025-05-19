package com.cigna.ruleengine

import com.cigna.jenkins_spock.JenkinsPipelineSpecification

class RuleengineSpec extends JenkinsPipelineSpecification {

    class Script {
        String JOB_NAME = 'job/name/here'
        def env = [
            GIT_BRANCH: 'stuff',
            GIT_COMMIT: 'stuff',
            GIT_PREVIOUS_COMMIT: 'stuff',
            GIT_PREVIOUS_SUCCESSFUL_COMMIT: 'stuff'
        ]
        def scm = [
            userRemoteConfigs:[
                [url: 'https://github.sys.cigna.com/somecool_project/super_cool.git']
            ]
        ]
    }
    def script = new Script()
    def setup() {
        
    }
    def """when Rule Engine interface is called and call stepsToDo"""() {
        when:
            def ruleengine = new ValidRuleengine(
                config: [
					branchPattern: 'stuff'
                ],
                script: script
            )
            ruleengine.stepsToDo()

        then:
            1 * getPipelineMock("echo").call('Running RuleEngine')
        }
}