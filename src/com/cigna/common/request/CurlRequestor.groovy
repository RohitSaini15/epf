package com.cigna.common.request

import com.cigna.common.utils.FeatureFlags
import com.cigna.state.PipelineStateContext
import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic
import hudson.Functions

import java.nio.file.Files

/**
 * Class designed to make http requests using curl
 */
class CurlRequestor implements Serializable {
    Object script
    PipelineStateContext psc
    private String containerName
    private String cloudName

    CurlRequestor(PipelineStateContext psc, Object script = null) {
        this.psc = psc
        this.script = script
    }

    void setContainerName(String name) {
        this.containerName = name
    }

    void setCloudName(String name) {
        this.cloudName = name
    }

    void setScript(Object sc) {
        this.script = sc
    }

    List evaluateListEnvVars(List varsList) {
        List listEvaluated = []
        varsList.each { listItem ->
            listEvaluated += evaluateItem(listItem)
        }
        listEvaluated
    }

    Map evaluateMapEnvVars(Map varsMap) {
        Map mapEvaluated = [:]
        varsMap.each { key, value ->
            mapEvaluated[evaluateKey(key)] = evaluateItem(value)
        }
        mapEvaluated
    }

    Map<String, Object> requestJson(
        String url,
        String credentialsId,
        String method,
        Map requestBodyMap = [:],
        String contentType = 'application/json',
        int timeOut = 60
    ) {
        script.withCredentials(
            [
                script.usernameColonPassword(
                    credentialsId: credentialsId,
                    variable: 'userId'
                )
            ]
        ) {
            requestJsonImpl(
                url,
                "-u '${script.userId}'",
                method, requestBodyMap, contentType, 'standard-', timeOut
            )
        }
    }

    Map<String, ?> requestJsonWithToken(
        String url,
        String credentialName,
        String method,
        Map requestBodyMap = [:],
        String contentType = 'application/json',
        String tokenType = 'Bearer'
    ) {
        script.withCredentials(
            [
                script.string(
                    credentialsId: credentialName,
                    variable: 'bearerToken'
                )
            ]
        ) {
            requestJsonImpl(
                url,
                "--header 'Authorization: $tokenType ${script.bearerToken}'",
                method, requestBodyMap, contentType, 'token-'
            )
        }
    }

    /**
     * Make a curl request but you want to pass the variable
     * @param url
     * @param credential
     * @param method
     * @param requestBodyMap
     * @param contentType
     * @return Map of response body and response code
     */
    Map<String, ?> requestJsonWithLiteralCred(
        String url,
        String credential,
        String method,
        Map requestBodyMap = [:],
        String contentType = 'application/json'
    ) {
        requestJsonImpl(url, "-u '$credential'", method, requestBodyMap, contentType, 'creds-')
    }

    Map<String, ?> requestJsonImpl(
        String url,
        String credentialPart,
        String method,
        Map requestBodyMap = [:],
        String contentType = 'application/json',
        String tag = '',
        int timeOut = 60
    ) {
        String responseCode = null, responseBody = null
        Object responseBodyObject = null
        Files.createTempFile(tag + 'cred-response-', '.json').toAbsolutePath().toString().with { responseFilePath ->
            Files.createTempFile(tag + 'cred-request-body-', '.json').toAbsolutePath().toString().with { requestBodyFilePath ->
                if (cloudName == null) {
                    cloudName = psc.podSelector.findCurrentCloudName()
                }
                psc.podSelector.select(psc, containerName, cloudName) {
                    String commandString =
                        "#!/bin/sh -e\ncurl -sSL -o $responseFilePath -w \"%{http_code}\" "
                    commandString += "-k -X $method -m $timeOut "
                    commandString += credentialPart
                    commandString += " -H 'Content-Type: $contentType' "
                    commandString += "-H 'Accept: application/json' "
                    if (requestBodyMap) {
                        commandString += contentType.equals("multipart/form-data") ?
                            bodyToFormParams(requestBodyMap) : bodyToJsonFile(requestBodyMap, requestBodyFilePath)
                    }
                    commandString += " '$url'"
                    responseCode = script.sh(script: commandString, returnStdout: true)

                    script.echo((commandString - credentialPart) + ' Response Code: ' + responseCode)

                    responseBody = script.sh(
                        script: "cat $responseFilePath",
                        returnStdout: true
                    )
                    script.echo(
                        "Response Body: ${FeatureFlags.debug ? "\n $responseBody" : "to view set FeatureFlags.debug == true"}"
                    )
                }
                if (responseBody) {
                    try {
                        JsonSlurperClassic jsonSlurper = new JsonSlurperClassic()
                        responseBodyObject = jsonSlurper.parseText(responseBody)
                    } catch (all) {
                        script.echo("Error parsing response. Response: \n $responseBody, error: ${all.message}")
                        if (FeatureFlags.showStackTraces) {
                            script.echo(Functions.printThrowable(all))
                        }
                    }
                }
            }
        }

        [responseCode: responseCode?.toInteger() ?: 400, responseBody: responseBodyObject]
    }

    String bodyToJsonFile(Map requestBodyMap, String requestBodyFilePath) {
        Map requestBodyMapEval = evaluateMapEnvVars(requestBodyMap)
        String requestBody = JsonOutput.toJson(requestBodyMapEval)
        script.sh(
            script: "echo '$requestBody' > $requestBodyFilePath",
            returnStdout: false
        )
        "-d @$requestBodyFilePath"
    }

    String bodyToFormParams(Map requestBodyMap) {
        Map requestBodyMapEval = evaluateMapEnvVars(requestBodyMap)
        String fileFormData = requestBodyMapEval.collect { key, value ->
            value = value.toString().replace('"', '\\"')
            "-F \"$key=$value\""
        }
            .join(' ')
        fileFormData
    }

    private Object evaluateItem(Object item) {
        Object listItemEval
        if (item.getClass() == LinkedHashMap || item.getClass() == Map) {
            listItemEval = evaluateMapEnvVars((Map) item)
        } else if (item.getClass() == ArrayList || item.getClass() == List) {
            listItemEval = evaluateListEnvVars((List) item)
        } else {
            if (item.toString().contains('$')) {
                listItemEval = script.sh(script: "echo \"$item\"", returnStdout: true).trim()
            } else {
                listItemEval = item
            }
        }
        listItemEval
    }

    private Object evaluateKey(Object key) {
        Object keyEval
        if (key.toString().contains('$')) {
            keyEval = script.sh(script: "echo \"${key}\"", returnStdout: true).trim()
        } else {
            keyEval = key
        }
        keyEval
    }
}
