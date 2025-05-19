package com.cigna.release

import com.cigna.SinglePodTest
import com.cigna.common.exception.ErrorStepException
import com.cigna.common.phases.PhaseLoader
import com.cigna.modules.Module
import com.evernorth.cloudnativebuild.service.PipelineStateManager

class ReleaseSpec extends SinglePodTest {

    def setup() {
        explicitlyMockPipelineStep('updateGitStatus')
        explicitlyMockPipelineVariable("EPF_GITHUB_ACCESS_TOKEN")
        initScriptAndPsc()
        psc.globalModuleManager._pipelineState = new PipelineStateManager()
    }

    def '''when pre-release phase contains embedded module phases, the correct step invocations are inserted in to the state manager'''() {
        given:
        def releasePhase = new Release(script: script, psc:psc,
            config: [
                releaseType         : 'preRelease',
                releaseBranchPattern: '^(release|hotfix).*',
                branchPattern       : '.*',
                phases              : [
                        [
                                moduleType            : 'maven',
                                moduleName            : 'cnp-build-publish-maven-create',
                                subCommand            : 'build',
                                branchPattern         : '.*',
                                sdlcEnvironment       : 'dev',
                                isProductionDeployment: false,
                                args                  : [
                                ]
                        ],

                        [
                                moduleType            : 'openshift',
                                moduleName            : 'cnp-deploy-argorollouts',
                                subCommand            : 'deploy',
                                branchPattern         : '.*',
                                releaseBranchPattern  : '.*',
                                sdlcEnvironment       : 'prod',
                                isProductionDeployment: true,
                                options               : [
                                        env: 'prod'
                                ],
                                args                  : [
                                        credentials                 : [
                                                [id: 'env.CNP_OC_CRED_DEV', env: 'dev'],
                                                [id: 'env.CNP_CIGNA_GIT', env: ''],
                                                [id: 'env.CNP_ARGOCD_CRED_DEV', env: 'dev'],
                                        ],
                                        appName                     : 'springpcf-testapp-1-dev',
                                        namespace                   : 'pipeline-automation',
                                        platform                    : 'OpenShift',
                                        imageName                   : 'lookup:imageName',
                                        imageTag                    : 'lookup:tagName',
                                        cluster                     : 'hs-1-nonprod',
                                        configDir                   : 'dev',
                                        monitoringSolution          : 'newrelic',
                                        progressingRetryCount       : 10,
                                        env                         : 'dev',
                                        cmdbApplicationServiceNumber: 'AS025707'
                                ]
                        ],
                ],
                phaseInstance       : [:] // mock phaseInstance from PhaseLoader
        ]
        )

        when:
        releasePhase.preValidate()
        def (List<Map<String, Object>> phases, List<String> issues) = new PhaseLoader(script, psc).loadPhases(releasePhase.config.phases, null)
        releasePhase.prePodConfig()
        releasePhase.addPipelineSteps()
        then:
        psc.globalModuleManager.stateManager().pipelineSteps.size() == 3
        psc.globalModuleManager.stateManager().pipelineSteps[0].verb == 'build'
        psc.globalModuleManager.stateManager().pipelineSteps[1].verb == 'deploy'
        psc.globalModuleManager.stateManager().pipelineSteps[2].verb == 'prerelease'
    }


    def '''When an argorollouts promote style cutover module is defined, the release logic is able to detect it'''() {
        given:
        def releasePhase = new Release(script: script, psc: psc,
            config: [
                releaseType         : 'release',
                releaseBranchPattern: '^(release|hotfix).*',
                branchPattern       : '.*',
                phases              : [
                        [
                                moduleType            : 'openshift',
                                moduleName            : 'cnp-deploy-argorollouts',
                                subCommand            : 'promote',
                                sdlcEnvironment       : 'prod',
                                isProductionDeployment: true,
                                args                  : [
                                        credentials          : [
                                                [id: 'env.CNP_OC_CRED_PROD', env: 'prod', type: 'usernamePassword'],
                                        ],
                                        appName              : 'springpcf-testapp-prod',
                                        configDir            : 'prod',
                                        namespace            : 'pipeline-automation-prod',
                                        progressingRetryCount: 10,
                                        cluster              : 'hs-1-prod',
                                        platform             : 'OpenShift',
                                        env                  : 'prod'
                                ]
                        ],
                ],
                phaseInstance       : [:] // mock phaseInstance from PhaseLoader
        ]
        )

        when:
        releasePhase.preValidate()
        def (List<Map<String, Object>> phases, List<String> issues) = new PhaseLoader(script, psc).loadPhases(releasePhase.config.phases, null)
        releasePhase.prePodConfig()
        releasePhase.addPipelineSteps()
        then:
        psc.globalModuleManager.stateManager().pipelineSteps.size() == 2
        psc.globalModuleManager.stateManager().pipelineSteps[0].verb == 'cutover'
        psc.globalModuleManager.stateManager().pipelineSteps[1].verb == 'release'
    }

