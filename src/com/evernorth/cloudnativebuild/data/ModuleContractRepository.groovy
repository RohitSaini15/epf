package com.evernorth.cloudnativebuild.data

import com.cloudbees.groovy.cps.NonCPS
import com.evernorth.cloudnativebuild.config.ReleaseConstants
import com.evernorth.cloudnativebuild.model.Credential
import com.evernorth.cloudnativebuild.model.ModuleContract
import com.evernorth.cloudnativebuild.model.ModuleContractType
import com.cigna.common.utils.Utils

/***
 * This class will be replaced in future versions with a dynamic lookup
 * that will pull the configuration from a service
 */
class ModuleContractRepository implements Serializable {

    /**
     * Creates an instance of the repository
     * @param DEFAULT_DOCKER_IMAGE - This item should be configured in Jenkins using an environment variable named DEFAULT_DOCKER_IMAGE
     * @param DEFAULT_K8S_IMAGE - This item should be configured in Jenkins using an environment variable named DEFAULT_K8S_IMAGE
     */
    public static final DFLT_CPU_LIMIT = 700
    public static final DFLT_MEMORY_LIMIT = 1000

    ModuleContractRepository(Map<String, String> env) {
        this.defaultModules = createDefaultModules(env)
    }

    Map<String, List<ModuleContract>> defaultModules
    final static String CNP_TOOLS = "cnptools"
    final static String NPM_STASH_NAME = "npm"

