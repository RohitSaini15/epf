package com.cigna.deployment

import static java.time.LocalDateTime.now

import com.cloudbees.groovy.cps.NonCPS
import jenkins.plugins.http_request.HttpMode
import jenkins.plugins.http_request.MimeType
import jenkins.plugins.http_request.ResponseContentSupplier
import com.cigna.common.kubernetes.PodTemplateCreator

import java.util.concurrent.TimeUnit

/**
 * Deployment via Ansible
 *
 * This Deployment phase will trigger an Ansible Tower Job Template and monitor its execution until it either succeeds,
 * fails, or is cancelled.
 */
class AnsibleTowerDeployment extends Deployment {
    public static final long JOB_EXEC_WAIT_TIME = 30000
    public static final int HTTP_OK = 200
    public static final int HTTP_CREATED = 201

    AnsibleTowerDeployment() {
        containerName = 'epf-curlvlatest'
        containerImage = 'enterprise-devops/epf-curl'
        containerVersion = 'latest'
        additionalValidationItems = [
            'ansibleTower.api.credentialsId', 'ansibleTower.template.id',
            'ansibleTower.template.name',
        ]
    }

    @Override
    Boolean prePodConfig() {
        List<Map> env = []

        PodTemplateCreator containerTemplate = PodTemplateCreator.newPodTemplateCreator()
        containerTemplate.addContainer(
            containerName,
            "${containerImage}:${containerVersion}",
            100,
            1000,
            500,
            1000,
            env
        )

        additionalPodConfig = [
            'volumes'   : [],
            'containers': [containerTemplate.getContainer(containerName)]
        ]
        super.prePodConfig()
    }

    String deployStatus = 'Deploy'

    @Override
    void deploy() {
        // validate() method already confirmed existence of child maps, safe to dereference
        Map<String, Object> ansibleConfig = deploymentConfiguration.ansibleTower as Map<String, Object>
        Map<String, String> apiConfig = ansibleConfig.api as Map<String, String>
        Map<String, Object> templateConfig = ansibleConfig.template as Map<String, Object>
        Map<String, Object> surveyConfig = templateConfig?.survey as Map<String, Object>

        String towerURL = ansibleConfig?.ansibleEnvEndpoint ?: 'https://ansibletower.sys.cigna.com/api/v2'
        String ansibleCreds = apiConfig?.credentialsId ?: 'prd-ansible-access-token'
        script.withCredentials([
            script.string(
                credentialsId: ansibleCreds,
                variable: 'EPF_ANSIBLE_ACCESS_TOKEN')
        ]) {
            String jobID = issueDeployRequest(towerURL, surveyConfig, templateConfig)
            String currentStatus = 'new'
            String ansibleResponse = ''
            long startTime = System.nanoTime()
            int numPolls = 0
            List<String> activeStatuses = ['new', 'pending', 'waiting', 'running']
            List<String> failureStatuses = ['failed', 'error', 'canceled']
            while (currentStatus in activeStatuses) {
                currentStatus = getDeploymentStatus(towerURL, jobID)
                numPolls++

                if (currentStatus in activeStatuses) {
                    script.echo(
                        "${now()} - # ${numPolls} - Monitoring ansible-tower template job '$jobID' - " +
                            "duration ${TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime)} seconds")
                    sleep(config.ansibleTower?.statusWaitTime ? (config.ansibleTower.statusWaitTime * 1000) : JOB_EXEC_WAIT_TIME)
                } else if (currentStatus in failureStatuses) {
                    ansibleResponse = getAnsibleResponse(towerURL, jobID)
                    // The below echo statement will be printed irrespective of debug value
                    script.echo("Failed Response ${ansibleResponse}")
                    throw new AnsibleTowerJobFailed(
                        "Job Template '${templateConfig.name}' failed with status '$currentStatus' ")
                }
            }
            ansibleResponse = getAnsibleResponse(towerURL, jobID)
            script.echo("Ansible Tower Response: ${ansibleResponse}")
        }
    }

