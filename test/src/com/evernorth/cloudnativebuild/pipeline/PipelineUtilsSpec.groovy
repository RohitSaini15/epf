package com.evernorth.cloudnativebuild.pipeline

import com.cigna.common.exception.ErrorStepException
import com.cigna.state.PipelineStateContext
import com.cigna.common.utils.Utils
import com.evernorth.cloudnativebuild.config.ReleaseConstants
import com.evernorth.cloudnativebuild.data.PipelineConstants
import com.evernorth.cloudnativebuild.mocks.MockJenkins
import com.evernorth.cloudnativebuild.model.ModuleContract
import com.evernorth.cloudnativebuild.model.ModuleContractType
import com.evernorth.cloudnativebuild.model.StepResult
import com.evernorth.cloudnativebuild.service.Logger
import com.evernorth.cloudnativebuild.service.PipelineStateManager
import spock.lang.Specification

import java.util.regex.Pattern

class PipelineUtilsSpec extends Specification {


    def "createStageNameFromModuleContractType replaces underscore with space"() {
        when:
        String stepName = PipelineUtils.createStageNameFromModuleContractType(ModuleContractType.PREFLIGHT_CHECK)
        then:
        stepName == "Preflight Check"
    }

    def "createStageNameFromModuleContractType capitalizes first word"() {
        when:
        String stepName = PipelineUtils.createStageNameFromModuleContractType(ModuleContractType.BUILD)
        then:
        stepName == "Build"
    }

    def "getStageNameFromModuleContract if stageName not set and env set"() {
        setup:
        PipelineStateManager manager = new PipelineStateManager()
        def modules = [[contractName: "DEPLOY", commandName: "cnp-deploy-static-aws.sh", image: "cnp/cnp-docker-aws:devel"]]
        def options = [env: 'DEV', filter: 'cnp-deploy-static-aws']
        manager.addList(modules)
        when:
        String stepName = PipelineUtils.getStageNameFromModuleContract(manager, ModuleContractType.DEPLOY, "cnp-deploy-static-aws", options)
        then:
        stepName == "Deploy DEV"
    }

    def "getStageNameFromModuleContract if stageName set and env not set"() {
        setup:
        PipelineStateManager manager = new PipelineStateManager()
        def modules = [[contractName: "DEPLOY", commandName: "cnp-deploy-static-aws.sh", image: "cnp/cnp-docker-aws:devel", stageName: "AWS"]]

        def options = [filter: 'cnp-deploy-static-aws']
        manager.addList(modules)
        when:
        String stepName = PipelineUtils.getStageNameFromModuleContract(manager, ModuleContractType.DEPLOY, "cnp-deploy-static-aws", options)
        then:
        stepName == "Deploy AWS"
    }

    def "getStageNameFromModuleContract if stageName set and env set"() {
        setup:
        PipelineStateManager manager = new PipelineStateManager()
        def modules = [[contractName: "DEPLOY", commandName: "cnp-deploy-static-aws.sh", image: "cnp/cnp-docker-aws:devel", stageName: "AWS"]]
        def options = [env: 'DEV', filter: 'cnp-deploy-static-aws']
        manager.addList(modules)
        when:
        String stepName = PipelineUtils.getStageNameFromModuleContract(manager, ModuleContractType.DEPLOY, "cnp-deploy-static-aws", options)
        then:
        stepName == "Deploy AWS DEV"
    }

    def "getStageNameFromModuleContract if stageName not set and env not set"() {
        setup:
        PipelineStateManager manager = new PipelineStateManager()
        def modules = [[contractName: "DEPLOY", commandName: "cnp-deploy-static-aws.sh", image: "cnp/cnp-docker-aws:devel"]]
        def options = [:]
        manager.addList(modules)
        when:
        String stepName = PipelineUtils.getStageNameFromModuleContract(manager, ModuleContractType.DEPLOY, "cnp-deploy-static-aws", options)
        then:
        stepName == "Deploy"
    }

    def "test getUserId from XLR"() {
        setup:
        MockJenkins jenkins = new MockJenkins()
        when:
        String userid = PipelineUtils.getUserId(jenkins)
        then:
        userid == 'userid'
    }

    def "test getUpstreamDescription from XLR"() {
        setup:
        MockJenkins jenkins = new MockJenkins()
        when:
        String description = PipelineUtils.getUpstreamDescription(jenkins)
        then:
        description == 'upstream description'
    }

    def "stopPipelineIfNotDeployable stops pipeline"() {
        setup:
        MockJenkins jenkins = new MockJenkins()
        StepResult result = StepResult.empty()
        when:
        PipelineUtils.stopPipelineIfNotDeployable(jenkins, result, StepResult.COULD_NOT_DEPLOY)
        then:
        thrown ErrorStepException
    }

