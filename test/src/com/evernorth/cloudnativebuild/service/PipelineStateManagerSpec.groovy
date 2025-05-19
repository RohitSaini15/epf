package com.evernorth.cloudnativebuild.service

import com.cigna.state.PipelineStateContext
import com.evernorth.cloudnativebuild.data.PipelineConstants
import com.evernorth.cloudnativebuild.data.PipelineEventType
import com.evernorth.cloudnativebuild.mocks.MockJenkins
import com.evernorth.cloudnativebuild.model.Credential
import com.evernorth.cloudnativebuild.model.ModuleContract
import com.evernorth.cloudnativebuild.model.ModuleContractType
import com.evernorth.cloudnativebuild.pipeline.PipelineUtils
import com.evernorth.cloudnativebuild.pipeline.steps.LoadPipelineModulesStep
import spock.lang.Specification

class PipelineStateManagerSpec extends Specification {

    def "convert throws IllegalArgumentException if object is not expected format"(){
        given:
        MockJenkins mockJenkins = new MockJenkins()
        PipelineStateManager stateManager = new PipelineStateManager()
        stateManager.addList(mockJenkins.defaultModules)
        when:
            PipelineStateManager.convertMapToModuleContract("foo")
        then:
            IllegalArgumentException ex = thrown()
            ex.message == PipelineStateManager.ERROR_INVALID_PLUGIN_DESCRIPTOR + ": foo"
    }

    def "add throws IllegalArgumentException if map name property does not match a defined contract"(){
        given:
        PipelineStateManager manager = new PipelineStateManager()
        String image = "devops/defaultCbcBuild:1.0.1"
        when:
        manager.add([name: "foo", image: "$image"])
        then:
        thrown IllegalArgumentException
    }

    def "when no moduleName and commandName does not contain dot use commandName as module name"(){
        given:
        PipelineStateManager manager = new PipelineStateManager()
        String image = "devops/defaultCbcBuild:latest"
        when:
        manager.add([contractName: ModuleContractType.BUILD.name(), commandName:"foo", image: "$image"])
        then:
        manager.getContracts()[0].moduleName=="foo"
    }

    def "when no moduleName and commandName contains dot use commandName minus chars after dot"(){
        given:
        PipelineStateManager manager = new PipelineStateManager()
        String image = "devops/defaultCbcBuild:latest"
        when:
        manager.add([contractName: ModuleContractType.BUILD.name(), commandName:"foo.sh", image: "$image"])
        then:
        manager.getContracts()[0].moduleName=="foo"
    }

    def "add contains 1 plugin after add using map"(){
        given:
        PipelineStateManager manager = new PipelineStateManager()
        String image = "devops/defaultCbcBuild:1.0.1"
        when:
        manager.add([contractName: ModuleContractType.PREFLIGHT_CHECK.name(), image: "$image"])
        then:
        manager.getContractsForContactType(ModuleContractType.PREFLIGHT_CHECK.name())?.size() == 1
        manager.getContractsForContactType(ModuleContractType.TEST.name())?.size() == 0
    }

    def "convert returns ModuleContract when passed name value pair"() {
        given:
        String image = "devops/defaultCbcBuild:1.0.1"
        when:
        def contract = PipelineStateManager.convertMapToModuleContract([contractName: ModuleContractType.PREFLIGHT_CHECK.name(), image: "$image"])
        then:
        contract instanceof ModuleContract
        contract.image == image
    }

    def "convert returns a PluginContract when ModuleContact passed"() {
        given:
            def contract = new ModuleContract()
        when:
            def contactInstance = PipelineStateManager.convertMapToModuleContract(contract)
        then:
            contract == contactInstance
    }

    def "convert throws IllegalArgumentException when bad ModuleContract name passed"(){
        given:
        String image = "devops/defaultCbcBuild:1.0.1"
        when:
        PipelineStateManager.convertMapToModuleContract([contractName: "foo", image: "$image"])
        then:
        thrown IllegalArgumentException
    }