    private String issueDeployRequest(String towerURL, Map<String, Object> sc,
                                      Map<String, Object> templateConfig) {
        String limit = templateConfig?.limit ?: ''
        String scm_branch = templateConfig?.scm_branch ?: ''
        String[] convertedLisOfCredentialIdInt = new String()
        String templateCredentialId = templateConfig?.credentialsId ?: ''
        if (templateCredentialId != '') {
            //templateConfig?.credentialsId ?: throwException(
            //        'Ansible Tower template credentialsId field must be defined')
            String[] listOfCredentialId = templateCredentialId.split(',')

            for (String values : listOfCredentialId) {
                convertedLisOfCredentialIdInt += getCredentialsId(towerURL, values)
            }
        }
        int inventoryId
        String inventoryName = templateConfig?.inventoryId ?: ''
        if (inventoryName != '') {
            inventoryId = getInventoryId(towerURL, inventoryName)
        }
        String variables = sc.inject([]) { r, e ->
            r.add("\"${e.key}\":\"${e.value}\"")
            r
        }.join(',\n')

        String requestBody = """
                    |{
                    |  ${limit == '' ? '' : "\"limit\" : \"$limit\","}
                    |  ${scm_branch == '' ? '' : "\"scm_branch\" : \"$scm_branch\","}
                    |  "credentials" : $convertedLisOfCredentialIdInt,
                    |  ${inventoryName == '' ? '' : "\"inventory\" : \"$inventoryId\","}
                    |  "extra_vars": {
                    |    ${variables}
                    |  }
                    |}""".stripMargin()

        if (deploymentConfiguration.ansibleTower?.debug) {
            script.echo("Request Body: ${requestBody}")
        }

        ResponseContentSupplier response = script.httpRequest(
            url: "${towerURL}/job_templates/${templateConfig.id}/launch/",
            httpMode: HttpMode.POST,
            consoleLogResponseBody: deploymentConfiguration.ansibleTower?.debug,
            contentType: MimeType.APPLICATION_JSON,
            acceptType: MimeType.APPLICATION_JSON,
            customHeaders: [
                [
                    maskValue: true,
                    name     : 'Authorization',
                    value    : 'Bearer ' + script.EPF_ANSIBLE_ACCESS_TOKEN
                ]
            ],
            requestBody: requestBody
        )
        if (![HTTP_OK, HTTP_CREATED].contains(response.status)) {
            throw new FailedToExecuteTemplate(
                "Unable to execute Ansible Tower template '${templateConfig.name}': " +
                    "${response.status} [${response.content}]"
            )
        }

        Map<String, Object> responseJSON = script.readJSON(
            text: response.content
        )

        responseJSON.job as String
    }

    private int getCredentialsId(String towerURL, String credentialsIdStr) {
        ResponseContentSupplier response = script.httpRequest(
            url: "${towerURL}/credentials?name=${URLEncoder.encode(credentialsIdStr, 'UTF-8')}",
            httpMode: HttpMode.GET,
            consoleLogResponseBody: deploymentConfiguration.ansibleTower?.debug,
            contentType: MimeType.APPLICATION_JSON,
            acceptType: MimeType.APPLICATION_JSON,
            customHeaders: [
                [
                    maskValue: true,
                    name     : 'Authorization',
                    value    : 'Bearer ' + script.EPF_ANSIBLE_ACCESS_TOKEN
                ]
            ])

        if (![HTTP_OK].contains(response.status)) {
            throw new FailedToGetCredentialsID(
                "Unable to fetch Ansible Tower credentials with name '$credentialsIdStr': " +
                    "${response.status} [${response.content}]"
            )
        }

        Map<String, String> responseJSON = script.readJSON(
            text: response.content
        )

        /**
         * If the count is 1 it means we have exactly 1 credential with this name, 0 means no credential was found
         * & >1 means more than 1 was found and therefore it is ambiguous.
         */
        if (responseJSON.count != 1) {
            throw new FailedToGetCredentialsID(
                "Unable to fetch Ansible Tower credentials with name '$credentialsIdStr': " +
                    "count ${responseJSON.count} status ${response.status} [${response.content}]"
            )
        }
        responseJSON.results[0].id as int
    }
    /**
     * get the inventory id from tower by giving inventory name as i/p
     * @param towerURL
     * @param credentialsIdStr
     * @return inventoryId integer value
     */
    private int getInventoryId(String towerURL, String inventoryIdStr) {
        ResponseContentSupplier response = script.httpRequest(
            url: "${towerURL}/inventories?name=${URLEncoder.encode(inventoryIdStr, 'UTF-8')}",
            httpMode: HttpMode.GET,
            consoleLogResponseBody: deploymentConfiguration.ansibleTower?.debug,
            contentType: MimeType.APPLICATION_JSON,
            acceptType: MimeType.APPLICATION_JSON,
            customHeaders: [
                [
                    maskValue: true,
                    name     : 'Authorization',
                    value    : 'Bearer ' + script.EPF_ANSIBLE_ACCESS_TOKEN
                ]
            ])

        if (![HTTP_OK].contains(response.status)) {
            throw new FailedToGetInventoryID(
                "Unable to fetch Ansible Tower inventories with name '$inventoryIdStr': " +
                    "${response.status} [${response.content}]"
            )
        }

        Map<String, String> responseJSON = script.readJSON(
            text: response.content
        )

        /**
         * If the count is 1 it means we have exactly 1 credential with this name, 0 means no credential was found
         * & >1 means more than 1 was found and therefore it is ambiguous.
         */
        if (responseJSON.count != 1) {
            throw new FailedToGetInventoryID(
                "Unable to fetch Ansible Tower inventory with name '$inventoryIdStr': " +
                    "count ${responseJSON.count} status ${response.status} [${response.content}]"
            )
        }
        responseJSON.results[0].id as int
    }

