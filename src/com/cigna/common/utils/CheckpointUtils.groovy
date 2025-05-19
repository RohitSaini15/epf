package com.cigna.common.utils

import com.cloudbees.groovy.cps.NonCPS

import java.util.regex.Matcher
import java.util.regex.Pattern

class CheckpointUtils {
    static boolean isCheckpointPhase(String podGroup) {
        Pattern p = Pattern.compile(/checkpoint-\d+/)
        Matcher m = p.matcher(podGroup)
        m.matches()
    }

    static void executeCheckpoint(List<Map<String, Object>> phases) {
        // There should never be more than 1 in a group
        phases.first().phaseInstance.run()
    }

    /**
     * Checks all of the configured clouds & podGroups and if any groups contain checkpoint phases, it will split
     * the groups into sub-groups.
     * @param maps
     * @return
     */
    @NonCPS
    static List<Map<String, Object>> assignPodGroupsByCheckpointGrouping(List<Map<String, Object>> maps) {
        def podGroupCounter = [:].withDefault { 1 }
        def checkpointCounter = 1
        def currentPodGroup = ''
        def currentGroup = ''

        maps.inject([]) { acc, phase ->
            def group = phase.containsKey('checkpointType') ? currentGroup : Utils.cloud(phase)

            if (currentGroup != group && !phase.containsKey('checkpointType')) {
                currentGroup = group
                currentPodGroup = "${group}-${podGroupCounter[group]}"
                phase.podGroup = currentPodGroup
            } else if (phase.containsKey('checkpointType')) {
                phase.podGroup = "checkpoint-${checkpointCounter++}"

                // we have to increment all the group counters in case we have a sequence like
                // cloud1, cloud2, checkpoint, cloud1 otherwise the final cloud1 will not get incremented correctly
                podGroupCounter = podGroupCounter.collectEntries { key, value ->
                    [(key):  value + 1]
                }.withDefault { 1 }
                currentPodGroup = "${group}-${podGroupCounter[group]}"
            } else {
                phase.podGroup = currentPodGroup
            }

            acc + phase
        }.each { phase ->
            if (phase.containsKey('phases')) {
                phase.phases = phase.phases.collect { nestedPhase ->
                    nestedPhase.podGroup = phase.podGroup
                    nestedPhase.cloudName = phase.cloudName
                    nestedPhase
                }
            }
        }
    }
}
