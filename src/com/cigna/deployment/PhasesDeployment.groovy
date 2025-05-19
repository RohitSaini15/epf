package com.cigna.deployment

import com.cigna.common.phases.PhaseExecution
import com.cigna.common.utils.Utils

class PhasesDeployment extends Deployment {
    
    PhasesDeployment() {
        deployStatus = 'Deploy'
    }

    private def phasesMatchingPattern() {
        config.phases.findAll {
            currentBranchMatches(it.branchPattern)
        }
    }

    @Override
    void init() {
        phasesMatchingPattern().each { phase ->
            phase.phaseInstance.init()
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
    void deploy() {
        phasesMatchingPattern().each { Map phase ->
            phase.cloudName = Utils.cloud(config)
            PhaseExecution.executePhase( script, phase, psc, this.getCredsList(), this.getConfigsList())
        }
    }
}
