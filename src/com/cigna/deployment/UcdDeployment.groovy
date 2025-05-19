package com.cigna.deployment

import com.cigna.common.request.CurlRequestor
import com.cigna.common.kubernetes.PodTemplateCreator

/**
 * Deployment to a Generic Ucd Container
 */
class UcdDeployment extends Deployment {

    static final Integer MAXREQUESTS = 8
    static final Integer SUCCESSCODE = 200
    static final Integer SLEEPTIME = 30000
    Integer requestCounter = 0

    UcdDeployment() {
        containerName = 'epf-curlvlatest'
        containerImage = 'enterprise-devops/epf-curl'
        containerVersion = 'latest'
        additionalValidationItems = ['ucd.application', 'ucd.ucdEnvironment', 'ucd.credentialsId']
    }

    protected CurlRequestor curlRequestor
    String urlBase
    String urlLogs
    String agentName
    Map ucdConfiguration
    List deployProcesses
    Map previousEnvVersions = [:]

    String deployStatus = 'Deploy'
    Map<String, String> ucdUrlMap = [
        'dev' : 'https://nudeploy.sys.cigna.com:8443',
        'prod': 'https://udeploy.sys.cigna.com:8443',
    ]

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

    Map<String, String> osLimits = [cpu: containerCpu, memory: containerMemory]

    void sleepForWaitTime() {
        sleep(ucdConfiguration?.statusWaitTime ? (ucdConfiguration.statusWaitTime * 1000) : SLEEPTIME)
    }

    List retrieveEnvironmentsInApplication(String application) {
        String url
        String urlEncodedApp = URLEncoder.encode(application)
        url = urlBase + "/application/environmentsInApplication?application=${urlEncodedApp}"
        Object response = curlRequestor.requestJson(
            url,
            ucdConfiguration.credentialsId,
            'GET',
            [:],
            'application/x-www-form-urlencoded'
        )
        if (response.responseCode != SUCCESSCODE) {
            String message = 'Failed to retrieve environments in application\n'
            message += "Response Code: ${response.responseCode}\n"
            message += "Response Body: ${response.responseBody}\n"
            throw new FailedToRequestEnvironmentsInApplication(message)
        }
        response.responseBody
    }

    List retrieveComponentsInEnvironment(String envUUID) {
        String url
        url = urlBase[0..-5] + "/rest/deploy/environment/${envUUID}/latestDesiredInventory"\
         + '/true?rowsPerPage=10000&pageNumber=1&orderField=name&sortType=desc'
        Object response = curlRequestor.requestJson(
            url,
            ucdConfiguration.credentialsId,
            'GET',
            [:],
            'application/x-www-form-urlencoded'
        )
        if (response.responseCode != SUCCESSCODE) {
            String message = 'Failed to retrieve components in environment\n'
            message += "Response Code: ${response.responseCode}\n"
            message += "Response Body: ${response.responseBody}\n"
            throw new FailedToRequestComponentsInEnvironment(message)
        }
        response.responseBody
    }

    List retrieveComponentVersions(Map<String, Object> component) {
        String componentName = URLEncoder.encode(component.name)
        String url = urlBase + "/component/versions?component=${componentName}"
        Map response = curlRequestor.requestJson(
            url,
            ucdConfiguration.credentialsId,
            'GET',
            [:],
            'application/x-www-form-urlencoded'
        )
        if (response.responseCode != SUCCESSCODE) {
            String message = 'Failed to retrieve components in environment\n'
            message += "Response Code: ${response.responseCode}\n"
            message += "Response Body: ${response.responseBody}\n"
            throw new FailedToRequestComponentVersions(message)
        }
        response.responseBody
    }