    def "when convert is given a string for post-event, it becomes an event type enum value"() {
        given:
        def toBeConverted = [contractName: "DEPLOY",  commandName:"cnp-deploy-fake.sh",
                             image: "coreImage", postExecuteEvent:'deployCompleted']
        when:
        def contract = PipelineStateManager.convertMapToModuleContract(toBeConverted)
        then:
        contract.postExecuteEvent != null
        contract.postExecuteEvent instanceof PipelineEventType
    }

    def "when convert is given a list of strings for triggeredByEvent, a list of pipeline events results"() {
        given:
        def toBeConverted = [
                contractName: "EVENT",
                commandName: "cnptools",
                subCommand: "deploymarker",
                moduleName:"cnp-event-deployment-marker",
                credentials: [
                        [id: "NEWRELIC_API_KEY_PROD", env:"prod", type:"string"],
                        [id: "NEWRELIC_API_KEY_NONPROD", type:"string"]],
                image: 'docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-core:latest',
                triggeredByEvent:['cutoverCompleted', 'deployCompleted']
        ]
        when:
        def contract = PipelineStateManager.convertMapToModuleContract(toBeConverted)
        then:
        contract.triggeredByEvent.every { it instanceof PipelineEventType}
        contract.triggeredByEvent.size() == 2
    }

    def "when convert is given a list of PET instances for triggeredByEvent, a list of pipeline events results"() {
        given:
        def toBeConverted = [
                contractName: "EVENT",
                commandName: "cnptools",
                subCommand: "deploymarker",
                moduleName:"cnp-event-deployment-marker",
                credentials: [
                        [id: "NEWRELIC_API_KEY_PROD", env:"prod", type:"string"],
                        [id: "NEWRELIC_API_KEY_NONPROD", type:"string"]],
                image: 'docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-core:latest',
                triggeredByEvent:[PipelineEventType.cutoverCompleted, PipelineEventType.deployCompleted]
        ]
        when:
        def contract = PipelineStateManager.convertMapToModuleContract(toBeConverted)
        then:
        contract.triggeredByEvent.every { it instanceof PipelineEventType}
        contract.triggeredByEvent.size() == 2
    }

    def "when convert is given a mix of PET instances and strings for triggeredByEvent, a list of pipeline events results"() {
        given:
        def toBeConverted = [
                contractName: "EVENT",
                commandName: "cnptools",
                subCommand: "deploymarker",
                moduleName:"cnp-event-deployment-marker",
                credentials: [
                        [id: "NEWRELIC_API_KEY_PROD", env:"prod", type:"string"],
                        [id: "NEWRELIC_API_KEY_NONPROD", type:"string"]],
                image: 'docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-core:latest',
                triggeredByEvent:[PipelineEventType.cutoverCompleted, 'deployCompleted',
                                  PipelineEventType.buildCompleted, 'createPackageCompleted']
        ]
        when:
        def contract = PipelineStateManager.convertMapToModuleContract(toBeConverted)
        then:
        contract.triggeredByEvent.every { it instanceof PipelineEventType}
        contract.triggeredByEvent.size() == 4
    }

    def "getContracts returns all preflightCheck PluginContracts when type filter given"() {
        given:
        PipelineStateManager manager = new PipelineStateManager()
        String image = "devops/defaultCbcBuild:1.0.1"
        manager.add([contractName: ModuleContractType.PREFLIGHT_CHECK.name(), image: "$image", commandName: "foo"])
        manager.add([contractName: ModuleContractType.PREFLIGHT_CHECK.name(), image: "$image"])
        manager.add([contractName: ModuleContractType.BUILD.name(), image: "$image"])
        when:
        def contactInstances = manager.getContracts(ModuleContractType.PREFLIGHT_CHECK.name())
        then:
        contactInstances.size() == 2
    }

    def "manager.add(ModuleContract)"() {
        given:
        PipelineStateManager manager = new PipelineStateManager()
        String image = "devops/defaultCbcBuild:1.0.1"
        manager.add(new ModuleContract(contractName: ModuleContractType.PREFLIGHT_CHECK.name(), image: "$image", commandName: "foo"))
        manager.add(new ModuleContract(contractName: ModuleContractType.PREFLIGHT_CHECK.name(), image: "$image"))
        manager.add(new ModuleContract(contractName: ModuleContractType.BUILD.name(), image: "$image"))
        when:
        def contactInstances = manager.getContracts(ModuleContractType.PREFLIGHT_CHECK.name())
        then:
        contactInstances.size() == 2
    }

