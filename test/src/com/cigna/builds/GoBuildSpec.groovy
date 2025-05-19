package com.cigna.builds

import com.cigna.SinglePodTest
import com.cigna.common.utils.GoEnvBuilder

class GoBuildSpec extends SinglePodTest {
    def setup() {
        explicitlyMockPipelineStep('findFiles')
        explicitlyMockPipelineStep('updateGitStatus')
        explicitlyMockPipelineStep('gitUsernamePassword')
        explicitlyMockPipelineVariable("gitToken")
        explicitlyMockPipelineVariable("GIT_USERNAME")
        explicitlyMockPipelineVariable("GIT_PASSWORD")
        initScriptAndPsc()
    }

    def """Testing that an illegal buildMode results in an exception"""() {
        when:
        def goBuild = Spy(
                GoBuild,
                constructorArgs: [
                        [
                                config: [
                                        golang: [
                                                apiToken : 'IAMNOTAREALTOKEN',
                                                tokenType: 'GitLab',
                                                buildMode: 'tasty'
                                        ]
                                ],
                                script: script,
                                psc   : psc
                        ]
                ]
        )
        goBuild.executeBuildAndTestStage()

        then:
        def exception = thrown(InvalidGoBuildModeException)
        exception.message == "The 'tasty' buildMode is not supported " +
                "(valid values are [plugin, pie, shared, c-shared, archive, c-shared, module])"
    }

    def """Testing that a plugin build for the multiple plugin packages results in the expected commands"""() {
        given:
        def goEnv = GoEnvBuilder.buildGoEnv()

        when:
        def goBuild = Spy(
                GoBuild,
                constructorArgs: [
                        [
                                config: [
                                        golang: [
                                                apiToken     : 'IAMNOTAREALTOKEN',
                                                tokenType    : 'GitLab',
                                                buildMode    : 'plugin',
                                                packageName  : 'executors',
                                                packagingType: 'individual'
                                        ]
                                ],
                                script: script,
                                psc   : psc
                        ]
                ]
        )
        goBuild.executeBuildAndTestStage()

        then:
        0 * getPipelineMock("findFiles")() >> [
                [name: "servicenow", directory: true],
                [name: "archer", directory: true],
                [name: "cloudconformity", directory: true],
                [name: "notadirectory", directory: false],
        ]


        0 * getPipelineMock("sh")(goEnv + 'go build -buildmode=plugin executors/servicenow')
        0 * getPipelineMock("sh")(goEnv + 'go build -buildmode=plugin executors/archer')
        0 * getPipelineMock("sh")(goEnv + 'go build -buildmode=plugin executors/cloudconformity')
    }

    def """Testing that it calls test and goreleaser"""() {
        given:
        def goEnv = GoEnvBuilder.buildGoEnv()


        when:
        def goBuild = Spy(
                GoBuild,
                constructorArgs: [
                        [
                                config: [
                                        golang: [
                                                apiToken : 'IAMNOTAREALTOKEN',
                                                tokenType: 'GitLab'
                                        ]
                                ],
                                script: script,
                                psc   : psc
                        ]
                ]
        )
        goBuild.executeBuildAndTestStage()
        goBuild.executePublishStage()

        then:
        1 * getPipelineMock("sh")(goEnv + 'goreleaser release -f /tmp/goreleaser/.goreleaser.yml --snapshot')
    }

    def """Testing that overriding the go proxy results in using the specified proxy url"""() {
        when:
        def goBuild = Spy(
                GoBuild,
                constructorArgs: [
                        [
                                config: [
                                        golang: [
                                                apiToken : 'IAMNOTAREALTOKEN',
                                                tokenType: 'GitLab',
                                                proxy    : proxy,
                                        ]
                                ],
                                script: script,
                                psc   : psc
                        ]
                ]
        )
        goBuild.executeBuildAndTestStage()
        goBuild.executePublishStage()

        then:
        1 * getPipelineMock("sh")(GoEnvBuilder.buildGoEnv(proxy) + 'goreleaser release -f /tmp/goreleaser/.goreleaser.yml --snapshot')
        where:
        proxy << ['https://repo.sys.cigna.com/artifactory/go-repos', 'https://cigna.jfrog.io/artifactory/go-repos']
    }

