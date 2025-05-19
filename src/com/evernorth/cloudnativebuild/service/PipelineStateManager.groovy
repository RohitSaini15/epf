package com.evernorth.cloudnativebuild.service

import com.cigna.common.utils.FeatureFlags
import com.cigna.common.utils.Utils
import com.cigna.modules.Module
import com.cloudbees.groovy.cps.NonCPS
import com.evernorth.cloudnativebuild.config.GlobalModuleManager
import com.evernorth.cloudnativebuild.data.ModuleContractList
import com.evernorth.cloudnativebuild.data.PipelineConstants
import com.evernorth.cloudnativebuild.data.PipelineEventType
import com.evernorth.cloudnativebuild.model.*
import com.evernorth.cloudnativebuild.model.logging.LogLevel
import com.evernorth.cloudnativebuild.pipeline.steps.StoreReleaseMetadataStep
import groovy.json.JsonBuilder
import groovy.json.JsonSlurperClassic
import hudson.Functions

/***
 * Manages pipeline plugin and generates pod templates used for steps
 */
class PipelineStateManager implements Serializable {
    static final String ERROR_INVALID_PLUGIN_DESCRIPTOR = "PSM: Invalid module descriptor. Module Descriptors should be in the format ['contractName', 'image path']"
    static final String STATE_FILE_NAME = PipelineConstants.RELEASE_METADATA_FILE
    static final String STATE_FILE_STASH_NAME = "pipelinestate"
    private List<ModuleContract> moduleContracts = []
    private List<StepInvocation> pipelineSteps = []
    int cnpNodeCount = 0

    // bad global state
    public PipelineStateManager(def conf=null) {
        configuration = conf
    }
    final static String podLabel(def script) {
        Utils.randomPodLabel(script.env.JOB_NAME)
    }
    String deploymentPackagePath
    String rollbackDeploymentPackagePath
    BuildConfiguration configuration
    Map releaseArguments = [:]

    String getRollbackDeploymentPackagePath() {
        return rollbackDeploymentPackagePath
    }

    void setRollbackDeploymentPackagePath(String rollbackDeploymentPackagePath) {
        this.rollbackDeploymentPackagePath = rollbackDeploymentPackagePath
        releaseArguments.put("Release_rollbackDeploymentPackagePath", rollbackDeploymentPackagePath)
    }

    /**
     * This filter is set when a block of code is wrapped
     * in a withModule closure
     */
    private String moduleFilter = ""

    String getGlobalModuleFilter() {
        return moduleFilter
    }

    /**
     * Called when entering withModule block, adds a global module filter
     * @param moduleName
     */
    @NonCPS
    void enableGlobalModuleFilter(String moduleName) {
        if (!moduleName || !moduleContracts.find { it.moduleName == moduleName }) {
            throw new IllegalArgumentException("Invalid module name ${moduleName} specified in withModule block")
        }
        moduleFilter = moduleName
    }

    /**
     * Called when exiting withModule block, it clears filter
     */
    void disableGlobalModuleFilter() {
        moduleFilter = ""
    }

    @NonCPS
    String getDeploymentPackagePath() {
        return deploymentPackagePath
    }

    void setDeploymentPackagePath(String deploymentPackagePath) {
        this.deploymentPackagePath = deploymentPackagePath
    }

    // Not actually a CPS problem child,but has to be marked thusly because it's called
    // by a legitimately non-cps method
    @NonCPS
    int addPipelineStep(StepInvocation step) {
        if (!step) return 0
        step.order = pipelineSteps.size() + 1
        pipelineSteps.add(step)
        return step.order
    }

    void saveStateToFile(def scriptContext) {
        Logger logger = newLogger(scriptContext)
        ReleaseInfo releaseConfiguration = StoreReleaseMetadataStep.createReleaseConfiguration(scriptContext, this, releaseArguments, "", "", "", "")
        try {
            logger.log("Creating JSON", LogLevel.TRACE)
            String jsonData = serializeReleaseConfiguration(releaseConfiguration)
            logger.log(jsonData, LogLevel.TRACE)
            scriptContext.writeFile(file: STATE_FILE_NAME, text: jsonData)
            scriptContext.stash(name: STATE_FILE_STASH_NAME, includes: STATE_FILE_NAME)
        }
        catch (ignored) {
            if (FeatureFlags.showStackTraces) {
                logger.log(Functions.printThrowable(ignored))
            }
            logger.log("Could not save state file: $STATE_FILE_NAME", LogLevel.WARNING)
        }
    }

