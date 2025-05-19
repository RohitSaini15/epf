package com.cigna.ticket

import com.cigna.SinglePodTest
import com.cigna.common.notification.Notification
import com.cigna.common.request.CurlRequestor
import com.cigna.common.utils.TicketTimer
import org.apache.http.HttpStatus
import spock.lang.Subject

class ServicenowTicketSpec extends SinglePodTest {
    @Subject
    private ServicenowTicket servicenowTicket

    private static final int DURATION = 60
    private final CurlRequestor curlRequestor = Mock()
    private final Notification notification = Mock()
    private final TicketTimer ticketTimer = Mock()

    private final Map<String, Object> validBody = [
        assigned_to               : 'C71921',
        requested_by              : 'C71921',
        cmdb_ci                   : 'Bla',
        u_emergency_contact_person: 'C71921',
        u_emergency_contact_number: '6106080266'
    ]

    def setup() {
        explicitlyMockPipelineStep('updateGitStatus')
        initScriptAndPsc()
        script.env.BUILD_TAG = 'jenkins-orchestrators-folders-adjudicator-Adjudicator-master-i80'
        script.scm.branches = [
            ['master']
        ]
    }

    def """When run is called and there is no cmTicket set, a ticket is created"""() {
        given:
        // CURL REQUEST FOR STANDARD CHANGE TEMPLATE
        curlRequestor.requestJson(
            'https://cignatest.service-now.com/api/now/table/std_change_record_producer' \
                                                    + '?sysparm_query=nameSTARTSWITHSC00001&sysparm_fields=sys_id&sysparm_limit=1',
            'auth',
            'GET'
        ) >> [
            responseCode: HttpStatus.SC_OK,
            responseBody: [
                result: [
                    [sys_id: "085989361bea8090caca77761a4bcbc2"]
                ]
            ]
        ]
        // CURL REQUEST FOR CI
        curlRequestor.requestJson(
            'https://cignatest.service-now.com/api/now/table/cmdb_ci'\
                                                    + '?sysparm_query=name%3DBla&sysparm_display_value=true&sysparm_limit=1',
            'auth',
            'GET',
            [:],
            'application/x-www-form-urlencoded'
        ) >> [
            responseCode: HttpStatus.SC_OK,
            responseBody: [
                result: [
                    [sys_id: 'd1ffb7fe1be740103ec4c8092a4bcb89']
                ]
            ]
        ]
        // CURL REQUEST FOR ASSIGNED_TO
        curlRequestor.requestJson(
            'https://cignatest.service-now.com/api/now/table/sys_user' \
                                                    + '?sysparm_query=u_lan_id%3DC71921^active%3Dtrue&sysparm_fields=sys_id&sysparm_limit=1',
            'auth',
            'GET'
        ) >> [
            responseBody: HttpStatus.SC_OK,
            responseBody: [
                result: [
                    [sys_id: 'f71a35fadbd44c90b47d54f948961975']
                ]
            ]
        ]
        // CURL REQUEST FOR REQUESTED_BY
        curlRequestor.requestJson(
            'https://cignatest.service-now.com/api/now/table/sys_user' \
                                                    + '?sysparm_query=u_lan_id%3DC71921^active%3Dtrue&sysparm_fields=sys_id&sysparm_limit=1',
            'auth',
            'GET'
        ) >> [
            responseBody: HttpStatus.SC_OK,
            responseBody: [
                result: [
                    [sys_id: 'f71a35fadbd44c90b47d54f948961975']
                ]
            ]
        ]
        // CURL REQUEST FOR U_EMERGENCY_CONTACT_PERSON
        curlRequestor.requestJson(
            'https://cignatest.service-now.com/api/now/table/sys_user' \
                                                    + '?sysparm_query=u_lan_id%3DC71921^active%3Dtrue&sysparm_fields=sys_id&sysparm_limit=1',
            'auth',
            'GET'
        ) >> [
            responseBody: HttpStatus.SC_OK,
            responseBody: [
                result: [
                    [sys_id: 'f71a35fadbd44c90b47d54f948961975']
                ]
            ]
        ]
        // CURL REQUEST FOR OPENING TICKET
        ticketTimer.startTimeActual >> '2020-01-01 00:00:00'
        ticketTimer.startTimePlanned >> '2021-04-30 18:00:00'
        ticketTimer.endTimePlanned >> '2021-04-30 23:00:00'
        curlRequestor.requestJson(
            'https://cignatest.service-now.com/api/sn_chg_rest/change/standard/085989361bea8090caca77761a4bcbc2',
            'auth',
            'POST',
            [
                assigned_to                 : 'f71a35fadbd44c90b47d54f948961975',
                requested_by                : 'f71a35fadbd44c90b47d54f948961975',
                cmdb_ci                     : 'd1ffb7fe1be740103ec4c8092a4bcb89',
                end_date                    : '2021-04-30 23:00:00',
                start_date                  : '2021-04-30 18:00:00',
                work_start                  : '2020-01-01 00:00:00',
                u_pre_implementation_testing: 'yes_testing_performed',
                u_emergency_contact_person  : 'f71a35fadbd44c90b47d54f948961975',
                u_emergency_contact_number  : '6106080266',
                u_chg_cat                   : null,
            ]
        ) >> [
            responseCode: HttpStatus.SC_OK,
            responseBody: [
                result: [
                    sys_id: [
                        value: 'bda8f120dbe0dc10718cf15aaf9619bb'
                    ]
                ]
            ]
        ]

        when:
        servicenowTicket = new ServicenowTicket(
            script: script,
            psc: psc
        )
        servicenowTicket.ticketConfiguration = [
            ticketType       : 'Servicenow',
            branchPattern    : 'master',
            changeEnvironment: 'Dev',
            plannedDuration  : DURATION,
            credentialsId    : 'auth',
        ] << validBody
        servicenowTicket.ticketConfiguration['title'] = 'SC00001'
        servicenowTicket.ticketConfiguration['u_emergency_contact_number'] = '6106080266'
        servicenowTicket.ticketConfiguration['u_chg_cat'] = '15ae9a3b1b65e0507edeed33b24bcbbf'
        servicenowTicket.cmTicket = 'bda8f120dbe0dc10718cf15aaf9619bb'
        servicenowTicket.urlBasePath = 'https://cignatest.service-now.com'
        servicenowTicket.ticketTimer = ticketTimer
        servicenowTicket.curlRequestor = curlRequestor
        servicenowTicket.notification = notification
        servicenowTicket.openTicket()

        then:
        assert servicenowTicket.cmTicket == 'bda8f120dbe0dc10718cf15aaf9619bb'
    }

