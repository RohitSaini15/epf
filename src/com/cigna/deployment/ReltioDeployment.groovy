package com.cigna.deployment

import com.cigna.common.phases.PodSelector
import com.cigna.common.utils.FeatureFlags
import com.cigna.common.utils.Utils
import com.cigna.common.kubernetes.PodTemplateCreator
import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import hudson.Functions

import java.nio.file.AccessDeniedException

/**
 * Class to provide an opinionated Reltio deployment
 */
class ReltioDeployment extends Deployment {
    private static final String ARTIFACTORY_URL = 'https://repo.sys.cigna.com/artifactory'
    private static final String OAUTH_URL = 'https://auth.reltio.com/oauth/token'

    ReltioDeployment() {
        additionalValidationItems = [
            'reltio.environmentUrl',
            'reltio.tenantId',
            'reltio.authCredentialsId',
            'reltio.artifactory',
        ]
        containerName = 'mavenv363-jq'
        containerImage = 'enterprise-devops/maven'
        containerVersion = '3.6.3-jq'
    }

    @Override
    Boolean prePodConfig() {
        List<Map> env = [
            [
                name : 'HOME',
                value: '/tmp'
            ]
        ]

        PodTemplateCreator containerTemplate = PodTemplateCreator.newPodTemplateCreator()
        containerTemplate.addContainer(
            containerName,
            "${containerImage}:${containerVersion}",
            100,
            500,
            1000,
            1000,
            env
        )

        additionalPodConfig = [
            volumes   : [],
            containers: [containerTemplate.getContainer(containerName)]
        ]
        super.prePodConfig()
    }

    String artifactPath

    @Override
    void deploy() {
        String reltioType = config.reltio?.reltioType

        if (reltioType == 'mdm') {
            script.echo('Running reltio mdm script')
            psc.podSelector.select(psc,containerName, Utils.cloud(config)) {
                downloadArtifact()
                deployMdmConfig()
                if (config.reltio?.rebuildMatchTable) {
                    rebuildMatchTable()
                }
                if (config.reltio?.reindex) {
                    reindex()
                }
            }
        } else if (reltioType == 'rdm') {
            script.echo('Running reltio rdm script')
            downloadArtifact()
            deployRdmConfig()
        } else if (reltioType == 'salesforce') {
            script.echo('Running reltio salesforce')
            psc.podSelector.select(psc,containerName, Utils.cloud(config)) {
                downloadArtifact()
                deployMdmConfig()
            }
        } else {
            throw new UnsupportedOperationException(
                'Must provide reltioType (mdm, rdm, salesforce)')
        }
    }

    void printWithNoTrace(String cmd, Boolean returnStdout = false) {
        script.sh(script: '#!/bin/sh -e\n' + cmd, returnStdout: returnStdout)
    }

    @Override
    List validate(boolean requiresBranchPattern = false) {
        String reltioType = config.reltio?.reltioType

        List issues = []
        if (reltioType == 'rdm') {
            additionalValidationItems += [
                'reltio.pathToPropertiesFile',
                'reltio.pathToMappingFile',
            ]
        } else if (reltioType == 'salesforce') {
            additionalValidationItems += [
                'reltio.profileName',
            ]
        } else if (reltioType == 'mdm') {
            additionalValidationItems += [
            ]
        }
        List validationIssues = super.validate(requiresBranchPattern)
        validationIssues + issues
    }
    
    void sleepForWaitTime() {
        sleep(config.reltio?.statusWaitTime ? (config.reltio?.statusWaitTime * 1000) : 10000 )
    }