    def '''when a release is triggered, the correct contracts are detected in the release step'''() {
        given:
        Release release = new Release(script: script, psc: psc,
            config: [
                releaseType           : 'release',
                releaseBranchPattern  : '^(release|hotfix).*',
                isProductionDeployment: true,
                branchPattern         : '.*',
                phases                : [
                        [
                                moduleType            : 'openshift',
                                moduleName            : 'cnp-deploy-argorollouts',
                                subCommand            : 'deploy',
                                branchPattern         : '.*',
                                releaseBranchPattern  : '.*',
                                sdlcEnvironment       : 'prod',
                                isProductionDeployment: true,
                                options               : [
                                        env: 'prod'
                                ],
                                args                  : [
                                        credentials                 : [
                                                [id: 'env.CNP_OC_CRED_DEV', env: 'dev'],
                                                [id: 'env.CNP_CIGNA_GIT', env: ''],
                                                [id: 'env.CNP_ARGOCD_CRED_DEV', env: 'dev'],
                                        ],
                                        appName                     : 'springpcf-testapp-1-dev',
                                        namespace                   : 'pipeline-automation',
                                        platform                    : 'OpenShift',
                                        imageName                   : 'lookup:imageName',
                                        imageTag                    : 'lookup:tagName',
                                        cluster                     : 'hs-1-nonprod',
                                        configDir                   : 'dev',
                                        monitoringSolution          : 'newrelic',
                                        progressingRetryCount       : 10,
                                        env                         : 'dev',
                                        cmdbApplicationServiceNumber: 'AS025707'
                                ]
                        ],
                ],
                phaseInstance         : [:] // mock phaseInstance from PhaseLoader
        ])
        when:
        release.preValidate()
        def (List<Map<String, Object>> phases, List<String> issues) = new PhaseLoader(script, psc).loadPhases(release.config.phases, null)
        release.prePodConfig()
        release.addPipelineSteps()
        then:
        psc.globalModuleManager.stateManager().pipelineSteps.size() == 2
        psc.globalModuleManager.stateManager().pipelineSteps[0].verb == 'deploy'
        psc.globalModuleManager.stateManager().pipelineSteps[1].verb == 'release'
    }

    def '''When a prerelease phase has nested phases, they are loaded correctly at runtime.'''() {
        given:
        def release = new Release(script: script, psc: psc,
            config: [
                releaseType           : 'release',
                releaseBranchPattern  : '^(release|hotfix).*',
                isProductionDeployment: true,
                branchPattern         : '.*',
                phases                : [
                        [
                                moduleType            : 'pcf',
                                branchPattern         : '.*',
                                sdlcEnvironment       : 'prod-01',
                                isProductionDeployment: true,
                                moduleName            : 'cnp-deploy-pcf',
                                subCommand            : 'deploy',
                                args                  : [
                                        credentials : [
                                                [id: 'NEWRELIC_API_KEY_NONPROD', prefix: 'NR_NONPROD', type: 'string'],
                                                [id: 'NEWRELIC_API_KEY_PROD', prefix: 'NR_PROD', type: 'string'],
                                        ],
                                        appName     : 'devops-testapps-SpringPcf-prod1-candidate',
                                        foundation  : 'ch3pcf01',
                                        organization: 'PipelineAutomation',
                                        space       : 'QA',
                                        env         : 'prod-01'
                                ]
                        ],
                        [
                                moduleType            : 'pcf',
                                branchPattern         : '.*',
                                sdlcEnvironment       : 'dr-01',
                                isProductionDeployment: true,
                                moduleName            : 'cnp-deploy-pcf',
                                subCommand            : 'deploy',
                                args                  : [
                                        credentials : [
                                                [id: 'NEWRELIC_API_KEY_NONPROD', prefix: 'NR_NONPROD', type: 'string'],
                                                [id: 'NEWRELIC_API_KEY_PROD', prefix: 'NR_PROD', type: 'string'],
                                        ],
                                        appName     : 'devops-testapps-SpringPcf-prod2-candidate',
                                        foundation  : 'ch3pcf01',
                                        organization: 'PipelineAutomation',
                                        space       : 'QA',
                                        env         : 'dr-01'
                                ]
                        ],
                ],
                phaseInstance         : [:] // mock phaseInstance from PhaseLoader
        ])
        when:
        def (List<Map<String, Object>> phases, List<String> issues) = new PhaseLoader(script, psc).loadPhases(release.config.phases, null)
        psc.podSelector.calculatePodTemplates([cloudName: 'test'], phases)
        then:
        assert phases.size() == 2
        assert issues.size() == 0
        assert phases[0].phaseInstance instanceof Module
        assert phases[1].phaseInstance instanceof Module
    }

