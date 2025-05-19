package com.cigna.common.kubernetes

import com.cloudbees.groovy.cps.NonCPS
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.ConfigBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder

class KubernetesAwareService {
    String namespace
    String oauthToken
    String masterUrl
    Closure<KubernetesClient> k8sClientSupplier = { -> newK8sClient() }

    KubernetesAwareService(def namespace, def oauthToken, def masterUrl = null) {
        this.namespace = namespace
        this.oauthToken = oauthToken
        this.masterUrl = masterUrl
    }

    @NonCPS
    KubernetesClient newK8sClient() {
        def configBuilder = new ConfigBuilder()
            .withOauthToken(oauthToken)

        if (masterUrl) {
            configBuilder
                .withMasterUrl(masterUrl)
                .withTrustCerts(true)
        }

        Config config = configBuilder.build()
        return new KubernetesClientBuilder().withConfig(config).build()
    }
}