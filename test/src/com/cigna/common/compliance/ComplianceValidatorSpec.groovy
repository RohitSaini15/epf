package com.cigna.common.compliance

import com.cigna.SinglePodTest
import com.evernorth.cloudnativebuild.mocks.Scm
import jenkins.scm.api.SCM2
import spock.lang.Shared

import java.util.regex.Matcher

class ComplianceValidatorSpec extends SinglePodTest {

    FakeBuildPhase fakeBuildPhase
    FakeOpenDeploymentPhase fakeOpenDeploymentPhase
    FakeUcdDeploymentPhase fakeUcdDeploymentPhase
    FakeFooDeploymentPhase fakeFooDeploymentPhase
    FakeBlazeTestingPhase fakeBlazeTestingPhase
    FakeJMeterTestingPhase fakeJMeterTestingPhase
    FakeNewmanTestingPhase fakeNewmanTestingPhase
    FakeSeleniumTestingPhase fakeSeleniumTestingPhase
    FakeTicketPhase fakeTicketPhase

    class FakeBuildPhase { def displayName(def prefix) {"Fake Build Phase"} }

    class FakeOpenDeploymentPhase {def displayName(def prefix) {"Fake Open Deployment Phase"}}

    class FakeUcdDeploymentPhase {def displayName(def prefix) {"Fake Ucd Deployment Phase"}}

    class FakeFooDeploymentPhase {def displayName(def prefix) {"Fake Foo Deployment Phase"}}

    class FakeBlazeTestingPhase {
        String testType = 'performance'
        def displayName(def prefix) {"Fake Blaze Testing Phase"}
    }

    class FakeJMeterTestingPhase {
        String testType = 'performance'
        def displayName(def prefix) {"Fake JMeter Testing Phase"}
    }

    class FakeNewmanTestingPhase {
        String testType = 'integration'
        def displayName(def prefix) {"Fake Newman Testing Phase"}
    }

    class FakeSeleniumTestingPhase {
        String testType = 'integration'
        def displayName(def prefix) {"Fake Selenium Testing Phase"}
    }

    class FakeTicketPhase {def displayName(def prefix) {"Fake Ticket Phase"}}

    @Shared
    def baseConfig = [
        isStashEnabled: true,
        phases        : [
            [
                buildType    : 'FakePhase',
                phaseInstance: new FakeBuildPhase()
            ]
        ]
    ]

    @Shared
    def validDeploymentConfigPhases = [
        [
            buildType    : 'FakePhase',
            phaseInstance: new FakeBuildPhase()
        ],
        [
            deploymentType        : 'FakeDeployment',
            sdlcEnvironment       : 'INT',
            phaseInstance         : new FakeOpenDeploymentPhase(),
            isProductionDeployment: false,
            testing               : [
                [
                    testType     : 'blazemeter',
                    phaseInstance: new FakeBlazeTestingPhase()
                ]
            ]
        ],
        [
            deploymentType        : 'FakeDeployment',
            sdlcEnvironment       : 'SIT',
            phaseInstance         : new FakeUcdDeploymentPhase(),
            isProductionDeployment: false,
            testing               : [
                [
                    testType     : 'seleniumGrid',
                    phaseInstance: new FakeSeleniumTestingPhase()
                ]
            ]
        ],
        [
            deploymentType        : 'FakeDeployment',
            sdlcEnvironment       : 'PROD',
            phaseInstance         : new FakeFooDeploymentPhase(),
            isProductionDeployment: true,
            testing               : [
                [
                    testType     : 'JMeter',
                    phaseInstance: new FakeJMeterTestingPhase()
                ],
                [
                    testType     : 'newman',
                    phaseInstance: new FakeNewmanTestingPhase()
                ]
            ],
            ticket                : [
                ticketType   : 'HpsmTicket',
                phaseInstance: new FakeTicketPhase()
            ]
        ]
    ]

    @Shared
    def validDeploymentConfig = [
        phases: validDeploymentConfigPhases
    ]

