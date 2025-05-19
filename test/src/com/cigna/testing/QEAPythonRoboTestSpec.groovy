package com.cigna.testing

import com.cigna.SinglePodTest

class QEAPythonRoboTestSpec extends SinglePodTest {

    def setup() {
        explicitlyMockPipelineStep('override')
        explicitlyMockPipelineStep('emailext')
        initScriptAndPsc()
    }

    def """When validate is called and required configuration items are missing issues are noted"""() {
        given:
        def qeapythonroboTest = new QEAPythonRoboTest(
            script: script,
            psc: psc
        )

        qeapythonroboTest.testingConfiguration = [
            testType     : testType,
            build        : build,
            frameworkType: frameworkType,
            commandString: commandString,
            reportDir    : 'Reports',
            robotFilePath: 'test.robot'
        ]

        when:
        simulatePodTemplate(psc, qeapythonroboTest)
        def issues = qeapythonroboTest.validate()

        then:
        assert issues.size() == numberOfIssues

        where:
        testType << [null, 'test', 'test', 'test']
        commandString << ['test', 'test', 'test', null]
        frameworkType << ['test', 'test', 'web', 'mainframe']
        build << ['test', 'test', null, 'test']
        numberOfIssues << [1, 0, 1, 1]
    }

    def '''robot image is selected based on framework type parameter'''() {
        given:
        def qeapythonroboTest = new QEAPythonRoboTest(
            script: script,
            psc: psc
        )

        qeapythonroboTest.testingConfiguration = [
            commandString: 'robot',
            reportDir    : 'Reports',
            frameworkType: frameworkType,
            robotFilePath: 'test.robot'
        ]

        when:
        simulatePodTemplate(psc, qeapythonroboTest)

        then:
        psc.podSelector.podTemplates['test-cloud'].contains(containerName)
        where:
        frameworkType | containerName
        'web'         | 'qea-pythonrobot-web'
        'mainframe'   | 'qea-pythonrobot-mainframe'
        'playwright'  |  'qea-pythonrobot-playwright'
        ''            | 'python-robot'
    }

    def """When run is called, a command line is constructed from parameters and executed"""() {
        given:
        def qeapythonroboTest = new QEAPythonRoboTest(
            script: script,
            psc: psc
        )

        qeapythonroboTest.testingConfiguration = [
            commandString : 'robot',
            reportDir     : 'Reports',
            frameworkType : 'web',
            robotFilePath : 'test.robot',
            optionalParams: optParams
        ]

        when:
        simulatePodTemplate(psc, qeapythonroboTest)
        qeapythonroboTest.run()

        then:
        1 * getPipelineMock("sh").call("robot -d Reports ${commandLinePart} --nostatusrc test.robot")
        where:
        optParams  | commandLinePart
        'blib'     | 'blib'
        ['a', 'b'] | '[a, b]'
        ['a': 1]   | '{a=1}'
    }

    def '''failing to invoke the tests doesn't fail the phase'''() {
        given:
        def qeapythonroboTest = new QEAPythonRoboTest(
            script: script,
            psc: psc
        )

        qeapythonroboTest.testingConfiguration = [
            commandString: 'oops',
            reportDir    : 'Reports',
            frameworkType: 'web',
            robotFilePath: 'test.robot'
        ]

        when:
        simulatePodTemplate(psc, qeapythonroboTest)
        qeapythonroboTest.run()

        then:
        // when we call a command line that errors out
        1 * getPipelineMock('sh')('oops -d Reports --nostatusrc test.robot') >> { throw new Exception('non-zero exit code') }
        // this throws an error as expected
        noExceptionThrown()
    }

    def """When RoboTest is called and the zephyrCommandString is defined, the test will execute zephyr command. """() {
        when:
        def qeapythonroboTest = new QEAPythonRoboTest(
            script: script,
            psc: psc
        )

        qeapythonroboTest.testingConfiguration = [
            commandString      : 'robot',
            reportDir          : 'Reports',
            frameworkType      : 'web',
            zephyrCommandString: 'python3 -m robot --pythonpath . tests/zephyr_integration/zephyr_integration.robot',
            robotFilePath      : 'test.robot'
        ]

        simulatePodTemplate(psc, qeapythonroboTest)
        qeapythonroboTest.run()

        then:
        1 * getPipelineMock("sh").call('robot -d Reports --nostatusrc test.robot')
        1 * getPipelineMock("sh")('python3 -m robot --pythonpath . --nostatusrc tests/zephyr_integration/zephyr_integration.robot')
    }

    def """When RoboTest is called and the zephyrCommandString is defined which would possibly fail due to incorrect test path is handled by try/catch, the test will execute zephyr command. """() {
        when:
        def qeapythonroboTest = new QEAPythonRoboTest(
            script: script,
            psc: psc
        )

        qeapythonroboTest.testingConfiguration = [
            commandString      : 'robot',
            reportDir          : 'Reports',
            frameworkType      : 'web',
            zephyrCommandString: 'python3 -m robot --pythonpath . zephyr-tests/zephyr_integration/zephyr_integration.robot',
            robotFilePath      : 'test.robot'
        ]

        simulatePodTemplate(psc, qeapythonroboTest)
        qeapythonroboTest.run()

        then:
        1 * getPipelineMock("sh").call('robot -d Reports --nostatusrc test.robot')
        1 * getPipelineMock("sh")('python3 -m robot --pythonpath . --nostatusrc zephyr-tests/zephyr_integration/zephyr_integration.robot')         // No exception is thrown, it prints an error message?

    }