    def """test that conduit adds additional flags to goreleaser cfg"""() {
        when:
        def goBuild = Spy(
                GoBuild,
                constructorArgs: [
                        [
                                config: [
                                        golang     : [
                                                apiToken     : 'IAMNOTAREALTOKEN',
                                                tokenType    : 'GitLab',
                                                buildMode    : 'plugin',
                                                packageName  : 'executors',
                                                packagingType: 'individual',
                                                flags        : [
                                                        '-trimpath',
                                                        '-mod=vendor',
                                                ],
                                        ],
                                        artifactory: [
                                                credentialsId: 'fakeartifactorygit '
                                        ],
                                ],
                                script: script,
                                psc   : psc
                        ]
                ]
        )
        goBuild.executePublishStage()

        then:
        1 * getPipelineMock("findFiles")() >> [
                [name: "servicenow", directory: true],
                [name: "archer", directory: true],
                [name: "cloudconformity", directory: true],
                [name: "notadirectory", directory: false],
        ]

        1 * getPipelineMock("sh")("""echo '
artifactories:
  - name: prd
    mode: binary
    checksum: true
    target: https://cigna.jfrog.io/artifactory/go-repos/{{ .ProjectName }}/{{ .Version }}/{{ .Os }}/{{ .Arch }}
release:
  disable: true
archives:
  - format: binary
builds:

  - main: ./executors/servicenow
    id: "servicenow"
    binary: servicenow.so
    flags:
      - -v
      - -trimpath
      - -mod=vendor

    ldflags:
      - -E
      - -pluginpath=servicenow
    goos:
      - linux
    goarch:
      - amd64

  - main: ./executors/archer
    id: "archer"
    binary: archer.so
    flags:
      - -v
      - -trimpath
      - -mod=vendor

    ldflags:
      - -E
      - -pluginpath=archer
    goos:
      - linux
    goarch:
      - amd64

  - main: ./executors/cloudconformity
    id: "cloudconformity"
    binary: cloudconformity.so
    flags:
      - -v
      - -trimpath
      - -mod=vendor

    ldflags:
      - -E
      - -pluginpath=cloudconformity
    goos:
      - linux
    goarch:
      - amd64

' >> .goreleaser.yml""")
    }

    def """Testing that it calls goreleaser for each individual package"""() {
        given:
        def goEnv = GoEnvBuilder.buildGoEnv()
        when:
        def goBuild = Spy(
                GoBuild,
                constructorArgs: [
                        [
                                config: [
                                        golang     : [
                                                apiToken     : 'IAMNOTAREALTOKEN',
                                                tokenType    : 'GitLab',
                                                buildMode    : 'plugin',
                                                packageName  : 'executors',
                                                packagingType: 'individual'
                                        ],
                                        artifactory: [
                                                credentialsId: 'fakeartifactorygit '
                                        ],
                                ],
                                script: script,
                                psc   : psc
                        ]
                ]
        )
        goBuild.executePublishStage()

        then:
        1 * getPipelineMock("sh")(goEnv + 'goreleaser release -f /tmp/goreleaser/.goreleaser.yml --snapshot')
        1 * getPipelineMock("findFiles")() >> [
                [name: "servicenow", directory: true],
                [name: "archer", directory: true],
                [name: "cloudconformity", directory: true],
                [name: "notadirectory", directory: false],
        ]

        1 * getPipelineMock("sh")("""echo '
artifactories:
  - name: prd
    mode: binary
    checksum: true
    target: https://cigna.jfrog.io/artifactory/go-repos/{{ .ProjectName }}/{{ .Version }}/{{ .Os }}/{{ .Arch }}
release:
  disable: true
archives:
  - format: binary
builds:

  - main: ./executors/servicenow
    id: "servicenow"
    binary: servicenow.so
    flags:
      - -v

    ldflags:
      - -E
      - -pluginpath=servicenow
    goos:
      - linux
    goarch:
      - amd64

  - main: ./executors/archer
    id: "archer"
    binary: archer.so
    flags:
      - -v

    ldflags:
      - -E
      - -pluginpath=archer
    goos:
      - linux
    goarch:
      - amd64

  - main: ./executors/cloudconformity
    id: "cloudconformity"
    binary: cloudconformity.so
    flags:
      - -v

    ldflags:
      - -E
      - -pluginpath=cloudconformity
    goos:
      - linux
    goarch:
      - amd64

' >> .goreleaser.yml""")
    }

    def """test that goreleaser can package and upload a module to artifactory"""() {
        when:
        def goBuild = Spy(
                GoBuild,
                constructorArgs: [
                        [
                                config: [
                                        golang     : [
                                                apiToken   : 'IAMNOTAREALTOKEN',
                                                tokenType  : 'GitLab',
                                                packageName: 'run',
                                                mainName   : 'mymain.go',
                                                module     : true,
                                        ],
                                        artifactory: [
                                                credentialsId: 'fakeartifactorygit '
                                        ],
                                ],
                                script: script,
                                psc   : psc
                        ]
                ]
        )
        goBuild.executePublishStage()

        then:
        1 * getPipelineMock("sh")("""echo '
artifactories:
  - name: prd
    mode: binary
    checksum: true
    target: https://cigna.jfrog.io/artifactory/go-repos/{{ .ProjectName }}/{{ .Version }}/{{ .Os }}/{{ .Arch }}
release:
  disable: true
archives:
  - format: binary
gomod:
  proxy: true
builds:

  - main: run/mymain.go

' >> .goreleaser.yml""")
    }