    def setupEachTest(def config = baseConfig) {
        initScriptAndPsc(null, config)
        config.phases.each {
            this.(it.phaseInstance.getClass().simpleName.uncapitalize()) = it.phaseInstance
            if (it.phaseInstance.getClass().simpleName.toLowerCase().contains('deployment')) {
                List<Map<String, Object>> testingPhases = it.get('testing', [])
                testingPhases.each { phase ->
                    this.(phase.phaseInstance.getClass().simpleName.uncapitalize()) = phase.phaseInstance
                }
                Map<String, Object> ticket = it.get('ticket', null)
                if (ticket) {
                    this.(ticket.phaseInstance.getClass().simpleName.uncapitalize()) = ticket.phaseInstance
                }
            }
        }

        psc.complianceValidator.loadPhases(config.phases).initState(config.phases)
        psc.complianceValidator.correlationID = '11111111-2222-3333-4444-55555555'
        psc.complianceValidator.urlTransformer = { Matcher m ->
            '\u001B[8mha:////4BYziqubzp4UAPnC9UpP8R/V8b8dyR4ZQfU7r3UU1j5nAAAA8h+LCAAAAAAAAP9djzF' +
                    'OxEAMRU0QVFSUKxoaumSqpWAVIUQD0kLBnsBKvMlkJzMj24PYE9BzDWg4BMfhDgxaIS1x87/s72f54xuOhOG8T60EXz' +
                    'XBS3BUPYSW3N02EjvrN49BCXZ1UAC8MJxN8v+il/X7Z/0Wvwoo7uHYke+0X8JhYqdwuhzwGY1D35mVsvXdIuNmE9ztT' +
                    'qd3YzYnCq+9apQrY5hiqGQrVWM7j3l7NMkaZLVrbHRFyE3/RJKcyrXHkeqB/MZ6KUMekCijBpZyHVxLWbEdUmub32Z5' +
                    's+dHFCUu5/MLzW/Wf3z5AWgtu7U8AQAA\u001B[0m' + GridBuilder.TARGET_VALUE
        }
    }

    void "When setState is called, the state is set appropriately"() {
        given:
        setupEachTest(baseConfig)
        when:
        def cv = psc.complianceValidator
        cv.setState(fakeBuildPhase, 'Foo', 'Bar')
        cv.setState(fakeBuildPhase, 'Bin', 'Baz')

        then:

        assert cv.state[fakeBuildPhase.toString()] == [
            state: 'Pending', environment: 'Undefined', name: 'Fake Build Phase', Foo: 'Bar', Bin: 'Baz'
        ]
    }

    void "When isStashEnabled is set at phase scope, all phases and global level have isStashEnabled matching phase scope "() {
        given:
        def testConfig = [
            isStashEnabled: true,
            phases        : [
                [
                    isStashEnabled: isStashEnabled,
                    buildType     : 'FakePhase',
                    phaseInstance : new FakeBuildPhase()
                ]
            ]
        ]
        when:
        setupEachTest(testConfig)
        then:
        assert testConfig.isStashEnabled == isStashEnabled

        where:
        isStashEnabled << [true, false]
    }


    void "When isStashEnabled is false at global scope, all phases and global level have isStashEnabled false "() {
        given:
        def testConfig = [
            isStashEnabled: false,
            phases        : [
                [
                    isStashEnabled: isStashEnabled,
                    buildType     : 'FakePhase',
                    phaseInstance : new FakeBuildPhase()
                ]
            ]
        ]
        when:
        setupEachTest(testConfig)
        then:
        assert testConfig.isStashEnabled == false

        where:
        isStashEnabled << [true, false]
    }

