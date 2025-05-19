package com.cigna.deployment

import com.cigna.common.phases.PodSelector
import com.cigna.common.kubernetes.PodTemplateCreator
import com.cigna.common.utils.Utils
import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

/**
 * Class to provide an opinionated Dynatrace deployment
 */
@SuppressWarnings(['DuplicateNumberLiteral', 'DuplicateMapLiteral'])
class DynatraceDeployment extends Deployment {
    private static final String ARTIFACTORY_URL = 'https://repo.sys.cigna.com/artifactory'

    DynatraceDeployment() {
        containerName = 'mavenv363-jq'
        containerImage = 'enterprise-devops/maven'
        containerVersion = '3.6.3-jq'
        additionalValidationItems = [
            'dynatrace.apiToken',
            'dynatrace.tenantId',
            'dynatrace.artifactory',
        ]
    }

    @Override
    Boolean prePodConfig() {

        List<Map> env = [
            [
                name : 'HOME',
                value: '/tmp'
            ],
        ]

        PodTemplateCreator containerTemplate = PodTemplateCreator.newPodTemplateCreator()
        containerTemplate.addContainer(
            containerName,
            "${containerImage}:${containerVersion}",
            100,
            500,
            500,
            500,
            env
        )

        additionalPodConfig = [
            'volumes'   : [],
            'containers': [containerTemplate.getContainer(containerName)]
        ]

        super.prePodConfig()
    }

    String deployStatus = 'Deploy'
    String artifactPath
    List jsonMappings = []

    @Override
    void deploy() {
        script.echo('Running config script')
        psc.podSelector.select(psc,podTemplateContainerName, Utils.cloud(config)) {
            downloadArtifact()
            configDeployToDynatrace()
        }
    }

    void printWithNoTrace(String cmd, Boolean returnStdout = false) {
        script.sh(
            script: '#!/bin/sh -e\n' + cmd,
            returnStdout: returnStdout
        )
    }

    @SuppressWarnings(['NoDef', 'VariableTypeRequired', 'NestedForLoop'])
    void collectJSONMappings() {
        List<Map<String, Object>> jsonFiles = script.findFiles(glob: artifactPath)
        script.echo("jsonFiles ${jsonFiles}")

        List files = []
        def changeLogSets = script.currentBuild.changeSets
        for (entries in changeLogSets) {
            for (entry in entries) {
                for (file in entry.affectedFiles) {
                    files += ( ( ( file.path ).toString() ).trim() ).split('/')[-1]
                }
            }
        }
        script.echo("files:${files}")
        for (jsonfile in jsonFiles) {
            script.echo("jsonfile:${jsonfile}")
            if (files.contains(( ( jsonfile.toString() ).trim() ).split('/')[-1])) {
                script.echo("changed file:${jsonfile}")
                jsonMappings += jsonfile
            }
        }
    }