    void versionImport(Map<String, Object> component) {
        String url
        String componentName = component.name
        String versionType = component?.version?.type ?: ''
        Map requestMap = [
            component : componentName,
            properties: [:],
        ]
        if (versionType == 'Maven') {
            requestMap.properties['version'] = component.version.version ?: ''
        } else if (versionType == 'Git') {
            requestMap.properties['versionOrTag'] = component.version.version
            requestMap.properties['versionName'] = component.version.name ?: ''
        } else {
            requestMap.properties['version'] = ''
            requestMap.properties['snapshotVersionSuffix'] = ''
            requestMap.properties['description'] = 'Import Via EPF'
        }
        url = urlBase + '/component/integrate'
        Map response = curlRequestor.requestJson(
            url,
            ucdConfiguration.credentialsId,
            'PUT',
            requestMap)
        if (response.responseCode != SUCCESSCODE) {
            String message = 'Failed to request application process\n'
            message += "Response Code: ${response.responseCode}\n"
            message += "Response Body: ${response.responseBody}\n"
            throw new FailedToImportVersion(message)
        }
    }

    void waitForVersionImport(Map<String, Object> component) {
        Boolean doWaitForVersionImport = true
        Integer tryCount = 0
        String versionEval = script.sh(
            script: "echo \"${component.version.version}\"",
            returnStdout: true
        ).trim()
        while (doWaitForVersionImport) {
            if (tryCount > 10) {
                doWaitForVersionImport = false
                script.echo('Failed to import version. Failing...')
                throw new FailedToImportVersion(message)
            }
            List componentVersions = retrieveComponentVersions(component)
            componentVersions.each { version ->
                if (version.name == versionEval) {
                    script.echo('Version Imported. Continuing...')
                    doWaitForVersionImport = false
                }
            }
            if (doWaitForVersionImport) {
                script.echo('Awaiting version to be available...')
                sleepForWaitTime()
                tryCount += 1
            }
        }
    }

    void saveCurrentProcessDeployState(Map process) {
        List environmentIds = retrieveEnvironmentsInApplication(process.application)
        String applicationEnvironmentUUID
        environmentIds.each { item ->
            if (item.name == process.environment) {
                applicationEnvironmentUUID = item.id
            }
        }
        List componentsInEnvironment = retrieveComponentsInEnvironment(applicationEnvironmentUUID)
        List versions = process.versions
        previousEnvVersions[process.application] = [( process.environment ): [:]]
        versions.each { version ->
            componentsInEnvironment.each { componentInEnv ->
                if (version.component == componentInEnv.component.name) {
                    previousEnvVersions[
                        process.application
                    ][
                        process.environment
                    ][
                        version.component
                    ] = componentInEnv.version.name
                }
            }
        }
    }

    Map retrieveApplicationProcessRequestStatus(String requestId) {
        String url = urlBase + "/applicationProcessRequest/requestStatus?request=${requestId}"
        Map response = curlRequestor.requestJson(
            url,
            ucdConfiguration.credentialsId,
            'GET',
            [:],
            'application/x-www-form-urlencoded'
        )
        if (response.exitCode == 408 && requestCounter < MAXREQUESTS) {
            requestCounter += 1
            response.responseBody = [status: 'NONE', result: 'A timeout occured']
        } else if (response.responseCode != SUCCESSCODE) {
            String message = 'Failed to request application process\n'
            message += "Response Code: ${response.responseCode}\n"
            message += "Response Body: ${response.responseBody}\n"
            throw new FailedToRequestApplicationProcessStatus(message)
        }
        response.responseBody
    }

