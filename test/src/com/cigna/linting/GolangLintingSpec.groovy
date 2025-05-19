package com.cigna.linting

import com.cigna.SinglePodTest
import com.cigna.common.phases.PodSelector
import com.cigna.common.scm.CommonGit

class GolangLintingSpec extends SinglePodTest {
    def cloudName = 'test-cloud'
    def setup() {
        explicitlyMockPipelineStep('updateGitStatus')
        initScriptAndPsc()
    }

    def """golangci-lint will use the correct packages when invoked"""() {
        when:
        def build = new Linting(
            config: configToVerify,
            script: script,
            psc: psc
        )
        simulatePodTemplate(psc, build, cloudName)
        build.runGolang()

        then:
        1 * getPipelineMock("sh")('pwd && ls -l')
        1 * getPipelineMock("sh")(expectedRunCommand)

        where:
        configToVerify << [
                [
                        lintingTypes: [
                                'go': [:]
                        ],
                ],
                [
                        lintingTypes: [
                                'go': [
                                        targets: [
                                                'package-one',
                                                'package-two',
                                        ]
                                ],
                        ],
                ],
                [
                        lintingTypes: [
                                'go': [
                                        targets: [
                                                'package-one',
                                                'package-two',
                                        ],
                                        timeout: '5m',
                                        fromRev: 'HEAD~'
                                ],
                        ],
                ],
        ]
        expectedRunCommand << [
                '$(go env GOPATH)/bin/golangci-lint run --timeout 2m ./...',
                '$(go env GOPATH)/bin/golangci-lint run --timeout 2m package-one package-two',
                '$(go env GOPATH)/bin/golangci-lint run --timeout 5m --new-from-rev HEAD~ package-one package-two',
        ]
    }

    def """golangci-lint will use invoke mod vendor if requested before scanning"""() {
        when:
        def build = new Linting(
            config: configToVerify,
            script: script,
            psc: psc
        )
        simulatePodTemplate(psc, build, cloudName)
        build.runGolang()

        then:
        1 * getPipelineMock("sh")('pwd && ls -l')
        1 * getPipelineMock("sh")('go mod vendor -v')
        1 * getPipelineMock("sh")(expectedRunCommand)

        where:
        configToVerify << [
                [
                        lintingTypes: [
                                'go': [
                                        targets: [
                                                'package-one',
                                                'package-two',
                                        ],
                                        vendor: true,
                                        timeout: '5m',
                                        fromRev: 'HEAD~'
                                ],
                        ],
                ],
        ]
        expectedRunCommand << [
                '$(go env GOPATH)/bin/golangci-lint run --timeout 5m --new-from-rev HEAD~ package-one package-two',
        ]
    }
}
