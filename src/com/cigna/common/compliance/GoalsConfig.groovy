package com.cigna.common.compliance

import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonOutput

/**
 * This class is used to create a code representation of an Adjudicator goals file with helpful methods.
 */
class GoalsConfig implements Serializable {

    protected final Map<String, Object> executors = [:]
    protected List<String> goals = []
    protected final Map<String, Object> baggage = [:]

    @NonCPS
    static GoalsConfig Instance(Map<String, Object> input = [:]) {
         new GoalsConfig(input)
    }

    static String normalizeGoal(String value) {
        value.toLowerCase().capitalize()
    }

    void executorUpsert(String key, Map<String, Object> value, Boolean merge = false) {
        if (merge) {
            this.executors[key] = mapMergeInternal(this.executors[key] ?: [:], value)
        } else {
            this.executors[key] = value
        }
    }

    void executorUpsert(String key, List<Object> value, Boolean merge = false) {
        if (merge) {
            this.executors[key] = this.executors[key] + value
        } else {
            this.executors[key] = value
        }
    }

    Object executorGet(String key) {
        this.executors[key]
    }

    boolean goalExists(String goal) {
        goals.contains(normalizeGoal(goal))
    }

    void executorRemove(String key) {
        this.executors.each {
            List<String> levels = key.tokenize('.')
            if (levels.size() == 1) {
                this.executors.remove(levels[0])
            } else {
                Map<String, Object> ref = this.executors
                levels[0..-2].each {
                    ref = ref[it] as Map<String, Object>
                }
                ref.remove(levels[-1])
            }
        }
    }

    void configMerge(GoalsConfig config) {
        config.goals.each { it ->
            this.goalUpsert(it)
        }
        config.executors.each {
            this.executorUpsert(it.key, it.value, true)
        }
    }

    void mapMerge(Map<String, Object> config) {
        List<String> goals = config?.goals as List<String> ?: config?.Goals as List<String> ?: []
        Map<String, Object> executors = config?.executors as Map<String, Object> ?:
            config?.Executors as Map<String, Object> ?: [:]
        goals.each {
            this.goalUpsert(it)
        }
        executors.each {
            this.executorUpsert(it.key, it.value, true)
        }
    }

    void goalUpsert(String value) {
        String goalName = normalizeGoal(value)
        if (this.goals.contains(goalName)) {
            return
        }
        this.goals += goalName
    }

    void goalRemove(String key) {
        String goalName = normalizeGoal(key)
        this.goals.removeAll { it == goalName }
    }

    @NonCPS
    void attach(String key, Object value) {
        this.baggage[key] = value
    }

    String toJson() {
        Map<String, Object> output = ['goals': this.goals, 'executors': this.executors]
        this.baggage.each { it ->
            if (!output.containsKey(it.key)) {
                output[it.key] = it.value
            }
        }
        JsonOutput.toJson(output)
    }

    String toString() {
        Map<String, Object> output = ['goals': this.goals, 'executors': this.executors]
        this.baggage.each {
            if (!output.containsKey(it.key)) {
                output[it.key] = it.value
            }
        }
        output.toString()
    }

    @SuppressWarnings(['Instanceof'])
    private Object mapMergeInternal(Map lhs, Map rhs) {
        rhs.inject(lhs.clone()) { map, entry ->
            if (map[entry.key as String] instanceof Map && entry.value instanceof Map) {
                map[entry.key as String] = mapMergeInternal(map[entry.key as String], entry.value)
            } else {
                map[entry.key as String] = entry.value
            }
            map
        }
    }

    private GoalsConfig(Map<String, Object> input = [:]) {
        this.executors = input['executors'] ?: [:]
        this.goals = input['Goals'] ?: []
        this.baggage = [:]
    }
}