    def "stopPipelineIfNotDeployable does not stop pipeline"() {
        setup:
        MockJenkins jenkins = new MockJenkins()
        StepResult result = StepResult.empty()
        when:
        PipelineUtils.stopPipelineIfNotDeployable(jenkins, result, "")
        then:
        jenkins.currentBuild.result != PipelineConstants.BUILD_RESULT_NOT_BUILT
    }

    def "abortPipeline"() {
        setup:
        MockJenkins jenkins = new MockJenkins()
        when:
        PipelineUtils.abortPipeline(jenkins, "")
        then:
        thrown ErrorStepException
    }

    def "getMasterName without Jenkins url"() {
        setup:
        MockJenkins jenkins = new MockJenkins()
        jenkins.env.JENKINS_URL = null
        when:
        def masterName = PipelineUtils.getMasterName(jenkins)
        then:
        masterName == null
    }

    // TESTS FOR getDeploymentSourceRepoBase
    def "When getDeploymentSourceRepoBase jenkins job is in sub-folder repoName excludes Jenkins child folder"() {
        setup:
        MockJenkins jenkins = new MockJenkins()
        Logger logger = new Logger(jenkins)
        jenkins.env.JOB_NAME = name
        when:
        String actual = PipelineUtils.getDeploymentSourceRepoBase(jenkins, logger, "https://git.express-scripts.com/expressScripts/repoName.git")
        then:
        expectedName ==  actual
        where:
        name << [
            'orchestrators-folders/hs-pipeline/Functional Test Apps/devops-testapps-nodeserver/release%2Fconducive',
            'orchestrators-folders/epf/epf-test-apps/Enterprise%20Pipeline%20Framework%20-%20Sample%20GoLang%20Test%20Application/feature%252Fmultilibrary',
            'orchestrators-folders/adjudicator/Adjudicator/main',
            'orchestrators-folders/ba13975/hc360-populations-api/Non-Production/hc360-populations-api/release%2Fv1.7.0',
            'orchestrators-folders/ba13833/specialty-rxp-pega/Master/release_RXP_2024_JK_11R2_TEST',
            'orchestrators-folders/BA13890/hc360-populations/Non-Production/hc360-populations-api/release%2Fv1.7.0',
            'pilot-folders/epf-vpa-testing/epf-sampleapp-jdk/feature%252FVPA',
            'pilot-folders/BA12733/adjudicator/adjudicator/main',
            'conduit/conduit-pipeline/Enterprise Pipeline Converter/fix-ansible-deployflag',
            'conduit/conduit-pipeline/master'
        ]
        expectedName << [
        'ci-snapshot-local/releases/hs-pipeline/Functional Test Apps/devops-testapps-nodeserver/release%2Fconducive/repoName', 
        'ci-snapshot-local/releases/epf/epf-test-apps/Enterprise%20Pipeline%20Framework%20-%20Sample%20GoLang%20Test%20Application/feature%252Fmultilibrary/repoName',
        'ci-snapshot-local/releases/adjudicator/Adjudicator/main/repoName', 
        'ci-snapshot-local/releases/ba13975/hc360-populations-api/hc360-populations-api/release%2Fv1.7.0/repoName',
        'ci-snapshot-local/releases/ba13833/specialty-rxp-pega/Master/release_RXP_2024_JK_11R2_TEST/repoName',
        'ci-snapshot-local/releases/BA13890/hc360-populations/hc360-populations-api/release%2Fv1.7.0/repoName',
        'ci-snapshot-local/releases/epf-vpa-testing/epf-sampleapp-jdk/feature%252FVPA/repoName',
        'ci-snapshot-local/releases/BA12733/adjudicator/adjudicator/main/repoName',
        'ci-snapshot-local/releases/conduit/conduit-pipeline/Enterprise Pipeline Converter/fix-ansible-deployflag/repoName',
        'ci-snapshot-local/releases/conduit/conduit-pipeline/master/repoName']  
    }

    def "When getDeploymentSourceRepoBase on any branch use release repo to get rollback package path"() {
        setup:
        MockJenkins jenkins = new MockJenkins()
        Logger logger = new Logger(jenkins)
        jenkins.env.JOB_NAME = "orchestrators-folders/adjudicator/Adjudicator/main"
        String expected = "ci-release-local/releases/adjudicator/Adjudicator/main/repoName"
        when:
        String actual = PipelineUtils.getDeploymentSourceRepoBase(jenkins, logger, "https://git.express-scripts.com/expressScripts/repoName.git", true)
        then:
        expected == actual
    }

    def "When retrieveDeploymentPackage with TRACE Logging prints ls"() {

        given:
        MockJenkins jenkins = new MockJenkins()
        def psc = new PipelineStateContext(jenkins)
        jenkins.env.CNP_LOG_LEVEL = "TRACE"
        jenkins.env.mockEnvironment.CNP_LOG_LEVEL = "TRACE"
        jenkins.addReadFileResult("cnp-release-util-results.json", "cnp-success-results.json")
        when:
        StepResult result = PipelineUtils.retrieveDeploymentPackage(psc, jenkins, psc.globalModuleManager.stateManager(), "somepath", [:])
        then:
        result.commandResult == StepResult.SUCCESS
    }

