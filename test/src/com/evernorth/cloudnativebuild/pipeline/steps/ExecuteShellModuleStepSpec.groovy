package com.evernorth.cloudnativebuild.pipeline.steps

import com.cigna.state.PipelineStateContext
import com.evernorth.cloudnativebuild.mocks.MockJenkins
import com.evernorth.cloudnativebuild.model.Credential
import com.evernorth.cloudnativebuild.model.ModuleContract
import com.evernorth.cloudnativebuild.model.ModuleContractType
import com.evernorth.cloudnativebuild.model.StepResult
import spock.lang.Specification

class ExecuteShellModuleStepSpec extends Specification {
    
    Object script
    PipelineStateContext psc
    
    def setup() {
        script = new MockJenkins()
        psc = new PipelineStateContext(script)
    }
    
    def "executeStep with contract with credential"(){
        setup:
        psc.globalModuleManager.stateManager().add([contractName: "PREFLIGHT_CHECK",
                           buildStage: "PRE_STASH",
                           commandName: "check-git",
                           image: "docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-core",
                           credentials: [[id: 'mycred'], [id: 'something']],
                           moduleName:"cnp-preflight-terraform"])
        def step = new ExecuteShellModuleStep(psc, script)
        when:
        def result = step.execute(ModuleContractType.PREFLIGHT_CHECK)
        then:
        result.commandResult == StepResult.SUCCESS
        script.shellCommands.size()==1
        script.creds.size()==2
        script.creds.find {it["credentialsId"]=='mycred'}
        script.creds.find {it["credentialsId"]=='something'}
    }

    def "executeStep with contract without credential"(){
        setup:

        psc.globalModuleManager.stateManager().add([contractName: "PREFLIGHT_CHECK",
                           buildStage: "PRE_STASH",
                           commandName: "check-git",
                           image: "docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-core",
                           moduleName:"cnp-preflight-terraform"])
        def step = new ExecuteShellModuleStep(psc, script)
        when:
        def result = step.execute(ModuleContractType.PREFLIGHT_CHECK)
        then:
        result.commandResult == StepResult.SUCCESS
    }

    def "executeStep when allowPipelineToModifyArgumentList is true"(){
        setup:
        psc.globalModuleManager.stateManager().releaseArguments.put("repoUrl", "somerepo")
        psc.globalModuleManager.stateManager().add([contractName: "PREFLIGHT_CHECK",
                           buildStage: "PRE_STASH",
                           commandName: "check-git",
                           allowPipelineToModifyArgumentList:true,
                           image: "docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-core",
                           moduleName:"cnp-preflight-terraform"])
        def step = new ExecuteShellModuleStep(psc, script)
        when:
        def result = step.execute(ModuleContractType.PREFLIGHT_CHECK)
        then:
        script.shellCommands[0].contains("repoUrl")
        result.commandResult == StepResult.SUCCESS
    }


    def "throws exception when no module contract specified"(){
        setup:
        def step = new ExecuteShellModuleStep(psc, script)
        when:
        step.execute(null,"",null)
        then:
        thrown IllegalArgumentException
    }


    def "returns empty Result when no matching modules found with moduleName filter" (){
        setup:
        psc.globalModuleManager.stateManager().addList(script.defaultModules)
        def step = new ExecuteShellModuleStep(psc, script)
        when:
        def results = step.execute(ModuleContractType.PREFLIGHT_CHECK,"foo",[:])
        then:
        results == StepResult.empty()
    }

    def "returns empty Result when no matching module found" (){
        setup:
        def step = new ExecuteShellModuleStep(psc, script)
        when:
        def results = step.execute(ModuleContractType.TEST)
        then:
        results==StepResult.empty()
    }