    void """test scm data"""() {
        given:
        setupEachTest(validDeploymentConfig)
        script.scm = [
            branches: [
                [name: 'master']
            ],
            userRemoteConfigs: [
                [url: remoteConfigs]
            ]
        ]

        when:
        def cv = psc.complianceValidator
        cv.retrieveSCMData(psc)

        then:
        assert psc.goalsConfig.executorGet('SCM') == expectedGoals

        where:
        remoteConfigs << [
            'https://github.sys.cigna.com/cigna/enterprise-pipeline-framework.git',
            'https://github.com/test/blah.git',
            'https://git.express-scripts.com/esi/boom.git'
        ]
        expectedGoals << [
            [
                "repo": "enterprise-pipeline-framework",
                "owner": "cigna",
                "branch": "master",
                "type": "github"
            ],
            [
                "repo": "blah",
                "owner": "test",
                "branch": "master",
                "type": "publicgithub"
            ],
            [
                "repo": "boom",
                "owner": "esi",
                "branch": "master",
                "type": "esigithub"
            ]
        ]
    }

    void """When initState is called, there is a 'Pending' state set, an environment (if applicable) set, and a simple
            name is set, then deployment phases checked for testing and ticket, and initialized"""() {
        given:
        setupEachTest(validDeploymentConfig)

        when:
        def cv = psc.complianceValidator

        then:
        assert cv.state[fakeBuildPhase.toString()] == [state: 'Pending', environment: 'Undefined', name: 'Fake Build Phase']
        assert cv.state[fakeOpenDeploymentPhase.toString()] == [state: 'Pending', environment: 'INT', name: 'Fake Open Deployment Phase']
        assert cv.state[fakeBlazeTestingPhase.toString()] == [state: 'Pending', environment: 'Undefined', name: 'Fake Blaze Testing Phase']
        assert cv.state[fakeUcdDeploymentPhase.toString()] == [state: 'Pending', environment: 'SIT', name: 'Fake Ucd Deployment Phase']
        assert cv.state[fakeSeleniumTestingPhase.toString()] == [state: 'Pending', environment: 'Undefined', name: 'Fake Selenium Testing Phase']
        assert cv.state[fakeFooDeploymentPhase.toString()] == [state: 'Pending', environment: 'PROD', name: 'Fake Foo Deployment Phase']
        assert cv.state[fakeNewmanTestingPhase.toString()] == [state: 'Pending', environment: 'Undefined', name: 'Fake Newman Testing Phase']
        assert cv.state[fakeJMeterTestingPhase.toString()] == [state: 'Pending', environment: 'Undefined', name: 'Fake JMeter Testing Phase']
        assert cv.state[fakeTicketPhase.toString()] == [state: 'Pending', environment: 'Undefined', name: 'Fake Ticket Phase']
    }

    void """test scan risk violation with url encoding"""() {
        given:
        setupEachTest(baseConfig)
        def cv = psc.complianceValidator
        when:
        cv.complianceCheckMessages += [
            [
                Article: 'Checkmarx',
                Message: "Scan Risk 85 violates threshold of 60, Please address Checkmarx Scanning Issues by visiting 'https://cigna.checkmarx.net/CxWebClient/projectscans.aspx?id=adjudicator&ProjectState=true'",
                Status : 'FAILURE',
            ],
        ]
        String outcome = cv.outputFormatted('test')
        then:
        assert outcome == '''--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|                                                                               Compliance                                                                               |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|             Article              |                                            Statement                                             |              Status              |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|       Checkmarx       |              Scan Risk 85 violates threshold of 60, Please address Checkmarx Scanning Issues by visiting               |        FAILURE        |
|                       |             'https://cigna.checkmarx.net/CxWebClient/projectscans.aspx?id=adjudicator&ProjectState=true'               |                       |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|                                                                                                                                                                        |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|                                                                                Pipeline                                                                                |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|                         Phase                         |                         State                         |                      Environment                       |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|                   Fake Build Phase                    |                        Pending                        |                       Undefined                        |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|                                                                                                                                                                        |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|                                                                                  test                                                                                  |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|                                      Please see https://github.sys.cigna.com/cigna/enterprise-pipeline-framework for phase types.                                      |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
'''
    }