    void waitForApplicationProcessRequest(String requestId) {
        List pendingStatusList = [
            'COMPENSATING',
            'EXECUTING',
            'FAULTING',
            'PENDING',
            'CANCELING',
        ]
        List failedStatusList = ['FAULTED']
        List completedStatusList = ['CLOSED', 'INITIALIZED', 'UNINITIALIZED']
        List pendingResultList = ['NONE', 'AWAITING APPROVAL', 'UNINITIALIZED']
        List failedResultList = ['APPROVAL REJECTED', 'CANCELED', 'FAULTED', 'FAILED TO START']
        List completedResultList = ['SCHEDULED FOR FUTURE', 'SUCCEEDED', 'COMPENSATED']
        Map requestResultStatusMap = retrieveApplicationProcessRequestStatus(requestId)
        String requestStatus = requestResultStatusMap.status
        String requestResult = requestResultStatusMap.result
        String applicationStatusResultString = "Status: ${requestStatus}\nResult: ${requestResult}"

        switch (requestStatus) {
            case pendingStatusList:
                script.echo("Application request status pending.\n${applicationStatusResultString}")
                sleepForWaitTime()
                waitForApplicationProcessRequest(requestId)
                break
            case failedStatusList:
                throw new ApplicationRequestStatusFailed(
                    "Application request status failed.\n${applicationStatusResultString}"
                )
            case completedStatusList:
                switch (requestResult) {
                    case pendingResultList:
                        script.echo("Application request result pending.\n${applicationStatusResultString}")
                        sleepForWaitTime()
                        waitForApplicationProcessRequest(requestId)
                        break
                    case failedResultList:
                        throw new ApplicationRequestResultFailed(
                            "Application request result failed.\n${applicationStatusResultString}"
                        )
                    case completedResultList:
                        script.echo("Application request result completed.\n${applicationStatusResultString}")
                        break
                }
        }
    }

    String requestApplicationProcess(List processes) {
        String url
        Map<String, Object> response = null
        url = urlBase + '/applicationProcessRequest/request'
        if (deployStatus == 'Deploy' || deployStatus == 'PostDeploy') {
            deployProcesses = processes
            processes.each { process ->
                saveCurrentProcessDeployState(process)
                response = curlRequestor.requestJson(
                    url,
                    ucdConfiguration.credentialsId,
                    'PUT',
                    process
                )
                if (response.responseCode != SUCCESSCODE) {
                    String message = 'Failed to request application process\n'
                    message += "Response Code: ${response.responseCode}\n"
                    message += "Response Body: ${response.responseBody}\n"
                    throw new FailedToRequestApplicationProcess(message)
                }
            }
        } else if (deployStatus == 'Rollback') {
            processes.each { process ->
                response = curlRequestor.requestJson(url, ucdConfiguration.credentialsId, 'PUT', process)
                if (response.responseCode != SUCCESSCODE) {
                    String message = 'Failed to request application process\n'
                    message += "Response Code: ${response.responseCode}\n"
                    message += "Response Body: ${response.responseBody}\n"
                    throw new FailedToRequestApplicationProcess(message)
                }
            }
        } else {
            throw new FailedToRequestApplicationProcess("Unknown deploy status: ${deployStatus}, response=$response")
        }

        if (!response || !response.containsKey('responseBody')) {
            throw new FailedToRequestApplicationProcess("Illegal response getting application process: $response")
        }
        response.responseBody.requestId
    }

    /*
     * Main entry method for deploy a phase.
     */

    @Override
    void deploy() {
        curlRequestor = new CurlRequestor(psc, script)
        curlRequestor.containerName = containerName
        ucdConfiguration = deploymentConfiguration.ucd
        urlBase = ucdUrlMap[( ucdConfiguration.get('ucdEnvironment', 'prod').toLowerCase() )] + '/cli'
        urlLogs = ucdUrlMap[( ucdConfiguration.get('ucdEnvironment', 'prod').toLowerCase() )]
        agentName = ucdConfiguration.get('agent')
        retrieveAgentStatus(agentName)
        switch (deployStatus) {
            case 'Deploy':
                deployProcesses = ucdConfiguration.deployProcesses
                ucdConfiguration.components.each { component ->
                    if (component.get('version')) {
                        versionImport(component)
                        Object needsToWaitForVersionImport = component.version.get('version')
                        if (needsToWaitForVersionImport) {
                            waitForVersionImport(component)
                        }
                    }
                }
                String requestId = requestApplicationProcess(ucdConfiguration.deployProcesses)
                script.echo("uDeploy request logs: ${urlLogs}/#applicationProcessRequest/${requestId}")
                // polling is specifically used for mainframe deployment processes
                if (ucdConfiguration.polling == false) {
                    script.echo('You selected to skip polling for application process: '
                        + "${ucdConfiguration.deployProcesses.application}. Pipeline continuing..."
                    )
                } else {
                    waitForApplicationProcessRequest(requestId)
                }
                break
            case 'Rollback':
                List rollbackProcesses = deployProcesses
                rollbackProcesses?.forEach { process ->
                    process?.versions?.forEach { component ->
                        component.version = previousEnvVersions[
                            process.application
                        ][
                            process.environment
                        ][
                            component.component
                        ]
                    }
                }
                String requestId = requestApplicationProcess(rollbackProcesses)
                waitForApplicationProcessRequest(requestId)
                break
            case 'PostDeploy':
                requestApplicationProcess(ucdConfiguration.postDeployProcesses)
                break
        }
    }
    /*
    * https://www.ibm.com/support/knowledgecenter/SS4GSP_7.0.3/
    * com.ibm.udeploy.api.doc/topics/rest_cli_component_propvalue_put.html
    */

