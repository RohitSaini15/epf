import com.cigna.builds.MavenBuild
import com.cigna.common.compliance.ComplianceValidator
import com.cigna.common.exception.ErrorStepException
import com.cigna.common.logging.ConsoleLogger
import com.cigna.common.notification.Notification
import com.cigna.common.utils.FeatureFlags
import com.cigna.common.utils.StashUtils
import com.cigna.common.utils.Utils
import com.cigna.state.PipelineStateContext
import com.evernorth.cloudnativebuild.mocks.JenkinsEnv
import com.cigna.jenkins_spock.JenkinsPipelineSpecification
import com.splunk.splunkjenkins.utils.LogEventHelper
import jenkins.plugins.http_request.ResponseContentSupplier

import java.util.regex.Matcher

class cignaBuildFlowSpec extends JenkinsPipelineSpecification {
    Script cignaBuildFlow
    def currentBuild = [:]
    def error = { String msg ->
        throw new ErrorStepException(msg)
    }

    def scmMock = [
        [
            name: 'master'
        ]
    ]
    def remoteConfigs = [
        [
            url: "https://github.sys.cigna.com/somecool_project/super_cool.git"
        ]
    ]

    ResponseContentSupplier response = new ResponseContentSupplier(
        '{"files": [{"filename": "cool"}, {"filename": "supercool"}]}',
        200
    )

    Notification notification

    def JOB_NAME = "orchestrators-folders/job/test/job/here"
    def STASH_NAME = StashUtils.normalize(JOB_NAME)
    def BUILD_NUMBER = '123'
    def BRANCH_NAME = 'feature/api-test'
    def env = [:]

    PipelineStateContext psc

    def setup() {

        env = JenkinsEnv.build([
            JOB_NAME              : JOB_NAME,
            JENKINS_URL           : "https://orchestrator1.orchestrator-v2.sys.cigna.com",
            GIT_COMMIT            : '177a8fa56d5cc76c9ca715cca22776319f829b70',
            CNP_DEFAULT_JAVA_IMAGE: 'cnp/cnp-docker-maven-java11:1.0.2-dev-ov2',
            BRANCH_NAME           : BRANCH_NAME,
            BUILD_NUMBER          : BUILD_NUMBER
        ])
        cignaBuildFlow = loadPipelineScriptForTest("vars/cignaBuildFlow.groovy")
        cignaBuildFlow.getBinding().setVariable("currentBuild", currentBuild)
        cignaBuildFlow.getBinding().setVariable("env", env)
        cignaBuildFlow.getBinding().setVariable("error", error)
        GroovyMock(ConsoleLogger, global: true)
        GroovyMock(LogEventHelper, global: true)
        notification = Mock(Notification)
        explicitlyMockPipelineStep('override')
        cignaBuildFlow.getBinding().setVariable("scm", explicitlyMockPipelineVariable("scm"))
        explicitlyMockPipelineVariable("out")
        explicitlyMockPipelineVariable('ARTIFACTORY_USER')
        explicitlyMockPipelineVariable('ARTIFACTORY_APIKEY')
        explicitlyMockPipelineVariable('userId')
        explicitlyMockPipelineStep('libraryResource')
        explicitlyMockPipelineStep('commonGit.updateGitStatus')
        explicitlyMockPipelineStep('updateGitStatus')
        explicitlyMockPipelineStep('updateGitlabCommitStatus')
        explicitlyMockPipelineStep('moveFiles')
        explicitlyMockPipelineStep('deleteFolder')
        explicitlyMockPipelineStep('echo')
        explicitlyMockPipelineStep('readYaml')
        explicitlyMockPipelineStep('readJSON')
        explicitlyMockPipelineStep('checkpoint')
        explicitlyMockPipelineStep('junit')
        getPipelineMock('readProperties')(*_) >> [:]
        getPipelineMock("httpRequest")(*_) >> response
        getPipelineMock("scm.getProperty")('branches') >> scmMock
        getPipelineMock("scm.getProperty")('userRemoteConfigs') >> remoteConfigs

        // This was added to allow for tests to pass when complianceValidator was called
        // since we need to regex match on a map field, we need a catch all (_)
        getPipelineMock("sh")({
            if (!(it instanceof Map)) {
                false
            } else {
                it?.script ==~ /cat .*/
            }
        }) >> '{"compliance":{"status":true, "results": [{\n' +
            '                "Status": "SUCCESS",\n' +
            '                "Message": "Assignment Group set.",\n' +
            '                "Article": "Ops Readiness: CMDB"\n' +
            '            }]}}'
        getPipelineMock("sh")({
            if (!(it instanceof Map)) {
                false
            } else {
                it?.script ==~ /.*curl.*/
            }
        }) >> "200"
        // mock a successful checkmarx run
        getPipelineMock('sh')({ it.script.startsWith('dso-cli checkmarx') }) >> 0

        getPipelineMock("sh")(_)
        explicitlyMockPipelineVariable("EPF_GITHUB_ACCESS_TOKEN")
        explicitlyMockPipelineVariable("MAVEN_SETTINGS")
        explicitlyMockPipelineVariable('QUAY_TOKEN')
        explicitlyMockPipelineVariable('quayToken')
        explicitlyMockPipelineVariable("sonarQubeToken")
        getPipelineMock('checkout')(_) >> [
            GIT_COMMIT         : '177a8fa56d5cc76c9ca715cca22776319f829b70',
            GIT_BRANCH         : 'main',
            GIT_PREVIOUS_COMMIT: '147a8fa56d5cc76c9ca715cca22776319f829b00'
        ]

        ComplianceValidator.urlTransformer = { Matcher m ->
            m.group(1)
        }
        psc = new PipelineStateContext(cignaBuildFlow, [:])
        cignaBuildFlow.getBinding().setVariable("psc", psc)
        FeatureFlags.podAutotuning.enabled = false
        FeatureFlags.reportOnNamespace = false
    }

    // this method is a little redundant, but it makes IntelliJ's analyzer happier about the arg types
    def cignaBuildFlow(Closure c, Notification notification = null) {
        cignaBuildFlow.call(c, notification)
    }

    def "When no phases are included an error message is received"() {
        when:
        cignaBuildFlow(
            {
                gitlabConnectionName = 'stuff'
            },
            notification
        )

        then:
        currentBuild.result == "FAILURE"
        1 * getPipelineMock("splunkins.send")(*_) >> { arguments ->
            def jobResultValue = "FAILURE"
            assert arguments['job_result'] ==~ /.*${jobResultValue}.*/
        }
        def exception = thrown(ErrorStepException)
        exception.message == 'You must define phases to run. Please verify your configuration' +
            ' file is correctly formatted'
    }

    def "When a phase with no type is included an error message is received'"() {
        when:
        cignaBuildFlow(
            {
                gitlabConnectionName = 'stuff'
                cloudName = 'notNull'
                phases = [
                    [
                        branchPattern: '.*'
                    ]
                ]
            },
            notification
        )

        then:
        currentBuild.result == "FAILURE"
        1 * getPipelineMock("splunkins.send")(*_) >> { arguments ->
            def jobResultValue = "FAILURE"
            assert arguments['job_result'] ==~ /.*${jobResultValue}.*/
        }
        def exception = thrown(ErrorStepException)
        exception.message ==~ /Unable to determine phase type for phase.*/
    }

    def "When no phases are matched to the current branch the user is notified that no actions will be taken'"() {
        when:
        cignaBuildFlow(
            {
                gitlabConnectionName = 'stuff'
                cloudName = 'notNull'
                phases = [
                    [
                        buildType    : "valid",
                        branchPattern: whereBranchPattern,
                        changePattern: whereChangePattern,
                        artifactory  : [
                            applicationName: "stuff",
                            credentialsId  : "stuff"
                        ],
                        checkmarx    : [
                            credentialsId: "stuff",
                            settings     : [
                                CX_PROJECT_TEAM_NAME: "stuff"
                            ]
                        ],
                        sonarQube    : [
                            credentialsId: "stuff"
                        ]
                    ]
                ]
            },
            notification
        )

        then:
        currentBuild.result == "SUCCESS"
        noBranchMatchEcho * getPipelineMock("echo")(
            'No phase branchPatterns matched the current branch, so no actions will be taken.'
        )
        noChangeMatchEcho * getPipelineMock("echo")({
            it == 'Did not match changePattern, will not run phase: [buildType:valid, branchPattern:master, changePattern:test, artifactory:[applicationName:stuff, credentialsId:stuff], checkmarx:[credentialsId:stuff, settings:[CX_PROJECT_TEAM_NAME:stuff]], sonarQube:[credentialsId:stuff]]'
        })

        where:
        whereBranchPattern << ['master', 'nomatch', 'master', 'master', 'master']
        noBranchMatchEcho << [0, 1, 1, 0, 0]
        whereChangePattern << [null, null, 'test', 'cool', 'supercool']
        noChangeMatchEcho << [0, 0, 1, 0, 0]
    }


    def '''when a build contains multiple containers with the same image:version, only include one in the pod template'''() {
        when:
        cignaBuildFlow {
            githubConnectionName = 'github'
            commitStatusName = 'Enterprise Pipeline Framework'

            cloudName = 'test-cloud'
            logHistoryCount = '5'
            webexTeamsRoom = 'bd7cdcb0-27a4-11ed-af92-f5afe966fcf4'
            phases = [
                [
                    moduleType            : 'maven',
                    branchPattern         : '.*',
                    sdlcEnvironment       : 'dev',
                    isProductionDeployment: false,
                    module                : [
                        image       : 'cnp/cnp-docker-maven-java8',
                        contractName: 'BUILD',
                        moduleName  : 'cnp-build-publish-maven-create',
                        subCommand  : 'build',
                        version     : 'latest',
                        commandName : "cnp-build-publish-maven-create.sh",
                    ],
                    container             : [
                        cpu   : 2000,
                        memory: 8000
                    ],
                    args                  : [
                        credentials: [
                            [id: 'cred']
                        ],
                        publish    : false
                    ]
                ],
                [
                    moduleType            : 'maven',
                    branchPattern         : '.*',
                    sdlcEnvironment       : 'dev',
                    isProductionDeployment: false,
                    module                : [
                        image       : 'cnp/cnp-docker-maven-java8',
                        contractName: 'BUILD',
                        moduleName  : 'cnp-build-publish-maven-create',
                        subCommand  : 'build',
                        version     : 'latest',
                        commandName : "cnp-build-publish-maven-create.sh",
                    ],
                    container             : [
                        cpu   : 2000,
                        memory: 8000
                    ],
                    args                  : [
                        credentials: [
                            [id: 'cred']
                        ],
                        publish    : false
                    ]
                ],
                [
                    moduleType            : 'test',
                    moduleName            : 'cnp-jenkins-job-build',
                    branchPattern         : '.*',
                    sdlcEnvironment       : 'dev',
                    isProductionDeployment: false,
                    args                  : [
                        jobName    : 'orchestrators-folders/job/hs-pipeline/job/Functional%20Test%20Apps/job/zz-test-wright-jobs/job/test-wright-dev',
                        jenkinsURL : 'https://orchestrator18.orchestrator-v2.sys.cigna.com/',
                        buildParams: 'test-categories=epf%2Fci',
                        credentials: [[id: 'cred']]
                    ]
                ],
                [
                    moduleType            : 'test',
                    moduleName            : 'cnp-jenkins-job-build',
                    branchPattern         : '.*',
                    sdlcEnvironment       : 'dev',
                    isProductionDeployment: false,
                    args                  : [
                        jobName    : 'orchestrators-folders/job/hs-pipeline/job/Functional%20Test%20Apps/job/zz-test-wright-jobs/job/test-wright-dev',
                        jenkinsURL : 'https://orchestrator18.orchestrator-v2.sys.cigna.com/',
                        buildParams: 'test-categories=epf%2Fci',
                        credentials: [[id: 'cred']]
                    ]
                ]
            ]
        }

        then:
        assert psc.podSelector.baseCloudName == 'test-cloud'
        assert psc.podSelector.podTemplates.size() == 1
        assert psc.podSelector.podTemplates['test-cloud'].findAll(/cnp-docker-maven-java8vlatest/).size() == 1
        assert psc.podSelector.podTemplates['test-cloud'].findAll(/cnp-docker-corev/).size() == 1
    }

    def '''when a pipeline references multiple clouds, ensure each gets a podTemplate'''() {
        when:
        cignaBuildFlow {
            githubConnectionName = 'github'
            commitStatusName = 'Enterprise Pipeline Framework'

            cloudName = 'test-cloud'
            logHistoryCount = '5'
            webexTeamsRoom = 'bd7cdcb0-27a4-11ed-af92-f5afe966fcf4'
            phases = [
                [
                    moduleType            : 'maven',
                    branchPattern         : '.*',
                    sdlcEnvironment       : 'dev',
                    isProductionDeployment: false,
                    module                : [
                        image       : 'cnp/cnp-docker-maven-java8',
                        contractName: 'BUILD',
                        moduleName  : 'cnp-build-publish-maven-create',
                        subCommand  : 'build',
                        version     : 'latest',
                        commandName : "cnp-build-publish-maven-create.sh",
                    ],
                    container             : [
                        cpu   : 2000,
                        memory: 8000
                    ],
                    args                  : [
                        credentials: [
                            [id: 'cred']
                        ],
                        publish    : false
                    ]
                ],
                [
                    moduleType            : 'test',
                    moduleName            : 'cnp-jenkins-job-build',
                    branchPattern         : '.*',
                    sdlcEnvironment       : 'dev',
                    cloudName             : 'another-test',
                    isProductionDeployment: false,
                    args                  : [
                        jobName    : 'orchestrators-folders/job/hs-pipeline/job/Functional%20Test%20Apps/job/zz-test-wright-jobs/job/test-wright-dev',
                        jenkinsURL : 'https://orchestrator18.orchestrator-v2.sys.cigna.com/',
                        buildParams: 'test-categories=epf%2Fci',
                        credentials: [[id: 'cred']]
                    ]
                ]
            ]
        }

        then:
        assert psc.podSelector.podTemplates.size() == 2
    }

    def "When an invalid build type is provided an error of 'Invalid Build Type is received'"() {
        when:
        cignaBuildFlow(
            {
                gitlabConnectionName = 'stuff'
                cloudName = 'notNull'
                phases = [
                    [
                        branchPattern: 'master',
                        buildType    : "clearlyWrong"
                    ]
                ]
            },
            notification
        )

        then:
        currentBuild.result == "FAILURE"
        1 * getPipelineMock("splunkins.send")(*_) >> { arguments ->
            def jobResultValue = "FAILURE"
            assert arguments['job_result'] ==~ /.*${jobResultValue}.*/
        }
        1 * getPipelineMock("sendSplunkConsoleLog")(*_)
        2 * notification.notifyWithAllMethods(*_)
        2 * ConsoleLogger.logJobInfo(*_)
        def exception = thrown(ErrorStepException)
        exception.message == "Invalid Build Type. Error: com.cigna.builds.ClearlyWrongBuild"
    }

    def """Classes that aren't subclasses of Build cannot be instantiated"""() {
        when:
        cignaBuildFlow(
            {
                gitlabConnectionName = 'stuff'
                cloudName = 'notNull'
                phases = [
                    [
                        branchPattern: 'master',
                        buildType    : "invalid"
                    ]
                ]
            },
            notification
        )

        then:
        currentBuild.result == "FAILURE"
        1 * getPipelineMock("splunkins.send")(*_) >> { arguments ->
            def jobResultValue = "FAILURE"
            assert arguments['job_result'] ==~ /.*${jobResultValue}.*/
        }
        1 * getPipelineMock("sendSplunkConsoleLog")(*_)
        2 * notification.notifyWithAllMethods(*_)
        def exception = thrown(ErrorStepException)
        exception.message == "Invalid Build Type. Error: The provided buildType invalid" +
            " is not a valid subclass" +
            " of the Build abstract base class. This is a requirement of created build types."
    }

    def """artifactory.applicationName (when no package.json for node builds),
            artifactory.credentialsId,
            sonarQube.credentialsId, checkmarx.settings.CX_PROJECT_TEAM_NAME, and checkmarx.credentialsId
            are required"""() {
        when:
        cignaBuildFlow(
            {
                cloudName = 'notNull'
                phases = [
                    [
                        branchPattern: 'master',
                        buildType    : 'npm',
                        sonarQube    : [
                            credentialsId: whereSonarQubeCredentialsId,
                        ],
                        artifactory  : [
                            applicationName: whereArtifactoryApplicationName,
                            credentialsId  : whereArtifactoryCredentialsId
                        ],
                        checkmarx    : [
                            credentialsId: whereCheckmarxCredentialsId,
                            settings     : [
                                CX_PROJECT_TEAM_NAME: whereCheckmarxSettings
                            ]
                        ]
                    ]
                ]
            },
            notification
        )

        then:

        currentBuild.result == "FAILURE"
        1 * getPipelineMock("splunkins.send")(*_) >> { arguments ->
            arguments['job_result'].contains('FAILURE')
        }
        1 * getPipelineMock("sendSplunkConsoleLog")(*_)
        def exception = thrown(ErrorStepException)
        exception.message.contains(exceptionMessage)
        where:
        whereArtifactoryApplicationName << [null, "stuff", "stuff", "stuff", "stuff"]
        whereSonarQubeCredentialsId << ["stuff", null, "stuff", "stuff", "stuff"]
        whereArtifactoryCredentialsId << ["stuff", "stuff", null, "stuff", "stuff"]
        whereCheckmarxSettings << [["stuff"], ["stuff"], ["stuff"], [], ["stuff"]]
        whereCheckmarxCredentialsId << ["stuff", "stuff", "stuff", "stuff", null]
        exceptionMessage << [
            "Must have either artifactory.applicationName or a package.json name configured",
            "Missing required Npm Build specification: sonarQube.credentialsId",
            "Missing required Npm Build specification: artifactory.credentialsId",
            "Missing required Npm Build specification: checkmarx.settings.CX_PROJECT_TEAM_NAME",
            "Missing required Npm Build specification: checkmarx.credentialsId"
        ]
    }

    def """When currentBranchIsReleaseBranch is true, build instance executePreBuildStage,
            executeBuildAndTestStage and executePublishStage are all called otherwise just the
            first two are called. Also if customProperties is true, then the properties step is not called,
            otherwise it is."""() {
        when:
        cignaBuildFlow(
            {
                customProperties = booleanCustomProperties
                gitlabConnectionName = "stuff"
                mattermostWebhook = "stuff"
                emailRecipients = "DevOpsSystems@CIGNA.COM"
                cloudName = 'notNull'
                phases = [
                    [
                        buildType           : "valid",
                        branchPattern       : 'master',
                        releaseBranchPattern: whereBranchPatterns,
                        sonarQube           : [
                            credentialsId: "stuff"
                        ],
                        artifactory         : [
                            applicationName: "stuff",
                            credentialsId  : "stuff"
                        ],
                        checkmarx           : [
                            credentialsId: "stuff",
                            settings     : [
                                CX_PROJECT_TEAM_NAME: "stuff"
                            ]
                        ]
                    ]
                ]
            },
            notification
        )

        then:
        1 * getPipelineMock("splunkins.send")(*_) >> { arguments ->
            def jobResultValue = "SUCCESS"
            assert arguments['job_result'] ==~ /.*${jobResultValue}.*/
        }
        1 * getPipelineMock("sendSplunkConsoleLog")(*_)
        if (!booleanCustomProperties) {
            1 * getPipelineMock("properties")(*_)
        } else {
            0 * getPipelineMock("properties")(*_)
        }
        1 * notification.notifyWithAllMethods('Pipeline Started', 'Pipeline event', 'STARTED')
        (5 - timesBranchPatternNotMatched) * getPipelineMock("stage")(*_)
        timesBranchPatternNotMatched * getPipelineMock("echo")(
            "releaseBranchPattern stuff does not match "
                + "current branch master, so no publish steps are being taken."
        )

        where:
        whereBranchPatterns << ["stuff", "master", "master", "master"]
        timesBranchPatternNotMatched << [1, 0, 0, 0]
        booleanCustomProperties << [null, null, false, true]
    }

    def """When parallel phases are defined, the parallel primitive is called to execute them all in parallel."""() {
        when:
        cignaBuildFlow(
            {
                gitlabConnectionName = "stuff"
                mattermostWebhook = "stuff"
                emailRecipients = "DevOpsSystems@CIGNA.COM"
                cloudName = 'notNull'
                phases = [
                    // XXX.cnm - Don't be tempted to create a single variable and reference it 3+ times here
                    //         - because they will be 3 references to the same phase and 3 nested references
                    //         - to the same 3 nested phases resulting in reusing the same phase instances 3 times.
                    [
                        parallelType : 'jenkins',
                        branchPattern: '.*',
                        phases       : [
                            RunQualityCheckStep: [
                                buildType       : 'scanOnly',
                                branchPattern   : '.*',
                                checkmarxEnabled: false,
                                sonarEnabled    : true,
                                sonarQube       : [
                                    projectKey   : 'csp-medicare-enrollment-api',
                                    credentialsId: 'env.SONAR_CREDENTIAL_ID'
                                ]
                            ],
                            RunSecurityScanStep: [
                                buildType       : 'scanOnly',
                                branchPattern   : '.*',
                                checkmarxEnabled: true,
                                sonarEnabled    : false,
                                checkmarx       : [
                                    credentialsId: 'env.CX_CREDENTIAL',
                                    settings     : [
                                        CX_PROJECT_TEAM_NAME   : '9aa27efa-e38c-424a-937d-2e98dd81306a',
                                        CX_PROJECT_PROJECT_NAME: 'csp-medicare-enrollment-api',
                                        CX_EXCLUDE_FOLDER_LIST : '.m2, target',
                                    ]
                                ]
                            ],
                            CreateContainerStep: [
                                moduleType            : 'docker',
                                branchPattern         : '.*',
                                sdlcEnvironment       : 'dev',
                                isProductionDeployment: false,
                                moduleName            : 'cnp-build-image',
                                subCommand            : 'buildimage',
                                args                  : [
                                    credentials        : [
                                        [id: 'env.CNP_REGISTRY_DEV_CRED', env: ''],
                                    ],
                                    pomPath            : '',
                                    generateDockerFile : true,
                                    org                : 'csphs',
                                    appendCommitIdToTag: false,
                                    artifact           : 'lookup:publishUrl',
                                    registryName       : 'http://registry-dev.cigna.com'
                                ],
                            ]
                        ]
                    ],
                    [
                        parallelType : 'jenkins',
                        branchPattern: '.*',
                        phases       : [
                            RunQualityCheckStep: [
                                buildType       : 'scanOnly',
                                branchPattern   : '.*',
                                checkmarxEnabled: false,
                                sonarEnabled    : true,
                                sonarQube       : [
                                    projectKey   : 'csp-medicare-enrollment-api',
                                    credentialsId: 'env.SONAR_CREDENTIAL_ID'
                                ]
                            ],
                            RunSecurityScanStep: [
                                buildType       : 'scanOnly',
                                branchPattern   : '.*',
                                checkmarxEnabled: true,
                                sonarEnabled    : false,
                                checkmarx       : [
                                    credentialsId: 'env.CX_CREDENTIAL',
                                    settings     : [
                                        CX_PROJECT_TEAM_NAME   : '9aa27efa-e38c-424a-937d-2e98dd81306a',
                                        CX_PROJECT_PROJECT_NAME: 'csp-medicare-enrollment-api',
                                        CX_EXCLUDE_FOLDER_LIST : '.m2, target',
                                    ]
                                ]
                            ],
                            CreateContainerStep: [
                                moduleType            : 'docker',
                                branchPattern         : '.*',
                                sdlcEnvironment       : 'dev',
                                isProductionDeployment: false,
                                moduleName            : 'cnp-build-image',
                                subCommand            : 'buildimage',
                                args                  : [
                                    credentials        : [
                                        [id: 'env.CNP_REGISTRY_DEV_CRED', env: '']
                                    ],
                                    pomPath            : '',
                                    generateDockerFile : true,
                                    org                : 'csphs',
                                    appendCommitIdToTag: false,
                                    artifact           : 'lookup:publishUrl',
                                    registryName       : 'http://registry-dev.cigna.com'
                                ],
                            ]
                        ]
                    ],
                    [
                        parallelType : 'jenkins',
                        branchPattern: '.*',
                        phases       : [
                            RunQualityCheckStep: [
                                buildType       : 'scanOnly',
                                branchPattern   : '.*',
                                checkmarxEnabled: false,
                                sonarEnabled    : true,
                                sonarQube       : [
                                    projectKey   : 'csp-medicare-enrollment-api',
                                    credentialsId: 'env.SONAR_CREDENTIAL_ID'
                                ]
                            ],
                            RunSecurityScanStep: [
                                buildType       : 'scanOnly',
                                branchPattern   : '.*',
                                checkmarxEnabled: true,
                                sonarEnabled    : false,
                                checkmarx       : [
                                    credentialsId: 'env.CX_CREDENTIAL',
                                    settings     : [
                                        CX_PROJECT_TEAM_NAME   : '9aa27efa-e38c-424a-937d-2e98dd81306a',
                                        CX_PROJECT_PROJECT_NAME: 'csp-medicare-enrollment-api',
                                        CX_EXCLUDE_FOLDER_LIST : '.m2, target',
                                    ]
                                ]
                            ],
                            CreateContainerStep: [
                                moduleType            : 'docker',
                                branchPattern         : '.*',
                                sdlcEnvironment       : 'dev',
                                isProductionDeployment: false,
                                moduleName            : 'cnp-build-image',
                                subCommand            : 'buildimage',
                                args                  : [
                                    credentials        : [
                                        [id: 'env.CNP_REGISTRY_DEV_CRED', env: ''],
                                        [id: 'env.CNP_QUAY_SIGN_TOKEN_CRED', env: ''],
                                    ],
                                    pomPath            : '',
                                    generateDockerFile : true,
                                    org                : 'csphs',
                                    appendCommitIdToTag: false,
                                    artifact           : 'lookup:publishUrl',
                                    registryName       : 'http://registry-dev.cigna.com'
                                ],
                            ]
                        ]
                    ]

                ]
            },
            notification
        )

        then:
        1 * getPipelineMock("sendSplunkConsoleLog")(*_)
        9 * getPipelineMock("parallel")(*_)
        currentBuild.result == "SUCCESS"
        assert psc.complianceValidator.state.size() == 12
    }

    def """When checkpoint phase is defined, checkpoint is called"""() {
        when:
        cignaBuildFlow(
            {
                gitlabConnectionName = "stuff"
                mattermostWebhook = "stuff"
                emailRecipients = "DevOpsSystems@CIGNA.COM"
                cloudName = 'notNull'
                phases = [
                    [
                        checkpointType: "simple",
                        branchPattern : '.*',
                        name          : 'test-checkpoint',
                    ]
                ]
            },
            notification
        )
        then:
        1 * getPipelineMock("checkpoint")(*_)
        1 * getPipelineMock("echo")("Creating 'test-checkpoint' checkpoint")
    }


    def """When additionalProperties is used, the properties included get added to the properties step call."""() {
        when:
        cignaBuildFlow(
            {
                additionalProperties = [
                    'additionalPropertiesHere'
                ]
                gitlabConnectionName = "stuff"
                mattermostWebhook = "stuff"
                emailRecipients = "DevOpsSystems@CIGNA.COM"
                cloudName = 'notNull'
                phases = [
                    [
                        buildType           : "valid",
                        branchPattern       : 'master',
                        releaseBranchPattern: '.*',
                        sonarQube           : [
                            credentialsId: "stuff"
                        ],
                        artifactory         : [
                            applicationName: "stuff",
                            credentialsId  : "stuff"
                        ],
                        checkmarx           : [
                            credentialsId: "stuff",
                            settings     : [
                                CX_PROJECT_TEAM_NAME: "stuff"
                            ]
                        ]
                    ]
                ]
            },
            notification
        )
        then:
        1 * getPipelineMock('properties')(
            {
                'additionalPropertiesHere' in it
            }
        )
    }

    def """additionalProperties and customProperties are mutually exclusive"""() {
        when:
        cignaBuildFlow(
            {
                customProperties = true
                additionalProperties = [
                    'addtionalPropertiesHere'
                ]
                gitlabConnectionName = "stuff"
                mattermostWebhook = "stuff"
                emailRecipients = "DevOpsSystems@CIGNA.COM"
                cloudName = 'notNull'
                phases = [
                    [
                        buildType           : "valid",
                        branchPattern       : 'master',
                        releaseBranchPattern: '.*',
                        sonarQube           : [
                            credentialsId: "stuff"
                        ],
                        artifactory         : [
                            applicationName: "stuff",
                            credentialsId  : "stuff"
                        ],
                        checkmarx           : [
                            credentialsId: "stuff",
                            settings     : "stuff"
                        ]
                    ]
                ]
            },
            notification
        )
        then:
        def exception = thrown(ErrorStepException)
        exception.message == 'additionalProperties and customProperties options are mutually exclusive'
    }

    def """When an exception is thrown while in the build stages, Gitlab commit status is set to
            failed and an exception is raised"""() {
        given:
        notification.notifyWithAllMethods('Pipeline Started', 'Pipeline event', 'STARTED') >> { throw new Exception("error") }

        when:
        cignaBuildFlow(
            {
                commitStatusName = commitStatName
                gitlabConnectionName = "stuff"
                mattermostWebhook = "stuff"
                emailRecipients = "DevOpsSystems@CIGNA.COM"
                cloudName = 'notNull'
                phases = [
                    [
                        branchPattern: 'master',
                        buildType    : "valid",
                        sonarQube    : [
                            credentialsId: "stuff"
                        ],
                        artifactory  : [
                            applicationName: "stuff",
                            credentialsId  : "stuff"
                        ],
                        checkmarx    : [
                            credentialsId: "stuff",
                            settings     : "stuff"
                        ]
                    ]
                ]
            },
            notification
        )

        then:
        def exception = thrown(ErrorStepException)
        exception.message == 'error'
        where:
        commitStatName << [null, 'Testing']
        commitStatCall << ['Conduit', 'Testing']
    }

    def """When handleError is called, ConsoleLogger.log is called currentBuild.result is set to
            'FAILURE' notification is sent splunkEvent is sent with splunkins and the error step is
            called"""() {
        when:
        def splunkEvent = [
            'event_tag': 'pipeline_tm_event',
            'build_url': 'urlStuff',
            'build_env': 'envStuff',
            'metadata' : 'metadataStuff',
            'causes'   : 'causeStuff',
        ]
        cignaBuildFlow.handleError(notification, [:], 'Test error message', splunkEvent)

        then:
        currentBuild.result == "FAILURE"
        1 * getPipelineMock("splunkins.send")(*_) >> { arguments ->
            def jobResultValue = "FAILURE"
            assert arguments['job_result'] ==~ /.*${jobResultValue}.*/
        }
        1 * notification.notifyWithAllMethods(*_)
        1 * ConsoleLogger.logJobInfo(*_)
        def exception = thrown(ErrorStepException)
        exception.message == 'Test error message'
    }

    def """When an invalid container version is provided, an error of Invalid containerVersion
            is received"""() {
        when:
        cignaBuildFlow(
            {
                gitlabConnectionName = "stuff"
                mattermostWebhook = "stuff"
                emailRecipients = "DevOpsSystems@CIGNA.COM"
                cloudName = 'notNull'
                phases = [
                    [
                        branchPattern   : 'master',
                        buildType       : "valid",
                        containerVersion: "clearlyWrongVersion!",
                        sonarQube       : [
                            credentialsId: "stuff"
                        ],
                        artifactory     : [
                            applicationName: "stuff",
                            credentialsId  : "stuff"
                        ],
                        checkmarx       : [
                            credentialsId: "stuff",
                            settings     : "stuff"
                        ]
                    ]
                ]
            },
            notification
        )

        then:
        currentBuild.result == "FAILURE"
        def exception = thrown(ErrorStepException)
        def error = 'Invalid build containerVersion. Regex for containerVersion is'
        exception.message.contains(error)
    }

    def """Classes that aren't subclasses of Packaging cannot be instantiated"""() {
        when:
        cignaBuildFlow(
            {
                gitlabConnectionName = "stuff"
                mattermostWebhook = "stuff"
                emailRecipients = "DevOpsSystems@CIGNA.COM"
                cloudName = 'notNull'
                phases = [
                    [
                        branchPattern: 'master',
                        packagingType: 'invalid'
                    ]
                ]
            },
            notification
        )

        then:
        currentBuild.result == "FAILURE"
        1 * getPipelineMock("splunkins.send")(*_) >> { arguments ->
            def jobResultValue = "FAILURE"
            assert arguments['job_result'] ==~ /.*${jobResultValue}.*/
        }
        1 * getPipelineMock("sendSplunkConsoleLog")(*_)
        2 * notification.notifyWithAllMethods(*_)
        def exception = thrown(ErrorStepException)
        assert exception.message.contains("Invalid Packaging Type")
    }

    def """When an invalid Packaging type is provided an error of 'Invalid Packaging Type is received'"""() {
        when:
        cignaBuildFlow(
            {
                gitlabConnectionName = "stuff"
                mattermostWebhook = "stuff"
                emailRecipients = "DevOpsSystems@CIGNA.COM"
                cloudName = 'notNull'
                phases = [
                    [
                        branchPattern: 'master',
                        packagingType: 'clearlyWrongPackagingType'
                    ]
                ]
            },
            notification
        )

        then:
        currentBuild.result == "FAILURE"
        1 * getPipelineMock("splunkins.send")(*_) >> { arguments ->
            def jobResultValue = "FAILURE"
            assert arguments['job_result'] ==~ /.*${jobResultValue}.*/
        }
        1 * getPipelineMock("sendSplunkConsoleLog")(*_)
        2 * notification.notifyWithAllMethods(*_)
        2 * ConsoleLogger.logJobInfo(*_)
        def exception = thrown(ErrorStepException)
        exception.message == "Invalid Packaging Type. Error: " +
            "com.cigna.packaging.ClearlyWrongPackagingTypePackaging"
    }