    def """Testing that specifying tag details results in a git tag being created for the current commit"""() {
        given:
        def goEnv = GoEnvBuilder.buildGoEnv()
        script.scm.branches = [
            [name: 'master']
        ]
        when:
        GoBuild goBuild = new GoBuild(config: [
                tagDetails: [
                        branch       : 'test|master',
                        gitTagCredKey: 'gitlab-api-token',
                        tag          : '1.0.1',
                        prefix       : 'v',
                        message      : 'Version v1.0.1',
                ],
                cloudName : 'test-cloud',
                golang    : [
                        apiToken : 'IAMNOTAREALTOKEN',
                        tokenType: 'GitLab',
                ]
        ], script: script,
                psc: psc
        )
        simulatePodTemplate(psc, goBuild)
        goBuild.validate()
        goBuild.executePreBuildStage()
        goBuild.executeBuildAndTestStage()
        goBuild.executePublishStage()

        then:
        1 * getPipelineMock('sh')(goEnv + 'goreleaser release -f /tmp/goreleaser/.goreleaser.yml --snapshot')
        1 * getPipelineMock('sh')("git tag -a 'v1.0.1' -m 'Version v1.0.1'")
    }

    def """Testing that specifying tag details with prefix results in a git tag with a prefix being created for the current commit"""() {
        given:
        def goEnv = GoEnvBuilder.buildGoEnv()
        script.scm.branches = [
                [name: 'test']
        ]

        when:
        GoBuild goBuild = new GoBuild(
                config: [
                        tagDetails: [
                                branch       : 'test',
                                gitTagCredKey: 'gitlab-api-token',
                                tag          : '1.0.1',
                                prefix       : 'v',
                                trace        : traceFlag,
                                message      : 'Version X',
                        ],
                        golang    : [
                                apiToken : 'IAMNOTAREALTOKEN',
                                tokenType: 'GitLab',
                        ]
                ],
                script: script,
                psc: psc
        )
        simulatePodTemplate(psc, goBuild)
        goBuild.executePreBuildStage()
        goBuild.executeBuildAndTestStage()
        goBuild.executePublishStage()

        then:
        1 * getPipelineMock('sh')(goEnv + 'goreleaser release -f /tmp/goreleaser/.goreleaser.yml --snapshot')
        1 * getPipelineMock('sh')(tagExpr)
        1 * getPipelineMock('sh')({
            it ==~ /${pushExpr}/
        })
        1 * getPipelineMock('echo')("Using tag prefix 'v'")
        where:
        traceFlag << [false, true]
        pushExpr << ["git push -v 'https://git.express-scripts.com/expressScripts/fakerepo.git' --tags",
                     "GIT_TRACE=1 git push -v 'https://git.express-scripts.com/expressScripts/fakerepo.git' --tags"]
        tagExpr << ["git tag -a 'v1.0.1' -m 'Version X'",
                    "GIT_TRACE=1 git tag -a 'v1.0.1' -m 'Version X'"]
    }

    def """When withEnv is configured in the phase, validate if it is propagated to the execution environment"""() {
        given:
        def goEnv = GoEnvBuilder.buildGoEnv()
        script.scm.branches = [
            [name: 'master']
        ]

        when:
        GoBuild goBuild = new GoBuild(config: [
                tagDetails: [
                        branch       : 'test|master',
                        gitTagCredKey: 'gitlab-api-token',
                        tag          : '1.0.1',
                        prefix       : 'v',
                        message      : 'Version v1.0.1',
                ],
                cloudName : 'test-cloud',
                golang    : [
                        apiToken : 'IAMNOTAREALTOKEN',
                        tokenType: 'GitLab',
                ],
                withEnv   : ['somevar=somevalue']
        ], script: script,
                psc: psc
        )
        simulatePodTemplate(psc, goBuild)
        goBuild.validate()
        goBuild.executePreBuildStage()
        goBuild.executeBuildAndTestStage()
        goBuild.executePublishStage()

        then:
        1 * getPipelineMock('sh')(goEnv + 'goreleaser release -f /tmp/goreleaser/.goreleaser.yml --snapshot')
        1 * getPipelineMock('sh')("git tag -a 'v1.0.1' -m 'Version v1.0.1'")
        1 * getPipelineMock("withEnv").call(['GITLAB_TOKEN=Mock Generator for [apiToken]', 'somevar=somevalue'], _)
    }


}