    @NonCPS
    private Logger newLogger(scriptContext) {
        return new Logger(scriptContext)
    }

    @NonCPS
    private static String serializeReleaseConfiguration(ReleaseInfo releaseInfo) {
        return new JsonBuilder(releaseInfo).toPrettyString()
    }

    void recoverStateFromFile(def scriptContext) {
        if (moduleContracts.size() == 0) {
            scriptContext.echo("Looks like we lost state, attempting to load from $STATE_FILE_NAME")
            // restore from stash
            scriptContext.unstash(name: STATE_FILE_STASH_NAME)
            // read the file
            def jsonData
            try {
                jsonData = scriptContext.readFile(file: "$STATE_FILE_NAME") as String
            } catch (ignored) {
                if (FeatureFlags.showStackTraces) {
                    scriptContext.echo(Functions.printThrowable(ignored))
                }
                scriptContext.echo("Could not find $STATE_FILE_NAME")
                return
            }


            try {
                // load the config
                JsonSlurperClassic slurper = new JsonSlurperClassic()
                def data = slurper.parseText(jsonData)
                if (!data?.moduleContractList) {
                    throw new IllegalArgumentException("moduleContractList is missing from $STATE_FILE_NAME")
                }
                // this will allow deployments from callback jobs as branch names will always be null
                loadAllContracts(data?.moduleContractList, false)
                loadBuildConfiguration(data.buildConfiguration)
            }
            catch (ex) {
                if (FeatureFlags.showStackTraces) {
                    scriptContext.echo(Functions.printThrowable(ex))
                }
                throw new Exception("Error parsing data in ${STATE_FILE_NAME}. ${ex.message}")
            }
        }
    }

    @NonCPS
    List<StepInvocation> getPipelineSteps() {
        return pipelineSteps
    }

    @NonCPS
    void updatePipelineStep(StepInvocation step) {
        if (!step) throw new IllegalArgumentException("Cannot update. StepInvocation was null")
        StepInvocation stepToUpdate = pipelineSteps.find { it.order == step.order }
        if (stepToUpdate) {
            pipelineSteps.removeAll { it.order == step.order }
            pipelineSteps.add(step)
        } else {
            throw new IllegalArgumentException("step with order: ${step.order} was not found")
        }
    }

    @NonCPS
    void loadConfigurationFromFile(String jsonData, boolean allowNonStandardDeployments = true) {
        JsonSlurperClassic slurper = new JsonSlurperClassic()
        try {
            def data = slurper.parseText(jsonData)
            if (!data?.moduleContractList || !data?.steps) {
                throw new IllegalArgumentException("moduleContractList and steps are missing from releaseInfo")
            }
            // since we're loading contracts from a release state file, we'll clear out the existing contracts
            moduleContracts.clear()
            // this will allow deployments from callback jobs as branch names will always be null
            loadAllContracts(data?.moduleContractList, allowNonStandardDeployments)
            loadStepsFromFile(data?.steps)
            loadBuildConfiguration(data.buildConfiguration)
            loadReleaseArgumentsFromFile(data.releaseArguments)
        }
        catch (ex) {

            throw new Exception("Error parsing data in ${PipelineConstants.RELEASE_METADATA_FILE}. ${ex.message}")
        }
    }

    @NonCPS
    void loadAllContracts(List moduleContracts, boolean allowNonStandardDeployments) {
        moduleContracts.each { def contractAsMap ->
            ModuleContract convertedContract = convertMapToModuleContract(contractAsMap)
            convertedContract?.allowNonStandardDeployment = allowNonStandardDeployments
            // if contract type is deploy, then unstash should be enabled from release stash for callback jobs to get the configuration files like manifest.yaml, release info, etc
            // this should be revisited
            if (convertedContract?.contractName?.equals(ModuleContractType.DEPLOY.name())) {
                convertedContract?.requiresUnStash = true
                convertedContract?.unStashName = PipelineConstants.STASH_RELEASE_FILES
            }
            add(convertedContract)
        }
    }

