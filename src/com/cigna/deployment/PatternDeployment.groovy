package com.cigna.deployment


import com.cigna.common.utils.GoEnvBuilder
import com.cigna.common.utils.PlzUtils
import com.cigna.common.utils.Utils
import com.cloudbees.groovy.cps.NonCPS
import com.cigna.common.kubernetes.PodTemplateCreator

/**
 * Pattern Deployment
 */
class PatternDeployment extends Deployment {
    protected static final String LINT_STAGE = 'Lint Project Code'
    protected static final String TEST_STAGE = 'Test Project Code'
    protected static final String PLAN_STAGE = 'Plan Infrastructure Changes'
    protected static final String CONFTEST_STAGE = 'Check Infrastructure Compliance'
    protected static final String DEPLOY_STAGE = 'Deploy Infrastructure Changes'
    protected static final String DESTROY_STAGE = 'Destroy Infrastructure'
    protected static final String CLEAN_STAGE = 'Clean Previous Deployments'

    PatternDeployment() {
        containerName = 'pattern-deployervbase-latest'
        containerImage = 'webappd/pattern-deployer'
        containerVersion = 'base-latest'
        awsAllowedPhase = true
    }

    @Override
    Boolean prePodConfig() {
        List<Map> env = GoEnvBuilder.buildGoDeploymentEnv([
            [
                name : 'AWS_SHARED_CREDENTIALS_FILE',
                value: '/home/jenkins/.aws/credentials'
            ],
            [
                name : 'TF_IN_AUTOMATION',
                value: 'true'
            ],

        ])

        PodTemplateCreator containerTemplate = PodTemplateCreator.newPodTemplateCreator()
        containerTemplate.addContainer(
            containerName,
            "${containerImage}:${containerVersion}",
            'Always',
            true,
            '/home/jenkins/agent',
            10,
            50,
            1000,
            1000,
            [
                '/usr/local/bin/adhoc-perms'
            ],
            env
        )
        def containerPattern = containerTemplate.getContainer(containerName)
        containerPattern.put('args', com.cigna.common.utils.Utils.defaultSidecarCommand)

        additionalPodConfig = [
                'volumes'   : [],
                'containers': [containerPattern]
        ]
        if ("${script.env.AUTH_TYPE}" != 'gatekeeper') {
            additionalPodConfig.containers[0].env += [name: 'AWS_PROFILE', value: 'saml']
        }

        super.prePodConfig()
    }

    String patternDeployerGit = 'github.sys.cigna.com/cigna/terraform-pattern-deployer.git'
    String raasGit = 'github.sys.cigna.com/cigna/aws-roles-as-a-service.git'
    String patternDeployerVersion = 'master'
    String deployStatus = 'Preparing Deploy'
    String plzArgs
    String terraIam
    String logLevel
    String patternConfig
    String cleanConfig
    String planConfig
    String deployConfig
    String terragruntInitArgs
    String terragruntPlanArgs
    String terragruntApplyArgs


    /*
     * Overriding validate to add awsFed specific configs
     */

    @Override
    @NonCPS
    List validate(boolean requiresBranchPattern = false) {
        List issues = []

        if (config?.awsFed) {
            additionalValidationItems += [
                    'awsFed.credentialsId'
            ]
        }

        if (config?.aws) {
            additionalValidationItems += [
                    'aws.targetAccount',
                    'aws.accountRoleName',
            ]
        }

        List validationIssues = super.validate(requiresBranchPattern)
        validationIssues + issues
    }

    /*
     * Parse Terragrunt Configuration
     */

    void parseTerragrunt() {
        terragruntInitArgs = config.terragrunt?.args?.init?.replaceAll(' ', '%~1') ?: ''
        terragruntPlanArgs = config.terragrunt?.args?.plan?.replaceAll(' ', '%~1') ?: ''
        terragruntApplyArgs = config.terragrunt?.args?.apply?.replaceAll(' ', '%~1') ?: '-auto-approve'
    }

    /*
     * Setup run configurations used by pattern deployer
     */

