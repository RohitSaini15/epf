package com.cigna.deployment

import com.cigna.SinglePodTest
import com.cigna.common.notification.Notification
import com.cigna.testing.JMeterTest
import jenkins.plugins.http_request.ResponseContentSupplier

class DeploymentSpec extends SinglePodTest {
    def cloudName = 'test-cloud'
    ResponseContentSupplier response = new ResponseContentSupplier(
            '{"files": [{"filename": "cool"}, {"filename": "supercool"}]}',
            200
    )

    def setup() {
        explicitlyMockPipelineVariable("EPF_GITHUB_ACCESS_TOKEN")
        explicitlyMockPipelineStep('updateGitStatus')
        explicitlyMockPipelineStep('override')
        getPipelineMock("httpRequest")(*_) >> response
        new File('/tmp/jmeter-results.jtl').delete()
        initScriptAndPsc()
    }

    def """When branchPattern, sdlcEnvironment or deploymentType are missing from a the config,
        an exception is thrown"""() {
        when:
        def deployment = new ValidDeployment(
                config: [:],
                script: script,
                deploymentConfiguration: configToVerify,
                psc: psc
        )

        def issues = deployment.validate()

        then:
        assert issues.size() == numberOfIssues

        where:
        configToVerify << [
                [:],
                [
                        branchPattern  : 'stuff',
                        sdlcEnvironment: 'stuff',
                ],
                [
                        branchPattern : 'stuff',
                        deploymentType: 'stuff',
                ],
                [
                        sdlcEnvironment: 'stuff',
                        deploymentType : 'stuff',
                ]
        ]
        numberOfIssues << [3, 1, 1, 1]
    }

    def """When a testing phase returns a non-zero exit code from deployment a rollback is attempted'"""() {
        given:
        getPipelineMock("sh")({
            it ==~ /jmeter -f -n -t this\/path\/doesnt\/exist -l .*jmeter-results.jtl/
        }) >> { throw new Exception("Deploy testing failed, rolling back.") }
        explicitlyMockPipelineVariable("openshiftSecret")

        when:
        def test = new JMeterTest(
                testingConfiguration: [
                        testType: 'JMeter',
                        planPath: 'this/path/doesnt/exist',
                ],
                script: script,
                psc: psc
        )
        def deployment = new ValidDeployment(
                config: [
                        branchPattern         : 'master',
                        deploymentType        : 'Openshift',
                        isProductionDeployment: false,
                        sdlcEnvironment       : 'test',
                        deployScript          : 'echo "This is a deploy"',
                        rollbackScript        : 'echo "This is a rollback"',
                        cloudName             : cloudName,
                        openshift             : [
                                credentialsId: 'fake-creds-id',
                                project      : 'fake-project-id'
                        ]
                ],
                tests: [test],
                script: script,
                psc: psc
        )

        simulatePodTemplate(psc, deployment, cloudName)
        def inputFileContents = '''timeStamp,elapsed,label,responseCode,responseMessage,threadName,dataType,success,failureMessage,bytes,sentBytes,grpThreads,allThreads,URL,Latency,IdleTime,Connect
1663706560865,841,Make Passing SCM Request,401,Unauthorized,Simulate 10 Simultaneous Users making 20 requests each 1-9,text,false,,351,502,10,10,https://adjudicator.apps.gp-2-nonprod.openshift.cignacloud.com/adjudicate,840,0,651
'''

        File.createTempFile('jmeter-results','.jtl').with { complianceRequestFile ->

            complianceRequestFile.deleteOnExit()
            complianceRequestFile.write(inputFileContents)
            1 * getPipelineMock("readFile")(*_) >> inputFileContents
            deployment.run([], [])
        }

        then:
        1 * getPipelineMock("echo")('Caught exception while testing, attempting a rollback.')
        def exception = thrown(Exception)
        exception.message == "'1' JMeter test failures"
    }

    def """When a deployment phase is called with withEnv configured, the withEnv step is called with
        it's contents, otherwise no env is set"""() {
        when:
        def deployment = new ValidDeployment(
                config: [
                        branchPattern         : 'master',
                        deploymentType        : 'valid',
                        sdlcEnvironment       : 'test',
                        isProductionDeployment: false
                ],
                script: script,
                psc: psc
        )
        deployment.config += extraConfig
        simulatePodTemplate(psc, deployment, cloudName)
        deployment.run([], [])

        then:
        if (extraConfig.withEnv != null)
            1 * getPipelineMock("withEnv").call(actualEnv, _)
        else
            0 * getPipelineMock("withEnv").call(actualEnv, _)

        where:
        extraConfig << [
                [withEnv: ['STUFF=hello']],
                [:]
        ]
        actualEnv << [
                ['STUFF=hello'],
                null
        ]
    }

