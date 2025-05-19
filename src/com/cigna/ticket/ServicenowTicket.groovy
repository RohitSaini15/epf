package com.cigna.ticket

import com.cigna.base.Phase
import com.cigna.common.kubernetes.PodTemplateCreator
import com.cigna.common.request.CurlRequestor
import com.cigna.common.utils.TicketTimer
import com.cloudbees.groovy.cps.NonCPS
import org.joda.time.DateTimeZone

/**
 * Defines steps for Servicenow tickets.
 *
 * Closure Code Description
 * KB0031051  -  Latest Version
 * After implementing a change, you will need to enter the “Closure Code Description.”
 * To do this, select the appropriate results code and add notes. This will successfully close out the change.
 *
 * o `Successful`
 *   A results code of Successful would meet any of the following conditions:
 *   1) Change was completed successfully with no issues.
 *
 * o `Deployed with Issues and No Business Impact`
 *   A results code of Deployed with Issue and No Business Impact would meet any of the following conditions:
 *   1) Change was partially backed out before impact is identified by the business
 *   2)Change was deployed with minimal to no business impact, but with a fix planned for future date.
 *   3) Change was deployed, but created a P2 through P4 Incident with minimal to no business impact;
 *      Business owned and supported application or single user issues.
 *
 * o `Not Implemented`
 *   A results code of Not Implemented would meet any of the following conditions:
 *   1) Change was cancelled or rescheduled.
 *   2) Change was fully backed out before impact is identified by the business
 *
 * o `Failed`
 * A results code of Failed would meet any of the following conditions:
 * The Severe Incident Management team is engaged.
 * Managed Incident is discovered after the change has been implemented.
 * M1 - A Managed Incident which will significantly disrupt normal business process, customer experience,
 *      and or brand reputation and will be communicated broadly to business areas and IT.
 * M2 - A Managed Incident which may slow down or interrupt a specific business function and
 *      may even make the customer experience less than optimal.
 * M3 - An incident with minimal impact, but with potential to have increased impact if not addressed.
 * If the change Failed, a Post  Implementation Review task is  auto-generated after clicking Review.
 * A Change Manager will review, close the task, and move the change to a Closed state.*/
class ServicenowTicket extends Ticket {
    // State Model
    static final int IMPLEMENT = -1
    static final int SCHEDULED = -2
    long millisPerSec = 1000

    static private final Map SNOW_URL_MAP = [
        dev : 'https://cignadev1.service-now.com',
        qa  : 'https://cignaqa.service-now.com',
        prod: 'https://cigna.service-now.com',
        test: 'https://cignatest.service-now.com',
    ].asImmutable()
    static private final Map SNOW_SUB_CATEGORY_SYS_ID = [
        dev : '15ae9a3b1b65e0507edeed33b24bcbbf',
        qa  : '9e3506fcdb9641102a501582399619a4',
        prod: '47b0a2b71ba5e0507edeed33b24bcbb4',
        test: '15ae9a3b1b65e0507edeed33b24bcbbf',
    ].asImmutable()

    // Close Codes
    static final String SUCCESS = 'Successful'
    static final String FAIL_CODE = 'Failed'
    static final String NOT_IMPL_CODE = 'Not Implemented'
    static final String SUCCESS_ISSUES = 'Deployed with Issues and No Business Impact'

    protected String implementationComments
    protected TicketTimer ticketTimer
    protected Map openTicketMap
    protected Map scheduleTicketMap
    protected Map<String, Object> implementTicketMap = [
        state      : SCHEDULED,
        close_code : SUCCESS,
        close_notes: '',
        work_end   : '',
    ]
    protected Map reviewTicketMap
    protected Map closeTicketMap
    protected String urlBasePath
    protected DateTimeZone tz
    String subCategory
    String cmTicket
    String closeState
    String scheduledState
    String createStage
    String changeSysId
    String titleSysId
    String requestedByUserId
    String assignedToUserId
    String emergencyContactUserId
    String wasTestingPerformed = 'true'

    List<ServicenowTask> tasks = []

    protected CurlRequestor curlRequestor

    ServicenowTicket() {
        containerName = 'epf-curlvlatest'
        containerImage = 'enterprise-devops/epf-curl'
        containerVersion = 'latest'
    }

