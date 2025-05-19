package com.cigna.common.phases

import com.cigna.base.Phase
import com.cigna.common.utils.CheckpointUtils
import com.cigna.common.utils.FeatureFlags
import com.cigna.common.utils.Utils
import com.cigna.modules.MetadataEntry
import com.cigna.state.PipelineStateContext
import hudson.AbortException
import hudson.Functions

class PhaseExecution {

    static void executePhase(Object script, Map<String, Object> phase, PipelineStateContext psc, List outerCreds = [], List outerConfigs = []) {
        List pCreds = phase?.extraCredentials ?: []
        List pConfigs = phase?.extraConfigs ?: []

        def withCredsList = pCreds + (outerCreds ?: [])
        def withConfigFilesList = pConfigs + (outerConfigs ?: [])

        psc.complianceValidator.setState(phase.phaseInstance, 'state', 'Running')
        String simpleName = phase.phaseInstance.getClass().simpleName.split(
            '(?<=[a-z])(?=[A-Z])'
        ).join(' ')
        try {
            List varsList = []
            Map currentEnv = script.env.getEnvironment()
            currentEnv.findAll { (it.key as String).contains('.') }.each { key, value ->
                def sanitizedKey = Utils.dotsToUnderscores(key as String)
                varsList += "${sanitizedKey}=${value}"
            }
            varsList += "PHASE_NAME=${phase?.phaseName ?: simpleName}"
            script.withEnv(varsList) {
                script.ansiColor('xterm') {
                    script.echo("---> START PHASE '${script.env.PHASE_NAME}'")
                    psc.podSelector.select(psc, phase.phaseInstance.podTemplateContainerName, Utils.cloud(phase)) {
                        if (phase.beforePhase?.closure) {
                            phase.beforePhase.closure()
                        }
                        if (phase.beforePhase?.script) {
                            script.stage('Before-phase script') {
                                script.sh(phase.beforePhase.script)
                            }
                        }
                        (phase.phaseInstance as Phase).loadEnvProperties()
                        phase.phaseInstance.run(withCredsList, withConfigFilesList)
                        if (phase.afterPhase?.closure) {
                            phase.afterPhase.closure()
                        }
                    }
                    script.echo("<--- END PHASE '${script.env.PHASE_NAME}'")
                }
            }
        } catch (e) {
            if (FeatureFlags.showStackTraces) {
                script.echo(Functions.printThrowable(e))
            }
            psc.complianceValidator.setState(phase.phaseInstance, 'state', 'Failure')
            // if exception message is generic, add on the phase name
            def newEx = e.localizedMessage?.startsWith("script returned exit code") ?
                new AbortException("${phase.phaseInstance.displayName()}: ${e.localizedMessage}") : e
            script.echo(psc.complianceValidator.outputFormatted(newEx.localizedMessage))
            throw newEx
        } finally {
            phase.phaseInstance.sendTailoredEmail()
        }
        psc.complianceValidator.setState(phase.phaseInstance, 'state', 'Success')
    }

    /**
     * Run the phases in the order in which they were given. If the user provides an extraCredentials
     * key to their phase then apply those credentials with the withCredentials step
     *
     * @param script The jenkins pipeline object
     * @param psc The PSC for the phases
     * @param phasesToRun The phases that should be run (these phases should already be loaded/validated)
     * @param config The pipeline-level config
     */
    static void runPhases(Object script, PipelineStateContext psc, List<Map<String, Object>> allPhases, Map<String, Object> config) {
        // If pods are organized in to multiple pod groups (or clouds) we partition the phases so that they are all grouped
        // together. this ensures that the podCloudStack will reflect the correct pod so that we don't constantly create
        // new pods for each phase.
        psc.podSelector.calculatePodTemplates(config, allPhases)
        def groups = psc.podSelector.byPodGroup()
        showLogicalNameMappings(script, psc)
        script.echo(Utils.explainExecutionPlan(psc, groups, allPhases))
        groups.each { podGroup ->
            def phasesToRun = allPhases.findAll { Utils.cloud(it) == podGroup }
            if (phasesToRun.size() > 0) {
                if (CheckpointUtils.isCheckpointPhase(podGroup)) {
                    CheckpointUtils.executeCheckpoint(phasesToRun)
                } else {
                    psc.podSelector.podTemplate(psc, podGroup) {
                        script.echo("Starting pod group '$podGroup' with ${phasesToRun.size()} phase${phasesToRun.size() == 1 ? '' : 's'}")
                        List cCreds = config?.extraCredentials ?: []
                        List cConfigs = config?.extraConfigs ?: []
                        for (phase in phasesToRun) {
                            executePhase(script, phase, psc, cCreds, cConfigs)
                        }
                    }
                    if (FeatureFlags.injectCheckpoints) {
                        script.checkpoint("After PodGroup: $podGroup")
                    }
                }
            }
        }

        if (FeatureFlags.debug) {
            StringBuilder builder = Utils.StringBuilder()
            builder.append("Pipeline Metadata contains ${psc.metadata.size()} entries:\n")
            psc.metadata.eachEntry { MetadataEntry metadata ->
                builder.append(
                    "  KEY [${metadata.prettyKey()}]  " +
                        "VALUE [${metadata.printableValue()}]\n"
                )
            }

            script.echo(builder.toString())
        }
    }

    private static void showLogicalNameMappings(Object script, PipelineStateContext psc) {
        if (FeatureFlags.verbose) {
            def output = "Logical container names => Actual container names:\n"
            output += psc.nameRegistry.allNames().collect {
                "\t${it.key} => ${it.value}"
            }.join("\n")
            script.echo("$output")
        }
    }
}
