package com.evernorth.cloudnativebuild.pipeline.steps


import com.cigna.state.PipelineStateContext
import com.evernorth.cloudnativebuild.config.ReleaseConstants
import com.evernorth.cloudnativebuild.data.PipelineConstants
import com.evernorth.cloudnativebuild.mocks.MockJenkins
import com.evernorth.cloudnativebuild.model.ModuleContract
import com.evernorth.cloudnativebuild.model.ModuleContractType
import com.evernorth.cloudnativebuild.model.ReleaseInfo
import com.evernorth.cloudnativebuild.model.StepResult
import com.evernorth.cloudnativebuild.pipeline.PipelineUtils
import com.evernorth.cloudnativebuild.service.PipelineStateManager
import spock.lang.Specification

class StoreReleaseMetadataStepSpec extends Specification {
    private static final String jarArtifact = "https://artifactory.express-scripts.com/artifactory/libs-snapshot-local/mycndirectory/com/esrx/devops-testapps-SpringPcf/1.3.6-SNAPSHOT/devops-testapps-SpringPcf-1.3.6.jar"

    PipelineStateContext psc
    MockJenkins script

    def setup() {
        script = new MockJenkins()
        psc = new PipelineStateContext(script)
    }
    // TESTS FOR createAndStoreReleaseInfoFile
    def "When no pipeline state manager module configured default is used"() {
        given:
        Map releaseArguments = [:]
        releaseArguments.put(ReleaseConstants.ARTIFACT_PROPERTY_NAME, jarArtifact)
        when:
        StoreReleaseMetadataStep.createAndStoreReleaseInfoFile(script, [:], psc, releaseArguments)
        then:
        psc.globalModuleManager.stateManager().getContracts().size() == 1
    }


    def "When artifact name not found step fails"() {
        given:
        def modules = new LoadPipelineModulesStep(psc, script).loadModuleContracts(["release", "k8s"])
        psc.globalModuleManager.stateManager().addList(modules)
        def releaseArguments = [:]
        releaseArguments.put(ReleaseConstants.ARTIFACT_PROPERTY_NAME, "")
        when:
        StoreReleaseMetadataStep.createAndStoreReleaseInfoFile(script, [:], psc, releaseArguments)
        then:
        script.currentBuild.result == PipelineConstants.BUILD_RESULT_FAILURE
    }

    def "When artifact passed as an argument it is used for artifact path"() {
        given:
        script.addReadFileResult("cnp-release-util-results.json")
        PipelineStateManager manager = new PipelineStateManager()
        def modules = new LoadPipelineModulesStep(psc, script).loadModuleContracts(["release", "k8s"])
        manager.addList(modules)
        def releaseArgs = [:]
        releaseArgs.put(ReleaseConstants.ARTIFACT_PROPERTY_NAME, jarArtifact)
        when:
        String publishUrl = StoreReleaseMetadataStep.createAndStoreReleaseInfoFile(script, [:], psc, releaseArgs)
        then:
        publishUrl != null
        script.currentBuild.result == PipelineConstants.BUILD_RESULT_SUCCESS
    }


    def "When more then one build step and no artifact argument fail build"() {
        given:
        def modules = new LoadPipelineModulesStep(psc, script).loadModuleContracts(["release", "npm"])
        psc.globalModuleManager.stateManager().addList(modules)
        psc.globalModuleManager.stateManager().add(new ModuleContract(contractName: String.valueOf(ModuleContractType.BUILD), commandName: "build.sh"))
        def releaseArgs = [:]
        when:
        def step = new StoreReleaseMetadataStep(psc, script)
        StepResult stepResult = step.execute(releaseArgs)
        then:
        stepResult?.commandResult == StepResult.FAILURE
    }


