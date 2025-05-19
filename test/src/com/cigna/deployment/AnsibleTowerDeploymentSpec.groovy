package com.cigna.deployment

import com.cigna.SinglePodTest
import groovy.json.JsonSlurper
import jenkins.plugins.http_request.ResponseContentSupplier

class AnsibleTowerDeploymentSpec extends SinglePodTest {

    private JsonSlurper readJSON = new JsonSlurper()

    def setup() {
        explicitlyMockPipelineStep('readJSON')
        explicitlyMockPipelineStep('updateGitStatus')
        explicitlyMockPipelineVariable("EPF_ANSIBLE_ACCESS_TOKEN") << 'MOCK_TOKEN'
        initScriptAndPsc()
    }

    def setupDeploymentInstance(boolean debug = false, String endpoint = 'https://fakehost.sys.cigna.com/api/v2') {
        return new AnsibleTowerDeployment(script: script, psc: psc,
            deploymentConfiguration: [
                deploymentType : 'ansibleTower',
                sdlcEnvironment: 'prod',
                branchPattern  : '.*',
                ansibleTower   : [
                    debug             : debug,
                    statusWaitTime    : 0.1,
                    ansibleEnvEndpoint: endpoint,
                    template          : [
                        name         : 'Test-Template',
                        id           : 10,
                        limit        : 'dev',
                        credentialsId: 'Test Credential',
                        survey       : [
                            environment: 'prod',
                            another    : 'test',
                        ],
                    ],
                    api               : [
                        credentialsId: 'prd-ansible-tower-creds',
                    ],
                ]
            ])
    }

    def """that ansibleTower phase calls job_templates launch api endpoint and polls until either the job fails or
           successful is returned"""() {
        when:
        def deployInstance = setupDeploymentInstance()
        deployInstance.deploy()
        then:
        3 * getPipelineMock('echo')(*_)

        1 * getPipelineMock("httpRequest")(
            {
                it['url'].endsWith '/job_templates/10/launch/'
            }
        ) >> new ResponseContentSupplier('{ "job" : 1 }', 200)

        1 * getPipelineMock("httpRequest")(
            {
                it['url'].endsWith '/jobs/1/'
            }
        ) >> new ResponseContentSupplier('{ "job" : 1, "status" : "pending" }', 200)

        1 * getPipelineMock("httpRequest")(
            {
                it['url'].endsWith '/credentials?name=Test+Credential'
            }
        ) >> new ResponseContentSupplier('{"count": 1,"next": null,"previous": null,"results": [{"id": 101}]}',
            200)

        1 * getPipelineMock("httpRequest")(
            {
                it['url'].endsWith '/jobs/1/'
            }
        ) >> new ResponseContentSupplier('{ "job" : 1, "status" : "running" }', 200)

        1 * getPipelineMock("httpRequest")(
            {
                it['url'].endsWith '/jobs/1/'
            }
        ) >> new ResponseContentSupplier('{ "job" : 1, "status" : "successful" }', 200)

        1 * getPipelineMock("httpRequest")(
            {
                it['url'].endsWith '/jobs/1/stdout/'
            }
        ) >> new ResponseContentSupplier('{ "job" : 1, "content" : "successful" }', 200)

        6 * getPipelineMock("readJSON")(*_) >> { arguments -> readJSON.parseText(arguments[0].text) }
    }


    def """that the debug flag being set to true will suppress the log calls and consoleLogResponseBody flag so that
             the consoleLogResponseBody flag is not set"""() {
        when:
                def deployInstance = setupDeploymentInstance(consoleLogResponseBody)
                deployInstance.deploy()

        then:
                numberOfEchoCalls * getPipelineMock('echo')(*_)

                1 * getPipelineMock("httpRequest")(
                        {
                        it['url'] ==~ /^.*\/credentials\?name=Test\+Credential/ &&
                                it['consoleLogResponseBody'] == consoleLogResponseBody
                        }
                ) >> new ResponseContentSupplier('''
                        |{
                        |    "count": 1,
                        |    "next": null,
                        |    "previous": null,
                        |    "results": [
                        |        {
                        |            "id": 101
                        |        }
                        |    ]
                        |}'''.stripMargin(), 200)

                1 * getPipelineMock("httpRequest")(
                        {
                        it['url'] ==~ /^.*\/job_templates\/10\/launch\// &&
                                it['consoleLogResponseBody'] == consoleLogResponseBody
                        }
                ) >> new ResponseContentSupplier('{ "job" : 1 }', 200)

                1 * getPipelineMock("httpRequest")(
                        {
                        it['url'] ==~ /^.*\/jobs\/1\// &&
                                it['consoleLogResponseBody'] == consoleLogResponseBody
                        }
                ) >> new ResponseContentSupplier('{ "job" : 1, "status" : "pending" }', 200)


                1 * getPipelineMock("httpRequest")(
                        {
                        it['url'] ==~ /^.*\/jobs\/1\// &&
                                it['consoleLogResponseBody'] == consoleLogResponseBody
                        }
                ) >> new ResponseContentSupplier('{ "job" : 1, "status" : "running" }', 200)

                1 * getPipelineMock("httpRequest")(
                        {
                        it['url'] ==~ /^.*\/jobs\/1\// &&
                                it['consoleLogResponseBody'] == consoleLogResponseBody
                        }
                ) >> new ResponseContentSupplier('{ "job" : 1, "status" : "successful" }', 200)

                1 * getPipelineMock("httpRequest")(
                        {
                        it['url'] ==~ /^.*\/jobs\/1\/stdout\// &&
                                it['consoleLogResponseBody'] == consoleLogResponseBody
                        }
                ) >> new ResponseContentSupplier('{ "job" : 1, "status" : "successful" }', 200)

                6 * getPipelineMock("readJSON")(*_) >> { arguments -> readJSON.parseText(arguments[0].text) }

        where:
                consoleLogResponseBody << [true, false]
                numberOfEchoCalls << [7, 3]
    }


