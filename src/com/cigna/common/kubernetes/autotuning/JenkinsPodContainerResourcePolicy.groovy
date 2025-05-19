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
    "containerName",
    "controlledResources",
    "minAllowed",
    "maxAllowed",
])
/**
 * Kubernetes Custom Resource for managing container resource policies (POJO w/ JSON accessors)
 */
class JenkinsPodContainerResourcePolicy implements KubernetesResource {
    @JsonProperty("containerName")
    private String containerName
    @JsonProperty("controlledResources")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<String> controlledResources = []
    @JsonProperty("minAllowed")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private def minAllowed = [:]
    @JsonProperty("maxAllowed")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private def maxAllowed = [:]

    JenkinsPodContainerResourcePolicy() {}

    JenkinsPodContainerResourcePolicy(
        String containerName,
        List<String> controlledResources,
        Map<String, String> minAllowed,
        Map<String, String> maxAllowed) {
        this.containerName = containerName
        this.controlledResources = controlledResources
        this.minAllowed = minAllowed
        this.maxAllowed = maxAllowed
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
    @JsonProperty("controlledResources")
    String getControlledResources() {
        return controlledResources
    }

    @NonCPS
    @JsonProperty("controlledResources")
    void setControlledResources(List<String> controlledResources) {
        this.controlledResources = controlledResources
    }

    @NonCPS
    @JsonProperty("minAllowed")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    Map<String, String> getMinAllowed() {
        return minAllowed as Map<String, String>
    }

    @NonCPS
    @JsonProperty("minAllowed")
    void setLowerBound(Map<String, String> minAllowed) {
        this.minAllowed = minAllowed
    }

    @NonCPS
    @JsonProperty("maxAllowed")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    Map<String, String> getMaxAllowed() {
        return maxAllowed as Map<String, String>
    }

    @NonCPS
    @JsonProperty("maxAllowed")
    void setMaxAllowed(Map<String, String> maxAllowed) {
        this.maxAllowed = maxAllowed
    }


}