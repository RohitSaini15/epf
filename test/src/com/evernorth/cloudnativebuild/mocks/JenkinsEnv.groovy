package com.evernorth.cloudnativebuild.mocks

import com.cloudbees.groovy.cps.NonCPS
import com.evernorth.cloudnativebuild.model.BaseDefaults

/**
 * This class simulates the Jenkins env DSL command.
 */
class JenkinsEnv implements GroovyObject {
    @NonCPS
    static JenkinsEnv build(Map<String, String> defaultEnv = [:]) {
        def env = new JenkinsEnv()
        if (defaultEnv.size() > 0) {
            env.mockEnvironment.putAll(defaultEnv)
        }
        env
    }

    private JenkinsEnv() {
    }

    Object getProperty(String propertyName) {
        if (propertyName == 'mockEnvironment') {
            mockEnvironment
        } else {
            mockEnvironment[propertyName]
        }

    }

    /**
     * Sets the given property to the new value.
     *
     * @param propertyName the name of the property of interest
     * @param newValue the new value for the property
     */
    void setProperty(String propertyName, Object newValue) {
        if (propertyName != 'mockEnvironment') {
            mockEnvironment[propertyName] = newValue
        } else {
            mockEnvironment = newValue
        }
    }

    def propertyMissing(String propertyName) {
        if (propertyName != 'mockEnvironment') {
            if (mockEnvironment.containsKey(propertyName)) {
                return mockEnvironment[propertyName]
            }
        }
        ''
    }