    void deployMdmConfig() {
        String reltioType = config.reltio?.reltioType
        script.withCredentials(
            [
                script.usernamePassword(
                    credentialsId: "${config.reltio?.credentialsId}",
                    usernameVariable: 'RELTIO_USER',
                    passwordVariable: 'RELTIO_PASS'
                ),
                script.string(
                    credentialsId: "${config.reltio?.authCredentialsId}",
                    variable: 'RELTIO_AUTH_TOKEN'
                ),
            ]
        ) {
            Boolean displayResponse = config?.reltio?.displayResponse ?: false
            if (config.reltio.containsKey('configProperties')) {
                addConfigProperties()
            }

            String url = ''
            if (reltioType == 'mdm') {
                url = "https://${config.reltio.environmentUrl}/reltio/api/${config.reltio.tenantId}/configuration"
            } else if (reltioType == 'salesforce') {
                url = "https://${config.reltio.environmentUrl}/configuration/${config.reltio.tenantId}"
                +"/${config.reltio.profileName}/mapping"
            }
            script.echo("Uploading configuration from: ${artifactPath}")

            script.sh("cat $artifactPath")

            String accessToken = reltioAccessToken()
            String jsonResponse = script.sh(
                script: """curl -s -d @${artifactPath} \
                    -H \"Authorization: Bearer ${accessToken}\" \
                    -H \"Content-Type: application/json\" \
                    -X PUT ${url}""",
                returnStdout: true
            )

            if (displayResponse) {
                printWithNoTrace("echo 'JsonResponse: ${jsonResponse}'", false)
            }

            if ("${jsonResponse}" ==~ /.*Access is denied.*/) {
                printWithNoTrace(
                    "echo 'ACCESS DENIED: ${jsonResponse}'",
                    false
                )
                throw new AccessDeniedException(
                    "ACCESS DENIED: ${jsonResponse}"
                )
            }

            String sourceJson = script.sh(
                script: "cat $artifactPath | jq -c",
                returnStdout: true
            )

            script.echo("Source JSON:\n\n$sourceJson\n\n")

            /*  LazyMap sourceData = new JsonSlurper().parseText(sourceJson)
                LazyMap responseData = new JsonSlurper().parseText(jsonResponse)

                try {
                    script.echo('Comparing source JSON to response JSON.')
                    assert sourceData == responseData
                    script.echo('Source JSON matched response JSON.')
                } catch (AssertionError e) {
                    script.echo(
                        // String concats instead of multi-line string so the margin is clean in the Jenkins
                        // log
                        '###################################################################################\n\n' +

                        'ERROR: source JSON did not match the JSON sent back from Reltio. Please copy the\n' +
                        'JSON below, and paste into your preferred JSON comparison tool.\n\n' +

                        '###################################################################################\n\n' +

                        "Source JSON:\n\n$sourceJson\n\n" +

                        '###################################################################################\n\n' +

                        "Response JSON:\n\n$jsonResponse\n\n" +

                        '###################################################################################'
                    )
                    throw new UnsupportedOperationException(
                        'Source JSON file from your repo did not match the JSON response from Reltio. ' +
                        'Please see the error message above.'
                    )
                } */
        }
    }

    void addConfigProperties() {
        String regexKey = '(.*)(?i)tenantid'
        String regexValue = '^[a-zA-Z0-9]{13,18}$'
        config.reltio.configProperties.each { key, value ->
            if (key.trim().matches(regexKey) && !value.trim().matches(regexValue)) {
                throw new UnsupportedOperationException(
                    "Invalid tenant Id '${value}' for configuration value '${key}'!"
                )
            }

            script.sh("jq '.${key} = \"${value}\"' ${artifactPath} > " \
                  + "config.json && mv config.json ${artifactPath}"
            )
        }
    }

    void reindex() {
        sleepForWaitTime()
        String accessToken = reltioAccessToken()
        String environmentUrl = config?.reltio?.environmentUrl ?: ''
        String tenantId = config?.reltio?.tenantId ?: ''
        String params = 'updateEntities=true'
        params += '&distributed=true'
        params += '&taskPartsCount=4'
        params += '&force=true'
        params += '&enableSeparateIndexing=true'
        params += "&forceIgnoreInStreaming=${config.reltio?.reindex_forceIgnoreInStreaming.toString()}"
        String jsonResponse = '\n' + JsonOutput.prettyPrint(
            script.sh(
                script: """\
                    curl -X POST \
                        -s \
                        -H 'Authorization: Bearer ${accessToken}' \
                        -H 'Content-Type: application/json' \
                        'https://${environmentUrl}/reltio/api/${tenantId}/reindex?${params}'
                """,
                returnStdout: true
            )
        )
        script.echo("Reindex API response: ${jsonResponse}")
    }

