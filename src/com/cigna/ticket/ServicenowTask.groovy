package com.cigna.ticket

import com.cigna.common.notification.Notification
import com.cigna.common.request.CurlRequestor
import com.cigna.common.utils.FeatureFlags
import com.cloudbees.groovy.cps.NonCPS

/**
 * This class represents a task in ServiceNow and provides methods to manage the task lifecycle.
 * */
class ServicenowTask {

    static final Map<String, String> TASK_FLOW = [
        impl: 'Implementation',
        plan: 'Planning',
        rdy : 'Ready',
        prog: 'in progress',
        cl  : 'Closed'
    ].asImmutable()
    static final Map<String, String> TIME_OUT_CLOSE_TASK_MAP = [
        state           : TASK_FLOW.cl,
        change_task_type: TASK_FLOW.impl,
        close_notes     : 'Not implemented due to timeout. Task was not completed within defined timeout of the pipeline.',
        close_code      : 'Not Implemented',
    ].asImmutable()

    Map<String, String> config
    String cmTicket
    String closeCode = ''
    String sysId
    String urlBasePath
    String number
    protected Map<String, Object> ticketConfiguration
    protected def script
    protected ServicenowTicket snowTicket
    protected CurlRequestor curlRequestor
    protected Notification notification

    /**
     * Constructor to initialize a ServicenowTask.
     *
     * @param snowTicket The ServiceNow ticket associated with this task.
     * @param taskConfig The configuration map for the task.
     * @param cmTicket The change management ticket ID.
     * @param index The index of the task.
     */
    protected ServicenowTask(ServicenowTicket snowTicket, Map taskConfig) {
        this.script = snowTicket.script
        this.snowTicket = snowTicket
        this.curlRequestor = snowTicket.curlRequestor
        this.urlBasePath = snowTicket.urlBasePath
        this.notification = snowTicket.notification
        this.cmTicket = snowTicket.cmTicket
        this.ticketConfiguration = snowTicket.ticketConfiguration
        this.config = taskConfig
    }

    /**
     * Translate Assign Group into sys_id to use for GET request.
     * or throw exception
     * */
    String assignLookup(String assignGroup) throws Exception {
        String assignGroupEncoded = URLEncoder.encode(assignGroup)
        String getAssignUrl = "$urlBasePath/api/now/table/sys_user_group?sysparm_query=name%3D$assignGroupEncoded^active%3Dtrue&sysparm_fields=sys_id&sysparm_limit=1"
        Map<String, Object> response = curlRequestor.requestJson(
            getAssignUrl,
            ticketConfiguration.credentialsId as String,
            'GET'
        )
        List getSysId = response.responseBody.result ?: []
        if (!getSysId) {
            throw new TaskException("Failed to retrieve the System Id for Task Assignment Group [$assignGroup]")
        }
        getSysId[0].sys_id ?: ''
    }

    /**
     * Translate User ID into sys_id to use for GET request.*/
    String userIdLookup(String userId) {
        String getUserIdUrl =
            "$urlBasePath/api/now/table/sys_user?sysparm_query=u_lan_id%3D$userId^active%3Dtrue&sysparm_fields=sys_id&sysparm_limit=1"
        Map<String, Object> userResponse = curlRequestor.requestJson(
            getUserIdUrl,
            ticketConfiguration.credentialsId as String,
            'GET'
        )
        Map responseBody = userResponse.responseBody ?: [:]
        List results = responseBody.result ?: []
        if (!results) {
            throw new TaskException(
                'Failed to retrieve System User ID for the provided '
                    + 'requested_by, assigned_to,  and/or u_emergency_contact_person items. Please ensure they are correct.'
            )
        }
        String taskAssignedToUserId = results[0].sys_id ?: ''
        if (FeatureFlags.debug) {
            script.echo("taskAssignedToUserId sys_id: " + taskAssignedToUserId)
        }
        taskAssignedToUserId
    }

    /**
     * Create the task under the Standard change ticket.
     * Put taskSysId in sysIdTaskMap and send notification.
     *
     * @return The response map from the request.
     */
    Map<String, Object> open() {
        String openTaskUrl = "$urlBasePath/api/sn_chg_rest/change/$cmTicket/task"
        String assignGroupSysId = assignLookup(this.config.assignGroup)
        String taskAssignedToUserId = userIdLookup(this.config.assignedTo)
        Map<String, String> openTaskMap = [
            short_description: this.config.shortDescription,
            description      : this.config.description,
            assignment_group : assignGroupSysId,
            assigned_to      : taskAssignedToUserId,
            work_notes       : this.config.workNotes
        ]
        script.echo("openTaskMap: $openTaskMap")
        Map<String, Object> response = curlRequestor.requestJson(
            openTaskUrl,
            ticketConfiguration.credentialsId as String,
            'POST',
            openTaskMap
        )
        this.sysId = response.responseBody?.result?.sys_id?.value ?: ''
        if (!this.sysId) {
            throw new TaskException("Failed to Open the Task under the Change. \n Output: ${response.responseBody.result}")
        }
        uploadFileToTask()
        notification.notifyWithAllMethods("Task Messages: ${response.responseBody.result}")
        number = response?.responseBody?.result?.number?.value
        script.echo("task number: $number")

        response
    }

