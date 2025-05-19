package com.cigna.common.scm


import com.cigna.jenkins_spock.JenkinsPipelineSpecification
import jenkins.plugins.http_request.ResponseContentSupplier

class CommonGitSpec extends JenkinsPipelineSpecification {

    class Script {
        public Map scm = [:]
        public Map env = [:]
    }

    def script = new Script()
    def config = [:]

    def setup() {
        
        explicitlyMockPipelineStep("echo")
        explicitlyMockPipelineStep("withCredentials")
        explicitlyMockPipelineStep('updateGitlabCommitStatus')
    }

    def """Check scm url vars are set to use proper scm provider to update commit status"""() {
        given:
            def testScmUrl = "https://github.sys.cigna.com/some_cool_org/super_cool_project.git"
            def script = [
                scm: [
                    userRemoteConfigs: [
                        [
                            url: testScmUrl
                        ]
                    ]
                ]
            ]
            CommonGit cg = new CommonGit(config, script)

        when:
            String scmUrl = cg.scmUrl
            String scmName = cg.scmHost
            String org = cg.org
            String repo = cg.repo

        then:
            scmName == "github.sys.cigna.com"
            scmUrl == "https://github.sys.cigna.com/some_cool_org/super_cool_project.git"
            org == "some_cool_org"
            repo == "super_cool_project"
    }

    def """GitLab call passes correct parameters to gitlab status update api call"""() {
        given:
            def testScmUrl = "https://git.sys.cigna.com/some_cool_org/super_cool_project.git"
            script.scm = [
                userRemoteConfigs: [
                    [
                        url: testScmUrl
                    ]
                ]
            ]

        when:
            CommonGit cg = new CommonGit(config, script)
            cg.updateGitStatus(name: testName, state: testState)

        then:
            1 * getPipelineMock("updateGitlabCommitStatus")([name: testName, state: testState])
        
        where:
            testName << ['Conduit', 'Testing']
            testState << ['pending', 'failed']

    }

    def """GitHub call passes correct parameters to github status update api call"""() {
        given:
            explicitlyMockPipelineVariable("EPF_GITHUB_ACCESS_TOKEN")
            script.scm = [
                userRemoteConfigs: [
                    [
                        url: testSCM
                    ]
                ]
            ]
            script.env = [
                GIT_COMMIT: testCommitSHA,
                BUILD_URL : testBuildUrl,
                BRANCH_NAME: testBranch
            ]

        when:
            CommonGit cg = new CommonGit(config, script)
            cg.updateGitStatus(testName, testState, 'message', 'continuous-integration/jenkins')

        then:
            sendRequest * getPipelineMock("httpRequest")({
                it['url'] ==~ /^.*\/cigna\/$testRepo\/statuses\/.*/
            }) >> new ResponseContentSupplier("", 200)
            sendRequest * getPipelineMock("string.call").call(['credentialsId':'prd-github-access-token', 'variable':'EPF_GITHUB_ACCESS_TOKEN'])
            echoIsCalled * getPipelineMock("echo").call(*_)
            sendRequest * getPipelineMock("withCredentials").call(*_)

        where:
            testName << ['Conduit', 'Testing', 'Conduit']
            testState << ['pending', 'failure', 'success']
            testSCM << ['https://github.sys.cigna.com/cigna/test-one', 'https://github.sys.cigna.com/cigna/test-two', 'https://github.sys.cigna.com/cigna/test-three']
            testRepo << ['test-one', 'test-two', 'test-three']
            testBuildUrl << ['https://jenkins.sys.cigna.com/job/test-one', 'https://jenkins.sys.cigna.com/job/test-two', 'https://jenkins.sys.cigna.com/job/test-three']
            testCommitSHA << ['deadbeef8ed43cbbea493c5e4495dbee038776d2', 'feeddeed8ed43cbbea493c5e4495dbee038776d2', null]
            testBranch << ['branch1', 'branch2', 'branch3']
            sendRequest << [1, 1, 0]
            echoIsCalled << [2, 2, 1]
    }