    @NonCPS
    void loadBuildConfiguration(def buildConfig) {
        BuildConfiguration importedConfig = buildConfig as BuildConfiguration
        if (importedConfig != null) {
            this.configuration = importedConfig
        }
    }

    @NonCPS
    private void loadStepsFromFile(def stepsObject) {
        stepsObject.each {
            def step = it as StepInvocation
            pipelineSteps.add(step)
        }
    }

    @NonCPS
    void add(Map pluginContract) {
        ModuleContract convertedContract = convertMapToModuleContract(pluginContract)
        add(convertedContract)
    }

    @NonCPS
    void addList(List moduleContracts) {
        moduleContracts.each { newContract ->
            add(newContract)
        }
    }


    @NonCPS
    void add(ModuleContract contract) {
        if (contract == null) return

        if (contract.moduleName == "") {
            if (contract.commandName?.indexOf(".") > 0) {
                contract.moduleName = contract.commandName.substring(0, contract.commandName.indexOf("."))
            } else {
                contract.moduleName = contract.commandName
            }

        }
        if (contract.logFileName == PipelineConstants.LOG_FILE_POSTFIX) {
            contract.logFileName = "${contract.moduleName}${PipelineConstants.LOG_FILE_POSTFIX}"
        }
        if (moduleContracts.contains(contract)) return
        moduleContracts.add(contract)
    }

    /**
     * Gets list of contacts for a given type
     * @param contractType - A string that should match one of the constants defined in PluginContracts type
     * @return A list of plugin contracts that match contractType or null if none are found
     */
    @NonCPS
    List<ModuleContract> getContractsForContactType(String contractType) {
        return moduleContracts.findAll { it.contractName == contractType }
    }

    @NonCPS
    ModuleContract getFirstContractForContactType(String contractType) {
        return moduleContracts.find { it.contractName == contractType }
    }
    /**
     * Returns all contracts that can be used for deployment and rollback
     * @return
     */
    @NonCPS
    List<ModuleContract> getDeploymentContracts() {
        return moduleContracts.findAll { it.contractName in [String.valueOf(ModuleContractType.DEPLOY), String.valueOf(ModuleContractType.CUTOVER)] }
    }

    /**
     * clearModules - Removes all module contracts
     */
    @NonCPS
    void clearModules() {
        moduleContracts.clear()
    }

    /**
     * Allows module configuration to by updated. This is useful for when we loaded modules
     * from a module template and need to customize the credentials or stash settings
     * @param moduleName - The name of the module
     * @param patch - Map that contains the values that need to change.
     */
    @NonCPS
    void patchModule(String moduleName, String contractName = "", Map patch) {
        List<ModuleContract> contracts = ( contractName != "" ) ? contracts.findAll { ( it.moduleName.toLowerCase() == moduleName.toLowerCase() && it.contractName == contractName.toUpperCase() ) } : contracts.findAll { it.moduleName.toLowerCase() == moduleName.toLowerCase() }
        if (!contracts) throw new IllegalArgumentException("No module named $moduleName found in configuration.")
        contracts.each { contract ->
            patch.each { prop ->
                if (!contract.hasProperty(prop.key as String)) {
                    throw new IllegalArgumentException("Invalid property name: ${prop.key}.")
                }
                if (prop.key == CREDENTIALS) {
                    contract.setProperty(CREDENTIALS, mapCredentials(contract, prop.value))
                } else {
                    contract.setProperty(prop.key as String, prop.value)
                }
            }
        }
    }
    static final String CREDENTIALS = "credentials"
    /**
     * Pulls data in Map from a Jenkins file, and finds a matching plugin-contract that
     * @param moduleContract
     * @return
     */
    @NonCPS
    static ModuleContract convertMapToModuleContract(def moduleContract) {
        if (moduleContract instanceof ModuleContract) return moduleContract
        if (moduleContract == null || ( !validateModuleContract(moduleContract) )) {
            throw new IllegalArgumentException(ERROR_INVALID_PLUGIN_DESCRIPTOR + ": ${moduleContract}")
        }
        // try and find matching contract from list of supported module contracts
        ModuleContract contractTemplate = getModuleFromContractTypes(moduleContract.contractName)?.clone() as ModuleContract

        if (contractTemplate == null) {
            throw new IllegalArgumentException("The module ${moduleContract.contractName} is not a supported module contract.")
        }
        def propMap = new Properties()

        contractTemplate.getProperties().each { key, value ->
            propMap.setProperty(key.toString().toLowerCase(), key.toString())
        }
        moduleContract.each { key, value ->
            // Use lowercase property name from map to get actual property name in BuildConfiguration
            String propertyName = propMap.getProperty(key.toString().toLowerCase())
            if (propertyName && contractTemplate.hasProperty(propertyName)) {
                switch (propertyName) {
                    case CREDENTIALS:
                        contractTemplate.setProperty(propertyName, mapCredentials(contractTemplate, value))
                        break
                    case 'triggeredByEvent':
                        contractTemplate.setProperty(propertyName, mapEventType(value))
                        break
                    default:
                        contractTemplate.setProperty(propertyName, value)
                }
            }
        }
        return contractTemplate
    }

