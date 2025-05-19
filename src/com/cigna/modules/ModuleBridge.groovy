package com.cigna.modules

import com.cloudbees.groovy.cps.NonCPS
import com.evernorth.cloudnativebuild.model.Credential
import com.evernorth.cloudnativebuild.model.ModuleContract
import com.evernorth.cloudnativebuild.service.ModuleUtil

/**
 * Bridges settings from modules to conduit orchestration settings.
 *
 * Some of the settings that are specified in module configuration
 * are managed by the orchestration engine and not the modules themselves.
 * This class manages the bridge between modules and EPF orchestration.
 */
@SuppressWarnings(['UnnecessaryObjectReferences', 'CyclomaticComplexity'])
class ModuleBridge {
    ModuleContract moduleContract = new ModuleContract()
    private final Object script
    private final Map<String, Object> config

    @NonCPS
    Map getReleaseVariables(String rollbackDeploymentPackagePath = '') {
        Map map = [:]
        map['Release_JenkinsMaster'] = ModuleUtil.getMasterName(script)
        map['Release_BuildUri'] = script.env.BUILD_URL
        map['Release_rollbackDeploymentPackagePath'] = rollbackDeploymentPackagePath
        map['branchName'] = script.env.BRANCH_NAME
        map['repoUrl'] = script?.scm?.userRemoteConfigs[0]?.url ?: ''

        map
    }

    Map postProcess() {
        ['commitId': script.env.GIT_COMMIT]
    }

    ModuleContract parse(ModuleContract moduleToRun, Map<String, Object> moduleSettings) {
        moduleContract.contractName = moduleToRun?.contractName ?: ''
        moduleContract.stageName = moduleToRun?.stageName ?: moduleToRun.moduleName
        moduleContract.moduleName = moduleToRun.moduleName
        moduleContract.commandName = moduleToRun.commandName
        moduleContract.subCommand = moduleToRun.subCommand
        moduleContract.image = moduleToRun.image
        if (!moduleContract.moduleName || moduleContract.moduleName?.isEmpty()) {
            moduleContract.moduleName = moduleToRun.commandName.takeWhile { it != '.' }
        }
        moduleContract.credentials = moduleToRun?.credentials ?: new Credential[0]

        moduleContract.requiresStash = moduleSettings?.requiresStash?.asBoolean() ?: false
        moduleContract.requiresUnStash = moduleSettings?.requiresUnStash?.asBoolean() ?: false
        moduleContract.stashName = moduleSettings?.stashName ?: ''
        moduleContract.unStashName = moduleSettings?.unStashName ?: ''
        moduleContract.stashPattern = moduleSettings?.stashPattern ?: ''
        moduleContract.excludesPattern = moduleSettings?.excludesPattern ?: ''
        moduleContract.preflightSubCommand = moduleSettings?.preflightSubCommand ?: ''
        parseCredentials(moduleSettings)

        moduleContract

    }

    private void parseCredentials(Map<String, Object> moduleSettings) {
        if (moduleSettings.containsKey('credentials')) {
            if (isValidCredentialList(moduleSettings)) {
                List credMap = moduleSettings['credentials'] as List
                int size = credMap.size()
                if (isValidCredentialMap(size, credMap)) {
                    createMappedCredentials(credMap)
                } else {
                    script.echo("WARNING, you have declared a non-map type credential inside the 'credentials' (${moduleSettings['credentials']}) " +
                        "element for module '${moduleContract.moduleName}'")
                    moduleContract.credentials = []
                }
            } else {
                script.echo("WARNING, you have declared a non-list type 'credentials' (${moduleSettings['credentials']}) " +
                    "element inside your '${moduleContract.moduleName}' module definition")
                moduleContract.credentials = []
            }
        }
    }

    @NonCPS
    private void createMappedCredentials(List credMap) {
        moduleContract.credentials = Credential.convertToArrayListOfCredentials(credMap).toArray(new Credential[credMap.size()])
    }

    @NonCPS
    private boolean isValidCredentialMap(int size, List credMap) {
        return size > 0 && credMap[0] instanceof Map
    }

    @NonCPS
    private boolean isValidCredentialList(Map<String, Object> moduleSettings) {
        return moduleSettings['credentials'] instanceof List
    }

    ModuleBridge(def script, def config = [:]) {
        this.script = script
        this.config = config
    }
}