    def "when filter is used executes only first matching module"() {
        setup:
        String moduleName = "cnp-custom-mod-name"
        psc.globalModuleManager.stateManager().addList(script.defaultModules)
        psc.globalModuleManager.stateManager().add([contractName: "PREFLIGHT_CHECK",
                           commandName: "check-git",
                           image: "docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-core",
                           moduleName:moduleName])
        def step = new ExecuteShellModuleStep(psc, script)
        when:
        def result = step.execute(ModuleContractType.PREFLIGHT_CHECK, moduleName)
        then:
        result.commandResult==StepResult.SUCCESS
        script.shellCommands.size()==1
    }

    def "when filter is used execute step is called with matching contract"() {
        setup:
        psc.globalModuleManager.stateManager().addList(script.defaultModules)
        psc.globalModuleManager.stateManager().add([contractName: "PREFLIGHT_CHECK",
                           commandName: "cnp-preflight-terraform.sh",
                            subCommand: "check",
                           image: "docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-core"])
        psc.globalModuleManager.stateManager().add([contractName: "DEPLOY",
                          commandName: "cnp-preflight-terraform.sh",
                          subCommand: "deploy",
                          image: "docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-core"])
        def step = new ExecuteShellModuleStep(psc, script)
        when:
        def result = step.execute(ModuleContractType.PREFLIGHT_CHECK, "cnp-preflight-terraform")
        then:
        result.commandResult==StepResult.SUCCESS
        script.shellCommands.size()==1
        script.shellCommands[0].startsWith("cnp-preflight-terraform.sh check")
    }




    def "when execute with no contract throw IllegalArgumentException"(){
        setup:
        def step = new ExecuteShellModuleStep(psc, script)
        when:
        step.execute(null, null)
        then:
        thrown IllegalArgumentException
    }

    def "when results file not found"(){
        setup:
        StepResult expectedResult = new StepResult(commandResult: "SUCCESS", errors: [])
        psc.globalModuleManager.stateManager().add([contractName: "PREFLIGHT_CHECK",
                           buildStage: "PRE_STASH",
                           commandName: "check-git",
                           image: "docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-core",
                           moduleName:"cnp-noresult-module"])
        def step = new ExecuteShellModuleStep(psc, script)
        when:
        def actualResult = step.execute(ModuleContractType.PREFLIGHT_CHECK)
        then:
        actualResult.commandOutput == expectedResult.commandOutput
        actualResult.commandResult == expectedResult.commandResult
    }

    def "when module writes success to results log step is success"(){
        setup:
        // note that since JSON does not have way of expressing empty map
        // when deserialized it will show as empty array
        StepResult expectedResult = new StepResult(commandResult: "SUCCESS", commandOutput: [:])
        psc.globalModuleManager.stateManager().add([contractName: "PREFLIGHT_CHECK",
                                                        buildStage: "PRE_STASH",
                                                        commandName: "check-git",
                                                        image: "docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-core",
                                                        moduleName:"cnp-preflight-terraform"])
        script.addReadFileResult("cnp-preflight-terraform-results.json","cnp-success-results.json")
        def step = new ExecuteShellModuleStep(psc, script)
        when:
        def actualResult = step.execute(ModuleContractType.PREFLIGHT_CHECK)
        then:
        actualResult.commandOutput == expectedResult.commandOutput
        actualResult.commandResult == expectedResult.commandResult
    }

    def "When module writes errors to result log step fails"(){
        setup:
        StepResult actualResult
        psc.globalModuleManager.stateManager().add([contractName: "PREFLIGHT_CHECK",
                        commandName: "check-git",
                        image: "docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-core:devel",
                        moduleName:"cnp-preflight-validatejenkinsfile"])
        script.addReadFileResult("cnp-preflight-validatejenkinsfile-results.json","cnp-preflight-validatejenkinsfile-results.txt")
        def step = new ExecuteShellModuleStep(psc, script)
        when:
        actualResult = step.execute(ModuleContractType.PREFLIGHT_CHECK)
        then:
        actualResult.errors.size() == 2
        actualResult.commandResult == "FAILURE"
    }