    void rebuildMatchTable() {
        sleepForWaitTime()
        String accessToken = reltioAccessToken()
        String environmentUrl = config?.reltio?.environmentUrl ?: ''
        String tenantId = config?.reltio?.tenantId ?: ''
        String params = 'distributed=true'
        params += '&taskPartsCount=4'
        String jsonResponse = '\n' + JsonOutput.prettyPrint(
            script.sh(
                script: """\
                    curl -X POST \
                        -s \
                        -H 'Authorization: Bearer ${accessToken}' \
                        -H 'Content-Type: application/json' \
                        'https://${environmentUrl}/reltio/api/${tenantId}/rebuildmatchtable?${params}'
                """,
                returnStdout: true
            )
        )
        script.echo("Rebuild Match Table API response: ${jsonResponse}")
    }

    // ------------------- MDM end -------------------

    //Shared logic MDM RDM
    @SuppressWarnings(['GStringExpressionWithinString'])
    void downloadArtifact() {
        String reltioType = config.reltio?.reltioType
        script.withCredentials([
            script.usernamePassword(
                credentialsId: "${config.reltio?.artifactory.credentialsId}",
                usernameVariable: 'ARTIFACT_USER',
                passwordVariable: 'ARTIFACT_PASS',
            )
        ]) {
            String repo = "${config.reltio.artifactory.repo}"
            String path = "${config.reltio.artifactory.path}"
            String name = "${config.reltio.artifactory.name}"
            String baseUrl = "${ARTIFACTORY_URL}/${repo}/${path}/${name}"

            script.echo("${baseUrl}")

            String version = script.sh(
                script: "echo \$(curl -s ${baseUrl}/maven-metadata.xml | " \
                          + 'grep latest | sed \"s/.*<latest>\\([^<]*\\)<\\/latest>.*/\\1/\")',
                returnStdout: true
            ).trim()
            String versionUrl = "${baseUrl}/${version}"

            String buildId = script.sh(
                script: "echo \$(curl -s ${versionUrl}/maven-metadata.xml | " \
                          + "grep '<value>' | head -1 | sed \"s/.*<value>\\([^<]*\\)<\\/value>.*/\\1/\")",
                returnStdout: true
            ).trim()
            buildId = buildId ?: version
            String artifactUrl = "${ARTIFACTORY_URL}/${repo}/${path}/${name}/${version}/${name}-${buildId}.tar.gz"

            script.sh("mkdir 'target'")
            script.sh(
                'curl -s -u ${ARTIFACT_USER}:${ARTIFACT_PASS} ' +
                    "${artifactUrl} " +
                    "--output ${name}-${buildId}.gz"
            )
            script.sh("tar -xvzf ${name}-${buildId}.gz -C target")
        }

        artifactPath = script.sh(
            script: 'ls ./target',
            returnStdout: true
        ).trim()

        artifactPath = "./target/${artifactPath}"

        if (reltioType == 'rdm') {
            String propertiesFile = config.reltio.pathToPropertiesFile
            String rdmArtifactName = script.sh(
                script: "sed -n 's/^OUTPUT_FILE=//p' $propertiesFile",
                returnStdout: true
            ).trim()
            artifactPath = "$artifactPath/$rdmArtifactName"
        }

        script.echo("artifact path ${artifactPath}")
    }

    @SuppressWarnings(['GStringExpressionWithinString'])
    String reltioAccessToken() {
        String accessToken

        script.withCredentials(
            [
                script.usernamePassword(
                    credentialsId: config.reltio?.credentialsId,
                    usernameVariable: 'RELTIO_USER',
                    passwordVariable: 'RELTIO_PASS'
                ),
                script.string(
                    credentialsId: config.reltio?.authCredentialsId,
                    variable: 'RELTIO_AUTH_TOKEN'
                ),
            ]
        ) {
            String response = script.sh(
                script: 'curl -X POST ' +
                    '-H "Authorization: Basic ${RELTIO_AUTH_TOKEN}" ' +
                    "\"${OAUTH_URL}" +
                    '?username=${RELTIO_USER}' +
                    '&password=${RELTIO_PASS}' +
                    '&grant_type=password"',
                returnStdout: true
            )

            script.echo("Auth response ${response}")

            Map<String, Object> parsed = script.readJSON(
                text: response
            )

            accessToken = parsed.access_token
            script.echo("accessToken: ${accessToken}")
        }
        accessToken
    }

