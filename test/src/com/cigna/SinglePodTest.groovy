package com.cigna

import com.cigna.base.ValidPhase
import com.cigna.common.scm.CommonGit
import com.cigna.common.utils.FeatureFlags
import com.cigna.mocks.MockScript
import com.cigna.state.PipelineStateContext
import com.cigna.jenkins_spock.JenkinsPipelineSpecification
import spock.lang.Shared

class SinglePodTest extends JenkinsPipelineSpecification {
    @Shared
    def script
    @Shared
    PipelineStateContext psc

    def init() {
        FeatureFlags.podAutotuning.enabled = false
    }

    def initScriptAndPsc(def localScript = null, def config = [:], def complianceValidator = null) {
        init()
        this.script = localScript ?: new Script()
        psc = new PipelineStateContext(this.script, config)
        CommonGit cg = Mock()
        if (complianceValidator) {
            psc.complianceValidator = complianceValidator
        }
        psc.podSelector.commonGit = cg

    }

    class Script extends MockScript {}

    void simulatePodTemplate(PipelineStateContext psc, def phase, String cloudName = 'test-cloud') {
        init()
        phase.config.phaseInstance = phase
        psc.podSelector.calculatePodTemplates([cloudName: cloudName], [phase.config])

        // when the initial base pod is created, its cloudName is pushed to the stack
        psc.podSelector.podCloudStack.push(cloudName)
    }

    void simulatePodTemplates(PipelineStateContext psc, List phases, String cloudName = 'test-cloud') {
        init()
        phases.each { phase ->
            phase.config.phaseInstance = phase
        }

        psc.podSelector.calculatePodTemplates([cloudName: cloudName], phases*.config)
        // when the initial base pod is created, its cloudName is pushed to the stack
        psc.podSelector.podCloudStack.push(cloudName)
    }

    void simulatePodTemplate(PipelineStateContext psc, String cloudName = 'test-cloud') {
        init()
        def instance = new ValidPhase(psc: psc)
        def phase = [
            config: [
                phaseInstance: instance
            ]]
        psc.podSelector.calculatePodTemplates([cloudName: cloudName], [phase.config])
        // when the initial base pod is created, its cloudName is pushed to the stack
        psc.podSelector.podCloudStack.push(cloudName)
    }
}