    void throwAnsibleException(String msg) {
        throw new UnknownEnvironmentException(msg)
    }

    /**
     * new: New
     * pending: Pending
     * waiting: Waiting
     * running: Running
     * successful: Successful
     * failed: Failed
     * error: Error
     * canceled: Canceled
     *
     * @param towerURL
     * @param jobID
     * @return string Current job status
     */
    private String getDeploymentStatus(String towerURL, String jobID) {
        ResponseContentSupplier response = script.httpRequest(
            url: "${towerURL}/jobs/${jobID}/",
            httpMode: HttpMode.GET,
            consoleLogResponseBody: deploymentConfiguration.ansibleTower?.debug,
            contentType: MimeType.APPLICATION_JSON,
            acceptType: MimeType.APPLICATION_JSON,
            customHeaders: [
                [
                    maskValue: true,
                    name     : 'Authorization',
                    value    : "Bearer ${script.EPF_ANSIBLE_ACCESS_TOKEN}"
                ]
            ])

        Map<String, String> responseJSON = script.readJSON(
            text: response.content
        )

        if (deploymentConfiguration.ansibleTower?.debug) {
            script.echo("Response: ${responseJSON}")
        }

        responseJSON.status
    }

    /**
     * @param towerURL
     * @param jobID
     * @return string Current Ansible job stdout
     */
    private String getAnsibleResponse(String towerURL, String jobID) {
        ResponseContentSupplier response = script.httpRequest(
            url: "${towerURL}/jobs/${jobID}/stdout/",
            httpMode: HttpMode.GET,
            consoleLogResponseBody: deploymentConfiguration.ansibleTower?.debug,
            contentType: MimeType.APPLICATION_JSON,
            acceptType: MimeType.APPLICATION_JSON,
            customHeaders: [
                [
                    maskValue: true,
                    name     : 'Authorization',
                    value    : "Bearer ${script.EPF_ANSIBLE_ACCESS_TOKEN}"
                ]
            ])

        Map<String, String> responseJSON = script.readJSON(
            text: response.content
        )

        responseJSON.content
    }

    class UnknownEnvironmentException extends Exception {
        UnknownEnvironmentException(String message) {
            super(message)
        }
    }

    class FailedToGetCredentialsID extends Exception {
        FailedToGetCredentialsID(String message) {
            super(message)
        }
    }

    class FailedToGetInventoryID extends Exception {
        FailedToGetInventoryID(String message) {
            super(message)
        }
    }

    class FailedToExecuteTemplate extends Exception {
        FailedToExecuteTemplate(String message) {
            super(message)
        }
    }

    class AnsibleTowerJobFailed extends Exception {
        AnsibleTowerJobFailed(String message) {
            super(message)
        }
    }
}