    def '''detects valid release configuration when releaseBranchPattern and isProductionDeployment are set'''() {
        when:
        def release = new Release(script: script, psc: psc,
            config: [
                releaseType           : 'release',
                releaseBranchPattern  : releaseBranch,
                isProductionDeployment: isProdDeploy,
                branchPattern         : '.*',
                phases                : [
                        [
                                moduleType            : 'openshift',
                                moduleName            : 'cnp-deploy-argorollouts',
                                subCommand            : 'deploy',
                                branchPattern         : '.*',
                                releaseBranchPattern  : releaseBranch,
                                sdlcEnvironment       : 'prod',
                                isProductionDeployment: isProdDeploy,
                                options               : [
                                        env: 'prod'
                                ],
                                args                  : [
                                        credentials                 : [
                                                [id: 'env.CNP_OC_CRED_DEV', env: 'dev'],
                                                [id: 'env.CNP_CIGNA_GIT', env: ''],
                                                [id: 'env.CNP_ARGOCD_CRED_DEV', env: 'dev'],
                                        ],
                                        appName                     : 'springpcf-testapp-1-dev',
                                        namespace                   : 'pipeline-automation',
                                        platform                    : 'OpenShift',
                                        imageName                   : 'lookup:imageName',
                                        imageTag                    : 'lookup:tagName',
                                        cluster                     : 'hs-1-nonprod',
                                        configDir                   : 'dev',
                                        monitoringSolution          : 'newrelic',
                                        progressingRetryCount       : 10,
                                        env                         : 'dev',
                                        cmdbApplicationServiceNumber: 'AS025707'
                                ]
                        ],
                ],
                phaseInstance         : [:] // mock phaseInstance from PhaseLoader
        ])
        script.scm.branches = [[name: 'release/v1']]
        Boolean isReleasePhase = release.isReleasePhase(release.config)
        then:
        isReleasePhase == releaseTestResult
        where:
        isProdDeploy << [false, false, true, true]
        releaseBranch << ['release.*', 'develop.*', 'develop.*', 'release.*']
        releaseTestResult << [true, false, true, true]
    }


    def '''When list of modules is specified, they are not included in release phases as they will be executed in another pipeline'''() {
        given:

        Release release = new Release(script: script, psc: psc,
            config: [
                releaseType           : 'release',
                releaseBranchPattern  : '^(release|hotfix).*',
                isProductionDeployment: true,
                branchPattern         : '.*',
                phases                : [
                        [
                                moduleType            : 'openshift',
                                moduleName            : 'cnp-deploy-argorollouts',
                                subCommand            : 'deploy',
                                branchPattern         : '.*',
                                releaseBranchPattern  : '.*',
                                sdlcEnvironment       : 'prod',
                                isProductionDeployment: true,
                                options               : [
                                        env: 'prod'
                                ],
                                args                  : [
                                        credentials                 : [
                                                [id: 'env.CNP_OC_CRED_DEV', env: 'dev'],
                                                [id: 'env.CNP_CIGNA_GIT', env: ''],
                                                [id: 'env.CNP_ARGOCD_CRED_DEV', env: 'dev'],
                                        ],
                                        appName                     : 'springpcf-testapp-1-dev',
                                        namespace                   : 'pipeline-automation',
                                        platform                    : 'OpenShift',
                                        imageName                   : 'lookup:imageName',
                                        imageTag                    : 'lookup:tagName',
                                        cluster                     : 'hs-1-nonprod',
                                        configDir                   : 'dev',
                                        monitoringSolution          : 'newrelic',
                                        progressingRetryCount       : 10,
                                        env                         : 'dev',
                                        cmdbApplicationServiceNumber: 'AS025707'
                                ]
                        ],
                ],

        ])
        when:
        release.preValidate()
        def (List<Map<String, Object>> phases, List<String> _) = new PhaseLoader(script, psc).loadPhases(release.config.phases, null)
        simulatePodTemplate(psc, release)
        release.addPipelineSteps()

        then:
        def containerNamesInPod = release.additionalPodConfig.containers.collect { it.name }
        assert release.additionalPodConfig.containers.size() == 1
        // each container name from this list is in the pod (order doesn't matter)
        ['cnp-docker-corev'].each { expected ->
            assert containerNamesInPod.any { it.startsWith expected }
        }
    }