    @NonCPS
    static ArrayList<PipelineEventType> mapEventType(def value) {
        return ( value as List )?.collect {
            it instanceof PipelineEventType ? it : PipelineEventType.valueOf(it as String)
        }
    }

    @NonCPS
    static ArrayList<Credential> mapCredentials(ModuleContract contract, def value) {
        ArrayList<Credential> incomingCredentialList = Credential.convertToArrayListOfCredentials(value)
        if (( !contract.credentials ) || contract.credentials?.size() == 0) {
            // no default credentials configured for modules
            return incomingCredentialList
        }
        if (incomingCredentialList.size() == 0) { // no credentials supplied from Jenkinsfile
            return contract.credentials
        }
        return consolidateCredentials(contract, incomingCredentialList)
    }

    /**
     * Attempts to find a matching contact type in the ModuleContractList
     * @param name - the Name of the contact type
     * @return Module Contract
     */
    @NonCPS
    static ModuleContract getModuleFromContractTypes(String name) {
        def contact = ModuleContractList.contracts.find { it.contractName == name }
        return contact
    }

    @NonCPS
    static boolean validateModuleContract(def pluginContract) {
        if (pluginContract != null &&
            pluginContract instanceof Map && pluginContract.get("contractName") != null) return true
        return false
    }


    @NonCPS
    List<ModuleContract> getContracts(String contractName, String moduleName = "") {
        if (!moduleName) {
            return moduleContracts.findAll({ item -> item.contractName == contractName })
        }
        return moduleContracts.findAll({ item -> item.contractName == contractName && item.moduleName == moduleName })
    }


    @NonCPS
    ModuleContract getContractByTypeAndModuleName(ModuleContractType type, String moduleName) {
        if (!moduleName) {
            return moduleContracts.find(
                {
                    item -> item.contractName == type.name()
                })
        }
        return moduleContracts.find(
            {
                item -> item.contractName == type.name() && item.moduleName?.toLowerCase()?.trim() == moduleName?.toLowerCase()?.trim()
            }
        )
    }

    @NonCPS
    List<ModuleContract> getContracts() {
        return moduleContracts
    }

    @NonCPS
    static ArrayList<Credential> consolidateCredentials(ModuleContract originalContract, ArrayList<Credential> addonCredential) {
        ArrayList<Credential> credentialList = new ArrayList<Credential>()
        credentialList.addAll(originalContract.credentials)
        originalContract.credentials.each { originalCred ->
            addonCredential.each { addonCred ->
                if (originalCred.isReplaceable(addonCred)) {
                    credentialList.remove(originalCred)
                    credentialList.add(addonCred)
                    return
                } else {
                    if (!credentialList.contains(addonCred)) credentialList.add(addonCred)
                }
            }
        }
        return credentialList
    }

    @NonCPS
    String printModuleList() {
        StringBuilder builder = new StringBuilder()
        builder.append("========== MODULE INFO ==========")
        moduleContracts.each {
            builder.append(Module.buildDescription(it))
        }
        return builder.toString()
    }

    @NonCPS
    void loadReleaseArgumentsFromFile(def releaseArguments) {
        Map importedReleaseArguments = releaseArguments as Map
        if (importedReleaseArguments != null) {
            this.releaseArguments = importedReleaseArguments
        }
    }
}