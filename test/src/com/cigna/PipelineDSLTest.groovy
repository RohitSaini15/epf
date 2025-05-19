package com.cigna

import com.cigna.common.exception.ErrorStepException
import com.cigna.jenkins_spock.JenkinsPipelineSpecification
import com.evernorth.cloudnativebuild.mocks.JenkinsEnv
import com.splunk.splunkjenkins.utils.LogEventHelper

/**
 * Base class for tests that don't need a specific pipeline script loaded. Bindings and mocks and
 * what not are added to `this` in the setup method, so things that need to pass a script can pass `this`.
 */
class PipelineDSLTest extends JenkinsPipelineSpecification {
    def JOB_NAME = "orchestrators-folders/job/test/job/here"
    def BUILD_NUMBER = '123'
    def BRANCH_NAME = 'feature/api-test'
    def env = [:]
    String causeShortDescription = 'upstream description'
    String causeUserName = ""
    String causeUserId = "userid"

    def getCause(def cause = null) {
        return [
            cause           : [:],
            userName        : causeUserName,
            userId          : causeUserId,
            getUserId       : { return causeUserId },
            shortDescription: causeShortDescription
        ]
    }
    def currentBuild = [
        result     : "SUCCESS",
        description: "",
        rawBuild   : [
            parent   : [
                parent: [
                    sources: [
                        [
                            source: [
                                repository: 'develop'
                            ]
                        ]
                    ]
                ]
            ],
            action   : [],
            getAction: { c -> ['getTotalCount': { 30 }, 'getFailCount': { 0 }, 'getSkipCount': { 1 }] },
            getCause : { getCause() }
        ]
    ]
    def error = { String msg ->
        throw new ErrorStepException(msg)
    }
    def scmMock = [
        [
            name: 'master'
        ]
    ]
    def remoteConfigs = [
        [
            url: "https://github.sys.cigna.com/somecool_project/super_cool.git"
        ]
    ]

    def configureScript(def step) {

        env = JenkinsEnv.build([
            JOB_NAME              : JOB_NAME,
            JENKINS_URL           : "https://orchestrator1.orchestrator-v2.sys.cigna.com",
            GIT_COMMIT            : '177a8fa56d5cc76c9ca715cca22776319f829b70',
            CNP_DEFAULT_JAVA_IMAGE: 'cnp/cnp-docker-maven-java11:1.0.2-dev-ov2',
            BRANCH_NAME           : BRANCH_NAME,
            BUILD_NUMBER          : BUILD_NUMBER
        ])

        explicitlyMockPipelineStep('libraryResource')
        explicitlyMockPipelineStep('checkpoint')
        step.getBinding().setVariable("currentBuild", currentBuild)
        step.getBinding().setVariable("env", env)
        step.getBinding().setVariable("error", error)
        step.getBinding().setVariable("scm", explicitlyMockPipelineVariable("scm"))
        getPipelineMock("scm.getProperty")('branches') >> scmMock
        getPipelineMock("scm.getProperty")('userRemoteConfigs') >> {
            remoteConfigs
        }
        GroovyMock(LogEventHelper, global: true)
        getPipelineMock("splunkins.getProperty")('build') >> new Object()
    }

    def setup() {
        configureScript(this)
    }
}