    def """Classes that aren't subclasses of Deployment cannot be instantiated"""() {
        when:
        cignaBuildFlow(
            {
                gitlabConnectionName = "stuff"
                mattermostWebhook = "stuff"
                emailRecipients = "DevOpsSystems@CIGNA.COM"
                cloudName = 'notNull'
                phases = [
                    [
                        branchPattern  : 'master',
                        deploymentType : 'invalid',
                        sdlcEnvironment: 'test',
                        openshift      : [
                            credentialsId: 'devops-system-kubernetes-plugin-sa-token',
                            project      : 'devops-system-kubernetes-plugin'
                        ],
                        helm           : [
                            version: '2.14.1',
                            command: 'stuff'
                        ]
                    ]
                ]
            },
            notification
        )

        then:
        currentBuild.result == "FAILURE"
        1 * getPipelineMock("splunkins.send")(*_) >> { arguments ->
            def jobResultValue = "FAILURE"
            assert arguments['job_result'] ==~ /.*${jobResultValue}.*/
        }
        1 * getPipelineMock("sendSplunkConsoleLog")(*_)
        2 * notification.notifyWithAllMethods(*_)
        def exception = thrown(ErrorStepException)
        assert exception.message.contains("Invalid Deployment Type")
    }

    def """When an invalid Deployment type is provided an error of 'Invalid Deployment Type is received'"""() {
        when:
        cignaBuildFlow(
            {
                gitlabConnectionName = "stuff"
                mattermostWebhook = "stuff"
                emailRecipients = "DevOpsSystems@CIGNA.COM"
                cloudName = 'notNull'
                phases = [
                    [
                        branchPattern  : 'master',
                        deploymentType : 'clearlyWrongDeploymentType',
                        sdlcEnvironment: 'test',
                        openshift      : [
                            credentialsId: 'devops-system-kubernetes-plugin-sa-token',
                            project      : 'devops-system-kubernetes-plugin'
                        ],
                        helm           : [
                            version: '2.14.1',
                            command: 'stuff'
                        ]
                    ]
                ]
            },
            notification
        )

        then:
        currentBuild.result == "FAILURE"
        1 * getPipelineMock("splunkins.send")(*_) >> { arguments ->
            def jobResultValue = "FAILURE"
            assert arguments['job_result'] ==~ /.*${jobResultValue}.*/
        }
        1 * getPipelineMock("sendSplunkConsoleLog")(*_)
        2 * notification.notifyWithAllMethods(*_)
        2 * ConsoleLogger.logJobInfo(*_)
        def exception = thrown(ErrorStepException)
        assert exception.message.contains("Invalid Deployment Type")
    }

    def """When an invalid Testing Type is provided an error of 'Invalid Test Type' is received"""() {
        when:
        explicitlyMockPipelineVariable("scm")
        cignaBuildFlow(
            {
                gitlabConnectionName = "stuff"
                mattermostWebhook = "stuff"
                emailRecipients = "DevOpsSystems@CIGNA.COM"
                cloudName = 'notNull'
                phases = [
                    [
                        branchPattern  : 'master',
                        deploymentType : 'valid',
                        sdlcEnvironment: 'test',
                        openshift      : [
                            credentialsId: 'devops-system-kubernetes-plugin-sa-token',
                            project      : 'devops-system-kubernetes-plugin'
                        ],
                        helm           : [
                            version: '2.14.1',
                            command: 'stuff'
                        ],
                        testing        : [
                            [
                                testType  : 'clearlyWrongTestType',
                                rally     : [
                                    credentialsId: "rallyApiCreds",
                                    userStory    : "US963016",
                                    user         : "test",
                                ],
                                runner    : [
                                    type   : "mvn-runner",
                                    command: "mvn clean test",
                                    url    : "https://git.sys.cigna.com/quality-engineering/conduit-sample-test.git"
                                ],
                                featureDir: "src/test/resources/features/forms",
                                resultDir : "target/cucumber-report",
                            ]
                        ]
                    ]
                ]
            },
            notification
        )
        then:
        currentBuild.result == "FAILURE"
        1 * getPipelineMock("splunkins.send")(*_) >> { arguments ->
            def jobResultValue = "FAILURE"
            assert arguments['job_result'] ==~ /.*${jobResultValue}.*/
        }
        1 * getPipelineMock("sendSplunkConsoleLog")(*_)
        2 * notification.notifyWithAllMethods(*_)
        2 * ConsoleLogger.logJobInfo(*_)
        def exception = thrown(ErrorStepException)
        exception.message == "Invalid Testing Type. Error: " +
            "com.cigna.testing.ClearlyWrongTestTypeTest"
    }

    def """Classes that aren't subclasses of Testing cannot be instantiated"""() {
        when:
        explicitlyMockPipelineVariable("scm")
        cignaBuildFlow(
            {
                gitlabConnectionName = "stuff"
                mattermostWebhook = "stuff"
                emailRecipients = "DevOpsSystems@CIGNA.COM"
                cloudName = 'notNull'
                phases = [
                    [
                        branchPattern  : 'master',
                        deploymentType : 'valid',
                        sdlcEnvironment: 'test',
                        openshift      : [
                            credentialsId: 'devops-system-kubernetes-plugin-sa-token',
                            project      : 'devops-system-kubernetes-plugin'
                        ],
                        helm           : [
                            version: '2.14.1',
                            command: 'stuff'
                        ],
                        testing        : [
                            [
                                testType  : 'invalid',
                                rally     : [
                                    credentialsId: "rallyApiCreds",
                                    userStory    : "US963016",
                                    user         : "test",
                                ],
                                runner    : [
                                    type   : "mvn-runner",
                                    command: "mvn clean test",
                                    url    : "https://git.sys.cigna.com/quality-engineering/conduit-sample-test.git"
                                ],
                                featureDir: "src/test/resources/features/forms",
                                resultDir : "target/cucumber-report",
                            ]
                        ]
                    ]
                ]
            },
            notification
        )

        then:
        currentBuild.result == "FAILURE"
        1 * getPipelineMock("splunkins.send")(*_) >> { arguments ->
            def jobResultValue = "FAILURE"
            assert arguments['job_result'] ==~ /.*${jobResultValue}.*/
        }
        1 * getPipelineMock("sendSplunkConsoleLog")(*_)
        2 * notification.notifyWithAllMethods(*_)
        def exception = thrown(ErrorStepException)
        assert exception.message.contains("Invalid Testing Type")
    }

    def """Build Phase with Valid Testing Test Types load correctly"""() {
        when:
        explicitlyMockPipelineVariable("scm")
        explicitlyMockPipelineVariable("MAVEN_SETTINGS")
        explicitlyMockPipelineVariable("ARTIFACTORY_USER")
        explicitlyMockPipelineVariable("ARTIFACTORY_APIKEY")
        cignaBuildFlow(
            {
                gitlabConnectionName = 'connection'
                mattermostWebhook = "stuff"
                emailRecipients = "DevOpsSystems@CIGNA.COM"
                cloudName = 'notNull'
                phases = [
                    [
                        branchPattern: '.*',
                        buildType    : 'maven',
                        sonarQube    : [
                            credentialsId: 'creds',
                        ],
                        artifactory  : [
                            applicationName: 'name',
                            credentialsId  : 'creds'
                        ],
                        checkmarx    : [
                            credentialsId: 'creds',
                            settings     : [
                                CX_PROJECT_TEAM_NAME: 'settings'
                            ]
                        ],
                    ],
                    [
                        testType      : 'Rego',
                        branchPattern : '.*',
                        bundlePath    : '.',
                        rootPath      : 'tests',
                        additionalArgs: args
                    ]
                ]
            },
            notification
        )

        then:
        currentBuild.result == 'SUCCESS'
        1 * getPipelineMock('splunkins.send')(*_) >> { arguments ->
            def jobResultValue = 'SUCCESS'
            assert arguments['job_result'] ==~ /.*${jobResultValue}.*/
        }
        1 * getPipelineMock('sendSplunkConsoleLog')(*_)
        2 * notification.notifyWithAllMethods(*_)
        1 * getPipelineMock('sh')({
            it != null && it instanceof Map &&
                it.containsKey('returnStatus') && it.containsKey('script') &&
                it.returnStatus == true && it.script == cmdLine
        }) >> 0
        where:
        args << [
            ['-v', '-f pretty'],
            ['-v', '-f pretty', '--bench']
        ]
        cmdLine << [
            'opa test -b . -v -f pretty tests',
            'opa test -b . -v -f pretty --bench tests'
        ]

    }

    def """When an invalid Remote Type is provided an error of 'Invalid Remote Type' is received"""() {
        when:
        cignaBuildFlow(
            {
                gitlabConnectionName = "stuff"
                mattermostWebhook = "stuff"
                emailRecipients = "DevOpsSystems@CIGNA.COM"
                cloudName = 'notNull'
                phases = [
                    [
                        branchPattern: 'master',
                        remoteType   : 'clearlyWrongRemoteType',
                        jobPath      : 'test'
                    ]
                ]
            },
            notification
        )
        then:
        currentBuild.result == "FAILURE"
        1 * getPipelineMock("splunkins.send")(*_) >> { arguments ->
            def jobResultValue = "FAILURE"
            assert arguments['job_result'] ==~ /.*${jobResultValue}.*/
        }
        1 * getPipelineMock("sendSplunkConsoleLog")(*_)
        2 * notification.notifyWithAllMethods(*_)
        2 * ConsoleLogger.logJobInfo(*_)
        def exception = thrown(ErrorStepException)
        exception.message == "Invalid Remote Type. Error: " +
            "com.cigna.remote.ClearlyWrongRemoteTypeRemote"
    }

    def """Classes that aren't subclasses of Remote cannot be instantiated"""() {
        when:
        cignaBuildFlow(
            {
                gitlabConnectionName = "stuff"
                mattermostWebhook = "stuff"
                emailRecipients = "DevOpsSystems@CIGNA.COM"
                cloudName = 'notNull'
                phases = [
                    [
                        branchPattern: 'master',
                        remoteType   : 'invalid',
                        jobPath      : 'test',
                    ]
                ]
            },
            notification
        )

        then:
        currentBuild.result == "FAILURE"
        1 * getPipelineMock("splunkins.send")(*_) >> { arguments ->
            def jobResultValue = "FAILURE"
            assert arguments['job_result'] ==~ /.*${jobResultValue}.*/
        }
        1 * getPipelineMock("sendSplunkConsoleLog")(*_)
        2 * notification.notifyWithAllMethods(*_)
        def exception = thrown(ErrorStepException)
        assert exception.message.contains("Invalid Remote Type")
    }


    def """When extraCredentials or extraConfigs is used, the appropriate steps are called"""() {
        given:
        explicitlyMockPipelineVariable("openshiftSecret")

        when:
        def buildPhase = { String name ->
            [
                extraCredentials: [
                    string(
                        credentialsId: 'test-secret',
                        variable: "${name}_SECRET"
                    )
                ],
                extraConfigs    : [
                    configFile(
                        fileId: 'test-file',
                        variable: "${name}_FILE_PATH"
                    )
                ],
                branchPattern   : 'master',
                buildType       : "valid",
                sonarQube       : [
                    credentialsId: "stuff"
                ],
                artifactory     : [
                    applicationName: "stuff",
                    credentialsId  : "stuff"
                ],
                checkmarx       : [
                    credentialsId: "stuff",
                    settings     : [
                        CX_PROJECT_TEAM_NAME: "stuff"
                    ]
                ]
            ]
        }
        cignaBuildFlow(
            {
                gitlabConnectionName = "stuff"
                mattermostWebhook = "stuff"
                emailRecipients = "DevOpsSystems@CIGNA.COM"
                cloudName = 'notNull'
                featureFlags = [nonProdComplianceChecks: true]
                extraCredentials = [string(credentialsId: 'top-level',
                    variable: 'TOP_LEVEL_SECRET')]
                phases = [
                    buildPhase('BUILD1'),
                    buildPhase('BUILD2'),
                    [deploymentType        : 'phases',
                     branchPattern         : '.*',
                     sdlcEnvironment       : 'non-prod',
                     isProductionDeployment: false,
                     extraCredentials      : [string(credentialsId: 'deployment',
                         variable: 'DEPLOYMENT_SECRET')],
                     phases                : [
                         [deploymentType        : 'valid',
                          branchPattern         : '.*',
                          sdlcEnvironment       : 'non-prod',
                          isProductionDeployment: false,
                          extraCredentials      : [string(credentialsId: 'valid',
                              variable: 'VALID_SECRET')]
                         ],
                         [moduleType            : 'docker',
                          branchPattern         : '.*',
                          sdlcEnvironment       : 'non-prod',
                          isProductionDeployment: false,
                          moduleName            : 'cnp-publish-image',
                          subCommand            : 'publishimage',
                          serviceAccount        : 'kaniko',
                          extraCredentials      : [string(credentialsId: 'publish',
                              variable: 'PUBLISH_SECRET')],
                          args                  : [sourceRepository            : 'registry-dev.cigna.com',
                                                   sourceImage                 : 'test-source-image',
                                                   sourceTag                   : 'test-source-tag',
                                                   destinationRegistry         : 'registry-dev.cigna.com',
                                                   destinationTag              : 'test-target-tag',
                                                   publishOnNonDeployableBranch: true]]
                     ]]
                ]
            },
            notification
        )

        then:
        // return the variable name for `string` and `configFile` calls so we can assert the params
        getPipelineMock("string.call")(*_) >> { it.variable }
        getPipelineMock("usernamePassword.call")(*_) >> { it.usernameVariable }
        getPipelineMock("configFile.call")(*_) >> { it.variable }
        // we don't care how many times the github token gets used
        _ * getPipelineMock("withCredentials")({ args -> args[0] == [['EPF_GITHUB_ACCESS_TOKEN']] })
        // there should be one call with the top-level secret and the build 1 secret, and one for build 2...
        1 * getPipelineMock("withCredentials")({ args -> args[0] == [['BUILD1_SECRET'], ['TOP_LEVEL_SECRET']] })
        1 * getPipelineMock("withCredentials")({ args -> args[0] == [['BUILD2_SECRET'], ['TOP_LEVEL_SECRET']] })
        // there should be one call for each phase that's part of the deployment with the aggregated creds
        1 * getPipelineMock("withCredentials")({ args -> args[0] == [['VALID_SECRET'], ['DEPLOYMENT_SECRET'], ['TOP_LEVEL_SECRET']] })
        // because the module has extra creds from the MCR, we're only checking that the list starts with the 'extraCredentials' creds
        1 * getPipelineMock("withCredentials")({ args -> args[0].take(3) == [['PUBLISH_SECRET'], ['DEPLOYMENT_SECRET'], ['TOP_LEVEL_SECRET']] })
        // ... and one call for config files for each of the builds
        1 * getPipelineMock("configFileProvider.call")({ args -> args[0] == [['BUILD1_FILE_PATH']] })
        1 * getPipelineMock("configFileProvider.call")({ args -> args[0] == [['BUILD2_FILE_PATH']] })
    }

    def """When a cloudServiceAccountName is specified in the config, it will propagate to the
        podTemplate call"""() {
        given:
        explicitlyMockPipelineVariable("sonarQubeToken")

        when:
        cignaBuildFlow(
            {
                gitlabConnectionName = "stuff"
                mattermostWebhook = "stuff"
                emailRecipients = "DevOpsSystems@CIGNA.COM"
                cloudName = 'notNull'
                phases = [
                    [
                        runInAWS              : true,
                        branchPattern         : 'master',
                        buildType             : 'plz',
                        isProductionDeployment: false,
                        sdlcEnvironment       : 'test',
                        aws                   : [
                            targetAccount          : 'test',
                            accountRoleName        : 'test',
                            cloudServiceAccountName: "test",
                        ],
                        checkmarx             : [credentialsId: 'test', settings: [CX_PROJECT_TEAM_NAME: 'test']],
                        sonarQube             : [credentialsId: 'test']
                    ]
                ]
            },
            notification
        )

        then:
        psc.podSelector.podTemplates['notNull'] ==~ /.*"serviceAccountName":"test".*/
    }

    def """When multiple cloudServiceAccountNames are specified in the config, a warning is printed to the console"""() {
        given:
        explicitlyMockPipelineVariable("sonarQubeToken")

        when:
        cignaBuildFlow(
            {
                gitlabConnectionName = "stuff"
                mattermostWebhook = "stuff"
                emailRecipients = "DevOpsSystems@CIGNA.COM"
                cloudName = 'notNull'
                phases = [
                    [
                        runInAWS              : true,
                        branchPattern         : 'master',
                        buildType             : 'plz',
                        isProductionDeployment: false,
                        sdlcEnvironment       : 'test',
                        aws                   : [
                            targetAccount          : 'test',
                            accountRoleName        : 'test',
                            cloudServiceAccountName: "test",
                        ],
                        checkmarx             : [credentialsId: 'test', settings: [CX_PROJECT_TEAM_NAME: 'test']],
                        sonarQube             : [credentialsId: 'test']
                    ],
                    [
                        runInAWS              : true,
                        branchPattern         : 'master',
                        buildType             : 'plz',
                        isProductionDeployment: false,
                        sdlcEnvironment       : 'test',
                        aws                   : [
                            targetAccount          : 'test1',
                            accountRoleName        : 'test1',
                            cloudServiceAccountName: "test1",
                        ],
                        checkmarx             : [credentialsId: 'test', settings: [CX_PROJECT_TEAM_NAME: 'test']],
                        sonarQube             : [credentialsId: 'test']
                    ]
                ]
            },
            notification
        )

        then:
        psc.podSelector.podTemplates['notNull'] ==~ /.*"serviceAccountName":"test1".*/
        1 * getPipelineMock('echo')('*** Cannot currently support multiple cloud serviceAccounts in a single pod template ([test, test1]) - test will be used')
    }


    def """When Freestyle Type is used the value of freestyleType is the stage and the
        script gets run"""() {
        when:
        cignaBuildFlow(
            {
                gitlabConnectionName = "stuff"
                mattermostWebhook = "stuff"
                emailRecipients = "DevOpsSystems@CIGNA.COM"
                cloudName = 'notNull'
                phases = [
                    [
                        freestyleType: 'Arbitrary Text Here',
                        branchPattern: '.*',
                        container    : [
                            image : 'cnp/test-image:latest',
                            cpu   : 1000,
                            memory: 1000
                        ],
                        script       : 'echo "hello"'
                    ]
                ]
            },
            notification
        )

        then:
        1 * getPipelineMock("stage")({ it ==~ /.*Arbitrary Text Here.*/ })
        1 * getPipelineMock("sh")('echo "hello"')
    }

    def """When phaseCache is given, the stash is used"""() {
        given:
        explicitlyMockPipelineVariable("sonarQubeToken")
        when:
        cignaBuildFlow(
            {
                phaseCache = wherePhaseCache
                cloudName = 'test'
                gitlabConnectionName = "stuff"
                mattermostWebhook = "stuff"
                emailRecipients = "DevOpsSystems@CIGNA.COM"
                phases = [
                    [
                        buildType    : "npm",
                        podGroup     : wherePodGroup,
                        branchPattern: '.*',
                        sonarQube    : [
                            credentialsId: "stuff"
                        ],
                        artifactory  : [
                            applicationName: "stuff",
                            credentialsId  : "stuff"
                        ],
                        checkmarx    : [
                            credentialsId: "stuff",
                            settings     : [
                                CX_PROJECT_TEAM_NAME: "stuff"
                            ]
                        ]
                    ],
                    [
                        buildType    : "npm",
                        branchPattern: '.*',
                        sonarQube    : [
                            credentialsId: "stuff"
                        ],
                        artifactory  : [
                            applicationName: "stuff",
                            credentialsId  : "stuff"
                        ],
                        checkmarx    : [
                            credentialsId: "stuff",
                            settings     : [
                                CX_PROJECT_TEAM_NAME: "stuff"
                            ]
                        ]
                    ]
                ]
            },
            notification
        )

        then:
        timesUnstashCalled * getPipelineMock('unstash')({
            it.name == STASH_NAME
        })
        timesStashCalled * getPipelineMock('stash')(_)

        where:
        wherePhaseCache << [false, true, false, true]
        wherePodGroup << ['npm', 'npm', null, null]
        timesUnstashCalled << [
            0,
            1, // once at start of second phase,
            0, // single pods don't need stashing
            0
        ]
        timesStashCalled << [
            0,
            2, // once at end of each phase
            0, // single pods don't need stashing
            0
        ]
    }

    def '''When resource scaling is disabled and module phase overrides container cpu and memory, it is reflected in the resulting pod template'''() {
        when:
        cignaBuildFlow {
            githubConnectionName = 'github'
            commitStatusName = 'Enterprise Pipeline Framework'

            cloudName = 'test-cloud'
            logHistoryCount = '5'
            webexTeamsRoom = 'bd7cdcb0-27a4-11ed-af92-f5afe966fcf4'
            resourceScaleFactor = resourceScaleFactorValue
            phases = [
                [
                    moduleType            : 'maven',
                    branchPattern         : '.*',
                    sdlcEnvironment       : 'dev',
                    isProductionDeployment: false,
                    module                : [
                        image       : baseImageName,
                        contractName: 'BUILD',
                        moduleName  : 'cnp-build-publish-maven-create',
                        subCommand  : 'build',
                        version     : baseVersion,
                        commandName : "cnp-build-publish-maven-create.sh",
                    ],
                    container             : [
                        cpu   : 3333,
                        memory: 8888
                    ],
                    args                  : [
                        credentials: [
                            [id: 'cred']
                        ],
                        publish    : false
                    ]
                ],
                [
                    moduleType            : 'maven',
                    branchPattern         : '.*',
                    sdlcEnvironment       : 'dev',
                    isProductionDeployment: false,
                    module                : [
                        image       : baseImageName,
                        contractName: 'BUILD',
                        moduleName  : 'cnp-build-publish-maven-create',
                        subCommand  : 'build',
                        version     : baseVersion,
                        commandName : "cnp-build-publish-maven-create.sh",
                    ],
                    container             : [
                        cpu   : 4444,
                        memory: 6666,
                    ],
                    args                  : [
                        credentials: [
                            [id: 'cred']
                        ],
                        publish    : false
                    ]
                ],
                [
                    moduleType            : 'test',
                    moduleName            : 'cnp-jenkins-job-build',
                    branchPattern         : '.*',
                    sdlcEnvironment       : 'dev',
                    isProductionDeployment: false,
                    args                  : [
                        jobName    : 'orchestrators-folders/job/hs-pipeline/job/Functional%20Test%20Apps/job/zz-test-wright-jobs/job/test-wright-dev',
                        jenkinsURL : 'https://orchestrator18.orchestrator-v2.sys.cigna.com/',
                        buildParams: 'test-categories=epf%2Fci',
                        credentials: [[id: 'cred']]
                    ]
                ],
                [
                    moduleType            : 'test',
                    moduleName            : 'cnp-jenkins-job-build',
                    branchPattern         : '.*',
                    sdlcEnvironment       : 'dev',
                    isProductionDeployment: false,
                    args                  : [
                        jobName    : 'orchestrators-folders/job/hs-pipeline/job/Functional%20Test%20Apps/job/zz-test-wright-jobs/job/test-wright-dev',
                        jenkinsURL : 'https://orchestrator18.orchestrator-v2.sys.cigna.com/',
                        buildParams: 'test-categories=epf%2Fci',
                        credentials: [[id: 'cred']]
                    ]
                ]
            ]
        }

        then:
        assert psc.podSelector.baseCloudName == 'test-cloud'
        assert psc.podSelector.podTemplates.size() == 1
        assert psc.podSelector.podTemplates['test-cloud'].findAll(/${expectedImageName}/).size() == 1
        assert psc.podSelector.podTemplates['test-cloud'].findAll(/cnp-docker-corev/).size() == 1
        assert psc.podSelector.podTemplates['test-cloud'].findAll(/"cpu":"${expectedResourceCPU}m"/).size() == expectedCount
        assert psc.podSelector.podTemplates['test-cloud'].findAll(/"cpu":"4444m"/).size() == expectedCount
        assert psc.podSelector.podTemplates['test-cloud'].findAll(/"memory":"${expectedResourceMemory}Mi"/).size() == expectedCount
        assert psc.podSelector.podTemplates['test-cloud'].findAll(/"memory":"8888Mi"/).size() == expectedCount
        where:
        resourceScaleFactorValue << [2, 1, 1, 1]
        expectedResourceCPU << [2222, 4444, 4444, 4444]
        expectedResourceMemory << [4444, 8888, 8888, 8888]
        expectedCount << [1, 2, 2, 2]
        baseImageName << ['cnp/cnp-docker-maven-java8', 'docker.io/ubuntu', 'registry.cigna.com/cnp/cnp-docker-maven-java11', 'enterprise-devops/epf-kaniko']
        baseVersion << ['latest', '2024', '1.2.3', 'prod-v1.0.2.43']
        expectedImageName << ['cnp-docker-maven-java8vlatest', 'ubuntuv2024', 'cnp-docker-maven-java11v123', 'epf-kanikovprod-v10243',]
    }


    def '''Overriding the default linting container for a nested lint type results in the correct container and name being set'''() {
        given:
        explicitlyMockPipelineVariable('bearerToken')
        explicitlyMockPipelineVariable('AWS_FED_PASSWORD')
        explicitlyMockPipelineVariable('AWS_FED_USERNAME')
        explicitlyMockPipelineVariable('QUAY_TOKEN')
        when:

        String containerImage = 'enterprise-devops/aws-d-megatainer'
        String containerVersion = "1.0.12"

// Application Version
        String projectVersion = "0.0.3"
        String buildNum = 'test-num'

// Set Defaults
        String envName = 'FIXME'
        String credentialsId = 'FIXME'
        boolean isProductionDeployment = true
        String awsAccountNumber = ''
        boolean kanikoExpire = true
        String dockerRegistry = 'FIXME'
        String quayCredsId = 'FIXME'
        String branchName = 'feature/api-test'
        String containerTag = 'FIXME'
        String containerRepository = ''

        String GIT_COMMIT_ID = UUID.randomUUID().toString()

        if (branchName == 'master') {
            envName = 'prod'
            credentialsId = 'SAMLSVPMOSJenkinsPRDADM'
            isProductionDeployment = false
            awsAccountNumber = '654874575355'
            kanikoExpire = false
            dockerRegistry = "registry.cigna.com"
            quayCredsId = "dae-mos-api"
            containerTag = "${projectVersion}-${buildNum}"
            containerRepository = "dae-mos-api"
        } else if (branchName == 'test') {
            envName = 'test'
            credentialsId = 'SAMLSVPMOSJenkinsTSTADM'
            isProductionDeployment = false
            awsAccountNumber = '321326846349'
            kanikoExpire = false
            dockerRegistry = "registry-dev.cigna.com"
            quayCredsId = "dae-mos-api-dev"
            containerTag = "${projectVersion}.RC-${buildNum}"
            containerRepository = "dae-mos-api"
        } else if (branchName.contains('feature/api-')) {
            String WORKSPACE = branchName.substring(12)
            envName = 'dev'
            credentialsId = 'SAMLSVTMOSJenkinsADMIN'
            isProductionDeployment = false
            awsAccountNumber = '435400131687'
            kanikoExpire = true
            dockerRegistry = "registry-dev.cigna.com"
            quayCredsId = "dae-mos-api-dev"
            containerRepository = "dae-mos-api-${WORKSPACE}"
            containerTag = "${projectVersion}.${WORKSPACE}-${GIT_COMMIT_ID}"
        } else {
            envName = 'dev'
            credentialsId = 'SAMLSVTMOSJenkinsADMIN'
            isProductionDeployment = false
            awsAccountNumber = '435400131687'
            kanikoExpire = false
            dockerRegistry = "registry-dev.cigna.com"
            quayCredsId = "dae-mos-api-dev"
            containerRepository = "dae-mos-api"
            containerTag = "${projectVersion}.${branchName}-${GIT_COMMIT_ID}"
        }

        cignaBuildFlow {
            githubConnectionName = 'cigna-github'
            logLevel = 'SEVERE'
            cloudName = 'cigna-us-da-mos-api-openshift-devops1'
            mattermostWebhook = 'https://mm.sys.cigna.com/hooks/qx599ymesbn97eszmmqe8p7oow'
            additionalProperties = [
                disableConcurrentBuilds()
            ]

            phases = [
                [
                    lintingTypes   : [
                        'plz': [
                            verbosityFlag: '-vvv',
                            container    : [
                                image  : "${containerImage}",
                                version: "${containerVersion}",
                                cpu    : 500,
                                memory : 400,
                            ],
                        ]
                    ],
                    branchPattern  : '.*',
                    sdlcEnvironment: envName,

                ],
                /*
                        We wanted to execute below phase on all branches except master. Run scans and unit tests before deploying the application to PROD
                    */
                [
                    buildType      : 'plz',
                    verbosityFlag  : '-vvv',
                    branchPattern  : '(?!master$).*',
                    changePattern  : 'api/django/.*', // Run this phase only when the files inside the django path are updated
                    sdlcEnvironment: envName,
                    awsFed         : [
                        credentialsId: credentialsId,
                        callPlzFed   : false
                    ],
                    container      : [
                        image  : "enterprise-devops/jenkins-generic-agent",
                        version: "0.0.36"
                    ],
                    sonarQube      : [
                        credentialsId     : 'sonarqube-service-id',
                        containerMaxMemory: 500,
                        mainBranch        : 'test',
                        scannerProperties : [
                            'sonar.projectKey=dae.mos-aws-api',
                            'sonar.projectName=dae.mos-aws-api',
                            'sonar.analysis.ciid=CI-dae.mos-aws-api',
                            'sonar.exclusions=**/*test*.py',
                            'sonar.sources=.',
                            'sonar.inclusions=api/**/*.py',
                            'sonar.python.coverage.reportPaths=/home/jenkins/agent/workspace/api/django/dae-mos/dae_mos_api_coverage.xml'
                        ],
                    ],
                    checkmarx      : [
                        credentialsId: 'checkmarx-service-creds',
                        settings     : [
                            CX_PROJECT_TEAM_NAME  : 'mgmtopsys',
                            CX_SCAN_COMMENT       : "Pipeline:${buildNum} - Branch:${branchName}",
                            CX_EXCLUDE_FOLDER_LIST: 'plz-out, module, commands, test, tests, third_party',
                            CX_SCAN_TYPE          : 'AsyncScan',
                        ],
                    ],
                ],
                // Feature tag option:  tag: "${projectVersion}.${branchName}.${GIT_COMMIT_ID}",
                [
                    branchPattern     : 'feature/api-.*|develop|test|master',
                    packagingType     : 'kaniko',
                    container         : [
                        memory: 1500
                    ],

                    dockerRegistry    : "${dockerRegistry}",
                    image             : [
                        org : 'da-mos-cloud',
                        name: "${containerRepository}",
                        tags: [
                            [
                                tag   : "${containerTag}",
                                expire: kanikoExpire
                            ],
                        ]
                    ],
                    quay              : [
                        credentialsId        : "${quayCredsId}",
                        // may be able to remove ^^ for below; testing
                        // ref: https://github.sys.cigna.com/cigna/Conduit/blob/master/docs/using-conduit/phases/packaging-type.md
                        // How To gen token: https://confluence.sys.cigna.com/display/k8s/Create+Quay+CLI+Token (perms:  Admin Repo + R/W to any accessible repo)
                        apiTokenCredentialsId: "${quayCredsId}"
                    ],
                    conftestValidation: false
                ],
                [
                    deploymentType        : 'plz',
                    alias                 : 'deploy_workspace',
                    extraArgs             : 'ecs-task-definitions dev',
                    verbosityFlag         : '-vvvv',

                    branchPattern         : 'feature/api-.*',
                    sdlcEnvironment       : 'dev',

                    awsFed                : [
                        credentialsId: credentialsId,
                        callPlzFed   : true
                    ],
                    withEnv               : [
                        "ENV=${envName}",
                        "TF_VAR_container_tag=${containerTag}"
                    ],
                    container             : [
                        image  : "${containerImage}",
                        version: "${containerVersion}",
                        cpu    : 1500,
                        memory : 2000,
                    ],

                    isProductionDeployment: false
                ],
                /* Branch pattern is opposite of how we defined in other stages.
                       We wanted to execute below phase on all branches except develop, test and master
                       Using below branch pattern, we don't have to create workspace for making minor change to infrastructure and don't need to follow branching pattern for plan
                    */
                [
                    freestyleType   : 'Terraform Plan',
                    branchPattern   : '^(?!develop$|test$|master$).*',
                    extraCredentials: [
                        usernamePassword(
                            credentialsId: credentialsId,
                            passwordVariable: 'SAML2AWS_PASSWORD',
                            usernameVariable: 'SAML2AWS_USERNAME'
                        )
                    ],
                    sdlcEnvironment : 'dev',
                    container       : [
                        image  : "${containerImage}",
                        version: "${containerVersion}",
                        cpu    : 1000,
                        memory : 2000,
                    ],
                    script          : """
                    sh /usr/local/bin/adhoc-perms

                    export ENV=dev
                    export TF_VAR_container_tag=${containerTag}

                    plz fed dev
                    plz plan_all dev --show_all_output -vvv
            """ //required
                ],
                [
                    alias                 : 'deploy',
                    branchPattern         : 'develop|test|master',
                    deploymentType        : 'plz',

                    awsFed                : [
                        credentialsId: credentialsId,
                        callPlzFed   : true
                    ],
                    container             : [
                        image  : "${containerImage}",
                        version: "${containerVersion}",
                        cpu    : 1500,
                        memory : 2000,
                    ],
                    extraArgs             : "${envName}",
                    isProductionDeployment: isProductionDeployment,
                    sdlcEnvironment       : "${envName}",
                    verbosityFlag         : '-vvv',
                    withEnv               : [
                        "ENV=${envName}",
                        "TF_VAR_container_tag=${containerTag}"
                    ],
                ],
                [
                    freestyleType  : 'Health Check API Endpoint',
                    branchPattern  : 'feature/api-.*',
                    sdlcEnvironment: 'dev',
                    container      : [
                        image  : "${containerImage}",
                        version: "${containerVersion}",
                        memory : 2000,
                    ],
                    script         : '''
                set +x
                WORKSPACE_NAME=$(cut -d "-" -f2- <<< "${BRANCH_NAME}")
                DNS_RECORD_WRKSP=https://mos-api-${WORKSPACE_NAME//_/-}.da-mos-dev.aws.cignacloud.com
                COUNTER=1
                API_ENDPOINT_HEALTH_STATUS=$(curl --insecure -XGET -sw "%{http_code}" -o /dev/null "${DNS_RECORD_WRKSP}/mos-api/v1/health")
                while [[ ${API_ENDPOINT_HEALTH_STATUS} == "503" && ${COUNTER} -le 10 ]]
                do
                    echo "The API endpoint service is unavailable. Wait for 60 seconds before checking again..."
                    sleep 60
                    API_ENDPOINT_HEALTH_STATUS=$(curl --insecure -XGET -sw "%{http_code}" -o /dev/null "${DNS_RECORD_WRKSP}/mos-api/v1/health")
                    let "COUNTER+=1"
                done
                echo "Check if the api health status code is 200"
                if [ ${API_ENDPOINT_HEALTH_STATUS} == "200" ]
                then
                    echo "Health check of API endpoint is successfull"
                else
                    let "COUNTER-=1"
                    echo "Health check for workspace API endpoint: ${DNS_RECORD_WRKSP} failed after checking ${COUNTER} times consecutively for ${COUNTER} minute period"
                    exit 1
                fi
            ''' //required
                ]
            ]
        }
        then:
        psc.podSelector.podTemplates.size() == 1
        assert !psc.podSelector.podTemplates['cigna-us-da-mos-api-openshift-devops1'].contains('aws-d-megatainerv1014-2')
        assert psc.podSelector.podTemplates['cigna-us-da-mos-api-openshift-devops1'].contains('aws-d-megatainerv1012')
    }

