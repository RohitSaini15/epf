package com.cigna.parallel

import com.cigna.base.Phase
import com.cigna.common.phases.PhaseExecution
import com.cigna.common.utils.Utils

/**
 * Parallel phase implementation that allows for running of parallel phases
 */
class Parallel extends Phase {

    Parallel() {
        containerImage = 'parallel'
        containerVersion = 'latest'
        containerCpu = '2000m'
        containerMemory = '2000Mi'
        groupID = 'parallel'
    }

    private def phasesMatchingPattern() {
        config.phases.findAll {
            currentBranchMatches(it.branchPattern)
        }
    }
    
    @Override
    void init() {
        phasesMatchingPattern().each {
            it.phaseInstance.init()
        }
        super.init()
    }

    @Override
    Boolean prePodConfig() {
        phasesMatchingPattern().each { phase ->
            phase.phaseInstance.prePodConfig()
        }
        super.prePodConfig()
    }

    @Override
    Boolean postPodConfig() {
        // We do not support running parallel phases in different clouds or pod groups; they must be identical to the
        // parent or epf will not be able to schedule them correctly.
        config.phases = config.phases.collect {
            it.cloudName = config.cloudName
            it.podGroup = config.podGroup
            it
        }
        super.postPodConfig()
    }

    void run(
        List credsList = [],
        List configsList = []
    ) {
        def parallelPhases = [:]
        phasesMatchingPattern().each { phase ->
            parallelPhases[phase.phaseLabel] = {
                if (phase.cloudName != config.cloudName) {
                    phase.cloudName = config.cloudName
                }
                PhaseExecution.executePhase(script, phase, psc, credsList, configsList)
            }
        }

        script.stage(generateStageName(config.parallelType)) {
            psc.podSelector.select(psc, podTemplateContainerName, Utils.cloud(config)) {
                script.withCredentials(credsList) {
                    script.configFileProvider(configsList) {
                        script.dir(baseDirectory) {
                            moveFiles('begin')
                            script.parallel(parallelPhases)
                            moveFiles('end')
                        }
                    }
                }
            }
        }
    }
}
