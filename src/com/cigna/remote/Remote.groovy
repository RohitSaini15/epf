package com.cigna.remote

import com.cigna.base.Phase
import com.cloudbees.groovy.cps.NonCPS

/**
 * Abstract base class that defines the contract for remote job strategies
 */
abstract class Remote extends Phase {

    protected Map<String, String> remoteConfiguration
    Remote() {
        baseValidationItems = ['remoteType']
        groupID = 'remote'
    }

    /*
     * Overriding validate to require branch pattern
     */
    @Override
    @NonCPS
    List validate(boolean requiresBranchPattern = false, Phase phase = this) {
        List versionIssues = []
        boolean requireBranchPattern = true
        versionIssues.addAll(super.validate(requireBranchPattern, phase))
        versionIssues
    }

    /**
     * Encompasses the steps to take when running the given remote type
     */
    abstract void run()
}
