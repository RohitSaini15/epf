package com.cigna.common.phases

import com.cigna.SinglePodTest
import com.cigna.common.notification.Notification

class PhaseExecutionSpec extends SinglePodTest {

    def setup() {
        explicitlyMockPipelineStep('updateGitStatus')
        explicitlyMockPipelineStep('override')
        initScriptAndPsc()
    }

    def '''phase can execute with null args for creds and configs'''() {
        given:
        def phase = [deploymentType : 'plz',
                     branchPattern  : '.*',
                     sdlcEnvironment: 'prod']
        def loadedPhase = new PhaseLoader(script, psc).loadPhases([phase], Mock(Notification))[0][0]
        simulatePodTemplate(psc, loadedPhase.phaseInstance)
        when:
        PhaseExecution.executePhase(script, loadedPhase, psc, null, null)
        then:
        // this technically relies on the deployment making these calls
        1 * getPipelineMock('withCredentials')([], _)
        1 * getPipelineMock('configFileProvider.call')([], _)
        1 * getPipelineMock('sh')('plz deploy --show_all_output')
    }

    def '''phase execution adds outer creds and configs to those on the phase'''() {
        given:
        def phase = [deploymentType  : 'plz',
                     branchPattern   : '.*',
                     sdlcEnvironment : 'prod',
                     extraCredentials: ['a'],
                     extraConfigs    : ['d']]
        def loadedPhase = new PhaseLoader(script, psc).loadPhases([phase], Mock(Notification))[0][0]
        simulatePodTemplate(psc, loadedPhase.phaseInstance)
        when:
        PhaseExecution.executePhase(script, loadedPhase, psc, ['b', 'c'], ['e', 'f'])
        then:
        // this technically relies on the deployment making these calls
        1 * getPipelineMock('withCredentials')(['a', 'b', 'c'], _)
        1 * getPipelineMock('configFileProvider.call')(['d', 'e', 'f'], _)
        1 * getPipelineMock('sh')('plz deploy --show_all_output')
    }

    def '''phase execution runs a before-phase script if provided'''() {
        given:
        def phase = [buildType       : 'valid',
                     branchPattern   : '.*',
                     sdlcEnvironment : 'prod',
                     beforePhase     : [script: 'echo hi'],
                     sonarEnabled    : false,
                     checkmarxEnabled: false
        ]
        def loadedPhase = new PhaseLoader(script, psc).loadPhases([phase], Mock(Notification))[0][0]
        simulatePodTemplate(psc, loadedPhase.phaseInstance)
        when:
        PhaseExecution.executePhase(script, loadedPhase, psc)
        then:
        1 * getPipelineMock('stage')('Before-phase script', *_)
        1 * getPipelineMock('sh')('echo hi')
    }

    def '''phase execution runs around-phase closures if provided'''() {
        given:
        def phase = [buildType       : 'valid',
                     branchPattern   : '.*',
                     sdlcEnvironment : 'prod',
                     beforePhase     : [closure: { echo 'this goes before the phase' }],
                     afterPhase      : [closure: { echo 'this goes after the phase' }],
                     sonarEnabled    : false,
                     checkmarxEnabled: false
        ]
        def loadedPhase = new PhaseLoader(script, psc).loadPhases([phase], Mock(Notification))[0][0]
        simulatePodTemplate(psc, loadedPhase.phaseInstance)
        when:
        PhaseExecution.executePhase(script, loadedPhase, psc)
        then:
        1 * getPipelineMock('echo')('this goes before the phase')
        1 * getPipelineMock('echo')('this goes after the phase')
    }
}