    def '''When a prerelease phase has nested phases, they result in a single pod template.'''() {
        given:
        def release = new Release(script: script, psc: psc,
            config: [
                releaseType           : 'release',
                releaseBranchPattern  : '^(release|hotfix).*',
                isProductionDeployment: true,
                branchPattern         : '.*',
                phases                : [
                        [
                                moduleType            : 'pcf',
                                branchPattern         : '.*',
                                sdlcEnvironment       : 'prod-01',
                                isProductionDeployment: true,
                                moduleName            : 'cnp-deploy-pcf',
                                subCommand            : 'deploy',
                                args                  : [
                                        credentials : [
                                                [id: 'NEWRELIC_API_KEY_NONPROD', prefix: 'NR_NONPROD', type: 'string'],
                                                [id: 'NEWRELIC_API_KEY_PROD', prefix: 'NR_PROD', type: 'string'],
                                        ],
                                        appName     : 'devops-testapps-SpringPcf-prod1-candidate',
                                        foundation  : 'ch3pcf01',
                                        organization: 'PipelineAutomation',
                                        space       : 'QA',
                                        env         : 'prod-01'
                                ]
                        ],
                        [
                                moduleType            : 'pcf',
                                branchPattern         : '.*',
                                sdlcEnvironment       : 'dr-01',
                                isProductionDeployment: true,
                                moduleName            : 'cnp-deploy-pcf',
                                subCommand            : 'deploy',
                                args                  : [
                                        credentials : [
                                                [id: 'NEWRELIC_API_KEY_NONPROD', prefix: 'NR_NONPROD', type: 'string'],
                                                [id: 'NEWRELIC_API_KEY_PROD', prefix: 'NR_PROD', type: 'string'],
                                        ],
                                        appName     : 'devops-testapps-SpringPcf-prod2-candidate',
                                        foundation  : 'ch3pcf01',
                                        organization: 'PipelineAutomation',
                                        space       : 'QA',
                                        env         : 'dr-01'
                                ]
                        ],
                ],
                phaseInstance         : [:] // mock phaseInstance from PhaseLoader
        ])
        when:
        def (List<Map<String, Object>> phases, List<String> issues) = new PhaseLoader(script, psc).loadPhases(release.config.phases, null)
        then:
        assert phases.size() == 2
        assert issues.size() == 0
        assert phases[0].phaseInstance instanceof Module
        assert phases[1].phaseInstance instanceof Module
    }

