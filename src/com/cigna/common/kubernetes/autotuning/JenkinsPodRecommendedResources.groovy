package com.cigna.common.kubernetes.autotuning

import com.cloudbees.groovy.cps.NonCPS
import com.fasterxml.jackson.annotation.*
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import io.fabric8.kubernetes.api.model.KubernetesResource

@JsonDeserialize(using = JsonDeserializer.None.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder([
        "containerRecommendations",
])
/**
 * Kubernetes Custom Resource for managing container recommendations (POJO w/ JSON accessors)
 */
class JenkinsPodRecommendedResources implements KubernetesResource {

    @JsonProperty("containerRecommendations")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<JenkinsPodContainerResources> containerRecommendations = []
    @JsonIgnore
    private Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>();

    @NonCPS
    @JsonProperty("containerRecommendations")
    List<JenkinsPodContainerResources> getContainerRecommendations() {
        return containerRecommendations;
    }

    @NonCPS
    @JsonProperty("containerRecommendations")
    void setContainerRecommendations(List<JenkinsPodContainerResources> containerRecommendations) {
        this.containerRecommendations = containerRecommendations;
    }

    @NonCPS
    @JsonAnyGetter
    Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @NonCPS
    @JsonAnySetter
    void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

}