    def "When results file not existing return success"(){
        setup:
        StepResult result
        psc.globalModuleManager.stateManager().add([contractName: "PREFLIGHT_CHECK",
                        buildStage: "PRE_STASH",
                        commandName: "check-git",
                        image: "docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-core",
                        moduleName:"cnp-noresult-module"])
        def step = new ExecuteShellModuleStep(psc, script)
        when:
        result = step.execute(ModuleContractType.PREFLIGHT_CHECK)
        then:
        result == StepResult.empty()
    }

    def "When shell command returns non zero and no results file result step fails"(){
        setup:
        psc.globalModuleManager.stateManager().add([contractName: "PREFLIGHT_CHECK",
                           buildStage: "PRE_STASH",
                           commandName: "cause-an-error",
                           image: "docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-core",
                           moduleName:"cnp-noresult-module"])
        def step = new ExecuteShellModuleStep(psc, script)
        when:
        def result = step.execute(ModuleContractType.PREFLIGHT_CHECK)
        then:
        result.commandResult==StepResult.FAILURE
    }

    def "When shell command returns non zero result and writes success to results log, step is success"(){
        setup:
        psc.globalModuleManager.stateManager().add([contractName: "PREFLIGHT_CHECK",
                           buildStage: "PRE_STASH",
                           commandName: "cause-an-error",
                           image: "docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-core",
                           moduleName:"cnp-preflight-terraform"])
        script.addReadFileResult("cnp-preflight-terraform-results.json","cnp-success-results.json")
        def step = new ExecuteShellModuleStep(psc, script)
        when:
        def result = step.execute(ModuleContractType.PREFLIGHT_CHECK)
        then:
        result.commandResult==StepResult.SUCCESS
    }

    def "When shell command returns non zero result and writes errors to results log, step fails"(){
        setup:
        psc.globalModuleManager.stateManager().add([contractName: "PREFLIGHT_CHECK",
                           buildStage: "PRE_STASH",
                           commandName: "cause-an-error",
                           image: "docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-core",
                           moduleName:"cnp-preflight-validatejenkinsfile"])

        script.addReadFileResult("cnp-preflight-validatejenkinsfile-results.json","cnp-preflight-validatejenkinsfile-results.txt")
        def step = new ExecuteShellModuleStep(psc, script)
        when:
        def result = step.execute(ModuleContractType.PREFLIGHT_CHECK)
        then:
        result.errors.size() == 2
        result.commandResult==StepResult.FAILURE
    }

    def "when module generates malformed results file step fails"(){
        setup:
        psc.globalModuleManager.stateManager().add([contractName: "PREFLIGHT_CHECK",
                           commandName: "badfile.sh",
                           image: "docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-core",
                           moduleName:"badfile"])
        script.addReadFileResult("badfile-results.json","malformed-results.txt")
        def step = new ExecuteShellModuleStep(psc, script)
        when:
        def result = step.execute(ModuleContractType.PREFLIGHT_CHECK)
        then:
        result.commandResult==StepResult.FAILURE
    }


    def "When execute build stash and unStash is preformed"(){
        setup:
        psc.globalModuleManager.stateManager().add([contractName: "BUILD",
                           commandName: "mvn",
                           requiresStash: true,
                           stashName: "foo",
                           requiresUnStash: true,
                           unStashName: "bar",
                           image: "docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-java",
                           moduleName:"cnp-build-java"])
        def step = new ExecuteShellModuleStep(psc, script)
        when:
        def result = step.execute(ModuleContractType.BUILD)
        then:
        script.stashCount==1
        result==StepResult.empty()
    }