    def "a release phase does not contribute containers from its subphases to the pod template"() {
        given:
        def phases = [
                [
                        releaseType           : 'release',
                        releaseBranchPattern  : '^(release|hotfix).*',
                        isProductionDeployment: true,
                        branchPattern         : '.*',
                        phases                : [
                                [
                                        moduleType            : 'pcf',
                                        branchPattern         : '.*',
                                        sdlcEnvironment       : 'prod-01',
                                        isProductionDeployment: true,
                                        moduleName            : 'cnp-deploy-pcf',
                                        subCommand            : 'deploy',
                                        args                  : [
                                                credentials : [
                                                        [id: 'NEWRELIC_API_KEY_NONPROD', prefix: 'NR_NONPROD', type: 'string'],
                                                        [id: 'NEWRELIC_API_KEY_PROD', prefix: 'NR_PROD', type: 'string'],
                                                ],
                                                appName     : 'devops-testapps-SpringPcf-prod1-candidate',
                                                foundation  : 'ch3pcf01',
                                                organization: 'PipelineAutomation',
                                                space       : 'QA',
                                                env         : 'prod-01'
                                        ]
                                ],
                                [
                                        moduleType            : 'pcf',
                                        branchPattern         : '.*',
                                        sdlcEnvironment       : 'dr-01',
                                        isProductionDeployment: true,
                                        moduleName            : 'cnp-deploy-pcf',
                                        subCommand            : 'deploy',
                                        args                  : [
                                                credentials : [
                                                        [id: 'NEWRELIC_API_KEY_NONPROD', prefix: 'NR_NONPROD', type: 'string'],
                                                        [id: 'NEWRELIC_API_KEY_PROD', prefix: 'NR_PROD', type: 'string'],
                                                ],
                                                appName     : 'devops-testapps-SpringPcf-prod2-candidate',
                                                foundation  : 'ch3pcf01',
                                                organization: 'PipelineAutomation',
                                                space       : 'QA',
                                                env         : 'dr-01'
                                        ]
                                ],
                        ]
                ]
        ]
        when:
        def (loadedPhases, issues) = new PhaseLoader(script, psc).loadPhases(phases, null)
        psc.podSelector.calculatePodTemplates([cloudName: 'test-cloud'], loadedPhases)
        then:
        // ensure that the pod templates doesn't contain the subphase containers
        def createdPodTemplates = psc.podSelector.podTemplates['test-cloud']
        // pod includes jnlp and docker core
        ['conduit-jnlp', 'cnp-docker-core'].every { createdPodTemplates.contains(it) }
        // pod doesn't include pcf
        !createdPodTemplates.contains('cnp-docker-pcf')
    }

    def '''that a Release block will reject lintingtypes other than approvalrequest types.'''() {
        given:
        def release = new Release(script: script, psc: psc,
            config:
                [
                        releaseType           : 'release',
                        releaseBranchPattern  : '^(release|hotfix).*',
                        isProductionDeployment: true,
                        branchPattern         : '.*',
                        phases                : [
                                [
                                        emailRecipients: 'grangaswamy@express-scripts.com',
                                        branchPattern  : '.*',
                                        lintingTypes   : [
                                                approvalrequest: [
                                                        timeOut  : 120,
                                                        message  : 'Do you want to continue deployment to Prod candidate?',
                                                        id       : 'cnpApprove',
                                                        submitter: 'accounts\\ei0733,internal\\C46043',
                                                ],
                                                shellcheck     : [:],
                                                plz            : [:]
                                        ],
                                ]
                        ]
                ]
        )
        // need to ensure modules are configured
        release.preValidate()
        def (List<Map<String, Object>> phases, List<String> issues) = new PhaseLoader(script, psc).loadPhases(release.config.phases, null)
        simulatePodTemplate(psc, release)
        when:
        release.addPipelineSteps()
        then:
        Exception ex = thrown()
        ex.class == ErrorStepException.class
        ex.message == 'Cannot support non approval request linting types in release/preRelease blocks'
    }


    def '''that a Release block will accept approvalrequest types.'''() {
        given:
        def release = new Release(script: script, psc: psc,
            config:
                [
                        releaseType           : 'release',
                        releaseBranchPattern  : '^(release|hotfix).*',
                        isProductionDeployment: true,
                        branchPattern         : '.*',
                        phases                : [
                                [
                                        emailRecipients: 'grangaswamy@express-scripts.com',
                                        branchPattern  : '.*',
                                        lintingTypes   : [
                                                approvalrequest: [
                                                        timeOut  : 120,
                                                        message  : 'Do you want to continue deployment to Prod candidate?',
                                                        id       : 'cnpApprove',
                                                        submitter: 'accounts\\ei0733,internal\\C46043',
                                                ],
                                        ],
                                ]
                        ]
                ]
        )
        // need to ensure modules are configured
        release.preValidate()
        def (List<Map<String, Object>> phases, List<String> issues) = new PhaseLoader(script, psc).loadPhases(release.config.phases, null)
        simulatePodTemplate(psc, release)
        when:
        release.addPipelineSteps()
        then:
        def embeddedPhase = release.config.phases.find { it.containsKey('lintingTypes') }
        embeddedPhase.args.moduleType == 'awaitApproval'
        embeddedPhase.args.time == 120
        embeddedPhase.args.message == 'Do you want to continue deployment to Prod candidate?'
        embeddedPhase.args.id == 'cnpApprove'
        embeddedPhase.args.approvers == 'accounts\\ei0733,internal\\C46043'
    }
}
