package com.cigna.remote

import com.cigna.jenkins_spock.JenkinsPipelineSpecification

public class JenkinsRemoteSpec extends JenkinsPipelineSpecification {

    def setup() {
        
        explicitlyMockPipelineStep('updateGitStatus')
        explicitlyMockPipelineStep('build')
}

    def """When validate is called and required configuration items are missing issues are noted"""() {
        when:
            def jenkinsRemote = new JenkinsRemote(
                config: configToVerify,
                script: {},
            )

            def issues = jenkinsRemote.validate()

        then:
            assert issues.size() == numberOfIssues
        
        where:
            configToVerify << [
                [:],
                [
                    remoteType: null,
                    jobPath: 'test',
                    branchPattern: 'test'
                ],
                [
                    remoteType: 'test',
                    jobPath: null,
                    branchPattern: 'test'
                ],
                [
                    remoteType: 'test',
                    jobPath: 'test',
                    branchPattern: null
                ]
            ]
            numberOfIssues << [3, 1, 1, 1]
    }

    def """When params is called and parameters are given in the config 
            each parameter is given in build step preferred manner"""() {
        when:
            def jenkinsRemote = new JenkinsRemote(
                script: {},
                config: [
                    remoteType: 'Jenkins',
                    branchPattern: 'test',
                    jobPath: '/test/test',
                    params: [
                        'test': 'test',
                        'another_test': 'test'
                    ]
                ]
            )
            def testParams = jenkinsRemote.jenkinsBuildStepParams()
        
        then:
            assert testParams == paramsToVerify
        
        where:
            paramsToVerify << [
                [
                    [
                        $class:'StringParameterValue',
                        name:'test',
                        value:'test'
                    ], 
                    [
                        $class:'StringParameterValue',
                        name:'another_test',
                        value:'test'
                    ]
                ]
            ]
    }

    def """When run is called a build step is called with given config"""() {
        when:
            def jenkinsRemote = new JenkinsRemote(
                script: {},
                config: [
                    remoteType: 'Jenkins',
                    branchPattern: 'test',
                    jobPath: '/test/test',
                    params: [
                        'test': 'test'
                    ]
                ]
            )
            jenkinsRemote.run()

        then:
            1 * getPipelineMock("build").call([
                'job':'/test/test', 
                'parameters':[['$class':'StringParameterValue', 'name':'test', 'value':'test']], 
                'wait':'false', 
                'propagate':'false'
            ])
    }
}