    def "when addList adds three contracts contacts added to manager"() {
        given:
        PipelineStateManager manager = new PipelineStateManager()
        def modules  = [
                [contractName: "PREFLIGHT_CHECK", buildStage: "PRE_BUILD", commandName:"validatefile1", image: "docker-dev.artifactory-dev.express-scripts.com/devops/preflight-check-image:latest"],
                [contractName: "PREFLIGHT_CHECK", buildStage: "PRE_CHECK_OUT",  commandName:"validatefile2", image: "docker-dev.artifactory-dev.express-scripts.com/devops/preflight-check-image:latest"],
                [contractName: "PREFLIGHT_CHECK", buildStage: "PRE_STASH", commandPrefix:"foo", commandName:"validatefile3", image: "docker-dev.artifactory-dev.express-scripts.com/devops/preflight-check-image:latest"]
        ]
        when:
        manager.addList(modules)
        then:
        def foo = manager.getContracts()
        foo.size()==3
        foo[0].commandName=="validatefile1"
        foo[2].commandName=="validatefile3"
    }

    def "when addList duplicates are ignored"(){
        given:
        MockJenkins jenkins = new MockJenkins()
        PipelineStateContext psc = new PipelineStateContext(jenkins)
        LoadPipelineModulesStep step = new LoadPipelineModulesStep(psc, jenkins)
        def modules = step.loadModuleContracts(["common","pcf"])
        def modules2 = step.loadModuleContracts(["common","release"])
        when:
        psc.globalModuleManager.stateManager().addList(modules)
        psc.globalModuleManager.stateManager().addList(modules2)
        then:
        psc.globalModuleManager.stateManager().getContracts().findAll{it.moduleName=="cnp-preflight-check-git-settings"}.size()==1
    }

    def "PipelineStateManager with additional modules & from mockJenkins"(){
        given:
        def modules  = [
                [contractName: "PREFLIGHT_CHECK", buildStage: "PRE_BUILD", commandName:"validatefile1", image: "docker-dev.artifactory-dev.express-scripts.com/devops/preflight-check-image:latest"],
                [contractName: "PREFLIGHT_CHECK", buildStage: "PRE_CHECK_OUT",  commandName:"validatefile2", image: "docker-dev.artifactory-dev.express-scripts.com/devops/preflight-check-image:latest"],
                [contractName: "PREFLIGHT_CHECK", buildStage: "PRE_STASH", commandPrefix:"foo", commandName:"validatefile3", image: "docker-dev.artifactory-dev.express-scripts.com/devops/preflight-check-image:latest"]
        ]
        when:
        MockJenkins mockJenkins = new MockJenkins()
        PipelineStateManager stateManager = new PipelineStateManager()
        stateManager.addList(mockJenkins.defaultModules)
        stateManager.addList(modules)
        then:
        stateManager.getContracts().commandName.toString()!=""
    }
    def "PipelineStateManager from mockJenkins"(){
        when:
        MockJenkins mockJenkins = new MockJenkins()
        PipelineStateManager stateManager = new PipelineStateManager()
        stateManager.addList(mockJenkins.defaultModules)
        then:
        stateManager.getContracts().commandName.toString()!=""
    }

    def "when add called and no module name then use commandName without file extension"(){
        given:
        MockJenkins mockJenkins = new MockJenkins()
        PipelineStateManager stateManager = new PipelineStateManager()
        stateManager.addList(mockJenkins.defaultModules)
        when:
        stateManager.add([contractName: "PREFLIGHT_CHECK",
                           commandName: "check-git.sh",
                           image: "docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-core"])
        then:
        stateManager.getContractByTypeAndModuleName(ModuleContractType.PREFLIGHT_CHECK, "check-git") !=null
    }

    def "when getDeploymentContracts called and no deployable contracts return empty list"(){
        given:
        def modules = [[contractName: "BUILD",
                        commandName: "deploy.sh",
                        image: "/cnp/cnp-docker-core"]]
        PipelineStateManager manager = new PipelineStateManager()
        manager.addList(modules)
        int expected = 0
        when:
        def actual = manager.getDeploymentContracts().size()
        then:
        expected==actual

    }

