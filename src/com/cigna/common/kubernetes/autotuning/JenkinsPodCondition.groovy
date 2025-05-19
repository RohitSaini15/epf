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
        "lastTransitionTime",
        "message",
        "reason",
        "status",
        "type"
])
/**
 * Kubernetes Custom Resource for mapped to CRD schema 'condition' field (POJO w/ JSON accessors)
 */
class JenkinsPodCondition implements KubernetesResource {
    @JsonProperty("lastTransitionTime")
    private String lastTransitionTime
    @JsonProperty("message")
    private String message
    @JsonProperty("reason")
    private String reason
    @JsonProperty("status")
    private String status
    @JsonProperty("type")
    private String type

    JenkinsPodCondition() {

    }

    JenkinsPodCondition(String lastTransitionTime, String message, String reason, String status, String type) {
        super()
        this.lastTransitionTime = lastTransitionTime
        this.message = message
        this.reason = reason
        this.status = status
        this.type = type
    }

    @NonCPS
    @JsonProperty("lastTransitionTime")
    String getLastTransitionTime() {
        return lastTransitionTime
    }

    @NonCPS
    @JsonProperty("lastTransitionTime")
    void setLastTransitionTime(String lastTransitionTime) {
        this.lastTransitionTime = lastTransitionTime
    }

    @NonCPS
    @JsonProperty("message")
    String getMessage() {
        return message
    }

    @NonCPS
    @JsonProperty("message")
    void setMessage(String message) {
        this.message = message
    }

    @NonCPS
    @JsonProperty("reason")
    String getReason() {
        return reason
    }

    @NonCPS
    @JsonProperty("reason")
    void setReason(String reason) {
        this.reason = reason
    }

    @NonCPS
    @JsonProperty("status")
    String getStatus() {
        return status
    }

    @NonCPS
    @JsonProperty("status")
    void setStatus(String status) {
        this.status = status
    }

    @NonCPS
    @JsonProperty("type")
    String getType() {
        return type
    }

    @NonCPS
    @JsonProperty("type")
    void setType(String type) {
        this.type = type
    }
}