    def '''Using a phase containing multiple additional containers sets the correct resource quotas for each container'''() {
        given:
        explicitlyMockPipelineVariable('bearerToken')
        when:

        cignaBuildFlow {
            featureFlags = [
                nonProdComplianceChecks: false,
                debug                  : true,
                verbose                : true,
                showStackTraces        : true,
            ]
            gitlabConnectionName = 'gitlab_server'
            commitStatusName = 'backstage build pipeline'
            mattermostWebhook = 'https://mm.sys.cigna.com/hooks/wex64p9mp3ghtbygro5sjien4c'
            emailRecipients = 'ankush.sharma@evernorth.com'
            cloudName = 'selfservicetooling-openshift-devops1'
            phases = [
                [
                    deploymentType        : 'ArgoCD',
                    branchPattern         : '.*',
                    sdlcEnvironment       : 'Dev',
                    isProductionDeployment: false,
                    cloudName             : 'selfservicetooling-openshift-devops1',
                    containers            : [
                        [
                            name  : 'ansiblevlatest',
                            cpu   : 3003,
                            memory: 4004,
                        ]
                    ],
                    argoCd                : [
                        argoCdServer        : 'argocd-devops.apps.gp-4-nonprod.openshift.cignacloud.com',
                        namespace           : 'backstage-test',
                        appName             : 'cigna-idp-test',
                        credentialsId       : 'backstage-test-jwt-service-id',
                        chartPath           : 'chart',
                        revisionHistoryLimit: 3,
                        prune               : true,
                        timeout             : 9000,
                        valuesFiles         : [
                            "values-backstage-dev.yaml",
                        ],
                        setValues           : [
                            'imageTag=1.0.12'
                        ]
                    ]
                ]
            ]
        }

        then:
        psc.podSelector.podTemplates.size() == 1
        assert psc.podSelector.podTemplates['selfservicetooling-openshift-devops1'].findAll(/ansiblev/).size() == 1
        assert psc.podSelector.podTemplates['selfservicetooling-openshift-devops1'].findAll(/"cpu":"3003m"/).size() == 1
        assert psc.podSelector.podTemplates['selfservicetooling-openshift-devops1'].findAll(/"memory":"4004Mi"/).size() == 1
    }

    def '''Full featured pcf conversion pipeline can be parsed using single pod logic'''() {
        given:
        explicitlyMockPipelineVariable('bearerToken')
        List phasesToRun = [
            [
                moduleType            : "maven",
                moduleName            : "cnp-build-publish-maven-create",
                subCommand            : "build",
                branchPattern         : '.*',
                releaseBranchPattern  : '^release.*',
                isProductionDeployment: false,
                args                  : [
                    publish: true
                ],
                container             : [
                    cpu      : 1500,
                    memory   : 3000,
                    ephemeral: 1000
                ]
            ],
            [
                moduleType            : 'quality',
                moduleName            : 'cnp-quality-check-sonarqube',
                subCommand            : 'scan',
                branchPattern         : '.*',
                sdlcEnvironment       : 'dev',
                isProductionDeployment: false,
                args                  : [
                    projectName    : 'snow-spring-boot',
                    applicationType: 'maven'
                ],
                container             : [
                    cpu   : 1500,
                    memory: 3000
                ]
            ],
            [
                moduleType            : 'quality',
                moduleName            : 'cnp-quality-profile-checkmarx',
                subCommand            : 'scan',
                branchPattern         : '.*',
                sdlcEnvironment       : 'dev',
                isProductionDeployment: false,
                args                  : [
                    cxProjectName             : 'config-snow-proxy',
                    cxPreset                  : '100009',
                    cxGroupId                 : '8d6db7ad-a8a9-4316-acd8-0e5de7e7e04b',
                    cxVulnerabilityThreshValue: 'RED',
                    cxExcludeFolders          : 'test,target,dist,lib,.*',
                    cxIncremental             : true
                ]
            ],
            [
                moduleType            : 'pcf',
                moduleName            : 'cnp-deploy-pcf',
                subCommand            : 'deploy',
                branchPattern         : '.*',
                releaseBranchPattern  : '.*',
                sdlcEnvironment       : 'dev',
                isProductionDeployment: false,
                args                  : [
                    credentials : [
                        [id: 'CH3PCF04 - Jenkins Space Developer', env: 'dev'],
                    ],
                    appName     : 'snow-1-dev-candidate',
                    foundation  : 'ch3pcf04',
                    organization: 'CoreEng',
                    space       : 'development',
                    env         : 'dev',
                    artifact    : 'lookup:publishUrl',
                    autoscale   : false,
                ]
            ],
            [
                parallelType   : 'jenkins',
                sdlcEnvironment: 'DEPLOYDEV',
                branchPattern  : '.*',
                phases         : [
                    DEPLOYDEV: [
                        moduleType            : 'test',
                        moduleName            : 'cnp-jenkins-job-build',
                        subCommand            : 'build',
                        branchPattern         : '.*',
                        sdlcEnvironment       : '',
                        isProductionDeployment: false,
                        args                  : [
                            jobName    : 'orchestrators-folders/job/hs-config-snow-proxy/job/Testing/job/test-pipeline/job/test-ov2',
                            buildParams: "env=dev&target=DEPLOY"
                        ],
                    ]
                ],
            ],
            [
                moduleType            : 'pcf',
                moduleName            : 'cnp-deploy-pcf',
                subCommand            : 'cutover',
                branchPattern         : '.*',
                sdlcEnvironment       : 'dev',
                isProductionDeployment: false,
                args                  : [
                    credentials : [
                        [id: 'CH3PCF04 - Jenkins Space Developer', env: 'dev'],
                    ],
                    appName     : 'snow-1-dev-candidate',
                    foundation  : 'ch3pcf04',
                    organization: 'CoreEng',
                    space       : 'development',
                    env         : 'dev',
                ]
            ],
            [
                parallelType   : 'jenkins',
                sdlcEnvironment: 'ROUTEDEV',
                branchPattern  : '.*',
                phases         : [
                    ROUTEDEV: [
                        moduleType            : 'test',
                        moduleName            : 'cnp-jenkins-job-build',
                        subCommand            : 'build',
                        branchPattern         : '.*',
                        sdlcEnvironment       : '',
                        isProductionDeployment: false,
                        args                  : [
                            jobName    : 'orchestrators-folders/job/hs-config-snow-proxy/job/Testing/job/test-pipeline/job/test-ov2',
                            buildParams: "env=dev&target=ROUTE"
                        ],
                    ]
                ],
            ],
            [
                moduleType            : 'openshift',
                moduleName            : 'cnp-deploy-argorollouts',
                subCommand            : 'deploy',
                branchPattern         : 'NotYet.*',
                sdlcEnvironment       : 'dev',
                isProductionDeployment: false,
                args                  : [
                    appName     : 'snow-1-dev',
                    namespace   : '',
                    platform    : '',
                    imageName   : 'lookup:imageName',
                    imageTag    : 'lookup:imageTag',
                    useArgoCD   : 'true',
                    argoCDServer: 'snow-1-dev',
                    cluster     : '',
                    configDir   : 'envs/dev',
                    env         : 'dev'
                ]
            ],
            [
                moduleType            : 'openshift',
                moduleName            : 'cnp-deploy-argorollouts',
                subCommand            : 'promote',
                branchPattern         : 'NotYet.*',
                sdlcEnvironment       : 'dev',
                isProductionDeployment: false,
                args                  : [
                    appName  : 'snow-1-dev',
                    configDir: 'envs/dev',
                    namespace: '',
                    cluster  : '',
                    platform : '',
                    env      : 'dev'
                ]
            ],
            [
                moduleType            : 'pcf',
                moduleName            : 'cnp-deploy-pcf',
                subCommand            : 'deploy',
                branchPattern         : '.*',
                releaseBranchPattern  : '.*',
                sdlcEnvironment       : 'qa',
                isProductionDeployment: false,
                args                  : [
                    credentials : [
                        [id: 'CH3PCF01 - Jenkins Space Developer', env: 'qa'],
                    ],
                    appName     : 'snow-1-qa-candidate',
                    foundation  : 'ch3pcf01',
                    organization: 'CoreEng',
                    space       : 'QA',
                    env         : 'qa',
                    artifact    : 'lookup:publishUrl',
                    autoscale   : false,
                ]
            ],
            [
                parallelType   : 'jenkins',
                sdlcEnvironment: 'DEPLOYQA',
                branchPattern  : '.*',
                phases         : [
                    DEPLOYQA: [
                        moduleType            : 'test',
                        moduleName            : 'cnp-jenkins-job-build',
                        subCommand            : 'build',
                        branchPattern         : '.*',
                        sdlcEnvironment       : '',
                        isProductionDeployment: false,
                        args                  : [
                            jobName    : 'orchestrators-folders/job/hs-config-snow-proxy/job/Testing/job/test-pipeline/job/test-ov2',
                            buildParams: "env=qa&target=DEPLOY"
                        ],
                    ]
                ],
            ],
            [
                moduleType            : 'pcf',
                moduleName            : 'cnp-deploy-pcf',
                subCommand            : 'cutover',
                branchPattern         : '.*',
                sdlcEnvironment       : 'qa',
                isProductionDeployment: false,
                args                  : [
                    credentials : [
                        [id: 'CH3PCF01 - Jenkins Space Developer', env: 'qa'],
                    ],
                    appName     : 'snow-1-qa-candidate',
                    foundation  : 'ch3pcf01',
                    organization: 'CoreEng',
                    space       : 'QA',
                    env         : 'qa',
                ]
            ],
            [
                parallelType   : 'jenkins',
                sdlcEnvironment: 'ROUTEQA',
                branchPattern  : '.*',
                phases         : [
                    ROUTEQA: [
                        moduleType            : 'test',
                        moduleName            : 'cnp-jenkins-job-build',
                        subCommand            : 'build',
                        branchPattern         : '.*',
                        sdlcEnvironment       : '',
                        isProductionDeployment: false,
                        args                  : [
                            jobName    : 'orchestrators-folders/job/hs-config-snow-proxy/job/Testing/job/test-pipeline/job/test-ov2',
                            buildParams: "env=qa&target=ROUTE"
                        ],
                    ]
                ],
            ],
            [
                moduleType            : 'openshift',
                moduleName            : 'cnp-deploy-argorollouts',
                subCommand            : 'deploy',
                branchPattern         : 'NotYet.*',
                sdlcEnvironment       : 'qa',
                isProductionDeployment: false,
                args                  : [
                    appName     : 'snow-1-qa',
                    namespace   : '',
                    platform    : '',
                    imageName   : 'lookup:imageName',
                    imageTag    : 'lookup:imageTag',
                    useArgoCD   : 'true',
                    argoCDServer: 'snow-1-qa',
                    cluster     : '',
                    configDir   : 'envs/qa',
                    env         : 'qa'
                ]
            ],
            [
                moduleType            : 'openshift',
                moduleName            : 'cnp-deploy-argorollouts',
                subCommand            : 'promote',
                branchPattern         : 'NotYet.*',
                sdlcEnvironment       : 'qa',
                isProductionDeployment: false,
                args                  : [
                    appName  : 'snow-1-qa',
                    configDir: 'envs/qa',
                    namespace: '',
                    cluster  : '',
                    platform : '',
                    env      : 'qa'
                ]
            ],
            [
                moduleType            : 'pcf',
                moduleName            : 'cnp-deploy-pcf',
                subCommand            : 'deploy',
                branchPattern         : '.*',
                releaseBranchPattern  : '.*',
                sdlcEnvironment       : 'uat',
                isProductionDeployment: false,
                args                  : [
                    credentials : [
                        [id: 'CH3PCF01 - Jenkins Space Developer', env: 'uat'],
                    ],
                    appName     : 'snow-1-uat-candidate',
                    foundation  : 'ch3pcf01',
                    organization: 'CoreEng',
                    space       : 'UAT',
                    env         : 'uat',
                    artifact    : 'lookup:publishUrl',
                    autoscale   : false,
                ]
            ],
            [
                parallelType   : 'jenkins',
                sdlcEnvironment: 'DEPLOYUAT',
                branchPattern  : '.*',
                phases         : [
                    DEPLOYUAT: [
                        moduleType            : 'test',
                        moduleName            : 'cnp-jenkins-job-build',
                        subCommand            : 'build',
                        branchPattern         : '.*',
                        sdlcEnvironment       : '',
                        isProductionDeployment: false,
                        args                  : [
                            jobName    : 'orchestrators-folders/job/hs-config-snow-proxy/job/Testing/job/test-pipeline/job/test-ov2',
                            buildParams: "env=uat&target=DEPLOY"
                        ],
                    ]
                ],
            ],
            [
                moduleType            : 'pcf',
                moduleName            : 'cnp-deploy-pcf',
                subCommand            : 'cutover',
                branchPattern         : '.*',
                sdlcEnvironment       : 'uat',
                isProductionDeployment: false,
                args                  : [
                    credentials : [
                        [id: 'CH3PCF01 - Jenkins Space Developer', env: 'uat'],
                    ],
                    appName     : 'snow-1-uat-candidate',
                    foundation  : 'ch3pcf01',
                    organization: 'CoreEng',
                    space       : 'UAT',
                    env         : 'uat',
                ]
            ],
            [
                parallelType   : 'jenkins',
                sdlcEnvironment: 'ROUTEUAT',
                branchPattern  : '.*',
                phases         : [
                    ROUTEUAT: [
                        moduleType            : 'test',
                        moduleName            : 'cnp-jenkins-job-build',
                        subCommand            : 'build',
                        branchPattern         : '.*',
                        sdlcEnvironment       : '',
                        isProductionDeployment: false,
                        args                  : [
                            jobName    : 'orchestrators-folders/job/hs-config-snow-proxy/job/Testing/job/test-pipeline/job/test-ov2',
                            buildParams: "env=uat&target=ROUTE"
                        ],
                    ]
                ],
            ],
            [
                moduleType            : 'openshift',
                moduleName            : 'cnp-deploy-argorollouts',
                subCommand            : 'deploy',
                branchPattern         : 'NotYet.*',
                sdlcEnvironment       : 'uat',
                isProductionDeployment: false,
                args                  : [
                    appName     : 'snow-1-uat',
                    namespace   : '',
                    platform    : '',
                    imageName   : 'lookup:imageName',
                    imageTag    : 'lookup:imageTag',
                    useArgoCD   : 'true',
                    argoCDServer: 'snow-1-uat',
                    cluster     : '',
                    configDir   : 'envs/uat',
                    env         : 'uat'
                ]
            ],
            [
                moduleType            : 'openshift',
                moduleName            : 'cnp-deploy-argorollouts',
                subCommand            : 'promote',
                branchPattern         : 'NotYet.*',
                sdlcEnvironment       : 'uat',
                isProductionDeployment: false,
                args                  : [
                    appName  : 'snow-1-uat',
                    configDir: 'envs/uat',
                    namespace: '',
                    cluster  : '',
                    platform : '',
                    env      : 'uat'
                ]
            ],
            [
                releaseType         : 'preRelease',
                releaseBranchPattern: '^(release|hotfix).*',
                branchPattern       : '^(release|hotfix).*',
                phases              : [
                    [
                        moduleType            : 'pcf',
                        moduleName            : 'cnp-deploy-pcf',
                        subCommand            : 'deploy',
                        branchPattern         : '^(release|hotfix).*',
                        sdlcEnvironment       : 'prod',
                        isProductionDeployment: true,
                        args                  : [
                            credentials       : [
                                [id: 'PS2PCF02 - Jenkins Space Developer', env: 'prod'],
                            ],
                            appName           : 'snow-1-prod-ps2-candidate',
                            foundation        : 'ps2pcf02',
                            organization      : 'CoreEng',
                            space             : 'Production',
                            env               : 'prod',

                            autoscale         : false,
                            monitoringSolution: 'newrelic',
                            newRelicAppName   : 'snow-spring-boot'
                        ]
                    ],
                    [
                        moduleType            : 'pcf',
                        moduleName            : 'cnp-deploy-pcf',
                        subCommand            : 'deploy',
                        branchPattern         : '^(release|hotfix).*',
                        sdlcEnvironment       : 'dr',
                        isProductionDeployment: true,
                        args                  : [
                            credentials       : [
                                [id: 'CH3PCF03 - Jenkins Space Developer', env: 'dr'],
                            ],
                            appName           : 'snow-1-prod-ch3-candidate',
                            foundation        : 'ch3pcf03',
                            organization      : 'CoreEng',
                            space             : 'Production',
                            env               : 'dr',

                            autoscale         : false,
                            monitoringSolution: 'newrelic',
                            newRelicAppName   : 'snow-spring-boot'
                        ]
                    ],
                ]
            ],
            [
                releaseType         : 'release',
                releaseBranchPattern: '^(release|hotfix).*',
                branchPattern       : '^(release|hotfix).*',
                phases              : [
                    [
                        moduleType            : 'pcf',
                        moduleName            : 'cnp-deploy-pcf',
                        subCommand            : 'cutover',
                        branchPattern         : '^(release|hotfix).*',
                        releaseBranchPattern  : '^(release|hotfix).*',
                        sdlcEnvironment       : 'prod',
                        isProductionDeployment: true,
                        args                  : [
                            credentials : [
                                [id: 'PS2PCF02 - Jenkins Space Developer', env: 'prod'],
                            ],
                            appName     : 'snow-1-prod-ps2-candidate',
                            foundation  : 'ps2pcf02',
                            organization: 'CoreEng',
                            space       : 'Production',
                            env         : 'prod',
                        ]
                    ],
                    [
                        moduleType            : 'pcf',
                        moduleName            : 'cnp-deploy-pcf',
                        subCommand            : 'cutover',
                        branchPattern         : '^(release|hotfix).*',
                        releaseBranchPattern  : '^(release|hotfix).*',
                        sdlcEnvironment       : 'dr',
                        isProductionDeployment: true,
                        args                  : [
                            credentials : [
                                [id: 'CH3PCF03 - Jenkins Space Developer', env: 'dr'],
                            ],
                            appName     : 'snow-1-prod-ch3-candidate',
                            foundation  : 'ch3pcf03',
                            organization: 'CoreEng',
                            space       : 'Production',
                            env         : 'dr',
                        ]
                    ],
                ]
            ],
            [
                parallelType   : 'jenkins',
                sdlcEnvironment: 'DEPLOYPROD',
                branchPattern  : '^(release|hotfix).*',
                phases         : [
                    DEPLOYPROD: [
                        moduleType            : 'test',
                        moduleName            : 'cnp-jenkins-job-build',
                        subCommand            : 'build',
                        branchPattern         : '.*',
                        sdlcEnvironment       : '',
                        isProductionDeployment: false,
                        args                  : [
                            jobName: 'orchestrators-folders/job/hs-config-snow-proxy/job/Testing/job/test-pipeline/job/test-ov2'
                        ],
                    ]
                ],
            ],
            [
                parallelType   : 'jenkins',
                sdlcEnvironment: 'ROUTEPROD',
                branchPattern  : '^(release|hotfix).*',
                phases         : [
                    ROUTEPROD: [
                        moduleType            : 'test',
                        moduleName            : 'cnp-jenkins-job-build',
                        subCommand            : 'build',
                        branchPattern         : '.*',
                        sdlcEnvironment       : '',
                        isProductionDeployment: false,
                        args                  : [
                            jobName: 'orchestrators-folders/job/hs-config-snow-proxy/job/Testing/job/test-pipeline/job/test-ov2'
                        ],
                    ]
                ],
            ],
            [
                parallelType   : 'jenkins',
                sdlcEnvironment: 'DEPLOYDR',
                branchPattern  : '^(release|hotfix).*',
                phases         : [
                    DEPLOYDR: [
                        moduleType            : 'test',
                        moduleName            : 'cnp-jenkins-job-build',
                        subCommand            : 'build',
                        branchPattern         : '.*',
                        sdlcEnvironment       : '',
                        isProductionDeployment: false,
                        args                  : [
                            jobName: 'orchestrators-folders/job/hs-config-snow-proxy/job/Testing/job/test-pipeline/job/test-ov2'
                        ],
                    ]
                ],
            ],
            [
                parallelType   : 'jenkins',
                sdlcEnvironment: 'ROUTEDR',
                branchPattern  : '^(release|hotfix).*',
                phases         : [
                    ROUTEDR: [
                        moduleType            : 'test',
                        moduleName            : 'cnp-jenkins-job-build',
                        subCommand            : 'build',
                        branchPattern         : '.*',
                        sdlcEnvironment       : '',
                        isProductionDeployment: false,
                        args                  : [
                            jobName: 'orchestrators-folders/job/hs-config-snow-proxy/job/Testing/job/test-pipeline/job/test-ov2'
                        ]
                    ]
                ],
            ],
        ]
        when:
        cignaBuildFlow {
            githubConnectionName = 'github'
            commitStatusName = 'snow'
            cloudName = 'hs-config-snow-proxy-openshift-devops1'
            phases = phasesToRun
        }
        then:
        psc.podSelector.podTemplates.size() == 1
        // the pod template should contain the images for scheduled phases (image names shortened to allow for version changes)
        ['docker-core', 'docker-maven', 'docker-pcf'].each {
            assert psc.podSelector.podTemplates['hs-config-snow-proxy-openshift-devops1'].contains(it)
        }
        // since OSCP phases are excluded by branch pattern, k8s should _not_ be in the pod template
        !psc.podSelector.podTemplates['hs-config-snow-proxy-openshift-devops1'].contains('docker-k8s')

    }

    def '''that linting phase resolves the required containers for pod template construction'''() {
        given:
        explicitlyMockPipelineVariable('bearerToken')
        when:
        cignaBuildFlow {
            gitlabConnectionName = 'gitlab_server'
            commitStatusName = 'filler'
            cloudName = 'cloud'
            phases = [
                [
                    branchPattern: '.*',
                    lintingTypes : [
                        maven : [
                            commandArgs : ['clean', 'verify'],
                            authSettings: true,
                            junit       : [
                                testResults: 'target/surefire-reports/*.xml'
                            ]
                        ],
                        bandit: [
                            commandArgs: ['-lll', '-iii'],
                            versionSpec: '>=1.6.2',
                            junit      : [
                                allowEmptyResults: true
                            ]
                        ]
                    ],
                ]
            ]
        }
        then:
        psc.podSelector.podTemplates.size() == 1
        psc.podSelector.podTemplates['cloud'].contains('maven')
    }

    def '''Verify that phases with nested ticketing or testing phases accumulate all required containers.'''() {
        given:
        explicitlyMockPipelineVariable('bearerToken')
        explicitlyMockPipelineVariable('openshiftSecret')
        explicitlyMockPipelineVariable('QUAY_TOKEN')
        getPipelineMock("sh")({
            if (!(it instanceof Map)) {
                false
            } else {
                it?.script ==~ /ls -l .* | grep ^d | awk '\{print .*}'/
            }
        }) >> "file_one\nfile_two"


        def version = 'test'
        when:
        explicitlyMockPipelineVariable('QUAY_TOKEN')
        cignaBuildFlow {
            githubConnectionName = 'github'
            commitStatusName = 'Adjudicator App Build'
            cloudName = "adjudicator-openshift-devops1"
            webexTeamsRoom = 'a3b03650-838b-11ec-b8ff-cde520824ca3'
            compliance:
            [
                audit: [
                    buildCount: 5,
                ]
            ]
            releaseBranchPattern = 'main'
            checkmarxEnabled = true
            sonarEnabled = true
            phases = [
                [
                    branchPattern: '.*',
                    lintingTypes : [
                        'go': [
                            timeout: '8m',
                        ],
                    ]
                ],
                [
                    buildType           : 'Go',
                    golang              : [
                        apiTokenId: 'ADJUDICATOR_GITHUB_TOKEN',
                        tokenType : 'GitHub',
                    ],
                    branchPattern       : '.*',
                    releaseBranchPattern: 'none', // do not need to package
                    verbosityFlag       : '-vvv',
                    sonarQube           : [
                        credentialsId    : 'Sonarqube-A-Scanning-Token',
                        scannerProperties: [
                            'sonar.projectKey=theAdjudicator',
                            'sonar.projectName=theAdjudicator',
                            'sonar.sources=.',
                            'sonar.exclusions=**/*_test.go,**/vendor/**',
                            'sonar.tests=.',
                            'sonar.test.inclusions=**/*_test.go',
                            'sonar.test.exclusions=**/vendor/**',
                            'sonar.cfamily.cache.enabled=false',
                            'sonar.c.file.suffixes=-',
                            'sonar.cpp.file.suffixes=-',
                            'sonar.objc.file.suffixes=-'
                        ]
                    ],
                    checkmarx           : [
                        credentialsId: 'checkmarx-devops-prod',
                        settings     : [
                            CX_PROJECT_TEAM_NAME  : 'devops',
                            CX_PROJECT_NAME       : 'adjudicator',
                            CX_EXCLUDE_FOLDER_LIST: 'vendor',
                            CX_INCL_EXCL_FILE_LIST: '!**/*_test.go',
                        ],
                    ],
                ],
                [
                    packagingType       : 'kaniko',
                    branchPattern       : '.*',
                    releaseBranchPattern: 'main',
                    conftestValidation  : false, // XXX.cnm - temporary until correct minideb version tags available
                    dockerRegistry      : 'registry.cigna.com',
                    image               : [
                        org         : 'adjudicator-devops',
                        name        : 'adjudicator',
                        securityScan: true,
                        buildArgs   : [
                            ADJUDICATOR_DNS_SERVICE: "adjudicator-dns-service.adjudicator-nonproduction.svc.cluster.local",
                            ADJUDICATOR_VERSION    : "$version",
                            DEPLOY_ENVIRONMENT     : "NonProd",
                            PROMETHEUS_ENABLED     : "true",
                            OPENTELEMETRY_ENABLED  : "false",
                            REDIS_ADDRESS          : "redis-master.adjudicator-nonproduction.svc.cluster.local",
                        ],
                        tags        : [
                            [
                                tag   : 'dev',
                                expire: true
                            ],
                            [
                                tag   : "dev-$version",
                                expire: false
                            ]
                        ]
                    ],
                    quay                : [
                        credentialsId: 'quay_adjudicator_token_prod'
                    ]
                ],
                [
                    packagingType     : 'kaniko',
                    branchPattern     : 'main',
                    conftestValidation: false, // XXX.cnm - temporary until correct minideb version tags available
                    dockerRegistry    : 'registry.cigna.com',
                    image             : [
                        org         : 'adjudicator-devops',
                        name        : 'adjudicator',
                        securityScan: true,
                        buildArgs   : [
                            ADJUDICATOR_DNS_SERVICE: "adjudicator-dns-service.adjudicator-production.svc.cluster.local",
                            ADJUDICATOR_VERSION    : "$version",
                            PROMETHEUS_ENABLED     : "true",
                            OPENTELEMETRY_ENABLED  : "false",
                            DEPLOY_ENVIRONMENT     : "Prod",
                            REDIS_ADDRESS          : "redis-master.adjudicator-production.svc.cluster.local",
                        ],
                        tags        : [
                            [
                                tag   : 'latest',
                                expire: true
                            ],
                            [
                                tag   : 'prod',
                                expire: false
                            ],
                            [
                                tag   : "$version",
                                expire: false
                            ],
                            [
                                tag   : "prod-$version",
                                expire: false
                            ]
                        ]
                    ],
                    quay              : [
                        credentialsId: 'quay_adjudicator_token_prod'
                    ]
                ],
                [
                    deploymentType        : 'openshift',
                    containerImage        : 'enterprise-devops/helm-cli',
                    containerVersion      : 'v3.5.3',
                    branchPattern         : '.*',
                    extraCredentials      : [
                        string(
                            credentialsId: 'SVP_ADJUDICATOR_INT',
                            variable: 'SVP_ADJUDICATOR_INT'
                        ),
                        string(
                            credentialsId: 'PRD_ADJUDICATOR_ARTIFACTORY_TOKEN',
                            variable: 'ADJUDICATOR_ARTIFACTORY_TOKEN'
                        ),
                        string(
                            credentialsId: 'ADJUDICATOR_SNOW_INT_ID',
                            variable: 'ADJUDICATOR_SNOW_INT_ID'
                        ),
                        string(
                            credentialsId: 'SVT_ADJUDICATOR_INT',
                            variable: 'SVT_ADJUDICATOR_INT'
                        ),
                        string(
                            credentialsId: 'ADJUDICATOR_GITHUB_TOKEN',
                            variable: 'GITHUB_ACCESS_TOKEN'
                        ),
                        string(
                            credentialsId: 'SPLUNK_HEC_TOKEN',
                            variable: 'SPLUNK_HEC_TOKEN'
                        ),
                        string(
                            credentialsId: 'REDIS_NONPROD_PASSWORD',
                            variable: 'REDIS_SECURE'
                        ),
                        usernamePassword(
                            credentialsId: 'adjudicator-ldap-auth-internal',
                            usernameVariable: 'UNUSED_VALUE',
                            passwordVariable: 'LDAP_PWD'
                        ),
                    ],
                    sdlcEnvironment       : 'dev_oscpv4',
                    isProductionDeployment: false,
                    openshift             : [
                        url          : 'https://api.gp-2-nonprod.openshift.cignacloud.com:6443', // This url may need to change
                        credentialsId: 'SVP_ADJUDICATOR_INT',
                        project      : 'adjudicator-nonproduction',
                        user         : 'SVP_ADJUDICATOR_INT'
                    ],
                    helm                  : [
                        version: '3.5.3',
                        command: 'helm upgrade the-adjudicator ./deploy/adjudicator-helm' +
                            ' -f ./deploy/adjudicator-helm/nonprod/values.yaml' +
                            " --set-string revision=dev-$version" +
                            ' --set-string revisionHash=$GIT_COMMIT' +
                            " --set-string imageTag=dev-$version" +
                            ' --set-string env.secret.ADJUDICATOR_ARTIFACTORY_TOKEN=$ADJUDICATOR_ARTIFACTORY_TOKEN' +
                            ' --set-string env.secret.ADJUDICATOR_SNOW_INT_ID=$ADJUDICATOR_SNOW_INT_ID' +
                            ' --set-string env.secret.SVP_ADJUDICATOR_INT=$SVP_ADJUDICATOR_INT' +
                            ' --set-string env.secret.SVT_ADJUDICATOR_INT=$SVT_ADJUDICATOR_INT' +
                            ' --set-string env.secret.GITHUB_ACCESS_TOKEN=$GITHUB_ACCESS_TOKEN' +
                            ' --set-string env.secret.SPLUNK_HEC_TOKEN=$SPLUNK_HEC_TOKEN' +
                            ' --set-string env.secret.REDIS_SECURE=$REDIS_SECURE' +
                            ' --set-string env.secret.LDAP_PWD=$LDAP_PWD' +
                            ' --wait --install --cleanup-on-fail --history-max 1'
                    ],
                    testing               : [
                        [
                            testType       : 'newman',
                            basicAuth      : 'adjudicator-newman-testcreds',
                            collectionsPath: 'tests/newman'
                        ],
                        [
                            testType: "JMeter",
                            planPath: "tests/jmeter/PerformanceTests.jmx",
                            args    : '-JSERVER_NAME=adjudicator.apps.gp-2-nonprod.openshift.cignacloud.com -JAUTH_PWD=$LDAP_PWD -JNUM_ITERATIONS=10'
                        ]
                    ]
                ],
                [
                    deploymentType        : 'openshift',
                    branchPattern         : 'main',
                    extraCredentials      : [
                        string(
                            credentialsId: 'SVP_ADJUDICATOR_INT',
                            variable: 'SVP_ADJUDICATOR_INT'
                        ),
                        string(
                            credentialsId: 'PRD_ADJUDICATOR_ARTIFACTORY_TOKEN',
                            variable: 'ADJUDICATOR_ARTIFACTORY_TOKEN'
                        ),
                        string(
                            credentialsId: 'ADJUDICATOR_SNOW_INT_ID',
                            variable: 'ADJUDICATOR_SNOW_INT_ID'
                        ),
                        string(
                            credentialsId: 'SVT_ADJUDICATOR_INT',
                            variable: 'SVT_ADJUDICATOR_INT'
                        ),
                        string(
                            credentialsId: 'ADJUDICATOR_GITHUB_TOKEN',
                            variable: 'GITHUB_ACCESS_TOKEN'
                        ),
                        string(
                            credentialsId: 'SPLUNK_HEC_TOKEN',
                            variable: 'SPLUNK_HEC_TOKEN'
                        ),
                        string(
                            credentialsId: 'REDIS_PROD_PASSWORD',
                            variable: 'REDIS_SECURE'
                        ),
                        usernamePassword(
                            credentialsId: 'adjudicator-ldap-auth-internal',
                            usernameVariable: 'UNUSED_VALUE',
                            passwordVariable: 'LDAP_PWD'
                        ),
                    ],
                    sdlcEnvironment       : 'prod_oscpv4',
                    isProductionDeployment: true,
                    openshift             : [
                        url          : 'https://api.gp-2-prod.openshift.cignacloud.com:6443', // This url may need to change
                        credentialsId: 'SVP_ADJUDICATOR_INT_PROPER',
                        project      : 'adjudicator-production',
                        user         : 'SVP_ADJUDICATOR_INT'
                    ],
                    helm                  : [
                        version: '3.5.3',
                        command: 'helm upgrade the-adjudicator ./deploy/adjudicator-helm' +
                            ' -f ./deploy/adjudicator-helm/prod/values.yaml' +
                            " --set-string revision=prod-$version" +
                            ' --set-string revisionHash=$GIT_COMMIT' +
                            " --set-string imageTag=prod-$version" +
                            ' --set-string env.secret.ADJUDICATOR_ARTIFACTORY_TOKEN=$ADJUDICATOR_ARTIFACTORY_TOKEN' +
                            ' --set-string env.secret.ADJUDICATOR_SNOW_INT_ID=$ADJUDICATOR_SNOW_INT_ID' +
                            ' --set-string env.secret.SVP_ADJUDICATOR_INT=$SVP_ADJUDICATOR_INT' +
                            ' --set-string env.secret.SVT_ADJUDICATOR_INT=$SVT_ADJUDICATOR_INT' +
                            ' --set-string env.secret.GITHUB_ACCESS_TOKEN=$GITHUB_ACCESS_TOKEN' +
                            ' --set-string env.secret.SPLUNK_HEC_TOKEN=$SPLUNK_HEC_TOKEN' +
                            ' --set-string env.secret.REDIS_SECURE=$REDIS_SECURE' +
                            ' --set-string env.secret.LDAP_PWD=$LDAP_PWD' +
                            ' --wait --install --cleanup-on-fail --history-max 1'
                    ],
                    testing               : [
                        [
                            testType       : 'newman',
                            basicAuth      : 'adjudicator-newman-testcreds',
                            collectionsPath: 'tests/newman'
                        ],
                        [
                            testType: "JMeter",
                            planPath: "tests/jmeter/PerformanceTests.jmx",
                            args    : '-JSERVER_NAME=adjudicator.cigna.com -JAUTH_PWD=$LDAP_PWD -JNUM_ITERATIONS=1'
                        ]
                    ],
                    ticket                : [
                        ticketType                : 'Servicenow',
                        changeEnvironment         : 'prod',
                        credentialsId             : 'adjudicator_snow', // add to jenkins creds
                        title                     : 'SCT0000600',
                        cmdb_ci                   : 'Adjudicator', // check status of CI
                        requested_by              : 'person',
                        assigned_to               : 'person',
                        plannedDuration           : 60,
                        u_emergency_contact_person: 'person',
                        u_emergency_contact_number: 1234567890
                    ]
                ]
            ]
        }
        then:
        psc.podSelector.podTemplates['adjudicator-openshift-devops1'].contains('newmanvalpine')
    }

