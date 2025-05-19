package com.cigna.common.kubernetes

import com.cigna.common.utils.FeatureFlags
import com.evernorth.cloudnativebuild.mocks.MockJenkins
import io.fabric8.kubernetes.api.model.*
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.server.mock.KubernetesServer
import spock.lang.Specification

class NamespaceReportingServiceSpec extends Specification {

    public KubernetesServer server = new KubernetesServer(true, true)

    def setup() {
        server.before()
    }

    def cleanup() {
        server.after()
    }

    def "inferMasterUrl returns the correct URL based on namespace"() {
        when:
        def result = callInferMasterUrl(namespaceName)

        then:
        result == expectedUrl

        where:
        namespaceName        || expectedUrl
        "myEksDev"           || "https://7682CA9FCAD8AA53B12D6293A200890C.gr7.us-east-1.eks.amazonaws.com"
        "some-eks-prod"      || "https://A57158C7829AC77E796290CE56CAA6DD.gr7.us-east-1.eks.amazonaws.com"
        "test-eks-namespace" || "https://1A110575CBA235C2FB5539C941D9FC1E.gr7.us-east-1.eks.amazonaws.com"
        "no-validone-here"   || null
        "my-prod-env"        || null
    }

    def "printNamespaceResourceSummary should show resource quotas with correct formatting"() {
        given:
        KubernetesClient client = server.getClient()
        createNamespace(client, "format-testing")
        createResourceQuota(
            client, "format-testing", "test-quota",
            ["requests.cpu": Quantity.parse("2"), "requests.memory": Quantity.parse("1Gi")],
            ["requests.cpu": Quantity.parse("0"), "requests.memory": Quantity.parse("0")]
        )

        def service = new NamespaceReportingService("format-testing", "mock-oauth") {
            protected KubernetesClient k8sClientSupplier() {
                return client
            }
        }
        def script = new MockJenkins()

        when:
        service.printNamespaceResourceSummary(script, "format-testing")

        then:
        def result = script.consoleMessages.first()
        result.contains("+----------------------------- Namespace Quota Usage Report ------------------------------")
        result.contains("| Namespace: format-testing")
        result.contains("| Resource Quotas:")
        result.contains("requests.cpu: 0/2 (0.0%)")
        result.contains("requests.memory: 0/1Gi (0.0%)")
    }

    def "printNamespaceResourceSummary should handle multiple quota and pod scenarios: #description"() {
        given:
        def client = server.getClient()
        createNamespace(client, testNamespace)

        // Create resource quotas
        quotas.each { quotaName, quotaSpec ->
            createResourceQuota(client, testNamespace, quotaName, quotaSpec.hard, quotaSpec.used)
        }

        // Create pods
        pods.each { podName, containerResources ->
            createPod(client, testNamespace, podName, containerResources)
        }

        def service = new NamespaceReportingService(testNamespace, "mock-oauth") {
            protected KubernetesClient k8sClientSupplier() {
                return client
            }
        }
        def script = new MockJenkins()

        when:
        service.printNamespaceResourceSummary(script, testNamespace)

        then:
        def report = script.consoleMessages.join("\n")
        expectedText.each { expectedLine ->
            assert report.contains(expectedLine)
        }

        where:
        description                        | testNamespace           | quotas | pods           | expectedText
        "No usage with simple CPU/memory"  | "ns-quota-pods-zero"    | [
            "test-quota0": [
                hard: [
                    "requests.cpu"   : Quantity.parse("2"),
                    "requests.memory": Quantity.parse("1Gi")
                ],
                used: [
                    "requests.cpu"   : Quantity.parse("0"),
                    "requests.memory": Quantity.parse("0")
                ]
            ]
        ]                                                                     | [:]            | [
            "Namespace: ns-quota-pods-zero",
            "requests.cpu: 0/2",
            "requests.memory: 0/1Gi"
        ]
        "Partial usage with multiple pods" | "ns-quota-pods-partial" | [
            "test-quota1": [
                hard: [
                    "requests.cpu"   : Quantity.parse("4"),
                    "requests.memory": Quantity.parse("2Gi")
                ],
                used: [
                    "requests.cpu"   : Quantity.parse("2"),
                    "requests.memory": Quantity.parse("1Gi")
                ]
            ]
        ]                                                                     | [
            "my-pod-1": new ResourceRequirementsBuilder()
                .withRequests(["cpu": Quantity.parse("1"), "memory": Quantity.parse("512Mi")])
                .build(),
            "my-pod-2": new ResourceRequirementsBuilder()
                .withRequests(["cpu": Quantity.parse("1"), "memory": Quantity.parse("512Mi")])
                .build()
        ]                                                                                      | [
            "Namespace: ns-quota-pods-partial",
            "requests.cpu: 2/4",
            "requests.memory: 1Gi/2Gi"
        ]
    }

