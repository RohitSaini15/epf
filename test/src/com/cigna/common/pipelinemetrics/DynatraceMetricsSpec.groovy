package com.cigna.common.pipelinemetrics

import com.cigna.jenkins_spock.JenkinsPipelineSpecification

class DynatraceMetricsSpec extends JenkinsPipelineSpecification {

    public class HttpMock {
        def getContent() {
            '{ "totalCount": 1, "pageSize": 50, "entities": [{"entityId": "CLOUD_APPLICATION_NAMESPACE-D9A6DBB354F28E12", "type": "CLOUD_APPLICATION_NAMESPACE", "displayName": "epf-tests-openshift-nonprod1"}]}'
        }
        def getStatus() {
            return 200
        }
    }

    class Script {
        public Map env = [:]
    }

    def script = new Script()
    def config = [:]

    def setup() {
        explicitlyMockPipelineStep("echo")
        explicitlyMockPipelineStep("withCredentials")
    }

    def """When retrieveDynatraceEntityAndDisplay is called steps are run to retrieve entity and display objects"""() {
        given:
        explicitlyMockPipelineVariable('PIPELINE_DYNATRACE_ACCESS_TOKEN')

        when:
        DynatraceMetrics dm = new DynatraceMetrics(displayName, script)
        dm.printDynatraceMetricsUrl()

        then:
        1 * getPipelineMock("httpRequest")({
            it['url'] =~ /($displayName)/
        }) >> new HttpMock()
        1 * getPipelineMock("echo").call('Retrieving Dynatrace entity Id and namespace display name...')
        1 * getPipelineMock("echo").call({
            it =~ /.RELATED_NAMESPACE-$entityId%7C$displayName./
        })

        where:
        entityId << ['CLOUD_APPLICATION_NAMESPACE-D9A6DBB354F28E12']
        displayName << ['epf-tests-openshift-nonprod1']
    }
}