    void """test formatting an anchor"""() {
        given:
        setupEachTest(baseConfig)
        when:
        def cv = psc.complianceValidator
        cv.news += [
            [
                Author : 'Phillip Sinats',
                Date   : '10/18/2021',
                Message: 'The Ops Readiness article will now fail the CI/CD pipeline when not compliant. ' +
                    'Information on how to comply with the article can be found at ' +
                    'https://pages.github.sys.cigna.com/cigna/adjudicator-docs/docs/userguide/tutorial/',
            ],
            [
                Author : 'Auditor',
                Date   : '03/03/2022',
                Message: 'Written Audit bundle to \'\u001B[8mha:////4BYziqubzp4UAPnC9UpP8R/V8b8dyR4ZQfU7r3UU1j5nAAAA8h+LCAAAAAAAAP9djzF' +
                    'OxEAMRU0QVFSUKxoaumSqpWAVIUQD0kLBnsBKvMlkJzMj24PYE9BzDWg4BMfhDgxaIS1x87/s72f54xuOhOG8T60EXz' +
                    'XBS3BUPYSW3N02EjvrN49BCXZ1UAC8MJxN8v+il/X7Z/0Wvwoo7uHYke+0X8JhYqdwuhzwGY1D35mVsvXdIuNmE9ztT' +
                    'qd3YzYnCq+9apQrY5hiqGQrVWM7j3l7NMkaZLVrbHRFyE3/RJKcyrXHkeqB/MZ6KUMekCijBpZyHVxLWbEdUmub32Z5' +
                    's+dHFCUu5/MLzW/Wf3z5AWgtu7U8AQAA\u001B[0m(Click Link)\' & correlation id \'ee778aaa-6163-4489-9dc2-465591a38649\', evicting from correlation cache',
            ]
        ]

        cv.state = [
            'Linting'             : [
                name       : 'Linting',
                state      : 'Success',
                environment: 'Undefined'
            ],
            'Go Build'            : [
                name       : 'Go Build',
                state      : 'Success',
                environment: 'Undefined'
            ],
            'Kaniko Packaging'    : [
                name       : 'Kaniko Packaging',
                state      : 'Success',
                environment: 'Undefined'
            ],
            'Openshift Deployment': [
                name       : 'Openshift Deployment',
                state      : 'Pending',
                environment: 'dev_oscp4'
            ],
            'Newman Test'         : [
                name       : 'Newman Test',
                state      : 'Pending',
                environment: 'Undefined'
            ],
            'JMeter  Test'        : [
                name       : 'JMeter Test',
                state      : 'Pending',
                environment: 'Undefined'
            ],
        ]

        cv.complianceCheckMessages += [
            [
                Article: 'Ops Readiness: CMDB',
                Message: 'Recovery tier not set. Request update here: https://cigna.service-now.com/nav_to.do?uri=%2Fkb_view.do%3Fsys_kb_id%3Dc0f46f8d87767c1c9fa2fc07cebb35cb',
                Status : 'WARNING',
            ],
            [
                Article: 'SCM',
                Message: 'The last 5 Pull requests have not been approved by the user that created it.',
                Status : 'SUCCESS',
            ],
            [
                Article: 'Ops Readiness: CMDB',
                Message: 'Recovery tier not set. Request update here: https://cigna.service-now.com/nav_to.do?uri=%2Fkb_view.do%3Fsys_kb_id%3Dc0f46f8d87767c1c9fa2fc07cebb35cb',
                Status : 'WARNING',
            ],
            [
                Article: 'Ops Readiness: CMDB',
                Message: 'Recovery tier not set. Request update here: https://cigna.service-now.com/nav_to.do?uri=%2Fkb_view.do%3Fsys_kb_id%3Dc0f46f8d87767c1c9fa2fc07cebb35cb',
                Status : 'WARNING',
            ],
            [
                Article: 'Ops Readiness: CMDB',
                Message: 'Recovery tier not set. Request update here: https://cigna.service-now.com/nav_to.do?uri=%2Fkb_view.do%3Fsys_kb_id%3Dc0f46f8d87767c1c9fa2fc07cebb35cb',
                Status : 'WARNING',
            ],
        ]

        String outcome = cv.outputFormatted(
            "Men occasionally stumble over the truth, but most of them pick themselves up and hurry off as though nothing had happened. " +
                "Success is stumbling from failure to failure with no loss of enthusiasm. " +
                "Men occasionally stumble over the truth, but most of them pick themselves up and hurry off as though nothing had happened. " +
                "Success is stumbling from failure to failure with no loss of enthusiasm. " +
                "Men occasionally stumble over the truth, but most of them pick themselves up and hurry off as though nothing had happened. " +
                "Success is stumbling from failure to failure with no loss of enthusiasm. " +
                "Men occasionally stumble over the truth, but most of them pick themselves up and hurry off as though nothing had happened. " +
                "Success is stumbling from failure to failure with no loss of enthusiasm. " +
                "Men occasionally stumble over the truth, but most of them pick themselves up and hurry off as though nothing had happened. " +
                "Success is stumbling from failure to failure with no loss of enthusiasm. " +
                "Men occasionally stumble over the truth, but most of them pick themselves up and hurry off as though nothing had happened. " +
                "Success is stumbling from failure to failure with no loss of enthusiasm. " +
                "Men occasionally stumble over the truth, but most of them pick themselves up and hurry off as though nothing had happened. " +
                "Success is stumbling from failure to failure with no loss of enthusiasm. " +
                "Men occasionally stumble over the truth, but most of them pick themselves up and hurry off as though nothing had happened. " +
                "Success is stumbling from failure to failure with no loss of enthusiasm. " +
                "Men occasionally stumble over the truth, but most of them pick themselves up and hurry off as though nothing had happened. " +
                "Success is stumbling from failure to failure with no loss of enthusiasm. " +
                "Men occasionally stumble over the truth, but most of them pick themselves up and hurry off as though nothing had happened. " +
                "Success is stumbling from failure to failure with no loss of enthusiasm.")

        then:
        assert outcome == '''--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|                                                                               Compliance                                                                               |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|             Article              |                                            Statement                                             |              Status              |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|  Ops Readiness: CMDB  |                                      Recovery tier not set. Request update here:                                       |        WARNING        |
|                       |       https://cigna.service-now.com/nav_to.do?uri=%2Fkb_view.do%3Fsys_kb_id%3Dc0f46f8d87767c1c9fa2fc07cebb35cb         |                       |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|          SCM          |                      The last 5 Pull requests have not been approved by the user that created it.                      |        SUCCESS        |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|  Ops Readiness: CMDB  |                                      Recovery tier not set. Request update here:                                       |        WARNING        |
|                       |       https://cigna.service-now.com/nav_to.do?uri=%2Fkb_view.do%3Fsys_kb_id%3Dc0f46f8d87767c1c9fa2fc07cebb35cb         |                       |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|  Ops Readiness: CMDB  |                                      Recovery tier not set. Request update here:                                       |        WARNING        |
|                       |       https://cigna.service-now.com/nav_to.do?uri=%2Fkb_view.do%3Fsys_kb_id%3Dc0f46f8d87767c1c9fa2fc07cebb35cb         |                       |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|  Ops Readiness: CMDB  |                                      Recovery tier not set. Request update here:                                       |        WARNING        |
|                       |       https://cigna.service-now.com/nav_to.do?uri=%2Fkb_view.do%3Fsys_kb_id%3Dc0f46f8d87767c1c9fa2fc07cebb35cb         |                       |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|                                                                                                                                                                        |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|                                                                                Pipeline                                                                                |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|                         Phase                         |                         State                         |                      Environment                       |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|                        Linting                        |                        Success                        |                       Undefined                        |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|                       Go Build                        |                        Success                        |                       Undefined                        |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|                   Kaniko Packaging                    |                        Success                        |                       Undefined                        |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|                 Openshift Deployment                  |                        Pending                        |                       dev_oscp4                        |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|                      Newman Test                      |                        Pending                        |                       Undefined                        |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|                      JMeter Test                      |                        Pending                        |                       Undefined                        |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|                                                                                                                                                                        |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|     Author     |    Date    |                                                                 Message                                                                  |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
| Phillip Sinats | 10/18/2021 |The Ops Readiness article will now fail the CI/CD pipeline when not compliant. Information on how to comply with the article can be found |
|                |            |                          at https://pages.github.sys.cigna.com/cigna/adjudicator-docs/docs/userguide/tutorial/                           |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|    Auditor     | 03/03/2022 |     Written Audit bundle to '\u001B[8mha:////4BYziqubzp4UAPnC9UpP8R/V8b8dyR4ZQfU7r3UU1j5nAAAA8h+LCAAAAAAAAP9djzFOxEAMRU0QVFSUKxoaumSqpWAVIUQD0kLBnsBKvMlkJzMj24PYE9BzDWg4BMfhDgxaIS1x87/s72f54xuOhOG8T60EXzXBS3BUPYSW3N02EjvrN49BCXZ1UAC8MJxN8v+il/X7Z/0Wvwoo7uHYke+0X8JhYqdwuhzwGY1D35mVsvXdIuNmE9ztTqd3YzYnCq+9apQrY5hiqGQrVWM7j3l7NMkaZLVrbHRFyE3/RJKcyrXHkeqB/MZ6KUMekCijBpZyHVxLWbEdUmub32Z5s+dHFCUu5/MLzW/Wf3z5AWgtu7U8AQAA\u001B[0m(Click Link)' & correlation id 'ee778aaa-6163-4489-9dc2-465591a38649', evicting from correlation cache      |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|                                                                                                                                                                        |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|Men occasionally stumble over the truth, but most of them pick themselves up and hurry off as though nothing had happened. Success is stumbling from failure to failure |
|   with no loss of enthusiasm. Men occasionally stumble over the truth, but most of them pick themselves up and hurry off as though nothing had happened. Success is    |
|  stumbling from failure to failure with no loss of enthusiasm. Men occasionally stumble over the truth, but most of them pick themselves up and hurry off as though    |
|     nothing had happened. Success is stumbling from failure to failure with no loss of enthusiasm. Men occasionally stumble over the truth, but most of them pick      |
|themselves up and hurry off as though nothing had happened. Success is stumbling from failure to failure with no loss of enthusiasm. Men occasionally stumble over the  |
| truth, but most of them pick themselves up and hurry off as though nothing had happened. Success is stumbling from failure to failure with no loss of enthusiasm. Men  |
|  occasionally stumble over the truth, but most of them pick themselves up and hurry off as though nothing had happened. Success is stumbling from failure to failure   |
|   with no loss of enthusiasm. Men occasionally stumble over the truth, but most of them pick themselves up and hurry off as though nothing had happened. Success is    |
|  stumbling from failure to failure with no loss of enthusiasm. Men occasionally stumble over the truth, but most of them pick themselves up and hurry off as though    |
|     nothing had happened. Success is stumbling from failure to failure with no loss of enthusiasm. Men occasionally stumble over the truth, but most of them pick      |
|themselves up and hurry off as though nothing had happened. Success is stumbling from failure to failure with no loss of enthusiasm. Men occasionally stumble over the  |
|   truth, but most of them pick themselves up and hurry off as though nothing had happened. Success is stumbling from failure to failure with no loss of enthusiasm.    |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|                                      Please see https://github.sys.cigna.com/cigna/enterprise-pipeline-framework for phase types.                                      |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
'''
    }

