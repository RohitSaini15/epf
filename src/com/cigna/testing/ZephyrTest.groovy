package com.cigna.testing

import com.cigna.common.request.CurlRequestor
import com.cigna.common.kubernetes.PodTemplateCreator
import groovy.json.JsonSlurper
import jenkins.plugins.http_request.HttpMode
import jenkins.plugins.http_request.MimeType
/**
 * Defines testing steps for Zephyr
 */
class ZephyrTest extends Testing {
    ZephyrTest() {
        testType = 'integration'
        containerName = 'zbotvlatest'
        containerImage = 'enterprise-devops/zbot'
        containerVersion = 'latest'
        additionalValidationItems = [
            'resultPath',
            'zbotAgentMachine',
            'endDate',
            'startDate',
            'cycleName',
            'testRepositoryPath',
            'automationFramework',
            'projectId',
            'releaseId',
            'folderId',
            'folderName',
            'testEnvironment',
            'credentialsId',
        ]
    }
    Boolean response
    String urlBase
    Integer scheduledTestId
    Integer vortexTestId
    String zbotAgentMachine = testingConfiguration?.zbotAgentMachine

    protected Map zephyrConfiguration
    Map<String, ?> testResponse
    CurlRequestor curlRequestor

    private final Map<String, String> zephyrUrlMap = [
        'dev' : 'https://dev-cigna.yourzephyr.com/flex/services/rest/v3/',
        'prod': 'https://cigna.yourzephyr.com/flex/services/rest/v3/',
    ]

    @Override
    Boolean prePodConfig() {
        List<Map> env = []

        PodTemplateCreator containerTemplate = PodTemplateCreator.newPodTemplateCreator()
        containerTemplate.addContainer(
            containerName,
            "${containerImage}:${containerVersion}",
            'IfNotPresent',
            10,
            50,
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

    @Override
    void runImpl() {
        urlBase = zephyrUrlMap[( testingConfiguration.testEnvironment.toLowerCase() )]
        script.container(containerName) {
            Object var = vortexTest()
            script.echo("var: ${var}")
            scheduleVortexJob(var.responseBody.get('id'))
            if (scheduledTestId) {
                deleteVortexJob(var.responseBody.get('id'))
            } else {
                throw new FailedToDeleteTest('Failed to delete Zephyr Vortex Test.')
            }
        }
    }

    /*
    * Creates vortex scan in Zephyr which takes in a map
    * of parameters from config
    */

    @SuppressWarnings(['UnnecessaryGetter'])
    Object vortexTest() {
        Map createVortexMap = [
            resultPath               : testingConfiguration?.resultPath,
            zbotAgentMachine         : testingConfiguration?.zbotAgentMachine,
            cycleEndDateStr          : testingConfiguration?.endDate,
            cycleStartDateStr        : testingConfiguration?.startDate,
            isDateStr                : true,
            cycleName                : testingConfiguration?.cycleName,
            testRepositoryPath       : testingConfiguration?.testRepositoryPath,
            assignResultsTo          : '-10',
            isReuse                  : false,
            timeStamp                : true,
            jobName                  : 'EPF Zephyr Test Automation',
            jobDescription           : 'Created by EPF',
            automationFramework      : testingConfiguration?.automationFramework,
            projectId                : testingConfiguration?.projectId,
            releaseId                : testingConfiguration?.releaseId,
            jobDetailTcrCatalogTreeId: testingConfiguration?.folderId,
            phaseName                : testingConfiguration?.folderName,
        ]
        String vortexUrl = urlBase + 'automation/job/detail'
        curlRequestor = curlRequestor ?: new CurlRequestor(psc, script)
        curlRequestor.containerName = containerName
        testResponse = curlRequestor.requestJsonWithToken(
            vortexUrl,
            testingConfiguration.credentialsId as String,
            'POST',
            createVortexMap
        )
        vortexTestId = testResponse.responseBody.get('id', 0)
        if (!vortexTestId) {
            throw new FailedToCreateTest('Failed to create Vortex test.')
        }
        script.echo("Vortex Test ID: ${vortexTestId}")
        testResponse
    }

    /*
    * Schedules Vortex Test/Job to run once created
    */

    @SuppressWarnings(['UnnecessaryGetter'])
    void scheduleVortexJob(Integer testId) {
        String scheduleTestUrl = urlBase + 'automation/schedule'

        script.withCredentials([
            script.string(
                credentialsId: testingConfiguration.credentialsId,
                variable: 'ZEPHYR_ACCESS_TOKEN')
        ]) {
            String response = script.httpRequest(
                url: scheduleTestUrl,
                httpMode: HttpMode.POST,
                consoleLogResponseBody: false,
                contentType: MimeType.APPLICATION_JSON,
                acceptType: MimeType.APPLICATION_JSON,
                customHeaders: [
                    [
                        maskValue: true,
                        name     : 'Authorization',
                        value    : 'Bearer ' + script.ZEPHYR_ACCESS_TOKEN
                    ]
                ],
                requestBody: """
            {
                "ids": [
                    "${testId}"
                ]
            }
            """).getContent()

            JsonSlurper jsonSlurper = new JsonSlurper()
            List<Map<String, Object>> parsedResponse = jsonSlurper.parseText(response)
            scheduledTestId = parsedResponse[0].get('id')

            if (scheduledTestId) {
                script.echo("Scheduled Vortex Test with ${scheduledTestId}")
            } else {
                throw new FailedToScheduleTest('Failed to schedule Vortex test.')
            }
        }
    }

    /*
    * Deletes Vortex Test after scheduled
    */

    void deleteVortexJob(Integer testId) {
        String deleteTestUrl = urlBase + 'automation/job/delete'

        script.withCredentials([
            script.string(
                credentialsId: testingConfiguration.credentialsId,
                variable: 'ZEPHYR_ACCESS_TOKEN')
        ]) {
            response = script.httpRequest(
                url: deleteTestUrl,
                httpMode: HttpMode.POST,
                consoleLogResponseBody: false,
                contentType: MimeType.APPLICATION_JSON,
                acceptType: MimeType.APPLICATION_JSON,
                customHeaders: [
                    [
                        maskValue: true,
                        name     : 'Authorization',
                        value    : 'Bearer ' + script.ZEPHYR_ACCESS_TOKEN
                    ]
                ],
                requestBody: """
            {
                "ids": [
                    "${testId}"
                ]
            }
            """).getContent() as Boolean

            if (response) {
                script.echo("Zephyr Vortex Test with ID: ${testId}"
                    + ' has been deleted.')
            } else {
                throw new FailedToDeleteTest('Failed to delete Vortex test.')
            }
        }
        notification.notifyWithAllMethods("Deleted Vortex Test with Schedule ID ${testId}")
    }
}

class FailedToCreateTest extends Exception {
    FailedToCreateTest(String message) {
        super(message)
    }
}

class FailedToScheduleTest extends Exception {
    FailedToScheduleTest(String message) {
        super(message)
    }
}

class FailedToDeleteTest extends Exception {
    FailedToDeleteTest(String message) {
        super(message)
    }
}
