package com.cigna.common.kubernetes.autotuning

import com.cloudbees.groovy.cps.NonCPS
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import io.fabric8.kubernetes.api.model.KubernetesResource

@JsonDeserialize(using = JsonDeserializer.None.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder([
        "containerName",
        "lowerBound",
        "target",
        "upperBound",
])
/**
 * Kubernetes Custom Resource for managing container resources (POJO w/ JSON accessors)
 */
class JenkinsPodContainerResources implements KubernetesResource {
    @JsonProperty("containerName")
    private String containerName
    @JsonProperty("lowerBound")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private def lowerBound = [:]
    @JsonProperty("target")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private def target = [:]
    @JsonProperty("upperBound")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private def upperBound = [:]
    @JsonIgnore
    private Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>()

    JenkinsPodContainerResources() {}

    JenkinsPodContainerResources(
            String containerName,
            Map<String, String> lowerBound,
            Map<String, String> upperBound,
            Map<String, String> target) {
        this.containerName = containerName
        this.lowerBound = lowerBound
        this.upperBound = upperBound
        this.target = target
    }

    @NonCPS
    @JsonProperty("containerName")
    String getContainerName() {
        return containerName
    }

    @NonCPS
    @JsonProperty("containerName")
    void setContainerName(String containerName) {
        this.containerName = containerName
    }

    @NonCPS
    @JsonProperty("lowerBound")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    Map<String, String> getLowerBound() {
        return lowerBound as Map<String, String>
    }

    @NonCPS
    @JsonProperty("lowerBound")
    void setLowerBound(Map<String, String> lowerBound) {
        this.lowerBound = lowerBound
    }

    @NonCPS
    @JsonProperty("upperBound")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    Map<String, String> getUpperBound() {
        return upperBound as Map<String, String>
    }

    @NonCPS
    @JsonProperty("upperBound")
    void setUpperBound(Map<String, String> upperBound) {
        this.upperBound = upperBound
    }

    @NonCPS
    @JsonProperty("target")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    Map<String, String> getTarget() {
        return target as Map<String, String>
    }

    @NonCPS
    @JsonProperty("target")
    void setTarget(Map<String, String> target) {
        this.target = target
    }


}