    def "executeStep with contract with multiple credentials"(){
        when:
        psc.globalModuleManager.stateManager().add([contractName: "PREFLIGHT_CHECK",
                           buildStage: "PRE_STASH",
                           commandName: "check-git",
                           image: "docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-core",
                           credentials: [[id: 'mycred'], [id: 'anotherone', type:'string']],
                           moduleName:"cnp-preflight-terraform"])
        then:
        psc.globalModuleManager.stateManager().getContractsForContactType("PREFLIGHT_CHECK").get(0).credentials.length == 2
    }
    def "executeStep with contract with multiple credentials when default is set in ModuleContractList for package"(){
        when:
        psc.globalModuleManager.stateManager().add([contractName: "PACKAGE",
                           commandName: "cnp-package-deb-build.sh",
                           image: "cnp/cnp-docker-core",
                           credentials: [[id: 'mycred', prefix:'GIT'], [id: 'anotherone', type:'string']],
                           moduleName:"cnp-package-deb"])
        then:
        psc.globalModuleManager.stateManager().getContractsForContactType("PACKAGE").get(0).credentials.length == 2
    }
    def "executeStep with contract with multiple credentials when default is set in ModuleContractList for quality check"(){
        when:
        psc.globalModuleManager.stateManager().add([contractName: "QUALITY_CHECK",
                           commandName: "cnp-qc.sh",
                           image: "cnp/cnp-docker-core",
                           credentials: [[id: 'mycred'], [id: 'anotherone', type:'string']],
                           moduleName:"cnp-qc"])
        then:
        psc.globalModuleManager.stateManager().getContractsForContactType("QUALITY_CHECK").get(0).credentials.length == 2
    }

    def "executeStep with contract with multiple credentials when default is set in ModuleContractList for quality check and not overriding ld"(){
        when:
        psc.globalModuleManager.stateManager().add([contractName: "QUALITY_CHECK",
                           commandName: "cnp-qc.sh",
                           image: "cnp/cnp-docker-core",
                           credentials: [[id: 'mycred', type:'azureServicePrincipal']],
                           moduleName:"cnp-qc"])
        then:
        psc.globalModuleManager.stateManager().getContractsForContactType("QUALITY_CHECK").get(0).credentials.length == 1
    }
    def "executeStep with contract with credentials when default is not set in ModuleContractList "(){
        when:
        psc.globalModuleManager.stateManager().add([contractName: "DEPLOY",
                           commandName: "cnp-deloy-terraform.sh",
                           image: "cnp/cnp-docker-terraform",
                           credentials: [[id: 'mycred']],
                           moduleName:"cnp-deloy-terraform"])
        then:
        psc.globalModuleManager.stateManager().getContractsForContactType("DEPLOY").get(0).credentials.length == 1 //0 defaulted & 1 here
    }


    def "when commandResults added to stepResults file they exist in step result" (){
        setup:

        psc.globalModuleManager.stateManager().add([contractName: "DEPLOY",
                           commandName: "cnp-deploy-terraform-azure.sh",
                           subCommand: "plan",
                           image: "docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-terraform:0.0.4"])


        when:
        script.addReadFileResult("cnp-deploy-terraform-azure-results.json")
        StepResult result = new CreateStepResultFromFileStep(script).execute(psc.globalModuleManager.stateManager().getFirstContractForContactType("DEPLOY"))
        def output = result.commandOutput
        List<String> artifactList = output.get("publishUrl")
        String fullArtifactPath=""
        artifactList.each {if (it.endsWith(".zip")) fullArtifactPath = it}
        def artifact = fullArtifactPath.split('/').last()
        def artifact_path = fullArtifactPath.replace("https://artifactory.express-scripts.com/artifactory/", '').replace(artifact, '').replaceFirst('.$',"")
        then:
        artifact_path == 'libs-snapshot-local/mycndirectory/com/evernorth/azure/functions/fhir-meta-tagger/1.0.0-SNAPSHOT'
        artifact == 'fhir-meta-tagger.zip'
    }

    def "get module contract by name & type"(){
        when:
        ModuleContract contract = new ModuleContract(
                contractName: "DEPLOY",
                commandName:"cnp-deploy-terraform-azure.sh",
                subCommand:"apply",
                image: "docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-terraform:0.0.4")
        psc.globalModuleManager.stateManager().add(contract)
        then:
        psc.globalModuleManager.stateManager().getContractByTypeAndModuleName(ModuleContractType.DEPLOY, "cnp-deploy-terraform-azure") != null
    }

