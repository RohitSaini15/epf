package com.cigna.parallel

import com.cigna.SinglePodTest
import com.cigna.common.notification.Notification
import com.cigna.common.phases.PhaseLoader
import com.cigna.deployment.PlzDeployment
import com.evernorth.cloudnativebuild.model.BaseDefaults

class ParallelSpec extends SinglePodTest {
    def setup() {
        explicitlyMockPipelineStep('updateGitStatus')
        explicitlyMockPipelineStep('override')
        initScriptAndPsc()
    }

    // Mock process
    Notification notification = Mock()

    def "a parallel deployment with phase instances executes its phases"() {
        given:
        def phases = [
                [parallelType   : 'jenkins',
                 branchPattern  : '.*',
                 sdlcEnvironment: 'prod',
                 phases         : [
                         plz    : [deploymentType : 'plz',
                                   branchPattern  : '.*',
                                   sdlcEnvironment: 'prod'],
                         publish: [moduleType            : 'docker',
                                   branchPattern         : '.*',
                                   sdlcEnvironment       : 'prod',
                                   isProductionDeployment: true,
                                   moduleName            : 'cnp-publish-image',
                                   subCommand            : 'publishimage',
                                   serviceAccount        : 'kaniko',
                                   args                  : [sourceRepository            : 'registry-dev.cigna.com',
                                                            sourceImage                 : 'test-source-image',
                                                            sourceTag                   : 'test-source-tag',
                                                            destinationRegistry         : 'registry-dev.cigna.com',
                                                            destinationTag              : 'test-target-tag',
                                                            publishOnNonDeployableBranch: true]]
                 ]]
        ]
        when:
        def pl = new PhaseLoader(script, psc).loadPhases(phases, notification)
        Parallel phaseToSimulate = (Parallel) pl[0][0].phaseInstance
        simulatePodTemplate(psc, phaseToSimulate)
        phaseToSimulate.run()
        then:
        // plz runs
        1 * getPipelineMock('sh')({ it.startsWith('plz')})
        // publish image runs
        1 * getPipelineMock('sh')({ it.script.startsWith('cnptools')})
        // ensure that the pod templates contain the necessary containers
        def createdPodTemplates = psc.podSelector.podTemplates.toString()
        [new PlzDeployment().containerImage, BaseDefaults.defaultDockerBuilderImage].every {
            createdPodTemplates.contains(it)
        }
    }

    def "a parallel deployment with extraCreds and extraConfigs to its phases"() {
        given:

        def phases = [
            [parallelType   : 'jenkins',
             branchPattern  : '.*',
             sdlcEnvironment: 'prod',
             phases         : [
                 plz    : [deploymentType : 'plz',
                           branchPattern  : '.*',
                           sdlcEnvironment: 'prod',
                           extraConfigs: ['a'],
                           extraCredentials      : ['cheeseit'],
                 ],
                 publish: [moduleType            : 'docker',
                           branchPattern         : '.*',
                           sdlcEnvironment       : 'prod',
                           isProductionDeployment: true,
                           moduleName            : 'cnp-publish-image',
                           subCommand            : 'publishimage',
                           serviceAccount        : 'kaniko',
                           args                  : [sourceRepository            : 'registry-dev.cigna.com',
                                                    sourceImage                 : 'test-source-image',
                                                    sourceTag                   : 'test-source-tag',
                                                    destinationRegistry         : 'registry-dev.cigna.com',
                                                    destinationTag              : 'test-target-tag',
                                                    publishOnNonDeployableBranch: true],
                 ],
             ]]
    ]
        when:
        def pl = new PhaseLoader(script, psc).loadPhases(phases, notification)
        Parallel phaseToSimulate = (Parallel) pl[0][0].phaseInstance
        simulatePodTemplate(psc, phaseToSimulate)
        phaseToSimulate.run()
        then:
        1 * getPipelineMock("configFileProvider.call")(['a'], _)
        1 * getPipelineMock("withCredentials")(['cheeseit'], _)
    }
}
