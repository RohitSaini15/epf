package com.evernorth.cloudnativebuild.model

import com.cloudbees.groovy.cps.NonCPS
import com.evernorth.cloudnativebuild.data.PipelineConstants
import com.evernorth.cloudnativebuild.model.logging.LogLevel

import static com.evernorth.cloudnativebuild.model.logging.LogLevel.*


/**
 * This class contains Pipeline configuration settings and their default values.
 * All values can be overwritten using Environment variables set as job, folder, or master configuration
 */
class BuildConfiguration implements Serializable {
    // when these property names are converted to env vars, do not prefix them with 'CNP_'
    static List<String> nonPrefixedProperties = ['gitCredential', 'artifactoryCredential', 'npmCredentialsId', 'artifactoryRootUrl', 'cnpCignaGit', 'jobBuildCred', 'cxEndpoint', 'cxServerAddress', 'sonarCredentialId', 'sonarHost', 'cxCredential', 'gitToken', 'authorizedUsers', 'imageValidationEnabled', 'imageValidationModuleName', 'imageValidationModuleCommand', 'epfDefaultAnsibleImage']
    boolean simulateOnly = BaseDefaults.simulateOnly
    boolean disableAllTests = BaseDefaults.disableAllTests
    boolean disableAlerts = BaseDefaults.disableAlerts
    String debLocal = BaseDefaults.debLocal
    String debVirtual = BaseDefaults.debVirtual
    String debPublicMirror = BaseDefaults.debPublicMirror
    String debPrivate = BaseDefaults.debPrivate
    String imageRepo = BaseDefaults.imageRepo
    String dockerRegistry = BaseDefaults.dockerRegistry
    String dockerDevRegistry = BaseDefaults.dockerDevRegistry
    String defaultDockerImage = BaseDefaults.defaultDockerImage
    String defaultK8sImage = BaseDefaults.defaultK8sImage
    String defaultDockerBuilderImage = BaseDefaults.defaultDockerBuilderImage
    String defaultGoImage = BaseDefaults.defaultGoImage
    String defaultInitImage = BaseDefaults.defaultInitImage
    String defaultNpmImage = BaseDefaults.defaultNpmImage
    String defaultTerraformImage = BaseDefaults.defaultTerraformImage
    String defaultAwsImage = BaseDefaults.defaultAwsImage
    String defaultPcfImage = BaseDefaults.defaultPcfImage
    String epfDefaultAnsibleImage = BaseDefaults.epfDefaultAnsibleImage
    String defaultJavaImage = BaseDefaults.defaultJavaImage
    String defaultGradleImage = BaseDefaults.defaultGradleImage
    String defaultDojoMsgImage = BaseDefaults.defaultDojoMsgImage
    String defaultAcsImage = BaseDefaults.defaultAcsImage
    String artifactoryRootUrl = BaseDefaults.artifactoryRootUrl
    String artifactoryCredential = BaseDefaults.artifactoryCredential
    String npmCredentialsId = BaseDefaults.npmCredentialsId
    String gitCredential = BaseDefaults.gitCredential
    String jobBuildCred = BaseDefaults.jobBuildCred
    String cnpCignaGit = BaseDefaults.cnpCignaGit
    String cxEndpoint = BaseDefaults.cxEndpoint
    String cxServerAddress = BaseDefaults.cxServerAddress
    String sonarCredentialId = BaseDefaults.sonarCredentialId
    String sonarHost = BaseDefaults.sonarHost
    String cxCredential = BaseDefaults.cxCredential
    String gitToken = BaseDefaults.gitToken
    LogLevel logLevel = INFO
    boolean ignoreConftestAnalysisFailure = BaseDefaults.ignoreConftestAnalysisFailure
    String xlrTemplateUrl = BaseDefaults.xlrTemplateUrl
    String callbackJob = BaseDefaults.callbackJob
    String callbackJobBase = BaseDefaults.callbackJobBase
    String candidateJob = BaseDefaults.candidateJob
    String authorizedUsers = BaseDefaults.authorizedUsers
    boolean overrideCommon = BaseDefaults.overrideCommon
    String deployableBranches = BaseDefaults.deployableBranches
    String releaseInfoFileName = BaseDefaults.releaseInfoFileName
    String dojoMsgEndpoint = BaseDefaults.dojoMsgEndpoint