    def """When RoboTest is called and the continueOnTestFailure is false, and fail count is not '0', build will fail and exception is thrown. """() {
        when:
        def qeapythonroboTest = new QEAPythonRoboTest(
            script: script,
            psc: psc
        )

        qeapythonroboTest.testingConfiguration = [
            commandString        : 'robot',
            reportDir            : 'Reports',
            frameworkType        : 'web',
            continueOnTestFailure: false,
            robotFilePath        : 'test.robot'
        ]

        simulatePodTemplate(psc, qeapythonroboTest)
        getPipelineMock("sh").call([script: "sed -n 's/.*fail=\"\\([0-9]*\\)\".*/\\1/p' Reports/output.xml", returnStdout: true]) >> '10'
        qeapythonroboTest.run()

        then:
        def e = thrown(Exception)
        e.message == "There are test failures in the test suite"
    }

    def """When RoboTest is called and the continueOnTestFailure is true, build should not throw any exception. """() {
        given:
        def qeapythonroboTest = new QEAPythonRoboTest(
            script: script,
            psc: psc
        )

        qeapythonroboTest.testingConfiguration = [
            commandString        : 'robot',
            reportDir            : 'Reports',
            frameworkType        : 'web',
            continueOnTestFailure: true,
            robotFilePath        : 'test.robot'
        ]

        when:
        simulatePodTemplate(psc, qeapythonroboTest)
        getPipelineMock("sh").call([script: "sed -n 's/.*fail=\"\\([0-9]*\\)\".*/\\1/p' Reports/output.xml", returnStdout: true]) >> '5'
        qeapythonroboTest.run()

        then:
        1 * getPipelineMock("sh").call('robot -d Reports --nostatusrc test.robot')
    }

    def """When RoboTest is called and the continueOnTestFailure is false, and fail count is '0', build should not throw any exception. """() {
        given:
        def qeapythonroboTest = new QEAPythonRoboTest(
            script: script,
            psc: psc
        )

        qeapythonroboTest.testingConfiguration = [
            commandString        : 'robot',
            reportDir            : 'Reports',
            frameworkType        : 'web',
            continueOnTestFailure: false,
            robotFilePath        : 'test.robot',

        ]
        getPipelineMock("sh").call([script: "sed -n 's/.*fail=\"\\([0-9]*\\)\".*/\\1/p' Reports/output.xml", returnStdout: true]) >> '0'

        when:
        simulatePodTemplate(psc, qeapythonroboTest)
        qeapythonroboTest.run()

        then:
        1 * getPipelineMock("sh").call('robot -d Reports --nostatusrc test.robot')
    }

    def """RoboTest phase can send an email using the QEA-specific parameters"""() {
        given:

        def configPython = [
            commandString  : 'robot',
            reportDir      : 'Reports',
            frameworkType  : 'web',
            robotFilePath  : 'test.robot',
            emailBody      : 'information',
            emailRecipients: 'person@cigna.com',
            reportFile     : 'index.html',
        ]
        def qeaPythonRoboTest = new QEAPythonRoboTest(
            script: script,
            psc: psc
        )
        qeaPythonRoboTest.testingConfiguration = configPython
        script.env.JOB_BASE_NAME = 'some job'
        script.env.ENVIRONMENT = 'test env'

        when:
        simulatePodTemplate(psc, qeaPythonRoboTest)
        qeaPythonRoboTest.run()

        then:
        1 * getPipelineMock('sh')({ it['script']?.startsWith('sed') }) >> '0'
        assert qeaPythonRoboTest.config.email == configEmail

        where:
        configEmail << [
            [recipients        : 'person@cigna.com',
             subject           : 'Test Execution: some job; BUILD_NUMBER:[1]; REGION : test env',
             attachmentFile    : 'index.html',
             body              : 'information<br><br>null'
            ]
        ]
    }

    def """RoboTest phase can send an email using generic email parameters"""() {
        given:

        def configPython = [
            commandString: 'robot',
            reportDir    : 'Reports',
            frameworkType: 'web',
            robotFilePath: 'test.robot',
            email        : [
                body          : 'information',
                recipients    : 'person@cigna.com',
                attachmentFile: 'index.html'
            ]
        ]
        def qeaPythonRoboTest = new QEAPythonRoboTest(
            script: script,
            psc: psc
        )
        qeaPythonRoboTest.testingConfiguration = configPython
        script.env.JOB_BASE_NAME = 'some job'
        script.env.ENVIRONMENT = 'test env'

        when:
        simulatePodTemplate(psc, qeaPythonRoboTest)
        qeaPythonRoboTest.run()

        then:
        1 * getPipelineMock('sh')({ it['script']?.startsWith('sed') }) >> '0'
        assert qeaPythonRoboTest.config.email == configPython.email

        where:
        configEmail << [[
                            to                : 'person@cigna.com',
                            subject           : 'EPF pipeline execution JOB: some job; BUILD_NUMBER: [1];',
                            attachmentsPattern: 'index.html',
                            body              : 'information<br><br>Build URL: some build url',
                            mimeType          : 'text/html']
        ]
    }
}
