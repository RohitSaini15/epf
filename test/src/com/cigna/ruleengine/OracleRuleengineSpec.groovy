package com.cigna.ruleengine

import com.cigna.jenkins_spock.JenkinsPipelineSpecification

class OracleRuleengineSpec extends JenkinsPipelineSpecification {

    class Script {

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
        
		explicitlyMockPipelineVariable("pathForBuildXml")
	}

    def """when Oracle Rule Engine is called and the scan is run"""() {
		when:
            def oracleruleengine = new OracleRuleengine( 
			    config: [
					ruleengineType: 'Oracle',
                    branchPattern: 'stuff',
                    oracle: [
					    pathForBuildXml: '${WORKSPACE}/src/build.xml',
                    ],
                ],
		        script: script
            )
            oracleruleengine.runApplication()

        then:
            1 * getPipelineMock("echo").call('################ Oracle Ruleengine Started ############################')	
	}
}