    String podIdleMinutes = BaseDefaults.podIdleMinutes
    String podNodeSelector = BaseDefaults.podNodeSelector
    String podWorkingDir = BaseDefaults.podWorkingDir
    String podCommand = BaseDefaults.podCommand
    String podCommandArgs = BaseDefaults.podCommandArgs
    String podServiceAccount = BaseDefaults.podServiceAccount
    String podCloud = BaseDefaults.podCloud
    String podJnlpImage = BaseDefaults.podJnlpImage
    boolean enableExternalConfiguration = BaseDefaults.enableExternalConfiguration
    boolean enableResourceRequests = BaseDefaults.enableResourceRequests
    boolean disableJirascan = BaseDefaults.disableJirascan

    /** Flag which triggers image validation during pipeline initialization. */
    boolean imageValidationEnabled = true

    /** The default module name which validates images. */
    String imageValidationModuleName = BaseDefaults.imageValidationModuleName

    /** The default module command used to validate images. */
    String imageValidationModuleCommand = BaseDefaults.imageValidationModuleCommand
    boolean jenkinsDollarHackEnabled = BaseDefaults.jenkinsDollarHackEnabled

    // when disabled the pre-execute and post-execute events are ignored
    boolean enableEvents = BaseDefaults.enableEvents
    String asnValidateEnabled = BaseDefaults.asnValidateEnabled
    // which auth provider to use for AWS
    String awsIdpProvider = BaseDefaults.awsIdpProvider

    /**
     * This is stash patten that will be used to create a stash after checkout that
     * can be used by most deployment and release modules
     */
    String releaseStashPattern = BaseDefaults.releaseStashPattern

    @NonCPS
    static String gitCredentialFromRepo(String repoUrl) {
        if (!repoUrl) {
            return PipelineConstants.CIGNA_GIT_CRED
        }
        return ['github.sys.cigna.com'   : PipelineConstants.CIGNA_GIT_CRED,
                'git.express-scripts.com': PipelineConstants.HS_GIT_CRED,
                'github.com'             : PipelineConstants.CLOUD_GIT_CRED].find { url, cred ->
            repoUrl.contains(url)
        }?.value ?: PipelineConstants.CIGNA_GIT_CRED
    }

    @NonCPS
    static String gitTokenFromRepo(String repoUrl) {
        if (!repoUrl) {
            return PipelineConstants.CIGNA_GIT_TOKEN
        }
        return ['github.sys.cigna.com'   : PipelineConstants.CIGNA_GIT_TOKEN,
                'git.express-scripts.com': PipelineConstants.HS_GIT_TOKEN,
                'github.com'             : PipelineConstants.CLOUD_GIT_TOKEN].find { url, cred ->
            repoUrl.contains(url)
        }?.value ?: PipelineConstants.CIGNA_GIT_TOKEN
    }