    def "when publish artifactory fails step fails"() {
        given:
        script.env.JOB_NAME = "myteam/myjobname"
        def modules = new LoadPipelineModulesStep(psc, script).loadModuleContracts(["npm"])
        def badPublisher = new ModuleContract(moduleName: "badfile", commandName: "badfile", contractName: ModuleContractType.PIPELINE_STATE_WRITER.name(), image: "cnp/cnp-docker-bad:latest")
        psc.globalModuleManager.stateManager().addList(modules)
        psc.globalModuleManager.stateManager().add(badPublisher)
        when:
        def publishResult = StoreReleaseMetadataStep.createAndStoreReleaseInfoFile(script, [:], psc, [:])
        then:
        publishResult == ""
        script.currentBuild.result == PipelineConstants.BUILD_RESULT_FAILURE
    }


    def "when createAndStoreReleaseInfoFile cannot determine release artifact build fails"() {
        given:
        when:
        def result = StoreReleaseMetadataStep.createAndStoreReleaseInfoFile(script, [:], psc)
        then:
        result == ""
        script.currentBuild.result == PipelineConstants.BUILD_RESULT_FAILURE
    }

    // TESTS FOR ensureReleaseArgumentsContainsArtifact
    def "when ensureReleaseArgumentsContainsArtifact gets null releaseArguments return false"() {
        given:
        boolean expected = false
        when:
        boolean actual = new StoreReleaseMetadataStep(psc, script).ensureReleaseArgumentsContainsArtifact(null)
        then:
        expected == actual
    }

    def "when ensureReleaseArgumentsContainsArtifact gets empty releaseArguments return false"() {
        given:
        boolean expected = false
        when:
        boolean actual = new StoreReleaseMetadataStep(psc, script).ensureReleaseArgumentsContainsArtifact([:])
        then:
        expected == actual
    }

    def "when ensureReleaseArgumentsContainsArtifact finds artifact in release arguments return true"() {
        given:
        boolean expected = true
        Map releaseArguments = [:]
        String artifact = "artifact name"
        releaseArguments.put(ReleaseConstants.ARTIFACT_PROPERTY_NAME, artifact)
        when:
        boolean actual = new StoreReleaseMetadataStep(psc, script).ensureReleaseArgumentsContainsArtifact(releaseArguments)
        then:
        expected == actual
        releaseArguments.get(ReleaseConstants.ARTIFACT_PROPERTY_NAME) == artifact
    }

    def "test archiveName for archive type"() {
        given:
        Map releaseArguments = [:]
        String artifact = "https://artifactory-dev.express-scripts.com/artifactory/api/npm/npm-local/@esi/greetings-ui/-/@esi/greetings-ui-1.0.5.tgz"
        releaseArguments.put(ReleaseConstants.ARTIFACT_PROPERTY_NAME, artifact)
        releaseArguments.put("repoUrl", "https://git.express-scripts.com/ExpressScripts/greetings-ui.git")
        StoreReleaseMetadataStep step = new StoreReleaseMetadataStep(psc, script)
        when:
        step.execute(releaseArguments)
        then:
        true
    }

    def "test getReleaseVersionFromArtifact"() {
        given:
        def artifact = "https://artifactory-dev.express-scripts.com/artifactory/api/npm/npm-local/@esi/greetings-ui/-/@esi/greetings-ui-1.0.5.tgz"
        StoreReleaseMetadataStep step = new StoreReleaseMetadataStep(psc, script)
        when:
        def afversion = step.getReleaseVersionFromArtifact(artifact)
        then:
        afversion == "1.0.5"
    }

    def "getReleaseVersionFromArtifact returns release version for a release branch name"() {
        given:
        def artifact = "release/v1.2.3"
        StoreReleaseMetadataStep step = new StoreReleaseMetadataStep(psc, script)
        when:
        def afversion = step.getReleaseVersionFromArtifact(artifact)
        then:
        afversion == "v1.2.3"
    }

    def "getReleaseVersionFromArtifact returns its input when version can't be determined"() {
        given:
        def artifact = "1.0.0-SNAPSHOT"
        StoreReleaseMetadataStep step = new StoreReleaseMetadataStep(psc, script)
        when:
        def afversion = step.getReleaseVersionFromArtifact(artifact)
        then:
        afversion == artifact
    }

    def "getReleaseVersionFromArtifact returns null if artifact is null"() {
        given:
        def artifact = null
        StoreReleaseMetadataStep step = new StoreReleaseMetadataStep(psc, script)
        when:
        def afversion = step.getReleaseVersionFromArtifact(artifact)
        then:
        afversion == null
    }

