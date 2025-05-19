package com.cigna.state

import com.cigna.base.PipelineMetadata
import com.cigna.common.compliance.ComplianceValidator
import com.cigna.common.compliance.GoalsConfig
import com.cigna.common.naming.NameRegistry
import com.cigna.common.phases.PodSelector
import com.cigna.common.utils.FeatureFlags
import com.evernorth.cloudnativebuild.config.GlobalModuleManager
import com.evernorth.cloudnativebuild.data.ModuleContractRepository

class PipelineStateContext {
    PipelineMetadata metadata
    PodSelector podSelector
    ModuleContractRepository repository
    ComplianceValidator complianceValidator
    NameRegistry nameRegistry
    GlobalModuleManager globalModuleManager
    GoalsConfig goalsConfig
    Map splunkEvent = [:]

    PipelineStateContext(def script, Map<String, Object> config = [:]) {
        metadata = PipelineMetadata.Instance(script)
        podSelector = PodSelector.Instance(script)
        complianceValidator = ComplianceValidator.Instance(script, config)
        nameRegistry = NameRegistry.Instance()
        globalModuleManager = GlobalModuleManager.Instance(this)
        goalsConfig = GoalsConfig.Instance(['goals': [], 'executors': [:]])
        repository = new ModuleContractRepository(
                globalModuleManager.configuration().environment(script).withDefault {
                    k -> script.env[k] ?: k
                } as Map<String, String>)
        FeatureFlags.setFromMap(config.featureFlags as Map ?: [:])
    }
}