    /** Since you can't mix data tables with thrown() calls in a way that lets you test both the success and failure
     * paths in a single test, we need to break out in to 2 tests
     */
    def """that an invalid credentials name results in an exception being thrown and the build fails."""() {
        when:
                def deployInstance = setupDeploymentInstance()
                deployInstance.deploy()

        then:
                0 * getPipelineMock('echo')(*_)

                1 * getPipelineMock("httpRequest")(
                        {
                        it['url'] ==~ /^.*\/credentials\?name=Test\+Credential/
                        }
                ) >> new ResponseContentSupplier('{"count": 0,"next": null,"previous": null,"results": []}', 200)

                0 * getPipelineMock("httpRequest")(
                        {
                        it['url'] ==~ /^.*\/job_templates\/10\/launch\//
                        }
                ) >> new ResponseContentSupplier('{ "job" : 1 }', 200)

                0 * getPipelineMock("httpRequest")(
                        {
                        it['url'] ==~ /^.*\/jobs\/1\//
                        }
                ) >> new ResponseContentSupplier('{ "job" : 1, "status" : "pending" }', 200)

                0 * getPipelineMock("httpRequest")(
                        {
                        it['url'] ==~ /^.*\/jobs\/1\//
                        }
                ) >> new ResponseContentSupplier('{ "job" : 1, "status" : "running" }', 200)

                0 * getPipelineMock("httpRequest")(
                        {
                        it['url'] ==~ /^.*\/jobs\/1\//
                        }
                ) >> new ResponseContentSupplier('{ "job" : 1, "status" : "successful" }', 200)

                0 * getPipelineMock("httpRequest")(
                        {
                        it['url'] ==~ /^.*\/jobs\/1\/stdout\//
                        }
                ) >> new ResponseContentSupplier('{ "job" : 1, "status" : "successful" }', 200)

                1 * getPipelineMock("readJSON")(*_) >> { arguments -> readJSON.parseText(arguments[0].text) }

                def exception = thrown(AnsibleTowerDeployment.FailedToGetCredentialsID)
                exception.message == 'Unable to fetch Ansible Tower credentials with name \'Test Credential\': ' +
                        'count 0 status 200 [{"count": 0,"next": null,"previous": null,"results": []}]'
    }

    def """that the correct credentials id is being used in the launch template call"""() {
        when:
                def deployInstance = setupDeploymentInstance()
                deployInstance.deploy()

        then:
                3 * getPipelineMock('echo')(*_)

                1 * getPipelineMock("httpRequest")(
                        {
                        it['url'] ==~ /^.*\/credentials\?name=Test\+Credential/
                        }
                ) >> new ResponseContentSupplier('{"count": 1,"next": null,"previous": null,"results": [{"id": 101}]}',
                        200)

                1 * getPipelineMock("httpRequest")(
                        {
                        it['url'] ==~ /^.*\/job_templates\/10\/launch\// &&
                        it['requestBody'] ==~ /(?ms).*"credentials"\s+:\s+\[101].*/
                        }
                ) >> new ResponseContentSupplier('{ "job" : 1 }', 200)

                1 * getPipelineMock("httpRequest")(
                        {
                        it['url'] ==~ /^.*\/jobs\/1\//
                        }
                ) >> new ResponseContentSupplier('{ "job" : 1, "status" : "pending" }', 200)

                1 * getPipelineMock("httpRequest")(
                        {
                        it['url'] ==~ /^.*\/jobs\/1\//
                        }
                ) >> new ResponseContentSupplier('{ "job" : 1, "status" : "running" }', 200)

                1 * getPipelineMock("httpRequest")(
                        {
                        it['url'] ==~ /^.*\/jobs\/1\//
                        }
                ) >> new ResponseContentSupplier('{ "job" : 1, "status" : "successful" }', 200)

                1 * getPipelineMock("httpRequest")(
                        {
                        it['url'] ==~ /^.*\/jobs\/1\/stdout\//
                        }
                ) >> new ResponseContentSupplier('{ "job" : 1, "status" : "successful",  }', 200)

                6 * getPipelineMock("readJSON")(*_) >> { arguments -> readJSON.parseText(arguments[0].text) }
    }
}
