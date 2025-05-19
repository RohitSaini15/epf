package com.cigna.common.kubernetes


import com.cigna.common.utils.FeatureFlags
import com.cloudbees.groovy.cps.NonCPS
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.PodList
import io.fabric8.kubernetes.api.model.ResourceQuotaList
import io.fabric8.kubernetes.client.KubernetesClient

import static com.cigna.base.DockerPipelineLib.updateHardLimits

class NamespaceReportingService extends KubernetesAwareService {

    public static NamespaceReportingService newReportingService(String token, String namespace) {
        def masterUrl = inferMasterUrl(namespace)
        return new NamespaceReportingService(namespace, token, masterUrl)
    }

    NamespaceReportingService(def namespace, def oauthToken, def masterUrl = null) {
        super(namespace, oauthToken, masterUrl)
    }

    @NonCPS
    private static String inferMasterUrl(String namespace) {
        def ns = namespace.toLowerCase()
        def master_urls = [
            dev : 'https://7682CA9FCAD8AA53B12D6293A200890C.gr7.us-east-1.eks.amazonaws.com',
            test: 'https://1A110575CBA235C2FB5539C941D9FC1E.gr7.us-east-1.eks.amazonaws.com',
            prod: 'https://A57158C7829AC77E796290CE56CAA6DD.gr7.us-east-1.eks.amazonaws.com'
        ]

        if (!ns.contains('eks')) return null
        def env_type = 'dev'
        if (ns.contains('prod')) {
            env_type = 'prod'
        } else if (ns.contains('test')) {
            env_type = 'test'
        }

        return master_urls[env_type]
    }

    void printNamespaceResourceSummary(def script, def namespace) {
        KubernetesClient client = null
        def builder = new StringBuilder('\n+----------------------------- Namespace Quota Usage Report ------------------------------')
        try {
            builder.append("\n| Namespace: ${namespace}")
            client = k8sClientSupplier()

            Namespace ns = client.namespaces().withName(namespace).get()
            ResourceQuotaList quotaList = client.resourceQuotas().inNamespace(namespace).list()
            PodList podList = client.pods().inNamespace(namespace).list()

            if (ns != null) {
                builder.append("\n| Status: ${ns.getStatus().getPhase()}")
            } else {
                builder.append('\n| Namespace not found')
                return
            }

            if (quotaList?.getItems()?.size() > 0) {
                def quota = quotaList.getItems()[0]
                builder.append('\n| Resource Quotas:')
                quota.getStatus().getHard().sort().each { k, v ->
                    def used = quota.getStatus().getUsed().get(k)


                    if (used != null) {
                        double usedValue = used.getNumericalAmount()
                        double hardValue = v.getNumericalAmount()
                        double percentage = hardValue > 0 ? (usedValue / hardValue) * 100 : 1
                        builder.append("\n|  ${k}: ${used}/${v} (${percentage.round(2)}%)")
                        if (k == 'pods' && FeatureFlags.debug) {
                            printPodDetails(podList, builder)
                        }
                        updateHardLimits(k, hardValue)
                    } else {
                        builder.append "\n|  ${k}: 0/${v} (0%)"
                    }
                }
            }


        } catch (Exception e) {
            builder.append("\n| ${e.class.name} exception when calling Kubernetes API: ${e.message}")
        } finally {
            builder.append('\n+-----------------------------------------------------------------------------------------')
            script.echo(builder.toString())
            if (client != null) {
                client.close()
            }
        }
    }

    def void printPodDetails(PodList podList, StringBuilder builder) {
        podList?.items?.each { pod ->
            pod.spec?.containers?.each { container ->
                def requests = container.resources?.requests
                def limits = container.resources?.limits

                def memRequest = requests?.get("memory") ?: "N/A"
                def cpuRequest = requests?.get("cpu") ?: "N/A"
                def memLimit = limits?.get("memory") ?: "N/A"
                def cpuLimit = limits?.get("cpu") ?: "N/A"

                builder.append("\n|   ${pod.metadata.name}:${container.name} cpu (${cpuRequest}/${cpuLimit})" +
                    " memory (${memRequest}/${memLimit})")

            }
        }
    }
}