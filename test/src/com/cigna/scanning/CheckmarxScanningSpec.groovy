package com.cigna.scanning

import com.cigna.jenkins_spock.JenkinsPipelineSpecification
import jenkins.plugins.http_request.ResponseContentSupplier

class CheckmarxScanningSpec extends JenkinsPipelineSpecification {

    class Script {

        def env = [
            GIT_BRANCH                    : 'stuff',
            GIT_COMMIT                    : 'stuff',
            GIT_PREVIOUS_COMMIT           : 'stuff',
            GIT_PREVIOUS_SUCCESSFUL_COMMIT: 'stuff'
        ]

        def scm = [
            userRemoteConfigs: [
                [url: 'https://github.sys.cigna.com/somecool_project/super_cool.git']
            ]
        ]
    }

    def script = new Script()

    def setup() {
        explicitlyMockPipelineVariable("CX_PROJECT_TEAM_NAME")
        explicitlyMockPipelineVariable("CX_PRODUCTION_BRANCH")
        explicitlyMockPipelineVariable("CX_PROJECT_NAME")
        explicitlyMockPipelineVariable("CX_USR")
        explicitlyMockPipelineVariable("CX_PSW")
        explicitlyMockPipelineVariable("EPF_GITHUB_ACCESS_TOKEN")

    }

    def """When checkmarxScan is called then the scan is run"""() {
        given:
        ResponseContentSupplier response = new ResponseContentSupplier(
            githubResponse,
            200
        )
        def testScmUrl = "https://github.sys.cigna.com/some_cool_org/super_cool_project.git"

        script.scm = [
            userRemoteConfigs: [
                [
                    url: testScmUrl
                ]
            ]
        ]
        when:
        CheckmarxScanning scan = new CheckmarxScanning(
            config: [
                branchPattern: 'stuff',
                checkmarx    : [
                    credentialsId: 'global-checkmarx-id',
                    settings     : [
                        CX_PROJECT_TEAM_NAME: 'DevOps',
                        CX_PROJECT_NAME     : 'Conduit-Project',
                    ]
                ]
            ],
            script: script
        )
        scan.scan()

        then:
        1 * getPipelineMock("httpRequest")({ args -> args.url == githubUrl }) >> response
        1 * getPipelineMock("echo").call('Starting Checkmarx Scan...')
        1 * getPipelineMock('sh')({ it.returnStatus == true }) >> 0

        where:
        githubUrl << [
            "https://github.sys.cigna.com/api/v3/repos/some_cool_org/super_cool_project"
        ]
        githubResponse << [ // this is not the full response schema, shortened for brevity
                            '''{
                                "name": "Hello-World",
                                "full_name": "octocat/Hello-World",
                                "default_branch": "main",
                        }'''
        ]
    }

    def """When checkmarxScan is called then the scan is run if project name not provided"""() {
        given:
        ResponseContentSupplier response = new ResponseContentSupplier(
            githubResponse,
            200
        )
        def testScmUrl = "https://github.sys.cigna.com/some_cool_org/super_cool_project.git"
        script.scm = [
            userRemoteConfigs: [
                [
                    url: testScmUrl
                ]
            ]
        ]
        when:
        CheckmarxScanning scan = new CheckmarxScanning(
            config: [
                branchPattern: 'stuff',
                checkmarx    : [
                    credentialsId: 'global-checkmarx-id',
                    settings     : [
                        CX_PROJECT_TEAM_NAME: 'DevOps',
                    ]
                ]
            ],
            script: script
        )
        scan.scan()

        then:
        1 * getPipelineMock("httpRequest")({ args -> args.url == githubUrl }) >> response
        1 * getPipelineMock("echo").call('Starting Checkmarx Scan...')
        1 * getPipelineMock('sh')({ it.returnStatus == true }) >> 0

        where:
        githubUrl << [
            "https://github.sys.cigna.com/api/v3/repos/some_cool_org/super_cool_project"
        ]
        githubResponse << [ // this is not the full response schema, shortened for brevity
                            '''{
                                "name": "Hello-World",
                                "full_name": "octocat/Hello-World",
                                "default_branch": "main",
                        }'''
        ]

    }

    def """When verboseEnabled is set, verbose mode is enabled for CheckMarx"""() {
        given:
        ResponseContentSupplier response = new ResponseContentSupplier(
            githubResponse,
            200
        )
        def testScmUrl = "https://github.sys.cigna.com/some_cool_org/super_cool_project.git"
        script.scm = [
            userRemoteConfigs: [
                [
                    url: testScmUrl
                ]
            ]
        ]
        when:
        CheckmarxScanning scan = new CheckmarxScanning(
            config: [
                branchPattern: 'stuff',
                checkmarx    : [
                    credentialsId: 'global-checkmarx-id',
                    settings     : [
                        CX_PROJECT_TEAM_NAME: 'DevOps',
                        CX_PROJECT_NAME     : 'Conduit-Project',
                        verboseEnabled      : true
                    ]
                ]
            ],
            script: script
        )
        scan.scan()

        then:
        1 * getPipelineMock("httpRequest")({ args -> args.url == githubUrl }) >> response
        1 * getPipelineMock("sh")({ it.script ==~ /dso-cli.*-v.*/ }) >> 0

        where:
        githubUrl << [
            "https://github.sys.cigna.com/api/v3/repos/some_cool_org/super_cool_project"
        ]
        githubResponse << [ // this is not the full response schema, shortened for brevity
                            '''{
                                "name": "Hello-World",
                                "full_name": "octocat/Hello-World",
                                "default_branch": "main",
                        }'''
        ]
    }

