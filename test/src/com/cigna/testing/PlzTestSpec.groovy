package com.cigna.testing

import com.cigna.jenkins_spock.JenkinsPipelineSpecification

public class PlzTestSpec extends JenkinsPipelineSpecification {

    class Script {
        def JOB_NAME = 'job/name/here'
        def env =  [
            GIT_BRANCH: 'stuff',
            GIT_COMMIT: 'stuff',
            GIT_PREVIOUS_COMMIT: 'stuff',
            GIT_PREVIOUS_SUCCESSFUL_COMMIT: 'stuff',
            WORKSPACE: 'workspace_dir'
        ]
    }

    def script = {}

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

    def """When validate is called and required configuration items are missing, issues are raised """() {

        when:
            def plzTest = new PlzTest(script: script)

            plzTest.config = [
                testType: 'plz',
                branchPattern: 'stuff',
                sdlcEnvironment: 'thing',
                awsFed: [
                    credentialsId: awsFedCredentialsId,
                ],
                labels: labels
            ]
            def issues = plzTest.validate()

        then:
            issues.size() == numberOfIssues

        where:
            awsFedCredentialsId << [null, 'test', ]
            labels << ['test', ['test']]
            numberOfIssues << [2, 0]
    }

    def """When validate is called and runInAWS is called, issues presented accordingly"""() {
        when:
            def plzTest = new PlzTest(script: script)

            plzTest.config = [
                runInAWS: whereRunInAWS,
                testType: 'plz',
                branchPattern: 'stuff',
                sdlcEnvironment: 'thing',
                labels: [']test']
            ]
            plzTest.config += whereConfig
            def issues = plzTest.validate()

        then:
            issues.size() == numberOfIssues

        where:
            whereConfig << [
                [awsFed:[credentialsId:'test']],
                [:],
                [awsFed:[credentialsId:'test']],
                [aws:[targetAccount:null,accountRoleName:null]],
                [aws:[targetAccount:null,accountRoleName:'test']],
                [aws:[targetAccount:'test',accountRoleName:null]],
                [aws:[targetAccount:'test',accountRoleName:'test']]
            ]
            whereRunInAWS << [null, true, true, true, true, true, true]
            numberOfIssues << [0, 0, 1, 2, 1, 1, 0]
    }

    def """When run is called code is checked out, if awsFed
        configuration is configured, it is called, then plz test is called with the configured labels
        and verbosity flag"""() {

        when:
            explicitlyMockPipelineVariable('AWS_FED_USERNAME')
            explicitlyMockPipelineVariable('AWS_FED_PASSWORD')

            def script = new Script()
            def plzTest = new PlzTest(script: script)

            plzTest.config = [
                deploymentType: 'plz',
                branchPattern: 'stuff',
                sdlcEnvironment: 'thing',
                awsFed: awsFedCredentials,
                labels: labels,
                verbosityFlag: verbosityFlag
            ]
            plzTest.run()

        then:
            if (labels && verbosityFlag) {
                1 * getPipelineMock('sh')('plz test //... -i integration --show_all_output -vvv')
            } else if (verbosityFlag) {
                1 * getPipelineMock('sh')('plz test //... --show_all_output -vvv')
            } else {
                1 * getPipelineMock('sh')('plz test //... --show_all_output')
            }
            if (awsFedCredentials) {
                1 * getPipelineMock('sh')({it ==~ /export AWS_FED_PASSWORD=.* && export AWS_FED_USERNAME=.*/})
            } else {
                0 * getPipelineMock('sh')({it ==~ /export AWS_FED_PASSWORD=.* && export AWS_FED_USERNAME=.*/})
            }

        where:
            awsFedCredentials << [[credentialsId: 'stuff'], null, null]
            labels << [['integration'], [], []]
            verbosityFlag << ['-vvv', '-vvv', null]
    }

    def """When user provides modules only those modules are tested"""() {
        when:
            def plzTest = new PlzTest(
                config: [
                    testType: 'plz',
                    branchPattern: 'stuff',
                    sdlcEnvironment: 'dev',
                    labels: ['e2e', 'integration'],
                    modules: ['//module/aws/module1', '//module/aws/module2'],
                ],
                script: new Script(),
            )

            plzTest.run()

        then:
            1  * getPipelineMock('sh')({
                it == 'plz test //module/aws/module1/... -i e2e -i integration --show_all_output' +
                ' && plz test //module/aws/module2/... -i e2e -i integration --show_all_output'
            })
        
        when:
            def plzTestDefaultLabel = new PlzTest(
                config: [
                    testType: 'plz',
                    branchPattern: 'stuff',
                    sdlcEnvironment: 'dev',
                    modules: ['//module/azure/module1'],
                ],
                script: new Script(),
            )

            plzTestDefaultLabel.run()
        
        then:
            1 * getPipelineMock('sh')({
                it == 'plz test //module/azure/module1/... --show_all_output'
            })
    }

    def """When test is executed with modules configured but empty, 
        nothing is targeted for testing"""() {
        when:
            def plzTest = new PlzTest(
                config: [
                    testType: 'plz',
                    branchPattern: 'stuff',
                    sdlcEnvironment: 'dev',
                    modules: [],
                ],
                script: new Script(),
            )

            plzTest.run()
        
        then:
            1 * getPipelineMock('echo')({it == 'Modules is specified, but the list is empty. Nothing to test.'})
    }
}