    class defaults {
        public static def MOCK_CRED_ID = "somecred"
        // the following properties allow us to emulate the Jenkins env.VAR_NAME
        public static def STASH_ID = null
        public static def CNP_POD_SERVICE_ACCOUNT = BaseDefaults.defaultServiceAccount
        public static def JOB_URL = "${TestConstants.JENKINS_URL}/default/job/url"
        public static def CNP_POD_IDLE_MINUTES = BaseDefaults.podIdleMinutes
        public static def CNP_POD_NODE_SELECTOR = BaseDefaults.podNodeSelector
        public static def CNP_POD_WORKING_DIR = BaseDefaults.podWorkingDir
        public static def CNP_POD_COMMAND = BaseDefaults.podCommand
        public static def CNP_POD_COMMAND_ARGS = BaseDefaults.podCommandArgs
        public static def https_proxy = "http://10.221.221.1:3128"
        public static def http_proxy = "http://10.221.221.1:3128"
        public static def no_proxy = "localhost,127.0.0.1,.express-scripts.com,.sys.cigna.com,.medco.com"
        public static def CNP_DEB_PUBLIC_MIRROR = BaseDefaults.debPublicMirror
        public static def CNP_DEB_LOCAL = BaseDefaults.debLocal
        public static def CNP_DEB_VIRTUAL = BaseDefaults.debLocal
        public static def CNP_SIMULATE_ONLY = "${BaseDefaults.simulateOnly}"
        public static def CNP_DISABLE_ALL_TESTS = "${BaseDefaults.disableAllTests}"
        public static def ARTIFACTORY_ROOT_URL = BaseDefaults.artifactoryRootUrl
        public static def CX_SERVER_ADDRESS = BaseDefaults.cxServerAddress
        public static def NODE_NAME = 'somenode'
        public static def BRANCH_NAME = 'release/1.0.0'
        public static def JOB_NAME = 'test/test/unit_test_job'
        public static def BUILD_NUMBER = '1'
        public static def BUILD_URL = 'some build url'
        public static def BUILD_NAME = 'some name'
        public static def BUILD_DISPLAY_NAME = '#50'
        public static def BUILD_TAG = 'some tag'
        public static def WORKSPACE = '/home/jenkins/myfolder'
        public static def LOG_LEVEL = 'TRACE'
        public static def CNP_LOG_LEVEL = 'ERROR'
        public static def CNP_DOCKER_REGISTRY = BaseDefaults.dockerRegistry
        public static def CNP_DEFAULT_DOCKER_IMAGE = BaseDefaults.defaultDockerImage
        public static def CNP_DEFAULT_K8S_IMAGE = BaseDefaults.defaultK8sImage
        public static def CNP_DEFAULT_DOCKER_BUILDER_IMAGE = BaseDefaults.defaultDockerBuilderImage
        public static def CNP_DEFAULT_GO_IMAGE = BaseDefaults.defaultGoImage
        public static def CNP_DEFAULT_INIT_IMAGE = BaseDefaults.defaultInitImage
        public static def CNP_DEFAULT_AWS_IMAGE = BaseDefaults.defaultAwsImage
        public static def CNP_DEFAULT_PCF_IMAGE = BaseDefaults.defaultPcfImage
        public static def EPF_DEFAULT_ANSIBLE_IMAGE = BaseDefaults.epfDefaultAnsibleImage
        public static def GIT_CREDENTIAL = "gitcred"
        public static def GIT_BRANCH = 'release/1.0.0'
        public static def GIT_COMMIT = ""
        public static def GIT_COMMIT_SHORT = ""
        public static def GIT_PREVIOUS_COMMIT = ""
        public static def GIT_PREVIOUS_COMMIT_SHORT = ""
        public static def GIT_PREVIOUS_SUCCESSFUL_COMMIT = ""
        public static def GIT_PREVIOUS_SUCCESSFUL_COMMIT_SHORT = ""
        public static def GIT_URL = ""
        public static def scmVars = [
                GIT_COMMIT                          : GIT_COMMIT,
                GIT_COMMIT_SHORT                    : GIT_COMMIT_SHORT,
                GIT_PREVIOUS_COMMIT                 : GIT_PREVIOUS_COMMIT,
                GIT_PREVIOUS_SUCCESSFUL_COMMIT      : GIT_PREVIOUS_SUCCESSFUL_COMMIT,
                GIT_PREVIOUS_SUCCESSFUL_COMMIT_SHORT: GIT_PREVIOUS_SUCCESSFUL_COMMIT_SHORT,
                GIT_URL                             : GIT_URL,
        ]
        public static def CHECKED_OUT = false
        public static def ARTIFACTORY_CREDENTIAL = BaseDefaults.artifactoryCredential
        public static def NPM_CREDENTIALS_ID = BaseDefaults.npmCredentialsId
        public static def CNP_PREFLIGHT_TERRAFORM_MODULE_USER = "deployer_122344"
        public static def CNP_PREFLIGHT_TERRAFORM_MODULE_PASSWORD = "fakepasswordfortesting"
        public static def CNP_DEPLOY_ARGOROLLOUTS_MODULE_TOKEN = "faketoken"
        public static def CNP_OVERRIDE_COMMON = false
        public static def CNP_DEPLOY_ARGOROLLOUTS_FOO_MODULE_TOKEN = "FOO"
        public static def CNP_DEPLOY_AWS_MODULE_USER = "FOO"
        public static def EN_MODULE_USER = "BAR"
        public static def EN_MODULE_PASSWORD = "BAR"
        public static def CNP_DEPLOYABLE_BRANCHES = BaseDefaults.deployableBranches
        public static def JENKINS_URL = "https://orchestrator1.orchestrator-v2.sys.cigna.com/"
        public static def CNP_DISABLE_ALERTS = "${BaseDefaults.disableAlerts}"
        public static def CNP_DOCKER_DEV_REGISTRY = BaseDefaults.dockerDevRegistry
        public static def CNP_IMAGE_REPO = BaseDefaults.imageRepo
        public static def CNP_DEFAULT_NPM_IMAGE = BaseDefaults.defaultNpmImage
        public static def CNP_DEFAULT_TERRAFORM_IMAGE = BaseDefaults.defaultTerraformImage
        public static def CNP_DEB_PRIVATE = BaseDefaults.debPrivate
        public static def CNP_XLR_TEMPLATE_URL = BaseDefaults.xlrTemplateUrl
        public static def CNP_CALLBACK_JOB = BaseDefaults.callbackJob
        public static def CNP_DISABLE_JIRASCAN = "${BaseDefaults.disableJirascan}"
        public static def CNP_CANDIDATE_JOB = BaseDefaults.candidateJob
        public static def CNP_IGNORE_CONFTEST_ANALYSIS_FAILURE = "${BaseDefaults.ignoreConftestAnalysisFailure}"
        public static def CNP_RELEASE_INFO_FILE_NAME = BaseDefaults.releaseInfoFileName
        public static def CNP_DEPLOY_AWS_MODULE_PASSWORD = "someuser"
        public static def CNP_DEPLOY_AWS_CH3PCF04_MODULE_USER = "foo"
        public static def CNP_DEPLOY_AWS_CH3PCF04_MODULE_PASSWORD = "someuser"
        public static def CNP_DEPLOY_PCF_CH3PCF04_MODULE_USER = "foo"
        public static def CNP_DEPLOY_PCF_CH3PCF04_MODULE_PASSWORD = "somepass"
        public static def CNP_DEPLOY_AWS_CH3PCF01_MODULE_USER = "someuser"
        public static def CNP_DEPLOY_AWS_CH3PCF01_MODULE_PASSWORD = "somepass"
        public static def CNP_DEPLOY_PCF_CH3PCF01_MODULE_USER = "someuser"
        public static def CNP_DEPLOY_PCF_CH3PCF01_MODULE_PASSWORD = "somepass"
        public static def CNP_DEPLOY_STATIC_AWS_MODULE_USER = "aaa"
        public static def CNP_DEPLOY_STATIC_AWS_MODULE_PASSWORD = "bbb"
        public static def CNP_DEPLOY_STATIC_AWS_MODULE_TOKEN = "token"
        public static def CNP_DEPLOY_PCF_MODULE_USER = "someuser"
        public static def CNP_DEPLOY_PCF_MODULE_PASSWORD = "somepass"
        public static def CNP_RELEASE_XLR_AF_MODULE_USER = "someuser"
        public static def CNP_RELEASE_XLR_AF_MODULE_PASSWORD = 'aaa'
        public static def CNP_RELEASE_XLR_GIT_MODULE_USER = "someuser"
        public static def CNP_RELEASE_XLR_GIT_MODULE_PASSWORD = 'aaa'
        public static def CNP_RELEASE_UTIL_MODULE_USER = "someuser"
        public static def CNP_RELEASE_UTIL_MODULE_PASSWORD = "somepass"
        public static def CNP_ENABLE_MULTI_CLOUD_TRANSACTIONAL_RELEASES = false
        public static def CNP_DEPLOY_ARGOROLLOUT_MODULE_TOKEN = "token"
        public static def EPF_DEPLOY_ANSIBLE_NONPROD_MODULE_TOKEN = "token"
        public static def EPF_DEPLOY_ANSIBLE_PROD_MODULE_TOKEN = "token"
        public static def CNP_RELEASE_UTI_MODULE_USER = "someuser"
        public static def CNP_RELEASE_UTI_MODULE_PASSWORD = "somepass"
        public static def CNP_DEPLOY_ARGOROLLOUT_FOO_MODULE_TOKEN = "token"
        public static def CNP_RELEASE_XLR_MODULE_USER = "someuser"
        public static def CNP_RELEASE_XLR_MODULE_PASSWORD = "somepass"
        public static def CNP_JENKINS_JOB_BUILD_MODULE_USER = "builduser"
        public static def CNP_JENKINS_JOB_BUILD_MODULE_PASSWORD = "somepwd"
        public static def CNP_RELEASE_UTIL_GIT_MODULE_USER = "user"
        public static def CNP_RELEASE_UTIL_GIT_MODULE_PASSWORD = "pass"
        public static def CNP_DEPLOY_ARGOROLLOUTS_MODULE_USER = "user"
        public static def CNP_DEPLOY_ARGOROLLOUTS_MODULE_PASSWORD = "pass"
        public static def CNP_DEPLOY_ARGOROLLOUTS_GIT_MODULE_USER = "user"
        public static def CNP_DEPLOY_ARGOROLLOUTS_GIT_MODULE_PASSWORD = "pass"
        public static def IMAGE_VALIDATION_ENABLED = null
        public static def IMAGE_VALIDATION_MODULE_NAME = null
        public static def IMAGE_VALIDATION_MODULE_COMMAND = null
        public static def CNP_REGISTRY_DEV_CRED = MOCK_CRED_ID
        public static def CNP_REGISTRY_PROD_CRED = MOCK_CRED_ID
        public static def CNP_OC_CRED_DEV = MOCK_CRED_ID
        public static def CNP_OC_CRED_QA = MOCK_CRED_ID
        public static def CNP_OC_CRED_UAT = MOCK_CRED_ID
        public static def CNP_OC_CRED_PROD = MOCK_CRED_ID
        public static def CNP_OC_CRED_DR = MOCK_CRED_ID
        public static def CNP_CIGNA_GIT = MOCK_CRED_ID
        public static def CNP_ARGOCD_CRED_DEV = MOCK_CRED_ID
        public static def CNP_ARGOCD_CRED_QA = MOCK_CRED_ID
        public static def CNP_ARGOCD_CRED_UAT = MOCK_CRED_ID
        public static def CNP_ARGOCD_CRED_PROD = MOCK_CRED_ID
        public static def CNP_ARGOCD_CRED_DR = MOCK_CRED_ID