    def """When changeTitleIdLookup is called, Standard Change Title is translated into sys_id to open ticket"""() {
        given:
        curlRequestor.requestJson(
            'https://cignatest.service-now.com/api/now/table/std_change_record_producer' \
                                                    + '?sysparm_query=nameSTARTSWITHSC00001&sysparm_fields=sys_id&sysparm_limit=1',
            'credsId',
            'GET'
        ) >> [
            responseCode: HttpStatus.SC_OK,
            responseBody: [
                result: [
                    [sys_id: '085989361bea8090caca77761a4bcbc2']
                ]
            ]
        ]

        when:
        servicenowTicket = new ServicenowTicket(
            script: script,
            psc: psc
        )
        servicenowTicket.ticketConfiguration = [:]
        servicenowTicket.curlRequestor = curlRequestor
        servicenowTicket.ticketConfiguration['credentialsId'] = 'credsId'
        servicenowTicket.urlBasePath = 'https://cignatest.service-now.com'
        servicenowTicket.changeTitleIdLookup('SC00001')

        then:
        1 * getPipelineMock("echo").call(
            'Standard Change Template sys_id: '\
                                                                                                      + '085989361bea8090caca77761a4bcbc2'
        )
    }

    def """When changeIdLookup is called, CI Name is translated into sys_id to open ticket"""() {
        given:
        curlRequestor.requestJson(
            'https://cignatest.service-now.com/api/now/table/cmdb_ci'\
                                                    + "?sysparm_query=name%3DBla&sysparm_display_value=true&sysparm_limit=1",
            'credsId',
            'GET',
            [:],
            'application/x-www-form-urlencoded'
        ) >> [
            responseCode: HttpStatus.SC_OK,
            responseBody: [
                result: [
                    [sys_id: 'd1ffb7fe1be740103ec4c8092a4bcb89']
                ]
            ]
        ]

        when:
        servicenowTicket = new ServicenowTicket(
            script: script,
            psc: psc
        )
        servicenowTicket.ticketConfiguration = [:]
        servicenowTicket.curlRequestor = curlRequestor
        servicenowTicket.ticketConfiguration['credentialsId'] = 'credsId'
        servicenowTicket.urlBasePath = 'https://cignatest.service-now.com'
        servicenowTicket.changeIdLookup('Bla')

        then:
        1 * getPipelineMock("echo").call('CI sys_id: d1ffb7fe1be740103ec4c8092a4bcb89')
    }