    /**
     * Move the task into the schedule state.
     * */
    Map<String, Object> scheduleTask() {
        Map<String, String> SCHEDULE_TASK_MAP = [
            state           : TASK_FLOW.rdy,
            change_task_type: TASK_FLOW.plan,
        ]
        moveTask(SCHEDULE_TASK_MAP)
    }

    /**
     * Move the task into the in-progress state.
     *
     * @return The response map from the request.
     */
    Map<String, Object> inProgressTask() {
        Map<String, String> IN_PROGRESS_TASK_MAP = [
            state           : TASK_FLOW.prog,
            change_task_type: TASK_FLOW.impl,
        ]
        moveTask(IN_PROGRESS_TASK_MAP)
    }

    /**
     * Close the task when the task is failed due to the timeout.
     *
     * @param sysId The system ID of the task.
     * @return The response map from the request.
     */
    Map<String, Object> closeTask() {
        if (closeCode) {
            return null
        }

        moveTask(TIME_OUT_CLOSE_TASK_MAP)
    }

    /**
     * Move the task into the specified state.
     *
     * @param taskMap The map containing task details.
     * @return The response map from the request.
     */
    Map<String, Object> moveTask(Map<String, String> taskMap) {
        String progressUrl = "$urlBasePath/api/sn_chg_rest/change/$cmTicket/task/$sysId"

        Map<String, Object> response = curlRequestor.requestJson(
            progressUrl,
            ticketConfiguration.credentialsId as String,
            'PATCH',
            taskMap
        )
        String stateTaskStage = response.responseBody?.result?.state?.display_value ?: ''
        String typeTaskStage = response.responseBody?.result?.change_task_type?.display_value ?: ''
        if (!stateTaskStage && !typeTaskStage) {
            throw new TaskException(
                "Failed to move task [number: $number] to [state: ${taskMap.state}] and [change task type: ${taskMap.change_task_type}]."
            )
        }
        script.echo("task number [$number], state: [$stateTaskStage], type: [$typeTaskStage]")
        notification.notifyWithAllMethods("Current task [$number] State: ${response.responseBody.result}")
        response
    }

    /**
     * Check the close code if not set and update the state
     */
    String updateCloseCode() {
        if (closeCode) {
            return closeCode
        }
        String progressUrl = "$urlBasePath/api/sn_chg_rest/change/$cmTicket/task/$sysId"
        Map<String, Object> response = curlRequestor.requestJson(
            progressUrl,
            ticketConfiguration.credentialsId as String,
            'GET'
        )
        if (FeatureFlags.debug) {
            script.echo("Change Task Url: $urlBasePath/nav_to.do?uri=change_task.do?sys_id=$sysId")
        }
        Map<String, Map> result = response.responseBody?.result
        closeCode = result?.close_code?.display_value
        if (!closeCode) {
            return null
        }
        String taskCloseNotes = result.close_notes?.display_value ?: ""
        String taskState = result.state?.display_value ?: ""
        String taskClosedAt = result.closed_at?.display_value ?: ""
        String taskClosedBy = result.closed_by?.display_value ?: ""
        script.echo(
            """|Close Code        : $closeCode
                           |Comment           : $taskCloseNotes
                           |Task State        : $taskState
                           |Approved/Closed by: $taskClosedBy
                           |Task Closed at    : $taskClosedAt"""
                .stripMargin()
        )

        closeCode
    }

/**
 * Attach a file with the task under standard change ticket.
 *
 * @param config The map containing task details.
 * */
    void uploadFileToTask() {
        if (!config.fileName) {
            return
        }
        String uploadUrl = "$urlBasePath/api/now/attachment/upload"
        String pathFileUploadTask = "@${config.fileName}"
        Map<String, String> fileUploadTaskMap = [
            table_name  : "change_task",
            table_sys_id: this.sysId,
            file        : pathFileUploadTask
        ]
        script.echo("fileUploadTaskMap: $fileUploadTaskMap")
        Map<String, Object> response = curlRequestor.requestJson(
            uploadUrl,
            ticketConfiguration.credentialsId as String,
            'POST',
            fileUploadTaskMap,
            'multipart/form-data'
        )
        String fileUploadSysId = response.responseBody?.result?.sys_id ?: ''
        if (!fileUploadSysId) {
            throw new TaskException(
                "Failed to upload the file in task [$number]. \n Output: ${response.responseBody.result}"
            )
        }
        notification.notifyWithAllMethods("Task Messages: ${response.responseBody.result}")
    }

    @Override
    @NonCPS
    String toString() {
        "com.cigna.ticket.ServicenowTask(config:$config, cmTicket:$cmTicket, closeCode:$closeCode, sysId:$sysId, " +
            "urlBasePath:$urlBasePath, number:$number)"
    }
}

class TaskException extends Exception {
    TaskException(String message) {
        super(message)
    }
}