    @SuppressWarnings(['CyclomaticComplexity'])
    void parseConfiguration() {
        String conftestPolicyVersion = config?.conftest?.policyVersion ?: 'default'
        String terraformVersion = config?.terraform?.version ?: 'default'
        String terragruntVersion = config?.terragrunt?.version ?: 'default'
        String cleanConfiguration = config?.clean_configuration ?: ''
        parseTerragrunt()
        patternConfig = "-n ${config?.patternName} -v ${config?.patternVersion}"
        cleanConfig = "${patternConfig} -f ${terraformVersion} -g ${terragruntVersion} " +
                "-c ${cleanConfiguration}"
        planConfig = "-d ${config?.deploymentID} ${patternConfig} -f ${terraformVersion} " +
                "-g ${terragruntVersion} -i ${terragruntInitArgs} -p ${terragruntPlanArgs} " +
                "-c ${conftestPolicyVersion}"
        deployConfig = "${planConfig} -a ${terragruntApplyArgs}"
    }

    /*
     * Clone Pattern Deployer
     */

    void setupDeployer() {
        String gitCredential = config?.gitCredential ?: 'prd-github-access-token'
        psc.podSelector.select(psc,podTemplateContainerName, Utils.cloud(config)) {
            script.withCredentials(
                    [script.string(
                            credentialsId: gitCredential,
                            variable: 'githubtoken'
                    )]
            ) {
                script.sh(
                        "git clone https://${script.githubtoken}@${patternDeployerGit} " +
                                "--branch ${patternDeployerVersion} --single-branch"
                )
                script.sh(
                        "git clone https://${script.githubtoken}@${raasGit} " +
                                '--branch main --single-branch'
                )
            }
        }
    }

    /*
     * Run Pre-Deploy Stages
     */

    void predeployStages() {
        script.stage(LINT_STAGE) {
            script.sh("plz ${plzArgs} lint ${patternConfig}")
        }
        script.stage(TEST_STAGE) {
            script.sh("plz ${plzArgs} run-tests ${patternConfig}")
        }
        withPhaseConfigEnv(["TERRAGRUNT_IAM_ROLE=${terraIam}", "TF_LOG=${logLevel}"]) {
            script.stage(PLAN_STAGE) {
                script.sh(
                        "plz ${plzArgs} plan ${planConfig}"
                )
            }
            script.stage(CONFTEST_STAGE) {
                script.sh(
                        "plz ${plzArgs} conftest ${planConfig}"
                )
            }
        }
    }

    /*
     * Run Deploy Stage
     */

    void deployStage() {
        withPhaseConfigEnv(["TERRAGRUNT_IAM_ROLE=${terraIam}", "TF_LOG=${logLevel}"]) {
            script.stage(DEPLOY_STAGE) {
                script.sh("plz ${plzArgs} deploy ${deployConfig}")
            }
        }
    }

    /*
     * Run Destroy Stage
     */

    void destroyStage() {
        withPhaseConfigEnv(["TERRAGRUNT_IAM_ROLE=${terraIam}", "TF_LOG=${logLevel}"]) {
            script.stage(DESTROY_STAGE) {
                script.sh("plz ${plzArgs} destroy ${deployConfig}")
            }
        }
    }

    /*
     * Run Clean Stage
     */

    void cleanStage() {
        withPhaseConfigEnv(["TERRAGRUNT_IAM_ROLE=${terraIam}", "TF_LOG=${logLevel}"]) {
            script.stage(CLEAN_STAGE) {
                script.sh("plz ${plzArgs} clean-deployments ${cleanConfig}")
            }
        }
    }

    /**
     * Call plz with the given alias (deploy default) with the given extraArgs and verbosity flag
     */
    @SuppressWarnings(['UnusedVariable', 'CyclomaticComplexity'])
    @Override
    void deploy() {
        parseConfiguration()
        logLevel = config?.terraform?.logLevel ?: 'ERROR'
        Boolean aws = config?.runInAWS ?: false
        String action = config?.action?.toLowerCase() ?: 'plan'
        terraIam = (aws) ?
                'arn:aws:iam::' + config.aws.targetAccount + ':role/' + config.aws.accountRoleName : ''
        plzArgs = PlzUtils.constructArgs(config?.extraArgs, config?.verbosityFlag)

        psc.podSelector.select(psc,podTemplateContainerName, Utils.cloud(config)) {
            // Configure Please Build
            PlzUtils.plzConfigureCache(script, config)
            PlzUtils.dependencyOverride(script, config)

            // Pattern Plan/Deploy Steps
            Closure codeToRunAfter = {
                setupDeployer()
                script.dir('terraform-pattern-deployer') {
                    switch (action) {
                        case 'deploy':
                            predeployStages()
                            deployStage()
                            break
                        case 'destroy':
                            destroyStage()
                            break
                        case 'clean':
                            cleanStage()
                            break
                        default:
                            predeployStages()
                            break
                    }
                }
            }

            awsLogin(codeToRunAfter)
        }
    }
}