    @NonCPS
    private static Map<String, List<ModuleContract>> createDefaultModules(Map<String, String> env) {
        def digital_aws_credentials = [
            new Credential(id: "DAWS-ESI-MEMBERWEB", type: 'string', env: 'Dev'),
            new Credential(id: "TAWS-ESI-MEMBERWEB", type: 'string', env: 'QA'),
            new Credential(id: "TAWS-ESI-MEMBERWEB", type: 'string', env: 'UAT'),
            new Credential(id: "PAWS-ESI-MEMBERWEB", type: 'string', env: 'PROD')
        ]

        def pcf_credentials = [
            new Credential(id: "CH3PCF04 - Jenkins Space Developer", env: 'dev'),
            new Credential(id: "CH3PCF01 - Jenkins Space Developer", env: "qa"),
            new Credential(id: "CH3PCF05 - Jenkins Space Developer", env: "uat"), // to maintain backwards compatibility for anyone using this env
            new Credential(id: "CH3PCF01 - Jenkins Space Developer", env: "uat-01"),
            new Credential(id: "CH3PCF05 - Jenkins Space Developer", env: "uat-05"),
            new Credential(id: "PS2PCF02 - Jenkins Space Developer", env: 'prod-02'),
            new Credential(id: "PS2PCF03 - Jenkins Space Developer", env: 'prod-03'),
            new Credential(id: "PS2PCF05 - Jenkins Space Developer", env: 'prod-05'),
            new Credential(id: "PS2PCF06 - Jenkins Space Developer", env: 'prod-06'),
            new Credential(id: "PS2PCF07 - Jenkins Space Developer", env: 'prod-07'),
            new Credential(id: "CH3PCF03 - Jenkins Space Developer", env: "dr-03"),
            new Credential(id: "CH3PCF06 - Jenkins Space Developer", env: "dr-06"),
            new Credential(id: "NEWRELIC_API_KEY_NONPROD", type: 'string', prefix: 'NR_NONPROD'),
            new Credential(id: "NEWRELIC_API_KEY_PROD", type: 'string', prefix: 'NR_PROD')
        ]

        def static_aws_credentials = [
            new Credential(id: env.CNP_STATIC_AWS_CRED_DEV, type: 'string', env: 'DEV'),
            new Credential(id: env.CNP_STATIC_AWS_CRED_QA, type: 'string', env: 'QA'),
            new Credential(id: env.CNP_STATIC_AWS_CRED_UAT, type: 'string', env: 'UAT'),
            new Credential(id: env.CNP_STATIC_AWS_CRED_PROD, type: 'string', env: 'PROD')
        ]

        /********************
         * This is a list of contracts that can be used in many contract templates
         */

        /**
         * Module is used to verify that all docker images included in the
         * module list have valid docker images
         */
        def imageValidationModule = Utils.createModuleContract(
            moduleName: 'cnp-image-validation',
            contractName: ModuleContractType.INIT,
            commandName: env.IMAGE_VALIDATION_MODULE_COMMAND,
            image: env.CNP_DEFAULT_INIT_IMAGE
        )

        /**
         * Verifies that git branch protections have been applied
         */
        def gitSettingsCheckModule = Utils.createModuleContract(
            moduleName: 'cnp-preflight-check-git-settings',
            contractName: ModuleContractType.PREFLIGHT_CHECK,
            stageName: "Verify Branch Rules",
            commandName: "cnp-preflight-check-git-settings.sh",
            credentials: [new Credential(id: env.GIT_TOKEN, type: "string")],
            image: env.CNP_DEFAULT_DOCKER_IMAGE,
        )

        /**
         * Verifies that CODEOWNERS file exists and has required info
         */
        def codeOwnersCheckModule = Utils.createModuleContract(
            moduleName: 'cnp-preflight-check-codeowners',
            contractName: ModuleContractType.PREFLIGHT_CHECK,
            stageName: "Verify CODEOWNERS",
            commandName: "cnp-preflight-check-codeowners.sh",
            image: env.CNP_DEFAULT_DOCKER_IMAGE,
        )
        /**
         * Runs another Jenkins job and waits for results
         * Can be used for running tests but also useful for running releases
         */
        def jenkinsJobBuildModule = Utils.createModuleContract(
            moduleName: 'cnp-jenkins-job-build',
            contractName: ModuleContractType.TEST,
            credentials: [new Credential(id: env.JOB_BUILD_CRED)], // this is for jenkins job build
            commandName: "cnp-jenkins-job-build.sh",
            subCommand: "build",
            image: env.CNP_DEFAULT_DOCKER_IMAGE
        )

        /**
         * Launch Darkly CodeRefs tool
         */
        def launchDarklyCodeRefsModule = Utils.createModuleContract(
            moduleName: 'cnp-quality-check-launchdarkly',
            contractName: ModuleContractType.QUALITY_CHECK,
            commandName: "cnp-quality-check-launchdarkly-coderefs.sh",
            subCommand: "check",
            credentials: [new Credential(id: env.GIT_CREDENTIAL),
                          new Credential(id: "ldtoken", type: "string")],
            image: env.CNP_DEFAULT_DOCKER_IMAGE
        )

        /**
         * Release Module - Starts Release in XLR
         */
        def releaseModule = Utils.createModuleContract(
            moduleName: 'cnp-release-xlr',
            contractName: ModuleContractType.RELEASE,
            commandName: "cnp-release-xlr.sh",
            subCommand: "release",
            credentials: [new Credential(id: "xlRelease"),
                          new Credential(id: env.ARTIFACTORY_CREDENTIAL, prefix: 'ARTIFACTORY'),
                          new Credential(id: env.GIT_CREDENTIAL, prefix: 'GIT')
            ],
            image: env.CNP_DEFAULT_DOCKER_IMAGE,
            requiresStash: true,
            requireCheckout: true,
            unStashName: ReleaseConstants.ReleaseStashName
        )

        /**
         * Finalize Release Module
         */
        def finalizeReleaseModule = Utils.createModuleContract(
            moduleName: 'cnp-release-xlr',
            contractName: ModuleContractType.FINALIZE_RELEASE,
            commandName: "cnp-release-xlr.sh",
            subCommand: 'finalize',
            image: env.CNP_DEFAULT_DOCKER_IMAGE,
            credentials: [new Credential(id: env.ARTIFACTORY_CREDENTIAL, prefix: 'AF'),
                          new Credential(id: env.GIT_CREDENTIAL, prefix: 'GIT')],
            requiresUnStash: true,
            unStashName: PipelineConstants.STASH_RELEASE_FILES
        )

        /**
         * State Writer Module saves pipeline information into a release package
         * for use on callback from XLR
         */
        def pipelineStateWriterModule = Utils.createModuleContract(
            moduleName: 'cnp-release-util',
            contractName: ModuleContractType.PIPELINE_STATE_WRITER,
            commandName: "cnp-release-util.sh",
            subCommand: "publish",
            image: env.CNP_DEFAULT_DOCKER_IMAGE,
            requiresUnStash: true,
            unStashName: ReleaseConstants.ReleaseStashName,
            credentials: [new Credential(id: env.ARTIFACTORY_CREDENTIAL)],
        )

        /**
         * Retrieve module pulls pipeline state stored by state writer
         * Used in Release Call Back jobs
         */
        def pipelineStateRetrieveModule = Utils.createModuleContract(
            moduleName: 'cnp-release-util',
            contractName: ModuleContractType.RETRIEVE,
            commandName: "cnp-release-util.sh",
            subCommand: "retrieve",
            image: env.CNP_DEFAULT_DOCKER_IMAGE,
            credentials: [new Credential(id: env.ARTIFACTORY_CREDENTIAL),
                          new Credential(id: env.GIT_CREDENTIAL, prefix: 'GIT')
            ],
            requiresStash: true,
            stashName: PipelineConstants.STASH_RELEASE_FILES
        )

        /**
         * Builds Docker images
         */
        def buildDockerModule = Utils.createModuleContract(
            contractName: ModuleContractType.CONTAINER,
            commandName: CNP_TOOLS,
            subCommand: "buildimage",
            moduleName: "cnp-build-image",
            credentials: [
                new Credential(id: env.CNP_REGISTRY_DEV_CRED),
            ],
            image: env.CNP_DEFAULT_DOCKER_BUILDER_IMAGE
        )

        /**
         * Publish / Promote Docker Images
         */
        def promoteDockerModule = Utils.createModuleContract(
            contractName: ModuleContractType.PUBLISH_IMAGE,
            commandName: CNP_TOOLS,
            subCommand: "publishimage",
            moduleName: "cnp-publish-image",
            credentials: [
                new Credential(id: env.CNP_REGISTRY_PROD_CRED, prefix: 'PROD'),
                new Credential(id: env.CNP_REGISTRY_DEV_CRED),
            ],
            image: env.CNP_DEFAULT_DOCKER_BUILDER_IMAGE
        )

        /**
         * Run Red Hat ACS Security Scan on docker images
         */
        def rhacsModule = Utils.createModuleContract(
            contractName: ModuleContractType.SECURITY_SCAN,
            commandName: 'check-image.sh',
            moduleName: 'cnp-acs-check-image',
            image: env.CNP_DEFAULT_ACS_IMAGE
        )

        /**
         * Argo Rollouts module for Openshift Deployments
         */
        def argoRolloutsOCDeployModule = Utils.createModuleContract(
            moduleName: 'cnp-deploy-argorollouts',
            contractName: ModuleContractType.DEPLOY,
            stageName: 'OpenShift',
            commandName: "cnp-deploy-argorollouts.sh",
            subCommand: "deploy",
            artifactType: PipelineConstants.ARTIFACT_TYPE_CONTAINER,
            credentials: [
                new Credential(id: env.CNP_OC_CRED_DEV, env: "dev"),
                new Credential(id: env.CNP_OC_CRED_QA, env: "qa"),
                new Credential(id: env.CNP_OC_CRED_UAT, env: "uat"),
                new Credential(id: env.CNP_OC_CRED_PROD, env: "prod"),
                new Credential(id: env.CNP_OC_CRED_DR, env: "dr"),
                new Credential(id: env.CNP_CIGNA_GIT, prefix: 'GIT'),
                new Credential(id: env.CNP_ARGOCD_CRED_DEV, env: "dev", type: 'string'),
                new Credential(id: env.CNP_ARGOCD_CRED_QA, env: "qa", type: 'string'),
                new Credential(id: env.CNP_ARGOCD_CRED_UAT, env: "uat", type: 'string'),
                new Credential(id: env.CNP_ARGOCD_CRED_PROD, env: "prod", type: 'string'),
                new Credential(id: env.CNP_ARGOCD_CRED_DR, env: "dr", type: 'string')],
            image: env.CNP_DEFAULT_K8S_IMAGE,
            requiresUnStash: true,
            unStashName: ReleaseConstants.ReleaseStashName,
            postExecuteEvent: PipelineEventType.deployCompleted
        )

        /**
         * Argo Rollouts cutover module for performing BlueGreen cut overs in Openshift
         */
        def argoRolloutsOCCutOverModule = Utils.createModuleContract(
            moduleName: 'cnp-deploy-argorollouts',
            contractName: ModuleContractType.CUTOVER,
            stageName: 'OpenShift',
            commandName: "cnp-deploy-argorollouts.sh",
            subCommand: "promote",
            credentials: [
                new Credential(id: env.CNP_OC_CRED_DEV, env: "dev"),
                new Credential(id: env.CNP_OC_CRED_QA, env: "qa"),
                new Credential(id: env.CNP_OC_CRED_UAT, env: "uat"),
                new Credential(id: env.CNP_OC_CRED_PROD, env: "prod"),
                new Credential(id: env.CNP_OC_CRED_DR, env: "dr")],
            image: env.CNP_DEFAULT_K8S_IMAGE,
            postExecuteEvent: PipelineEventType.cutoverCompleted,
            artifactType: PipelineConstants.ARTIFACT_TYPE_CONTAINER
        )
        /**
         * Argo Rollouts module for EKS Deployments
         */
        def argoRolloutsEKSDeployModule = Utils.createModuleContract(
            contractName: ModuleContractType.DEPLOY,
            stageName: 'EKS',
            commandName: "cnp-deploy-argorollouts.sh",
            moduleName: "cnp-deploy-argorollouts",
            subCommand: "deploy",
            credentials: [
                new Credential(id: env.CNP_EKS_CRED_DEV, env: "dev", prefix: 'AWS', type: 'string'),
                new Credential(id: env.CNP_EKS_CRED_QA, env: "qa", prefix: 'AWS', type: 'string'),
                new Credential(id: env.CNP_EKS_CRED_UAT, env: "uat", prefix: 'AWS', type: 'string'),
                new Credential(id: env.CNP_EKS_CRED_PROD, env: "prod", prefix: 'AWS', type: 'string'),
                new Credential(id: env.CNP_EKS_CRED_DR, env: "dr", prefix: 'AWS', type: 'string'),
                new Credential(id: env.CNP_CIGNA_GIT, prefix: 'GIT'),
                new Credential(id: env.CNP_EKS_ARGOCD_CRED_DEV, env: "dev", type: 'string'),
                new Credential(id: env.CNP_EKS_ARGOCD_CRED_QA, env: "qa", type: 'string'),
                new Credential(id: env.CNP_EKS_ARGOCD_CRED_UAT, env: "uat", type: 'string'),
                new Credential(id: env.CNP_EKS_ARGOCD_CRED_PROD, env: "prod", type: 'string'),
                new Credential(id: env.CNP_EKS_ARGOCD_CRED_DR, env: "dr", type: 'string')],
            image: env.CNP_DEFAULT_K8S_IMAGE,
            artifactType: PipelineConstants.ARTIFACT_TYPE_CONTAINER,
            requiresUnStash: true,
            unStashName: ReleaseConstants.ReleaseStashName,
            postExecuteEvent: PipelineEventType.deployCompleted
        )

        /**
         * Argo Rollouts cutover module for performing BlueGreen cut overs in EKS
         */
        def argoRolloutsEKSCutOverModule = Utils.createModuleContract(
            contractName: ModuleContractType.CUTOVER,
            stageName: 'EKS',
            commandName: "cnp-deploy-argorollouts.sh",
            moduleName: "cnp-deploy-argorollouts",
            subCommand: "promote",
            credentials: [
                new Credential(id: env.CNP_EKS_CRED_DEV, env: "dev", prefix: 'AWS', type: 'string'),
                new Credential(id: env.CNP_EKS_CRED_QA, env: "qa", prefix: 'AWS', type: 'string'),
                new Credential(id: env.CNP_EKS_CRED_UAT, env: "uat", prefix: 'AWS', type: 'string'),
                new Credential(id: env.CNP_EKS_CRED_PROD, env: "prod", prefix: 'AWS', type: 'string'),
                new Credential(id: env.CNP_EKS_CRED_DR, env: "dr", prefix: 'AWS', type: 'string')],
            image: env.CNP_DEFAULT_K8S_IMAGE,
            postExecuteEvent: PipelineEventType.cutoverCompleted,
            artifactType: PipelineConstants.ARTIFACT_TYPE_CONTAINER
        )
        /**
         * NPM Build Module for performing NPM Builds
         */
        def npmBuildModule = Utils.createModuleContract(
            moduleName: 'cnp-build-npm-create',
            contractName: "BUILD",
            commandName: "cnp-build-npm-create.sh",
            subCommand: "build",
            credentials: [new Credential(id: env.NPM_CREDENTIALS_ID, type: 'string'),
                          new Credential(id: env.SONAR_CREDENTIAL_ID, prefix: 'SONAR', type: 'string')],
            image: env.CNP_DEFAULT_NPM_IMAGE,
            requiresUnStash: false,
            requiresStash: true,
            stashName: NPM_STASH_NAME,
            stashPattern: "*manifest.y*l,release.info,package.json"
        )

        /**
         * Original Member Web Module that Deploys content to AWS Cloud Front using Blue / Green deployment
         */
        def awsMemberWebDeployOriginalModule = Utils.createModuleContract(
            moduleName: 'cnp-deploy-aws',
            contractName: "DEPLOY",
            commandName: "cnp-deploy-memberweb-aws.sh",
            subCommand: "deploy",
            stageName: 'AWS',
            credentials: digital_aws_credentials,
            image: env.CNP_DEFAULT_AWS_IMAGE,
            requiresUnStash: true,
            unStashName: NPM_STASH_NAME,
            logFileName: "cnp-deploy-memberweb-aws-results.json"
        )

        /**
         * Duplicate of Original Member Web Module that Deploys content to AWS Cloud Front using Blue / Green deployment
         * using different module name
         */
        def awsMemberWebDeployModule = Utils.createModuleContract(
            moduleName: 'cnp-deploy-memberweb-aws',
            contractName: "DEPLOY",
            commandName: "cnp-deploy-memberweb-aws.sh",
            subCommand: "deploy",
            stageName: 'AWS',
            credentials: digital_aws_credentials,
            image: env.CNP_DEFAULT_AWS_IMAGE,
            requiresUnStash: true,
            unStashName: NPM_STASH_NAME
        )

        /**
         * Deploys static content to AWS Cloud Front using Blue / Green deployment
         */
        def awsStaticDeployModule = Utils.createModuleContract(
            moduleName: 'cnp-deploy-static-aws',
            contractName: "DEPLOY",
            commandName: "cnp-deploy-static-aws.sh",
            subCommand: "deploy",
            stageName: 'AWS',
            credentials: static_aws_credentials,
            image: env.CNP_DEFAULT_AWS_IMAGE,
            requiresUnStash: true,
            unStashName: NPM_STASH_NAME
        )

        /**
         * Original Member Web Cut Over for static content to AWS Cloud Front using Blue / Green deployment
         */
        def awsMemberWebCutOverOriginalModule = Utils.createModuleContract(
            moduleName: 'cnp-cutover-aws',
            contractName: "CUTOVER",
            stageName: 'AWS',
            commandName: "cnp-deploy-memberweb-aws.sh",
            subCommand: "cutover",
            image: env.CNP_DEFAULT_AWS_IMAGE,
            credentials: digital_aws_credentials,
            requiresUnStash: true,
            unStashName: NPM_STASH_NAME,
            logFileName: "cnp-deploy-memberweb-aws-results.json"
        )

        /**
         * Duplicate of Original Cut Over for static content to AWS Cloud Front using Blue / Green deployment
         * using different module name
         */
        def awsMemberWebCutOverModule = Utils.createModuleContract(
            moduleName: 'cnp-deploy-memberweb-aws',
            contractName: "CUTOVER",
            stageName: 'AWS',
            commandName: "cnp-deploy-memberweb-aws.sh",
            subCommand: "cutover",
            image: env.CNP_DEFAULT_AWS_IMAGE,
            credentials: digital_aws_credentials,
            requiresUnStash: true,
            unStashName: NPM_STASH_NAME
        )

        /**
         * Cut Over for static content to AWS Cloud Front using Blue / Green deployment
         */

        def awsStaticCutOverModule = Utils.createModuleContract(
            moduleName: 'cnp-deploy-static-aws',
            contractName: "CUTOVER",
            stageName: 'AWS',
            commandName: "cnp-deploy-static-aws.sh",
            subCommand: "cutover",
            image: env.CNP_DEFAULT_AWS_IMAGE,
            credentials: static_aws_credentials,
            requiresUnStash: true,
            unStashName: NPM_STASH_NAME
        )

        /**
         * Blue Green Deployment to PCF
         */
        def pcfDeployModule = Utils.createModuleContract(
            moduleName: 'cnp-deploy-pcf',
            contractName: "DEPLOY",
            stageName: 'PCF',
            commandName: "cnp-deploy-pcf.sh",
            subCommand: "deploy",
            image: env.CNP_DEFAULT_PCF_IMAGE,
            credentials: pcf_credentials,
            requiresUnStash: true,
            unStashName: ReleaseConstants.ReleaseStashName,
            postExecuteEvent: PipelineEventType.deployCompleted
        )

        /**
         *  Blue Green Deployment Cut over for PCF
         */
        def pcfCutOverModule = Utils.createModuleContract(
            moduleName: 'cnp-deploy-pcf',
            contractName: "CUTOVER",
            stageName: 'PCF',
            commandName: "cnp-deploy-pcf.sh",
            subCommand: "cutover",
            image: env.CNP_DEFAULT_PCF_IMAGE,
            requiresUnStash: true,
            unStashName: ReleaseConstants.ReleaseStashName,
            credentials: pcf_credentials,
            postExecuteEvent: PipelineEventType.cutoverCompleted
        )

        /**
         * Deployment to ansible tower
         */
        def ansibleDeployModule = Utils.createModuleContract(
            moduleName: 'epf-deploy-ansible',
            contractName: ModuleContractType.DEPLOY,
            stageName: 'ANSIBLE',
            commandName: "epf-deploy-ansible.sh",
            subCommand: "deploy",
            image: env.EPF_DEFAULT_ANSIBLE_IMAGE,
            credentials: [
                new Credential(id: env.ANSIBLE_TOKEN, type: 'string')],
            requiresUnStash: true,
            unStashName: ReleaseConstants.ReleaseStashName,
            postExecuteEvent: PipelineEventType.deployCompleted
        )

        /**
         * Build Java Project Using Maven
         */
        def buildMavenModule = Utils.createModuleContract(
            moduleName: 'cnp-build-publish-maven-create',
            contractName: ModuleContractType.BUILD,
            commandName: "cnp-build-publish-maven-create.sh",
            subCommand: "build",
            image: env.CNP_DEFAULT_JAVA_IMAGE,
            credentials: [new Credential(id: env.ARTIFACTORY_CREDENTIAL)],
            requiresUnStash: false,
            requests: [
                cpu   : 50,
                memory: 100
            ],
            limits: [
                cpu   : DFLT_CPU_LIMIT,
                memory: DFLT_MEMORY_LIMIT
            ]
        )
        /**
         * Build Java Project Using Gradle
         */
        def buildGradleModule = Utils.createModuleContract(
            moduleName: 'cnp-build-publish-gradle-create',
            contractName: ModuleContractType.BUILD,
            commandName: "cnp-build-publish-gradle-create.sh",
            subCommand: "build",
            image: env.CNP_DEFAULT_GRADLE_IMAGE,
            requiresUnStash: false,
            credentials: [new Credential(id: env.ARTIFACTORY_CREDENTIAL)],
            requests: [
                cpu   : 50,
                memory: 100
            ],
            limits: [
                cpu   : DFLT_CPU_LIMIT,
                memory: DFLT_MEMORY_LIMIT
            ]
        )

        /**
         * Runs synchronus Checkmarx security scan
         */
        def checkmarxScanModule = Utils.createModuleContract(
            moduleName: 'cnp-quality-profile-checkmarx',
            contractName: ModuleContractType.SECURITY_SCAN,
            commandName: "cnp-quality-profile-checkmarx.sh",
            subCommand: "scan",
            image: env.CNP_DEFAULT_JAVA_IMAGE,
            credentials: [new Credential(id: env.CX_CREDENTIAL, prefix: 'CX')]
        )
        /**
         * Runs asynchronus Checkmarx security scan
         * NOTE: To run this module, a subcommand of "asyncscan" must be specified
         */
        def checkmarxAsyncScanModule = Utils.createModuleContract(
            moduleName: 'cnp-quality-profile-checkmarx',
            contractName: ModuleContractType.SECURITY_SCAN,
            commandName: "cnp-quality-profile-checkmarx.sh",
            subCommand: "asyncscan",
            image: env.CNP_DEFAULT_JAVA_IMAGE,
            credentials: [new Credential(id: env.CX_CREDENTIAL, prefix: 'CX')]
        )
        /**
         * Builds Single Debian Package
         */
        def debianPackagingModule = Utils.createModuleContract(
            moduleName: 'cnp-package-deb',
            contractName: ModuleContractType.PACKAGE,
            commandName: "cnp-package-deb.sh",
            subCommand: "create",
            image: env.CNP_DEFAULT_DOCKER_IMAGE,
            credentials: [new Credential(id: env.ARTIFACTORY_CREDENTIAL)],
        )

        def sonarqubeModule = Utils.createModuleContract(
            moduleName: 'cnp-quality-check-sonarqube',
            contractName: ModuleContractType.QUALITY_CHECK,
            image: env.CNP_DEFAULT_JAVA_IMAGE,
            commandName: 'cnp-quality-check-sonarqube.sh',
            subCommand: 'scan',
            credentials: [new Credential(id: env.SONAR_CREDENTIAL_ID, type: 'string', prefix: 'SQ')]
        )
        def goBuildModule = Utils.createModuleContract(
            moduleName: 'cnp-build-go',
            contractName: "BUILD",
            commandName: "cnp-build-go.sh",
            subCommand: 'build',            
            credentials: [new Credential(id: env.CNP_BUILD_GO_CREDENTIAL)],
            image: env.CNP_DEFAULT_GO_IMAGE
        )

        def goBuildModuleTest = Utils.createModuleContract(
            moduleName: 'cnp-build-go',
            contractName: "BUILD",
            commandName: "cnp-build-go.sh",
            subCommand: 'test',            
            credentials: [new Credential(id: env.CNP_BUILD_GO_CREDENTIAL)],
            image: env.CNP_DEFAULT_GO_IMAGE
        )        

        def newRelicDeploymentMarker = Utils.createModuleContract(
            contractName: ModuleContractType.EVENT,
            commandName: CNP_TOOLS,
            subCommand: "deploymarker",
            image: env.CNP_DEFAULT_DOCKER_IMAGE,
            moduleName: "cnp-event-deployment-marker",
            credentials: [
                new Credential(id: "NEWRELIC_API_KEY_PROD", env: "prod", type: "string"),
                new Credential(id: "NEWRELIC_API_KEY_NONPROD", type: "string")],
            triggeredByEvent: [PipelineEventType.cutoverCompleted]
        )
        final String STATE_FILE_STASH_NAME = "pipelinestate"
        def dojoMessengerModule = Utils.createModuleContract(
            moduleName: 'cnp-dojo-messenger',
            contractName: ModuleContractType.EVENT,
            commandName: 'cnp-dojo-messenger.sh',
            image: env.CNP_DEFAULT_DOJO_MSG_IMAGE,
            requiresUnStash: true,
            unStashName: STATE_FILE_STASH_NAME,
            triggeredByEvent: [PipelineEventType.cutoverCompleted, PipelineEventType.deployCompleted]
        )
        def awsCnpBaseImage = "cnp/cnp-docker-aws-ecr:0.3.5-dev-ov2"

        /**
         * Copy image from Quay to AWS ECR
         */
        def quayToEcrModule = Utils.createModuleContract(
            moduleName: 'cnp-deploy-quay-to-ecr',
            contractName: ModuleContractType.PUBLISH_IMAGE,
            commandName: "cnp-deploy-quay-to-ecr.sh",
            subCommand: "deploy",
            credentials: [[id: 'DAWS-CLOUD-ADOPT', type: 'string'],
                          [id: 'encounter-cnp-robot']
            ],
            image: awsCnpBaseImage
        )

        /**
         * Execute generic terraform
         */
        def terraformModule = Utils.createModuleContract(
            moduleName: 'cnp-deploy-terraform-aws',
            contractName: ModuleContractType.DEPLOY,
            commandName: "cnp-deploy-terraform-aws.sh",
            subCommand: "apply",
            credentials: [
                new Credential(id: env.CNP_AWS_TF_CRED_DEV, env: "dev", type: 'string'),
                new Credential(id: env.CNP_AWS_TF_CRED_QA, env: "qa", type: 'string'),
                new Credential(id: env.CNP_AWS_TF_CRED_UAT, env: "uat", type: 'string'),
                new Credential(id: env.CNP_AWS_TF_CRED_PROD, env: "prod", type: 'string'),
                new Credential(id: env.CNP_AWS_TF_CRED_DR, env: "dr", type: 'string'),
                new Credential(id: env.GIT_CREDENTIAL)],
            image: env.CNP_DEFAULT_TERRAFORM_IMAGE,
            artifactType: PipelineConstants.ARTIFACT_TYPE_GIT_TAG,
            requiresUnStash: true,
            unStashName: ReleaseConstants.ReleaseStashName
        )
        
        def helmRenderModule = Utils.createModuleContract(
            moduleName: 'pipeline-helm',
            contractName: ModuleContractType.BUILD,
            commandName: 'pipeline-helm.sh',
            subCommand: 'render',
            credentials: [
                new Credential(id: env.CNP_CIGNA_GIT, prefix: 'GIT')
            ],
            image: env.CNP_DEFAULT_K8S_IMAGE,
            requiresUnStash: true,
            unStashName: PipelineConstants.STASH_RELEASE_FILES
        )

        [

            "initialize"        : [
                imageValidationModule
            ],
            "go"                : [
                goBuildModule,
                goBuildModuleTest
            ],
            "cnpbundle"         : [
                debianPackagingModule,
                buildDockerModule,
                releaseModule
            ],
            "common"            : [
                gitSettingsCheckModule,
                codeOwnersCheckModule,
                jenkinsJobBuildModule // this is required for preRelease step
            ],
            "quality"           : [
                checkmarxScanModule,
                checkmarxAsyncScanModule,
                sonarqubeModule
            ],
            "release"           : [
                releaseModule,
                finalizeReleaseModule,
                pipelineStateWriterModule,
                pipelineStateRetrieveModule
            ],
            "maven"             : [buildMavenModule],
            "gradle"            : [buildGradleModule],
            "newRelic"          : [newRelicDeploymentMarker],
            "dojoMessenger"     : [dojoMessengerModule],
            "docker"            : [
                buildDockerModule,
                promoteDockerModule
            ],
            "k8s"               : [
                buildDockerModule,
                promoteDockerModule,
                argoRolloutsOCDeployModule,
                argoRolloutsOCCutOverModule,
                helmRenderModule
            ],
            "launchDarkly"      : [
                launchDarklyCodeRefsModule
            ],
            "test"              : [
                jenkinsJobBuildModule
            ],
            "npm"               : [
                npmBuildModule
            ],
            "pcf"               : [
                pcfDeployModule,
                pcfCutOverModule
            ],
            "ansible"           : [
                ansibleDeployModule
            ],
            "openshift"         : [
                jenkinsJobBuildModule,
                buildMavenModule,
                buildGradleModule,
                promoteDockerModule,
                checkmarxScanModule,
                checkmarxAsyncScanModule,
                buildDockerModule,
                argoRolloutsOCDeployModule,
                argoRolloutsOCCutOverModule,
                releaseModule
            ],
            "eks"               : [
                jenkinsJobBuildModule,
                promoteDockerModule,
                checkmarxScanModule,
                checkmarxAsyncScanModule,
                buildDockerModule,
                argoRolloutsEKSDeployModule,
                argoRolloutsEKSCutOverModule,
                releaseModule
            ],
            "ecs"               : [buildDockerModule, checkmarxScanModule, checkmarxAsyncScanModule, quayToEcrModule, terraformModule],
            "terraform"         : [terraformModule],
            "digital"           : [
                npmBuildModule,
                awsMemberWebDeployOriginalModule,
                awsMemberWebDeployModule,
                awsMemberWebCutOverOriginalModule,
                awsMemberWebCutOverModule,
                checkmarxScanModule,
                checkmarxAsyncScanModule,
                pcfDeployModule,
                pcfCutOverModule,
                releaseModule
            ],
            "digital-aws-static": [
                awsMemberWebDeployOriginalModule,
                awsMemberWebDeployModule,
                awsMemberWebCutOverOriginalModule,
                awsMemberWebCutOverModule,
                releaseModule
            ],
            "digital-aws"       : [
                npmBuildModule,
                awsMemberWebDeployOriginalModule,
                awsMemberWebDeployModule,
                awsMemberWebCutOverOriginalModule,
                awsMemberWebCutOverModule,
                checkmarxScanModule,
                checkmarxAsyncScanModule,
                releaseModule
            ],
            "static-aws"        : [
                awsStaticDeployModule,
                awsStaticCutOverModule
            ],
            "digital-pcf"       : [
                npmBuildModule,
                pcfDeployModule,
                pcfCutOverModule,
                checkmarxScanModule,
                checkmarxAsyncScanModule,
                releaseModule
            ],
            "acs"               : [
                rhacsModule
            ]
        ]
    }
}