    void """Test formatted output"""() {
        given:
        setupEachTest(baseConfig)
        when:
        def cv = psc.complianceValidator
        cv.news = [
            [
                Author : 'Winston Churchill',
                Date   : '09/16/2021',
                Message: 'Men occasionally stumble over the truth, but most of them pick themselves up and hurry off as though nothing had happened. ' +
                    'Success is stumbling from failure to failure with no loss of enthusiasm.'
            ]
        ]

        cv.state = [
            'Linting'             : [
                name       : 'Linting',
                state      : 'Success',
                environment: 'Undefined'
            ],
            'Go Build'            : [
                name       : 'Go Build',
                state      : 'Success',
                environment: 'Undefined'
            ],
            'Kaniko Packaging'    : [
                name       : 'Kaniko Packaging',
                state      : 'Success',
                environment: 'Undefined'
            ],
            'Openshift Deployment': [
                name       : 'Openshift Deployment',
                state      : 'Pending',
                environment: 'dev_oscp4'
            ],
            'Newman Test'         : [
                name       : 'Newman Test',
                state      : 'Pending',
                environment: 'Undefined'
            ],
            'JMeter  Test'        : [
                name       : 'JMeter Test',
                state      : 'Pending',
                environment: 'Undefined'
            ],
        ]
        String outcome = cv.outputFormatted(
            "This is a message being sent to the grid builder for validation purposes and is intentionally long so that" +
                " the grid builder logic will break it in to multiple lines for formatting. ")

        then:
        assert outcome == '''--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|                                                                               Compliance                                                                               |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|             Article              |                                            Statement                                             |              Status              |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|                                                                                                                                                                        |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|                                                                                Pipeline                                                                                |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|                         Phase                         |                         State                         |                      Environment                       |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|                        Linting                        |                        Success                        |                       Undefined                        |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|                       Go Build                        |                        Success                        |                       Undefined                        |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|                   Kaniko Packaging                    |                        Success                        |                       Undefined                        |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|                 Openshift Deployment                  |                        Pending                        |                       dev_oscp4                        |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|                      Newman Test                      |                        Pending                        |                       Undefined                        |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|                      JMeter Test                      |                        Pending                        |                       Undefined                        |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|                                                                                                                                                                        |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|      Author       |    Date    |                                                                Message                                                                |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
| Winston Churchill | 09/16/2021 |Men occasionally stumble over the truth, but most of them pick themselves up and hurry off as though nothing had happened. Success is  |
|                   |            |                                    stumbling from failure to failure with no loss of enthusiasm.                                      |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|                                                                                                                                                                        |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
| This is a message being sent to the grid builder for validation purposes and is intentionally long so that the grid builder logic will break it in to multiple lines   |
|                                                                            for formatting.                                                                             |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
|                                      Please see https://github.sys.cigna.com/cigna/enterprise-pipeline-framework for phase types.                                      |
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
'''
    }