    def "When a testing phase returns a zero exit code from deployment, post deployment is attempted"() {
        given:
        getPipelineMock("sh")({ it =~ /jmeter.*/ }) >> 0
        when:
        def test = new JMeterTest(
                testingConfiguration: [
                        testType: 'JMeter',
                        planPath: 'this/path/doesnt/exist',
                        args    : ''
                ],
                script: script,
                psc: psc,
        )
        def deployment = new ValidDeployment(
                config: [
                        branchPattern         : 'master',
                        deploymentType        : 'Openshift',
                        isProductionDeployment: false,
                        sdlcEnvironment       : 'test',
                        deployScript          : 'echo "This is a deploy"',
                        postDeployScript      : 'echo "This is a postDeploy"',
                        cloudName             : cloudName,
                        openshift             : [
                                credentialsId: 'fake-creds-id',
                                project      : 'fake-project-id'
                        ]
                ],
                script: script,
                tests: [test],
                psc: psc,
        )
        def inputFileContents = '''timeStamp,elapsed,label,responseCode,responseMessage,threadName,dataType,success,failureMessage,bytes,sentBytes,grpThreads,allThreads,URL,Latency,IdleTime,Connect
1663706560865,841,Make Passing SCM Request,401,Unauthorized,Simulate 10 Simultaneous Users making 20 requests each 1-9,text,true,,351,502,10,10,https://adjudicator.apps.gp-2-nonprod.openshift.cignacloud.com/adjudicate,840,0,651
'''
        File.createTempFile('jmeter-results','.jtl').with { complianceRequestFile ->
            complianceRequestFile.deleteOnExit()
            complianceRequestFile.write(inputFileContents)
            1 * getPipelineMock("readFile")(*_) >> inputFileContents
            simulatePodTemplate(psc, deployment, cloudName)
            deployment.run([], [])
        }

        then:
        1 * getPipelineMock("echo")('Deploy test successful, running post deployment.')
    }

    def "When runDeployment is called with a specified action, the specified action is taken"() {
        given:
        ValidDeployment deployment = Spy(constructorArgs: [
                config: [cloudName: cloudName],
                script: script,
                psc   : psc,
        ]) {
            prePodConfig() >> true
        }
        when:
        simulatePodTemplate(psc, deployment, cloudName)
        deployment.runDeployment(whereAction)
        then:
        1 * deployment."$whereAction"()
        where:
        whereAction << ['deploy', 'rollback']
    }

    def """When a deployment is intended for production, the base article is auto-injected in to the compliance configuration'"""() {
        given:
        explicitlyMockPipelineVariable("openshiftSecret")

        when:
        def deployment = new ValidDeployment(
                config: [
                        branchPattern         : 'master',
                        deploymentType        : 'Openshift',
                        isProductionDeployment: true,
                        sdlcEnvironment       : 'test',
                        deployScript          : 'echo "This is a deploy"',
                        rollbackScript        : 'echo "This is a rollback"',
                        openshift             : [
                                credentialsId: 'fake-creds-id',
                                project      : 'fake-project-id'
                        ]
                ],
                script: script,
                psc: psc,
        )
        deployment.init()
        deployment.prePodConfig()
        deployment.run([], [])

        then:
        String goalConfig = psc.goalsConfig.toJson()
        goalConfig.contains('Base')
    }

    def '''when a deployment doesn't have a ticket defined, a normal change is auto-injected in to the pipeline'''() {
        given:
        explicitlyMockPipelineStep('updateGitStatus')
        explicitlyMockPipelineStep('override')
        explicitlyMockPipelineVariable("openshiftSecret")
        Notification notification = Mock()

        // [i]['data']['Servicenow']['normal_change']['change_request'][0]
        psc.complianceValidator.adjudicatorResponse = mockAdjudicatorResponse()
        when:
        def deployment = new PlzDeployment(
                config: [
                        branchPattern         : 'master',
                        deploymentType        : 'Openshift',
                        isProductionDeployment: true,
                        sdlcEnvironment       : 'test',
                        deployScript          : 'echo "This is a deploy"',
                        rollbackScript        : 'echo "This is a rollback"',
                        cloudName             : 'test-cloud',
                        openshift             : [
                                credentialsId: 'fake-creds-id',
                                project      : 'fake-project-id'
                        ]
                ],
                script: script,
                psc: psc,
                notification: notification
        )
        simulatePodTemplate(psc, deployment)
        deployment.run([], [])

        then:
        psc.complianceValidator.normalChangeData.type == 'normal'
        1 * getPipelineMock('echo')("Running Ticket phase in cloud/group 'test-cloud'.")
    }

    private LinkedHashMap<String, ArrayList<LinkedHashMap<String, Serializable>>> mockAdjudicatorResponse() {
        [
            articles: [
                [
                    name: 'Base',
                    data: [
                        Servicenow: [
                            normal_change: [
                                change_request: [
                                    [
                                        sys_id: '123456789',
                                        type  : 'normal'
                                    ]
                                ]
                            ]
                        ]
                    ]
                ]
            ]
        ]
    }
}