    def "when getField return data from json string"() {
        setup:
        String deploymentInfo = "{\"deploymentPackagePath\":\"https://artifactory.express-scripts.com/artifactory/ci-snapshot-local/Cloud%20Native%20Pipeline/greetings-ui/deploymentPackage_greetings-ui-1.0.0-beta.30.zip\"," +
                "\"repoUrl\":\"https://git.express-scripts.com/ExpressScripts/user-profile-ui.git\"," +
                "\"branchName\":\"somebranch\"}"
        when:
        String actualDeploymentPackagePath = PipelineUtils.getField(deploymentInfo, "deploymentPackagePath")
        String actualRepoUrl = PipelineUtils.getField(deploymentInfo, "repoUrl")
        String actualBranchName = PipelineUtils.getField(deploymentInfo, "branchName")
        then:
        actualDeploymentPackagePath == "https://artifactory.express-scripts.com/artifactory/ci-snapshot-local/Cloud%20Native%20Pipeline/greetings-ui/deploymentPackage_greetings-ui-1.0.0-beta.30.zip"
        actualBranchName == "somebranch"
        actualRepoUrl == "https://git.express-scripts.com/ExpressScripts/user-profile-ui.git"
    }

    def "when getField on malformed json string return null"() {
        setup:
        String malformedJsonString = "hi am am not json"
        when:
        String thatShouldBeNull = PipelineUtils.getField(malformedJsonString, "fieldName")
        then:
        thatShouldBeNull == null
    }

    def "when addGlobalFilter and no state manager filter options not changed"() {
        setup:
        PipelineStateManager manager = new PipelineStateManager()
        Map options = [:]
        when:
        PipelineUtils.addGlobalFilter(options, manager)
        then:
        options == [:]
    }

    def "when addGlobalFilter and and no state manager options contains filter options not changed"() {
        setup:
        PipelineStateManager manager = new PipelineStateManager()
        Map options = [filter: "cnp-deploy-pcf"]
        when:
        PipelineUtils.addGlobalFilter(options, manager)
        then:
        options == [filter: "cnp-deploy-pcf"]
    }

    def "when addGlobalFilter and options contains filter throw exception"() {
        setup:
        PipelineStateManager manager = new PipelineStateManager()
        manager.add(new ModuleContract(commandName: "cnp-deploy-pcf", contractName: "DEPLOY", image: "cnp/cnp-docker-pcf"))
        manager.enableGlobalModuleFilter("cnp-deploy-pcf")
        Map options = [filter: "cnp-deploy-pcf"]
        when:
        PipelineUtils.addGlobalFilter(options, manager)
        then:
        thrown IllegalArgumentException
    }

    def "when addGlobalFilter and state manager filter add filter to options"() {
        setup:
        PipelineStateManager manager = new PipelineStateManager()
        manager.add(new ModuleContract(commandName: "cnp-deploy-pcf", contractName: "DEPLOY", image: "cnp/cnp-docker-pcf"))
        manager.enableGlobalModuleFilter("cnp-deploy-pcf")
        Map options = [:]
        Map expected = [filter: "cnp-deploy-pcf"]
        when:
        PipelineUtils.addGlobalFilter(options, manager)
        then:
        options == expected
    }

    def "when stash stash is executed"() {
        setup:
        MockJenkins jenkins = new MockJenkins()
        when:
        PipelineUtils.stash(jenkins, "foo", "foo.txt")
        then:
        jenkins.stashCount == 1
    }

    def "pipelineNameAndBranch returns pipeline and branch when on feature"() {
        setup:
        MockJenkins jenkins = new MockJenkins()
        jenkins.env.mockEnvironment.put("library.epf.version", "feature/myfeature")
        when:
        def pipelineName = PipelineUtils.pipelineNameAndBranch(jenkins)
        then:
        pipelineName == "epf@feature/myfeature"
    }

    def "pipelineNameAndBranch returns override when set"() {
        setup:
        MockJenkins jenkins = new MockJenkins()
        String overrideLibraryName = "OverrideLibraryName"
        jenkins.env.mockEnvironment.put("CNP_LIBRARY_NAME_OVERRIDE", overrideLibraryName)
        when:
        def pipelineName = PipelineUtils.pipelineNameAndBranch(jenkins)
        then:
        pipelineName == overrideLibraryName
    }

    def "pipelineNameAndBranch returns default if there's no version in the environment"() {
        setup:
        MockJenkins jenkins = new MockJenkins()
        when:
        def pipelineName = PipelineUtils.pipelineNameAndBranch(jenkins)
        then:
        pipelineName == 'epf'
    }
    
}