    void """verify that adjudicate and audit closures utilize the same correlation id"""() {
        given:
        setupEachTest(validDeploymentConfig)
        explicitlyMockPipelineStep('readYaml')
        def cv = psc.complianceValidator
        when:
        def adjudicateMap = cv.adjudicateClosure(psc)(null)
        def auditMap = cv.auditClosure()(null)
        then:
        assert adjudicateMap != null
        assert auditMap != null
        def correlationElement = "\"CorrelationID\":\"${cv.correlationID}\""
        assert auditMap.contains(correlationElement)
        assert adjudicateMap.contains(correlationElement)
    }

    def """verify transformed urls are not malformed"""() {
        given:
        setupEachTest(baseConfig)
        when:
        def cv = psc.complianceValidator
        psc.complianceValidator.urlTransformer = { Matcher m ->
            m.group(1)
        }

        String msg = "Written Audit bundle to &#39;https://repo.sys.cigna.com/ui/artifactSearchResults?name=jenkins-orchestrators-folders-adjudicator-" +
            "Veracode+Executor-master-37&amp;type=artifacts&#39; &amp; correlation id &#39;ea61d1de-1631-4149-9704-61ab531a1699&#39;, evicting from correlation cache"
        then:
        assert cv.transformURLs(msg) == "Written Audit bundle to 'https://repo.sys.cigna.com/ui/artifactSearchResults?name=jenkins-orchestrators-folders-adjudicator" +
            "-Veracode+Executor-master-37&type=artifacts' & correlation id 'ea61d1de-1631-4149-9704-61ab531a1699', evicting from correlation cache"
    }
}
