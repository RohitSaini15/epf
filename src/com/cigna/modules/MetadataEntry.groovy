package com.cigna.modules

import com.cloudbees.groovy.cps.NonCPS

/**
 * Class used to store inter-module/phase state
 */
class MetadataEntry {
    String key
    Object value
    String moduleName
    boolean isSensitive

    MetadataEntry(String key, Object value, String moduleName, boolean isSensitive) {
        this.key = key
        this.value = value
        this.moduleName = moduleName
        this.isSensitive = isSensitive
    }

    @NonCPS
    String prettyKey() {
        return moduleName ? "${moduleName}:${key}" : key
    }

    @NonCPS
    String printableValue() {
        return isSensitive ? "********" : value.toString()
    }

    @NonCPS
    String prettyString() {
        return "${prettyKey()} = ${printableValue()}"
    }

    @NonCPS
    boolean equals(o) {
        if (this.is(o)) return true
        if (o == null || getClass() != o.class) return false

        MetadataEntry that = (MetadataEntry) o

        if (isSensitive != that.isSensitive) return false
        if (key != that.key) return false
        if (moduleName != that.moduleName) return false
        if (value != that.value) return false

        return true
    }

    @NonCPS
    int hashCode() {
        int result
        result = (key != null ? key.hashCode() : 0)
        result = 31 * result + (value != null ? value.hashCode() : 0)
        result = 31 * result + (moduleName != null ? moduleName.hashCode() : 0)
        result = 31 * result + (isSensitive ? 1 : 0)
        return result
    }
}