        public static def CNP_DEFAULT_JAVA_IMAGE = BaseDefaults.defaultJavaImage
        public static def CNP_DEFAULT_GRADLE_IMAGE = BaseDefaults.defaultGradleImage
        public static def DEFAULT_INCLUSION_PATTERN = "releaseInfo.json,*manifest.y*l,deployment*/,releaseNotes.txt,release.info"
        public static def AUTHORIZED_USERS = "xlrelease,internal\\svp_pipeline_admin,accounts\\ei0733"
        public static def PHASE_NAME = 'Test Phase'
    }
    // This Map allows us to manipulate the map used by env.getEnvironment()
    // allowing us to simulate when a value is not defined
    def mockEnvironment = [
            STASH_ID                                     : defaults.STASH_ID,
            CNP_POD_SERVICE_ACCOUNT                      : defaults.CNP_POD_SERVICE_ACCOUNT,
            PHASE_NAME                                   : defaults.PHASE_NAME,
            JOB_URL                                      : defaults.JOB_URL,
            CNP_POD_IDLE_MINUTES                         : defaults.CNP_POD_IDLE_MINUTES,
            CNP_POD_NODE_SELECTOR                        : defaults.CNP_POD_NODE_SELECTOR,
            CNP_POD_WORKING_DIR                          : defaults.CNP_POD_WORKING_DIR,
            CNP_POD_COMMAND                              : defaults.CNP_POD_COMMAND,
            CNP_POD_COMMAND_ARGS                         : defaults.CNP_POD_COMMAND_ARGS,
            http_proxy                                   : defaults.http_proxy,
            https_proxy                                  : defaults.https_proxy,
            no_proxy                                     : defaults.no_proxy,
            NODE_NAME                                    : defaults.NODE_NAME,
            BRANCH_NAME                                  : defaults.BRANCH_NAME,
            JOB_NAME                                     : defaults.JOB_NAME,
            BUILD_NUMBER                                 : defaults.BUILD_NUMBER,
            BUILD_URL                                    : defaults.BUILD_URL,
            BUILD_NAME                                   : defaults.BUILD_NAME,
            BUILD_DISPLAY_NAME                           : defaults.BUILD_DISPLAY_NAME,
            BUILD_TAG                                    : defaults.BUILD_TAG,
            WORKSPACE                                    : defaults.WORKSPACE,
            LOG_LEVEL                                    : defaults.LOG_LEVEL,
            CNP_DEB_PUBLIC_MIRROR                        : defaults.CNP_DEB_PUBLIC_MIRROR,
            CNP_DEFAULT_DOCKER_IMAGE                     : defaults.CNP_DEFAULT_DOCKER_IMAGE,
            CNP_DEFAULT_K8S_IMAGE                        : defaults.CNP_DEFAULT_K8S_IMAGE,
            CNP_DEFAULT_DOCKER_BUILDER_IMAGE             : defaults.CNP_DEFAULT_DOCKER_BUILDER_IMAGE,
            CNP_DEFAULT_GO_IMAGE                         : defaults.CNP_DEFAULT_GO_IMAGE,
            CNP_DEFAULT_INIT_IMAGE                       : defaults.CNP_DEFAULT_INIT_IMAGE,
            CNP_DEFAULT_NPM_IMAGE                        : defaults.CNP_DEFAULT_NPM_IMAGE,
            CNP_DEFAULT_PCF_IMAGE                        : defaults.CNP_DEFAULT_PCF_IMAGE,
            EPF_DEFAULT_ANSIBLE_IMAGE                    : defaults.EPF_DEFAULT_ANSIBLE_IMAGE,
            CNP_DEFAULT_AWS_IMAGE                        : defaults.CNP_DEFAULT_AWS_IMAGE,
            CNP_DEFAULT_TERRAFORM_IMAGE                  : defaults.CNP_DEFAULT_TERRAFORM_IMAGE,
            CNP_IMAGE_REPO                               : defaults.CNP_IMAGE_REPO,
            CNP_DEB_LOCAL                                : defaults.CNP_DEB_LOCAL,
            CNP_DEB_VIRTUAL                              : defaults.CNP_DEB_VIRTUAL,
            CNP_DEB_PRIVATE                              : defaults.CNP_DEB_PRIVATE,
            DEFAULT_INCLUSION_PATTERN                    : defaults.DEFAULT_INCLUSION_PATTERN,
            ARTIFACTORY_ROOT_URL                         : defaults.ARTIFACTORY_ROOT_URL,
            ARTIFACTORY_CREDENTIAL                       : defaults.ARTIFACTORY_CREDENTIAL,
            NPM_CREDENTIALS_ID                           : defaults.NPM_CREDENTIALS_ID,
            CX_SERVER_ADDRESS                            : defaults.CX_SERVER_ADDRESS,
            GIT_CREDENTIAL                               : defaults.GIT_CREDENTIAL,
            CNP_PREFLIGHT_TERRAFORM_MODULE_USER          : defaults.CNP_PREFLIGHT_TERRAFORM_MODULE_USER,
            CNP_PREFLIGHT_TERRAFORM_MODULE_PASSWORD      : defaults.CNP_PREFLIGHT_TERRAFORM_MODULE_PASSWORD,
            CNP_DEPLOY_ARGOROLLOUTS_MODULE_TOKEN         : defaults.CNP_DEPLOY_ARGOROLLOUTS_MODULE_TOKEN,
            CNP_IGNORE_CONFTEST_ANALYSIS_FAILURE         : defaults.CNP_IGNORE_CONFTEST_ANALYSIS_FAILURE,
            CNP_LOG_LEVEL                                : defaults.CNP_LOG_LEVEL,
            CNP_OVERRIDE_COMMON                          : defaults.CNP_OVERRIDE_COMMON,
            CNP_DEPLOY_ARGOROLLOUTS_FOO_MODULE_TOKEN     : defaults.CNP_DEPLOY_ARGOROLLOUTS_FOO_MODULE_TOKEN,
            CNP_SIMULATE_ONLY                            : defaults.CNP_SIMULATE_ONLY,
            CNP_DISABLE_ALL_TESTS                        : defaults.CNP_DISABLE_ALL_TESTS,
            CNP_DISABLE_ALERTS                           : defaults.CNP_DISABLE_ALERTS,
            CNP_DOCKER_REGISTRY                          : defaults.CNP_DOCKER_REGISTRY,
            CNP_XLR_TEMPLATE_URL                         : defaults.CNP_XLR_TEMPLATE_URL,
            CNP_CALLBACK_JOB                             : defaults.CNP_CALLBACK_JOB,
            CNP_DISABLE_JIRASCAN                         : defaults.CNP_DISABLE_JIRASCAN,
            EN_MODULE_USER                               : defaults.EN_MODULE_USER,
            EN_MODULE_PASSWORD                           : defaults.EN_MODULE_PASSWORD,
            CNP_DEPLOYABLE_BRANCHES                      : defaults.CNP_DEPLOYABLE_BRANCHES,
            CNP_DOCKER_DEV_REGISTRY                      : defaults.CNP_DOCKER_DEV_REGISTRY,
            JENKINS_URL                                  : defaults.JENKINS_URL,
            CNP_RELEASE_INFO_FILE_NAME                   : defaults.CNP_RELEASE_INFO_FILE_NAME,
            CNP_DEPLOY_AWS_MODULE_USER                   : defaults.CNP_DEPLOY_AWS_MODULE_USER,
            CNP_DEPLOY_AWS_MODULE_PASSWORD               : defaults.CNP_DEPLOY_AWS_MODULE_PASSWORD,
            CNP_DEPLOY_AWS_CH3PCF04_MODULE_USER          : defaults.CNP_DEPLOY_AWS_CH3PCF04_MODULE_USER,
            CNP_DEPLOY_AWS_CH3PCF04_MODULE_PASSWORD      : defaults.CNP_DEPLOY_AWS_CH3PCF04_MODULE_PASSWORD,
            CNP_DEPLOY_PCF_CH3PCF04_MODULE_USER          : defaults.CNP_DEPLOY_PCF_CH3PCF04_MODULE_USER,
            CNP_DEPLOY_PCF_CH3PCF04_MODULE_PASSWORD      : defaults.CNP_DEPLOY_PCF_CH3PCF04_MODULE_PASSWORD,
            CNP_DEPLOY_AWS_CH3PCF01_MODULE_USER          : defaults.CNP_DEPLOY_AWS_CH3PCF01_MODULE_USER,
            CNP_DEPLOY_AWS_CH3PCF01_MODULE_PASSWORD      : defaults.CNP_DEPLOY_AWS_CH3PCF01_MODULE_PASSWORD,
            CNP_DEPLOY_PCF_CH3PCF01_MODULE_USER          : defaults.CNP_DEPLOY_PCF_CH3PCF01_MODULE_USER,
            CNP_DEPLOY_PCF_CH3PCF01_MODULE_PASSWORD      : defaults.CNP_DEPLOY_PCF_CH3PCF01_MODULE_PASSWORD,
            CNP_DEPLOY_STATIC_AWS_MODULE_USER            : defaults.CNP_DEPLOY_STATIC_AWS_MODULE_USER,
            CNP_DEPLOY_STATIC_AWS_MODULE_PASSWORD        : defaults.CNP_DEPLOY_STATIC_AWS_MODULE_PASSWORD,
            CNP_DEPLOY_STATIC_AWS_MODULE_TOKEN           : defaults.CNP_DEPLOY_STATIC_AWS_MODULE_TOKEN,
            CNP_DEPLOY_PCF_MODULE_USER                   : defaults.CNP_DEPLOY_PCF_MODULE_USER,
            CNP_DEPLOY_PCF_MODULE_PASSWORD               : defaults.CNP_DEPLOY_PCF_MODULE_PASSWORD,
            CNP_RELEASE_UTIL_MODULE_USER                 : defaults.CNP_RELEASE_UTIL_MODULE_USER,
            CNP_RELEASE_UTIL_MODULE_PASSWORD             : defaults.CNP_RELEASE_UTIL_MODULE_PASSWORD,
            CNP_ENABLE_MULTI_CLOUD_TRANSACTIONAL_RELEASES: defaults.CNP_ENABLE_MULTI_CLOUD_TRANSACTIONAL_RELEASES,
            CNP_DEPLOY_ARGOROLLOUT_MODULE_TOKEN          : defaults.CNP_DEPLOY_ARGOROLLOUT_MODULE_TOKEN,
            CNP_RELEASE_UTI_MODULE_USER                  : defaults.CNP_RELEASE_UTI_MODULE_USER,
            CNP_RELEASE_UTI_MODULE_PASSWORD              : defaults.CNP_RELEASE_UTI_MODULE_PASSWORD,
            CNP_DEPLOY_ARGOROLLOUT_FOO_MODULE_TOKEN      : defaults.CNP_DEPLOY_ARGOROLLOUT_FOO_MODULE_TOKEN,
            CNP_RELEASE_XLR_MODULE_USER                  : defaults.CNP_RELEASE_XLR_MODULE_USER,
            CNP_RELEASE_XLR_MODULE_PASSWORD              : defaults.CNP_RELEASE_XLR_MODULE_PASSWORD,
            CNP_JENKINS_JOB_BUILD_MODULE_USER            : defaults.CNP_JENKINS_JOB_BUILD_MODULE_USER,
            CNP_JENKINS_JOB_BUILD_MODULE_PASSWORD        : defaults.CNP_JENKINS_JOB_BUILD_MODULE_PASSWORD,
            CNP_RELEASE_UTIL_GIT_MODULE_USER             : defaults.CNP_RELEASE_UTIL_GIT_MODULE_USER,
            CNP_RELEASE_UTIL_GIT_MODULE_PASSWORD         : defaults.CNP_RELEASE_UTIL_GIT_MODULE_PASSWORD,
            IMAGE_VALIDATION_ENABLED                     : defaults.IMAGE_VALIDATION_ENABLED,
            IMAGE_VALIDATION_MODULE_NAME                 : defaults.IMAGE_VALIDATION_MODULE_NAME,
            IMAGE_VALIDATION_MODULE_COMMAND              : defaults.IMAGE_VALIDATION_MODULE_COMMAND,
            CNP_RELEASE_XLR_AF_MODULE_USER               : defaults.CNP_RELEASE_XLR_AF_MODULE_USER,
            CNP_RELEASE_XLR_AF_MODULE_PASSWORD           : defaults.CNP_RELEASE_XLR_AF_MODULE_PASSWORD,
            CNP_RELEASE_XLR_GIT_MODULE_USER              : defaults.CNP_RELEASE_XLR_GIT_MODULE_USER,
            CNP_RELEASE_XLR_GIT_MODULE_PASSWORD          : defaults.CNP_RELEASE_XLR_GIT_MODULE_PASSWORD,
            CNP_DEFAULT_JAVA_IMAGE                       : defaults.CNP_DEFAULT_JAVA_IMAGE,
            CNP_DEFAULT_GRADLE_IMAGE                     : defaults.CNP_DEFAULT_GRADLE_IMAGE,
            CNP_CANDIDATE_JOB                            : defaults.CNP_CANDIDATE_JOB,
            CNP_REGISTRY_DEV_CRED                        : defaults.CNP_REGISTRY_DEV_CRED,
            CNP_REGISTRY_PROD_CRED                       : defaults.CNP_REGISTRY_PROD_CRED,
            CNP_OC_CRED_DEV                              : defaults.CNP_OC_CRED_DEV,
            CNP_OC_CRED_QA                               : defaults.CNP_OC_CRED_QA,
            CNP_OC_CRED_UAT                              : defaults.CNP_OC_CRED_UAT,
            CNP_OC_CRED_PROD                             : defaults.CNP_OC_CRED_PROD,
            CNP_OC_CRED_DR                               : defaults.CNP_OC_CRED_DR,
            CNP_CIGNA_GIT                                : defaults.CNP_CIGNA_GIT,
            CNP_ARGOCD_CRED_DEV                          : defaults.CNP_ARGOCD_CRED_DEV,
            CNP_ARGOCD_CRED_QA                           : defaults.CNP_ARGOCD_CRED_QA,
            CNP_ARGOCD_CRED_UAT                          : defaults.CNP_ARGOCD_CRED_UAT,
            CNP_ARGOCD_CRED_PROD                         : defaults.CNP_ARGOCD_CRED_PROD,
            CNP_ARGOCD_CRED_DR                           : defaults.CNP_ARGOCD_CRED_DR,
            CNP_DEPLOY_ARGOROLLOUTS_MODULE_USER          : defaults.CNP_DEPLOY_ARGOROLLOUTS_MODULE_USER,
            CNP_DEPLOY_ARGOROLLOUTS_MODULE_PASSWORD      : defaults.CNP_DEPLOY_ARGOROLLOUTS_MODULE_PASSWORD,
            CNP_DEPLOY_ARGOROLLOUTS_GIT_MODULE_USER      : defaults.CNP_DEPLOY_ARGOROLLOUTS_GIT_MODULE_USER,
            CNP_DEPLOY_ARGOROLLOUTS_GIT_MODULE_PASSWORD  : defaults.CNP_DEPLOY_ARGOROLLOUTS_GIT_MODULE_PASSWORD,
            AUTHORIZED_USERS                             : defaults.AUTHORIZED_USERS
    ]

    // Emulates Jenkins env.getEnvironment()
    Map getEnvironment() {
        return mockEnvironment
    }

    void changeValue(String propertyName, String value) {
        mockEnvironment[propertyName] = value
        this.setProperty(propertyName, value)
    }

    void addValue(String key, String value) {
        mockEnvironment.put(key, value)
    }
}