    @Override
    Boolean prePodConfig() {
        List<Map> env = []

        PodTemplateCreator containerTemplate = PodTemplateCreator.newPodTemplateCreator()
        containerTemplate.addContainer(
            containerName,
            "${containerImage}:${containerVersion}",
            'IfNotPresent',
            25,
            125,
            100,
            250,
            env
        )

        additionalPodConfig = [
            'volumes'   : [],
            'containers': [containerTemplate.getContainer(containerName)]
        ]

        super.prePodConfig()
    }
    Map<String, String> testingResults = [
        testSuccess: 'yes_testing_performed',
        testFailure: 'no_testing_performed',
    ]

    Map<String, Object> stageFlow = [
        sched: 'Scheduled',
        imp  : IMPLEMENT,
        rev  : 'Review',
        close: 'Closed',
    ]

    void initImplementStageMap() {
        String url = 'https://repo.sys.cigna.com/ui/artifactSearchResults'
        script.echo(this.implementationComments)
        implementTicketMap.state = stageFlow.imp
        implementTicketMap.close_notes = """|${this.implementationComments}
               |Audit Bundle: $url?name=${script.env.BUILD_TAG}&type=artifacts"""
            .stripMargin('|')
        implementTicketMap.work_end = ticketTimer.endTimeActual
        script.echo("Creating impl ticket map [$implementTicketMap]")
    }

    /**
     * Translates 'SC\d\d\d\d\d' title of Standard Change Template into sys_id to use for
     * POST request to create the change ticket with correct SC template from config*/
    void changeTitleIdLookup(String changeTitle) {
        String getTitleIdUrl =
            "$urlBasePath/api/now/table/std_change_record_producer?sysparm_query=nameSTARTSWITH$changeTitle&sysparm_fields=sys_id&sysparm_limit=1"
        Map<String, Object> idResponse = curlRequestor.requestJson(
            getTitleIdUrl,
            ticketConfiguration.credentialsId as String,
            'GET'
        )
        if (!idResponse) {
            throwTicketException()
        }
        Map responseBody = idResponse.responseBody ?: [:]
        List results = responseBody.result ?: []
        if (!results) {
            throwTicketException()
        }
        titleSysId = results[0].sys_id ?: ''
        script.echo("Standard Change Template sys_id: $titleSysId")
    }

    @NonCPS
    private void throwTicketException() {
        throw new FailedToRetrieveChangeTitleSysId(
            'Failed to retrieve the Template System ID for the provided title. Please ensure it is correct.'
        )
    }

    /**
     * Translates cmbd_ci name into sys_id to use for POST request
     * to create the change ticket with correct CI from config*/
    void changeIdLookup(String changeId) {
        String urlEncodedApp = URLEncoder.encode(changeId)
        String getCiIdUrl =
            "$urlBasePath/api/now/table/cmdb_ci?sysparm_query=name%3D$urlEncodedApp&sysparm_display_value=true&sysparm_limit=1"
        Object ciResponse = curlRequestor.requestJson(
            getCiIdUrl,
            ticketConfiguration.credentialsId as String,
            'GET',
            [:],
            'application/x-www-form-urlencoded'
        )
        Map responseBody = ciResponse.responseBody ?: [:]
        List results = responseBody.result ?: [:]
        if (!results) {
            throw new FailedToRetrieveConfigItemSysId(
                'Failed to retrieve the Configuration Item System ID for the provided item. Please ensure it is correct.'
            )
        }
        changeSysId = results[0].sys_id ?: ''
        script.echo("CI sys_id: $changeSysId")
    }