    def "printNamespaceResourceSummary debug-level pod logic: #description"() {
        given:
        FeatureFlags.debug = debugFlag
        def client = server.getClient()

        createNamespace(client, nsName)

        if (podsKeyInQuota) {
            createResourceQuota(
                client,
                nsName,
                "debug-pods-quota",
                ["pods": Quantity.parse("${hardPodsValue}")],
                ["pods": Quantity.parse("${usedPodsValue}")]
            )
        } else {
            createResourceQuota(
                client,
                nsName,
                "debug-other-quota",
                ["requests.cpu": Quantity.parse("1")],
                ["requests.cpu": Quantity.parse("0")]
            )
        }

        (1..createdPods).each { index ->
            createPod(client, nsName, "debug-pod-${index}",
                new ResourceRequirementsBuilder()
                    .withRequests(["cpu": Quantity.parse("100m"), "memory": Quantity.parse("128Mi")])
                    .withLimits(["cpu": Quantity.parse("200m"), "memory": Quantity.parse("256Mi")])
                    .build()
            )
        }

        def service = new NamespaceReportingService(nsName, "mock-oauth") {
            protected KubernetesClient k8sClientSupplier() {
                return client
            }
        }
        def script = new MockJenkins()

        when:
        service.printNamespaceResourceSummary(script, nsName)

        then:
        def consoleOutput = script.consoleMessages.join("\n")
        if (expectPodDetails) {
            (1..createdPods).each { index ->
                assert consoleOutput.contains("debug-pod-${index}:test-container cpu (100m/200m) memory (128Mi/256Mi)")
            }
        } else {
            (1..createdPods).each { index ->
                assert !consoleOutput.contains("debug-pod-${index}:test-container cpu (100m/200m) memory (128Mi/256Mi)")
            }
        }

        cleanup:
        FeatureFlags.debug = false

        where:
        description                                            | debugFlag | podsKeyInQuota | hardPodsValue | usedPodsValue | createdPods | expectPodDetails | nsName
        "debug off, pods key present => no debug lines"        | false     | true           | 5             | 3             | 2           | false            | "debug-ns1"
        "debug on, pods key present => prints pod details"     | true      | true           | 5             | 2             | 2           | true             | "debug-ns2"
        "debug on, pods key present but 0 used => still shows" | true      | true           | 5             | 0             | 2           | true             | "debug-ns3"
        "debug on, no pods key => no debug lines"              | true      | false          | 1             | 0             | 2           | false            | "debug-ns4"
        "debug off, no pods key => no debug lines"             | false     | false          | 1             | 0             | 2           | false            | "debug-ns5"
    }

    private void createNamespace(KubernetesClient client, String name) {
        client.namespaces().create(new NamespaceBuilder()
            .withNewMetadata()
            .withName(name)
            .endMetadata()
            .withNewStatus()
            .withPhase("Active")
            .endStatus()
            .build()
        )
    }

    private void createResourceQuota(KubernetesClient client, String namespace, String rqName,
                                     Map<String, Quantity> hardValues,
                                     Map<String, Quantity> usedValues) {
        client.resourceQuotas().inNamespace(namespace).create(
            new ResourceQuotaBuilder()
                .withNewMetadata()
                .withName(rqName)
                .endMetadata()
                .withNewSpec()
                .withHard(hardValues)
                .endSpec()
                .withNewStatus()
                .withHard(hardValues)
                .withUsed(usedValues)
                .endStatus()
                .build()
        )
    }

    private void createPod(KubernetesClient client, String namespace, String podName, ResourceRequirements containerResources) {
        client.pods().inNamespace(namespace).create(
            new PodBuilder()
                .withNewMetadata()
                .withName(podName)
                .endMetadata()
                .withNewSpec()
                .addNewContainer()
                .withName("test-container")
                .withResources(containerResources)
                .endContainer()
                .endSpec()
                .build()
        )
    }

    private String callInferMasterUrl(String namespaceName) {
        return new NamespaceReportingService("test", "placeholder-token").with {
            def closure = NamespaceReportingService.metaClass.getMetaMethod("inferMasterUrl", String)
            return closure.invoke(delegate, namespaceName)
        }
    }
}