    def "test archiveName for git_tag type"() {
        given:
        Map releaseArguments = [:]
        String artifact = "release/v1.0.0"
        releaseArguments.put(ReleaseConstants.ARTIFACT_PROPERTY_NAME, artifact)
        releaseArguments.put("repoUrl", "https://git.express-scripts.com/kubernetes/aws-terraform-hs-pipelineautomation.git")
        StoreReleaseMetadataStep step = new StoreReleaseMetadataStep(psc, script)
        when:
        step.execute(releaseArguments)
        then:
        true
    }

    def "when ensureReleaseArgumentsContainsArtifact has no artifact in release arguments but finds in stepInvocations return true"() {
        given:
        boolean expected = true
        Map releaseArguments = ["somearg": "somevalue"]
        String artifact = "artifact name"
        Map deployArguments = [:]
        deployArguments.put(ReleaseConstants.ARTIFACT_PROPERTY_NAME, "artifact name")
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(verb: "deploy", arguments: deployArguments))
        when:
        boolean actual = new StoreReleaseMetadataStep(psc, script).ensureReleaseArgumentsContainsArtifact(releaseArguments)
        then:
        expected == actual
        releaseArguments.get(ReleaseConstants.ARTIFACT_PROPERTY_NAME) == artifact
    }

    def "when ensureReleaseArgumentsContainsArtifact has no artifact and no stepInvocations return false"() {
        given:
        boolean expected = false
        Map releaseArguments = ["somearg": "somevalue"]
        when:
        boolean actual = new StoreReleaseMetadataStep(psc, script).ensureReleaseArgumentsContainsArtifact(releaseArguments)
        then:
        expected == actual
    }

    def "when ensureReleaseArgumentsContainsArtifact has no artifact in release arguments or stepInvocations return false"() {
        given:
        boolean expected = false
        Map releaseArguments = ["somearg": "somevalue"]
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(verb: "deploy"))
        when:
        boolean actual = new StoreReleaseMetadataStep(psc, script).ensureReleaseArgumentsContainsArtifact(releaseArguments)
        then:
        expected == actual
    }

    def "when ensureReleaseArgumentsContainsArtifact has no artifact in release arguments or stepInvocations finds in build return true"() {
        given:
        def modules = new LoadPipelineModulesStep(psc, script).loadModuleContracts(["release", "npm"])
        psc.globalModuleManager.stateManager().addList(modules)
        boolean expected = true
        Map releaseArguments = ["somearg": "somevalue"]
        psc.globalModuleManager.stateManager().addPipelineStep(
                PipelineUtils.CreateInvocation(
                        verb: PipelineConstants.VERB_BUILD,
                        completed: true,
                        result: new StepResult(
                                commandResult: StepResult.SUCCESS,
                                commandOutput: [(ReleaseConstants.PUBLISH_URL_PROPERTY_NAME): "https://artifactory.express-scripts.com/repo/foo2321.jar"])
                )
        )
        when:
        boolean actual = new StoreReleaseMetadataStep(psc, script).ensureReleaseArgumentsContainsArtifact(releaseArguments)
        then:
        expected == actual
    }

    def "when ensureReleaseArgumentsContainsArtifact uses git path when provision step return true"() {
        given:
        // we fake this using the mockShellCommand to find a StePResult for npm build step
        def modules = [
                new ModuleContract(
                        contractName: ModuleContractType.PROVISION.name(),
                        artifactType: PipelineConstants.ARTIFACT_TYPE_GIT_TAG,
                        commandName: "baa",
                        image: "/cnp/cnp-docker-aws:latest"
                )
        ]
        psc.globalModuleManager.stateManager().addList(modules)

        boolean expected = true
        Map releaseArguments = ["somearg": "somevalue"]
        psc.globalModuleManager.stateManager().addPipelineStep(PipelineUtils.CreateInvocation(verb: "provision"))
        when:
        boolean actual = new StoreReleaseMetadataStep(psc, script).ensureReleaseArgumentsContainsArtifact(releaseArguments)
        then:
        expected == actual

    }

    // TESTS for ensureRepoInfoInReleaseArguments
    def "when ensureRepoInfoInReleaseArguments passed null args return false"() {
        given:
        boolean expected = false
        when:
        boolean actual = StoreReleaseMetadataStep.ensureRepoInfoInReleaseArguments(script, null)
        then:
        expected == actual
    }

    def "when ensureRepoInfoInReleaseArguments passed empty args add repo info and return true"() {
        given:
        boolean expected = true
        Map releaseArgs = [:]
        when:
        boolean actual = StoreReleaseMetadataStep.ensureRepoInfoInReleaseArguments(script, releaseArgs)
        then:
        expected == actual
        releaseArgs.get(ReleaseConstants.BRANCH_NAME_PROPERTY_NAME) != null
        releaseArgs.get(ReleaseConstants.REPO_URL_PROPERTY_NAME) != null
    }

    def "when ensureRepoInfoInReleaseArguments passed args that contains repo info leave unchanged"() {
        given:
        boolean expected = true
        Map releaseArgs = [:]
        String branchName = "release/dontchange"
        String repoUrl = "https://git.express-scripts.com/foo/bar.git"
        releaseArgs.put(ReleaseConstants.BRANCH_NAME_PROPERTY_NAME, branchName)
        releaseArgs.put(ReleaseConstants.REPO_URL_PROPERTY_NAME, repoUrl)
        when:
        boolean actual = StoreReleaseMetadataStep.ensureRepoInfoInReleaseArguments(script, releaseArgs)
        then:
        expected == actual
        releaseArgs.get(ReleaseConstants.BRANCH_NAME_PROPERTY_NAME) == branchName
        releaseArgs.get(ReleaseConstants.REPO_URL_PROPERTY_NAME) == repoUrl
    }

    def test() {
        given:
        def repoUrl = "https://github.sys.cigna.com/cigna/devops-testapps-SpringPcf.git"
        def artifactPath = "cnp/devops-testapps-Springpcf:1.3.42_148c8a5228d519ec2fcc0b014731f2c431c96a8c"
        //def artifactPath = "release/v1.3.42"
        //def artifactPath = "https://cigna.jfrog.io/artifactory/libs-release-local/com/esrx/devops-testapps-SpringPcf/1.3.42/devops-testapps-SpringPcf-1.3.42.jar"
        when:
        String archiveName = StoreReleaseMetadataStep.generateZipFileName(StoreReleaseMetadataStep.getRepositoryName(repoUrl),
                StoreReleaseMetadataStep.getReleaseVersionFromArtifact(artifactPath))
        then:
        archiveName == "deploymentPackage_devops-testapps-SpringPcf_1.3.42.zip"
    }

    def "createReleaseConfiguration only includes permitted release arguments from PipelineStateManager"() {
        given:
        PipelineStateManager manager = new PipelineStateManager()
        Map releaseArguments = [
            "Release_JenkinsMaster": "orchestrator17.orchestrator-v2.sys.cigna.com",
            "publishUrl"           : "[https://cigna.jfrog.io/artifactory/libs-release-local/com/express-scripts/ilm-attachments-saml-service/1.0.1/ilm-attachments-saml-service-1.0.1.jar, https://cigna.jfrog.io/artifactory/libs-release-local/com/express-scripts/ilm-attachments-saml-service/1.0.1/ilm-attachments-saml-service-1.0.1.pom]"
        ]
        manager.releaseArguments = releaseArguments
        MockJenkins jenkins = new MockJenkins()
        when:
        ReleaseInfo releaseInfo = StoreReleaseMetadataStep.createReleaseConfiguration(jenkins,
            manager,
            [:],
            'releaseId' as String,
            'artifactPath',
            'rollbackPackagePath' as String,
            'repoUrl' as String)
        then:
        releaseInfo.releaseArguments['Release_JenkinsMaster'] == releaseArguments['Release_JenkinsMaster'] // in the list of permitted args
        !releaseInfo.releaseArguments.containsKey('publishUrl') // not in the list of permitted args to release call
    }
}
