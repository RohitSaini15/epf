package com.cigna.testing

import com.cigna.SinglePodTest
import java.nio.file.Files

class JMeterTestSpec extends SinglePodTest {

    JMeterTest jmeterTest

    def setup() {
        initScriptAndPsc()
        jmeterTest = new JMeterTest(
            script: script,
            psc: psc
        )
        new File('/tmp/jmeter-results.jtl').delete()
    }

    def """When a JMeter class is called, and the use does not supply a test path, validation
        catches it"""() {
        when:
        jmeterTest.testingConfiguration = [
            testType: "JMeter"
        ]
        jmeterTest.testingConfiguration += plan
        def issues = jmeterTest.validate()

        then:
        assert issues.size() == numberOfIssues

        where:
        plan << [[planPath: '/test/path'], []]
        numberOfIssues << [0, 1]
    }

    def """hasFailures detected failing tests and reports accordingly"""() {
        when:
        def inputFileContents = '''timeStamp,elapsed,label,responseCode,responseMessage,threadName,dataType,success,failureMessage,bytes,sentBytes,grpThreads,allThreads,URL,Latency,IdleTime,Connect
1663706560865,841,Make Passing SCM Request,401,Unauthorized,Simulate 10 Simultaneous Users making 20 requests each 1-9,text,true,,351,502,10,10,https://adjudicator.apps.gp-2-nonprod.openshift.cignacloud.com/adjudicate,840,0,651
1663706560865,841,Make Passing SCM Request,401,Unauthorized,Simulate 10 Simultaneous Users making 20 requests each 1-7,text,false,,351,502,10,10,https://adjudicator.apps.gp-2-nonprod.openshift.cignacloud.com/adjudicate,837,0,658
1663706560865,841,Make Passing SCM Request,401,Unauthorized,Simulate 10 Simultaneous Users making 20 requests each 1-10,text,true,,351,502,10,10,https://adjudicator.apps.gp-2-nonprod.openshift.cignacloud.com/adjudicate,837,0,658
1663706560865,877,Make Passing SCM Request,401,Unauthorized,Simulate 10 Simultaneous Users making 20 requests each 1-1,text,false,,351,502,10,10,https://adjudicator.apps.gp-2-nonprod.openshift.cignacloud.com/adjudicate,877,0,658
1663706560865,917,Make Passing SCM Request,401,Unauthorized,Simulate 10 Simultaneous Users making 20 requests each 1-6,text,true,,351,502,10,10,https://adjudicator.apps.gp-2-nonprod.openshift.cignacloud.com/adjudicate,917,0,646
1663706560865,940,Make Passing SCM Request,401,Unauthorized,Simulate 10 Simultaneous Users making 20 requests each 1-8,text,false,,351,502,10,10,https://adjudicator.apps.gp-2-nonprod.openshift.cignacloud.com/adjudicate,940,0,646
1663706560865,947,Make Passing SCM Request,401,Unauthorized,Simulate 10 Simultaneous Users making 20 requests each 1-2,text,true,,351,502,10,10,https://adjudicator.apps.gp-2-nonprod.openshift.cignacloud.com/adjudicate,947,0,646
'''

        boolean hasFailures = false
        Files.createTempFile('jmeter-results', '.jtl',).with { complianceRequestFile ->
            complianceRequestFile.write(inputFileContents)
            1 * getPipelineMock("readFile")(*_) >> inputFileContents
            hasFailures = jmeterTest.hasFailures(complianceRequestFile.toAbsolutePath().toString())
        }
        then:

        hasFailures
        jmeterTest.failureCount == 3

    }

    def """When JMeter is called, and a plan path is passed, EPF successfully executes the plan"""() {
        when:
        jmeterTest.testingConfiguration = [
            testType: "JMeter",
            planPath: "/plan/path.jmx",
            args    : "-P 1337 --proxy localhost"
        ]
        def inputFileContents = '''timeStamp,elapsed,label,responseCode,responseMessage,threadName,dataType,success,failureMessage,bytes,sentBytes,grpThreads,allThreads,URL,Latency,IdleTime,Connect
1663706560865,841,Make Passing SCM Request,401,Unauthorized,Simulate 10 Simultaneous Users making 20 requests each 1-9,text,true,,351,502,10,10,https://adjudicator.apps.gp-2-nonprod.openshift.cignacloud.com/adjudicate,840,0,651
'''

        File.createTempFile('jmeter-results','.jtl').with { complianceRequestFile ->
            complianceRequestFile.deleteOnExit()
            complianceRequestFile.write(inputFileContents)
            1 * getPipelineMock("readFile")(*_) >> inputFileContents
            jmeterTest.run()
        }

        then:
        1 * getPipelineMock("sh")({
            it ==~ /jmeter -f -n -t \/plan\/path.jmx -l .*.jtl -P 1337 --proxy localhost/
        })
    }
}