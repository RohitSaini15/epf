package com.cigna.builds

import com.cigna.SinglePodTest
import com.cigna.common.notification.Notification
import com.cigna.common.utils.FeatureFlags
import com.cigna.mocks.MockScript
import com.cigna.state.PipelineStateContext

class PlzBuildSpec extends SinglePodTest {
    def cloudName = 'test-cloud'

    def setup() {
        explicitlyMockPipelineVariable('steps')
        explicitlyMockPipelineStep('updateGitStatus')
        explicitlyMockPipelineStep('gitUsernamePassword')
        explicitlyMockPipelineVariable("GIT_USERNAME")
        explicitlyMockPipelineVariable("GIT_PASSWORD")
        initScriptAndPsc()
    }

    // Mock process
    Notification notification = Mock()

    def """When executeBuildAndTestStage without awsFed. No fed is called, then plz build is called with
        build compile label"""() {
        when:
        def plzBuild = new PlzBuild(config: [cloudName: cloudName], script: script, notification: notification, psc: psc)
        simulatePodTemplate(psc, plzBuild, cloudName)
        plzBuild.executeBuildAndTestStage()

        then:
        1 * getPipelineMock("sh")({ it ==~ /plz build \/\/... -i compile .*/ })
        1 * getPipelineMock("sh")({ it ==~ /plz test \/\/... -i unit .*/ })
    }

    def """When executeBuildAndTestStage without awsFed and with verbosity. No fed is called, then plz build is called
        with build compile label and verbosity"""() {
        when:
        def plzBuild = new PlzBuild(config: [
            cloudName    : cloudName,
            verbosityFlag: '-vvv'
        ], script: script, notification: notification, psc: psc)
        simulatePodTemplate(psc, plzBuild, cloudName)
        plzBuild.executeBuildAndTestStage()

        then:
        1 * getPipelineMock("sh")({ it == 'plz build //... -i compile --show_all_output -vvv' })
        1 * getPipelineMock("sh")({ it == 'plz test //... -i unit --show_all_output -vvv' })
    }

    def """When executeBuildAndTestStage with awsFed, first fed is called, then plz build is called with
        build compile label"""() {
        given:
        def numCredentialCalls = 1
        if (FeatureFlags.podAutotuning.enabled) {
            numCredentialCalls = 2
        }
        when:
        def plzBuild = new PlzBuild(config: [
            cloudName: cloudName,
            awsFed   : [
                credentialsId: 'test-creds'
            ]
        ], script: script, notification: notification, psc: psc)
        explicitlyMockPipelineVariable("AWS_FED_USERNAME")
        explicitlyMockPipelineVariable("AWS_FED_PASSWORD")
        simulatePodTemplate(psc, plzBuild, cloudName)
        plzBuild.executeBuildAndTestStage()

        then:
        numCredentialCalls * getPipelineMock("withCredentials")(*_)
        1 * getPipelineMock("usernamePassword.call")(*_)
        1 * getPipelineMock("sh")({ it ==~ /export AWS_FED_PASSWORD=.*/ })
        1 * getPipelineMock("sh")({ it ==~ /plz fed null/ })
        1 * getPipelineMock("sh")({ it ==~ /plz build \/\/... -i compile .*/ })
        1 * getPipelineMock("sh")({ it ==~ /plz test \/\/... -i unit .*/ })
    }

    def """When executePublishStage is called without tag element, just please publish is called. """() {
        when:
        def plzBuild = new PlzBuild(config: [cloudName: cloudName], script: script, notification: notification, psc: psc)
        simulatePodTemplate(psc, plzBuild, cloudName)
        plzBuild.executePublishStage()

        then:
        1 * getPipelineMock("sh")({ it ==~ /plz build \/\/... -i publish .*/ })
    }

    def """When executePublishStage is called without tag element and with verbosity, just please publish is called
        with verbose flags. """() {
        when:
        def plzBuild = new PlzBuild(config: [
            cloudName    : cloudName,
            verbosityFlag: '-vvv'
        ], script: script, notification: notification, psc: psc)
        simulatePodTemplate(psc, plzBuild, cloudName)
        plzBuild.executePublishStage()

        then:
        1 * getPipelineMock("sh")({ it ==~ /plz build \/\/... -i publish --show_all_output -vvv/ })
    }