    def """When changeUserIdLookup is called, requested_by, assigned_to, and u_emergency_contact_person is translated into sys_id to open ticket"""() {
        given:
        curlRequestor.requestJson(
            'https://cignatest.service-now.com/api/now/table/sys_user' \
                                                    + '?sysparm_query=u_lan_id%3DC71921^active%3Dtrue' \
                                                    + '&sysparm_fields=sys_id&sysparm_limit=1',
            'credsId',
            'GET'
        ) >> [
            responseBody: HttpStatus.SC_OK,
            responseBody: [
                result: [
                    [sys_id: 'f71a35fadbd44c90b47d54f948961975']
                ]
            ]
        ]

        when:
        servicenowTicket = new ServicenowTicket(
            script: script,
            psc: psc
        )
        servicenowTicket.ticketConfiguration = [:]
        servicenowTicket.curlRequestor = curlRequestor
        servicenowTicket.ticketConfiguration['credentialsId'] = 'credsId'
        servicenowTicket.urlBasePath = 'https://cignatest.service-now.com'
        servicenowTicket.changeUserIdLookup('C71921', 'assignedToUserId')

        then:
        1 * getPipelineMock("echo").call(
            'assignedToUserId sys_id: '\
                                                                                    + 'f71a35fadbd44c90b47d54f948961975'
        )
    }

    def """When run is called and a cmTicket is set, a ticket is scheduled"""() {
        given:
        curlRequestor.requestJson(
            'https://cignatest.service-now.com/api/sn_chg_rest/change/standard/bda8f120dbe0dc10718cf15aaf9619bb',
            'auth',
            'PATCH',
            [
                state: 'Scheduled'
            ]
        ) >> [
            responseCode: HttpStatus.SC_OK,
            responseBody: [
                result: [
                    state: [
                        display_value: 'Scheduled'
                    ]
                ]
            ]
        ]

        when:
        servicenowTicket = new ServicenowTicket(
            script: script,
            psc: psc
        )
        servicenowTicket.ticketConfiguration = [
            ticketType       : 'Servicenow',
            branchPattern    : 'master',
            changeEnvironment: 'prod',
            plannedDuration  : DURATION,
            credentialsId    : 'auth',
        ] << validBody
        servicenowTicket.curlRequestor = curlRequestor
        servicenowTicket.notification = notification
        servicenowTicket.urlBasePath = 'https://cignatest.service-now.com'
        servicenowTicket.cmTicket = 'bda8f120dbe0dc10718cf15aaf9619bb'
        servicenowTicket.scheduleTicket()

        then:
        1 * getPipelineMock("echo").call('State: Scheduled')

    }

