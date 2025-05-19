package com.cigna.common.kubernetes

import com.cloudbees.groovy.cps.NonCPS

class PodProfile {
    String podName
    String autotuningName
    String namespace
    String uid = null// needed to correctly configure ownerReferences
    Map<String, Map<String, String>> recommendations = [:]

    @Override
    @NonCPS
    String toString() {
        return "PodProfile(podName=$podName, autotuningName=$autotuningName, namespace=$namespace, recommendations=$recommendations, uid=$uid)"
    }
}