    @NonCPS
    Map<String, String> environment(def script) {

        /*
        Default git cred value is null; if it's set, then it likely came from the environment or loading of a release
        state file (if we're in a callback job). In either of those cases, we respect that value, otherwise we compute
        the value based on GIT_URL
         */
        this.gitCredential = this.gitCredential ?: gitCredentialFromRepo(script.scm.userRemoteConfigs[0]?.url)
        this.gitToken = this.gitToken ?: gitTokenFromRepo(script.scm.userRemoteConfigs[0]?.url)
        [
            CNP_DEFAULT_DOCKER_IMAGE        : script.env.CNP_DEFAULT_DOCKER_IMAGE ?: "${defaultDockerImage}",
            CNP_DEFAULT_K8S_IMAGE           : script.env.CNP_DEFAULT_K8S_IMAGE ?: "${defaultK8sImage}",
            CNP_DEFAULT_DOCKER_BUILDER_IMAGE: script.env.CNP_DEFAULT_DOCKER_BUILDER_IMAGE ?: "${defaultDockerBuilderImage}",
            CNP_DEFAULT_GO_IMAGE            : script.env.CNP_DEFAULT_GO_IMAGE ?: "${defaultGoImage}",
            CNP_DEFAULT_INIT_IMAGE          : script.env.CNP_DEFAULT_INIT_IMAGE ?: "${defaultInitImage}",
            CNP_DEFAULT_NPM_IMAGE           : script.env.CNP_DEFAULT_NPM_IMAGE ?: "${defaultNpmImage}",
            CNP_DEFAULT_TERRAFORM_IMAGE     : script.env.CNP_DEFAULT_TERRAFORM_IMAGE ?: "${defaultTerraformImage}",
            CNP_DEFAULT_AWS_IMAGE           : script.env.CNP_DEFAULT_AWS_IMAGE ?: "${defaultAwsImage}",
            CNP_DEFAULT_PCF_IMAGE           : script.env.CNP_DEFAULT_PCF_IMAGE ?: "${defaultPcfImage}",
            EPF_DEFAULT_ANSIBLE_IMAGE       : script.env.EPF_DEFAULT_ANSIBLE_IMAGE ?: "${epfDefaultAnsibleImage}",
            CNP_DEFAULT_JAVA_IMAGE          : script.env.CNP_DEFAULT_JAVA_IMAGE ?: "${defaultJavaImage}",
            CNP_DEFAULT_GRADLE_IMAGE        : script.env.CNP_DEFAULT_GRADLE_IMAGE ?: "${defaultGradleImage}",
            CNP_DEFAULT_DOJO_MSG_IMAGE      : script.env.CNP_DEFAULT_DOJO_MSG_IMAGE ?: "${defaultDojoMsgImage}",
            CNP_DEFAULT_ACS_IMAGE           : script.env.CNP_DEFAULT_ACS_IMAGE ?: "${defaultAcsImage}",
            GIT_CREDENTIAL                  : script.env.GIT_CREDENTIAL ?: "${gitCredential}",
            ARTIFACTORY_CREDENTIAL          : script.env.ARTIFACTORY_CREDENTIAL ?: "${artifactoryCredential}",
            NPM_CREDENTIALS_ID              : script.env.NPM_CREDENTIALS_ID ?: "${npmCredentialsId}",
            ARTIFACTORY_ROOT_URL            : script.env.ARTIFACTORY_ROOT_URL ?: "${artifactoryRootUrl}",
            IMAGE_VALIDATION_MODULE_COMMAND : script.env.IMAGE_VALIDATION_MODULE_COMMAND ?: "${imageValidationModuleCommand}",
            CNP_CIGNA_GIT                   : script.env.CNP_CIGNA_GIT ?: "${cnpCignaGit}",
            JOB_BUILD_CRED                  : script.env.JOB_BUILD_CRED ?: "${jobBuildCred}",
            CX_ENDPOINT                     : script.env.CX_ENDPOINT ?: "${cxEndpoint}",
            CX_SERVER_ADDRESS               : script.env.CX_SERVER_ADDRESS ?: "${cxServerAddress}",
            SONAR_CREDENTIAL_ID             : script.env.SONAR_CREDENTIAL_ID ?: "${sonarCredentialId}",
            SONAR_HOST                      : script.env.SONAR_HOST ?: "${sonarHost}",
            CX_CREDENTIAL                   : script.env.CX_CREDENTIAL ?: "${cxCredential}",
            GIT_TOKEN                       : script.env.GIT_TOKEN ?: "${gitToken}",
            CNP_ASN_VALIDATE_ENABLED        : script.env.CNP_ASN_VALIDATE_ENABLED ?: "${asnValidateEnabled}",
            CNP_XLR_TEMPLATE_URL            : script.env.CNP_XLR_TEMPLATE_URL ?: "${xlrTemplateUrl}",
            CNP_DISABLE_JIRASCAN            : script.env.CNP_DISABLE_JIRASCAN ?: "${disableJirascan}",
            CNP_CALLBACK_JOB                : script.env.CNP_CALLBACK_JOB ?: "${callbackJob}",
            CNP_CANDIDATE_JOB               : script.env.CNP_CANDIDATE_JOB ?: "${candidateJob}",
            AUTHORIZED_USERS                : script.env.AUTHORIZED_USERS ?: "${authorizedUsers}"
        ]
    }