    def """When run is called and a cmTicket is scheduled, a ticket is implemented"""() {
        given:
        ticketTimer.endTimeActual >> '00:00:00T02-01-20'
        curlRequestor.requestJson(
            'https://cignatest.service-now.com/api/now/table/change_request/bda8f120dbe0dc10718cf15aaf9619bb',
            'auth',
            'PATCH',
            [
                state      : -1,
                close_code : ServicenowTicket.SUCCESS,
                close_notes: 'Successfully Completed\n' +
                    'Audit Bundle: https://repo.sys.cigna.com/ui/artifactSearchResults' +
                    '?name=jenkins-orchestrators-folders-adjudicator-Adjudicator-master-i80&type=artifacts',
                work_end   : '00:00:00T02-01-20'
            ]
        ) >> [
            responseCode: HttpStatus.SC_OK,
            responseBody: [
                result: [
                    state: -1
                ]
            ]
        ]

        when:
        servicenowTicket = new ServicenowTicket(
            script: script,
            psc: psc
        )
        servicenowTicket.ticketConfiguration = [
            ticketType       : 'Servicenow',
            branchPattern    : 'master',
            changeEnvironment: 'prod',
            plannedDuration  : DURATION,
            credentialsId    : 'auth',
        ] << validBody
        servicenowTicket.updateState('deploySuccess')
        servicenowTicket.implementationComments = 'Successfully Completed'
        servicenowTicket.urlBasePath = 'https://cignatest.service-now.com'
        servicenowTicket.cmTicket = 'bda8f120dbe0dc10718cf15aaf9619bb'
        servicenowTicket.curlRequestor = curlRequestor
        servicenowTicket.ticketTimer = ticketTimer
        servicenowTicket.notification = notification
        servicenowTicket.implementTicket()

        then:
        1 * getPipelineMock("echo").call('State: -1')
    }

    def """When a deploy fails, the status is set to 'Deployed with Issues and No Business Impact' and notes are attached"""() {
        given:
        ticketTimer.endTimeActual >> '00:00:00T02-01-20'
        curlRequestor.requestJson(
            'https://cignatest.service-now.com/api/now/table/change_request/bda8f120dbe0dc10718cf15aaf9619bb',
            'auth',
            'PATCH',
            [
                state      : -1,
                close_code : ServicenowTicket.FAIL_CODE,
                close_notes: 'Deployment testing failed, no rollback attempted\n' +
                    'Audit Bundle: https://repo.sys.cigna.com/ui/artifactSearchResults' +
                    '?name=jenkins-orchestrators-folders-adjudicator-Adjudicator-master-i80&type=artifacts',
                work_end   : '00:00:00T02-01-20'
            ]
        ) >> [
            responseCode: HttpStatus.SC_OK,
            responseBody: [
                result: [
                    state: -1
                ]
            ]
        ]

        when:
        servicenowTicket = new ServicenowTicket(
            script: script,
            psc: psc
        )
        servicenowTicket.ticketConfiguration = [
            ticketType       : 'Servicenow',
            isWorkflowEnabled: false,
            branchPattern    : 'master',
            changeEnvironment: 'prod',
            plannedDuration  : DURATION,
            credentialsId    : 'auth',
        ] << validBody
        servicenowTicket.updateState('deployFailure')
        servicenowTicket.implementationComments = 'Deployment testing failed, no rollback attempted'
        servicenowTicket.urlBasePath = 'https://cignatest.service-now.com'
        servicenowTicket.cmTicket = 'bda8f120dbe0dc10718cf15aaf9619bb'
        servicenowTicket.curlRequestor = curlRequestor
        servicenowTicket.ticketTimer = ticketTimer
        servicenowTicket.notification = notification
        servicenowTicket.implementTicket()

        then:
        1 * getPipelineMock("echo").call('State: -1')
    }

    def """When run is called and cmTicket is implemented, a ticket is reviewed"""() {
        given:
        curlRequestor.requestJson(
            'https://cignatest.service-now.com/api/sn_chg_rest/change/standard/bda8f120dbe0dc10718cf15aaf9619bb',
            'auth',
            'PATCH',
            [
                state: 'Review'
            ]
        ) >> [
            responseCode: HttpStatus.SC_OK,
            responseBody: [
                result: [
                    state: [
                        display_value: 'Review'
                    ]
                ]
            ]
        ]

        when:
        servicenowTicket = new ServicenowTicket(
            script: script,
            psc: psc
        )
        servicenowTicket.ticketConfiguration = [
            ticketType       : 'Servicenow',
            branchPattern    : 'master',
            changeEnvironment: 'prod',
            plannedDuration  : DURATION,
            credentialsId    : 'auth',
        ] << validBody
        servicenowTicket.curlRequestor = curlRequestor
        servicenowTicket.urlBasePath = 'https://cignatest.service-now.com'
        servicenowTicket.cmTicket = 'bda8f120dbe0dc10718cf15aaf9619bb'
        servicenowTicket.notification = notification
        servicenowTicket.reviewTicket()

        then:
        1 * getPipelineMock("echo").call('State: Review')

    }