    /**
     * Translates requested_by, assigned_to, and u_emergency_contact_person (any sys_user) to use for POST
     * request to create and close change ticket with correct lan id from config*/
    void changeUserIdLookup(String userId, String param) {
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
            throw new FailedToRetrieveSysUserSysId(
                'Failed to retrieve System User ID for the provided ' +
                    'requested_by, assigned_to, and/or u_ emergency_contact_person items. Please ensure they are correct .'
            )
        }
        this."$param" = results[0].sys_id ?: ''
        script.echo("$param sys_id: ${this."$param"}")
    }

    @Override
    @NonCPS
    List validate(boolean requiresBranchPattern = false, Phase phase = this) {
        List issues = []
        String regexMatchStandard = /SCT\d\d\d\d\d\d\d/
        String regexMatchNormal = /CHG\d\d\d\d\d\d\d/
        if (ticketConfiguration.title.matches(regexMatchStandard)) {
            ticketType = 'standard'
            additionalValidationItems = [
                'changeEnvironment',
                'credentialsId',
                'assigned_to',
                'requested_by',
                'title',
                'cmdb_ci',
                'u_emergency_contact_person',
                'u_emergency_contact_number',
            ]
        } else if (ticketConfiguration.title.matches(regexMatchNormal)) {
            ticketType = 'normal'
            additionalValidationItems = ['title']
        } else {
            issues.add('title format is not valid format. Must contain SCT or CHG as first three characters.')
        }
        issues.addAll(super.validate(requiresBranchPattern, phase))
        issues
    }

    void openTicket() {
        script.echo("Opening ticket: ${ticketConfiguration.title}")
        ticketTimer.startTime(ticketConfiguration.get('plannedDuration'))
        changeTitleIdLookup(ticketConfiguration.title as String)
        changeIdLookup(ticketConfiguration.cmdb_ci as String)
        changeUserIdLookup(ticketConfiguration.requested_by as String, 'requestedByUserId')
        changeUserIdLookup(ticketConfiguration.assigned_to as String, 'assignedToUserId')
        changeUserIdLookup(ticketConfiguration.u_emergency_contact_person as String, 'emergencyContactUserId')
        openTicketMap = [
            assigned_to               : assignedToUserId,
            requested_by              : requestedByUserId,
            cmdb_ci                   : changeSysId,
            end_date                  : ticketTimer.endTimePlanned,
            start_date                : ticketTimer.startTimePlanned,
            work_start                : ticketTimer.startTimeActual,
            u_emergency_contact_person: emergencyContactUserId,
            u_emergency_contact_number: ticketConfiguration.u_emergency_contact_number,
            u_chg_cat                 : subCategory,
        ]
        openTicketMap['u_pre_implementation_testing'] = wasTestingPerformed ? testingResults.testSuccess : testingResults.testFailure
        script.echo("$openTicketMap")
        String openTicketUrl = "$urlBasePath/api/sn_chg_rest/change/standard/$titleSysId"
        Map<String, Object> response = curlRequestor.requestJson(
            openTicketUrl,
            ticketConfiguration.credentialsId as String,
            'POST',
            openTicketMap
        )
        script.echo("ticket number: ${response?.responseBody?.result?.number?.value}")
        cmTicket = response.responseBody?.result?.sys_id?.value ?: ''
        if (!cmTicket) {
            throw new FailedToOpenCMTicket("Failed to open a CM ticket.\nResults: ${response.responseBody?.result}")
        }
        script.echo("ticket url: $urlBasePath/nav_to.do?uri=change_request.do?sys_id=$cmTicket")
        notification.notifyWithAllMethods("CM Messages: ${response.responseBody.result}")
    }

    void scheduleTicket() {
        createStage = "$urlBasePath/api/sn_chg_rest/change/standard/$cmTicket"

        scheduleTicketMap = [state: stageFlow.sched,]
        Map<String, Object> response = curlRequestor.requestJson(
            createStage,
            ticketConfiguration.credentialsId as String,
            'PATCH',
            scheduleTicketMap
        )
        scheduledState = response.responseBody.get('result', [:]).get('state', [:]).get('display_value', '')
        if (!scheduledState) {
            throw new FailedToScheduleTicket("Failed to schedule CM ticket. Check conflicts.\nResults: ${response.responseBody?.result}")
        }
        notification.notifyWithAllMethods("CM Messages: ${response.responseBody.result}")
        script.echo("State: $scheduledState")
    }

    void addLinkToNormalChange(Map<String, Object> normalChangeData) {
        String url = 'https://repo.sys.cigna.com/ui/artifactSearchResults'
        urlBasePath = SNOW_URL_MAP.prod
        subCategory = SNOW_SUB_CATEGORY_SYS_ID.prod
        String sysID = normalChangeData.sys_id
        String normalChangeDataType = normalChangeData.type == 'emergency' ?: 'normal'
        createStage = "$urlBasePath/api/sn_chg_rest/change/$normalChangeDataType/$sysID"
        Map<String, Object> auditLinkMap = [close_notes: """Audit Bundle: $url?name=${script.env.BUILD_TAG}&type=artifacts"""]
        Map<String, Object> response = curlRequestor.requestJson(
            createStage,
            'SERVICE_NOW_TOKEN',
            'PATCH',
            auditLinkMap
        )

        String implementState = response.responseBody.get('result', [:]).get('state', '1').get('value', '1')
        if (implementState != '-1' && implementState != '-1.0') {
            script.echo('WARNING: Unable to post compliance audit link to change request')
        }
    }

    void implementTicket() {
        ticketTimer.stopTime()
        createStage = "$urlBasePath/api/now/table/change_request/$cmTicket"
        initImplementStageMap()
        Map<String, Object> response = curlRequestor.requestJson(
            createStage,
            ticketConfiguration.credentialsId as String,
            'PATCH',
            implementTicketMap
        )
        String implementState = response.responseBody.get('result', [:]).get('state', '1')
        if (implementState != '-1') {
            throw new FailedToImplementTicket("Failed to implement CM ticket. Check implementation results.\nResults: ${response.responseBody?.result}")
        }
        notification.notifyWithAllMethods("CM Messages: ${response.responseBody?.result}")
        script.echo("State: $implementState")
    }

    void reviewTicket() {
        createStage = "$urlBasePath/api/sn_chg_rest/change/standard/$cmTicket"
        reviewTicketMap = [state: stageFlow.rev,]
        Map<String, Object> response = curlRequestor.requestJson(
            createStage,
            ticketConfiguration.credentialsId as String,
            'PATCH',
            reviewTicketMap
        )
        String reviewState = response.responseBody.get('result', [:]).get('state', [:]).get('display_value', '')
        if (!reviewState) {
            throw new FailedToReviewTicket("Failed to review CM ticket. Check implementation.\nResults: ${response.responseBody?.result}")
        }
        notification.notifyWithAllMethods("CM Messages: ${response.responseBody.result}")
        script.echo("State: $reviewState")
    }

    void closeTicket() {
        createStage = "$urlBasePath/api/now/table/change_request/$cmTicket"
        closeTicketMap = [state: stageFlow.close,]
        Map<String, Object> response = curlRequestor.requestJson(
            createStage,
            ticketConfiguration.credentialsId as String,
            'PATCH',
            closeTicketMap
        )
        closeState = response.responseBody.result?.state ?: ''
        if (!closeState) {
            throw new CloseTicketFailure("Failed to close cmTicket.\nResults: ${response.responseBody.result}")
        }
        notification.notifyWithAllMethods("CM Messages: ${response.responseBody.result}")
    }

    boolean isWorkflowTicket() {
        ticketConfiguration.get('isWorkflowEnabled', true)
    }

    /**
     * Poll all the open tasks.
     * Return true if all tasks are closed
     * Return false if any were not closed manually.
     * */
    Boolean allTasksClosedPoll() {
        // this will be the lower bound timeout. i.e. time out could be timeout + interval
        long taskCheckIntervalMinutes = (ticketConfiguration.taskCheckIntervalMinutes ?: 15) as long
        // ensure interval inside [1, 60] minutes
        taskCheckIntervalMinutes = (taskCheckIntervalMinutes >= 1 ? taskCheckIntervalMinutes : 1) as long
        taskCheckIntervalMinutes = (taskCheckIntervalMinutes <= 60 ? taskCheckIntervalMinutes : 60) as long
        final long TASK_CHECK_INTERVAL_MS = (taskCheckIntervalMinutes * 60 * millisPerSec) as long

        long taskTimeOutMinutes = (ticketConfiguration.taskTimeOutMinutes ?: 1) as long
        if (taskTimeOutMinutes <= taskCheckIntervalMinutes) {
            taskTimeOutMinutes = taskCheckIntervalMinutes + 1
            script.echo(
                "WARNING: taskTimeOutMinutes must be at least taskCheckIntervalMinutes. " +
                    "Setting taskTimeOutMinutes to $taskTimeOutMinutes"
            )
        }
        // ensure timeout is less than 1 day
        taskTimeOutMinutes = (taskTimeOutMinutes <= 60 * 24 ? taskTimeOutMinutes : 60 * 24) as long
        final long TASK_TIME_OUT_MS = taskTimeOutMinutes * 60 * millisPerSec

        long TIMEOUT_EPOCH_MS = System.currentTimeMillis() + TASK_TIME_OUT_MS
        List<ServicenowTask> openTasks = checkOpenTasks(tasks)

        while (System.currentTimeMillis() < TIMEOUT_EPOCH_MS) {
            sleep(Math.min(TASK_CHECK_INTERVAL_MS, TIMEOUT_EPOCH_MS - System.currentTimeMillis()))
            BigDecimal minutesRemaining = (TIMEOUT_EPOCH_MS - System.currentTimeMillis()) / 60000
            script.echo(
                "Close all the following open tasks manually before the timeout. $minutesRemaining minutes remaining" +
                    " before aborting deployment."
            )

            openTasks = checkOpenTasks(openTasks)

            if (!openTasks) {
                return true
            }
        }

        throw new TaskException(
            "${openTasks?.size()} out of ${tasks.size()} tasks were not closed successfully before the ${taskTimeOutMinutes} minute timeout." +
                " Failed tasks: $openTasks"
        )
    }

    List<ServicenowTask> checkOpenTasks(List<ServicenowTask> tasks) {
        tasks.findAll { ServicenowTask task ->
            task.updateCloseCode()
            if (['Failed', 'Not Implemented'].contains(task.closeCode)) {
                throw new TaskException("task [number: ${task.number}] was closed with a close code of ${task.closeCode}.")
            }
            if (task.closeCode != 'Successful') {
                script.echo("task [number: ${task.number}] is still not closed succesfully. [closecode:$task.closeCode]")
                return true
            }
            false
        }
    }

    static void closeTasks(List<ServicenowTask> tasks) {
        tasks.each { ServicenowTask task ->
            task.closeTask()
        }
    }

    class TaskException extends Exception {
        TaskException(String message) {
            super(message)
        }
    }

    @Override
    void run(String cloudName) {
        // delayed instantiation to ensure psc and script are correctly valued.
        if (!curlRequestor) {
            curlRequestor = new CurlRequestor(psc, script)
        }
        tz = DateTimeZone.forID('Etc/GMT')

        if (ticketType != 'standard') {
            if (psc.complianceValidator.isProdDeploy) {
                addLinkToNormalChange(psc.complianceValidator.normalChangeData)
            }
            return
        }

        if (ticketType == 'standard') {
            urlBasePath = SNOW_URL_MAP[ticketConfiguration.changeEnvironment.toLowerCase()]
            subCategory = SNOW_SUB_CATEGORY_SYS_ID[ticketConfiguration.changeEnvironment.toLowerCase()]
        }
        ticketTimer = ticketTimer ?: new TicketTimer(timePattern: 'yyyy-MM-dd HH:mm:ss', convertTo: tz)
        curlRequestor.containerName = containerName
        curlRequestor.cloudName = cloudName
        if (cmTicket) {
            implementTicket()
            if (workflowTicket) {
                reviewTicket()
                closeTasks(tasks)
                if (implementTicketMap.close_code == SUCCESS) {
                    script.echo('Implementation was Successful, ticket will automatically move from Review stage to Closed stage.')
                } else {
                    //closeTicket()
                    script.echo(
                        "Ticket Closed with state: ${implementTicketMap.close_code}, " +
                            "please review change ID: ${changeIdLookup(ticketConfiguration.cmdb_ci)}."
                    )
                }
            }
            return
        }

        openTicket()
        if (ticketConfiguration?.tasks) {
            createTasks()
        }
        scheduleTicket()
    }

    void createTasks() {
        try {
            ticketConfiguration.tasks.each { Map taskConfig ->
                ServicenowTask task = new ServicenowTask(this, taskConfig)
                tasks += task
                task.open()
                task.scheduleTask()
                task.inProgressTask()
            }
        } catch (Exception e) {
            script.echo(e.message)
        }
    }

    @Override
    void updateState(String state) {
        implementTicketMap.close_code = determineCloseCode(state)
        script.echo("State of $state close_code = ${implementTicketMap.close_code}")
    }

    private String determineCloseCode(String state) {
        if (workflowTicket) {
            return SUCCESS
        }
        Map stateToCloseCodeMap = [
            deploySuccess : SUCCESS,
            deployFailure : FAIL_CODE,
            deployIssue   : SUCCESS_ISSUES,
            deployRollback: NOT_IMPL_CODE
        ]
        return stateToCloseCodeMap[state]
    }

    void readyToDeploy() {
        if (ticketType == 'standard' && tasks) {
            allTasksClosedPoll()
        }
    }
}


class FailedToRetrieveChangeTitleSysId extends Exception {
    FailedToRetrieveChangeTitleSysId(String message) {
        super(message)
    }
}

class FailedToRetrieveConfigItemSysId extends Exception {
    FailedToRetrieveConfigItemSysId(String message) {
        super(message)
    }
}

class FailedToRetrieveSysUserSysId extends Exception {
    FailedToRetrieveSysUserSysId(String message) {
        super(message)
    }
}

class FailedToOpenCMTicket extends Exception {
    FailedToOpenCMTicket(String message) {
        super(message)
    }
}

class FailedToScheduleTicket extends Exception {
    FailedToScheduleTicket(String message) {
        super(message)
    }
}

class FailedToImplementTicket extends Exception {
    FailedToImplementTicket(String message) {
        super(message)
    }
}

class FailedToReviewTicket extends Exception {
    FailedToReviewTicket(String message) {
        super(message)
    }
}