    void setComponentProperty(String componentName, String propertyName, String propertyValue) {
        String url
        Map requestMap = [
            component: componentName,
            name     : propertyName,
            value    : propertyValue,
            isSecure : 'false',
        ]
        url = urlBase + '/component/propValue'
        Map response = curlRequestor.requestJson(
            url,
            ucdConfiguration.credentialsId,
            'PUT',
            requestMap)
        if (response.responseCode != SUCCESSCODE) {
            String message = 'Failed to set property for component\n'
            message += "Response Code: ${response.responseCode}\n"
            message += "Response Body: ${response.responseBody}\n"
            throw new FailedToImportVersion(message)
        }
    }

    Map retrieveAgentStatus(String agentId) {
        if (config.ucd.containsKey('agent')) {
            String url = urlBase + "/agentCLI/info?agent=${agentId}"
            Map response = curlRequestor.requestJson(
                url,
                ucdConfiguration.credentialsId,
                'GET',
                [:],
                'application/x-www-form-urlencoded'
            )
            if (response.responseCode == SUCCESSCODE) {
                String agentStatus = response.responseBody.get('status', 'ONLINE')
                script.echo("Agent is online with status of ${agentStatus},\n"
                    + 'continuing with UCD deployment.\n')
            } else {
                String message = 'There was an issue retrieving the agent.\n'
                message += "Response Code: ${response.responseCode}\n"
                message += "Response Body: ${response.responseBody}\n"
                throw new FailedToRetrieveAgentStatus(message)
            }
            response.responseBody
        } else {
            script.echo('No agent provided, continuing with UCD deployment.\n')
        }
    }

}

class FailedToSetComponentProperty extends Exception {
    FailedToSetComponentProperty(String message) {
        super(message)
    }
}

class FailedToImportVersion extends Exception {
    FailedToImportVersion(String message) {
        super(message)
    }
}

class FailedToRequestApplicationProcess extends Exception {
    FailedToRequestApplicationProcess(String message) {
        super(message)
    }
}

class FailedToRequestApplicationProcessStatus extends Exception {
    FailedToRequestApplicationProcessStatus(String message) {
        super(message)
    }
}

class FailedToRequestEnvironmentsInApplication extends Exception {
    FailedToRequestEnvironmentsInApplication(String message) {
        super(message)
    }
}

class FailedToRequestComponentsInEnvironment extends Exception {
    FailedToRequestComponentsInEnvironment(String message) {
        super(message)
    }
}

class FailedToRequestComponentVersions extends Exception {
    FailedToRequestComponentVersions(String message) {
        super(message)
    }
}

class ApplicationRequestStatusFailed extends Exception {
    ApplicationRequestStatusFailed(String message) {
        super(message)
    }
}

class ApplicationRequestResultFailed extends Exception {
    ApplicationRequestResultFailed(String message) {
        super(message)
    }
}

class FailedToRetrieveAgentStatus extends Exception {
    FailedToRetrieveAgentStatus(String message) {
        super(message)
    }
}