    def """When run is called and there is a cmTicket set, a ticket is closed"""() {
        given:
        curlRequestor.requestJson(
            'https://cignatest.service-now.com/api/now/table/change_request/bda8f120dbe0dc10718cf15aaf9619bb',
            'auth',
            'PATCH',
            [
                state: 'Closed'
            ]
        ) >> [responseCode: HttpStatus.SC_OK, responseBody: [result: [state: '3']]]

        when:
        servicenowTicket = new ServicenowTicket(
            script: script,
            psc: psc
        )
        servicenowTicket.ticketConfiguration = [
            ticketType       : 'Servicenow',
            branchPattern    : 'master',
            changeEnvironment: 'prod',
            plannedDuration  : DURATION,
            credentialsId    : 'auth',
        ]
        servicenowTicket.urlBasePath = 'https://cignatest.service-now.com'
        servicenowTicket.cmTicket = 'bda8f120dbe0dc10718cf15aaf9619bb'
        servicenowTicket.curlRequestor = curlRequestor
        servicenowTicket.notification = notification
        servicenowTicket.closeTicket()

        then:
        assert servicenowTicket.closeState == '3'
    }

    def """When validate is called and required configuration items are missing issues are noted"""() {
        when:
        servicenowTicket = new ServicenowTicket(
            script: script,
            psc: psc
        )
        servicenowTicket.ticketConfiguration = [
            ticketType                : 'Servicenow',
            branchPattern             : 'master',
            changeEnvironment         : 'prod',
            plannedDuration           : 60,
            credentialsId             : 'auth',
            title                     : 'SCT000060',
            requested_by              : 'C71921',
            assigned_to               : 'C71921',
            cmdb_ci                   : 'Bla',
            u_emergency_contact_person: 'C71921',
            u_emergency_contact_number: '6106080266',
            u_chg_cat                 : '15ae9a3b1b65e0507edeed33b24bcbbf',
        ]
        List issues = servicenowTicket.validate()
        then:
        assert issues.size() == 1
        assert issues.contains('title format is not valid format. Must contain SCT or CHG as first three characters.'.toString())
    }

    def """When pollForAllTasksCloseCodes() is called, no exception is thrown"""() {
        given:
        curlRequestor.requestJson(_ as String, _ as String, _ as String, _ as Map) >>
            [responseCode: HttpStatus.SC_OK,
             responseBody: [
                 result: [
                     close_code : [display_value: 'Successful'],
                     close_notes: [display_value: 'Task completed successfully.'],
                     state      : [display_value: 'Closed'],
                     closed_at  : [display_value: '2023-10-01 12:00:00'],
                     closed_by  : [display_value: 'John Doe']
                 ]
             ]
            ]

        servicenowTicket = new ServicenowTicket(
            script: script,
            psc: psc,
            curlRequestor: curlRequestor,
            notification: notification,
            millisPerSec: 1,
        )
        servicenowTicket.ticketConfiguration = [
            ticketType                : 'Servicenow',
            branchPattern             : 'master',
            changeEnvironment         : 'prod',
            plannedDuration           : 60,
            credentialsId             : 'auth',
            title                     : 'SCT000060',
            requested_by              : 'C71921',
            assigned_to               : 'C71921',
            cmdb_ci                   : 'Bla',
            u_emergency_contact_person: 'C71921',
            u_emergency_contact_number: '6106080266',
            u_chg_cat                 : '15ae9a3b1b65e0507edeed33b24bcbbf',
            taskCheckIntervalMinutes  : 15,
            taskTimeOutMinutes        : 120,
        ] as Map<String, Object>
        servicenowTicket.tasks = [
            new ServicenowTask(servicenowTicket, [:]),
            new ServicenowTask(servicenowTicket, [:])
        ]

        when:
        Boolean result = servicenowTicket.allTasksClosedPoll()

        then:
        assert result == expectedResult
        assert servicenowTicket.tasks
        assert servicenowTicket.tasks.every { it.closeCode }
        3 * curlRequestor.requestJson(_ as String, _ as String, _ as String) >>> responseSequence

        where:
        responseSequence                                                              | expectedResult
        [
            [
                responseCode: HttpStatus.SC_OK,
                responseBody: [
                    result: [:]
                ]
            ],
            [
                responseCode: HttpStatus.SC_OK,
                responseBody: [
                    result: [
                        close_code : [display_value: 'Successful'],
                        close_notes: [display_value: 'Task completed successfully.'],
                        state      : [display_value: 'Closed'],
                        closed_at  : [display_value: '2023-10-01 12:00:00'],
                        closed_by  : [display_value: 'John Doe']
                    ]
                ]
            ],
            [
                responseCode: HttpStatus.SC_OK,
                responseBody: [
                    result: [
                        close_code : [display_value: 'Successful'],
                        close_notes: [display_value: 'Task completed successfully.'],
                        state      : [display_value: 'Closed'],
                        closed_at  : [display_value: '2023-10-01 12:00:00'],
                        closed_by  : [display_value: 'John Doe']
                    ]
                ]
            ],
        ]                                                                             | true

        [
            [
                responseCode: HttpStatus.SC_OK,
                responseBody: [
                    result: [
                        close_code : [display_value: 'Successful'],
                        close_notes: [display_value: 'Task completed successfully.'],
                        state      : [display_value: 'Closed'],
                        closed_at  : [display_value: '2023-10-01 12:00:00'],
                        closed_by  : [display_value: 'John Doe']
                    ]
                ]
            ],
            [
                responseCode: HttpStatus.SC_OK,
                responseBody: [
                    result: [:]
                ]
            ],
            [
                responseCode: HttpStatus.SC_OK,
                responseBody: [
                    result: [
                        close_code : [display_value: 'Successful'],
                        close_notes: [display_value: 'Task completed successfully.'],
                        state      : [display_value: 'Closed'],
                        closed_at  : [display_value: '2023-10-01 12:00:00'],
                        closed_by  : [display_value: 'John Doe']
                    ]
                ]
            ],
        ]                                                                             | true

    }

