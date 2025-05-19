package com.cigna.common.kubernetes

import com.cigna.common.compliance.ComplianceValidator
import com.cigna.common.kubernetes.autotuning.JenkinsPod
import com.cigna.common.kubernetes.autotuning.JenkinsPodContainerResources
import com.cigna.common.utils.FeatureFlags
import com.cigna.common.utils.Utils
import groovy.json.JsonOutput
import hudson.Functions
import io.fabric8.kubernetes.client.KubernetesClient

class PodAutotuningService extends KubernetesAwareService {
    PodAutotuningService(String namespace, String oauthToken) {
        super(namespace, oauthToken)
    }

    PodProfile getRecommendedResourcesForPod(def script, PodProfile podProfile) {
        KubernetesClient client = k8sClientSupplier()

        try {
            fetchAutotuningUsingCustomResources(client, script, podProfile)
        } finally {
            client.close()
        }

        podProfile
    }

    private void fetchAutotuningUsingCustomResources(KubernetesClient client, def script, PodProfile podProfile) {
        JenkinsPod jenkinsPod

        try {
            jenkinsPod = client.resources(JenkinsPod.class)
                .inNamespace(namespace)
                .withName(podProfile.autotuningName)
                .get()

            if (jenkinsPod) {
                if (FeatureFlags.debug) {
                    script.echo("JenkinsPod = ${JsonOutput.prettyPrint(JsonOutput.toJson(jenkinsPod))}")
                }
                if (jenkinsPod.metadata) {
                    podProfile.uid = jenkinsPod?.metadata?.uid

                    updatePodProfileWithRecommendations(podProfile, jenkinsPod)
                } else {
                    script.echo("Incomplete JenkinsPod body received from autotuning recommender")
                }
            } else {
                script.echo("No JenkinsPod received for pod ${podProfile.podName} in podGroup ${podProfile.autotuningName}")
            }
        } catch (e) {
            // We don't propagate the exception, just proceed without using EPF recommendations
            script.echo("Continuing without autotuning as pod recommendations are not available for pods. This is not an error.")
            if (FeatureFlags.debug) {
                script.echo(ComplianceValidator.transformURLs("If this is unexpected, please consult EPF FAQ Entry https://confluence.sys.cigna.com/display/DvOp/EPF+FAQ#expand-HowcanIenablePodAutotuninginmyPipeline:~:text=How%20can%20I%20enable%20Pod%20Autotuning%20in%20my%20Pipeline%3F"))
                if (FeatureFlags.showStackTraces) {
                    script.echo(Functions.printThrowable(e))
                }
            }
        }
    }


    def void updatePodProfileWithRecommendations(PodProfile podProfile, JenkinsPod jenkinsPod) {
        def containerRecs = jenkinsPod.status.recommendation.containerRecommendations
        containerRecs.each { container ->
            // after fixes in epf-operator, memory should always be in Mi.
            if (container.target.memory.contains("Ki")) {
                // need to normalize to Mi for resource totals reporting
                container.target.memory = Utils.normalizeToMi(container.target.memory)
            }
            if (!inactivePod(container)) {
                if (!podProfile.recommendations.containsKey(container.containerName)) {
                    podProfile.recommendations[container.containerName] = [:]
                }
                podProfile.recommendations[container.containerName].target = container.target
                podProfile.recommendations[container.containerName].lowerBound = container.lowerBound
                podProfile.recommendations[container.containerName].upperBound = container.upperBound
            }
        }
    }

    private boolean inactivePod(JenkinsPodContainerResources container) {
        container.target.memory == '0' || container.target.memory == '0Mi' || container.target.cpu == '0m' || container.target.cpu == '0'
    }
}