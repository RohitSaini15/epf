package com.cigna.deployment

import com.cigna.SinglePodTest
import com.cigna.common.notification.Notification

public class PlzDeploymentSpec extends SinglePodTest {

    def setup() {
        explicitlyMockPipelineStep('updateGitStatus')
        explicitlyMockPipelineStep('override')
        initScriptAndPsc()
    }

    // Mock process
    Notification notification = Mock()

    def """When validate is called and required configuration items are missing, issues are raised """() {

        when:
        def plzDeployment = new PlzDeployment(script: script, psc: psc)

        plzDeployment.config = [
                deploymentType : 'plz',
                branchPattern  : 'stuff',
                sdlcEnvironment: 'thing',
                awsFed         : [
                        credentialsId: awsFedCredentialsId,
                ]
        ]
        simulatePodTemplate(psc, plzDeployment)
        def issues = plzDeployment.validate()

        then:

        issues.size() == numberOfIssues

        where:
        awsFedCredentialsId << [null, 'test']
        numberOfIssues << [1, 0]
    }

    def """When validate is called and runInAWS is called, issues presented accordingly"""() {
        when:
        def plzDeployment = new PlzDeployment(script: script, psc: psc)

        plzDeployment.config = [
                runInAWS       : whereRunInAWS,
                deploymentType : 'plz',
                branchPattern  : 'stuff',
                sdlcEnvironment: 'thing',
        ]
        plzDeployment.config += whereConfig
        simulatePodTemplate(psc, plzDeployment)
        def issues = plzDeployment.validate()

        then:
        issues.size() == numberOfIssues

        where:
        whereConfig << [
                [awsFed: [credentialsId: 'test']],
                [:],
                [awsFed: [credentialsId: 'test']],
                [aws: [targetAccount: null, accountRoleName: null]],
                [aws: [targetAccount: null, accountRoleName: 'test']],
                [aws: [targetAccount: 'test', accountRoleName: null]],
                [aws: [targetAccount: 'test', accountRoleName: 'test']]
        ]
        whereRunInAWS << [null, true, true, true, true, true, true]
        numberOfIssues << [0, 0, 1, 2, 1, 1, 0]
    }

    def """When deploy is called with alias set, that alias is called instead of 'deploy alias.
        also plz is called with awsFed configured or not."""() {

        when:
        explicitlyMockPipelineVariable('AWS_FED_USERNAME')
        explicitlyMockPipelineVariable('AWS_FED_PASSWORD')
        def plzDeployment = new PlzDeployment(script: script, notification: notification, psc: psc)

        plzDeployment.config = [
                deploymentType : 'plz',
                branchPattern  : 'stuff',
                sdlcEnvironment: 'thing',
                awsFed         : awsFedCredentials,
                alias          : alias,
        ]
        simulatePodTemplate(psc, plzDeployment)
        plzDeployment.deploy()

        then:
        if (alias) {
            1 * getPipelineMock('sh')('plz plan_all --show_all_output')
        } else {
            1 * getPipelineMock('sh')('plz deploy --show_all_output')
        }

        where:
        awsFedCredentials << [[credentialsId: 'stuff'], null]
        alias << ['plan_all', '']
    }

    def """Deploy is called with the correct steps with callPlzFed option is true or false.
        extraArgs and verbosityFlag always get applied"""() {

        when:
        explicitlyMockPipelineVariable('AWS_FED_USERNAME')
        explicitlyMockPipelineVariable('AWS_FED_PASSWORD')
        def plzDeployment = new PlzDeployment(script: script, notification: notification, psc: psc)

        plzDeployment.config = [
                deploymentType : 'plz',
                branchPattern  : 'stuff',
                sdlcEnvironment: sdlcEnvironment,
                verbosityFlag  : '-vvv',
                extraArgs      : extraArgs,
                awsFed         : [
                        credentialsID: 'stuff',
                        callPlzFed   : callPlzFed
                ],
        ]
        simulatePodTemplate(psc, plzDeployment)
        plzDeployment.deploy()

        then:

        if (callPlzFed) {
            1 * getPipelineMock('sh')({ it ==~ /export AWS_FED_PASSWORD=.* && export AWS_FED_USERNAME=.*/ })
            1 * getPipelineMock('sh')('plz fed ci')
        } else {
            0 * getPipelineMock('sh')('plz fed ci')
        }

        where:
        callPlzFed << [true, false]
        sdlcEnvironment << ['ci', '']
        extraArgs << ['ci', '']
    }

    def """When cacheEnabled is provided in the user config and deploy is called, then the 
            configure cache script is called"""() {
        when:
        def plzDeployment = new PlzDeployment(script: script, notification: notification, psc: psc)

        plzDeployment.config = [
                deploymentType: 'plz',
                cacheEnabled  : whereCacheEnabled
        ]
        simulatePodTemplate(psc, plzDeployment)
        plzDeployment.deploy()

        then:
        cacheScriptCalled * getPipelineMock('sh')({ it ==~ /(?s).*mkdir.*echo.*>.*plzconfig.*/ })

        where:
        whereCacheEnabled << [true, false]
        cacheScriptCalled << [1, 0]
    }

    def """When withEnv is configured in the phase, validate if it is propagated to the execution environment"""() {
        when:
        def plzDeployment = new PlzDeployment(script: script, notification: notification, psc: psc)

        plzDeployment.config = [
                deploymentType: 'plz'
        ]
        plzDeployment.config += extraConfig
        simulatePodTemplate(psc, plzDeployment)
        plzDeployment.deploy()

        then:
        1 * getPipelineMock("withEnv").call(actualEnv, _)

        where:
        extraConfig << [
                [withEnv: ['somevar=somevalue']],
                [:]
        ]
        actualEnv << [
                ['TERRAGRUNT_IAM_ROLE=', 'somevar=somevalue'],
                ['TERRAGRUNT_IAM_ROLE=']
        ]
    }

    def """When user provides multiple modules only those modules are deployed"""() {
        when:
        def plzDeployment = new PlzDeployment(
                config: [deploymentType: 'plz', modules: ['//module/aws/module1', '//module/aws/module2']],
                script: script,
                notification: notification, psc: psc
        )
        simulatePodTemplate(psc, plzDeployment)
        plzDeployment.deploy()

        then:
        1 * getPipelineMock('sh')({
            it == 'plz run //module/aws/module1:deploy --show_all_output' +
                    ' && plz run //module/aws/module2:deploy --show_all_output'
        })

        when:
        def plzDeploymentPrefix = new PlzDeployment(
                config: [
                        deploymentType    : 'plz',
                        modules           : ['//module/azure/module1'],
                        moduleDeployTarget: 'engage',
                ],
                script: script,
                notification: notification, psc: psc
        )
        plzDeploymentPrefix.deploy()

        then:
        1 * getPipelineMock('sh')({ it == 'plz run //module/azure/module1:engage --show_all_output' })
    }

    def """When deploy is executed with modules configured but empty, 
        nothing is targeted for deploy"""() {
        when:
        def plzDeploy = new PlzDeployment(
                config: [
                        deploymentType: 'plz',
                        modules       : []
                ],
                script: script,
                notification: notification, psc: psc
        )
        simulatePodTemplate(psc, plzDeploy)
        plzDeploy.deploy()

        then:
        1 * getPipelineMock('echo')({ it == 'Modules is specified, but the list is empty. Nothing to deploy.' })
    }
}