    def "when getDeploymentContracts called and only 1 deployment module return list containing 1 item"(){
        given:
        def modules = [[contractName: "DEPLOY",
                        commandName: "deploy.sh",
                        image: "/cnp/cnp-docker-core"]]
        PipelineStateManager manager = new PipelineStateManager()
        manager.addList(modules)
        int expected = 1
        when:
        def actual = manager.getDeploymentContracts().size()
        then:
        expected==actual
    }

    def "when getDeploymentContracts called and has 2 deployment modules and 1 cutover module return list with 3 items"(){
        given:
        def modules = [[contractName: "DEPLOY",
                        commandName: "deploy-aws.sh",
                        image: "/cnp/cnp-docker-core"],
                       [contractName: "DEPLOY",
                       commandName: "deploy-pcf.sh",
                       image: "/cnp/cnp-docker-core"],
                       [contractName: "CUTOVER",
                        commandName: "cutover-pcf.sh",
                        image: "/cnp/cnp-docker-core"]]
        PipelineStateManager manager = new PipelineStateManager()
        manager.addList(modules)
        int expected = 3
        when:
        def actual = manager.getDeploymentContracts().size()
        then:
        expected==actual
    }

    def "when add called and module name then dont change moduleName"(){
        given:
        MockJenkins mockJenkins = new MockJenkins()
        PipelineStateManager stateManager = new PipelineStateManager()
        stateManager.addList(mockJenkins.defaultModules)
        when:
        stateManager.add([contractName: "PREFLIGHT_CHECK",
                           commandName: "check-git.sh",
                           image: "docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-core",
                moduleName:"foobar-bat"
        ])
        then:
        stateManager.getContractByTypeAndModuleName(ModuleContractType.PREFLIGHT_CHECK, "foobar-bat") !=null
    }
    def "when module has 1 credential defined"(){
        given:
        MockJenkins mockJenkins = new MockJenkins()
        PipelineStateManager stateManager = new PipelineStateManager()
        stateManager.addList(mockJenkins.defaultModules)
        when:
        stateManager.add([contractName: "PREFLIGHT_CHECK",
                           commandName: "check-git.sh",
                           image: "docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-core",
                           moduleName:"foobar-bat",
                           credentials: [[id: 'cbc/artifactory']]
        ])
        then:
        stateManager.getContractByTypeAndModuleName(ModuleContractType.PREFLIGHT_CHECK, "foobar-bat") !=null
        stateManager.getContractByTypeAndModuleName(ModuleContractType.PREFLIGHT_CHECK, "foobar-bat").credentials[0].id == 'cbc/artifactory'
        stateManager.getContractByTypeAndModuleName(ModuleContractType.PREFLIGHT_CHECK, "foobar-bat").credentials[0].type == 'usernamePassword'
    }
    def "when module has 2 credentials defined"(){
        given:
        MockJenkins mockJenkins = new MockJenkins()
        PipelineStateManager stateManager = new PipelineStateManager()
        stateManager.addList(mockJenkins.defaultModules)
        when:
        stateManager.add([contractName: "PREFLIGHT_CHECK",
                           commandName: "check-git.sh",
                           image: "docker-dev.artifactory.express-scripts.com/cnp/cnp-docker-core",
                           moduleName:"foobar-bat",
                           credentials: [[id: 'cbc/artifactory'], [id: 'ansiblevaultpassword', type: 'string']]
        ])
        then:
        stateManager.getContractByTypeAndModuleName(ModuleContractType.PREFLIGHT_CHECK, "foobar-bat") !=null
        stateManager.getContractByTypeAndModuleName(ModuleContractType.PREFLIGHT_CHECK, "foobar-bat").credentials[0].id == 'cbc/artifactory'
        stateManager.getContractByTypeAndModuleName(ModuleContractType.PREFLIGHT_CHECK, "foobar-bat").credentials[0].type == 'usernamePassword'
        stateManager.getContractByTypeAndModuleName(ModuleContractType.PREFLIGHT_CHECK, "foobar-bat").credentials[1].id == 'ansiblevaultpassword'
        stateManager.getContractByTypeAndModuleName(ModuleContractType.PREFLIGHT_CHECK, "foobar-bat").credentials[1].type == 'string'
    }