    @Override
    @NonCPS
    String toString() {
        return "BuildConfiguration{" +
                "simulateOnly=" + simulateOnly +
                ", disableAllTests=" + disableAllTests +
                ", disableAlerts=" + disableAlerts +
                ", debLocal='" + debLocal + '\'' +
                ", debVirtual='" + debVirtual + '\'' +
                ", debPublicMirror='" + debPublicMirror + '\'' +
                ", debPrivate='" + debPrivate + '\'' +
                ", imageRepo='" + imageRepo + '\'' +
                ", dockerRegistry='" + dockerRegistry + '\'' +
                ", dockerDevRegistry='" + dockerDevRegistry + '\'' +
                ", defaultDockerImage='" + defaultDockerImage + '\'' +
                ", defaultK8sImage='" + defaultK8sImage + '\'' +
                ", defaultDockerBuilderImage='" + defaultDockerBuilderImage + '\'' +
                ", defaultGoImage='" + defaultGoImage + '\'' +
                ", defaultInitImage='" + defaultInitImage + '\'' +
                ", defaultNpmImage='" + defaultNpmImage + '\'' +
                ", defaultTerraformImage='" + defaultTerraformImage + '\'' +
                ", defaultAwsImage='" + defaultAwsImage + '\'' +
                ", defaultPcfImage='" + defaultPcfImage + '\'' +
                ", epfDefaultAnsibleImage='" + epfDefaultAnsibleImage + '\'' +
                ", defaultJavaImage='" + defaultJavaImage + '\'' +
                ", defaultDojoMsgImage='" + defaultDojoMsgImage + '\'' +
                ", defaultAcsImage='" + defaultAcsImage + '\'' +
                ", artifactoryRootUrl='" + artifactoryRootUrl + '\'' +
                ", gitCredential='" + gitCredential + '\'' +
                ", jobBuildCred='" + jobBuildCred + '\'' +
                ", cnpCignaGit='" + cnpCignaGit + '\'' +
                ", cxEndpoint='" + cxEndpoint + '\'' +
                ", cxServerAddress='" + cxServerAddress + '\'' +
                ", sonarCredentialId='" + sonarCredentialId + '\'' +
                ", cxCredential='" + cxCredential + '\'' +
                ", logLevel=" + logLevel +
                ", ignoreConftestAnalysisFailure=" + ignoreConftestAnalysisFailure +
                ", xlrTemplateUrl='" + xlrTemplateUrl + '\'' +
                ", callbackJob='" + callbackJob + '\'' +
                ", authorizedUsers=" + authorizedUsers + '\'' +
                ", callbackJobBase='" + callbackJobBase + '\'' +
                ", candidateJob='" + candidateJob + '\'' +
                ", overrideCommon=" + overrideCommon +
                ", deployableBranches='" + deployableBranches + '\'' +
                ", releaseInfoFileName='" + releaseInfoFileName + '\'' +
                ", dojoMsgEndpoint='" + dojoMsgEndpoint + '\'' +
                ", podIdleMinutes='" + podIdleMinutes + '\'' +
                ", podNodeSelector='" + podNodeSelector + '\'' +
                ", podWorkingDir='" + podWorkingDir + '\'' +
                ", podCommand='" + podCommand + '\'' +
                ", podCommandArgs='" + podCommandArgs + '\'' +
                ", podServiceAccount='" + podServiceAccount + '\'' +
                ", podCloud='" + podCloud + '\'' +
                ", enableExternalConfiguration=" + enableExternalConfiguration +
                ", imageValidationEnabled=" + imageValidationEnabled +
                ", imageValidationModuleName='" + imageValidationModuleName + '\'' +
                ", imageValidationModuleCommand='" + imageValidationModuleCommand + '\'' +
                ", jenkinsDollarHackEnabled=" + jenkinsDollarHackEnabled + '\'' +
                ", enableEvents=" + enableEvents + '\'' +
                ", disableJirascan=" + disableJirascan +
                '}'
    }
    
    // this allows extra properties to be present when casting to BuildConfiguration
    // as part of loading a release state file
    @NonCPS
    def propertyMissing(String _name, _value) {
        //do nothing
    }
}