    def """When executePublishStage is called with tag element, please publish is called and tag is created"""() {
        when:
        def plzBuild = new PlzBuild(config: [
            cloudName : cloudName,
            tagDetails: [
                branch       : 'mock-branch',
                gitTagCredKey: 'gitlab-api-token',
                tagFile      : './module.version',
            ]
        ], script: script, notification: notification, psc: psc)
        simulatePodTemplate(psc, plzBuild, cloudName)
        plzBuild.executePreBuildStage()
        plzBuild.executePublishStage()

        then:
        1 * getPipelineMock("readFile")(*_) >> "1.0.1\n"
        1 * getPipelineMock("sh")({ it ==~ /plz build \/\/... -i publish .*/ })
        1 * getPipelineMock('sh')("git config --worktree user.email 'Mock Generator for [GIT_USERNAME]@cigna.com' && git config --worktree user.name 'Mock Generator for [GIT_USERNAME]'")
    }

    def """Succeed validation when required config items are provided and fail when using
            improper config, runInAWS in this case"""() {
        when:
        def plzBuild = new PlzBuild(config: [
            runInAWS  : whereAWS,
            cloudName : cloudName,
            checkmarx : [
                credentialsId: 'test',
                settings     : [
                    CX_PROJECT_TEAM_NAME: 'test'
                ]
            ],
            sonarQube : [
                credentialsId: 'test',
                mainBranch   : 'test'
            ],
            awsFed    : [
                credentialsId: 'test'
            ],
            tagDetails: [
                gitTagCredKey: 'test',
                branch       : 'test|main',
                tagFile      : 'test'
            ]
        ], script: script, psc: psc)
        def issues = plzBuild.validate()

        then:
        assert issues.size() == numberOfIssues

        where:
        whereAWS << [null, true]
        numberOfIssues << [0, 1]
    }

    def """When awsFed is given and awsFed.credentialsId is not, then validation fails"""() {
        when:
        def plzBuild = new PlzBuild(
            config: [
                cloudName: cloudName,
                checkmarx: [
                    credentialsId: 'test',
                    settings     : [
                        CX_PROJECT_TEAM_NAME: 'test'
                    ]
                ],
                sonarQube: [
                    credentialsId: 'test'
                ],
                awsFed   : [
                    notCredentialsId: 'test'
                ]
            ], script: script,
            psc: psc
        )
        def issues = plzBuild.validate()

        then:
        assert issues.contains('Missing required Plz Build specification: Missing AWS Fed Credentials')
    }

    def """When usePublishAlias is given and both artifactory.version and tagDetails is missing, then validation fails"""() {
        when:
        def plzBuild = new PlzBuild(
            config: [
                cloudName      : cloudName,
                usePublishAlias: true,
                checkmarx      : [
                    credentialsId: 'test',
                    settings     : [
                        CX_PROJECT_TEAM_NAME: 'test'
                    ]
                ],
                sonarQube      : [
                    credentialsId: 'test'
                ],
            ], script: script,
            psc: psc
        )
        def issues = plzBuild.validate()

        then:
        assert issues.contains('Missing required Plz Build specification: Version string not specified. Either set artifactory.version or provide tagDetails')
    }

    def """When usePublishAlias is given and tagDetails is provided, then validation succeeds"""() {
        when:
        def plzBuild = new PlzBuild(
            config: [
                usePublishAlias: true,
                checkmarx      : [
                    credentialsId: 'test',
                    settings     : [
                        CX_PROJECT_TEAM_NAME: 'test'
                    ]
                ],
                sonarQube      : [
                    credentialsId: 'test'
                ],
                tagDetails     : [
                    gitTagCredKey: 'testKey',
                    branch       : 'release',
                    tagFile      : 'testFile'
                ]
            ],
            script: script,
            psc: psc
        )
        def issues = plzBuild.validate()

        then:
        assert !issues.contains('Missing required Plz Build specification: Version string not specified. Either set artifactory.version or provide tagDetails')
    }