    def '''verify container resource allocation if no overrides detected'''() {
        given:
        explicitlyMockPipelineVariable("artifactoryDeployerIdName")
        explicitlyMockPipelineVariable("artifactoryDeployerIdToken")
        explicitlyMockPipelineStep('pyLint')
        explicitlyMockPipelineStep('recordIssues')
        explicitlyMockPipelineVariable('bearerToken')
        explicitlyMockPipelineVariable('openshiftSecret')
        explicitlyMockPipelineVariable('QUAY_TOKEN')
        when:
        cignaBuildFlow {
            gitlabConnectionName = 'gitlab_server'
            commitStatusName = 'Sample Python App'
            cloudName = 'epf-openshift-devops1'
            emailRecipients = 'EDOPipelines&Compliance@Cigna.com'
            webexTeamsRoom = '00833df0-c316-11eb-990d-e5df280e810c'
            phases = [
                [
                    branchPattern: '.*',
                    lintingTypes : [
                        bandit: [
                            commandArgs: ['-lll', '-iii'],
                            versionSpec: '>=1.6.2',
                            junit      : [
                                allowEmptyResults: true
                            ]
                        ]
                    ]
                ],
                [
                    buildType           : 'python',
                    chechmarxEnabled    : false,
                    branchPattern       : '.*',
                    releaseBranchPattern: 'master',
                    sonarQube           : [
                        credentialsId: 'sonarqube-demo-token'
                    ],
                    artifactory         : [
                        credentialsId: 'artifactory-prod-deployer-ci0008907951-apikey'
                    ],
                    checkmarx           : [
                        credentialsId: 'global-checkmarx-id',
                        settings     : [
                            CX_PROJECT_TEAM_NAME: 'DevOps',
                        ],
                    ],
                    warningsNG          : [
                        tools            : [pyLint(pattern: 'pylint.log')],
                        enabledForFailure: false,
                        qualityGates     : [
                            [threshold: 1, type: 'TOTAL_ERROR', unstable: false],
                            [threshold: 1, type: 'TOTAL_HIGH', unstable: false],
                            [threshold: 2, type: 'TOTAL_NORMAL', unstable: true]
                        ]
                    ]
                ],
                [
                    packagingType : 'kaniko',
                    branchPattern : 'master',
                    dockerRegistry: 'registry.cigna.com',
                    image         : [
                        org : 'enterprise-devops',
                        name: 'sample-python-app'
                    ],
                    quay          : [
                        credentialsId: 'conduit-quay-token'
                    ]
                ],
            ]
        }

        then:
        psc.podSelector.podTemplates['epf-openshift-devops1'].contains('1000Mi')
    }

    def '''Pipeline with container overrides and multiple namespaces uses correct container names'''() {
        given:
        explicitlyMockPipelineVariable("artifactoryDeployerIdName")
        explicitlyMockPipelineVariable("artifactoryDeployerIdToken")
        explicitlyMockPipelineVariable('bearerToken')
        explicitlyMockPipelineVariable('openshiftSecret')
        when:
        String containerImage = 'enterprise-devops/aws-d-megatainer'
        String containerVersion = '1.0.12-1'
        String cloudNameEKS = "da-hpp-camr-pmt-eks-jenkins-cluster-prod"
        String cloudNameOS = "da-hpp-camr-pmt-openshift-devops1"

        def buildPhase = { deploy_env, account ->
            [
                branchPattern         : ".*",
                runInAWS              : false,
                cloudName             : "${cloudNameOS}",
                container             : [
                    image  : "${containerImage}",
                    version: "${containerVersion}",
                    cpu    : 3000,
                    memory : 10000,
                ],
                phaseCache            : true,
                sdlcEnvironment       : deploy_env,
                isProductionDeployment: false,
                deploymentType        : 'plz',
                alias                 : 'build_app',
                deployEnv             : 'App Build'
            ]
        }

        def s3SyncPhase = { deploy_env, account ->
            [
                branchPattern         : ".*",
                runInAWS              : true,
                cloudName             : "${cloudNameEKS}",
                withEnv               : [
                    "ENV=${deploy_env}",
                ],
                aws                   : [
                    cloudServiceAccountName: "jenkins-robot-${deploy_env}",
                    targetAccount          : account,
                    accountRoleName        : 'Enterprise/GKCAMRPMTDEPLOYER',
                    region                 : 'us-east-1'
                ],
                container             : [
                    image  : "${containerImage}",
                    version: "${containerVersion}",
                    cpu    : 2000,
                    memory : 2000,
                ],
                phaseCache            : true,
                sdlcEnvironment       : deploy_env,
                isProductionDeployment: false,
                deploymentType        : 'plz',
                alias                 : 'sync_app',
                deployEnv             : 'S3 Sync Build'
            ]
        }


        def runThesePhases = []
        runThesePhases += buildPhase("prod", "361987197148")
        runThesePhases += s3SyncPhase("prod", "461894682041")
        cignaBuildFlow {
            githubConnectionName = 'test'
            cloudName = cloudNameOS
            phases = runThesePhases
        }
        then:
        psc.podSelector.podTemplates[cloudNameOS].contains('v1012-1')
        psc.podSelector.podTemplates[cloudNameEKS].contains("jenkins-robot-prod")
        !psc.podSelector.podTemplates[cloudNameOS].contains("jenkins-robot-prod")
    }

    def '''Verify that when each phase has it's own unique podGroup, it is given it's own podTemplate'''() {
        when:
        cignaBuildFlow {
            githubConnectionName = 'github'
            commitStatusName = 'Enterprise Pipeline Framework'

            cloudName = 'test-cloud'
            logHistoryCount = '5'
            webexTeamsRoom = 'bd7cdcb0-27a4-11ed-af92-f5afe966fcf4'
            phases = [
                [
                    moduleType            : 'maven',
                    branchPattern         : '.*',
                    sdlcEnvironment       : 'dev',
                    isProductionDeployment: false,
                    podGroup              : 'maven',
                    module                : [
                        image       : 'cnp/cnp-docker-maven-java8',
                        contractName: 'BUILD',
                        moduleName  : 'cnp-build-publish-maven-create',
                        subCommand  : 'build',
                        version     : 'latest',
                        commandName : "cnp-build-publish-maven-create.sh",
                    ],
                    container             : [
                        cpu   : 2000,
                        memory: 8000
                    ],
                    args                  : [
                        credentials: [
                            [id: 'cred']
                        ],
                        publish    : false
                    ]
                ],
                [
                    moduleType            : 'test',
                    moduleName            : 'cnp-jenkins-job-build',
                    branchPattern         : '.*',
                    sdlcEnvironment       : 'dev',
                    podGroup              : 'testpod',
                    isProductionDeployment: false,
                    args                  : [
                        jobName    : 'orchestrators-folders/job/hs-pipeline/job/Functional%20Test%20Apps/job/zz-test-wright-jobs/job/test-wright-dev',
                        jenkinsURL : 'https://orchestrator18.orchestrator-v2.sys.cigna.com/',
                        buildParams: 'test-categories=epf%2Fci',
                        credentials: [[id: 'cred']]
                    ]
                ]
            ]
        }

        // we must always have a baseCloudName pod, even if there's no specified phases because the root pod is
        // independent of the phase and podGroup is unknown at the point it is created
        then:
        assert psc.podSelector.baseCloudName == 'test-cloud'
        assert psc.podSelector.podTemplates.size() == 3
        assert psc.podSelector.podTemplates.keySet().toList() == ['test-cloud', 'maven', 'testpod']
    }

    def '''Verify that when one phase has it's own unique podGroup, containers are allocated to the correct pod groups'''() {
        when:
        cignaBuildFlow {
            githubConnectionName = 'github'
            commitStatusName = 'Enterprise Pipeline Framework'

            cloudName = 'test-cloud'
            logHistoryCount = '5'
            webexTeamsRoom = 'bd7cdcb0-27a4-11ed-af92-f5afe966fcf4'
            phases = [
                [
                    moduleType            : 'maven',
                    branchPattern         : '.*',
                    sdlcEnvironment       : 'dev',
                    isProductionDeployment: false,
                    module                : [
                        image       : 'cnp/cnp-docker-maven-java8',
                        contractName: 'BUILD',
                        moduleName  : 'cnp-build-publish-maven-create',
                        subCommand  : 'build',
                        version     : 'latest',
                        commandName : "cnp-build-publish-maven-create.sh",
                    ],
                    container             : [
                        cpu   : 2000,
                        memory: 8000
                    ],
                    args                  : [
                        credentials: [
                            [id: 'cred']
                        ],
                        publish    : false
                    ]
                ],
                [
                    moduleType            : 'test',
                    moduleName            : 'cnp-jenkins-job-build',
                    branchPattern         : '.*',
                    sdlcEnvironment       : 'dev',
                    podGroup              : 'testpod',
                    isProductionDeployment: false,
                    args                  : [
                        jobName    : 'orchestrators-folders/job/hs-pipeline/job/Functional%20Test%20Apps/job/zz-test-wright-jobs/job/test-wright-dev',
                        jenkinsURL : 'https://orchestrator18.orchestrator-v2.sys.cigna.com/',
                        buildParams: 'test-categories=epf%2Fci',
                        credentials: [[id: 'cred']]
                    ]
                ]
            ]
        }

        // we must always have a baseCloudName pod, even if there's no specified phases because the root pod is
        // independent of the phase and podGroup is unknown at the point it is created
        then:
        assert psc.podSelector.baseCloudName == 'test-cloud'
        assert psc.podSelector.podTemplates.size() == 2
        assert psc.podSelector.podTemplates['testpod'].contains('"name":"cnp-docker-corev')
        assert psc.podSelector.podTemplates['test-cloud'].contains('"name":"cnp-docker-maven-java8v')
        assert psc.podSelector.podTemplates['test-cloud'].contains('"name":"cnp-docker-corev') // common preflight
        assert !psc.podSelector.podTemplates['testpod'].contains('"name":"cnp-docker-maven-java8v')
    }

    def '''Verify that grouping phases by artificial pod group names, the containers are assigned to the correct pod template'''() {
        given:
        explicitlyMockPipelineVariable('bearerToken')
        explicitlyMockPipelineVariable('openshiftSecret')
        explicitlyMockPipelineVariable('QUAY_TOKEN')
        when:
        def version = "0.4.157"
        def REQUESTED_BY = "C46043"
        def ASSIGNED_TO = "C46043"

        cignaBuildFlow {
            githubConnectionName = 'github'
            commitStatusName = 'Adjudicator App Build'
            cloudName = "adjudicator-openshift-devops1"
            webexTeamsRoom = 'a3b03650-838b-11ec-b8ff-cde520824ca3'
            compliance:
            [
                audit: [
                    buildCount: 5,
                ]
            ]
            releaseBranchPattern = 'main'
            checkmarxEnabled = true
            sonarEnabled = true
            phaseCache = true
            stashIncludePattern = '**/vendor/**/*'
            phases = [
                [
                    podGroup     : 'golang',
                    branchPattern: '.*',
                    lintingTypes : [
                        'go': [
                            timeout: '8m',
                        ],
                    ]
                ],
                [
                    podGroup            : 'golang',
                    buildType           : 'Go',
                    golang              : [
                        apiTokenId: 'ADJUDICATOR_GITHUB_TOKEN',
                        tokenType : 'GitHub',
                    ],
                    branchPattern       : '.*',
                    releaseBranchPattern: 'none', // do not need to package
                    verbosityFlag       : '-vvv',
                    sonarQube           : [
                        credentialsId    : 'Sonarqube-A-Scanning-Token',
                        scannerProperties: [
                            'sonar.projectKey=theAdjudicator',
                            'sonar.projectName=theAdjudicator',
                            'sonar.sources=.',
                            'sonar.exclusions=**/*_test.go,**/vendor/**',
                            'sonar.tests=.',
                            'sonar.test.inclusions=**/*_test.go',
                            'sonar.test.exclusions=**/vendor/**',
                            'sonar.cfamily.cache.enabled=false',
                            'sonar.c.file.suffixes=-',
                            'sonar.cpp.file.suffixes=-',
                            'sonar.objc.file.suffixes=-'
                        ]
                    ],
                    checkmarx           : [
                        credentialsId: 'checkmarx-devops-prod',
                        settings     : [
                            CX_PROJECT_TEAM_NAME  : 'devops',
                            CX_PROJECT_NAME       : 'adjudicator',
                            CX_EXCLUDE_FOLDER_LIST: 'vendor',
                            CX_INCL_EXCL_FILE_LIST: '!**/*_test.go',
                        ],
                    ],
                ],
                [
                    packagingType       : 'kaniko',
                    branchPattern       : '.*',
                    releaseBranchPattern: 'main',
                    conftestValidation  : false, // XXX.cnm - temporary until correct minideb version tags available
                    dockerRegistry      : 'registry.cigna.com',
                    image               : [
                        org         : 'adjudicator-devops',
                        name        : 'adjudicator',
                        securityScan: true,
                        buildArgs   : [
                            ADJUDICATOR_DNS_SERVICE: "adjudicator-dns-service.adjudicator-nonproduction.svc.cluster.local",
                            ADJUDICATOR_VERSION    : "$version",
                            DEPLOY_ENVIRONMENT     : "NonProd",
                            PROMETHEUS_ENABLED     : "true",
                            OPENTELEMETRY_ENABLED  : "false",
                            REDIS_ADDRESS          : "redis-master.adjudicator-nonproduction.svc.cluster.local",
                        ],
                        tags        : [
                            [
                                tag   : 'dev',
                                expire: true
                            ],
                            [
                                tag   : "dev-$version",
                                expire: false
                            ]
                        ]
                    ],
                    quay                : [
                        credentialsId: 'quay_adjudicator_token_prod'
                    ]
                ],
                [
                    packagingType     : 'kaniko',
                    branchPattern     : 'main',
                    conftestValidation: false, // XXX.cnm - temporary until correct minideb version tags available
                    dockerRegistry    : 'registry.cigna.com',
                    image             : [
                        org         : 'adjudicator-devops',
                        name        : 'adjudicator',
                        securityScan: true,
                        buildArgs   : [
                            ADJUDICATOR_DNS_SERVICE: "adjudicator-dns-service.adjudicator-production.svc.cluster.local",
                            ADJUDICATOR_VERSION    : "$version",
                            PROMETHEUS_ENABLED     : "true",
                            OPENTELEMETRY_ENABLED  : "false",
                            DEPLOY_ENVIRONMENT     : "Prod",
                            REDIS_ADDRESS          : "redis-master.adjudicator-production.svc.cluster.local",
                        ],
                        tags        : [
                            [
                                tag   : 'latest',
                                expire: true
                            ],
                            [
                                tag   : 'prod',
                                expire: false
                            ],
                            [
                                tag   : "$version",
                                expire: false
                            ],
                            [
                                tag   : "prod-$version",
                                expire: false
                            ]
                        ]
                    ],
                    quay              : [
                        credentialsId: 'quay_adjudicator_token_prod'
                    ]
                ],
                [
                    deploymentType        : 'openshift',
                    containerImage        : 'enterprise-devops/helm-cli',
                    containerVersion      : 'v3.5.3',
                    branchPattern         : '.*',
                    extraCredentials      : [
                        string(
                            credentialsId: 'SVP_ADJUDICATOR_INT',
                            variable: 'SVP_ADJUDICATOR_INT'
                        ),
                        string(
                            credentialsId: 'PRD_ADJUDICATOR_ARTIFACTORY_TOKEN',
                            variable: 'ADJUDICATOR_ARTIFACTORY_TOKEN'
                        ),
                        string(
                            credentialsId: 'ADJUDICATOR_SNOW_INT_ID',
                            variable: 'ADJUDICATOR_SNOW_INT_ID'
                        ),
                        string(
                            credentialsId: 'SVT_ADJUDICATOR_INT',
                            variable: 'SVT_ADJUDICATOR_INT'
                        ),
                        string(
                            credentialsId: 'ADJUDICATOR_GITHUB_TOKEN',
                            variable: 'GITHUB_ACCESS_TOKEN'
                        ),
                        string(
                            credentialsId: 'SPLUNK_HEC_TOKEN',
                            variable: 'SPLUNK_HEC_TOKEN'
                        ),
                        string(
                            credentialsId: 'REDIS_NONPROD_PASSWORD',
                            variable: 'REDIS_SECURE'
                        ),
                        usernamePassword(
                            credentialsId: 'adjudicator-ldap-auth-internal',
                            usernameVariable: 'UNUSED_VALUE',
                            passwordVariable: 'LDAP_PWD'
                        ),
                    ],
                    sdlcEnvironment       : 'dev_oscpv4',
                    isProductionDeployment: false,
                    openshift             : [
                        url          : 'https://api.gp-2-nonprod.openshift.cignacloud.com:6443', // This url may need to change
                        credentialsId: 'SVP_ADJUDICATOR_INT',
                        project      : 'adjudicator-nonproduction',
                        user         : 'SVP_ADJUDICATOR_INT'
                    ],
                    helm                  : [
                        version: '3.5.3',
                        command: 'helm upgrade the-adjudicator ./deploy/adjudicator-helm' +
                            ' -f ./deploy/adjudicator-helm/nonprod/values.yaml' +
                            " --set-string revision=dev-$version" +
                            ' --set-string revisionHash=$GIT_COMMIT' +
                            " --set-string imageTag=dev-$version" +
                            ' --set-string env.secret.ADJUDICATOR_ARTIFACTORY_TOKEN=$ADJUDICATOR_ARTIFACTORY_TOKEN' +
                            ' --set-string env.secret.ADJUDICATOR_SNOW_INT_ID=$ADJUDICATOR_SNOW_INT_ID' +
                            ' --set-string env.secret.SVP_ADJUDICATOR_INT=$SVP_ADJUDICATOR_INT' +
                            ' --set-string env.secret.SVT_ADJUDICATOR_INT=$SVT_ADJUDICATOR_INT' +
                            ' --set-string env.secret.GITHUB_ACCESS_TOKEN=$GITHUB_ACCESS_TOKEN' +
                            ' --set-string env.secret.SPLUNK_HEC_TOKEN=$SPLUNK_HEC_TOKEN' +
                            ' --set-string env.secret.REDIS_SECURE=$REDIS_SECURE' +
                            ' --set-string env.secret.LDAP_PWD=$LDAP_PWD' +
                            ' --wait --install --cleanup-on-fail --history-max 1'
                    ],
                    testing               : [
                        [
                            testType       : 'newman',
                            basicAuth      : 'adjudicator-newman-testcreds',
                            collectionsPath: 'tests/newman'
                        ],
                        [
                            testType: "JMeter",
                            planPath: "tests/jmeter/PerformanceTests.jmx",
                            args    : '-JSERVER_NAME=adjudicator.apps.gp-2-nonprod.openshift.cignacloud.com -JAUTH_PWD=$LDAP_PWD -JNUM_ITERATIONS=10'
                        ]
                    ]
                ],
                [
                    deploymentType        : 'openshift',
                    branchPattern         : 'main',
                    extraCredentials      : [
                        string(
                            credentialsId: 'SVP_ADJUDICATOR_INT',
                            variable: 'SVP_ADJUDICATOR_INT'
                        ),
                        string(
                            credentialsId: 'PRD_ADJUDICATOR_ARTIFACTORY_TOKEN',
                            variable: 'ADJUDICATOR_ARTIFACTORY_TOKEN'
                        ),
                        string(
                            credentialsId: 'ADJUDICATOR_SNOW_INT_ID',
                            variable: 'ADJUDICATOR_SNOW_INT_ID'
                        ),
                        string(
                            credentialsId: 'SVT_ADJUDICATOR_INT',
                            variable: 'SVT_ADJUDICATOR_INT'
                        ),
                        string(
                            credentialsId: 'ADJUDICATOR_GITHUB_TOKEN',
                            variable: 'GITHUB_ACCESS_TOKEN'
                        ),
                        string(
                            credentialsId: 'SPLUNK_HEC_TOKEN',
                            variable: 'SPLUNK_HEC_TOKEN'
                        ),
                        string(
                            credentialsId: 'REDIS_PROD_PASSWORD',
                            variable: 'REDIS_SECURE'
                        ),
                        usernamePassword(
                            credentialsId: 'adjudicator-ldap-auth-internal',
                            usernameVariable: 'UNUSED_VALUE',
                            passwordVariable: 'LDAP_PWD'
                        ),
                    ],
                    sdlcEnvironment       : 'prod_oscpv4',
                    isProductionDeployment: true,
                    openshift             : [
                        url          : 'https://api.gp-2-prod.openshift.cignacloud.com:6443', // This url may need to change
                        credentialsId: 'SVP_ADJUDICATOR_INT_PROPER',
                        project      : 'adjudicator-production',
                        user         : 'SVP_ADJUDICATOR_INT'
                    ],
                    helm                  : [
                        version: '3.5.3',
                        command: 'helm upgrade the-adjudicator ./deploy/adjudicator-helm' +
                            ' -f ./deploy/adjudicator-helm/prod/values.yaml' +
                            " --set-string revision=prod-$version" +
                            ' --set-string revisionHash=$GIT_COMMIT' +
                            " --set-string imageTag=prod-$version" +
                            ' --set-string env.secret.ADJUDICATOR_ARTIFACTORY_TOKEN=$ADJUDICATOR_ARTIFACTORY_TOKEN' +
                            ' --set-string env.secret.ADJUDICATOR_SNOW_INT_ID=$ADJUDICATOR_SNOW_INT_ID' +
                            ' --set-string env.secret.SVP_ADJUDICATOR_INT=$SVP_ADJUDICATOR_INT' +
                            ' --set-string env.secret.SVT_ADJUDICATOR_INT=$SVT_ADJUDICATOR_INT' +
                            ' --set-string env.secret.GITHUB_ACCESS_TOKEN=$GITHUB_ACCESS_TOKEN' +
                            ' --set-string env.secret.SPLUNK_HEC_TOKEN=$SPLUNK_HEC_TOKEN' +
                            ' --set-string env.secret.REDIS_SECURE=$REDIS_SECURE' +
                            ' --set-string env.secret.LDAP_PWD=$LDAP_PWD' +
                            ' --wait --install --cleanup-on-fail --history-max 1'
                    ],
                    testing               : [
                        [
                            testType       : 'newman',
                            basicAuth      : 'adjudicator-newman-testcreds',
                            collectionsPath: 'tests/newman'
                        ],
                        [
                            testType: "JMeter",
                            planPath: "tests/jmeter/PerformanceTests.jmx",
                            args    : '-JSERVER_NAME=adjudicator.cigna.com -JAUTH_PWD=$LDAP_PWD -JNUM_ITERATIONS=1'
                        ]
                    ],
                    ticket                : [
                        ticketType                : 'Servicenow',
                        changeEnvironment         : 'prod',
                        credentialsId             : 'adjudicator_snow', // add to jenkins creds
                        title                     : 'SCT0000600',
                        cmdb_ci                   : 'Adjudicator', // check status of CI
                        requested_by              : REQUESTED_BY,
                        assigned_to               : ASSIGNED_TO,
                        plannedDuration           : 60,
                        u_emergency_contact_person: ASSIGNED_TO,
                        u_emergency_contact_number: 1234567890
                    ]
                ]
            ]
        }


        // we must always have a baseCloudName pod, even if there's no specified phases because the root pod is
        // independent of the phase and podGroup is unknown at the point it is created
        then:
        assert psc.podSelector.podTemplates.size() == 2
        assert psc.podSelector.podTemplates['golang'].contains('"name":"golangvubi9-go-120"')
        assert psc.podSelector.podTemplates['adjudicator-openshift-devops1'].contains('"name":"epf-kanikovv1160-debug-51325"')
        assert !psc.podSelector.podTemplates['adjudicator-openshift-devops1'].contains('"name":"golangvubi9-go-120"')
        assert !psc.podSelector.podTemplates['golang'].contains('"name":"epf-kanikovv1160-debug-51325"')
    }

    def '''verify cloudName is propagated as expected'''() {
        given:
        String containerImage = 'enterprise-devops/aws-d-megatainer'
        String containerVersion = 'latest'
        String cloudNameEKS = 'prs-eks-jenkins-cluster-prod'
        String deployerRole = 'Enterprise/PRSGATEKEEPER'
        String environment
        String awsAccountNumber
        boolean isProductionDeployment = false

        if (env.branchName == 'main') {
            environment = 'prod'
            awsAccountNumber = '647891403458'
            isProductionDeployment = true
        } else if (env.branchName == 'test') {
            environment = 'test'
            awsAccountNumber = '228281547536'
        } else {
            environment = 'dev'
            awsAccountNumber = '997499886925'
        }

        when:
        cignaBuildFlow {
            cloudName = 'prs-openshift-devops1'
            githubConnectionName = 'cigna-github'
            complianceQualityLevel = "Bronze"
            phases = [
                [
                    buildType       : 'plz',
                    container       : [
                        image  : "${containerImage}",
                        version: "${containerVersion}",
                    ],
                    branchPattern   : '.*',
                    sdlcEnvironment : 'Non-Prod',
                    runInAWS        : false,
                    cloudName       : 'prs-openshift-devops1',
                    checkmarxEnabled: false,
                    checkmarx       : [
                        credentialsId: 'checkmarx-creds',
                        settings     : [
                            CX_PROJECT_TEAM_NAME  : 'specialtydataservices',
                            CX_EXCLUDE_FOLDER_LIST: 'plz-out, third_party, docs, test, base, datavant, results',
                            CX_INCL_EXCL_FILE_LIST: '!**/*.tf, !**/*.hcl, !**/*.tfvars, !**/BUILD, !**/*.md, !**/*.MD, !**/*.tmpl, !**/*.sh, !**/test_*.py, !**/secrets.py',

                        ],
                    ],
                    sonarQube       : [
                        credentialsId    : 'sonar-prs-infra',
                        scannerProperties: [
                            'sonar.projectKey=prs-infra',
                            'sonar.projectName=prs-infra',
                            'sonar.analysis.ciid=CI',
                            'sonar.sources=./',
                            'sonar.inclusions=*',
                        ],
                    ],
                ],
                // Dev/Test deployment
                [
                    runInAWS              : true,
                    cloudName             : cloudNameEKS,
                    aws                   :
                        [
                            cloudServiceAccountName: 'jenkins-robot',
                            targetAccount          : awsAccountNumber,
                            accountRoleName        : deployerRole,
                            region                 : 'us-east-1'
                        ],
                    branchPattern         : '.*',
                    container             : [
                        image  : "${containerImage}",
                        version: "${containerVersion}"
                    ],
                    deploymentType        : 'plz',
                    alias                 : 'deploy',
                    extraArgs             : 'dev',
                    verbosityFlag         : '-vvv',
                    sdlcEnvironment       : environment,
                    isProductionDeployment: false
                ],
                // PROD deployment
                [
                    runInAWS              : true,
                    cloudName             : cloudNameEKS,
                    aws                   :
                        [
                            cloudServiceAccountName: 'jenkins-robot',
                            targetAccount          : awsAccountNumber,
                            accountRoleName        : deployerRole,
                            region                 : 'us-east-1'
                        ],
                    branchPattern         : 'main$',
                    container             : [
                        image  : "${containerImage}",
                        version: "${containerVersion}"
                    ],
                    deploymentType        : 'plz',
                    alias                 : 'deploy',
                    extraArgs             : 'test',
                    verbosityFlag         : '-vvv',
                    sdlcEnvironment       : environment,
                    isProductionDeployment: isProductionDeployment
                ],
            ]
        }

        then:
        assert psc.podSelector.podTemplates.size() == 2
    }

    def '''overriding linting phase container updates containerName correctly'''() {
        given:
        String containerImage = 'enterprise-devops/aws-d-slim'

        String containerVersion = 'latest'

        String branchName = 'develop'
        String edpDeployerRole = "TEAM/DEPLOYER-DMP"

        String cloudNameOS = "dmp-prospect-openshift-devops1"
        String cloudNameEKS = "dmp-prospect-eks-jenkins-cluster-prod"

        String envName
        String awsAccountNumber
        String cloudServiceAccountName
        boolean isProductionDeployment
        def runTheseStages = []

        if (branchName == 'master') {
            envName = 'prod'
            isProductionDeployment = true
            awsAccountNumber = '423498800552'
            cloudServiceAccountName = 'jenkins-robot-prod'
        } else if (branchName == 'release') {
            envName = 'test'
            isProductionDeployment = false
            awsAccountNumber = '320637861154'
            cloudServiceAccountName = 'jenkins-robot-test'
        } else {
            envName = 'dev'
            isProductionDeployment = false
            awsAccountNumber = '643101592424'
            cloudServiceAccountName = 'jenkins-robot-dev'
        }


        def extraCredentials = [
            string(
                credentialsId: "test",
                variable: 'TF_VAR_tdv_password'
            )
        ]

// databricks module(s) to deploy
        List<String> modules = [
            '//module/databricks/data-axle',
            '//module/databricks/common',
            '//module/databricks/esi',
        ]

// set build description
        currentBuild.description = modules.toString().replace("[", "").replace("]", "").replace(", ", "<br>")

/**
 Linting Phase.
 */
        def lintingStage = [
            lintingTypes    : [
                'plz': [
                    verbosityFlag: '-vvv',
                    container    : [
                        image  : containerImage,
                        version: containerVersion,
                    ],
                ]
            ],
            branchPattern   : '.*',
            sdlcEnvironment : "${envName}",
            runInAWS        : false,
            cloudName       : "${cloudNameOS}",
            extraCredentials: extraCredentials
        ]

/**
 Building phase with checkmarx and sonarqube.
 */
        def buildStage = [
            branchPattern   : '.*',
            buildType       : 'plz',
            container       : [
                image          : "${containerImage}",
                version        : "${containerVersion}",
                imagePullPolicy: "Always"
            ],
            sonarEnabled    : true,
            sonarQube       : [
                credentialsId       : 'sonarqube-service-id',
                sonarIncludesPattern: '\\.\\/lib\\/.*',
                scannerProperties   : [
                    'sonar.analysis.ciid=dmp-prospect',
                    'sonar.projectKey=dmp-prospect',
                    'sonar.projectName=dmp-prospect',
                    'sonar.sources=./',
                    'sonar.inclusions=*.py',
                ],
            ],
            checkmarxEnabled: true,
            checkmarx       : [
                credentialsId: 'CHECKMARX_DMP',
                settings     : [
                    CX_PROJECT_TEAM_NAME: 'DMP',
                    CX_PROJECT_NAME     : 'dmp-prospect',
                    CX_SCAN_TYPE        : 'Scan'
                ]
            ],
            sdlcEnvironment : "${envName}",
            runInAWS        : false,
            cloudName       : "${cloudNameOS}",
            extraCredentials: extraCredentials
        ]


/**
 Terraform planing phase.
 */
        def terraformDeployStage = [
            runInAWS              : true,
            cloudName             : "${cloudNameEKS}",
            ticketCloudName       : "${cloudNameOS}",
            aws                   :
                [
                    cloudServiceAccountName: "${cloudServiceAccountName}",
                    region                 : 'us-east-1',
                    accountRoleName        : "${edpDeployerRole}",
                    targetAccount          : "${awsAccountNumber}",
                ],
            branchPattern         : '.*',
            container             : [
                image          : "${containerImage}",
                version        : "${containerVersion}",
                imagePullPolicy: "Always"
            ],
            deploymentType        : 'plz',
            alias                 : 'deploy',
            modules               : modules,
            extraArgs             : "${envName}",
            verbosityFlag         : '-vvv',
            sdlcEnvironment       : "${envName}",
            isProductionDeployment: isProductionDeployment,
            extraCredentials      : extraCredentials
        ]

        runTheseStages += lintingStage
        runTheseStages += buildStage
        runTheseStages += terraformDeployStage

        when:
        cignaBuildFlow {
            cloudName = "${cloudNameEKS}"
            correlationStrategy = 'git_commit'
            githubConnectionName = 'cigna-github'
            complianceQualityLevel = "Bronze"
            additionalProperties = [
                disableConcurrentBuilds(),
            ]
            ticketCloudName = "${cloudNameOS}"
            phases = runTheseStages
        }
        then:
        psc.podSelector.podTemplates.size() == 2
        assert !psc.podSelector.podTemplates[cloudNameEKS].contains('aws-d-megatainer')
        assert !psc.podSelector.podTemplates[cloudNameOS].contains('aws-d-megatainer')
        assert psc.podSelector.podTemplates[cloudNameEKS].contains('aws-d-slim')
        assert psc.podSelector.podTemplates[cloudNameOS].contains('aws-d-slim')
    }

