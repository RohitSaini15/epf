package com.cigna.common.kubernetes.autotuning

import com.cloudbees.groovy.cps.NonCPS
import com.fasterxml.jackson.annotation.*
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import io.fabric8.kubernetes.api.model.KubernetesResource

@JsonDeserialize(using = JsonDeserializer.None.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
/**
 * Kubernetes Custom Resource mapped to CRD schema 'spec' field (POJO w/ JSON accessors)
 */
class JenkinsPodSpec implements KubernetesResource {
    @JsonProperty("resourcePolicy")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private JenkinsPodResourcePolicy resourcePolicy

    @JsonIgnore
    private Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>();

    JenkinsPodSpec() {}

    JenkinsPodSpec(JenkinsPodResourcePolicy resourcePolicy) {
        this.resourcePolicy = resourcePolicy
    }

    @NonCPS
    @JsonProperty("resourcePolicy")
    JenkinsPodResourcePolicy getResourcePolicy() {
        return resourcePolicy;
    }
    @NonCPS
    @JsonProperty("resourcePolicy")
    void setResourcePolicy(JenkinsPodResourcePolicy resourcePolicy) {
        this.resourcePolicy = resourcePolicy;
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
