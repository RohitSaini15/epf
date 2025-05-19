package com.cigna.common.kubernetes.autotuning

import com.cloudbees.groovy.cps.NonCPS
import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.Namespaced
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.model.annotation.Group
import io.fabric8.kubernetes.model.annotation.Version

@JsonDeserialize(using = JsonDeserializer.None.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(value = [
        'apiVersion',
        'kind',
        'metadata',
        'spec',
        'status'
])
@Version('v1')
@Group('evernorth.com')
/**
 * Kubernetes Custom Resource Definition for JenkinsPod (POJO w/ JSON accessors)
 */
class JenkinsPod implements HasMetadata, Namespaced {
    @JsonProperty("apiVersion")
    private String apiVersion = "v1";
    @JsonProperty("kind")
    private String kind = "JenkinsPod";
    @JsonProperty("metadata")
    private ObjectMeta metadata;
    @JsonProperty("spec")
    private JenkinsPodSpec spec;
    @JsonProperty("status")
    private JenkinsPodStatus status;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>();

    JenkinsPod() {

    }

    JenkinsPod(String apiVersion, String kind, ObjectMeta metadata, JenkinsPodSpec spec, JenkinsPodStatus status) {
        super();
        this.apiVersion = apiVersion;
        this.kind = kind;
        this.metadata = metadata;
        this.spec = spec;
        this.status = status;
    }

    @NonCPS
    @JsonProperty("metadata")
    ObjectMeta getMetadata() {
        return metadata
    }
    @NonCPS
    @JsonProperty("metadata")
    void setMetadata(ObjectMeta metadata) {
        this.metadata = metadata
    }
    @NonCPS
    @JsonProperty("kind")
    String getKind() {
        return this.kind
    }
    @NonCPS
    @JsonProperty("kind")
    void setKind(String kind) {
        this.kind = kind;
    }
    @NonCPS
    @JsonProperty("apiVersion")
    String getApiVersion() {
        return this.apiVersion
    }
    @NonCPS
    @JsonProperty("apiVersion")
    void setApiVersion(String version) {
        this.apiVersion = version
    }
    @NonCPS
    @JsonProperty("spec")
    JenkinsPodSpec getSpec() {
        return spec;
    }
    @NonCPS
    @JsonProperty("spec")
    void setSpec(JenkinsPodSpec spec) {
        this.spec = spec;
    }
    @NonCPS
    @JsonProperty("status")
    JenkinsPodStatus getStatus() {
        return status;
    }
    @NonCPS
    @JsonProperty("status")
    void setStatus(JenkinsPodStatus status) {
        this.status = status;
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

