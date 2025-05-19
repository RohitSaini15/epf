package com.cigna.deployment

import com.cigna.SinglePodTest
import com.cigna.common.phases.PodSelector
import com.cigna.common.scm.CommonGit

class ArgoCDDeploymentSpec extends SinglePodTest {
    def cloudName = 'test-cloud'

    def setup() {
        explicitlyMockPipelineStep('override')
        explicitlyMockPipelineStep('updateGitStatus')
        initScriptAndPsc()
    }

    def """When validate method is called and required field are missing the issues list is
        updated"""() {
        when:
        ArgoCDDeployment argoCDDeployment = new ArgoCDDeployment(
                config: [
                        deploymentType : 'argoCD',
                        branchPattern  : 'fake',
                        sdlcEnvironment: 'fake',
                        cloudName      : cloudName,
                        argoCd         : whereArgoCd
                ], script: script,
                psc: psc
        )

        simulatePodTemplate(psc, argoCDDeployment, cloudName)
        def issues = argoCDDeployment.validate()

        then:
        assert issues.size() == numberOfIssues

        where:
        numberOfIssues << [0, 1, 2, 3, 4, 5]
        whereArgoCd << [
                [
                        argoCdServer : 'fake',
                        namespace    : 'fake',
                        appName      : 'fake',
                        credentialsId: 'fake',
                        chartPath    : 'fake'
                ],
                [
                        namespace    : 'fake',
                        appName      : 'fake',
                        credentialsId: 'fake',
                        chartPath    : 'fake'
                ],
                [
                        appName      : 'fake',
                        credentialsId: 'fake',
                        chartPath    : 'fake'
                ],
                [
                        credentialsId: 'fake',
                        chartPath    : 'fake'
                ],
                [
                        chartPath: 'fake'
                ],
                [:]
        ]
    }

    def """When user provides vaultedValuesFiles and is missing required fields and validate 
        method is called then issues list is updated accordingly"""() {
        when:
        ArgoCDDeployment argoCDDeployment = new ArgoCDDeployment(
                config: [
                        deploymentType : 'ArgoCD',
                        branchPattern  : 'fake',
                        sdlcEnvironment: 'fake',
                        argoCd         : [
                                argoCdServer      : 'fake',
                                namespace         : 'fake',
                                appName           : 'fake',
                                credentialsId     : 'fake',
                                chartPath         : 'fake',
                                vaultedValuesFiles: [
                                        'fake'
                                ],
                        ] + whereVaultRequiredProp
                ],
                script: script,
                psc: psc
        )
        simulatePodTemplate(psc, argoCDDeployment, cloudName)

        def issues = argoCDDeployment.validate()

        then:
        assert issues.size() == numberOfIssues

        where:
        whereVaultRequiredProp << [[], [vaultCredentialsId: 'test']]
        numberOfIssues << [1, 0]
    }

    def """When rollback method is called, executeArgoCDDeploy is run with the last successful commit"""() {
        given:
        script.env.GIT_PREVIOUS_COMMIT = 'previous_commit'
        when:
        ArgoCDDeployment argoCDDeployment = new ArgoCDDeployment(
                config: [
                        deploymentType : 'ArgoCD',
                        branchPattern  : 'fake',
                        sdlcEnvironment: 'fake',
                        argoCd         : [
                                argoCdServer : 'fake',
                                namespace    : 'fake',
                                appName      : 'fake',
                                credentialsId: 'fake',
                                chartPath    : 'fake',
                        ]
                ],
                script: script,
                psc: psc
        )
        simulatePodTemplate(psc, argoCDDeployment, cloudName)
        argoCDDeployment.rollback()

        then:
        1 * getPipelineMock("sh")({
            it ==~ /argocd app create.*revision previous_commit.*/
        })
    }

    def """When deploy method is called, the proper steps are executed based on user inputs"""() {
        given:
        script.env.GIT_COMMIT = 'commit'
        when:
        ArgoCDDeployment argoCDDeployment = new ArgoCDDeployment(
                config: [
                        deploymentType : 'ArgoCD',
                        branchPattern  : 'fake',
                        sdlcEnvironment: 'fake',
                        argoCd         : [
                                argoCdServer : 'fake',
                                namespace    : 'fake',
                                appName      : 'fake',
                                credentialsId: 'fake',
                                chartPath    : 'fake',
                                argoCdOpts   : 'fake'
                        ] + whereExtraArgs
                ],
                script: script,
                psc: psc
        )
        simulatePodTemplate(psc, argoCDDeployment, cloudName)

        argoCDDeployment.deploy()

        then:
        1 * getPipelineMock("withEnv").call(['ARGOCD_SERVER=fake', 'ARGOCD_OPTS=fake'], _)
        1 * getPipelineMock("sh")({
            def pattern = "argocd app create fake --revision commit.*${whereCreateExtraParams}.*"
            it ==~ /${pattern}/
        })
        1 * getPipelineMock("sh")({ it == "argocd app sync fake --async${whereSyncExtraParams}" })
        1 * getPipelineMock("sh")({ it == "argocd app wait fake --sync --health --timeout ${whereWaitExtraParams}" })

        where:
        whereExtraArgs << [[], [revisionHistoryLimit: 1, prune: true, timeout: 3000]]
        whereCreateExtraParams << [' ', '--revision-history-limit 1']
        whereSyncExtraParams << [' ', ' --prune']
        whereWaitExtraParams << [300, 3000]
    }

