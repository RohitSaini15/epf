package com.evernorth.cloudnativebuild.pipeline.steps

import com.cigna.common.phases.PodSelector
import com.cigna.common.utils.FeatureFlags
import com.cigna.state.PipelineStateContext
import com.evernorth.cloudnativebuild.config.ReleaseConstants
import com.evernorth.cloudnativebuild.pipeline.PipelineUtils
import com.evernorth.cloudnativebuild.service.Logger
import com.evernorth.cloudnativebuild.service.PipelineStateManager

class CheckoutFromScmStep {
    def scriptContext
    Logger logger

    PipelineStateContext psc

    CheckoutFromScmStep(def scriptContext, PipelineStateContext psc) {
        this.scriptContext = scriptContext
        logger = new Logger(this.scriptContext)
        this.psc = psc
    }
    /**
     * Does checkout and creates a pipeline stage
     * @return true if execute is success
     */
    boolean execute() {
        LoadConfigStep loadConfigStep = new LoadConfigStep(scriptContext)
        loadConfigStep.updateDefaultBuildConfigurationFromEnvironment(psc.globalModuleManager.stateManager().configuration)
        // get the source code from the scm
        scriptContext.stage("SCM Check out") {
            executeLight(scriptContext, psc)

            Map repoInfo = PipelineUtils.getRepoInfo(scriptContext)
            psc.globalModuleManager.stateManager().releaseArguments = repoInfo
            psc.globalModuleManager.stateManager().releaseArguments.put('commitId', scriptContext.sh(returnStdout: true, script: 'git rev-parse HEAD')?.trim())
            // stash files needed for deployment and release
            PipelineUtils.stash(
                scriptContext,
                ReleaseConstants.ReleaseStashName,
                psc.globalModuleManager.stateManager().configuration.releaseStashPattern,
                "",
                logger
            )
        }
        return true
    }

    /**
     * does a bare bones checkout with no bells and whistles
     */
    static void executeLight(def scriptContext, PipelineStateContext psc) {
        if (FeatureFlags.scm.legacyCheckout) {
            scriptContext.checkout(scriptContext.scm)
            scriptContext.sh('git config --global --add safe.directory "\$WORKSPACE"')
        } else {
            psc.podSelector.commonGit.checkout()
        }
    }

}