    def """validate podgroups when qroupby unique/phase-type with ticketcloud in deployment"""() {
        given:
        String containerImage = 'enterprise-devops/aws-d-slim'

        String containerVersion = 'latest'
        String awscontainerImage = 'enterprise-devops/aws-d-megatainer'

        String awscontainerVersion = 'latest'

        String branchName = 'develop'
        String edpDeployerRole = "TEAM/DEPLOYER-DMP"

        String cloudNameOS = "dmp-prospect-openshift-devops1"
        String cloudNameEKS = "dmp-prospect-eks-jenkins-cluster-prod"

        String envName
        String awsAccountNumber
        String cloudServiceAccountName
        boolean isProductionDeployment
        def runTheseStages = []

        if (branchName == 'master') {
            envName = 'prod'
            isProductionDeployment = true
            awsAccountNumber = '423498800552'
            cloudServiceAccountName = 'jenkins-robot-prod'
        } else if (branchName == 'release') {
            envName = 'test'
            isProductionDeployment = false
            awsAccountNumber = '320637861154'
            cloudServiceAccountName = 'jenkins-robot-test'
        } else {
            envName = 'dev'
            isProductionDeployment = false
            awsAccountNumber = '643101592424'
            cloudServiceAccountName = 'jenkins-robot-dev'
        }


        def extraCredentials = [
            string(
                credentialsId: "test",
                variable: 'TF_VAR_tdv_password'
            )
        ]

// databricks module(s) to deploy
        List<String> modules = [
            '//module/databricks/data-axle',
            '//module/databricks/common',
            '//module/databricks/esi',
        ]

// set build description
        currentBuild.description = modules.toString().replace("[", "").replace("]", "").replace(", ", "<br>")

/**
 Linting Phase.
 */
        def lintingStage = [
            lintingTypes    : [
                'plz': [
                    verbosityFlag: '-vvv',
                    container    : [
                        image  : containerImage,
                        version: containerVersion,
                    ],
                ]
            ],
            branchPattern   : '.*',
            sdlcEnvironment : "${envName}",
            runInAWS        : false,
            cloudName       : "${cloudNameOS}",
            extraCredentials: extraCredentials
        ]

/**
 Building phase with checkmarx and sonarqube.
 */
        def buildStage = [
            branchPattern   : '.*',
            buildType       : 'plz',
            container       : [
                image          : "${containerImage}",
                version        : "${containerVersion}",
                imagePullPolicy: "Always"
            ],
            sonarEnabled    : true,
            sonarQube       : [
                credentialsId       : 'sonarqube-service-id',
                sonarIncludesPattern: '\\.\\/lib\\/.*',
                scannerProperties   : [
                    'sonar.analysis.ciid=dmp-prospect',
                    'sonar.projectKey=dmp-prospect',
                    'sonar.projectName=dmp-prospect',
                    'sonar.sources=./',
                    'sonar.inclusions=*.py',
                ],
            ],
            checkmarxEnabled: true,
            checkmarx       : [
                credentialsId: 'CHECKMARX_DMP',
                settings     : [
                    CX_PROJECT_TEAM_NAME: 'DMP',
                    CX_PROJECT_NAME     : 'dmp-prospect',
                    CX_SCAN_TYPE        : 'Scan'
                ]
            ],
            sdlcEnvironment : "${envName}",
            runInAWS        : false,
            cloudName       : "${cloudNameOS}",
            extraCredentials: extraCredentials
        ]


/**
 Terraform planing phase.
 */
        def terraformDeployStage = [
            runInAWS              : true,
            cloudName             : "${cloudNameEKS}",
            ticketCloudName       : "${cloudNameOS}",
            aws                   :
                [
                    cloudServiceAccountName: "${cloudServiceAccountName}",
                    region                 : 'us-east-1',
                    accountRoleName        : "${edpDeployerRole}",
                    targetAccount          : "${awsAccountNumber}",
                ],
            branchPattern         : '.*',
            container             : [
                image          : "${awscontainerImage}",
                version        : "${awscontainerVersion}",
                imagePullPolicy: "Always"
            ],
            deploymentType        : 'terraform',
            alias                 : 'deploy',
            modules               : modules,
            extraArgs             : "${envName}",
            terraform             : [version: '1.2.3'],
            terragrunt            : [version: '0.37.4'],
            verbosityFlag         : '-vvv',
            sdlcEnvironment       : "${envName}",
            isProductionDeployment: isProductionDeployment,
            extraCredentials      : extraCredentials,
            directories           : [
                [
                    directory: './module/aws/lambda',
                    extraArgs: [
                        init : '',
                        plan : '',
                        apply: ''
                    ],
                    tfVars   : [
                        env           : "${envName}",
                        region        : 'us-east-1',
                        account_number: "${awsAccountNumber}",
                    ],
                    useAll   : true,
                ]
            ],
            testing               : [
                [
                    testType           : 'terratest',
                    basicAuth          : 'adjudicator-newman-testcreds',
                    runInAWS           : true,
                    branchPattern      : '.*',
                    runBeforeDeployment: true,
                    container          : [
                        image: "${awscontainerImage}",
                    ],
                    aws                : [
                        cloudServiceAccountName: "${cloudServiceAccountName}",
                        region                 : 'us-east-1',
                        accountRoleName        : "${edpDeployerRole}",
                        targetAccount          : "${awsAccountNumber}",
                    ],
                    testDirectories    : [
                        './test',
                    ]
                ],
            ],
            ticket                : [
                ticketType: 'Servicenow',
                title     : 'CHG0000000',
            ],
        ]

        runTheseStages += lintingStage
        runTheseStages += buildStage
        runTheseStages += terraformDeployStage

        when:
        cignaBuildFlow {
            cloudName = "${cloudNameEKS}"
            correlationStrategy = 'git_commit'
            githubConnectionName = 'cigna-github'
            complianceQualityLevel = "Bronze"
            groupBy = groupBySelector
            additionalProperties = [
                disableConcurrentBuilds(),
            ]
            phases = runTheseStages
        }
        then:
        if (groupBySelector == 'phase-type') {
            assert psc.podSelector.byPodGroup() == ['linting', 'build', 'deploy', 'nested']
        } else if (groupBySelector == 'unique') {
            assert psc.podSelector.byPodGroup().size() == 4
        }
        where:
        groupBySelector << ['phase-type', 'unique']
    }


    def '''When an unprivileged phase in a privileged pod requires access to managed files, it is moved to a managed pod'''() {
        given:
        explicitlyMockPipelineVariable('bearerToken')
        explicitlyMockPipelineVariable('AWS_FED_PASSWORD')
        explicitlyMockPipelineVariable('AWS_FED_USERNAME')
        explicitlyMockPipelineVariable('QUAY_TOKEN')
        when:
        String containerImage = 'enterprise-devops/aws-d-megatainer'
        String containerVersion = '1.0.12'

        String branchName = 'feature/api-tests'
        String buildNumber = '11'
        String envName
        String credentialsId
        boolean isProductionDeployment = false

        if (branchName == 'master') {
            envName = 'prod'
            credentialsId = 'SAMLSVPMOSJenkinsPRDADM'
            isProductionDeployment = false
        } else if (branchName == 'test') {
            envName = 'test'
            credentialsId = 'SAMLSVPMOSJenkinsTSTADM'
            isProductionDeployment = false
        } else if (branchName == 'develop') {
            envName = 'dev'
            credentialsId = 'SAMLSVTMOSJenkinsADMIN'
            isProductionDeployment = false
        } else if (branchName.contains('feature/etl-')) {
            envName = 'dev'
            credentialsId = 'SAMLSVTMOSJenkinsADMIN'
            isProductionDeployment = false
        } else if (branchName.contains('feature/api-')) {
            envName = 'dev'
            credentialsId = 'SAMLSVTMOSJenkinsADMIN'
            isProductionDeployment = false
        } else {
            envName = 'dev'
            credentialsId = 'SAMLSVTMOSJenkinsADMIN'
            isProductionDeployment = false
        }

        cignaBuildFlow {
            featureFlags = [
                nonProdComplianceChecks: false
            ]
            cloudName = 'cigna-us-da-mos-data-openshift-devops1'
            githubConnectionName = 'cigna-github'
            logLevel = 'INFO'
            mattermostWebhook = 'https://mm.sys.cigna.com/hooks/ju666e9ijpdkbm84upo6n8g8ic'
            additionalProperties = [
                disableConcurrentBuilds()
            ]

            phases = [
                [
                    lintingTypes : [
                        'plz': [
                            verbosityFlag: '-vvv',
                            container    : [
                                image  : "${containerImage}",
                                version: "${containerVersion}",
                                cpu    : 300,
                                memory : 300,
                            ],
                        ]
                    ],

                    branchPattern: '.*',
                ],
                /*
                        We wanted to execute below phase on all branches except master. Run scans before deploying the application to PROD
                    */
                [
                    buildType    : 'plz',
                    container    : [
                        image  : "${containerImage}",
                        version: "${containerVersion}"
                    ],
                    branchPattern: '(?!master$).*',
                    awsFed       : [
                        credentialsId: credentialsId,
                        callPlzFed   : false
                    ],
                    sonarQube    : [
                        credentialsId    : 'sonarqube-service-id',
                        mainBranch       : 'test',
                        scannerProperties: [
                            'sonar.projectKey=dae-mos-data',
                            'sonar.projectName=dae-mos-data',
                            'sonar.analysis.ciid=CI-dae-mos-data',
                            'sonar.sources=.',
                            'sonar.inclusions=**/*.sql',
                        ],
                    ],
                    checkmarx    : [
                        credentialsId: 'checkmarx-service-creds',
                        settings     : [
                            CX_PROJECT_TEAM_NAME  : 'mgmtopsys',
                            CX_SCAN_COMMENT       : "Pipeline:${buildNumber} - Branch:${branchName}",
                            CX_EXCLUDE_FOLDER_LIST: 'plz-out, lib, commands, env-config, kms-policy, third_party, utilities',
                            CX_INCL_EXCL_FILE_LIST: '!**/*.tf, !**/*.hcl, !**/*.tfvars',
                            CX_SCAN_TYPE          : 'AsyncScan',
                        ],
                    ],
                ],
                [
                    freestyleType         : 'Create Workspace DB',
                    branchPattern         : '.*',
                    extraCredentials      : [
                        usernamePassword(
                            credentialsId: 'SAMLSVTMOSJenkinsADMIN',
                            passwordVariable: 'SAML2AWS_PASSWORD',
                            usernameVariable: 'SAML2AWS_USERNAME'
                        )
                    ],
                    extraConfigs          : [
                        configFile(
                            fileId: 'DATA_CP_ETL_TABLES_MAP',
                            variable: 'DATA_CP_ETL_TABLES_MAP'
                        )
                    ],
                    withEnv               : [
                        "ARTIFACTS_REPO=dae-mos-artifacts-${envName}"
                    ],
                    sdlcEnvironment       : 'dev',
                    container             : [
                        image  : "${containerImage}",
                        version: "${containerVersion}",
                        cpu    : 1000,
                        memory : 2000,
                    ],
                    isProductionDeployment: isProductionDeployment,
                    script                : """
                    sh /usr/local/bin/adhoc-perms
                    # Skips printing command execution in the console
                    set +x
                    export TF_VAR_DATA_CP_ETL_TABLES_MAP=\$(cat \${DATA_CP_ETL_TABLES_MAP})
                    set -x
                    plz fed dev
                    plz deploy_workspace ec2 dev --show_all_output -vvv
            """ //required
                ],
                /*
                        Branch pattern is opposite of how we defined in other stages.
                        We wanted to execute below phase on all branches except develop, test and master
                        Using below branch pattern, we don't have to create workspace for making minor change to infrastructure and don't need to follow branching pattern for plan
                        Plan is using freestyle job so that we have control over the Stage name in Jenkins
                        Plan is run only on Dev Default workspace to make sure there is no syntax errors in TF before merging into Develop
                        Use adhoc-perms in the script to fix "unknown userid" exception
                    */
                [
                    freestyleType         : 'Terraform Plan on Dev',
                    branchPattern         : '^(?!develop$|test$|master$).*',
                    extraCredentials      : [
                        usernamePassword(
                            credentialsId: 'SAMLSVTMOSJenkinsADMIN',
                            passwordVariable: 'SAML2AWS_PASSWORD',
                            usernameVariable: 'SAML2AWS_USERNAME'
                        )
                    ],
                    sdlcEnvironment       : 'dev',
                    container             : [
                        image  : "${containerImage}",
                        version: "${containerVersion}",
                        cpu    : 1000,
                        memory : 2000,
                    ],
                    isProductionDeployment: isProductionDeployment,
                    script                : """
                    sh /usr/local/bin/adhoc-perms

                    plz fed dev
                    plz plan_all dev --show_all_output -vvv
            """ //required
                ],
                [
                    awsFed                : [
                        credentialsId: credentialsId,
                        callPlzFed   : true
                    ],
                    branchPattern         : 'develop|test|master',
                    container             : [
                        image  : "${containerImage}",
                        version: "${containerVersion}",
                        cpu    : 2000,
                        memory : 2500,
                    ],
                    withEnv               : [
                        "ARTIFACTS_REPO=dae-mos-artifacts-${envName}"
                    ],
                    deploymentType        : 'plz',
                    alias                 : 'deploy',
                    extraArgs             : envName,
                    verbosityFlag         : '-vvv',
                    sdlcEnvironment       : envName,
                    isProductionDeployment: isProductionDeployment
                ],
                [
                    branchPattern     : '.*',
                    packagingType     : 'kaniko',
                    image             : [
                        org : 'da-mos-cloud',
                        name: 'test',
                        tags: [
                            [
                                tag   : 'tag',
                                expire: false
                            ],
                        ]
                    ],
                    quay              : [
                        credentialsId        : 'cred',
                        apiTokenCredentialsId: 'apicred'
                    ],
                    conftestValidation: false
                ],
            ]
        }

        then:
        psc.podSelector.podTemplates.size() == 2
        assert psc.podSelector.podTemplates['managed'].contains('enterprise-devops/aws-d-megatainer')
    }


    def '''Verify that phases with nested testing phases calculate their groupID and groupBy correctly.'''() {
        given:
        explicitlyMockPipelineVariable('userId')
        explicitlyMockPipelineVariable('bearerToken')
        explicitlyMockPipelineVariable('openshiftSecret')
        explicitlyMockPipelineVariable('QUAY_TOKEN')
        getPipelineMock("sh")({
            if (!(it instanceof Map)) {
                false
            } else {
                it?.script ==~ /ls -l .* | grep ^d | awk '\{print .*}'/
            }
        }) >> "file_one\nfile_two"

        def version = 'test'
        when:
        cignaBuildFlow {
            groupBy = groupBySelector
            githubConnectionName = 'github'
            commitStatusName = 'Adjudicator App Build'
            cloudName = "adjudicator-openshift-devops1"
            webexTeamsRoom = 'a3b03650-838b-11ec-b8ff-cde520824ca3'
            compliance:
            [
                audit: [
                    buildCount: 5,
                ]
            ]
            releaseBranchPattern = 'main'
            checkmarxEnabled = true
            sonarEnabled = true
            phases = [
                [
                    branchPattern: '.*',
                    lintingTypes : [
                        'go': [
                            timeout: '8m',
                        ],
                    ]
                ],
                [
                    buildType           : 'Go',
                    golang              : [
                        apiTokenId: 'ADJUDICATOR_GITHUB_TOKEN',
                        tokenType : 'GitHub',
                    ],
                    branchPattern       : '.*',
                    releaseBranchPattern: 'none', // do not need to package
                    verbosityFlag       : '-vvv',
                    sonarQube           : [
                        credentialsId    : 'Sonarqube-A-Scanning-Token',
                        scannerProperties: [
                            'sonar.projectKey=theAdjudicator',
                            'sonar.projectName=theAdjudicator',
                            'sonar.sources=.',
                            'sonar.exclusions=**/*_test.go,**/vendor/**',
                            'sonar.tests=.',
                            'sonar.test.inclusions=**/*_test.go',
                            'sonar.test.exclusions=**/vendor/**',
                            'sonar.cfamily.cache.enabled=false',
                            'sonar.c.file.suffixes=-',
                            'sonar.cpp.file.suffixes=-',
                            'sonar.objc.file.suffixes=-'
                        ]
                    ],
                    checkmarx           : [
                        credentialsId: 'checkmarx-devops-prod',
                        settings     : [
                            CX_PROJECT_TEAM_NAME  : 'devops',
                            CX_PROJECT_NAME       : 'adjudicator',
                            CX_EXCLUDE_FOLDER_LIST: 'vendor',
                            CX_INCL_EXCL_FILE_LIST: '!**/*_test.go',
                        ],
                    ],
                ],
                [
                    packagingType       : 'kaniko',
                    branchPattern       : '.*',
                    releaseBranchPattern: 'main',
                    conftestValidation  : false, // XXX.cnm - temporary until correct minideb version tags available
                    dockerRegistry      : 'registry.cigna.com',
                    image               : [
                        org         : 'adjudicator-devops',
                        name        : 'adjudicator',
                        securityScan: true,
                        buildArgs   : [
                            ADJUDICATOR_DNS_SERVICE: "adjudicator-dns-service.adjudicator-nonproduction.svc.cluster.local",
                            ADJUDICATOR_VERSION    : "$version",
                            DEPLOY_ENVIRONMENT     : "NonProd",
                            PROMETHEUS_ENABLED     : "true",
                            OPENTELEMETRY_ENABLED  : "false",
                            REDIS_ADDRESS          : "redis-master.adjudicator-nonproduction.svc.cluster.local",
                        ],
                        tags        : [
                            [
                                tag   : 'dev',
                                expire: true
                            ],
                            [
                                tag   : "dev-$version",
                                expire: false
                            ]
                        ]
                    ],
                    quay                : [
                        credentialsId: 'quay_adjudicator_token_prod'
                    ]
                ],
                [
                    packagingType     : 'kaniko',
                    branchPattern     : '.*',
                    conftestValidation: false, // XXX.cnm - temporary until correct minideb version tags available
                    dockerRegistry    : 'registry.cigna.com',
                    image             : [
                        org         : 'adjudicator-devops',
                        name        : 'adjudicator',
                        securityScan: true,
                        buildArgs   : [
                            ADJUDICATOR_DNS_SERVICE: "adjudicator-dns-service.adjudicator-production.svc.cluster.local",
                            ADJUDICATOR_VERSION    : "$version",
                            PROMETHEUS_ENABLED     : "true",
                            OPENTELEMETRY_ENABLED  : "false",
                            DEPLOY_ENVIRONMENT     : "Prod",
                            REDIS_ADDRESS          : "redis-master.adjudicator-production.svc.cluster.local",
                        ],
                        tags        : [
                            [
                                tag   : 'latest',
                                expire: true
                            ],
                            [
                                tag   : 'prod',
                                expire: false
                            ],
                            [
                                tag   : "$version",
                                expire: false
                            ],
                            [
                                tag   : "prod-$version",
                                expire: false
                            ]
                        ]
                    ],
                    quay              : [
                        credentialsId: 'quay_adjudicator_token_prod'
                    ]
                ],
                [
                    deploymentType        : 'openshift',
                    containerImage        : 'enterprise-devops/helm-cli',
                    containerVersion      : 'v3.5.3',
                    branchPattern         : '.*',
                    extraCredentials      : [
                        string(
                            credentialsId: 'SVP_ADJUDICATOR_INT',
                            variable: 'SVP_ADJUDICATOR_INT'
                        ),
                        string(
                            credentialsId: 'PRD_ADJUDICATOR_ARTIFACTORY_TOKEN',
                            variable: 'ADJUDICATOR_ARTIFACTORY_TOKEN'
                        ),
                        string(
                            credentialsId: 'ADJUDICATOR_SNOW_INT_ID',
                            variable: 'ADJUDICATOR_SNOW_INT_ID'
                        ),
                        string(
                            credentialsId: 'SVT_ADJUDICATOR_INT',
                            variable: 'SVT_ADJUDICATOR_INT'
                        ),
                        string(
                            credentialsId: 'ADJUDICATOR_GITHUB_TOKEN',
                            variable: 'GITHUB_ACCESS_TOKEN'
                        ),
                        string(
                            credentialsId: 'SPLUNK_HEC_TOKEN',
                            variable: 'SPLUNK_HEC_TOKEN'
                        ),
                        string(
                            credentialsId: 'REDIS_NONPROD_PASSWORD',
                            variable: 'REDIS_SECURE'
                        ),
                        usernamePassword(
                            credentialsId: 'adjudicator-ldap-auth-internal',
                            usernameVariable: 'UNUSED_VALUE',
                            passwordVariable: 'LDAP_PWD'
                        ),
                    ],
                    sdlcEnvironment       : 'dev_oscpv4',
                    isProductionDeployment: false,
                    openshift             : [
                        url          : 'https://api.gp-2-nonprod.openshift.cignacloud.com:6443', // This url may need to change
                        credentialsId: 'SVP_ADJUDICATOR_INT',
                        project      : 'adjudicator-nonproduction',
                        user         : 'SVP_ADJUDICATOR_INT'
                    ],
                    helm                  : [
                        version: '3.5.3',
                        command: 'helm upgrade the-adjudicator ./deploy/adjudicator-helm' +
                            ' -f ./deploy/adjudicator-helm/nonprod/values.yaml' +
                            " --set-string revision=dev-$version" +
                            ' --set-string revisionHash=$GIT_COMMIT' +
                            " --set-string imageTag=dev-$version" +
                            ' --set-string env.secret.ADJUDICATOR_ARTIFACTORY_TOKEN=$ADJUDICATOR_ARTIFACTORY_TOKEN' +
                            ' --set-string env.secret.ADJUDICATOR_SNOW_INT_ID=$ADJUDICATOR_SNOW_INT_ID' +
                            ' --set-string env.secret.SVP_ADJUDICATOR_INT=$SVP_ADJUDICATOR_INT' +
                            ' --set-string env.secret.SVT_ADJUDICATOR_INT=$SVT_ADJUDICATOR_INT' +
                            ' --set-string env.secret.GITHUB_ACCESS_TOKEN=$GITHUB_ACCESS_TOKEN' +
                            ' --set-string env.secret.SPLUNK_HEC_TOKEN=$SPLUNK_HEC_TOKEN' +
                            ' --set-string env.secret.REDIS_SECURE=$REDIS_SECURE' +
                            ' --set-string env.secret.LDAP_PWD=$LDAP_PWD' +
                            ' --wait --install --cleanup-on-fail --history-max 1'
                    ],
                    testing               : [
                        [
                            testType       : 'newman',
                            basicAuth      : 'adjudicator-newman-testcreds',
                            collectionsPath: 'tests/newman'
                        ],
                        [
                            testType: "JMeter",
                            planPath: "tests/jmeter/PerformanceTests.jmx",
                            args    : '-JSERVER_NAME=adjudicator.apps.gp-2-nonprod.openshift.cignacloud.com -JAUTH_PWD=$LDAP_PWD -JNUM_ITERATIONS=10'
                        ]
                    ]
                ],
                [
                    deploymentType        : 'openshift',
                    branchPattern         : '.*',
                    extraCredentials      : [
                        string(
                            credentialsId: 'SVP_ADJUDICATOR_INT',
                            variable: 'SVP_ADJUDICATOR_INT'
                        ),
                        string(
                            credentialsId: 'PRD_ADJUDICATOR_ARTIFACTORY_TOKEN',
                            variable: 'ADJUDICATOR_ARTIFACTORY_TOKEN'
                        ),
                        string(
                            credentialsId: 'ADJUDICATOR_SNOW_INT_ID',
                            variable: 'ADJUDICATOR_SNOW_INT_ID'
                        ),
                        string(
                            credentialsId: 'SVT_ADJUDICATOR_INT',
                            variable: 'SVT_ADJUDICATOR_INT'
                        ),
                        string(
                            credentialsId: 'ADJUDICATOR_GITHUB_TOKEN',
                            variable: 'GITHUB_ACCESS_TOKEN'
                        ),
                        string(
                            credentialsId: 'SPLUNK_HEC_TOKEN',
                            variable: 'SPLUNK_HEC_TOKEN'
                        ),
                        string(
                            credentialsId: 'REDIS_PROD_PASSWORD',
                            variable: 'REDIS_SECURE'
                        ),
                        usernamePassword(
                            credentialsId: 'adjudicator-ldap-auth-internal',
                            usernameVariable: 'UNUSED_VALUE',
                            passwordVariable: 'LDAP_PWD'
                        ),
                    ],
                    sdlcEnvironment       : 'prod_oscpv4',
                    isProductionDeployment: true,
                    endorse               : [
                        images: ['adjudicator-devops/adjudicator:' + "prod-$version"],
                    ],
                    openshift             : [
                        url          : 'https://api.gp-2-prod.openshift.cignacloud.com:6443', // This url may need to change
                        credentialsId: 'SVP_ADJUDICATOR_INT_PROPER',
                        project      : 'adjudicator-production',
                        user         : 'SVP_ADJUDICATOR_INT'
                    ],
                    helm                  : [
                        version: '3.5.3',
                        command: 'helm upgrade the-adjudicator ./deploy/adjudicator-helm' +
                            ' -f ./deploy/adjudicator-helm/prod/values.yaml' +
                            " --set-string revision=prod-$version" +
                            ' --set-string revisionHash=$GIT_COMMIT' +
                            " --set-string imageTag=prod-$version" +
                            ' --set-string env.secret.ADJUDICATOR_ARTIFACTORY_TOKEN=$ADJUDICATOR_ARTIFACTORY_TOKEN' +
                            ' --set-string env.secret.ADJUDICATOR_SNOW_INT_ID=$ADJUDICATOR_SNOW_INT_ID' +
                            ' --set-string env.secret.SVP_ADJUDICATOR_INT=$SVP_ADJUDICATOR_INT' +
                            ' --set-string env.secret.SVT_ADJUDICATOR_INT=$SVT_ADJUDICATOR_INT' +
                            ' --set-string env.secret.GITHUB_ACCESS_TOKEN=$GITHUB_ACCESS_TOKEN' +
                            ' --set-string env.secret.SPLUNK_HEC_TOKEN=$SPLUNK_HEC_TOKEN' +
                            ' --set-string env.secret.REDIS_SECURE=$REDIS_SECURE' +
                            ' --set-string env.secret.LDAP_PWD=$LDAP_PWD' +
                            ' --wait --install --cleanup-on-fail --history-max 1'
                    ],
                    testing               : [
                        [
                            testType       : 'newman',
                            basicAuth      : 'adjudicator-newman-testcreds',
                            collectionsPath: 'tests/newman'
                        ],
                        [
                            testType: "JMeter",
                            planPath: "tests/jmeter/PerformanceTests.jmx",
                            args    : '-JSERVER_NAME=adjudicator.cigna.com -JAUTH_PWD=$LDAP_PWD -JNUM_ITERATIONS=1'
                        ]
                    ],
                    ticket                : [
                        ticketType: 'Servicenow',
                        title     : 'CHG0000000',
                    ],
                ]
            ]
        }
        then:
        if (groupBySelector == 'phase-type') {
            assert psc.podSelector.byPodGroup() == ['linting', 'build', 'package', 'deploy']
            assert psc.podSelector.podTemplates['deploy'].contains('newmanvalpine')
            assert !psc.podSelector.podTemplates['linting'].contains('newmanvalpine')
            assert !psc.podSelector.podTemplates['build'].contains('newmanvalpine')
            assert !psc.podSelector.podTemplates['package'].contains('newmanvalpine')
        } else if (groupBySelector == 'unique') {
            assert psc.podSelector.byPodGroup().size() == 6
        }
        where:
        groupBySelector << ['phase-type', 'unique']
    }

