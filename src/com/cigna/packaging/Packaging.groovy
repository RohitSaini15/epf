package com.cigna.packaging

import com.cigna.base.Phase
import com.cloudbees.groovy.cps.NonCPS

/**
 * Abstract base class that defines the contract for packaging strategies.
 */
abstract class Packaging extends Phase {
    Packaging() {
        serviceAccount = 'kaniko'
        groupID = 'package'
    }

    abstract void packageApplication()

    /**
     * This packages an application into the given package type
     */
    void run(
        List credsList = [],
        List configsList = [],
        String packagingPhase = 'non-prod'
    ) {
        script.withCredentials(credsList) {
            script.configFileProvider(configsList) {
                if (config?.dockerRegistry == 'registry.cigna.com') {
                    packagingPhase = 'prod'
                } else if (config?.dockerRegistry ==~ /.*\.ecr.*\.amazonaws.com/) {
                    if (config?.sdlcEnvironment == 'prod') {
                        packagingPhase = 'prod'
                    }
                }
                script.stage(generateStageName("Package Step: ${packagingPhase}")) {
                    script.dir(baseDirectory) {
                        packagingSteps()
                    }
                }
            }
        }
    }

    /**
     * Pre package stage that executes commonPrePackageSteps method
     */
    void executePrePackageStage() {
        commonPrePackageSteps()
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
     * Steps that are common to all Packaging implementations. Currently includes:
     *   - SCM checkout
     *   - Unpack Stash
     */
    protected void commonPrePackageSteps() {
//        Always called after moveFiles which will handle stash/unstash
//        boolean isStashEnabled = config.get('stashEnabled', config.get('phaseCache', true))
//        if (isStashEnabled && script.env.STASH_ID && script.env.STASH_ID != null && !script.env.STASH_ID.empty) {
//            script.echo("unstashing from stash id: ${script.env.STASH_ID}")
//            script.unstash(script.env.STASH_ID)
//        } else {
//            script.echo('Not unstashing')
//        }
    }

    protected void packagingSteps() {
        moveFiles('begin')
        executePrePackageStage()
        packageApplication()
        moveFiles('end')
    }
}