    def "get module contract by name & type without filter returns first deploy contract"(){
        setup:
        ModuleContract contract = new ModuleContract(
                contractName: "DEPLOY",
                commandName:"cnp-deploy-terraform-azure.sh",
                subCommand:"apply",
                image: "docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-terraform:0.0.4")
        psc.globalModuleManager.stateManager().add(contract)
        when:
        ModuleContract matchedContract = psc.globalModuleManager.stateManager().getContractByTypeAndModuleName(ModuleContractType.DEPLOY, "")
        then:
        matchedContract == contract
    }

    def "shouldAddToCredsList is true when environment matches and scope is empty"(){
        setup:
        ExecuteShellModuleStep step = new ExecuteShellModuleStep(psc, script)
        Map options = [env: 'DEV']
        String environment = options.get('env')

        String scope = options.get('scope')
        boolean expected=true
        when:
        boolean actual = step.shouldAddToCredsList(environment, scope, options)
        then:
        expected==actual
    }

    def "shouldAddToCredsList with no environment and no options returns true"(){
        setup:
        ExecuteShellModuleStep step = new ExecuteShellModuleStep(psc, script)
        Map options = [:]
        String environment = options.get('env')
        String scope = options.get('scope')
        boolean expected = true
        when:
        boolean actual = step.shouldAddToCredsList(environment, scope, options)
        then:
        expected==actual
    }

    def "shouldAddToCredsList with scope and environment return true when both match"(){
        setup:
        ExecuteShellModuleStep step = new ExecuteShellModuleStep(psc, script)
        Map options = [env: 'DEV', scope: "AWS"]
        String environment = 'DEV'
        String scope = 'AWS'
        boolean expected = true
        when:
        boolean actual = step.shouldAddToCredsList(environment, scope, options)
        then:
        expected == actual

    }

   def "Manage credentials when present in module for different scope and env"() {
       setup:
       ExecuteShellModuleStep step = new ExecuteShellModuleStep(psc, script)
       ModuleContract contract = new ModuleContract(contractName: "DEPLOY",
               credentials: [
                       [id: 'aws_dev_user', type: 'string', env: 'DEV', scope: 'AWS'],
                       [id: 'oc_dev_user', type: 'string', env: 'DEV', scope: 'OC'],
                       [id: 'aws_qa_user', type: 'string', env: 'QA', scope: 'AWS'],
                       [id: 'oc_qa_user', type: 'string', env: 'QA', scope: 'OC']
               ],
               commandName: "cnp-deploy-argorollouts.sh",
               subCommand: "deploy",
               image: "docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-k8s:0.0.9")
       psc.globalModuleManager.stateManager().add(contract)
       Map devproperties = [platform       : "EKS",
                            credentials    : [[id: 'aws_dev_user', type: 'string', env: 'DEV', scope: 'AWS']],
                            cluster        : "eks-control.esi-us-entity-dev.aws.cignacloud.com",
                            dockerImage    : 'someimage',
                            tag            : 'imageTag',
                            ingressBaseHost: "eks-shared.esi-us-entity-dev.aws.cignacloud.com",
                            appName        : "greetings-1-dev",
                            namespace      : "coreeng-dev"
       ]
       Map devoptions = [scope: 'AWS', env: 'DEV']
       Map qaproperties = [platform       : "EKS",
                           cluster        : "eks-control.esi-us-entity-dev.aws.cignacloud.com",
                           dockerImage    : 'someimage',
                           tag            : 'imageTag',
                           ingressBaseHost: "eks-shared.esi-us-entity-dev.aws.cignacloud.com",
                           appName        : "greetings-1-dev",
                           namespace      : "coreeng-dev"
       ]
       Map qaoptions = [scope: 'AWS', env: 'QA']
       Map ocdevproperties = [platform       : "OpenShift",
                              cluster        : "eks-control.esi-us-entity-dev.aws.cignacloud.com",
                              dockerImage    : 'someimage',
                              tag            : 'imageTag',
                              ingressBaseHost: "eks-shared.esi-us-entity-dev.aws.cignacloud.com",
                              appName        : "greetings-1-dev",
                              namespace      : "coreeng-dev"
       ]
       Map ocdevoptions = [scope: 'OC', env: 'DEV']
       Map ocqaproperties = [platform       : "OpenShift",
                             cluster        : "eks-control.esi-us-entity-dev.aws.cignacloud.com",
                             dockerImage    : 'someimage',
                             tag            : 'imageTag',
                             ingressBaseHost: "eks-shared.esi-us-entity-dev.aws.cignacloud.com",
                             appName        : "greetings-1-dev",
                             namespace      : "coreeng-dev"
       ]
       Map ocqaoptions = [scope: 'OC', env: 'QA']
       when:
       def devResult = step.execute(ModuleContractType.DEPLOY, "", devproperties, devoptions)
       def qaResults = step.execute(ModuleContractType.DEPLOY, "", qaproperties, qaoptions)
       def ocDevResults = step.execute(ModuleContractType.DEPLOY, "", ocdevproperties, ocdevoptions)
       def ocQAResults = step.execute(ModuleContractType.DEPLOY, "", ocqaproperties, ocqaoptions)

       then:
       devResult.commandResult == "SUCCESS"
       qaResults.commandResult == "SUCCESS"
       ocDevResults.commandResult == "SUCCESS"
       ocQAResults.commandResult == "SUCCESS"


   }