    def '''Verify that phases with that override their default groupID are grouped with the correct phase-type.'''() {
        given:
        explicitlyMockPipelineVariable('userId')
        explicitlyMockPipelineVariable('bearerToken')
        explicitlyMockPipelineVariable('openshiftSecret')
        explicitlyMockPipelineVariable('QUAY_TOKEN')
        getPipelineMock("sh")({
            if (!(it instanceof Map)) {
                false
            } else {
                it?.script ==~ /ls -l .* | grep ^d | awk '\{print .*}'/
            }
        }) >> "file_one\nfile_two"


        def version = 'test'
        when:
        cignaBuildFlow {
            groupBy = 'phase-type'
            githubConnectionName = 'github'
            commitStatusName = 'Adjudicator App Build'
            cloudName = "adjudicator-openshift-devops1"
            webexTeamsRoom = 'a3b03650-838b-11ec-b8ff-cde520824ca3'
            compliance:
            [
                audit: [
                    buildCount: 5,
                ]
            ]
            releaseBranchPattern = 'main'
            checkmarxEnabled = true
            sonarEnabled = true
            phases = [
                [
                    branchPattern: '.*',
                    groupID      : lintingGroupID,
                    lintingTypes : [
                        'go': [
                            timeout: '8m',
                        ],
                    ]
                ],
                [
                    buildType           : 'Go',
                    groupID             : buildGroupID,
                    golang              : [
                        apiTokenId: 'ADJUDICATOR_GITHUB_TOKEN',
                        tokenType : 'GitHub',
                    ],
                    branchPattern       : '.*',
                    releaseBranchPattern: 'none', // do not need to package
                    verbosityFlag       : '-vvv',
                    sonarQube           : [
                        credentialsId    : 'Sonarqube-A-Scanning-Token',
                        scannerProperties: [
                            'sonar.projectKey=theAdjudicator',
                            'sonar.projectName=theAdjudicator',
                            'sonar.sources=.',
                            'sonar.exclusions=**/*_test.go,**/vendor/**',
                            'sonar.tests=.',
                            'sonar.test.inclusions=**/*_test.go',
                            'sonar.test.exclusions=**/vendor/**',
                            'sonar.cfamily.cache.enabled=false',
                            'sonar.c.file.suffixes=-',
                            'sonar.cpp.file.suffixes=-',
                            'sonar.objc.file.suffixes=-'
                        ]
                    ],
                    checkmarx           : [
                        credentialsId: 'checkmarx-devops-prod',
                        settings     : [
                            CX_PROJECT_TEAM_NAME  : 'devops',
                            CX_PROJECT_NAME       : 'adjudicator',
                            CX_EXCLUDE_FOLDER_LIST: 'vendor',
                            CX_INCL_EXCL_FILE_LIST: '!**/*_test.go',
                        ],
                    ],
                ],
                [
                    packagingType       : 'kaniko',
                    branchPattern       : '.*',
                    releaseBranchPattern: 'main',
                    conftestValidation  : false, // XXX.cnm - temporary until correct minideb version tags available
                    dockerRegistry      : 'registry.cigna.com',
                    image               : [
                        org         : 'adjudicator-devops',
                        name        : 'adjudicator',
                        securityScan: true,
                        buildArgs   : [
                            ADJUDICATOR_DNS_SERVICE: "adjudicator-dns-service.adjudicator-nonproduction.svc.cluster.local",
                            ADJUDICATOR_VERSION    : "$version",
                            DEPLOY_ENVIRONMENT     : "NonProd",
                            PROMETHEUS_ENABLED     : "true",
                            OPENTELEMETRY_ENABLED  : "false",
                            REDIS_ADDRESS          : "redis-master.adjudicator-nonproduction.svc.cluster.local",
                        ],
                        tags        : [
                            [
                                tag   : 'dev',
                                expire: true
                            ],
                            [
                                tag   : "dev-$version",
                                expire: false
                            ]
                        ]
                    ],
                    quay                : [
                        credentialsId: 'quay_adjudicator_token_prod'
                    ]
                ],
                [
                    packagingType     : 'kaniko',
                    branchPattern     : '.*',
                    conftestValidation: false, // XXX.cnm - temporary until correct minideb version tags available
                    dockerRegistry    : 'registry.cigna.com',
                    image             : [
                        org         : 'adjudicator-devops',
                        name        : 'adjudicator',
                        securityScan: true,
                        buildArgs   : [
                            ADJUDICATOR_DNS_SERVICE: "adjudicator-dns-service.adjudicator-production.svc.cluster.local",
                            ADJUDICATOR_VERSION    : "$version",
                            PROMETHEUS_ENABLED     : "true",
                            OPENTELEMETRY_ENABLED  : "false",
                            DEPLOY_ENVIRONMENT     : "Prod",
                            REDIS_ADDRESS          : "redis-master.adjudicator-production.svc.cluster.local",
                        ],
                        tags        : [
                            [
                                tag   : 'latest',
                                expire: true
                            ],
                            [
                                tag   : 'prod',
                                expire: false
                            ],
                            [
                                tag   : "$version",
                                expire: false
                            ],
                            [
                                tag   : "prod-$version",
                                expire: false
                            ]
                        ]
                    ],
                    quay              : [
                        credentialsId: 'quay_adjudicator_token_prod'
                    ]
                ],
                [
                    deploymentType        : 'openshift',
                    containerImage        : 'enterprise-devops/helm-cli',
                    containerVersion      : 'v3.5.3',
                    branchPattern         : '.*',
                    extraCredentials      : [
                        string(
                            credentialsId: 'SVP_ADJUDICATOR_INT',
                            variable: 'SVP_ADJUDICATOR_INT'
                        ),
                        string(
                            credentialsId: 'PRD_ADJUDICATOR_ARTIFACTORY_TOKEN',
                            variable: 'ADJUDICATOR_ARTIFACTORY_TOKEN'
                        ),
                        string(
                            credentialsId: 'ADJUDICATOR_SNOW_INT_ID',
                            variable: 'ADJUDICATOR_SNOW_INT_ID'
                        ),
                        string(
                            credentialsId: 'SVT_ADJUDICATOR_INT',
                            variable: 'SVT_ADJUDICATOR_INT'
                        ),
                        string(
                            credentialsId: 'ADJUDICATOR_GITHUB_TOKEN',
                            variable: 'GITHUB_ACCESS_TOKEN'
                        ),
                        string(
                            credentialsId: 'SPLUNK_HEC_TOKEN',
                            variable: 'SPLUNK_HEC_TOKEN'
                        ),
                        string(
                            credentialsId: 'REDIS_NONPROD_PASSWORD',
                            variable: 'REDIS_SECURE'
                        ),
                        usernamePassword(
                            credentialsId: 'adjudicator-ldap-auth-internal',
                            usernameVariable: 'UNUSED_VALUE',
                            passwordVariable: 'LDAP_PWD'
                        ),
                    ],
                    sdlcEnvironment       : 'dev_oscpv4',
                    isProductionDeployment: false,
                    openshift             : [
                        url          : 'https://api.gp-2-nonprod.openshift.cignacloud.com:6443', // This url may need to change
                        credentialsId: 'SVP_ADJUDICATOR_INT',
                        project      : 'adjudicator-nonproduction',
                        user         : 'SVP_ADJUDICATOR_INT'
                    ],
                    helm                  : [
                        version: '3.5.3',
                        command: 'helm upgrade the-adjudicator ./deploy/adjudicator-helm' +
                            ' -f ./deploy/adjudicator-helm/nonprod/values.yaml' +
                            " --set-string revision=dev-$version" +
                            ' --set-string revisionHash=$GIT_COMMIT' +
                            " --set-string imageTag=dev-$version" +
                            ' --set-string env.secret.ADJUDICATOR_ARTIFACTORY_TOKEN=$ADJUDICATOR_ARTIFACTORY_TOKEN' +
                            ' --set-string env.secret.ADJUDICATOR_SNOW_INT_ID=$ADJUDICATOR_SNOW_INT_ID' +
                            ' --set-string env.secret.SVP_ADJUDICATOR_INT=$SVP_ADJUDICATOR_INT' +
                            ' --set-string env.secret.SVT_ADJUDICATOR_INT=$SVT_ADJUDICATOR_INT' +
                            ' --set-string env.secret.GITHUB_ACCESS_TOKEN=$GITHUB_ACCESS_TOKEN' +
                            ' --set-string env.secret.SPLUNK_HEC_TOKEN=$SPLUNK_HEC_TOKEN' +
                            ' --set-string env.secret.REDIS_SECURE=$REDIS_SECURE' +
                            ' --set-string env.secret.LDAP_PWD=$LDAP_PWD' +
                            ' --wait --install --cleanup-on-fail --history-max 1'
                    ],
                    testing               : [
                        [
                            testType       : 'newman',
                            basicAuth      : 'adjudicator-newman-testcreds',
                            collectionsPath: 'tests/newman'
                        ],
                        [
                            testType: "JMeter",
                            planPath: "tests/jmeter/PerformanceTests.jmx",
                            args    : '-JSERVER_NAME=adjudicator.apps.gp-2-nonprod.openshift.cignacloud.com -JAUTH_PWD=$LDAP_PWD -JNUM_ITERATIONS=10'
                        ]
                    ]
                ],
                [
                    deploymentType        : 'openshift',
                    branchPattern         : '.*',
                    extraCredentials      : [
                        string(
                            credentialsId: 'SVP_ADJUDICATOR_INT',
                            variable: 'SVP_ADJUDICATOR_INT'
                        ),
                        string(
                            credentialsId: 'PRD_ADJUDICATOR_ARTIFACTORY_TOKEN',
                            variable: 'ADJUDICATOR_ARTIFACTORY_TOKEN'
                        ),
                        string(
                            credentialsId: 'ADJUDICATOR_SNOW_INT_ID',
                            variable: 'ADJUDICATOR_SNOW_INT_ID'
                        ),
                        string(
                            credentialsId: 'SVT_ADJUDICATOR_INT',
                            variable: 'SVT_ADJUDICATOR_INT'
                        ),
                        string(
                            credentialsId: 'ADJUDICATOR_GITHUB_TOKEN',
                            variable: 'GITHUB_ACCESS_TOKEN'
                        ),
                        string(
                            credentialsId: 'SPLUNK_HEC_TOKEN',
                            variable: 'SPLUNK_HEC_TOKEN'
                        ),
                        string(
                            credentialsId: 'REDIS_PROD_PASSWORD',
                            variable: 'REDIS_SECURE'
                        ),
                        usernamePassword(
                            credentialsId: 'adjudicator-ldap-auth-internal',
                            usernameVariable: 'UNUSED_VALUE',
                            passwordVariable: 'LDAP_PWD'
                        ),
                    ],
                    sdlcEnvironment       : 'prod_oscpv4',
                    isProductionDeployment: true,
                    openshift             : [
                        url          : 'https://api.gp-2-prod.openshift.cignacloud.com:6443', // This url may need to change
                        credentialsId: 'SVP_ADJUDICATOR_INT_PROPER',
                        project      : 'adjudicator-production',
                        user         : 'SVP_ADJUDICATOR_INT'
                    ],
                    helm                  : [
                        version: '3.5.3',
                        command: 'helm upgrade the-adjudicator ./deploy/adjudicator-helm' +
                            ' -f ./deploy/adjudicator-helm/prod/values.yaml' +
                            " --set-string revision=prod-$version" +
                            ' --set-string revisionHash=$GIT_COMMIT' +
                            " --set-string imageTag=prod-$version" +
                            ' --set-string env.secret.ADJUDICATOR_ARTIFACTORY_TOKEN=$ADJUDICATOR_ARTIFACTORY_TOKEN' +
                            ' --set-string env.secret.ADJUDICATOR_SNOW_INT_ID=$ADJUDICATOR_SNOW_INT_ID' +
                            ' --set-string env.secret.SVP_ADJUDICATOR_INT=$SVP_ADJUDICATOR_INT' +
                            ' --set-string env.secret.SVT_ADJUDICATOR_INT=$SVT_ADJUDICATOR_INT' +
                            ' --set-string env.secret.GITHUB_ACCESS_TOKEN=$GITHUB_ACCESS_TOKEN' +
                            ' --set-string env.secret.SPLUNK_HEC_TOKEN=$SPLUNK_HEC_TOKEN' +
                            ' --set-string env.secret.REDIS_SECURE=$REDIS_SECURE' +
                            ' --set-string env.secret.LDAP_PWD=$LDAP_PWD' +
                            ' --wait --install --cleanup-on-fail --history-max 1'
                    ],
                    testing               : [
                        [
                            testType       : 'newman',
                            basicAuth      : 'adjudicator-newman-testcreds',
                            collectionsPath: 'tests/newman'
                        ],
                        [
                            testType: "JMeter",
                            planPath: "tests/jmeter/PerformanceTests.jmx",
                            args    : '-JSERVER_NAME=adjudicator.cigna.com -JAUTH_PWD=$LDAP_PWD -JNUM_ITERATIONS=1'
                        ]
                    ],
                ]
            ]
        }
        then:
        if (podGroupings) {
            assert psc.podSelector.byPodGroup() == podGroupings
        }
        where:
        lintingGroupID << ['build', 'build', 'unique-1']
        buildGroupID << ['build', 'unique', 'unique-2']
        podGroupings << [
            ['build', 'package', 'deploy'],
            ['build', 'unique', 'package', 'deploy'],
            ['unique-1', 'unique-2', 'package', 'deploy']
        ]
    }

    def '''Verify that phases with that override their default groupBy are grouped with the correct pod group.'''() {
        given:
        explicitlyMockPipelineVariable('userId')
        explicitlyMockPipelineVariable('bearerToken')
        explicitlyMockPipelineVariable('openshiftSecret')
        explicitlyMockPipelineVariable('QUAY_TOKEN')
        getPipelineMock("sh")({
            if (!(it instanceof Map)) {
                false
            } else {
                it?.script ==~ /ls -l .* | grep ^d | awk '\{print .*}'/
            }
        }) >> "file_one\nfile_two"


        def version = 'test'
        when:
        cignaBuildFlow {
            groupBy = 'phase-type'
            githubConnectionName = 'github'
            commitStatusName = 'Adjudicator App Build'
            cloudName = "adjudicator-openshift-devops1"
            webexTeamsRoom = 'a3b03650-838b-11ec-b8ff-cde520824ca3'
            compliance:
            [
                audit: [
                    buildCount: 5,
                ]
            ]
            releaseBranchPattern = 'main'
            checkmarxEnabled = true
            sonarEnabled = true
            phases = [
                [
                    branchPattern: '.*',
                    groupBy      : lintingGroupBy,
                    lintingTypes : [
                        'go': [
                            timeout: '8m',
                        ],
                    ]
                ],
                [
                    branchPattern: '.*',
                    groupBy      : lintingGroupBy,
                    lintingTypes : [
                        'go': [
                            timeout: '8m',
                        ],
                    ]
                ],
                [
                    buildType           : 'Go',
                    groupBy             : buildGroupBy,
                    golang              : [
                        apiTokenId: 'ADJUDICATOR_GITHUB_TOKEN',
                        tokenType : 'GitHub',
                    ],
                    branchPattern       : '.*',
                    releaseBranchPattern: 'none', // do not need to package
                    verbosityFlag       : '-vvv',
                    sonarQube           : [
                        credentialsId    : 'Sonarqube-A-Scanning-Token',
                        scannerProperties: [
                            'sonar.projectKey=theAdjudicator',
                            'sonar.projectName=theAdjudicator',
                            'sonar.sources=.',
                            'sonar.exclusions=**/*_test.go,**/vendor/**',
                            'sonar.tests=.',
                            'sonar.test.inclusions=**/*_test.go',
                            'sonar.test.exclusions=**/vendor/**',
                            'sonar.cfamily.cache.enabled=false',
                            'sonar.c.file.suffixes=-',
                            'sonar.cpp.file.suffixes=-',
                            'sonar.objc.file.suffixes=-'
                        ]
                    ],
                    checkmarx           : [
                        credentialsId: 'checkmarx-devops-prod',
                        settings     : [
                            CX_PROJECT_TEAM_NAME  : 'devops',
                            CX_PROJECT_NAME       : 'adjudicator',
                            CX_EXCLUDE_FOLDER_LIST: 'vendor',
                            CX_INCL_EXCL_FILE_LIST: '!**/*_test.go',
                        ],
                    ],
                ],
                [
                    packagingType       : 'kaniko',
                    branchPattern       : '.*',
                    releaseBranchPattern: 'main',
                    conftestValidation  : false, // XXX.cnm - temporary until correct minideb version tags available
                    dockerRegistry      : 'registry.cigna.com',
                    image               : [
                        org         : 'adjudicator-devops',
                        name        : 'adjudicator',
                        securityScan: true,
                        buildArgs   : [
                            ADJUDICATOR_DNS_SERVICE: "adjudicator-dns-service.adjudicator-nonproduction.svc.cluster.local",
                            ADJUDICATOR_VERSION    : "$version",
                            DEPLOY_ENVIRONMENT     : "NonProd",
                            PROMETHEUS_ENABLED     : "true",
                            OPENTELEMETRY_ENABLED  : "false",
                            REDIS_ADDRESS          : "redis-master.adjudicator-nonproduction.svc.cluster.local",
                        ],
                        tags        : [
                            [
                                tag   : 'dev',
                                expire: true
                            ],
                            [
                                tag   : "dev-$version",
                                expire: false
                            ]
                        ]
                    ],
                    quay                : [
                        credentialsId: 'quay_adjudicator_token_prod'
                    ]
                ],
                [
                    packagingType     : 'kaniko',
                    branchPattern     : '.*',
                    conftestValidation: false, // XXX.cnm - temporary until correct minideb version tags available
                    dockerRegistry    : 'registry.cigna.com',
                    image             : [
                        org         : 'adjudicator-devops',
                        name        : 'adjudicator',
                        securityScan: true,
                        buildArgs   : [
                            ADJUDICATOR_DNS_SERVICE: "adjudicator-dns-service.adjudicator-production.svc.cluster.local",
                            ADJUDICATOR_VERSION    : "$version",
                            PROMETHEUS_ENABLED     : "true",
                            OPENTELEMETRY_ENABLED  : "false",
                            DEPLOY_ENVIRONMENT     : "Prod",
                            REDIS_ADDRESS          : "redis-master.adjudicator-production.svc.cluster.local",
                        ],
                        tags        : [
                            [
                                tag   : 'latest',
                                expire: true
                            ],
                            [
                                tag   : 'prod',
                                expire: false
                            ],
                            [
                                tag   : "$version",
                                expire: false
                            ],
                            [
                                tag   : "prod-$version",
                                expire: false
                            ]
                        ]
                    ],
                    quay              : [
                        credentialsId: 'quay_adjudicator_token_prod'
                    ]
                ],
                [
                    deploymentType        : 'openshift',
                    containerImage        : 'enterprise-devops/helm-cli',
                    containerVersion      : 'v3.5.3',
                    branchPattern         : '.*',
                    extraCredentials      : [
                        string(
                            credentialsId: 'SVP_ADJUDICATOR_INT',
                            variable: 'SVP_ADJUDICATOR_INT'
                        ),
                        string(
                            credentialsId: 'PRD_ADJUDICATOR_ARTIFACTORY_TOKEN',
                            variable: 'ADJUDICATOR_ARTIFACTORY_TOKEN'
                        ),
                        string(
                            credentialsId: 'ADJUDICATOR_SNOW_INT_ID',
                            variable: 'ADJUDICATOR_SNOW_INT_ID'
                        ),
                        string(
                            credentialsId: 'SVT_ADJUDICATOR_INT',
                            variable: 'SVT_ADJUDICATOR_INT'
                        ),
                        string(
                            credentialsId: 'ADJUDICATOR_GITHUB_TOKEN',
                            variable: 'GITHUB_ACCESS_TOKEN'
                        ),
                        string(
                            credentialsId: 'SPLUNK_HEC_TOKEN',
                            variable: 'SPLUNK_HEC_TOKEN'
                        ),
                        string(
                            credentialsId: 'REDIS_NONPROD_PASSWORD',
                            variable: 'REDIS_SECURE'
                        ),
                        usernamePassword(
                            credentialsId: 'adjudicator-ldap-auth-internal',
                            usernameVariable: 'UNUSED_VALUE',
                            passwordVariable: 'LDAP_PWD'
                        ),
                    ],
                    sdlcEnvironment       : 'dev_oscpv4',
                    isProductionDeployment: false,

                    openshift             : [
                        url          : 'https://api.gp-2-nonprod.openshift.cignacloud.com:6443', // This url may need to change
                        credentialsId: 'SVP_ADJUDICATOR_INT',
                        project      : 'adjudicator-nonproduction',
                        user         : 'SVP_ADJUDICATOR_INT'
                    ],
                    helm                  : [
                        version: '3.5.3',
                        command: 'helm upgrade the-adjudicator ./deploy/adjudicator-helm' + ' -f ./deploy/adjudicator-helm/nonprod/values.yaml' +
                            " --set-string revision=dev-$version" +
                            ' --set-string revisionHash=$GIT_COMMIT' +
                            " --set-string imageTag=dev-$version" +
                            ' --set-string env.secret.ADJUDICATOR_ARTIFACTORY _TOKEN=$ADJUDICATOR_ARTIFACTORY_TOKEN' +
                            ' --set-string env.secret.ADJUDICATOR_SNOW_INT_ID =$ADJUDICATOR_SNOW_INT_ID' +
                            ' --set-string env.secret.SVP_ADJUDICATOR_INT=$SV P_ADJUDICATOR_INT' +
                            ' --set-string env.secret.SVT_ADJUDICATOR_INT=$SV T_ADJUDICATOR_INT' +
                            ' --set-string env.secret.GITHUB_ACCESS_TOKEN=$GI THUB_ACCESS_TOKEN' +
                            ' --set-string env.secret.SPLUNK_HEC_TOKEN=$SPLUN K_HEC_TOKEN' +
                            ' --set-string env.secret.REDIS_SECURE=$REDIS_SEC URE' +
                            ' --set-string env.secret.LDAP_PWD=$LDAP_PWD' +
                            ' --wait --install --cleanup-on-fail --history-ma x 1'
                    ],
                    testing               : [
                        [
                            testType       : 'newman',
                            basicAuth      : 'adjudicator-newman-testcreds',
                            collectionsPath: 'tests/newman'
                        ],
                        [
                            testType: "JMeter",
                            planPath: "tests/jmeter/PerformanceTests.jmx",
                            args    : '-JSERVER_NAME=adjudicator.apps.gp-2-nonprod.openshift.cignacloud.com -JAUTH_PWD=$LDAP_PWD -JNUM_ITERATIONS=10'
                        ]
                    ]
                ],
                [
                    deploymentType        : 'openshift',
                    branchPattern         : '.*',
                    extraCredentials      : [
                        string(
                            credentialsId: 'SVP_ADJUDICATOR_INT',
                            variable: 'SVP_ADJUDICATOR_INT'
                        ),
                        string(
                            credentialsId: 'PRD_ADJUDICATOR_ARTIFACTORY_TOKEN',
                            variable: 'ADJUDICATOR_ARTIFACTORY_TOKEN'
                        ),
                        string(
                            credentialsId: 'ADJUDICATOR_SNOW_INT_ID',
                            variable: 'ADJUDICATOR_SNOW_INT_ID'
                        ),
                        string(
                            credentialsId: 'SVT_ADJUDICATOR_INT',
                            variable: 'SVT_ADJUDICATOR_INT'
                        ),
                        string(
                            credentialsId: 'ADJUDICATOR_GITHUB_TOKEN',
                            variable: 'GITHUB_ACCESS_TOKEN'
                        ),
                        string(
                            credentialsId: 'SPLUNK_HEC_TOKEN',
                            variable: 'SPLUNK_HEC_TOKEN'
                        ),
                        string(
                            credentialsId: 'REDIS_PROD_PASSWORD',
                            variable: 'REDIS_SECURE'
                        ),
                        usernamePassword(
                            credentialsId: 'adjudicator-ldap-auth-internal',
                            usernameVariable: 'UNUSED_VALUE',
                            passwordVariable: 'LDAP_PWD'
                        ),
                    ],
                    sdlcEnvironment       : 'prod_oscpv4',
                    isProductionDeployment: true,
                    openshift             : [
                        url          : 'https://api.gp-2-prod.openshift.cignacloud.com:6443', // This url may need to change
                        credentialsId: 'SVP_ADJUDICATOR_INT_PROPER',
                        project      : 'adjudicator-production',
                        user         : 'SVP_ADJUDICATOR_INT'
                    ],
                    helm                  : [
                        version: '3.5.3',
                        command: 'helm upgrade the-adjudicator ./deploy/adjudicator-helm' +
                            ' -f ./deploy/adjudicator-helm/prod/values.yaml' +
                            " --set-string revision=prod-$version" +
                            ' --set-string revisionHash=$GIT_COMMIT' +
                            " --set-string imageTag=prod-$version" +
                            ' --set-string env.secret.ADJUDICATOR_ARTIFACTORY _TOKEN=$ADJUDICATOR_ARTIFACTORY_TOKEN' +
                            ' --set-string env.secret.ADJUDICATOR_SNOW_INT_ID =$ADJUDICATOR_SNOW_INT_ID' +
                            ' --set-string env.secret.SVP_ADJUDICATOR_INT=$SV P_ADJUDICATOR_INT' +
                            ' --set-string env.secret.SVT_ADJUDICATOR_INT=$SV T_ADJUDICATOR_INT' +
                            ' --set-string env.secret.GITHUB_ACCESS_TOKEN=$GI THUB_ACCESS_TOKEN' +
                            ' --set-string env.secret.SPLUNK_HEC_TOKEN=$SPLUN K_HEC_TOKEN' +
                            ' --set-string env.secret.REDIS_SECURE=$REDIS_SEC URE' +
                            ' --set-string env.secret.LDAP_PWD=$LDAP_PWD' +
                            ' --wait --install --cleanup-on-fail --history-ma x 1'
                    ],
                    testing               : [
                        [
                            testType       : 'newman',
                            basicAuth      : 'adjudicator-newman-testcreds',
                            collectionsPath: 'tests/newman'
                        ],
                        [
                            testType: "JMeter",
                            planPath: "tests/jmeter/PerformanceTests.jmx",
                            args    : '-JSERVER_NAME=adjudicator.cigna.com -JAUTH_PWD=$LDAP_PWD -JNUM_ITERATIONS=1'
                        ]
                    ],
                ]
            ]
        }
        then:
        psc.podSelector.byPodGroup().size() == podCounts

        where:
        lintingGroupBy << ['phase-type', 'phase-type', 'unique']
        buildGroupBy << ['phase-type', 'unique', 'unique']
        podCounts << [4, 4, 5]
    }

    def '''Verify that container overrides in module phases are included in container definitions'''() {
        when:
        cignaBuildFlow {
            githubConnectionName = 'github'
            cloudName = 'test-cloud'
            phases = [
                [
                    moduleType            : 'maven',
                    branchPattern         : '.*',
                    sdlcEnvironment       : 'dev',
                    isProductionDeployment: false,
                    subCommand            : 'build',
                    moduleName            : 'cnp-build-publish-maven-create',
                    container             : configuredContainer,
                    args                  : [
                        publish: false
                    ]
                ],
            ]
        }

        then:
        assert psc.podSelector.podTemplates['test-cloud'].contains(expectedMemoryOutcome)
        assert psc.podSelector.podTemplates['test-cloud'].contains(expectedCpuOutcome)
        where:
        configuredContainer << [[cpu: 3456, memory: 6789], [:]]
        expectedMemoryOutcome << ['6789Mi', '1000Mi']
        expectedCpuOutcome << ['3456m', '700m']
    }

    def '''In a multi-cloud configuration with multiple service account requirements, each required SA is assigned to the correct pod group'''() {
        when:
        String containerImage = 'enterprise-devops/aws-d-megatainer'
        String containerEKSVersion = '1.0.14-2'
        String containerVersion = '1.0.13'
        String cloudNameEKS = "da-hpp-amp-eks-jenkins-cluster-prod"
        String cloudNameOS = "da-hpp-amp-openshift-devops1"

        def securityScanPhase = { deploy_env ->
            return [
                buildType      : "plz",
                branchPattern  : '.*',
                sdlcEnvironment: deploy_env,
                container      : [
                    image  : "${containerImage}",
                    version: "${containerVersion}",
                    cpu    : 2000,
                    memory : 2000
                ],
                sonarQube      : [
                    credentialsId: 'amp_sonar_key'
                ],
                checkmarx      : [
                    credentialsId: 'amp_checkmarx_api_creds',
                    settings     : [
                        CX_PROJECT_TEAM_NAME  : 'amp',
                        CX_SCAN_COMMENT       : "Pipeline:AMP-${BUILD_NUMBER} - Branch:${BRANCH_NAME}",
                        CX_EXCLUDE_FOLDER_LIST: 'plz-out, commands, test, tests, third_party',
                        CX_INCL_EXCL_FILE_LIST: 'module/aws/amp-lambda-functions/**'
                    ],
                ],
            ]
        }

        def layerBuildPhase = { deploy_env, account_number, isProdDeployment ->
            return [
                cloudName             : "${cloudNameEKS}",
                runInAWS              : true,
                aws                   : [
                    cloudServiceAccountName: "jenkins-robot-${deploy_env}",
                    targetAccount          : account_number,
                    accountRoleName        : 'Enterprise/GKAMPDEPLOYER',
                    region                 : 'us-east-1'
                ],
                branchPattern         : ".*",
                container             : [
                    image  : "${containerImage}",
                    version: "${containerEKSVersion}",
                    cpu    : 2000,
                    memory : 2000
                ],
                deploymentType        : 'plz',
                alias                 : 'create_layers',
                extraArgs             : deploy_env,
                verbosityFlag         : '-vvv',
                deployEnv             : 'Packages Layer Build',
                sdlcEnvironment       : deploy_env,
                isProductionDeployment: isProdDeployment
            ]
        }

        def datasourceDockerBuildPhase = { account_number, aws_sa, secret_name, isProdDeployment ->
            return [
                packagingType   : 'kaniko',
                dockerRegistry  : account_number + ".dkr.ecr.us-east-1.amazonaws.com",
                branchPattern   : ".*",
                // changePattern: '.*amp-datasource/.*',
                image           : [
                    name     : 'amp-datasource',
                    tags     : [
                        [
                            tag   : 'latest',
                            expire: true
                        ]
                    ],
                    buildArgs: [
                        GRAPHQL_DIR: "./module/aws/amp-lambda-functions/amp-datasource",
                        DB_PASS    : '\$DB_PASS',
                        DB_URL     : '\$DB_URL'
                    ]
                ],
                ecr             : [
                    credentialsId: aws_sa, // used for aws-fed
                    rolename     : 'HPNCCDJENKINS',
                    saml         : true,
                ],
                container       : [
                    cpu   : 4000,
                    memory: 8048
                ],
                extraCredentials: [
                    usernamePassword(
                        credentialsId: secret_name,
                        passwordVariable: 'DB_PASS',
                        usernameVariable: 'DB_URL'
                    )
                ]
            ]
        }

        def adminDockerBuildPhase = { account_number, aws_sa, secret_name, isProdDeployment ->
            return [
                packagingType   : 'kaniko',
                dockerRegistry  : account_number + ".dkr.ecr.us-east-1.amazonaws.com",
                branchPattern   : ".*",
                // changePattern: '.*amp-admin/.*',
                image           : [
                    name     : 'amp-admin',
                    tags     : [
                        [
                            tag   : 'latest',
                            expire: true
                        ]
                    ],
                    buildArgs: [
                        GRAPHQL_DIR: "./module/aws/amp-lambda-functions/amp-admin",
                        DB_PASS    : '\$DB_PASS',
                        DB_URL     : '\$DB_URL'
                    ]
                ],
                ecr             : [
                    credentialsId: aws_sa, // used for aws-fed
                    rolename     : 'HPNCCDJENKINS',
                    saml         : true,
                ],
                extraCredentials: [
                    usernamePassword(
                        credentialsId: secret_name,
                        passwordVariable: 'DB_PASS',
                        usernameVariable: 'DB_URL'
                    )
                ]
            ]
        }

        def planPhase = { tdv_secret_name, aws_sa, deploy_env, account_number, isProdDeployment ->
            return [
                extraCredentials      : [
                    usernamePassword(
                        credentialsId: tdv_secret_name,
                        passwordVariable: 'AMP_TDV_PSW',
                        usernameVariable: 'AMP_TDV_USR'
                    ),
                    usernamePassword(
                        credentialsId: aws_sa,
                        passwordVariable: 'AMP_SA_PSW',
                        usernameVariable: 'AMP_SA_USR'
                    )
                ],
                withEnv               : [
                    "ENV=${deploy_env}",
                ],
                cloudName             : "${cloudNameOS}",
                aws                   : [
                    targetAccount  : account_number,
                    accountRoleName: 'HPNCCDJENKINS',
                    region         : 'us-east-1',
                ],
                branchPattern         : ".*",
                container             : [
                    image  : "${containerImage}",
                    version: "${containerVersion}",
                    cpu    : 3000,
                    memory : 4000
                ],
                deploymentType        : 'plz',
                alias                 : 'plan_all',
                extraArgs             : deploy_env,
                verbosityFlag         : '-vvv',
                deployEnv             : 'Terraform Plan',
                sdlcEnvironment       : deploy_env,
                isProductionDeployment: isProdDeployment
            ]
        }


        def deployPhase = { tdv_secret_name, aws_sa, deploy_env, account_number, isProdDeployment ->
            return [
                extraCredentials      : [
                    usernamePassword(
                        credentialsId: tdv_secret_name,
                        passwordVariable: 'AMP_TDV_PSW',
                        usernameVariable: 'AMP_TDV_USR'
                    ),
                    usernamePassword(
                        credentialsId: aws_sa,
                        passwordVariable: 'AMP_SA_PSW',
                        usernameVariable: 'AMP_SA_USR'
                    )
                ],
                withEnv               : [
                    "ENV=${deploy_env}",
                ],
                cloudName             : "${cloudNameOS}",
                aws                   : [
                    targetAccount  : account_number,
                    accountRoleName: 'HPNCCDJENKINS',
                    region         : 'us-east-1',
                ],
                branchPattern         : ".*",
                container             : [
                    image  : "${containerImage}",
                    version: "${containerVersion}",
                    cpu    : 2000,
                    memory : 4000
                ],
                deploymentType        : 'plz',
                alias                 : 'deploy',
                extraArgs             : deploy_env,
                verbosityFlag         : '-vvv',
                deployEnv             : 'Terraform Deploy',
                sdlcEnvironment       : deploy_env,
                isProductionDeployment: isProdDeployment
            ]
        }

        def runMigrationsPhase = { deploy_env, account_number, isProdDeployment ->
            return [
                cloudName             : "${cloudNameEKS}",
                runInAWS              : true,
                aws                   : [
                    cloudServiceAccountName: "jenkins-robot-${deploy_env}",
                    targetAccount          : account_number,
                    accountRoleName        : 'Enterprise/GKAMPDEPLOYER',
                    region                 : 'us-east-1'
                ],
                branchPattern         : ".*",
                container             : [
                    image  : "${containerImage}",
                    version: "${containerEKSVersion}",
                    cpu    : 2000,
                    memory : 2000
                ],
                deploymentType        : 'plz',
                alias                 : 'run_migrations',
                extraArgs             : deploy_env,
                verbosityFlag         : '-vvv',
                deployEnv             : 'DB Migrations',
                sdlcEnvironment       : deploy_env,
                isProductionDeployment: isProdDeployment
            ]
        }

        def runThesePhases = []
        runThesePhases += securityScanPhase()
        switch ('test-branch') {
            case "develop":
                runThesePhases += layerBuildPhase("dev", "461894682041", false)
                runThesePhases += datasourceDockerBuildPhase("461894682041", "SVTAMPDEV", "amp_dev_db", false)
                runThesePhases += adminDockerBuildPhase("461894682041", "SVTAMPDEV", "amp_dev_db", false)
                runThesePhases += planPhase("SVT_AMP_SERVICE_TDV", "SVTAMPDEV", "dev", "461894682041", false)
                runThesePhases += deployPhase("SVT_AMP_SERVICE_TDV", "SVTAMPDEV", "dev", "461894682041", false)
                break
            case ~/feature(.*)/:
                runThesePhases += layerBuildPhase("dev", "461894682041", false)
                runThesePhases += datasourceDockerBuildPhase("461894682041", "SVTAMPDEV", "amp_dev_db", false)
                runThesePhases += adminDockerBuildPhase("461894682041", "SVTAMPDEV", "amp_dev_db", false)
                runThesePhases += planPhase("SVT_AMP_SERVICE_TDV", "SVTAMPDEV", "dev", "461894682041", false)
                runThesePhases += deployPhase("SVT_AMP_SERVICE_TDV", "SVTAMPDEV", "dev", "461894682041", false)
                break
            case "release":
                runThesePhases += layerBuildPhase("test", "790055516420", false)
                runThesePhases += datasourceDockerBuildPhase("790055516420", "SVTAMPTEST", "amp_test_db", false)
                runThesePhases += adminDockerBuildPhase("790055516420", "SVTAMPTEST", "amp_test_db", false)
                runThesePhases += planPhase("SVT_AMP_SERVICE_TDV", "SVTAMPTEST", "test", "790055516420", false)
                runThesePhases += deployPhase("SVT_AMP_SERVICE_TDV", "SVTAMPTEST", "test", "790055516420", false)
                runThesePhases += runMigrationsPhase("test", "790055516420", false)
                break
            case "main":
                runThesePhases += layerBuildPhase("prod", "361987197148", false)
                runThesePhases += datasourceDockerBuildPhase("361987197148", "SVPAMPPROD", "amp_prod_db", false)
                runThesePhases += adminDockerBuildPhase("361987197148", "SVPAMPPROD", "amp_prod_db", false)
                runThesePhases += planPhase("SVP_AMP_SERVICE_TDV", "SVPAMPPROD", "prod", "361987197148", false)
                runThesePhases += deployPhase("SVP_AMP_SERVICE_TDV", "SVPAMPPROD", "prod", "361987197148", false)
                runThesePhases += runMigrationsPhase("prod", "361987197148", false)
                break
            default:
                runThesePhases += layerBuildPhase("dev", "461894682041", false)
                runThesePhases += datasourceDockerBuildPhase("461894682041", "SVTAMPDEV", "amp_dev_db", false)
                runThesePhases += adminDockerBuildPhase("461894682041", "SVTAMPDEV", "amp_dev_db", false)
                runThesePhases += planPhase("SVT_AMP_SERVICE_TDV", "SVTAMPDEV", "dev", "461894682041", false)
                runThesePhases += deployPhase("SVT_AMP_SERVICE_TDV", "SVTAMPDEV", "dev", "461894682041", false)
                runThesePhases += runMigrationsPhase("dev", "461894682041", false)
                break
        }
        explicitlyMockPipelineVariable('AWS_PASSWORD')
        explicitlyMockPipelineVariable('AWS_USERNAME')

        cignaBuildFlow {
            cloudName = cloudNameOS
            githubConnectionName = 'cigna-github'
            complianceQualityLevel = "Bronze"
            phases = runThesePhases
            webexTeamsRoom = "Y2lzY29zcGFyazovL3VzL1JPT00vOTQ1ZWM3MjAtMzE2NS0xMWVjLWEwZGMtMTFjMGM4OTBmODNm"

        }
        then:
        assert psc.podSelector.podTemplates.size() == 2
        assert !psc.podSelector.podTemplates[cloudNameEKS].contains('kaniko')
        assert psc.podSelector.podTemplates[cloudNameOS].contains('kaniko')
        assert psc.podSelector.podTemplates[cloudNameEKS].contains('jenkins-robot-dev')
        assert !psc.podSelector.podTemplates[cloudNameOS].contains('jenkins-robot-dev')
    }

