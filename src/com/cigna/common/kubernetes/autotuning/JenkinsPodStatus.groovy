package com.cigna.common.kubernetes.autotuning

import com.cloudbees.groovy.cps.NonCPS
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import io.fabric8.kubernetes.api.model.KubernetesResource
import io.fabric8.kubernetes.api.model.PodCondition

@JsonDeserialize(using = JsonDeserializer.None.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
/**
 * Kubernetes Custom Resource mapped to CRD schema 'status' field (POJO w/ JSON accessors)
 */
class JenkinsPodStatus implements KubernetesResource {
    @JsonProperty("conditions")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<JenkinsPodCondition> conditions = []
    @JsonProperty("recommendation")
    private JenkinsPodRecommendedResources recommendation = null

    JenkinsPodStatus() {}

    JenkinsPodStatus(List<JenkinsPodCondition> conditions, JenkinsPodRecommendedResources recommendation) {
        this.conditions = conditions
        this.recommendation = recommendation

    }

    @NonCPS
    @JsonProperty("conditions")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    List<JenkinsPodCondition> getConditions() {
        return conditions;
    }

    @NonCPS
    @JsonProperty("conditions")
    void setConditions(List<JenkinsPodCondition> conditions) {
        this.conditions = conditions;
    }

    @NonCPS
    @JsonProperty("recommendation")
    void setRecommendation(JenkinsPodRecommendedResources recommendation) {
        this.recommendation = recommendation;
    }

    @NonCPS
    @JsonProperty("recommendation")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    JenkinsPodRecommendedResources getRecommendation() {
        return recommendation;
    }
}
