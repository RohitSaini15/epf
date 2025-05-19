package com.cigna.common.phases

import com.cigna.base.Phase

class PhaseListUtils {

    /*
    All of these one-arg "sanitizeForJson" methods polymorphically sanitize an input phase list.
    See the javadoc on the List version.
     */

    static String sanitizeForJson(Closure _) {
        return '<instance of Closure>'
    }

    static String sanitizeForJson(Phase p) {
        return "<instance of ${p.getClass().simpleName}>"
    }

    /**
     * Sanitize a list of phase maps for serializing to JSON.
     * Most types (and null) are returned as-is, because most things in the phase map are fine in JSON.
     * There's special handling for:
     * - Closures - when these go through JsonOutput.toJson they get executed, and we don't want that,
     *              so sanitizing these just returns a string
     * - Phases - these are cyclic (phase instance contain a `config` which contains the phase instance),
     *            so writing these to Json causes an infinite recursion. Sanitize these just a string
     *            containing the class name
     * - Lists - might contain closures or phases, so each item gets sanitized
     * - Maps - values might contain closures or phases, so each value gets sanitized
     * @param value when first called, expected to be a list of phase maps (recursive calls might be of other types)
     * @return a new List containing sanitized phase maps
     */
    static List sanitizeForJson(List l) {
        return l.collect {
            sanitizeForJson(it)
        }
    }

    static Map sanitizeForJson(Map m) {
        return m.collectEntries { k, v ->
            [(k): sanitizeForJson(v)]
        }
    }

    static def sanitizeForJson(def value) {
        return value
    }
    /* end sanitizeForJson methods */
}
