package com.cigna.linting

import com.cigna.SinglePodTest

class ShellcheckLintingSpec extends SinglePodTest {

    def setup() {
        explicitlyMockPipelineStep('updateGitStatus')
        explicitlyMockPipelineStep('recordIssues')
        explicitlyMockPipelineStep('checkStyle')
        explicitlyMockPipelineVariable("mccreds-access-key")
        explicitlyMockPipelineVariable("mccreds-secret-key")
        explicitlyMockPipelineVariable("MC_HOST_minio")
        initScriptAndPsc()
    }

    def """When shellcheck is called, parameters are inserted correctly for invocation"""() {
        when:
        def build = new Linting(
                config: [
                        branchPattern: 'stuff',
                        lintingTypes : [
                                shellcheck: shellcheckConfig
                        ]
                ],
                script: script,
                psc: psc
        )
        build.runShellcheck()
        then:
        1 * getPipelineMock("sh")(expectedCommand)
        where:
        shellcheckConfig << [
                [:],
                [commandArgs: ['--1', '--2']],
                [excludePaths: ['pattern1', 'pattern2']],
                [commandArgs: ['--1', '--2'], excludePaths: ['pattern']],
        ]
        expectedCommand << [
                "shellcheck --color=never \$(find . -name '*.*sh' -type f) | tee shellcheck_results.xml",
                "shellcheck --1 --2 \$(find . -name '*.*sh' -type f) | tee shellcheck_results.xml",
                "shellcheck --color=never \$(find . -name '*.*sh' -type f -not -path \"pattern1\" -not -path \"pattern2\") | tee shellcheck_results.xml",
                "shellcheck --1 --2 \$(find . -name '*.*sh' -type f -not -path \"pattern\") | tee shellcheck_results.xml",
        ]
    }

    def """When shellcheck container image overrides are provided, they are templated correctly"""() {
        when:
        def build = new Linting(
                config: [
                        branchPattern: 'stuff',
                        lintingTypes : [
                                shellcheck: [
                                        container: [
                                                image  : nameOverride,
                                                version: versionOverride
                                        ]
                                ]
                        ]
                ],
                script: script,
        )
        build.configureContainers()
        def podConfig = build.getPodConfig()
        then:
        assert podConfig =~ /"image":"${expectedImage}"/
        where:
        nameOverride << [null, null, 'enterprise-devops/custom', 'enterprise-devops/custom']
        versionOverride << [null, 'latest', null, 'latest']
        expectedImage << [
                'enterprise-devops/shellcheck:stable',
                'enterprise-devops/shellcheck:latest',
                'enterprise-devops/custom:stable',
                'enterprise-devops/custom:latest',
        ]
    }

    def """When shellcheck is called with warningsNG options, parameters are passed along"""() {
        when:
        def build = new Linting(
                config: [
                        branchPattern: 'stuff',
                        lintingTypes : [
                                shellcheck: shellcheckConfig
                        ]
                ],
                script: script,
        )
        build.runShellcheck()
        then:
        1 * getPipelineMock("recordIssues")(expectedCommand)
        1 * getPipelineMock("checkStyle")(_)
        where:
        shellcheckConfig << [
                [warningsNG: [:]],
                [warningsNG: [tools: ['fake-tool']]],
        ]
        expectedCommand << [
                [tools: [null]],
                [tools: ['fake-tool', null]],
        ]
    }
}
