package com.cigna.deployment

import com.cigna.SinglePodTest
import com.cigna.common.notification.Notification
import com.cigna.common.phases.PhaseLoader
import com.cigna.common.phases.PodSelector
import com.cigna.common.scm.CommonGit
import com.evernorth.cloudnativebuild.model.BaseDefaults
import com.evernorth.cloudnativebuild.mocks.JenkinsEnv


class PhasesDeploymentSpec extends SinglePodTest {
    def setup() {

        explicitlyMockPipelineStep('updateGitStatus')
        explicitlyMockPipelineStep('override')
        initScriptAndPsc()
    }

    // Mock process
    Notification notification = Mock()

    def "a phase deployment with phase instances executes its phases"() {
        given:
        def phases = [
                [deploymentType : 'phases',
                 branchPattern  : '.*',
                 sdlcEnvironment: 'prod',
                 phases         : [
                         [deploymentType : 'plz',
                          branchPattern  : '.*',
                          sdlcEnvironment: 'prod'],
                         [moduleType            : 'docker',
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
        PhasesDeployment phaseToSimulate = (PhasesDeployment) pl[0][0].phaseInstance
        simulatePodTemplate(psc, phaseToSimulate)
        phaseToSimulate.deploy()
        then:
        1 * getPipelineMock('sh')('plz deploy --show_all_output')
        1 * getPipelineMock('sh')({ it.script =~ /^cnptools publishimage/ })
        // ensure that the pod templates contain the necessary containers
        def createdPodTemplates = psc.podSelector.podTemplates.toString()
        [new PlzDeployment().containerImage, BaseDefaults.defaultDockerBuilderImage].every {
            createdPodTemplates.contains(it)
        }
    }
}
