package com.cigna.common.utils


import com.cigna.state.PipelineStateContext
import hudson.Functions

class StashUtils {
    private static final String stashingNotRequired = "Stashing is enabled, but not required, skipping"
    
    static void unstash(def script, PipelineStateContext psc) {
        if (script.env.STASH_ID) {
            if (FeatureFlags.skipStashingWhenSinglePod && !psc.podSelector.hasMultiplePods()) {
                script.echo(stashingNotRequired)
            } else {
                script.echo("Unstashing files from stashid '${script.env.STASH_ID}'")
                script.unstash(name: script.env.STASH_ID)
            }
        } else {
            script.echo("No stash id active, not unstashing")
        }
    }

    /**
     * This method will stash built artifacts to be used in subsequent stages
     *
     * @return The stash identifier
     */
    static String stash(def script, def config, PipelineStateContext psc, String stashIncludePattern='', String stashExcludePattern='') {
        boolean stashEnabled = config.get('stashEnabled', config.get('phaseCache', true))
        String stashId = normalize(config?.stashName ?: script.env.JOB_NAME ?: "stash-${UUID.randomUUID()}")
        String stashIncludes = config?.stashIncludePattern ?: stashIncludePattern
        String stashExcludes = config?.stashExcludePattern ?: stashExcludePattern

        try {
            if (stashEnabled) {
                if (FeatureFlags.skipStashingWhenSinglePod && !psc.podSelector.hasMultiplePods()) {
                    script.echo(stashingNotRequired)
                    return null
                }
                if (config?.preserveStashes) {
                    script.preserveStashes()
                }
                script.echo("Stash includes: ${stashIncludes}, excludes: ${stashExcludes} - ${stashId}")
                script.stash(
                        name: stashId, includes: stashIncludes, excludes: stashExcludes,
                        allowEmpty: true, useDefaultExcludes: true
                )
                return stashId
            }
        } catch (all) {
            script.echo("Failed to stash: ${all.localizedMessage}")
            if (FeatureFlags.showStackTraces) {
                script.echo(Functions.printThrowable(all))
            }
            throw all
        }
        script.echo('Not stashing')
        null
    }

    static String normalize(String value) {
        value.replaceAll(/[\/.%\u0024\[\]&^@(),-]/, '_')
    }
}