   def "when credential id starts with env replace with environment variable"(){
        setup:
        ExecuteShellModuleStep step = new ExecuteShellModuleStep(psc, script)
        ModuleContract contract = new ModuleContract(contractName: "DEPLOY",
                credentials: [
                        [id: 'env.GIT_CREDENTIAL', type: 'string', env: 'DEV', scope: 'AWS', prefix:"FOO"],
                ],
                commandName: "cnp-deploy-argorollouts.sh",
                subCommand: "deploy",
                image: "docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-k8s:0.0.9")
        psc.globalModuleManager.stateManager().add(contract)
        when:
        StepResult devResult = step.execute(ModuleContractType.DEPLOY)
        then:
        devResult.commandResult==StepResult.SUCCESS
    }

    def "when stash fails error is caught"(){
        setup:
        ExecuteShellModuleStep step = new ExecuteShellModuleStep(psc, script)
        ModuleContract contract = new ModuleContract(contractName: "DEPLOY",
                requiresStash: true,
                stashName: "bad",
                commandName: "cnp-deploy-argorollouts.sh",
                image: "docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-k8s:0.0.9")
        psc.globalModuleManager.stateManager().add(contract)
        when:
        StepResult devResult = step.execute(ModuleContractType.DEPLOY)
        then:
        devResult.commandResult==StepResult.SUCCESS
    }

    def "when unstash fails error is caught"(){
        setup:
        ExecuteShellModuleStep step = new ExecuteShellModuleStep(psc, script)
        ModuleContract contract = new ModuleContract(contractName: "DEPLOY",
                requiresUnStash: true,
                unStashName: "bad",
                commandName: "cnp-deploy-argorollouts.sh",
                image: "docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-k8s:0.0.9")
        psc.globalModuleManager.stateManager().add(contract)
        when:
        StepResult devResult = step.execute(ModuleContractType.DEPLOY)
        then:
        devResult.commandResult==StepResult.SUCCESS
    }

