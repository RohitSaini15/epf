package com.cigna.common.logging

import com.cigna.builds.ValidBuild
import com.cigna.common.utils.FeatureFlags
import com.cigna.jenkins_spock.JenkinsPipelineSpecification
import groovy.json.JsonSlurperClassic

class ConsoleLoggerSpec extends JenkinsPipelineSpecification {
    class Script {
        def env = [
            JOB_NAME    : "stuff",
            BUILD_NUMBER: "1",
            BRANCH_NAME : "test",
            NODE_NAME   : "random node",
            GIT_URL     : "random repo",
            GIT_COMMIT  : "random string",
        ]
        def currentBuild = [
            durationString: "200",
        ]
    }

    JsonSlurperClassic jsonSlurper = new JsonSlurperClassic()
    Map<String, Object> config
    // a phase with no run or closures or phaseInstance - can be used as an outcome of phase sanitizing
    Map<String, Object> trimmedPhase = [buildType: 'maven', run: { throw new Exception("this should never be thrown") }]
    def script = new Script()

    def setup() {

        // simulate a cyclic phase as created by phase loading, logJobInfo should make it non-cyclic before turning it into json
        def phase = trimmedPhase
        def phaseInstance = new ValidBuild(config: phase)
        phase.phaseInstance = phaseInstance
        config = [
            random_stuff: "test",
            phases      : [
                phase
            ]
        ]
    }

    def """ConsoleLogger.logJobInfo called with featureFlags.debug set to #debug"""(Boolean debug) {
        given:
        def logMap = [:]
        // Construct the mocked logMap map for comparison to the test
        logMap['scm'] = [
            repo     : script.env.GIT_URL,
            gitCommit: script.env.GIT_COMMIT,
        ]
        logMap['orchestrator'] = [
            jobName       : "stuff",
            buildNumber   : "1",
            branchName    : "test",
            node          : "random node",
            timeInPipeline: "200",
        ]
        logMap['epf'] = [
            stage             : "test stage",
            message           : "test message",
            //config.phases has the cyclic phase, so add sanitized version to expected output
            userProvidedConfig: config + [phases: [[buildType    : 'maven',
                                                    run          : '<instance of Closure>',
                                                    phaseInstance: '<instance of ValidBuild>']]
            ],
        ]
        if (!debug) {
            Map configWOPhases = config.clone() as Map
            configWOPhases.remove('phases')
            logMap.epf.userProvidedConfig = configWOPhases
        }

        when:
        FeatureFlags.debug = debug
        ConsoleLogger.logJobInfo(
            script, "test stage", "test message", config,
        )

        then:
        1 * getPipelineMock("echo")(
            { String echoString ->
                compareMaps(echoString, logMap)
            }
        )

        where:
        debug << [true, false]
    }

    def """ConsoleLogger.logJobInfo prints the stack trace of an exception passed to it"""() {
        given:
        String exMsg = "test exception"

        when:
        ConsoleLogger.logJobInfo(
            script, "Build failed", "exception thrown", config, new Exception(exMsg)
        )

        then:
        1 * getPipelineMock("echo")(
            { String echoString ->
                echoString.contains("java.lang.Exception: $exMsg")
            }
        )
    }

    Boolean compareMaps(String echoString, Map logMap) {
        // Get the json output from the test and make it a map
        Map actMap = (jsonSlurper.parseText(echoString) as Map)
        actMap = sortMap(actMap)

        Boolean result = actMap == logMap
        result
    }

    Map sortMap(Map map) {
        map.collectEntries { k, v ->
            [(k): (v instanceof Map ? sortMap(v) : v)]
        }.sort { it.key }
    }
}