    def """When grabChangedFiles is called steps are run to set the changedFiles list"""() {
        given:
            ResponseContentSupplier response = new ResponseContentSupplier(
                githubResponse,
                200
            )
            def testScmUrl = "https://github.sys.cigna.com/some_cool_org/super_cool_project.git"
            explicitlyMockPipelineVariable("EPF_GITHUB_ACCESS_TOKEN")
            script.scm = [
                userRemoteConfigs: [
                    [
                        url: testScmUrl
                    ]
                ]
            ]
            script.env = [
                BRANCH_NAME: branchName
            ]

        when:
            CommonGit cg = new CommonGit(config, script)
            cg.grabChangedFiles()
        
        then:
            1 * getPipelineMock("httpRequest")({ args ->
                args.url == githubUrl
            }) >> response
            cg.changedFiles == ['cool', 'supercool']

        where:
            branchName << ['test', 'PR-201']
            githubUrl << [
                "https://github.sys.cigna.com/api/v3/repos/some_cool_org/super_cool_project/commits/test",
                "https://github.sys.cigna.com/api/v3/repos/some_cool_org/super_cool_project/pulls/201/files"
            ]
            githubResponse << [
                '{"files": [{"filename": "cool"}, {"filename": "supercool"}]}',
                '[{"filename": "cool"}, {"filename": "supercool"}]'
            ]
    }

    def """When getDefaultBranch is called we get the repository details and return the default branch property"""(){
        given:
            ResponseContentSupplier response = new ResponseContentSupplier(
                githubResponse,
                200
            )
            def testScmUrl = "https://github.sys.cigna.com/some_cool_org/super_cool_project.git"
            explicitlyMockPipelineVariable("EPF_GITHUB_ACCESS_TOKEN")
            script.scm = [
                userRemoteConfigs: [
                    [
                        url: testScmUrl
                    ]
                ]
            ]

        when:
            CommonGit cg = new CommonGit(config, script)
            String defaultBranch = cg.getDefaultBranchName()

        then:
            1 * getPipelineMock("httpRequest")({ args ->
                args.url == githubUrl
            }) >> response

            assert defaultBranch == "main"
            

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

    def """When getDefaultBranch is called we get the repository details, we fail to parse the json response, and return a string containing a space"""(){
        given:
            ResponseContentSupplier response = new ResponseContentSupplier(
                githubResponse,
                200
            )
            def testScmUrl = "https://github.sys.cigna.com/some_cool_org/super_cool_project.git"
            explicitlyMockPipelineVariable("EPF_GITHUB_ACCESS_TOKEN")
            script.scm = [
                userRemoteConfigs: [
                    [
                        url: testScmUrl
                    ]
                ]
            ]

        when:
            CommonGit cg = new CommonGit(config, script)
            String branch = cg.getDefaultBranchName()

        then:
            1 * getPipelineMock("httpRequest")({ args ->
                args.url == githubUrl
            }) >> response
            assert branch == " "

        where:
            githubUrl << [
                "https://github.sys.cigna.com/api/v3/repos/some_cool_org/super_cool_project"
            ]
            githubResponse << [ // this is not the full response schema, shortened for brevity
                '''
                    "name": "Hello-World"
                    "full_name": "octocat/Hello-World
                    "default_branch": "main"
                '''
            ]
    }

    def """When getDefaultBranch is called we get a non 200 code with a json response"""(){
        given:
            ResponseContentSupplier response = new ResponseContentSupplier(
                githubResponse,
                401
            )
            def testScmUrl = "https://github.sys.cigna.com/some_cool_org/super_cool_project.git"
            explicitlyMockPipelineVariable("EPF_GITHUB_ACCESS_TOKEN")
            script.scm = [
                userRemoteConfigs: [
                    [
                        url: testScmUrl
                    ]
                ]
            ]

        when:
            CommonGit cg = new CommonGit(config, script)
            String defaultBranch = cg.getDefaultBranchName()

        then:
            1 * getPipelineMock("httpRequest")({ args -> args.url == githubUrl }) >> response
            1 * getPipelineMock("echo")({ String args -> args.startsWith("Getting repository details") })
            
            assert response.status != 200
            assert defaultBranch == " "
            

        where:
            githubUrl << [
                "https://github.sys.cigna.com/api/v3/repos/some_cool_org/super_cool_project"
            ]
            githubResponse << [
                '''{
                        "message": "Unauthorized",
                        "documentation_url": "https://docs.github.com/enterprise-server@3.9/rest/reference/repos#get-a-repository"
                }'''
            ]
    }
}
