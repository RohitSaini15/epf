package com.cigna.common.pipelinemetrics

import groovy.json.JsonSlurper
import jenkins.plugins.http_request.HttpMode
import jenkins.plugins.http_request.MimeType

/**
 * Class designed to manage calls to and from Dynatrace to present resource metrics to customers
 */
class DynatraceMetrics implements Serializable {

    private final Map dynatraceUrlMap = [
        nonprod : 'https://cvl90376.live.dynatrace.com',
        prod: 'https://bqp39699.live.dynatrace.com',
    ]

    private final Map dynatraceCredsMap = [
        nonprod: 'nonprod-dynatrace-access-token',
        prod: 'prod-dynatrace-access-token',
    ]

    String targetEnv = 'prod'
    String basePath = dynatraceUrlMap[targetEnv]
    String baseCred = dynatraceCredsMap[targetEnv]
    String namespace
    String completeEntityId
    String completeDisplayName
    def script

    void retrieveDynatraceEntityAndDisplay() {
        script.withCredentials([
            script.string(
                credentialsId: baseCred,
                variable: 'PIPELINE_DYNATRACE_ACCESS_TOKEN')
        ]) {
            script.echo('Retrieving Dynatrace entity Id and namespace display name...')
            def response = script.httpRequest(
                url: "${basePath}/api/v2/entities?entitySelector=type(CLOUD_APPLICATION_NAMESPACE),entityName.equals(${namespace})",
                httpMode: HttpMode.GET,
                quiet: true,
                contentType: MimeType.APPLICATION_JSON,
                acceptType: MimeType.APPLICATION_JSON,
                validResponseCodes: '100:599',
                customHeaders: [
                    [
                        maskValue: true,
                        name     : 'Authorization',
                        value    : 'Api-Token ' + script.PIPELINE_DYNATRACE_ACCESS_TOKEN
                    ]
                ]
            )

            if (response.status != 200) {
                script.echo("Response ${response.status} not in acceptable range: ${response.content}")
                return
            }

            def content = response.getContent()

            JsonSlurper jsonSlurper = new JsonSlurper()
            Map<String, Object> parsedResponse = jsonSlurper.parseText(content)
            String entityId = parsedResponse?.entities?.entityId ?: ''
            String displayName = parsedResponse?.entities?.displayName ?: ''
            completeEntityId = entityId.replace('[', '').replace(']', '')
            completeDisplayName = displayName.replace('[', '').replace(']', '')

            script.echo("Entity Id: $completeEntityId")
            script.echo("Display Name: $completeDisplayName")
        }
    }

    void printDynatraceMetricsUrl() {
        retrieveDynatraceEntityAndDisplay()
        if (completeEntityId && completeDisplayName) {
            script.echo(
                """\
                |Follow the below link to see resource metrics for this pipeline:
                |$basePath/#dashboard;gtf=-30m;gf=all;id=\
                8dde7207-a4c9-4bfc-8bbb-12f1853e4571;es=\
                RELATED_NAMESPACE-$completeEntityId%7C$completeDisplayName
                """.stripMargin().replaceAll(/\s\s/, '')
            )
        }
    }

    DynatraceMetrics(String cloudName, def script) {
        this.script = script
        this.namespace = cloudName
    }
}