package com.cigna.linting

import com.cigna.SinglePodTest
import com.cigna.common.exception.EndPipelineException
import com.cigna.common.notification.Notification
import hudson.model.Result
import hudson.model.Run
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException
import org.jenkinsci.plugins.workflow.support.steps.StageStepExecution

public class LintingSpec extends SinglePodTest {
    def cloudName = 'test-cloud'

    def setup() {
        explicitlyMockPipelineStep('updateGitStatus')
        explicitlyMockPipelineStep('override')
        initScriptAndPsc()
    }

    def """When branchPattern or lintingTypes are missing from a the config, an exception is thrown"""() {
        when:
        def build = new Linting(
            config: configToVerify,
            script: script,
            psc: psc
        )

        def issues = build.validate()

        then:
        assert issues.size() == numberOfIssues

        where:
        configToVerify << [
            [:],
            [
                branchPattern: 'stuff',
            ],
            [
                lintingTypes: [
                    'shellcheck': [:]
                ],
            ],
            [
                branchPattern: 'stuff',
                lintingTypes : [
                    'shellcheck': [:],
                    'plz'       : [:],
                ],
            ],
        ]
        numberOfIssues << [2, 1, 1, 0]
    }

    def """When multiple lintingTypes are provided, the containers are added and the proper steps run"""() {
        given:
        def config = [
            branchPattern: 'test',
            lintingTypes : [
                shellcheck: [:],
                bandit    : [:]
            ]
        ]
        when:
        def linting = new Linting(
            config: config,
            script: script,
            psc: psc
        )
        simulatePodTemplate(psc, linting, cloudName)
        linting.run()

        then:
        1 * getPipelineMock("parallel")({
            it ==~ /.*shellcheck.*/ && it ==~ /.*bandit.*/
        })
    }

    def """When awaitApproval throws a FlowInterruptedException and no failure message is defined, we default correctly"""() {
        given:
        def config = [
            emailRecipients: 'grangaswamy@express-scripts.com',
            branchPattern  : '.*',
            lintingTypes   : [
                approvalrequest: [
                    timeOut  : 120,
                    message  : 'Do you want to continue deployment to Prod candidate?',
                    id       : 'cnpApprove',
                    submitter: 'accounts\\ei0733,internal\\C46043',
                ],
            ],
        ]
        Notification notification = Mock()

        Run mockRun = Mock()
        when:
        def linting = new Linting(
            config: config,
            script: script,
            psc: psc
        )
        linting.notification = notification
        simulatePodTemplate(psc, linting, cloudName)
        linting.runApprovalRequest()

        then:
        1 * mockRun.getExternalizableId() >> 'something'
        1 * getPipelineMock("input")(*_) >> {
            throw new FlowInterruptedException(Result.NOT_BUILT,
                new StageStepExecution.CanceledCause(mockRun), null)
        }

        EndPipelineException ex = thrown()
        ex.message == 'Approval request was not approved'
        ex.outcome == 'FAILURE'
    }

    def '''When a linting config overrides imagePulPolicy, it is honored'''() {
        given:
        def config = [
            branchPattern: 'test',
            lintingTypes : [
                go: [
                    branchPattern: '.*',
                    container    : [
                        imagePullPolicy: whereImagePullPolicy,
                    ]
                ]
            ]
        ]
        when:
        def linting = new Linting(
            config: config,
            script: script,
            psc: psc
        )
        simulatePodTemplate(psc, linting, cloudName)
        linting.run()

        then:
        linting.containerSpecs['go'].imagePullPolicy == whereImagePullPolicy
        where:
        whereImagePullPolicy << ['Always', 'Never', 'IfNotPresent']
    }
}