    def """When usePublishAlias is given and artifactory.version is provided, then validation succeeds"""() {
        when:
        def plzBuild = new PlzBuild(
            config: [
                usePublishAlias: true,
                checkmarx      : [
                    credentialsId: 'test',
                    settings     : [
                        CX_PROJECT_TEAM_NAME: 'test'
                    ]
                ],
                sonarQube      : [
                    credentialsId: 'test'
                ],
                artifactory    : [
                    credentialsId: 'testId',
                    version      : 'testVersion'
                ]
            ],
            script: script,
            psc: psc
        )
        def issues = plzBuild.validate()

        then:
        assert !issues.contains('Missing required Plz Build specification: Version string not specified. Either set artifactory.version or provide tagDetails')
    }

    def """When tagDetails are given and required parameters are not, then validation fails"""() {
        when:
        def plzBuild = new PlzBuild(
            config: [
                checkmarx : [
                    credentialsId: 'test',
                    settings     : [
                        CX_PROJECT_TEAM_NAME: 'test'
                    ]
                ],
                sonarQube : [
                    credentialsId: 'test',
                    mainBranch   : 'test'
                ],
                tagDetails: [
                    gitTagCredKey: whereGitTagCredKey,
                    branch       : whereBranch,
                    tagFile      : whereTagFile
                ]
            ],
            script: script,
            psc: psc
        )
        def issues = plzBuild.validate()
        then:
        assert issues.size() == whereIssuesSize
        where:
        whereGitTagCredKey << [null, 'test', 'test', null, 'test', null, null]
        whereBranch << ['test', null, 'test', null, null, 'test', null]
        whereTagFile << ['test', 'test', null, 'test', null, null, null]
        whereIssuesSize << [1, 1, 1, 2, 2, 2, 3]
    }

    def """When executeBuildAndTestStage is called with user provided extraArgs, they are input into plz command"""() {
        when:
        def plzBuild = new PlzBuild(config: [
            extraBuildArgs: whereExtraBuildArgs,
            extraTestArgs : whereExtraTestArgs
        ], script: script, notification: notification, psc: psc)
        plzBuild.executeBuildAndTestStage()

        then:
        withExtraBuildArgs * getPipelineMock("sh")({ it == 'plz build //... -i compile --show_all_output test' })
        withExtraTestArgs * getPipelineMock("sh")({ it == 'plz test //... -i unit --show_all_output test' })
        withoutExtraBuildArgs * getPipelineMock("sh")({ it == 'plz build //... -i compile --show_all_output' })
        withoutExtraTestArgs * getPipelineMock("sh")({ it == 'plz test //... -i unit --show_all_output' })

        where:
        whereExtraBuildArgs << ['test', null, 'test', null]
        withExtraBuildArgs << [1, 0, 1, 0]
        withoutExtraBuildArgs << [0, 1, 0, 1]
        whereExtraTestArgs << ['test', null, null, 'test']
        withExtraTestArgs << [1, 0, 0, 1]
        withoutExtraTestArgs << [0, 1, 1, 0]
    }

    def """When executeBuildAndTestStage with modules configured, 
        only the given modules are targeted for build and test"""() {
        when:
        def plzBuild = new PlzBuild(
            config: [modules: ['//module/aws/module1', '//module/databricks/module2']],
            script: script,
            notification: notification,
            psc: psc
        )
        plzBuild.executeBuildAndTestStage()

        then:
        1 * getPipelineMock('sh')({
            it == 'plz build //module/aws/module1/... -i compile --show_all_output' +
                ' && plz build //module/databricks/module2/... -i compile --show_all_output'
        })
        1 * getPipelineMock('sh')({
            it == 'plz test //module/aws/module1/... -i unit --show_all_output' +
                ' && plz test //module/databricks/module2/... -i unit --show_all_output'
        })

        when:
        def plzBuildPrefix = new PlzBuild(
            config: [modules: ['//module/gcp/module1']],
            script: script,
            notification: notification,
            psc: psc
        )
        plzBuildPrefix.executeBuildAndTestStage()

        then:
        1 * getPipelineMock('sh')({ it == 'plz build //module/gcp/module1/... -i compile --show_all_output' })
        1 * getPipelineMock('sh')({ it == 'plz test //module/gcp/module1/... -i unit --show_all_output' })
    }