    def """When pollForAllTasksCloseCodes() and tasks don't close, exception is thrown"""() {
        given:
        curlRequestor.requestJson(_ as String, _ as String, _ as String, _ as Map) >>
            [responseCode: HttpStatus.SC_OK,
             responseBody: [
                 result: [
                     close_code : [display_value: 'Successful'],
                     close_notes: [display_value: 'Task completed successfully.'],
                     state      : [display_value: 'Closed'],
                     closed_at  : [display_value: '2023-10-01 12:00:00'],
                     closed_by  : [display_value: 'John Doe']
                 ]
             ]
            ]

        servicenowTicket = new ServicenowTicket(
            script: script,
            psc: psc,
            cmTicket: 'cmt0',
            curlRequestor: curlRequestor,
            notification: notification,
            millisPerSec: 1,
        )
        servicenowTicket.ticketConfiguration = [
            ticketType                : 'Servicenow',
            branchPattern             : 'master',
            changeEnvironment         : 'prod',
            plannedDuration           : 60,
            credentialsId             : 'auth',
            title                     : 'SCT000060',
            requested_by              : 'C71921',
            assigned_to               : 'C71921',
            cmdb_ci                   : 'Bla',
            u_emergency_contact_person: 'C71921',
            u_emergency_contact_number: '6106080266',
            u_chg_cat                 : '15ae9a3b1b65e0507edeed33b24bcbbf',
            taskCheckIntervalMinutes  : 15,
            taskTimeOutMinutes        : 16,
        ] as Map<String, Object>
        servicenowTicket.tasks = [new ServicenowTask(servicenowTicket, [:])]

        when:
        servicenowTicket.allTasksClosedPoll()

        then:
        thrown(Exception)
        assert servicenowTicket.tasks
        assert servicenowTicket.tasks.every { !it.closeCode }
        1 * curlRequestor.requestJson(_ as String, _ as String, _ as String) >>> [
            responseCode: HttpStatus.SC_OK,
            responseBody: [
                result: [
                    close_code : closeCode,
                    close_notes: [display_value: 'Task completed successfully.'],
                    state      : [display_value: 'Closed'],
                    closed_at  : [display_value: '2023-10-01 12:00:00'],
                    closed_by  : [display_value: 'John Doe']
                ]
            ]
        ]

        where:
        closeCode << ['Failed', 'Not Implemented', null]

    }
}
