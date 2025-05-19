package com.cigna.base

import com.cigna.PipelineDSLTest
import com.splunk.splunkjenkins.utils.LogEventHelper

class JenkinsIOSpec extends PipelineDSLTest {

    def """When 'splunkins' object is initialized, info from three LogEventHelper methods should be returned as
            a map object"""() {
        when:
        def splunkEvent = JenkinsIO.initializeSplunkins(this)

        then:
        1 * LogEventHelper.getBuildCauses(splunkins.build)
        1 * LogEventHelper.getEnvironment(splunkins.build)
        1 * LogEventHelper.getBuildVariables(splunkins.build)
        assert splunkEvent instanceof Map
    }
}