    def """When executePublishStage with modules configured, 
        only the given modules are targeted for publishing"""() {
        when:
        def plzBuild = new PlzBuild(
            config: [modules: ['//module/aws/module1', '//module/aws/module2']],
            script: script,
            notification: notification,
            psc: psc
        )

        plzBuild.executePublishStage()

        then:
        1 * getPipelineMock('sh')({
            it == 'plz build //module/aws/module1/... -i publish --show_all_output' +
                ' && plz build //module/aws/module2/... -i publish --show_all_output'
        })

    }

    def """When executeBuildAndTestStage with modules configured but empty, 
        nothing is targeted for build"""() {
        when:
        def plzBuild = new PlzBuild(
            config: [modules: []],
            script: script,
            notification: notification,
            psc: psc
        )

        plzBuild.executeBuildAndTestStage()
        plzBuild.executePublishStage()

        then:
        1 * getPipelineMock('echo')({ it == 'Modules is specified, but the list is empty. Nothing to build.' })
        1 * getPipelineMock('echo')({ it == 'Modules is specified, but the list is empty. Nothing to publish.' })
    }

    def """When executePublishStage with usePublishAlias is configured,
        the publish alias is executed"""() {
        setup:
        explicitlyMockPipelineVariable("deployerIdName")
        explicitlyMockPipelineVariable("deployerIdToken")

        when:
        def plzBuild = new PlzBuild(
            config: [
                usePublishAlias: true,
                artifactory    : [
                    credentialsId: 'testId',
                    version      : 'testVersion'
                ]
            ], script: script, notification: notification, psc: psc
        )

        plzBuild.executePublishStage()

        then:
        1 * getPipelineMock('withCredentials')(*_)
        1 * getPipelineMock('usernamePassword.call')([credentialsId: 'testId', usernameVariable: 'deployerIdName', passwordVariable: 'deployerIdToken'])
        1 * getPipelineMock('echo')({ it == 'Publishing modules with aliased command defined under "publish"' })
        1 * getPipelineMock('sh')({ it ==~ 'plz publish testVersion .*' })
    }

    def """When executePublishStage with usePublishAlias is configured with verbosityFlag and extra args,
        the publish alias is executed with extra build args"""() {
        setup:
        explicitlyMockPipelineVariable("deployerIdName")
        explicitlyMockPipelineVariable("deployerIdToken")

        when:
        def plzBuild = new PlzBuild(
            config: [
                verbosityFlag  : '-vvv',
                extraBuildArgs : '--profile dev',
                usePublishAlias: true,
                artifactory    : [
                    credentialsId: 'testId',
                    version      : 'testVersion'
                ]
            ], script: script, notification: notification, psc: psc
        )

        plzBuild.executePublishStage()

        then:
        1 * getPipelineMock('withCredentials')(*_)
        1 * getPipelineMock('usernamePassword.call')([credentialsId: 'testId', usernameVariable: 'deployerIdName', passwordVariable: 'deployerIdToken'])
        1 * getPipelineMock('echo')({ it == 'Publishing modules with aliased command defined under "publish"' })
        1 * getPipelineMock('sh')({ it ==~ 'plz --show_all_output --profile dev -vvv publish testVersion .*' })
    }

    def """When executePublishStage with usePublishAlias is configured and tagDetails is defined,
        the publish alias is executed with the version defined in the tagFile over artifactory.version"""() {
        setup:
        explicitlyMockPipelineVariable("deployerIdName")
        explicitlyMockPipelineVariable("deployerIdToken")

        when:
        def plzBuild = new PlzBuild(
            config: [
                usePublishAlias: true,
                artifactory    : [
                    credentialsId: 'testId',
                    version      : 'testVersion'
                ],
                tagDetails     : [
                    gitTagCredKey: 'test',
                    branch       : 'test|main',
                    tagFile      : 'test'
                ]
            ], script: script, notification: notification, psc: psc
        )

        plzBuild.executePublishStage()

        then:
        1 * getPipelineMock('withCredentials')(*_)
        1 * getPipelineMock('usernamePassword.call')([credentialsId: 'testId', usernameVariable: 'deployerIdName', passwordVariable: 'deployerIdToken'])
        1 * getPipelineMock("readFile")(*_) >> "1.0.1\n"
        1 * getPipelineMock('echo')({ it == 'Publishing modules with aliased command defined under "publish"' })
        1 * getPipelineMock('sh')({ it ==~ 'plz publish 1.0.1 .*' })
    }
}
