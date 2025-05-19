package com.cigna.testing

import com.cigna.SinglePodTest

class QEABehaveTestSpec extends SinglePodTest {

    def setup() {
        explicitlyMockPipelineStep('override')
        initScriptAndPsc()
    }

    def """When validate is called and required configuration items are missing issues are noted"""() {
        when:
        def qeabehaveTest = new QEABehaveTest(
            script: script,
            psc: psc
        )

        qeabehaveTest.testingConfiguration = [
                testType     : testType,
                commandString: commandString,
                build: build,
                frameworkType: frameworkType,
        ]
        simulatePodTemplate(psc, qeabehaveTest)
        def issues = qeabehaveTest.validate()

        then:
        assert issues.size() == numberOfIssues

        where:
        testType <<      [null, 'test', 'test', 'test']
        commandString << ['test', 'test', 'test', 'test']
        frameworkType << ['test', null, 'test', 'test']
        build <<         ['test', 'test',  'test', null]
        numberOfIssues << [1, 1, 0, 1]
    }

    def """When run is called a container step is called then, checkout steps are called,
        then chmod step is called, then withCredentials step is called, then qemingler
        command with given arguments, then sh step is called to cat results"""() {
        when:
        def qeabehaveTest = new QEABehaveTest(
            script: script,
            psc: psc
        )

        qeabehaveTest.testingConfiguration = [
                commandString: 'behavex'
        ]
        simulatePodTemplate(psc, qeabehaveTest)
        qeabehaveTest.run()

        then:
        1 * getPipelineMock("sh").call('behavex')
    }
}