    def "when module list created from moduleLoader Step"(){
        given:
        def jenkins = new MockJenkins()
        def psc = new PipelineStateContext(jenkins)
        LoadPipelineModulesStep step = new LoadPipelineModulesStep(psc, jenkins)
        when:
        def modules = step.loadModuleContracts([PipelineConstants.COMMON_MODULE_TEMPLATE])
        psc.globalModuleManager.stateManager().addList(modules)
        then:
        psc.globalModuleManager.stateManager().getContractByTypeAndModuleName(ModuleContractType.PREFLIGHT_CHECK, "cnp-preflight-check-git-settings").image != null
    }

    def "when module added without name command name used"(){
        given:
        PipelineStateManager manager = new PipelineStateManager()
        when:
        manager.add(new ModuleContract(contractName: ModuleContractType.DEPLOY, commandName: "cnp-preflight-check-git-flow-settings.sh"))
        then:
        manager.contracts[0].moduleName=="cnp-preflight-check-git-flow-settings"
        manager.contracts[0].logFileName=="cnp-preflight-check-git-flow-settings-results.json"
    }

    def "mapCredentials returns default cred when no creds in Jenkinsfile"() {
        given:
        ModuleContract contract = new ModuleContract(
            contractName: ModuleContractType.TEST,
            commandName: "test",
            credentials: [[id: "foo"]]
        )
        def credFromJenkins = []
        when:
        def list = PipelineStateManager.mapCredentials(contract, credFromJenkins)
        then:
        list != null
        list.size() == 1
        list[0].id == "foo"
    }

    def "addPipelineStep adds new InvocationStep to the list"(){
        given:
        PipelineStateManager manager = new PipelineStateManager()
        when:
        manager.addPipelineStep(PipelineUtils.CreateInvocation(verb: "deploy"))
        then:
        manager.getPipelineSteps().size()==1
    }

    def "addPipelineStep ignores null InvocationStep"(){
        given:
        PipelineStateManager manager = new PipelineStateManager()
        when:
        manager.addPipelineStep(null)
        then:
        manager.getPipelineSteps().size()==0
    }

    def "updatePipelineStep replaces InvocationStep with new version"(){
        given:
        PipelineStateManager manager = new PipelineStateManager()
        when:
        manager.addPipelineStep(PipelineUtils.CreateInvocation(verb: "deploy"))
        manager.addPipelineStep(PipelineUtils.CreateInvocation(verb: "cutover"))
        manager.updatePipelineStep(PipelineUtils.CreateInvocation(verb: "cutover", order: 2, retryCount: 1, completed: true))
        then:
        manager.getPipelineSteps().size()==2
        manager.getPipelineSteps().find({ it.order == 2 })?.completed
    }

    def "updatePipelineStep throws exception when step not found"(){
        given:
        PipelineStateManager manager = new PipelineStateManager()
        when:
        manager.addPipelineStep(PipelineUtils.CreateInvocation(verb: "deploy"))
        manager.addPipelineStep(PipelineUtils.CreateInvocation(verb: "cutover"))
        manager.updatePipelineStep(PipelineUtils.CreateInvocation(verb: "cutover", order: 3, retryCount: 1, completed: true))
        then:
        thrown IllegalArgumentException
    }
    def "updatePipelineStep throws exception when step is null"(){
        given:
        PipelineStateManager manager = new PipelineStateManager()
        when:
        manager.addPipelineStep(PipelineUtils.CreateInvocation(verb: "deploy"))
        manager.addPipelineStep(PipelineUtils.CreateInvocation(verb: "cutover"))
        manager.updatePipelineStep(null)
        then:
        thrown IllegalArgumentException
    }

    def "when enable global filter is null throw exception"(){
        given:
        PipelineStateManager manager = new PipelineStateManager()
        when:
        manager.enableGlobalModuleFilter(null)
        then:
        thrown IllegalArgumentException
    }

    def "when enable global filter is empty throw exception"(){
        given:
        PipelineStateManager manager = new PipelineStateManager()
        when:
        manager.enableGlobalModuleFilter("")
        then:
        thrown IllegalArgumentException
    }

