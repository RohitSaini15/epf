package com.evernorth.cloudnativebuild.model

import com.evernorth.cloudnativebuild.config.ReleaseConstants
import com.evernorth.cloudnativebuild.data.PipelineConstants
import com.evernorth.cloudnativebuild.model.logging.LogLevel

class BaseDefaults {
    final static boolean simulateOnly = false
    final static boolean disableAllTests = false
    final static boolean disableAlerts = false
    final static String debLocal = "deb-local"
    final static String debVirtual = "deb-virtual"
    final static String debPublicMirror = "https://cigna.jfrog.io/artifactory/deb-virtual"
    final static String debPrivate = "https://cigna.jfrog.io/artifactory/deb-local"
    final static String imageRepo = "docker-dev.cigna.jfrog.io"
    final static String dockerRegistry = "registry.cigna.com"
    final static String dockerDevRegistry = "registry-dev.cigna.com"
    final static String defaultDockerImage = "cnp/cnp-docker-core:1.1.10"
    final static String defaultK8sImage = "cnp/cnp-docker-k8s:1.2.3"
    final static String defaultDockerBuilderImage = "cnp/cnp-docker-podman:1.1.9"
    final static String defaultGoImage = "cnp/cnp-docker-go:1.1.7"
    final static String defaultInitImage = "cnp/cnp-docker-init:latest"
    final static String defaultNpmImage = "cnp/cnp-docker-node10:1.2.1"
    final static String defaultTerraformImage = "cnp/cnp-docker-terraform:1.1.4"
    final static String defaultAwsImage = "cnp/cnp-docker-aws:1.1.2"
    final static String defaultPcfImage = "cnp/cnp-docker-pcf:1.3.3"
    final static String epfDefaultAnsibleImage = "cnp/epf-docker-ansible:0.1.10"
    final static String defaultJavaImage = "cnp/cnp-docker-maven-java8:1.2.10"
    final static String defaultGradleImage = "cnp/cnp-docker-gradle-java8:1.2.10"
    final static String defaultDojoMsgImage = "cnp/cnp-dojo-messenger:latest"
    final static String defaultAcsImage = "cpe/acs-cli:prod"
    final static String artifactoryRootUrl = "https://cigna.jfrog.io/artifactory"
    final static String artifactoryCredential = "cbc/artifactory"
    final static String npmCredentialsId = "npm-artifactory-id"
    static String gitCredential = null
    final static String jobBuildCred = "testcallid"
    final static String cnpCignaGit = PipelineConstants.CIGNA_GIT_CRED
    final static String cxEndpoint = "https://ch3qw1017757.accounts.root.corp"
    final static String cxServerAddress = "https://10.222.239.180/"
    final static String sonarCredentialId = "sonar"
    final static String sonarHost = 'https://sonarqube.sys.cigna.com/'
    final static String cxCredential = "d2d7833b-38fc-405a-b1b5-4f573ac788a6"
    static String gitToken = null
    final static LogLevel logLevel = LogLevel.INFO
    final static boolean ignoreConftestAnalysisFailure = false
    final static String xlrTemplateUrl = "https://xlrelease.express-scripts.com/api/v1/templates/Applications/templates/Folder2a9c42e13b784e8fbb57c559852b807c/Release47c3527db6944b918488c5c90166c4b0/start"
    final static String callbackJob = ""
    final static String callbackJobBase = "job/DevOps/job/ProductionDeployment/job/CNP/job/XLR%20Callback%20Handler"
    final static String candidateJob = "DevOps/job/ProductionDeployment/job/CNP/job/CandidateDeployer"
    final static String authorizedUsers = 'internal\\svp_pipeline_admin'
    final static boolean overrideCommon = false
    final static String deployableBranches = PipelineConstants.DEFAULT_DEPLOYABLE_BRANCHES
    final static String releaseInfoFileName = PipelineConstants.RELEASE_METADATA_FILE
    final static String dojoMsgEndpoint = ""
    final static String podIdleMinutes = "1"
    final static String podNodeSelector = "beta.kubernetes.io/os: \"linux\""
    final static String podWorkingDir = "/home/jenkins/agent"
    final static String podCommand = "[\"/bin/sh\",\"-c\"]"
    final static String podCommandArgs = "cat"
    final static String podServiceAccount = ''
    final static String podCloud = 'kubernetes'
    final static String podJnlpImage = 'enterprise-devops/conduit-jnlp:cloudbees-2.462.1.3-v1'
    final static boolean enableExternalConfiguration = false
    final static boolean enableResourceRequests = true
    final static boolean disableJirascan = true
    final static String imageValidationModuleName = "cnp-validate-images"
    final static String imageValidationModuleCommand = "cnp-validate-images.sh"
    final static boolean jenkinsDollarHackEnabled = true // used in CNP Core Modules
    final static boolean enableEvents = true
    final static String asnValidateEnabled = false
    final static String awsIdpProvider = "Okta"
    final static String releaseStashPattern = ReleaseConstants.RELEASE_DEFAULT_STASH_PATTERN
    final static String defaultServiceAccount = 'kaniko'
}