    def "When shallow checkout is enabled at main scope"() {
        when:
        cignaBuildFlow(
            {
                gitlabConnectionName = 'stuff'
                cloudName = 'notNull'
                repo = [
                    shallow: false
                ]
                phases = [
                    [
                        freestyleType: 'debug',
                        branchPattern: '.*',
                        container    : [
                            image  : 'cnp/cnp-docker-node10',
                            version: '1.0.4-dev-ov2',

                        ],
                        script       : '''
                                    env | sort
                                '''
                    ]
                ]
            },
            notification
        )

        then:
        1 * getPipelineMock("echo")(
            "checkoutConfiguration.repo.shallow: false"
        )
        currentBuild.result == "SUCCESS"
    }

    def "When shallow checkout is not enabled"() {
        when:
        cignaBuildFlow(
            {
                gitlabConnectionName = 'stuff'
                cloudName = 'notNull'

                phases = [
                    [
                        freestyleType: 'debug',
                        branchPattern: '.*',
                        container    : [
                            image  : 'cnp/cnp-docker-node10',
                            version: '1.0.4-dev-ov2',

                        ],
                        script       : '''
                                    env | sort
                                '''
                    ]
                ]
            },
            notification
        )

        then:
        1 * getPipelineMock("echo")(
            "checkoutConfiguration.repo.shallow: false"
        )
        currentBuild.result == "SUCCESS"
    }

    def "When shallow checkout is enabled at phase level"() {
        when:
        cignaBuildFlow(
            {
                gitlabConnectionName = 'stuff'
                cloudName = 'notNull'
                phases = [
                    [
                        repo         : [
                            shallow: true
                        ],
                        freestyleType: 'debug',
                        branchPattern: '.*',
                        container    : [
                            image  : 'cnp/cnp-docker-node10',
                            version: '1.0.4-dev-ov2',

                        ],
                        script       : '''
                                    env | sort
                                '''
                    ]
                ]
            },
            notification
        )

        then:
        1 * getPipelineMock("echo")(
            "checkoutConfiguration.repo.shallow: true"
        )
        currentBuild.result == "SUCCESS"
    }

    def '''verify that maven variable expansion functions as designed when referenced via lookup in a later phase'''() {
        given:
        explicitlyMockPipelineVariable("MAVEN_SETTINGS")
        explicitlyMockPipelineVariable("bearerToken")
        explicitlyMockPipelineVariable('QUAY_TOKEN')
        when:
        cignaBuildFlow {
            featureFlags = [verbose: true, debug: true]
            repo = [shallow: true]
            githubConnectionName = 'github'
            commitStatusName = 'DevOps Pipeline Converter'
            cloudName = 'epf-openshift-devops1'
            phaseCache = true
            stashIncludePattern = '**/*.jar'
            logHistoryCount = '5'

            phases = [
                [
                    buildType           : 'maven',
                    branchPattern       : '.*',
                    releaseBranchPattern: 'notNeeded',
                    sonarQube           : [
                        credentialsId     : 'sonarqube-demo-token',
                        scannerOptions    : '-Xmx1536m',
                        containerMaxMemory: '2Gi'
                    ],
                    artifactory         : [
                        credentialsId: 'artifactory-prod-deployer-ci0008907951-apikey',
                    ],
                    checkmarx           : [
                        credentialsId: 'global-checkmarx-id',
                        settings     : [
                            CX_PROJECT_TEAM_NAME: 'DevOps',
                            CX_PROJECT_NAME     : 'enterprise-pipeline-converter',
                        ],
                    ],
                ],
                [
                    packagingType         : 'kaniko',
                    branchPattern         : '.*',
                    dockerRegistry        : 'registry.cigna.com',
                    conftestValidation    : false,
                    sdlcEnvironment       : 'test-1',
                    isProductionDeployment: false,
                    image                 : [
                        org : 'enterprise-devops',
                        name: 'pipeline-converter',
                        tags: [
                            [tag: "dev", expire: false],
                            [tag: 'dev-lookup:{epf-build-maven:version}', expire: false],
                        ]
                    ],
                    quay                  : [
                        credentialsId: 'conduit-quay-token'
                    ]
                ],
                [
                    packagingType         : 'kaniko',
                    branchPattern         : '.*',
                    dockerRegistry        : 'registry.cigna.com',
                    conftestValidation    : false,
                    sdlcEnvironment       : 'test-2',
                    isProductionDeployment: false,
                    image                 : [
                        org : 'enterprise-devops',
                        name: 'pipeline-converter',
                        tags: [
                            [tag: "latest", expire: false],
                            [tag: 'prd-lookup:{epf-build-maven:version}', expire: false],
                            [tag: 'prd-lookup']
                        ]
                    ],
                    quay                  : [
                        credentialsId: 'conduit-quay-token'
                    ]
                ],
            ]
        }

        then:
        1 * getPipelineMock("sh")(
            {
                it instanceof Map &&
                    it.returnStdout == true &&
                    it.script ==~ /.*-Dexpression=project.groupId.*/
            }
        ) >> expectedGroup
        1 * getPipelineMock("sh")(
            {
                it instanceof Map &&
                    it.returnStdout == true &&
                    it.script ==~ /.*-Dexpression=project.artifactId.*/
            }
        ) >> expectedArtifact
        1 * getPipelineMock("sh")(
            {
                it instanceof Map &&
                    it.returnStdout == true &&
                    it.script ==~ /.*-Dexpression=project.version.*/
            }
        ) >> expectedVersion
        1 * getPipelineMock("sh")(
            {
                it instanceof Map &&
                    it.returnStdout == true &&
                    it.script ==~ /.*-Dexpression=project.packaging.*/
            }
        ) >> expectedPackaging
        currentBuild.result == "SUCCESS"
        psc.metadata.get("${MavenBuild.MAVEN_MODULE_SCOPE}:version") == expectedVersion
        psc.metadata.get("${MavenBuild.MAVEN_MODULE_SCOPE}:artifactId") == expectedArtifact
        psc.metadata.get("${MavenBuild.MAVEN_MODULE_SCOPE}:packaging") == expectedPackaging
        psc.metadata.get("${MavenBuild.MAVEN_MODULE_SCOPE}:groupId") == expectedGroup
        psc.metadata.get('version') == expectedVersion
        psc.metadata.get('artifactId') == expectedArtifact
        psc.metadata.get('packaging') == expectedPackaging
        psc.metadata.get('groupId') == expectedGroup
        def versionWeWant = expectedVersion
        psc.podSelector.phaseConfigs.find {
            it.containsKey('packagingType') &&
                it.sdlcEnvironment == 'test-1'
        }.image.tags.findAll { it.tag.toString() == "dev-$versionWeWant".toString() }.size() == 1
        psc.podSelector.phaseConfigs.find {
            it.containsKey('packagingType') &&
                it.sdlcEnvironment == 'test-2'
        }.image.tags.findAll { it.tag.toString() == "prd-$versionWeWant".toString() }.size() == 1
        psc.podSelector.phaseConfigs.find {
            it.containsKey('packagingType') &&
                it.sdlcEnvironment == 'test-2'
        }.image.tags.findAll { it.tag.toString() == 'prd-lookup' }.size() == 1
        where:
        expectedGroup << ['my-group', 'another-group']
        expectedArtifact << ['my-artifact', 'another-artifact']
        expectedVersion << ['my-version', 'another-version']
        expectedPackaging << ['my-packaging', 'another-packaging']

    }

    def '''verify that custom modules pass release validation'''() {
        given:
        explicitlyMockPipelineVariable("MAVEN_SETTINGS")
        explicitlyMockPipelineVariable("bearerToken")
        explicitlyMockPipelineVariable('QUAY_TOKEN')
        BRANCH_NAME = 'release/mergeback-test'
        scmMock[0].name = BRANCH_NAME
        when:
        env.CNP_LIBRARY_NAME_OVERRIDE = 'epf@feature/mergeback'

        def FOLDER_NAME = 'workcomp-npm-005'
        def PROJECT_NAME = 'workcomp-ui-internal-admin'
// The conversion-pattern in the variable below is a temporary addition to help with setup.
        def DEPLOYABLE_BRANCH_PATTERN = /^(develop|integration\/.*|release\/.*)$/
        env.CNP_DEPLOYABLE_BRANCHES = DEPLOYABLE_BRANCH_PATTERN
        def RELEASE_BRANCH_PATTERN = /^(release\/.*)$/
        def ALL_BRANCHES_PATTERN = /^.*$/

        def BRANCH_NAME = env.CHANGE_BRANCH != null ? env.CHANGE_BRANCH : env.BRANCH_NAME

        def isProductionDeployment = BRANCH_NAME ==~ RELEASE_BRANCH_PATTERN
        def isPR = env.CHANGE_BRANCH != null
        def isDeployable = BRANCH_NAME ==~ DEPLOYABLE_BRANCH_PATTERN

// ++VERSION_VARIABLE++
        def VERSION = isProductionDeployment ? '1' : '2'

        def appName = PROJECT_NAME

// ++MORE_VARIABLES_GO_HERE++
        def NPM_IMAGE = 'cnp/cnp-docker-node16:1.0.4-dev-ov2'
        env.CNP_DEFAULT_NPM_IMAGE = NPM_IMAGE
        env.NODE_OPTIONS = '--use-openssl-ca --unhandled-rejections=warn'
        def PCF_ORG = 'MYM'
        def PCF_PROD_FOUNDATION = 'ps2pcf02'
        def PCF_PROD_SPACE = 'Production'
        def PCF_PROD_ENV = 'prod-02'

        List phasesToRun = []
        def buildPhases = [
            [
                moduleType            : "npm",
                container             : [
                    cpu   : 2000,
                    memory: 2000
                ],
                moduleName            : "cnp-build-npm-create",
                subCommand            : "build",
                branchPattern         : ALL_BRANCHES_PATTERN,
                releaseBranchPattern  : RELEASE_BRANCH_PATTERN,
                isProductionDeployment: isProductionDeployment,
                args                  : [
                    buildCommand: 'mkdir dist && touch dist/foo.txt && npm version 1.0.$(date +%Y%m%d%H%M%S)',
                    publish     : true
                ]
            ]
        ]
        phasesToRun.addAll(buildPhases)

        List releasePhases = []
        def preReleaseAndReleasePhases = [
            [
                releaseType         : 'release',
                branchPattern       : RELEASE_BRANCH_PATTERN,
                releaseBranchPattern: RELEASE_BRANCH_PATTERN,
                phases              : releasePhases
            ]
        ]
        phasesToRun.addAll(preReleaseAndReleasePhases)

        def prodDeployPhases = [
            [
                moduleType            : 'pcf',
                moduleName            : 'cnp-deploy-pcf',
                subCommand            : 'deploy',
                branchPattern         : RELEASE_BRANCH_PATTERN,
                releaseBranchPattern  : RELEASE_BRANCH_PATTERN,
                isProductionDeployment: isProductionDeployment,
                sdlcEnvironment       : PCF_PROD_ENV,
                args                  : [
                    artifact    : 'lookup:publishUrl',
                    appName     : "$appName-1-prod-candidate",
                    foundation  : PCF_PROD_FOUNDATION,
                    organization: PCF_ORG,
                    space       : PCF_PROD_SPACE,
                    env         : PCF_PROD_ENV
                ]
            ]
        ]
        releasePhases.addAll(prodDeployPhases)

        def mergebackPhase = [
            moduleType            : 'mergeback',
            branchPattern         : /^(release|hotfix)\/.*$/,
            releaseBranchPattern  : /^(release|hotfix)\/.*$/,
            stageName             : 'Git Merge-Back',
            isProductionDeployment: true,
            module                : [
                contractName: 'FINALIZE_RELEASE',
                commandName : 'cnp-git-mergeback.sh',
                subCommand  : 'finalizeRelease',
                image       : 'mym/cnp-git-mergeback:testing-1.1.0',
                moduleName  : 'cnp-git-mergeback'
            ],
            args                  : [
                credentials: [
                    [id: 'GIT_TOKEN', type: 'string']
                ]
            ]
        ]

        releasePhases.add(mergebackPhase)

        cignaBuildFlow {
            repo = [shallow: false]
            jnlp = [cpu: 1000, memory: 1000]
            githubConnectionName = 'github'
            githubCredentialsId = 'GIT_TOKEN'
            commitStatusName = PROJECT_NAME
            cloudName = "$FOLDER_NAME-openshift-devops1"
            phases = phasesToRun
        }
        def phases = psc.podSelector.phaseConfigs.find {
            it.containsKey('releaseType') &&
                it.moduleType == 'release'
        }.phases
        then:
        phases.size() == 2
        phases.findAll { it.moduleType == 'mergeback' }.size() == 1

    }

    def '''verify that non custom modules still pass release validation'''() {
        given:

        BRANCH_NAME = 'release/mergeback-test'
        scmMock[0].name = BRANCH_NAME
        when:
        env.CNP_LIBRARY_NAME_OVERRIDE = "epf"
        env.CNP_LOG_LEVEL = "TRACE"

        boolean isProductionDeployment = env.GIT_BRANCH ==~ /^(release|hotfix).*/
        env.GIT_TOKEN = 'prd-github-access-token'
        env.CNP_LOG_LEVEL = "TRACE"
        env.CX_CREDENTIAL = 'checkmarx_cnp-testapps'
        env.SONAR_CREDENTIAL_ID = 'sonar'
        env.ARTIFACTORY_CREDENTIAL = 'ARTIFACTORY_CREDENTIAL'
        env.CNP_REGISTRY_DEV_CRED = 'cnp_cnp_robot_registry_dev'
        env.CNP_REGISTRY_PROD_CRED = 'cnp_cnp_robot_registry_prod'
        env.CNP_CIGNA_GIT = 'cigna_git_cnp'
        env.CNP_OC_CRED_DEV = 'cnp-nonprod-oc'
        env.CNP_OC_CRED_QA = 'cnp-nonprod-oc'
        env.CNP_OC_CRED_UAT = 'cnp-nonprod-oc'
        env.CNP_OC_CRED_PROD = 'cnp-nonprod-oc'
        env.CNP_OC_CRED_DR = 'cnp-nonprod-oc'
        env.CNP_ARGOCD_CRED_DEV = 'cnp-nonprod-argocd'
        env.CNP_ARGOCD_CRED_QA = 'cnp-pa-qa-argocd-token'
        env.CNP_ARGOCD_CRED_UAT = 'cnp-pa-qa-argocd-token'
        env.CNP_ARGOCD_CRED_PROD = 'cnp-pa-prod-argocd-token'
        env.CNP_ARGOCD_CRED_DR = 'cnp-pa-prod-argocd-token'
        env.CNP_DEFAULT_JAVA_IMAGE = 'cnp/cnp-docker-maven-java11:1.2.3-dev-ov2'
        env.ARTIFACTORY_ROOT_URL = 'https://cigna.jfrog.io/artifactory'
        env.GIT_CREDENTIAL = 'SVPDEVOP-CIG-CLONEA_UserPw'
        env.CNP_OVERRIDE_COMMON = true

        List phasesToRun = [
            [
                freestyleType: 'debug',
                branchPattern: '.*',
                container    : [
                    image  : 'cnp/cnp-docker-node10',
                    version: '1.0.4-dev-ov2',

                ],
                script       : '''
            env | sort
        '''
            ],

            [
                moduleType            : "maven",
                moduleName            : "cnp-build-publish-maven-create",
                subCommand            : "build",
                branchPattern         : '.*',
                releaseBranchPattern  : '',
                isProductionDeployment: false,
            ],
            [
                moduleType            : 'docker',
                branchPattern         : '.*',
                sdlcEnvironment       : 'dev',
                isProductionDeployment: false,
                moduleName            : 'cnp-build-image',
                subCommand            : 'buildimage',
                serviceAccount        : 'kaniko',
                args                  : [
                    org                : 'cnp',
                    appendCommitIdToTag: false,
                    tag                : '${GIT_COMMIT_SHORT}',
                    generateDockerFile : true,
                    artifact           : 'https://cigna.jfrog.io/artifactory/libs-release-local/com/esrx/devops-testapps-SpringPcf/1.3.42/devops-testapps-SpringPcf-1.3.42.jar', //'lookup:publishUrl',
                    repository         : 'https:/artifactory-dev.express-scripts.com'
                ],
            ],
            [
                releaseType         : 'preRelease',
                releaseBranchPattern: 'test',
                branchPattern       : 'test',
                phases              : [
                    [
                        moduleType            : 'docker',
                        branchPattern         : '.*',
                        sdlcEnvironment       : 'dev',
                        isProductionDeployment: false,
                        moduleName            : 'cnp-publish-image',
                        subCommand            : 'publishimage',
                        serviceAccount        : 'kaniko',
                        args                  : [
                            sourceImage: 'cnp/devops-testapps-springpcf:1.3.51_79f9577db273be49881834f6575bdb4ad2a22f4c',
                            signImage  : true,
                            org        : 'cnp',
                            repository : 'registry.cigna.com'
                        ],
                    ],
                    [
                        moduleType            : 'openshift',
                        moduleName            : 'cnp-deploy-argorollouts',
                        subCommand            : 'deploy',
                        branchPattern         : '^(release|hotfix).*',
                        sdlcEnvironment       : 'dev',
                        isProductionDeployment: true,
                        args                  : [
                            appName                     : 'springpcf-testapp-1-dev',
                            namespace                   : 'pipeline-automation',
                            platform                    : 'OpenShift',
                            imageName                   : 'cnp/devops-testapps-springpcf',
                            imageTag                    : '1.3.42_fc6e2c1b9179163476b0c7b1a74e6cbd0297e35',
                            cluster                     : 'hs-1-nonprod',
                            configDir                   : 'dev',
                            monitoringSolution          : 'newrelic',
                            env                         : 'dev',
                            cmdbApplicationServiceNumber: 'AS025709'
                        ]
                    ]
                ]
            ],
            [
                releaseType         : 'release',
                releaseBranchPattern: '.*',
                branchPattern       : '.*',
                phases              : [
                    [
                        moduleType            : 'openshift',
                        moduleName            : 'cnp-deploy-argorollouts',
                        subCommand            : 'promote',
                        branchPattern         : '',
                        sdlcEnvironment       : 'dev',
                        isProductionDeployment: true,
                        args                  : [
                            //    credentials          : [[id: 'tomdicknharry', env: 'dev']],
                            appName              : 'springpcf-testapp-1-dev',
                            configDir            : 'dev',
                            namespace            : 'pipeline-automation',
                            progressingRetryCount: 10,
                            cluster              : 'hs-1-nonprod',
                            platform             : 'OpenShift',
                            env                  : 'dev'
                        ]
                    ],
                ]
            ],
        ]
        cignaBuildFlow {
            featureFlags = [
                debug                  : true, verbose: true,
                nonProdComplianceChecks: false
            ]
            resourceScaleFactor = 1
            githubConnectionName = 'github'
            commitStatusName = ''
            cloudName = 'hs-pipeline-openshift-devops1'
            webexTeamsRoom = 'bd7cdcb0-27a4-11ed-af92-f5afe966fcf4'
            phases = phasesToRun
        }
        def phases = psc.podSelector.phaseConfigs.find {
            it.containsKey('releaseType') &&
                it.moduleType == 'release'
        }.phases
        then:
        phases.size() == 1
        phases.findAll { it.moduleType == 'mergeback' }.size() == 0

    }

    def '''verify that checkpoints are injected at the correct points in the phase map'''() {
        given:
        BRANCH_NAME = 'release/test'
        scmMock[0].name = BRANCH_NAME
        cignaBuildFlow.env.BRANCH_NAME = 'develop'
        boolean isProductionDeployment = env.BRANCH_NAME ==~ /^(release|hotfix).*/
        def appName = 'pm-validator'
        def nonProdOSServer = 'https://api.hs-3-nonprod.openshift.evernorthcloud.com:6443'
        def nonProdArgoCDServer = 'argocd-devops.apps.hs-3-nonprod.openshift.evernorthcloud.com'
        def nonProdIngressHost = "apps-3.hs-3-nonprod.openshift.evernorthcloud.com"
        def deployableBranchPattern = '^(feature|develop|release|hotfix).*'
        def releaseBranchPattern = '^(release|hotfix).*'

        env.CNP_DEFAULT_JAVA_IMAGE = 'cnp/cnp-docker-maven-java11:1.0.2-dev-ov2'
        env.CNP_DEPLOYABLE_BRANCHES = deployableBranchPattern

        List phasesToRun = []

        def mavenBuild =
            [
                moduleType            : 'maven',
                moduleName            : 'cnp-build-publish-maven-create',
                subCommand            : 'build',
                branchPattern         : '.*',
                releaseBranchPattern  : releaseBranchPattern,
                isProductionDeployment: isProductionDeployment,
                container             : [
                    cpu   : 1000,
                    memory: 1000
                ],
                args                  : [
                    publish: true,
                    pomFile: 'pom.xml'
                ]
            ]


//def sonarScan =
//    [
//        moduleType            : 'quality',
//        moduleName            : 'cnp-quality-check-sonarqube',
//        subCommand            : 'scan',
//        branchPattern         : '.*',
//        sdlcEnvironment       : 'dev',
//        isProductionDeployment: isProductionDeployment,
//        args                  : [
//            projectName               : appName
//        ],
//        container : [
//            cpu   : 1000,
//            memory: 1000
//        ]
//    ]

        def dockerBuildImage =
            [
                moduleType            : 'docker',
                branchPattern         : deployableBranchPattern,
                sdlcEnvironment       : 'dev',
                isProductionDeployment: isProductionDeployment,
                moduleName            : 'cnp-build-image',
                subCommand            : 'buildimage',
                serviceAccount        : 'kaniko',
                args                  : [
                    pomPath            : 'pom.xml',
                    org                : 'payment_method',
                    artifact           : 'lookup:{cnp-build-publish-maven-create:publishUrl}',
                    registryName       : 'registry-dev.cigna.com',
                    publish            : true,
                    generateDockerFile : true,
                    appendCommitIdToTag: true,
                    skipBuildIfExists  : true
                ],
                container             : [
                    cpu   : 1000,
                    memory: 1000
                ]
            ]

        def approveDeploymentDevPreview =
            [
                branchPattern: deployableBranchPattern,
                lintingTypes : [
                    approvalrequest: [
                        message     : 'Do you want to continue deployment to DEV preview?',
                        id          : 'deployApprover',
                        timeOut     : 60,
                        submitter   : '',
                        failedStatus: 'SUCCESS'
                    ]
                ]
            ]

        def deployDevPreview =
            [
                moduleType            : 'openshift',
                moduleName            : 'cnp-deploy-argorollouts',
                subCommand            : 'deploy',
                branchPattern         : deployableBranchPattern,
                sdlcEnvironment       : 'dev',
                isProductionDeployment: isProductionDeployment,
                args                  : [
                    appName              : appName + '-1-dev',
                    namespace            : 'payment-method-dev',
                    platform             : 'OpenShift',
                    imageName            : 'lookup:imageName',
                    imageTag             : 'lookup:tagName',
                    cluster              : 'hs-3-nonprod',
                    useArgoCD            : 'true',
                    argoAppName          : 'pm-dev-pmvalidator-1',
                    server               : nonProdOSServer,
                    argoCDServer         : nonProdArgoCDServer,
                    timeout              : '300s',
                    progressingRetryCount: 10,
                    configDir            : 'dev',
                    valuesFilesDirectory : 'deployments/OpenShift/dev',
                    valuesFile           : 'values.yaml',
                    env                  : 'dev',
                    configRepo           : 'https://github.sys.cigna.com/cigna/payment-method-dev-argocd',
                    ingressBaseHost      : nonProdIngressHost,
                    monitoringSolution   : 'newrelic'
                ],
                container             : [
                    cpu   : 1000,
                    memory: 1000
                ]
            ]

        def approveDeploymentToActive =
            [
                branchPattern: deployableBranchPattern,
                lintingTypes : [
                    approvalrequest: [
                        message     : 'Pausing for if you would like to trigger cucumber manually. Proceed to Active deployment when cucumber is complete or if you want to skip it.',
                        id          : 'deployApprover',
                        timeOut     : 60,
                        submitter   : '',
                        failedStatus: 'SUCCESS'
                    ]
                ]
            ]

        def stageDev =
            [
                moduleType            : 'openshift',
                moduleName            : 'cnp-deploy-argorollouts',
                subCommand            : 'deploy',
                branchPattern         : deployableBranchPattern,
                sdlcEnvironment       : 'dev',
                isProductionDeployment: isProductionDeployment,
                args                  : [
                    appName              : appName + '-1-dev',
                    namespace            : 'payment-method-dev',
                    platform             : 'OpenShift',
                    imageName            : 'lookup:imageName',
                    imageTag             : 'lookup:tagName',
                    cluster              : 'hs-3-nonprod',
                    useArgoCD            : 'true',
                    argoAppName          : 'pm-dev-pmvalidator-1',
                    server               : nonProdOSServer,
                    argoCDServer         : nonProdArgoCDServer,
                    timeout              : '300s',
                    progressingRetryCount: 10,
                    configDir            : 'dev',
                    valuesFilesDirectory : 'deployments/OpenShift/dev',
                    env                  : 'dev',
                    configRepo           : 'https://github.sys.cigna.com/cigna/payment-method-dev-argocd',
                    ingressBaseHost      : nonProdIngressHost,
                    monitoringSolution   : 'newrelic'
                ],
                container             : [
                    cpu   : 1000,
                    memory: 1000
                ]
            ]


        def cutoverDev =
            [
                moduleType            : 'openshift',
                moduleName            : 'cnp-deploy-argorollouts',
                subCommand            : 'promote',
                branchPattern         : deployableBranchPattern,
                sdlcEnvironment       : 'dev',
                isProductionDeployment: isProductionDeployment,
                args                  : [
                    appName              : appName + '-1-dev',
                    configDir            : 'dev',
                    valuesFilesDirectory : 'deployments/OpenShift/dev',
                    namespace            : 'payment-method-dev',
                    cluster              : 'hs-3-nonprod',
                    platform             : 'OpenShift',
                    env                  : 'dev',
                    timeout              : '300s',
                    progressingRetryCount: 4,
                    server               : nonProdOSServer
                ],
                container             : [
                    cpu   : 1000,
                    memory: 1000
                ]
            ]


/*** QA ***/
        def approveDeploymentQaPreview =
            [
                branchPattern: deployableBranchPattern,
                lintingTypes : [
                    approvalrequest: [
                        message     : 'Do you want to continue deployment to QA preview?',
                        id          : 'deployApprover',
                        timeOut     : 60,
                        submitter   : '',
                        failedStatus: 'SUCCESS'
                    ]
                ]
            ]

// Checkpoints are currently disabled in EPF
        def qaCheckPoint =
            [
                checkpointType: 'jenkins',
                branchPattern : '.*',
                name          : 'Resume from QA'
            ]

        def deployQaPreview =
            [
                moduleType            : 'openshift',
                moduleName            : 'cnp-deploy-argorollouts',
                subCommand            : 'deploy',
                branchPattern         : deployableBranchPattern,
                sdlcEnvironment       : 'qa',
                isProductionDeployment: isProductionDeployment,
                args                  : [
                    appName              : appName + '-1-qa',
                    namespace            : 'payment-method-qa',
                    platform             : 'OpenShift',
                    imageName            : 'lookup:imageName',
                    imageTag             : 'lookup:tagName',
                    cluster              : 'hs-3-nonprod',
                    useArgoCD            : 'true',
                    argoAppName          : 'pm-qa-pmvalidator-1',
                    server               : nonProdOSServer,
                    argoCDServer         : nonProdArgoCDServer,
                    timeout              : '300s',
                    progressingRetryCount: 10,
                    configDir            : 'qa',
                    valuesFilesDirectory : 'deployments/OpenShift/qa',
                    valuesFile           : 'values.yaml',
                    env                  : 'qa',
                    configRepo           : 'https://github.sys.cigna.com/cigna/payment-method-qa-argocd',
                    ingressBaseHost      : nonProdIngressHost,
                    monitoringSolution   : 'newrelic'
                ],
                container             : [
                    cpu   : 1000,
                    memory: 1000
                ]
            ]

        def stageQa =
            [
                moduleType            : 'openshift',
                moduleName            : 'cnp-deploy-argorollouts',
                subCommand            : 'deploy',
                branchPattern         : deployableBranchPattern,
                sdlcEnvironment       : 'qa',
                isProductionDeployment: isProductionDeployment,
                args                  : [
                    appName              : appName + '-1-qa',
                    namespace            : 'payment-method-qa',
                    platform             : 'OpenShift',
                    imageName            : 'lookup:imageName',
                    imageTag             : 'lookup:tagName',
                    cluster              : 'hs-3-nonprod',
                    useArgoCD            : 'true',
                    argoAppName          : 'pm-qa-pmvalidator-1',
                    server               : nonProdOSServer,
                    argoCDServer         : nonProdArgoCDServer,
                    timeout              : '300s',
                    progressingRetryCount: 10,
                    configDir            : 'qa',
                    valuesFilesDirectory : 'deployments/OpenShift/qa',
                    env                  : 'qa',
                    configRepo           : 'https://github.sys.cigna.com/cigna/payment-method-qa-argocd',
                    ingressBaseHost      : nonProdIngressHost,
                    monitoringSolution   : 'newrelic'
                ],
                container             : [
                    cpu   : 1000,
                    memory: 1000
                ]
            ]


        def cutoverQa =
            [
                moduleType            : 'openshift',
                moduleName            : 'cnp-deploy-argorollouts',
                subCommand            : 'promote',
                branchPattern         : deployableBranchPattern,
                sdlcEnvironment       : 'qa',
                isProductionDeployment: isProductionDeployment,
                args                  : [
                    appName              : appName + '-1-qa',
                    configDir            : 'qa',
                    valuesFilesDirectory : 'deployments/OpenShift/qa',
                    namespace            : 'payment-method-qa',
                    cluster              : 'hs-3-nonprod',
                    platform             : 'OpenShift',
                    env                  : 'qa',
                    timeout              : '300s',
                    progressingRetryCount: 4,
                    server               : nonProdOSServer
                ],
                container             : [
                    cpu   : 1000,
                    memory: 1000
                ]
            ]

        def approveDeploymentProdPreview =
            [
                branchPattern: deployableBranchPattern,
                lintingTypes : [
                    approvalrequest: [
                        message     : 'Do you want to continue deployment to Prod Preview?',
                        id          : 'deployApprover',
                        timeOut     : 60,
                        submitter   : '',
                        failedStatus: 'SUCCESS'
                    ]
                ]
            ]

// Checkpoints are currently disabled in EPF
        def stagingCheckPoint =
            [
                checkpointType: 'jenkins',
                branchPattern : '.*',
                name          : 'Resume from Staging'
            ]


        def publishProdImage =
            [
                moduleType            : 'docker',
                branchPattern         : deployableBranchPattern,
                sdlcEnvironment       : 'prod',
                isProductionDeployment: isProductionDeployment,
                moduleName            : 'cnp-publish-image',
                subCommand            : 'publishimage',
                serviceAccount        : 'kaniko',
                args                  : [
                    sourceImage: 'lookup:{cnp-build-image:publishUrl}',
                    signImage  : true,
                    repository : 'registry.cigna.com',
                    signTimeout: '120m'
                ],
                container             : [
                    cpu   : 1000,
                    memory: 1000
                ]
            ]

/***---------------------------------------
 Pre-release step, Prod+DR
 ---------------------------------------***/
        def stageProd =
            [
                moduleType            : 'openshift',
                moduleName            : 'cnp-deploy-argorollouts',
                subCommand            : 'deploy',
                sdlcEnvironment       : 'prod',
                isProductionDeployment: isProductionDeployment,
                args                  : [
                    appName              : appName + '-1-prod',
                    namespace            : 'payment-method-prod',
                    platform             : 'OpenShift',
                    imageName            : 'lookup:imageName',
                    imageTag             : 'lookup:tagName',
                    useArgoCD            : 'true',
                    cluster              : 'hs-3-prod',
                    configDir            : 'prod',
                    valuesFilesDirectory : 'deployments/OpenShift/prod',
                    valuesFile           : 'values.yaml',
                    env                  : 'prod',
                    server               : 'https://api.hs-3-prod.openshift.evernorthcloud.com:6443',
                    argoAppName          : 'pm-prod-pmvalidator-1',
                    argoCDServer         : 'argocd-devops.apps.hs-3-prod.openshift.evernorthcloud.com',
                    timeout              : '60s',
                    progressingRetryCount: 10,
                    configRepo           : 'https://github.sys.cigna.com/cigna/payment-method-prod-argocd',
                    ingressBaseHost      : 'apps-1.hs-3-prod.openshift.evernorthcloud.com',
                    monitoringSolution   : 'newrelic'
                ],
                container             : [
                    cpu   : 1000,
                    memory: 1000
                ]
            ]

        List preReleasePhases = []
        preReleasePhases.add(stageProd)

        def preReleaseStep =
            [
                releaseType           : 'preRelease',
                branchPattern         : deployableBranchPattern,
                releaseBranchPattern  : releaseBranchPattern,
                isProductionDeployment: true,
                phases                : preReleasePhases
            ]

        def deployProdActive =
            [
                moduleType            : 'openshift',
                moduleName            : 'cnp-deploy-argorollouts',
                subCommand            : 'deploy',
                sdlcEnvironment       : 'prod',
                isProductionDeployment: true,
                args                  : [
                    appName              : appName + '-1-prod',
                    namespace            : 'payment-method-prod',
                    platform             : 'OpenShift',
                    imageName            : 'lookup:imageName',
                    imageTag             : 'lookup:tagName',
                    cluster              : 'hs-3-prod',
                    useArgoCD            : 'true',
                    argoAppName          : 'pm-prod-pmvalidator-1',
                    server               : 'https://api.hs-3-prod.openshift.evernorthcloud.com:6443',
                    argoCDServer         : 'argocd-devops.apps.hs-3-prod.openshift.evernorthcloud.com',
                    timeout              : '60s',
                    progressingRetryCount: 10,
                    configDir            : 'prod',
                    valuesFilesDirectory : 'deployments/OpenShift/prod',
                    env                  : 'prod',
                    configRepo           : 'https://github.sys.cigna.com/cigna/payment-method-prod-argocd',
                    ingressBaseHost      : 'apps-1.hs-3-prod.openshift.evernorthcloud.com',
                    monitoringSolution   : 'newrelic'
                ],
                container             : [
                    cpu   : 1000,
                    memory: 1000
                ]
            ]

        def promoteProd =
            [
                moduleType            : 'openshift',
                moduleName            : 'cnp-deploy-argorollouts',
                subCommand            : 'promote',
                sdlcEnvironment       : 'prod',
                isProductionDeployment: true,
                args                  : [
                    appName              : appName + '-1-prod',
                    namespace            : 'payment-method-prod',
                    platform             : 'OpenShift',
                    configDir            : 'prod',
                    cluster              : 'hs-3-prod',
                    env                  : 'prod',
                    server               : 'https://api.hs-3-prod.openshift.evernorthcloud.com:6443',
                    argoCDServer         : 'argocd-devops.apps.hs-3-prod.openshift.evernorthcloud.com',
                    timeout              : '120s',
                    progressingRetryCount: 10,
                    monitoringSolution   : 'newrelic'
                ],
                container             : [
                    cpu   : 1000,
                    memory: 1000
                ]
            ]


        List releasePhases = []
        releasePhases.add(deployProdActive)
        releasePhases.add(promoteProd)

        def releaseStep =
            [
                releaseType           : 'release',
                branchPattern         : releaseBranchPattern,
                releaseBranchPattern  : releaseBranchPattern,
                isProductionDeployment: true,
                phases                : releasePhases
            ]

        phasesToRun.add(mavenBuild)
//phasesToRun.add(sonarScan)
        phasesToRun.add(dockerBuildImage)

/*** DEV ***/
        phasesToRun.add(approveDeploymentDevPreview)
        phasesToRun.add(deployDevPreview)
// Cucumber tests work as part of the pipeline, but disabling this for now since we don't have nice cucumber reports
//phasesToRun.add(cucumberTestDev)
        phasesToRun.add(approveDeploymentToActive)
        phasesToRun.add(stageDev)
        phasesToRun.add(cutoverDev)

/*** QA ***/
        phasesToRun.add(approveDeploymentQaPreview)
// Checkpoints are currently disabled in EPF
        phasesToRun.add(qaCheckPoint)
        phasesToRun.add(deployQaPreview)
        phasesToRun.add(approveDeploymentToActive)
        phasesToRun.add(stageQa)
        phasesToRun.add(cutoverQa)


/*** Prod ***/
        phasesToRun.add(approveDeploymentProdPreview)
// Checkpoints are currently disabled in EPF
        phasesToRun.add(stagingCheckPoint)
// EPF doesn't like a second docker build. Add This step back once checkpoints have been reintroduced
//phasesToRun.add(dockerBuildImageProd)
        phasesToRun.add(publishProdImage)
//        phasesToRun.add(preReleaseStep)
//Disable prod release for validator
//phasesToRun.add(releaseStep)

        when:
        cignaBuildFlow {
            githubConnectionName = 'github'
            logHistoryAge = 60
            logHistoryCount = 30
            featureFlags = [
                verbose: true,
                debug  : true
            ]
            commitStatusName = appName
            cloudName = 'financial-entities-openshift-devops1'
            phases = phasesToRun
        }

        then:
        psc.podSelector.phaseConfigs.collect { it.podGroup } == ['financial-entities-openshift-devops1-1',
                                                                 'financial-entities-openshift-devops1-1',
                                                                 'financial-entities-openshift-devops1-1',
                                                                 'financial-entities-openshift-devops1-1',
                                                                 'financial-entities-openshift-devops1-1',
                                                                 'financial-entities-openshift-devops1-1',
                                                                 'financial-entities-openshift-devops1-1',
                                                                 'financial-entities-openshift-devops1-1',
                                                                 'financial-entities-openshift-devops1-1',
                                                                 'checkpoint-1',
                                                                 'financial-entities-openshift-devops1-2',
                                                                 'financial-entities-openshift-devops1-2',
                                                                 'financial-entities-openshift-devops1-2',
                                                                 'financial-entities-openshift-devops1-2',
                                                                 'financial-entities-openshift-devops1-2',
                                                                 'checkpoint-2',
                                                                 'financial-entities-openshift-devops1-3'
        ]
    }