    //currently not being used
    void validateMappingConfig(String filePath) {
        script.echo("validating json: ${filePath}")
        script.echo("cat '${filePath}'  | jq type")
    }

    // ------------------- Shared logic end -------------------

    // ------------------- Rdm start -------------------

    String[] readJsonFile() {
        script.echo("rdm-lookups.json: $artifactPath")

        String rdmLookupsJsonFile = printWithNoTrace("cat $artifactPath", true)

        String[] lines
        lines = rdmLookupsJsonFile.split('\\r?\\n')

        lines
    }

    String postRDMJsonFile(String jsonContent, String accessToken) {
        String baseUrl = config.reltio?.environmentUrl
        String tenantId = config.reltio?.tenantId

        tenantId = printWithNoTrace("""
                printf "$tenantId"
            """, true)
        script.echo("tenantId: $tenantId")

        String postUrl = "https://${baseUrl}/lookups/$tenantId"

        script.echo('RDM POST started')

        script.sh(script: 'set -x')
        String jsonRdmResponse = script.sh(
            script: """
                    curl -X POST -d '${jsonContent}' \
                    -H 'Authorization: Bearer ${accessToken}' \
                    -H 'Content-type: application/json' \
                    -X POST '${postUrl}'
                """,
            returnStdout: true
        )

        script.echo("jsonRdmResponse: ${jsonRdmResponse}")

        jsonRdmResponse
    }

    void rdmCompareMaps(String line, String response) {
        JsonSlurper jsonSlurper = new JsonSlurper()

        String lineJson = "{\"rdm\": $line}"
        Map lineParsed = jsonSlurper.parseText(lineJson)
        List lineValues = []

        String responseJson = "{\"rdm\": $response}"
        Map responseParsed = jsonSlurper.parseText(responseJson)
        List responseValues = []

        fetchValues(lineParsed.rdm.sourceMappings, lineValues)
        fetchValues(responseParsed.rdm.value.sourceMappings, responseValues)

        lineValues.eachWithIndex { it, i ->
            if (it != responseValues[i]) {
                script.echo(
                    "[ $it ] from lookups does not match [ ${responseValues[i]} ] from Reltio's reponse."
                )
                throw new UnsupportedOperationException(
                    'JSON result does NOT match the JSON file provided!'
                )
            }
        }
    }

    void fetchValues(List parsedJson, List valuesArray) {
        parsedJson.each { sourceMap ->
            sourceMap.values.each { v ->
                valuesArray.add(v.code)
                valuesArray.add(v.value)
            }
        }
    }

    void deployRdmConfig() {
        try {
            String[] lines = readJsonFile()
            String accessToken = reltioAccessToken()
            Integer postCounter = 0

            for (String line : lines) {
                if (postCounter == ( 3 )) {
                    script.echo('reauthorizing Access Token')
                    accessToken = reltioAccessToken()
                    postCounter = 0
                }

                script.echo("line: ${line}")
                String response = postRDMJsonFile(line, accessToken)
                // rdmCompareMaps(line, response)
                postCounter = postCounter + 1
                script.echo("response after POST: ${response}")
            }

            //reportSuccess()
            script.echo('############################')
            script.echo('Success RDM Deployment')
            script.echo('#############################')
        }
        catch (all) {
            if (FeatureFlags.showStackTraces) {
                script.echo(Functions.printThrowable(all))
            }
            script.echo('##############################')
            script.echo(all.message)
            script.echo('#############################')
            //reportFailed()
            script.error('Failed RDM Deployment')
        }
    }

    // ------------------- Rdm end -------------------
}