    @SuppressWarnings(['NoDef', 'VariableTypeRequired', 'NestedForLoop', 'MethodSize'])
    void configDeployToDynatrace() {
        List issues = []
        String tenantId = config.dynatrace.tenantId
        String apiToken = config.dynatrace.apiToken

        script.echo('jsonGetAllStarted')
        String jsonGetAllResponse = '\n' + JsonOutput.prettyPrint(
            script.sh(
                script: """curl --location --request GET \
                 'https://${tenantId}.live.dynatrace.com/api/v2/slo?pageSize=1000' \
                --header 'Authorization: ${apiToken}' \
                 """,
                returnStdout: true
            )
        )

        script.echo("jsonGetAllComplete ${jsonGetAllResponse}")

        collectJSONMappings()
        script.echo("changed files ${jsonMappings}")

        def getAlljsonSlurper = new JsonSlurper()
        def getAllObject = getAlljsonSlurper.parseText(jsonGetAllResponse)
        if (!jsonMappings) {
            printWithNoTrace(
                "echo 'No changed files to deploy.'",
                false
            )
        }
        for (jsonFile in jsonMappings) {
            //get slo name of slo file to deploy
            script.echo('Deploy the file')
            script.echo("${jsonFile}")
            def catJson = printWithNoTrace("cat ${jsonFile}", true)
            script.echo("catJson ${catJson}")
            def catJsonSlurper = new JsonSlurper()
            def catJsonObject = catJsonSlurper.parseText(catJson)
            String catJsonName = catJsonObject['name']
            script.echo("catJsonName ${catJsonName}")

            Boolean sloDeployed = false

            getAllObject.slo.each { sloItem ->
                String sloName = sloItem['name']
                script.echo("sloItem name ${sloName}")

                //if slo exists and is found in GET slo list, do PUT
                if (( sloName == catJsonName ) && !sloDeployed) {
                    String sloId = sloItem['id']
                    script.echo("sloId ${sloId}")

                    script.echo('jsonResponsePutStarted')

                    String jsonResponsePut = '\n' + JsonOutput.prettyPrint(
                        script.sh(
                            script: """
                            curl --location --request PUT \
                            'https://${tenantId}.live.dynatrace.com/api/v2/slo/${sloId}' \
                            --header 'Authorization: ${apiToken}' \
                            --header 'Content-Type: application/json' \
                            -d @${jsonFile}
                        """, returnStdout: true
                        )
                    )

                    script.echo("jsonResponsePutCompleted ${jsonResponsePut}")

                    if ("${jsonResponsePut}" ==~ /.*error.*/) {
                        printWithNoTrace(
                            "echo 'SLO failed on PUT API call ${jsonResponsePut}'",
                            false
                        )
                        issues.add("${catJsonName} 'existing SLO found, failed to deploy to API PUT'")
                    } else {
                        printWithNoTrace(
                            "echo 'Successfully deployed SLO ${jsonResponsePut}'",
                            false
                        )
                        sloDeployed = true
                    }
                }
            }

            //if slo is not found in GET list, treat as new SLO, POST to API
            if (!sloDeployed) {
                script.echo('jsonResponsePostStarted')
                String jsonResponsePost = '\n' + JsonOutput.prettyPrint(
                    script.sh(
                        script: """curl --location --request POST \
                        'https://${tenantId}.live.dynatrace.com/api/v2/slo/' \
                        --header 'Authorization: ${apiToken}' \
                        --header 'Content-Type: application/json' \
                        -d @${jsonFile}
                        """,
                        returnStdout: true
                    )
                )
                script.echo("jsonResponsePostCompleted ${jsonResponsePost}")
                if ("${jsonResponsePost}" ==~ /.*error.*/) {
                    printWithNoTrace(
                        "echo 'POST API error ${jsonResponsePost}'",
                        false
                    )
                    issues.add("${catJsonName} 'New SLO failed to deploy to API via POST call'")
                } else {
                    printWithNoTrace(
                        "echo 'Successfully deployed SLO ${jsonResponsePost}'",
                        false
                    )
                    sloDeployed = true
                }
            }
        }
        if (!issues.isEmpty()) {
            for (issue in issues) {
                printWithNoTrace(
                    "echo 'Slo deployment failure: ${issue}'",
                    false
                )
                throw new UnsupportedOperationException(
                    'Deployment failed.'
                )
            }
        }
    }

    void downloadArtifact() {
        script.withCredentials([
            script.usernamePassword(
                credentialsId: "${config.dynatrace?.artifactory.credentialsId}",
                usernameVariable: 'ARTIFACT_USER',
                passwordVariable: 'ARTIFACT_PASS',
            )
        ]) {
            String repo = "${config.dynatrace.artifactory.repo}"
            String path = "${config.dynatrace.artifactory.path}"
            String name = "${config.dynatrace.artifactory.name}"
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
            script.sh("curl -s -u ${script.ARTIFACT_USER}:${script.ARTIFACT_PASS} ${artifactUrl} " \
                  + "--output ${name}-${buildId}.gz")
            script.sh("tar -xvzf ${name}-${buildId}.gz -C target")
            artifactPath = 'target/'
        }
    }
}
