package com.evernorth.cloudnativebuild.config

import com.cigna.state.PipelineStateContext
import com.cloudbees.groovy.cps.NonCPS
import com.evernorth.cloudnativebuild.model.BuildConfiguration
import com.evernorth.cloudnativebuild.service.PipelineStateManager


class GlobalModuleManager implements Serializable {
    private PipelineStateManager _pipelineState = null
    private BuildConfiguration _buildConfig = null
    private PipelineStateContext psc

    // Necessary in order to disentangle hidden global state
    private GlobalModuleManager(def psc) {
        this.psc = psc
    }

    @NonCPS
    PipelineStateManager stateManager() {
        _pipelineState ? _pipelineState : (_pipelineState = new PipelineStateManager(this.configuration()))
    }

    @NonCPS
    static GlobalModuleManager Instance(def psc) {
        new GlobalModuleManager(psc)
    }

    @NonCPS
    BuildConfiguration configuration() {
        _buildConfig ? _buildConfig : (_buildConfig = new BuildConfiguration())
    }
}