    def "when enable global filter is not valid filter throw exception"(){
        given:
        PipelineStateManager manager = new PipelineStateManager()
        when:
        manager.enableGlobalModuleFilter("")
        then:
        thrown IllegalArgumentException
    }

    def "when enable global filter is valid set filter"(){
        given:
        PipelineStateManager manager = new PipelineStateManager()
        manager.add(new ModuleContract(contractName: ModuleContractType.DEPLOY, commandName: "cnp-deploy-pcf.sh"))
        when:
        manager.enableGlobalModuleFilter("cnp-deploy-pcf")
        then:
        manager.getGlobalModuleFilter() == "cnp-deploy-pcf"
    }

    def "when disable global filter sets to empty string"(){
        given:
        PipelineStateManager manager = new PipelineStateManager()
        manager.add(new ModuleContract(contractName: ModuleContractType.DEPLOY, commandName: "cnp-deploy-pcf.sh"))
        when:
        manager.enableGlobalModuleFilter("cnp-deploy-pcf")
        manager.disableGlobalModuleFilter()
        then:
        manager.getGlobalModuleFilter()==""
    }

    def "when same credential added to ModuleContract twice remove duplicate"(){
        given:
        ModuleContract contract = new ModuleContract(image: "foo", credentials: [new Credential(id:"test", scope:"all",env:"dev")])
        def credentialList = [
                new Credential(id:"test", scope:"all",env:"dev"),
                new Credential(id:"test2", scope:"all",env:"qa"),
                new Credential(id:"test3", scope:"all",env:"prod")
        ]
        when:
        def newCredentialList = PipelineStateManager.consolidateCredentials(contract,credentialList)
        then:
        newCredentialList?.size()==3
    }

    def "when patchModule uses invalid moduleName exception is thrown"(){
        given:
        def jenkins = new MockJenkins()
        def psc = new PipelineStateContext(jenkins)
        PipelineStateManager stateManager = psc.globalModuleManager.stateManager()
        LoadPipelineModulesStep loadPipelineModulesStep = new LoadPipelineModulesStep(psc, jenkins)
        def modules = loadPipelineModulesStep.loadModuleContracts(["pcf","common","openshift"])
        stateManager.addList(modules)
        when:
        stateManager.patchModule("notfound",[:])
        then:
        thrown IllegalArgumentException
    }

    def "when patchModule uses invalid property name exception is thrown"(){
        given:
        def jenkins = new MockJenkins()
        def psc = new PipelineStateContext(jenkins)
        PipelineStateManager stateManager = psc.globalModuleManager.stateManager()
        LoadPipelineModulesStep loadPipelineModulesStep = new LoadPipelineModulesStep(psc,jenkins)
        def modules = loadPipelineModulesStep.loadModuleContracts(["pcf","common","openshift"])
        stateManager.addList(modules)
        when:
        stateManager.patchModule("cnp-deploy-argorollouts",[foo:"bar"])
        then:
        thrown IllegalArgumentException
    }

    def "when patchModule adds new credentials they are added to matching modules"(){
        given:
        def jenkins = new MockJenkins()
        def psc = new PipelineStateContext(jenkins)
        PipelineStateManager stateManager = psc.globalModuleManager.stateManager()
        LoadPipelineModulesStep loadPipelineModulesStep = new LoadPipelineModulesStep(psc, jenkins)
        def modules = loadPipelineModulesStep.loadModuleContracts(["pcf","common","openshift"])
        stateManager.addList(modules)
        int expectedCredentials = stateManager.getContracts().find{it.moduleName=="cnp-deploy-argorollouts" && it.contractName=="DEPLOY"}.credentials.size() + 1
        int expectedCredentials2 = stateManager.getContracts().find{it.moduleName=="cnp-deploy-argorollouts" && it.contractName=="CUTOVER"}.credentials.size() + 1
        when:
        stateManager.patchModule("cnp-deploy-argorollouts",[credentials:[[id:"myEnvCred",env:"myEnv"]]])
        then:
        stateManager.getContracts().find{it.moduleName=="cnp-deploy-argorollouts" && it.contractName=="DEPLOY"}.credentials.find{it.env=="myEnv" && it.id=="myEnvCred"}!=null
        stateManager.getContracts().find{it.moduleName=="cnp-deploy-argorollouts" &&  it.contractName=="DEPLOY"}.credentials.size()==expectedCredentials
        stateManager.getContracts().find{it.moduleName=="cnp-deploy-argorollouts" && it.contractName=="CUTOVER"}.credentials.find{it.env=="myEnv" && it.id=="myEnvCred"}!=null
        stateManager.getContracts().find{it.moduleName=="cnp-deploy-argorollouts" && it.contractName=="CUTOVER"}.credentials.size()==expectedCredentials2
    }

