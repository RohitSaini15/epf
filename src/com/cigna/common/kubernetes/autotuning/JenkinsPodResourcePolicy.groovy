package com.cigna.common.kubernetes.autotuning

import com.cloudbees.groovy.cps.NonCPS
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import io.fabric8.kubernetes.api.model.KubernetesResource

@JsonDeserialize(using = JsonDeserializer.None.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder([
        "containerPolicies",
])
/**
 * Kubernetes Custom Resource for managing lists of container policies (POJO w/ JSON accessors)
 */
class JenkinsPodResourcePolicy implements KubernetesResource {
    @JsonProperty("containerPolicies")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<JenkinsPodContainerResourcePolicy> containerPolicies = []

    JenkinsPodResourcePolicy() {}

    JenkinsPodResourcePolicy(List<JenkinsPodContainerResourcePolicy> containerPolicies) {
        this.containerPolicies = containerPolicies
    }

    @NonCPS
    @JsonProperty("containerPolicies")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    List<JenkinsPodContainerResourcePolicy> getContainerPolicies() {
        return this.containerPolicies
    }

    @NonCPS
    @JsonProperty("containerPolicies")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    void setContainerPolicies(List<JenkinsPodContainerResourcePolicy> containerPolicies) {
        this.containerPolicies = containerPolicies
    }
}