    def """When checkmarxLogEnabled is set, cli log creation  is enabled for Checkmarx"""() {
        given:
        ResponseContentSupplier response = new ResponseContentSupplier(
            githubResponse,
            200
        )
        def testScmUrl = "https://github.sys.cigna.com/some_cool_org/super_cool_project.git"
        script.scm = [
            userRemoteConfigs: [
                [
                    url: testScmUrl
                ]
            ]
        ]
        when:
        CheckmarxScanning scan = new CheckmarxScanning(
            config: [
                branchPattern: 'stuff',
                checkmarx    : [
                    credentialsId: 'global-checkmarx-id',
                    settings     : [
                        CX_PROJECT_TEAM_NAME: 'DevOps',
                        CX_PROJECT_NAME     : 'Conduit-Project',
                        checkmarxLogEnabled : true
                    ]
                ]
            ],
            script: script
        )
        scan.scan()

        then:
        1 * getPipelineMock("httpRequest")({ args -> args.url == githubUrl }) >> response
        1 * getPipelineMock("sh")({ it.script ==~ /dso-cli.*-Log cxcli\.log.*/ }) >> 0

        where:
        githubUrl << [
            "https://github.sys.cigna.com/api/v3/repos/some_cool_org/super_cool_project"
        ]
        githubResponse << [ // this is not the full response schema, shortened for brevity
                            '''{
                                "name": "Hello-World",
                                "full_name": "octocat/Hello-World",
                                "default_branch": "main",
                        }'''
        ]
    }

    def """When forceScan is set, a force scan is enabled for Checkmarx"""() {
        given:
        ResponseContentSupplier response = new ResponseContentSupplier(
            githubResponse,
            200
        )
        def testScmUrl = "https://github.sys.cigna.com/some_cool_org/super_cool_project.git"
        script.scm = [
            userRemoteConfigs: [
                [
                    url: testScmUrl
                ]
            ]
        ]
        when:
        CheckmarxScanning scan = new CheckmarxScanning(
            config: [
                branchPattern: 'stuff',
                checkmarx    : [
                    credentialsId: 'global-checkmarx-id',
                    settings     : [
                        CX_PROJECT_TEAM_NAME: 'DevOps',
                        CX_PROJECT_NAME     : 'Conduit-Project',
                        forceScan           : true
                    ]
                ]
            ],
            script: script
        )
        scan.scan()

        then:
        1 * getPipelineMock("httpRequest")({ args -> args.url == githubUrl }) >> response
        1 * getPipelineMock("sh")({ it.script ==~ /dso-cli.*-ForceScan.*/ }) >> 0

        where:
        githubUrl << [
            "https://github.sys.cigna.com/api/v3/repos/some_cool_org/super_cool_project"
        ]
        githubResponse << [ // this is not the full response schema, shortened for brevity
                            '''{
                                "name": "Hello-World",
                                "full_name": "octocat/Hello-World",
                                "default_branch": "main",
                        }'''
        ]
    }

    def """When projectAS is set, the provided application service ID provided gets """() {
        given:
        ResponseContentSupplier response = new ResponseContentSupplier(
            githubResponse,
            200
        )
        def testScmUrl = "https://github.sys.cigna.com/some_cool_org/super_cool_project.git"
        script.scm = [
            userRemoteConfigs: [
                [
                    url: testScmUrl
                ]
            ]
        ]
        when:
        CheckmarxScanning scan = new CheckmarxScanning(
            config: [
                branchPattern: 'stuff',
                checkmarx    : [
                    credentialsId: 'global-checkmarx-id',
                    settings     : [
                        CX_PROJECT_TEAM_NAME     : 'DevOps',
                        CX_PROJECT_NAME          : 'Conduit-Project',
                        CX_PROJECT_APP_SERVICE_ID: 'AS123456'
                    ]
                ]
            ],
            script: script
        )
        scan.scan()

        then:
        1 * getPipelineMock("httpRequest")({ args -> args.url == githubUrl }) >> response
        1 * getPipelineMock("sh")({ it.script ==~ /dso-cli.*-projectAS.*/ }) >> 0

        where:
        githubUrl << [
            "https://github.sys.cigna.com/api/v3/repos/some_cool_org/super_cool_project"
        ]
        githubResponse << [ // this is not the full response schema, shortened for brevity
            '''{
                "name": "Hello-World",
                "full_name": "octocat/Hello-World",
                "default_branch": "main",
            }'''
        ]
    }
}
