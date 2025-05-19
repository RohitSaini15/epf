package com.cigna.base

import com.cigna.common.utils.FeatureFlags
import com.cigna.common.utils.Utils
import com.cigna.modules.MetadataEntry
import com.cloudbees.groovy.cps.NonCPS

/**
 * Provides a caching facility that is intended to allow phases to share metadata.
 * Unlike the phase-cache which allows phases to share files, the pipeline metadata
 * cache lets phases persist otherwise ephemeral data in order for subsequent phases
 * to cache it; an example might be:
 *
 *  [epf-build-maven] stores publishUrl of the uploaded artifact
 *  [cnp-deploy-pcf] retrieves the publishUrl of the uploaded artifact
 */
class PipelineMetadata {
    private static PipelineMetadata instance = null
    private final Object script
    private final Map<String, MetadataEntry> moduleMetadata = [:]

    int size() {
        moduleMetadata.size()
    }

    PipelineMetadata eachEntry(Closure closure) {
        moduleMetadata.each { entry ->
            closure(entry.value)
        }

        this
    }

    PipelineMetadata put(String key, Object value, String moduleName = '', boolean isSensitive = false) {
        MetadataEntry entry = Utils.MetadataEntry(key, value, moduleName, isSensitive)
        if (entry != moduleMetadata[entry.prettyKey()]) {
            moduleMetadata[entry.prettyKey()] = entry

            if (FeatureFlags.debug) {
                script.echo("inject key ${entry.prettyString()}")
            }
        }
        this
    }

    @NonCPS
    Object get(String key, Object defaultValue = null) {
        MetadataEntry entry = null
        if (moduleMetadata.containsKey(key)) { // match qualified key
            entry = moduleMetadata[key]
            if (FeatureFlags.debug) {
                script.echo("get module metadata: ${key} = ${entry.printableValue()}")
            }
        } else {// match unqualified key and return the first occurrence.
            moduleMetadata.each {
                if (it.key.endsWith(":${key}")) {
                    entry = it.value
                    if (FeatureFlags.debug) {
                        script.echo("get module metadata: ${key} = ${entry.printableValue()}")
                    }
                    return true
                }
            }
            if (entry == null) script.echo("get module metadata: no metadata found for ${key}")
        }
        entry ? entry.value : defaultValue
    }

    @NonCPS
    static PipelineMetadata Instance(Object script = null) {
        new PipelineMetadata(script)
    }

    static void Reset() {
        instance?.moduleMetadata?.clear()
        instance = null
    }
    
    private PipelineMetadata(Object script) {
        this.script = script
    }
}