    def "when step is deploy and branch is not deployable then skip step"(){
        setup:
        script.env.BRANCH_NAME = 'feature/v1.0.0'
        ExecuteShellModuleStep step = new ExecuteShellModuleStep(psc, script)
        ModuleContract contract = new ModuleContract(contractName: "DEPLOY",
                commandName: "cnp-deploy-argorollouts.sh",
                image: "docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-k8s:0.0.9")
        psc.globalModuleManager.stateManager().add(contract)
        when:
        StepResult devResult = step.execute(ModuleContractType.DEPLOY)
        then:
        devResult.commandResult==StepResult.SUCCESS
        devResult.commandOutput["status"]==StepResult.COULD_NOT_DEPLOY
    }
    def "when step is deploy and branch is deployable"(){
        setup:
        script.env.BRANCH_NAME = 'hotfix/v1.0.0'
        ExecuteShellModuleStep step = new ExecuteShellModuleStep(psc, script)
        ModuleContract contract = new ModuleContract(contractName: "DEPLOY",
                commandName: "cnp-deploy-argorollouts.sh",
                image: "docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-k8s:0.0.9")
        psc.globalModuleManager.stateManager().add(contract)
        when:
        StepResult devResult = step.execute(ModuleContractType.DEPLOY)
        then:
        devResult.commandResult==StepResult.SUCCESS
    }

    def "when step is deploy and branch is deployable with allowNonStandardDeployment flag"(){
        setup:
        ExecuteShellModuleStep step = new ExecuteShellModuleStep(psc, script)
        ModuleContract contract = new ModuleContract(contractName: "DEPLOY",
                allowNonStandardDeployment: true,
                commandName: "cnp-deploy-argorollouts.sh",
                image: "docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-k8s:0.0.9")
        psc.globalModuleManager.stateManager().add(contract)
        when:
        StepResult devResult = step.execute(ModuleContractType.DEPLOY)
        then:
        devResult.commandResult==StepResult.SUCCESS
    }



    def "generateCommandArgs creates command line args without any cred involved"(){
        setup:
        def step = new ExecuteShellModuleStep(psc, script)
        String command
        when:
        command = step.generateCommandArgs([args1: "foo", arg2: "foo2"])
        then:
        command == '{"args1":"foo","arg2":"foo2"}'
    }

    def "generateCommandWithArgs creates the expected command"(){
        setup:
        ModuleContract contract = new ModuleContract(commandName: "cnp-package-deb.sh", subCommand: "create" )
        String expected = "cnp-package-deb.sh create '{\"publish\":\"true\"}'"
        when:
        def actual = ExecuteShellModuleStep.generateCommandWithArguments(contract, [publish: "true"])
        then:
        expected==actual
    }
    def "generateCommandWithArgs creates the expected command with nested maps"(){
        setup:
        ModuleContract contract = new ModuleContract(commandName: "cnp-deploy-terraform-azure.sh", subCommand: "create" )
        String expected = "cnp-deploy-terraform-azure.sh create '{\"configDir\":\"envs/dev\",\"azTenantId\":\"tid\",\"azSubscriptionId\":\"sid\",\"azClientId\":\"cid\",\"tfDir\":\"deployments/Azure\",\"tfVars\":{\"artifactLocation\":\"something\"}}'"
        when:
        def actual = ExecuteShellModuleStep.generateCommandWithArguments(contract, [configDir: "envs/dev",
                                                                                   azTenantId: "tid",
                                                                                   azSubscriptionId: "sid",
                                                                                   azClientId: "cid",
                                                                                   tfDir: "deployments/Azure",
                                                                                   tfVars: [artifactLocation: "something"]
        ])
        then:
        expected==actual
    }
    def "generateCommandWithArgs creates the expected command with nested name/value pairs"(){
        setup:
        ModuleContract contract = new ModuleContract(commandName: "cnp-deploy-terraform-azure.sh", subCommand: "plan" )
        String expected = "cnp-deploy-terraform-azure.sh plan '{\"configDir\":\"envs/dev\",\"azTenantId\":\"tid\",\"azSubscriptionId\":\"sid\",\"azClientId\":\"cid\",\"tfDir\":\"deployments/Azure\",\"tfVars\":{\"artifactLocation\":\"something\"},\"anotherone\":\"name=value name1=value1\"}'"
        when:
        def actual = ExecuteShellModuleStep.generateCommandWithArguments(contract, [configDir: "envs/dev",
                                                                                   azTenantId: "tid",
                                                                                   azSubscriptionId: "sid",
                                                                                   azClientId: "cid",
                                                                                   tfDir: "deployments/Azure",
                                                                                   tfVars: [artifactLocation: "something"],
                                                                                   anotherone: "name=value name1=value1"
        ])
        then:
        expected==actual
    }

