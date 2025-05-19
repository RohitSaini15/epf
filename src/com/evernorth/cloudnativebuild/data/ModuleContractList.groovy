package com.evernorth.cloudnativebuild.data

import com.evernorth.cloudnativebuild.config.ReleaseConstants
import com.evernorth.cloudnativebuild.model.ModuleContract
import com.evernorth.cloudnativebuild.model.ModuleContractType


class ModuleContractList implements Serializable {
    /**
     * List of plugin contracts supported by the Pipeline and default values for each contract type.
     * These values are merged with the values passed in to the module configuration
     */
    static def contracts = [
        new ModuleContract(contractName: ModuleContractType.PIPELINE_STATE_WRITER),
        new ModuleContract(
            contractName: ModuleContractType.CONFIG
        ),
        new ModuleContract(
            contractName: ModuleContractType.SCM_CHECKOUT
        ),
        new ModuleContract(
            contractName: ModuleContractType.EVENT
        ),
        new ModuleContract(
            contractName: ModuleContractType.NOTIFY
        ),
        new ModuleContract(
            contractName: ModuleContractType.PUBLISH_IMAGE
        ),
        new ModuleContract(
            contractName: ModuleContractType.PREFLIGHT_CHECK
        ),
        new ModuleContract(
            contractName: ModuleContractType.BUILD,
            outputDirectory: "target",
        ),
        new ModuleContract(
            contractName: ModuleContractType.PUBLISH,
            subCommand: "publish",
            requiresUnStash: true,
            unStashName: PipelineConstants.STASH_RELEASE_FILES
        ),
        new ModuleContract(
            contractName: ModuleContractType.RETRIEVE,
            requiresUnStash: true,
            unStashName: PipelineConstants.STASH_RELEASE_FILES
        ),
        new ModuleContract(
            contractName: ModuleContractType.PACKAGE

        ),
        new ModuleContract(
            contractName: ModuleContractType.CONTAINER

        ),
        new ModuleContract(
            contractName: ModuleContractType.PROVISION
        ),
        new ModuleContract(
            contractName: ModuleContractType.DEPLOY
        ),
        new ModuleContract(
            contractName: ModuleContractType.SECURITY_SCAN
        ),
        new ModuleContract(
            contractName: ModuleContractType.CUTOVER
        ),
        new ModuleContract(
            contractName: ModuleContractType.QUALITY_CHECK
        ),
        new ModuleContract(
            contractName: ModuleContractType.TEST
        ),
        new ModuleContract(
            contractName: ModuleContractType.RELEASE,
            commandName: "cnp-release-xlr.sh",
            moduleName: ReleaseConstants.RELEASE_MODULE_NAME
        ),
        new ModuleContract(
            contractName: ModuleContractType.FINALIZE_RELEASE,
            commandName: "cnp-release-finalize.sh",
            requiresUnStash: true,
            unStashName: PipelineConstants.STASH_SOURCE_FULL
        ),
        new ModuleContract(
                contractName: ModuleContractType.OTHER
        ),
        new ModuleContract(
                contractName: ModuleContractType.APPROVAL
        )
    ]
}