    def '''combining managed pod creation with checkpoints preserves the original pod partitioning'''() {
        given:
        when:
        cignaBuildFlow {
            githubConnectionName = 'Cigna Github'
            commitStatusName = 'Sample JDK App'
            additionalProperties = [[$class              : 'ParametersDefinitionProperty',
                                     parameterDefinitions: [
                                         [$class      : 'StringParameterDefinition',
                                          defaultValue: 'conduit', name: 'TEST_LIB_NAME'],
                                         [$class      : 'StringParameterDefinition',
                                          defaultValue: 'master', name: 'TEST_LIB_VER']]]]
            cloudName = "epf-test-devops1"
            phases = [
                [
                    branchPattern: '.*',
                    podGroup     : 'managed',
                    lintingTypes : [
                        maven: [
                            commandArgs : ['clean', 'verify'],
                            authSettings: true,
                            junit       : [
                                testResults: 'target/surefire-reports/*.xml'
                            ]
                        ]
                    ]
                ],
                [
                    checkpointType: 'simple',
                    name          : 'after linting',
                    branchPattern : '.*'
                ],
                [
                    buildType           : 'maven',
                    checkmarxEnabled    : false,
                    branchPattern       : '.*',
                    releaseBranchPattern: 'master',
                    maven               : [
                        pathToPom   : 'pom.xml',
                        authSettings: true,
                    ],
                    junit               : [
                        testResults: 'target/surefire-reports/*.xml'
                    ],
                    sonarQube           : [
                        credentialsId     : 'sonarqube-demo-token',
                        scannerOptions    : '-Xmx1536m',
                        containerMaxMemory: '2Gi'
                    ],
                    artifactory         : [
                        credentialsId  : 'artifactory-prod-deployer-ci0008907951-apikey',
                        applicationName: '@cigna/doesnt-matter-not-used',
                    ],
                    checkmarx           : [
                        credentialsId: 'global-checkmarx-id',
                        settings     : [
                            CX_PROJECT_TEAM_NAME  : 'DevOps',
                            CX_PROJECT_NAME       : 'Conduit-Project',
                            CX_EXCLUDE_FOLDER_LIST: '.m2',
                        ],
                    ],
                ],
                [
                    packagingType : 'kaniko',
                    branchPattern : '.*',
                    dockerfile    : 'image/Dockerfile',
                    dockerRegistry: 'registry-dev.cigna.com',
                    image         : [
                        org : 'enterprise-devops',
                        name: 'pipeline-integration-testing',
                        tags: [
                            [
                                tag   : '$GIT_COMMIT_SHORT',
                                expire: true
                            ]
                        ]
                    ],
                    quay          : [
                        credentialsId: 'pipeline-dev-quay-token'
                    ]
                ],

            ]
        }

        then:
        assert psc.podSelector.phaseConfigs.collect { it.podGroup } == ['managed-1', 'checkpoint-1', 'managed-2', 'epf-test-devops1-1'

        ]
    }

    def '''test for infinite recursion issues'''() {
        given:
        cignaBuildFlow.env.BRANCH_NAME = 'release/test'
        def currentTimestamp = new Date().format("yyyy-MM-dd'T'HH.mm.ss.SSS'Z'")
        env.CNP_DISABLE_JIRASCAN = true

        def FOLDER_NAME = 'entity-accel-team-16'
        def PROJECT_NAME = 'cidr-validator'

// The conversion-pattern in the variable below is a temporary addition to help with setup.
// /^(develop|integration\/.*|release\/.*|conversion-pattern)$/
        def DEPLOYABLE_BRANCH_PATTERN = /^(develop|integration\/.*|release\/.*)$/

        env.CNP_DEPLOYABLE_BRANCHES = DEPLOYABLE_BRANCH_PATTERN
        def RELEASE_BRANCH_PATTERN = /^(release\/.*)$/
        def ALL_BRANCHES_PATTERN = /^.*$/

        def BRANCH_NAME = env.CHANGE_BRANCH != null ? env.CHANGE_BRANCH : env.BRANCH_NAME

        def isProductionDeployment = BRANCH_NAME ==~ RELEASE_BRANCH_PATTERN
        def isPR = env.CHANGE_BRANCH != null
        def isDeployable = BRANCH_NAME ==~ DEPLOYABLE_BRANCH_PATTERN

// ++VERSION_VARIABLE++

// ++MORE_VARIABLES_GO_HERE++

        env.CNP_DEFAULT_JAVA_IMAGE = 'cnp/cnp-docker-maven-java8:1.0.3-dev-ov2'


        def QUAY_ORG = 'customercontactevents-1'
        def OS_PLATFORM = 'OpenShift'
        def OS_ARGO_REPOSITORY = 'https://github.sys.cigna.com/cigna'
        def OS_MONITORING_SOLUTION = 'newrelic'
        def OS_CLOUD_DOMAIN = 'openshift.evernorthcloud.com'
        def OS_DEPLOY_TIMEOUT = '120s'
        def OS_PROMOTE_TIMEOUT = '30s'
        def OS_DEPLOY_RETRY_COUNT = '20'
        def OS_PROMOTE_RETRY_COUNT = '4'
        def ARGO_SYNC_TIMEOUT = 500

        def OS_NAMESPACE_DEV = 'cidr-validator-dev'
        def OS_CONFIGDIR_DEV = 'dev'
        def OS_ENV_DEV = 'dev'
        env.CNP_ARGOCD_CRED_DEV = 'cidr-validator-dev-CNP_ARGOCD_CRED_DEV'
        env.CNP_OC_CRED_DEV = 'cidr-validator-dev-CNP_OC_CRED_DEV'

        def OS_NAMESPACE_QA = 'cidr-validator-qa'
        def OS_CONFIGDIR_QA = 'qa'
        def OS_ENV_QA = 'qa'
        env.CNP_ARGOCD_CRED_QA = 'cidr-validator-qa-CNP_ARGOCD_CRED_QA'
        env.CNP_OC_CRED_QA = 'cidr-validator-qa-CNP_OC_CRED_QA'


        def QUAY_REGISTRY_DEV = 'registry-dev.cigna.com'
        def OS_CLUSTER_NONPROD_DEV = 'hs-7-nonprod'
        def OS_CLUSTER_NONPROD_QA = 'hs-7-nonprod'
        def OS_INGRESS_HOST_NONPROD_DEV = "apps-1.${OS_CLUSTER_NONPROD_DEV}.${OS_CLOUD_DOMAIN}"
        def OS_SERVER_NONPROD_DEV = "https://api.${OS_CLUSTER_NONPROD_DEV}.${OS_CLOUD_DOMAIN}:6443"
        def OS_ARGOCD_SERVER_NONPROD_DEV = "argocd-devops.apps.${OS_CLUSTER_NONPROD_DEV}.${OS_CLOUD_DOMAIN}:443"
        def OS_INGRESS_HOST_NONPROD_QA = "apps-1.${OS_CLUSTER_NONPROD_QA}.${OS_CLOUD_DOMAIN}"
        def OS_SERVER_NONPROD_QA = "https://api.${OS_CLUSTER_NONPROD_QA}.${OS_CLOUD_DOMAIN}:6443"
        def OS_ARGOCD_SERVER_NONPROD_QA = "argocd-devops.apps.${OS_CLUSTER_NONPROD_QA}.${OS_CLOUD_DOMAIN}:443"

        def QUAY_REGISTRY_PROD = 'registry.cigna.com'
        def OS_CLUSTER_PROD = 'hs-8-prod'
        def OS_INGRESS_HOST_PROD = "apps-2.${OS_CLUSTER_PROD}.${OS_CLOUD_DOMAIN}"
        def OS_SERVER_PROD = "https://api.${OS_CLUSTER_PROD}.${OS_CLOUD_DOMAIN}:6443"
        def OS_ARGOCD_SERVER_PROD = "argocd-devops.apps.${OS_CLUSTER_PROD}.${OS_CLOUD_DOMAIN}:443"
        def OS_NAMESPACE_PROD = 'cidr-validator-prod'
        def OS_CONFIGDIR_PROD = 'prod'
        def OS_ENV_PROD = 'prod'
        env.CNP_ARGOCD_CRED_PROD = 'cidr-validator-prod-CNP_ARGOCD_CRED_PROD'
        env.CNP_OC_CRED_PROD = 'cidr-validator-prod-CNP_OC_CRED_PROD'


//Existing PCF name:
        def appName = 'cidr-validator'
//will be cidr-validator in OpenShift, to match the Git repo name


//boolean isProductionDeployment = env.BRANCH_NAME ==~ /^(release|hotfix).*/

        List phasesToRun = []

        def buildPhases = [
            [
                moduleType            : "maven",
                moduleName            : "cnp-build-publish-maven-create",
                subCommand            : "build",
                container             : [
                    cpu   : 2000,
                    memory: 2000
                ],
                branchPattern         : ALL_BRANCHES_PATTERN,
                releaseBranchPattern  : RELEASE_BRANCH_PATTERN,
                isProductionDeployment: isProductionDeployment,
                args                  : [
                    publish: false
                ]
            ]
        ]
        phasesToRun.addAll(buildPhases)

        def sonarPhases = [
            [
                moduleType            : 'quality',
                moduleName            : 'cnp-quality-check-sonarqube',
                subCommand            : 'scan',
                branchPattern         : ALL_BRANCHES_PATTERN,
                releaseBranchPattern  : RELEASE_BRANCH_PATTERN,
                isProductionDeployment: isProductionDeployment,
                args                  : [
                    projectName: PROJECT_NAME
                ]
            ]
        ]
        phasesToRun.addAll(sonarPhases)

        def versionValidationPhases = [
            [
                freestyleType         : 'Validate Version',
                branchPattern         : ALL_BRANCHES_PATTERN,
                releaseBranchPattern  : RELEASE_BRANCH_PATTERN,
                isProductionDeployment: isProductionDeployment,
                container             : [
                    image  : env.CNP_DEFAULT_JAVA_IMAGE.split(':')[0],
                    version: env.CNP_DEFAULT_JAVA_IMAGE.split(':')[1]
                ],
                script                : "bash validate-version.sh $isProductionDeployment"
            ]
        ]
        if (!isPR) {
            phasesToRun.addAll(versionValidationPhases)
        }

        def publishPhases = [
            [
                moduleType            : "maven",
                moduleName            : "cnp-build-publish-maven-create",
                subCommand            : "build",
                stageName             : 'maven: PUBLISH',
                branchPattern         : ALL_BRANCHES_PATTERN,
                releaseBranchPattern  : RELEASE_BRANCH_PATTERN,
                isProductionDeployment: isProductionDeployment,
                args                  : [
                    buildGoals: '--version',
                    publish   : !isPR
                ]
            ]
        ]
        if (!isPR) {
            phasesToRun.addAll(publishPhases)
        }


        def dockerPhases = [
            [
                moduleType            : 'docker',
                container             : [
                    cpu   : 2000,
                    memory: 2000
                ],
                moduleName            : 'cnp-build-image',
                subCommand            : 'buildimage',
                serviceAccount        : 'kaniko',
                branchPattern         : DEPLOYABLE_BRANCH_PATTERN,
                releaseBranchPattern  : RELEASE_BRANCH_PATTERN,
                isProductionDeployment: isProductionDeployment,
                args                  : [
                    org                : QUAY_ORG,
                    registry           : QUAY_REGISTRY_DEV,
                    imageName          : "cidr-validator",
                    appendCommitIdToTag: true,
                    imageTag           : currentTimestamp,
                    generateDockerfile : false,
                    pomPath            : 'pom.xml'
                ]
            ]
        ]
        phasesToRun.addAll(dockerPhases)

        def osDevDeployPhases = [
            [
                moduleType            : 'openshift',
                moduleName            : 'cnp-deploy-argorollouts',
                subCommand            : 'deploy',
                sdlcEnvironment       : OS_ENV_DEV,
                branchPattern         : DEPLOYABLE_BRANCH_PATTERN,
                releaseBranchPattern  : RELEASE_BRANCH_PATTERN,
                isProductionDeployment: isProductionDeployment,
                args                  : [
                    platform             : OS_PLATFORM,
                    configDir            : OS_CONFIGDIR_DEV,
                    cluster              : OS_CLUSTER_NONPROD_DEV,
                    imageName            : 'lookup:imageName',
                    imageTag             : 'lookup:tagName',
                    ingressBaseHost      : OS_INGRESS_HOST_NONPROD_DEV,
                    server               : OS_SERVER_NONPROD_DEV,
                    argoCDServer         : OS_ARGOCD_SERVER_NONPROD_DEV,
                    appName              : "cidr-validator-dev",
                    useArgoCD            : true,
                    argoAppName          : "cidr-validator-dev",
                    namespace            : OS_NAMESPACE_DEV,
                    timeout              : OS_DEPLOY_TIMEOUT,
                    argoSyncTimeout      : ARGO_SYNC_TIMEOUT,
                    progressingRetryCount: OS_DEPLOY_RETRY_COUNT,
                    configRepo           : "$OS_ARGO_REPOSITORY/$OS_NAMESPACE_DEV-argocd",
                    monitoringSolution   : OS_MONITORING_SOLUTION,
                    env                  : OS_ENV_DEV,
                    additionalValuesFiles: "values.yaml" //"values-${VERSION}.yaml"
                ]
            ],
            [
                moduleType            : 'openshift',
                moduleName            : 'cnp-deploy-argorollouts',
                subCommand            : 'promote',
                sdlcEnvironment       : OS_ENV_DEV,
                branchPattern         : DEPLOYABLE_BRANCH_PATTERN,
                releaseBranchPattern  : DEPLOYABLE_BRANCH_PATTERN,
                isProductionDeployment: isProductionDeployment,
                args                  : [
                    platform             : OS_PLATFORM,
                    configDir            : OS_CONFIGDIR_DEV,
                    cluster              : OS_CLUSTER_NONPROD_DEV,
                    appName              : "cidr-validator-dev",
                    timeout              : OS_PROMOTE_TIMEOUT,
                    progressingRetryCount: OS_PROMOTE_RETRY_COUNT,
                    namespace            : OS_NAMESPACE_DEV,
                    env                  : OS_ENV_DEV,
                    server               : OS_SERVER_NONPROD_DEV
                ]
            ]
        ]
        phasesToRun.addAll(osDevDeployPhases)


        def osQaDeployPhases = [
            [
                moduleType            : 'openshift',
                moduleName            : 'cnp-deploy-argorollouts',
                subCommand            : 'deploy',
                sdlcEnvironment       : OS_ENV_QA,
                branchPattern         : DEPLOYABLE_BRANCH_PATTERN,
                releaseBranchPattern  : RELEASE_BRANCH_PATTERN,
                isProductionDeployment: isProductionDeployment,
                args                  : [
                    platform             : OS_PLATFORM,
                    configDir            : OS_CONFIGDIR_QA,
                    cluster              : OS_CLUSTER_NONPROD_QA,
                    imageName            : 'lookup:imageName',
                    imageTag             : 'lookup:tagName',
                    ingressBaseHost      : OS_INGRESS_HOST_NONPROD_QA,
                    server               : OS_SERVER_NONPROD_QA,
                    argoCDServer         : OS_ARGOCD_SERVER_NONPROD_QA,
                    appName              : "cidr-validator-qa",
                    useArgoCD            : true,
                    argoAppName          : "cidr-validator-qa",
                    namespace            : OS_NAMESPACE_QA,
                    timeout              : OS_DEPLOY_TIMEOUT,
                    argoSyncTimeout      : ARGO_SYNC_TIMEOUT,
                    progressingRetryCount: OS_DEPLOY_RETRY_COUNT,
                    configRepo           : "$OS_ARGO_REPOSITORY/$OS_NAMESPACE_QA-argocd",
                    monitoringSolution   : OS_MONITORING_SOLUTION,
                    env                  : OS_ENV_QA,
                    additionalValuesFiles: "values.yaml"
                ]
            ],
            [
                moduleType            : 'openshift',
                moduleName            : 'cnp-deploy-argorollouts',
                subCommand            : 'promote',
                sdlcEnvironment       : OS_ENV_QA,
                branchPattern         : DEPLOYABLE_BRANCH_PATTERN,
                releaseBranchPattern  : DEPLOYABLE_BRANCH_PATTERN,
                isProductionDeployment: isProductionDeployment,
                args                  : [
                    platform             : OS_PLATFORM,
                    configDir            : OS_CONFIGDIR_QA,
                    cluster              : OS_CLUSTER_NONPROD_QA,
                    appName              : "cidr-validator-qa",
                    timeout              : OS_PROMOTE_TIMEOUT,
                    progressingRetryCount: OS_PROMOTE_RETRY_COUNT,
                    namespace            : OS_NAMESPACE_QA,
                    env                  : OS_ENV_QA,
                    server               : OS_SERVER_NONPROD_QA
                ]
            ]
        ]
        phasesToRun.addAll(osQaDeployPhases)


        List preReleasePhases = []
        List releasePhases = []
        def osProdReleasePhases = [
            [
                moduleType            : 'openshift',
                moduleName            : 'cnp-deploy-argorollouts',
                subCommand            : 'deploy',
                sdlcEnvironment       : OS_ENV_PROD,
                branchPattern         : DEPLOYABLE_BRANCH_PATTERN,
                releaseBranchPattern  : RELEASE_BRANCH_PATTERN,
                isProductionDeployment: isProductionDeployment,
                args                  : [
                    platform             : OS_PLATFORM,
                    configDir            : OS_CONFIGDIR_PROD,
                    cluster              : OS_CLUSTER_PROD,
                    imageName            : 'lookup:imageName',
                    imageTag             : 'lookup:tagName',
                    ingressBaseHost      : OS_INGRESS_HOST_PROD,
                    server               : OS_SERVER_PROD,
                    argoCDServer         : OS_ARGOCD_SERVER_PROD,
                    appName              : "cidr-validator-prod",
                    useArgoCD            : true,
                    argoAppName          : "cidr-validator-prod",
                    namespace            : OS_NAMESPACE_PROD,
                    timeout              : OS_DEPLOY_TIMEOUT,
                    argoSyncTimeout      : ARGO_SYNC_TIMEOUT,
                    progressingRetryCount: OS_DEPLOY_RETRY_COUNT,
                    configRepo           : "$OS_ARGO_REPOSITORY/$OS_NAMESPACE_PROD-argocd",
                    monitoringSolution   : OS_MONITORING_SOLUTION,
                    env                  : OS_ENV_PROD,
                    additionalValuesFiles: 'values.yaml'
                ]
            ],
            [
                moduleType            : 'openshift',
                moduleName            : 'cnp-deploy-argorollouts',
                subCommand            : 'promote',
                sdlcEnvironment       : OS_ENV_PROD,
                branchPattern         : DEPLOYABLE_BRANCH_PATTERN,
                releaseBranchPattern  : DEPLOYABLE_BRANCH_PATTERN,
                isProductionDeployment: isProductionDeployment,
                args                  : [
                    platform             : OS_PLATFORM,
                    configDir            : OS_CONFIGDIR_PROD,
                    cluster              : OS_CLUSTER_PROD,
                    appName              : "cidr-validator-prod",
                    timeout              : OS_PROMOTE_TIMEOUT,
                    progressingRetryCount: OS_PROMOTE_RETRY_COUNT,
                    namespace            : OS_NAMESPACE_PROD,
                    env                  : OS_ENV_PROD,
                    server               : OS_SERVER_PROD
                ]
            ]
        ]
        releasePhases.addAll(osProdReleasePhases)

        def preReleaseAndReleasePhases = [
            [
                branchPattern         : RELEASE_BRANCH_PATTERN,
                releaseBranchPattern  : RELEASE_BRANCH_PATTERN,
                isProductionDeployment: isProductionDeployment,
                stageName             : 'approval: PROD Candidate',
                lintingTypes          : [
                    approvalrequest: [
                        id       : 'prod-candidate-approval',
                        message  : 'Deploy to Prod/DR Candidate',
                        timeOut  : 30,
                        submitter: 'Entity Acceleration Team'
                    ]
                ]
            ],
            [
                releaseType         : 'preRelease',
                branchPattern       : RELEASE_BRANCH_PATTERN,
                releaseBranchPattern: RELEASE_BRANCH_PATTERN,
                phases              : preReleasePhases
            ],
            [
                branchPattern         : RELEASE_BRANCH_PATTERN,
                releaseBranchPattern  : RELEASE_BRANCH_PATTERN,
                isProductionDeployment: isProductionDeployment,
                stageName             : 'approval: XLR Release',
                lintingTypes          : [
                    approvalrequest: [
                        id       : 'prod-release-approval',
                        message  : 'Request XLR Release',
                        timeOut  : 30,
                        submitter: 'Entity Acceleration Team'
                    ]
                ]
            ],
            [
                releaseType         : 'release',
                branchPattern       : RELEASE_BRANCH_PATTERN,
                releaseBranchPattern: RELEASE_BRANCH_PATTERN,
                phases              : releasePhases
            ]
        ]
        phasesToRun.addAll(preReleaseAndReleasePhases)


        when:
        if (true) {
            cignaBuildFlow {
                jnlp = [cpu: 1000, memory: 1000]
                githubConnectionName = 'github'
                githubCredentialsId = 'GIT_TOKEN'
                commitStatusName = PROJECT_NAME
                cloudName = "$FOLDER_NAME-openshift-devops1"
                phases = phasesToRun
            }
        } else {
            echo 'Feature branches are not buildable'
        }
        then:
        assert psc.podSelector.podTemplates.size() > 0
    }

    def '''metadataInArgs can be set at the phase level'''() {
        given:
        def testPhases = [
            [
                moduleType            : "maven",
                moduleName            : "cnp-build-publish-maven-create",
                subCommand            : "build",
                stageName             : 'maven: PUBLISH',
                branchPattern         : '.*',
                isProductionDeployment: false
            ] + metadataConfig
        ]
        psc.metadata.put('key', 'value')

        // FeatureFlags set as part of PSC construction which isn't done when PSC is injected
        // setting verbose here to get the desired logging of metadataInArgs
        FeatureFlags.setFromMap([verbose: true, reportOnNamespace: false])

        when:
        cignaBuildFlow {
            githubConnectionName = 'github'
            cloudName = 'cloud'
            phases = testPhases
        }

        then:
        // the module run does or doesn't get the arg from metadata
        1 * getPipelineMock('sh')({ it.script.startsWith('cnp-build-publish') && (it.script.contains('"key":"value"') == metaIncluded) })
        // the global config doesn't contain metadataInArgs because it wasn't set at the top level
        1 * getPipelineMock('echo')({ it?.startsWith('Global Config applied') && !it?.contains('metadataInArgs') })
        where:
        metadataConfig << [[metadataInArgs: true], [metadataInArgs: false], [:]]
        metaIncluded << [true, false, false]
    }

    def '''metadataInArgs can be set at the top level and overridden at the phase level'''() {
        given:
        def testPhases = [
            // possibly overridden phase
            [
                moduleType            : "maven",
                moduleName            : "cnp-build-publish-maven-create",
                subCommand            : "build",
                stageName             : 'maven: PUBLISH',
                branchPattern         : '.*',
                isProductionDeployment: false
            ] + phaseMeta,
            // always gets top-level setting
            [
                moduleType            : 'docker',
                moduleName            : 'cnp-build-image',
                subCommand            : 'buildimage',
                serviceAccount        : 'kaniko',
                branchPattern         : '.*',
                isProductionDeployment: false,
            ]
        ]
        psc.metadata.put('key', 'value')

        // FeatureFlags set as part of PSC construction which isn't done when PSC is injected
        // setting verbose here to get the desired logging of metadataInArgs
        FeatureFlags.setFromMap([verbose: true, reportOnNamespace: false])

        when:
        cignaBuildFlow {
            githubConnectionName = 'github'
            cloudName = 'cloud'
            metadataInArgs = topLevelMeta
            phases = testPhases
        }

        then:
        // the maven module run does or doesn't get the arg from metadata
        1 * getPipelineMock('sh')({ it.script.startsWith('cnp-build-publish') && (it.script.contains('"key":"value"') == mavenCallGetsArg) })
        // the docker module run does or doesn't get the arg (based on top-level setting)
        1 * getPipelineMock('sh')({ it.script.startsWith('cnptools') && (it.script.contains('"key":"value"') == topLevelMeta) })
        // the global config contains metadataInArgs because it was set at the top level
        1 * getPipelineMock('echo')({ it?.startsWith('Global Config applied') && it?.contains("metadataInArgs:$topLevelMeta") })
        where:
        phaseMeta               | topLevelMeta | mavenCallGetsArg
        [metadataInArgs: true]  | true         | true
        [metadataInArgs: true]  | false        | true
        [metadataInArgs: false] | true         | false
        [metadataInArgs: false] | false        | false
        [:]                     | true         | true
        [:]                     | false        | false
    }

    def '''parallel phases get container names'''() {
        given:
        def configByName = { containerName ->
            psc.podSelector.podGenerators['cloud'].podTemplate.spec.containers.find { it.name == containerName }
        }
        Object containerPythonFreestyleResourceRequest = [
            image  : 'enterprise-devops/python',
            version: 'python-39-ubi9-v2',
            cpu    : 1500,
            memory : 2000,
        ]
        def sdlcEnvironment = 'whatever'
        when:
        cignaBuildFlow {
            githubConnectionName = 'github'
            cloudName = 'cloud'
            phases = [
                [
                    parallelType   : 'jenkins',
                    branchPattern  : '.*',
                    sdlcEnvironment: sdlcEnvironment,
                    phases         : [
                        jsonLint: [
                            freestyleType   : 'Linting JSON',
                            branchPattern   : '.*',
                            sdlcEnvironment : sdlcEnvironment,
                            sonarEnabled    : false,
                            checkmarxEnabled: false,
                            container       : containerPythonFreestyleResourceRequest,
                            script          : 'python3 -m ./lint_json.py'
                        ],
                        yamlLint: [
                            freestyleType   : 'Linting YAML',
                            branchPattern   : '.*',
                            sdlcEnvironment : sdlcEnvironment,
                            sonarEnabled    : false,
                            checkmarxEnabled: false,
                            container       : containerPythonFreestyleResourceRequest,
                            script          : 'python3 -m pip install yamllint && python3 -m ruff check'
                        ]
                    ]
                ]
            ]
        }
        then:
        def resultConfig = configByName(Utils.calculateContainerName(containerPythonFreestyleResourceRequest.image,
            containerPythonFreestyleResourceRequest.version))
        resultConfig.resources.limits == [cpu: '1500m', memory: '2000Mi']
    }


    def '''test nested jmeter testing phase is included in pod template'''() {
        given:
        explicitlyMockPipelineVariable("openshiftSecret")
        when:
        cignaBuildFlow {
            baseDirectory = './dkc-frontend'
            gitlabConnectionName = 'gitlab_server'
            featureFlags = [nonProdComplianceChecks: false]
            cloudName = 'enterprise-search-openshift-devops1'
            resourceScaleFactor = 1
            phases = [
                [
                    deploymentType        : 'openshift',
                    branchPattern         : '.*',
                    podGroup              : 'deploy_environment',
                    container             : [
                        cpu   : 500, // default is 2000m
                        memory: 500, // default is 2000
                    ],
                    sdlcEnvironment       : 'Prod',
                    isProductionDeployment: true,

                    openshift             : [
                        url          : "eee",
                        user         : 'svp_dkc_infrastruct',
                        credentialsId: 'ddd',
                        project      : "dkc-dev"
                    ],
                    deployScript          : "ls",
                    rollbackScript        : "helm rollback react-frontend 0",
                    testing               : [
                        [
                            testType    : 'JMeter',
                            planPath    : "../jmeter-tests/jmeter-dev.jmx",
                            typeOverride: 'integration'
                        ]
                    ]
                ]
            ]
        }

        then:
        assert psc.podSelector.podGenerators.size() == 2
        assert psc.podSelector.podTemplates.size() == 2
        assert psc.podSelector.podGenerators['deploy_environment'].podTemplate.spec.containers.any { it.name == 'jmeterv564' }
    }
}