    def "generateCommand with empty map passes no args"(){
        setup:
        ModuleContract contract = new ModuleContract(commandName: "cnp-deploy-terraform-azure.sh", subCommand: "plan" )
        String expected = """#!/bin/bash -e
cnp-deploy-terraform-azure.sh plan"""
        when:
        def actual = ExecuteShellModuleStep.generateCommandWithArguments(contract,[:],true)
        then:
        expected==actual
    }

    def "step Skipped when test and disable tests enabled"(){
        setup:
        script.env.mockEnvironment["CNP_DISABLE_ALL_TESTS"]="true"
        ModuleContract contract = new ModuleContract(commandName: "runTest.sh", subCommand: "plan", contractName: ModuleContractType.TEST, image: "cnp/test-image:latest" )
        psc.globalModuleManager.stateManager().configuration.disableAllTests=true
        psc.globalModuleManager.stateManager().add(contract)
        ExecuteShellModuleStep step = new ExecuteShellModuleStep(psc, script)
        when:
        StepResult result = step.execute(ModuleContractType.TEST,"runTest",[:],[:])
        then:
        result.commandResult==StepResult.SUCCESS
        result.commandOutput["status"]== StepResult.TEST_SKIPPED
    }

    def "step not skipped when test and disable tests disabled"(){
        setup:
        script.env.mockEnvironment["CNP_DISABLE_ALL_TESTS"]="false"
        script.addReadFileResult("runTest-results.json","cnp-jenkins-job-build-failure-results.json")
        ModuleContract contract = new ModuleContract(commandName: "runTest.sh", subCommand: "plan", contractName: ModuleContractType.TEST, image: "cnp/test-image:latest" )
        psc.globalModuleManager.stateManager().configuration.disableAllTests=true
        psc.globalModuleManager.stateManager().add(contract)
        ExecuteShellModuleStep step = new ExecuteShellModuleStep(psc, script)
        when:
        StepResult result = step.execute(ModuleContractType.TEST,"runTest",[:],[:])
        then:
        result.commandResult==StepResult.FAILURE
    }

    def "verify translated env var in cred"(){
        setup:
        script.env.BRANCH_NAME = 'hotfix/v1.0.0'
        ExecuteShellModuleStep step = new ExecuteShellModuleStep(psc, script)
        ModuleContract contract = new ModuleContract(contractName: "DEPLOY",
                commandName: "cnp-deploy-argorollouts.sh",
                credentials: [
                        new Credential(id: "env.CNP_OC_CRED_DEV", env: "dev"),
                        new Credential(id: "env.CNP_OC_CRED_QA", env: "qa"),
                        new Credential(id: "env.CNP_OC_CRED_UAT", env: "uat"),
                        new Credential(id: "env.CNP_OC_CRED_PROD", env: "prod"),
                        new Credential(id: "env.CNP_OC_CRED_DR", env: "dr")],
                image: "docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-k8s:0.0.9")
        psc.globalModuleManager.stateManager().add(contract)
        def beforeTranslate = contract.credentials[0].id
        when:
        StepResult devResult = step.execute(ModuleContractType.DEPLOY)
        def afterTranslate = contract.credentials[0].id
        then:
        beforeTranslate == "env.CNP_OC_CRED_DEV"
        beforeTranslate == afterTranslate
        devResult.commandResult==StepResult.SUCCESS
    }


}