    def """When withEnv is configured in the phase, validate if it is propagated to the execution environment"""() {
        when:
        ArgoCDDeployment argoCDDeployment = new ArgoCDDeployment(
                config: [
                        deploymentType : 'ArgoCD',
                        branchPattern  : 'fake',
                        sdlcEnvironment: 'fake',
                        withEnv        : ['somevar=somevalue'],
                        argoCd         : [
                                argoCdServer : 'fake',
                                namespace    : 'fake',
                                appName      : 'fake',
                                credentialsId: 'fake',
                                chartPath    : 'fake',
                                argoCdOpts   : 'fake'
                        ]
                ],
                script: script,
                psc: psc
        )
        simulatePodTemplate(psc, argoCDDeployment, cloudName)

        argoCDDeployment.deploy()

        then:
        1 * getPipelineMock("withEnv").call(['ARGOCD_SERVER=fake', 'ARGOCD_OPTS=fake', 'somevar=somevalue'], _)
    }

    def """When deploy method is called with user provided helm values options,
        they are reflected in script"""() {
        given:
        script.env.GIT_COMMIT = 'commit'
        explicitlyMockPipelineVariable("VAULT_PASS")

        when:
        ArgoCDDeployment argoCDDeployment = new ArgoCDDeployment(
                config: [
                        deploymentType : 'ArgoCD',
                        branchPattern  : 'fake',
                        sdlcEnvironment: 'fake',
                        argoCd         : [
                                argoCdServer : 'fake',
                                namespace    : 'fake',
                                appName      : 'fake',
                                credentialsId: 'fake',
                                chartPath    : 'fake',
                        ] + whereExtraArgs
                ],
                script: script,
                psc: psc
        )
        simulatePodTemplate(psc, argoCDDeployment, cloudName)

        argoCDDeployment.deploy()

        then:
        1 * getPipelineMock("sh")({
            def pattern = "argocd app create fake --revision commit.*${whereCreateExtraParams}.*"
            it ==~ /${pattern}/
        })
        whereVaultScriptRuns * getPipelineMock("sh")({
            it ==~ /ansible-vault decrypt.*/
        })

        where:
        whereExtraArgs << [
                [
                        valuesFiles: [
                                'test',
                                'test'
                        ]
                ],
                [
                        setValues: [
                                'cool_var=test',
                                'anotha_one=test'
                        ]
                ],
                [
                        vaultedValuesFiles: [
                                'test/test',
                                'test/test'
                        ]
                ]
        ]
        whereCreateExtraParams << [
                '--values test --values test',
                '--helm-set \"cool_var=test\" --helm-set \"anotha_one=test\"',
                '--values-literal-file test/test --values-literal-file test/test'
        ]
        whereVaultScriptRuns << [0, 0, 2]
    }

    def """When separate repo config is supplied, ArgoCD app create uses the additional repo"""() {
        when:
        ArgoCDDeployment argoCDDeployment = new ArgoCDDeployment(
                config: [
                        deploymentType : 'ArgoCD',
                        branchPattern  : 'fake',
                        sdlcEnvironment: 'fake',
                        argoCd         : [
                                argoCdServer : 'fake',
                                namespace    : 'fake',
                                appName      : 'fake',
                                credentialsId: 'fake',
                                chartPath    : 'fake',
                        ] + extraRepoParams
                ],
                script: script,
                psc: psc
        )
        simulatePodTemplate(psc, argoCDDeployment, cloudName)
        argoCDDeployment.executeArgoCDDeploy('test')

        then:
        1 * getPipelineMock("sh")({ it ==~ /$expectedCommandLine/ })
        where:
        extraRepoParams << [
                [
                        repourl: 'https://git.express-scripts.com/ESI/test.git',
                ],
                []
        ]
        expectedCommandLine << [
                'argocd app create.*--repo https://git.express-scripts.com/ESI/test.git.*',
                'argocd app create.*--repo https://git.express-scripts.com/expressScripts/fakerepo.git.*',
        ]
    }
}