    def "when patchModule updates credential it is updated"() {
        given:
        def jenkins = new MockJenkins()
        def psc = new PipelineStateContext(jenkins)
        PipelineStateManager stateManager = psc.globalModuleManager.stateManager()
        LoadPipelineModulesStep loadPipelineModulesStep = new LoadPipelineModulesStep(psc, jenkins)
        def modules = loadPipelineModulesStep.loadModuleContracts(["pcf", "common", "openshift"])
        stateManager.addList(modules)
        int expectedCredentials = stateManager.getContracts().find { it.moduleName == "cnp-deploy-argorollouts" }.credentials.size()
        when:
        stateManager.patchModule("cnp-deploy-argorollouts", [credentials: [[id: "newid", env: "dev", type: "string"]]])
        int afterMod = stateManager.getContracts().find { it.moduleName == "cnp-deploy-argorollouts" }.credentials.size()
        then:
        stateManager.getContracts().find { it.moduleName == "cnp-deploy-argorollouts" }.credentials.find { it.id == "newid" && it.env == "dev" } != null
        afterMod == expectedCredentials
    }

    def "when two modules with same name patchModule updates credential it is updated"() {
        given:
        def jenkins = new MockJenkins()
        def psc = new PipelineStateContext(jenkins)
        PipelineStateManager stateManager = psc.globalModuleManager.stateManager()
        LoadPipelineModulesStep loadPipelineModulesStep = new LoadPipelineModulesStep(psc, jenkins)
        def modules = loadPipelineModulesStep.loadModuleContracts(["pcf", "common", "openshift"])
        stateManager.addList(modules)
        int expectedCredentials = stateManager.getContracts().find { it.moduleName == "cnp-deploy-argorollouts" && it.contractName == "CUTOVER" }.credentials.size()
        when:
        stateManager.patchModule("cnp-deploy-argorollouts", "CUTOVER", [credentials: [[id: "newid", env: "dev"]]])
        int afterMod = stateManager.getContracts().find { it.moduleName == "cnp-deploy-argorollouts" && it.contractName == "CUTOVER" }.credentials.size()
        then:
        stateManager.getContracts().find { it.moduleName == "cnp-deploy-argorollouts" && it.contractName == "CUTOVER" }.credentials.find { it.id == "newid" && it.env == "dev" } != null
        afterMod == expectedCredentials
    }

    def "when patchModule update stage name value updated in all matching modules"() {
        given:
        def jenkins = new MockJenkins()
        def psc = new PipelineStateContext(jenkins)
        PipelineStateManager stateManager = psc.globalModuleManager.stateManager()
        LoadPipelineModulesStep loadPipelineModulesStep = new LoadPipelineModulesStep(psc, jenkins)
        def modules = loadPipelineModulesStep.loadModuleContracts(["pcf", "common", "eks"])
        stateManager.addList(modules)
        when:
        stateManager.patchModule("cnp-deploy-argorollouts", "CUTOVER", [stageName: "aws"])
        then:
        stateManager.getContracts().find { it.moduleName == "cnp-deploy-argorollouts" && it.contractName == "CUTOVER" }.stageName == "aws"
    }

    def "loadConfiguration can load a build configuration that doesn't perfectly match class def"() {
        given:
        PipelineStateManager psm = new PipelineStateManager()
        when:
        psm.loadBuildConfiguration([simulateOnly     : true,
                                    sonarHost: 'override', 
                                    propDoesntExist: 'missing'
        ])
        then:
        psm.configuration.simulateOnly
        psm.configuration.sonarHost == 'override'
    }

}
