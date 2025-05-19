package com.cigna.ticket

import com.cigna.SinglePodTest
import com.cigna.common.notification.Notification
import com.cigna.common.request.CurlRequestor
import com.cigna.common.utils.FeatureFlags
import org.apache.http.HttpStatus

class ServicenowTaskSpec extends SinglePodTest {
    private static final int DURATION = 60
    private ServicenowTicket snowTicket
    private ServicenowTask task
    private CurlRequestor curlRequestor = Stub()
    private Notification notification = Mock()
    private String urlBasePath = 'https://cignatest.service-now.com'
    private final Map<String, Object> validBody = [
        assigned_to               : 'C71921',
        requested_by              : 'C71921',
        cmdb_ci                   : 'Bla',
        u_emergency_contact_person: 'C71921',
        u_emergency_contact_number: '6106080266'
    ]

    protected void setup() {
        explicitlyMockPipelineStep('updateGitStatus')
        initScriptAndPsc()
        script.env.BUILD_TAG = 'jenkins-orchestrators-folders-adjudicator-Adjudicator-master-i80'
        script.scm.branches = [['master']]
        FeatureFlags.debug = true
    }

    def """Open task stores task number"""() {
        given:
        final String SYS_ID = 'f71a35fadbd44c90b47d54f948961975'
        final String taskNumber = '123'
        Map response = [
            responseCode: HttpStatus.SC_OK,
            responseBody: [
                result: [[
                             number: [value: taskNumber]
                         ]]
            ]
        ]
        curlRequestor.requestJson(
            _,
            _,
            _,
        ) >> [
            responseCode: HttpStatus.SC_OK,
            responseBody: [
                result: [[sys_id: SYS_ID]]
            ]
        ]
        curlRequestor.requestJson(
            _,
            _,
            _,
            _,
        ) >> [
            responseCode: HttpStatus.SC_OK,
            responseBody: [
                result: [
                    sys_id: [value: SYS_ID],
                    number: [value: taskNumber]
                ]
            ]
        ]

        when:
        snowTicket = new ServicenowTicket(
            script: script,
            psc: psc,
            ticketConfiguration: [credentialsId: 'credsId'],
            curlRequestor: curlRequestor,
            urlBasePath: urlBasePath,
            notification: notification
        )
        task = new ServicenowTask(snowTicket, [assignGroup: 'HBoR DevOps', assignTo: 'C8R2SW'])
        task.open()

        then:
        task.number
        task.number == taskNumber
    }

    def """When userID for task is translated into sys_id to open task"""() {
        given:
        curlRequestor.requestJson(
            _,
            _,
            'GET'
        ) >> [responseCode: HttpStatus.SC_OK,
              responseBody: [result: [[sys_id: 'b21e43dbc3e54a50c01be50fb001314d']]]]
        curlRequestor.requestJson(
            _,
            _,
            'POST',
            _,
        ) >> [responseCode: HttpStatus.SC_OK,
              responseBody: [result: [[sys_id: 'b21e43dbc3e54a50c01be50fb001314d']]]]
        when:
        snowTicket = new ServicenowTicket(
            script: script,
            psc: psc,
            ticketConfiguration: [credentialsId: 'credsId'],
            curlRequestor: curlRequestor,
            urlBasePath: urlBasePath,
            notification: notification
        )
        task = new ServicenowTask(snowTicket, [assignGroup: 'HBoR DevOps', assignTo: 'C8R2SW'])
        task.open()

        then:
        1 * getPipelineMock("echo").call('taskAssignedToUserId sys_id: b21e43dbc3e54a50c01be50fb001314d')
    }

    def """When the task is moved to in progress"""() {
        given:
        curlRequestor.requestJson(
            _,
            _,
            'GET'
        ) >> [
            responseCode: HttpStatus.SC_OK,
            responseBody: [result: [[sys_id: 'b21e43dbc3e54a50c01be50fb001314d']]]
        ]
        curlRequestor.requestJson(
            _,
            'auth',
            _,
            [
                state           : 'in progress',
                change_task_type: 'Implementation'
            ]
        ) >>
            [
                responseCode: HttpStatus.SC_OK,
                responseBody: [
                    result: [
                        state           : [display_value: 'in progress'],
                        change_task_type: [display_value: 'Implementation']
                    ]
                ]
            ]

        when:
        snowTicket = new ServicenowTicket(
            script: script,
            cmTicket: 'ae779fad3b50da9033373f50c5e45aaf',
            psc: psc,
            ticketConfiguration: [
                ticketType       : 'Servicenow',
                branchPattern    : 'master',
                changeEnvironment: 'prod',
                plannedDuration  : DURATION,
                credentialsId    : 'auth',
            ] << validBody,
            curlRequestor: curlRequestor,
            urlBasePath: urlBasePath,
            notification: notification
        )
        task = new ServicenowTask(snowTicket, [assignGroup: 'HBoR DevOps', assignTo: 'C8R2SW'])
        task.inProgressTask()

        then:
        1 * getPipelineMock("echo").call({ it.contains('[in progress]') })
    }

    def """When the task is schedule"""() {
        given:
        curlRequestor.requestJson(
            _,
            _,
            'GET'
        ) >> [
            responseCode: HttpStatus.SC_OK,
            responseBody: [result: [[sys_id: 'b21e43dbc3e54a50c01be50fb001314d']]]
        ]
        curlRequestor.requestJson(
            'https://cignatest.service-now.com/api/sn_chg_rest/change/ae779fad3b50da9033373f50c5e45aaf/task/2488d3613b54da503820adc964e45ab0',
            'auth',
            'PATCH',
            [
                state           : 'Ready',
                change_task_type: 'Planning'
            ]
        ) >> [
            responseCode: HttpStatus.SC_OK,
            responseBody: [
                result: [
                    state           : [display_value: 'Ready'],
                    change_task_type: [display_value: 'Planning']
                ]
            ]
        ]

        when:
        snowTicket = new ServicenowTicket(
            script: script,
            cmTicket: 'ae779fad3b50da9033373f50c5e45aaf',
            psc: psc,
            ticketConfiguration: [
                ticketType       : 'Servicenow',
                branchPattern    : 'master',
                changeEnvironment: 'prod',
                plannedDuration  : DURATION,
                credentialsId    : 'auth',
            ] << validBody,
            curlRequestor: curlRequestor,
            urlBasePath: urlBasePath,
            notification: notification
        )
        task = new ServicenowTask(snowTicket, [assignGroup: 'HBoR DevOps', assignTo: 'C8R2SW'])
        task.sysId = '2488d3613b54da503820adc964e45ab0'
        task.scheduleTask()

        then:
        1 * getPipelineMock("echo").call({ it.contains('[Ready]') })
    }
}