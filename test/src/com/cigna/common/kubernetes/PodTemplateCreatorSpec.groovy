package com.cigna.common.kubernetes

import com.cigna.SinglePodTest
import com.cigna.state.PipelineStateContext
import com.evernorth.cloudnativebuild.model.BaseDefaults

class PodTemplateCreatorSpec extends SinglePodTest {
    PipelineStateContext psc

    def setup() {
        explicitlyMockPipelineStep('override')
        explicitlyMockPipelineVariable("EPF_GITHUB_ACCESS_TOKEN")
        initScriptAndPsc()
    }

    def """Create baseTemplate and get baseTemplate"""() {
        when:
           def pc = PodTemplateCreator.newPodTemplateCreator()
           Map bt = pc.getTemplate()
        then:
           bt.spec.containers.find { it.name=='jnlp' }.workingDir == '/home/jenkins/agent'
    }

    def """Create baseTemplate and add a Container via params"""() {
        when:
            def pc = PodTemplateCreator.newPodTemplateCreator()
            List<Map> env = [
                [
                    name : 'JENKINS_AGENT_WORKDIR',
                    value: "/home/jenkins/agent -internalDir remoting-${UUID.randomUUID().toString()[0..5]}"
                ]
            ]
            pc.addContainer('jnlpTwo',
                            BaseDefaults.podJnlpImage,
                            'Always',
                            true,
                            '/home/jenkins/agent',
                            500,
                            600,
                            500,
                            600,
                            ['/bin/sh','-c','umask 0000; jenkins-agent'],
                            env
            )
            Map bt = pc.getTemplate()
        then:
            bt.spec.containers.find { it.name=='jnlpTwo'}
    }

    def """Create baseTemplate and add a Container via params and getContainer"""() {
        when:
        def pc = PodTemplateCreator.newPodTemplateCreator()
        List<Map> env = [
            [
                name : 'JENKINS_AGENT_WORKDIR',
                value: "/home/jenkins/agent -internalDir remoting-${UUID.randomUUID().toString()[0..5]}"
            ]
        ]
        pc.addContainer('jnlpTwo',
            BaseDefaults.podJnlpImage,
            'Always',
            true,
            '/home/jenkins/agent',
            500,
            600,
            500,
            600,
            ['/bin/sh','-c','umask 0000; jenkins-agent'],
            env
        )
        Map bt = pc.getContainer('jnlpTwo')
        then:
        bt.containsValue('jnlpTwo')
    }

    def """Create baseTemplate and addEnVars"""() {
        when:
            List<Map> envars = [['MyEnvar': '1'], ['MyEnvar2': 2]]
            def pc = PodTemplateCreator.newPodTemplateCreator()
            pc.addEnVars('jnlp',envars)
            Map bt = pc.getTemplate()
        then:
            bt.spec.containers.find {it.name=='jnlp' }.env.contains(['MyEnvar2':2])
    }

    def """Create baseTemplate and addEnVars all containers"""() {
        when:
            List<Map> envars = [['MyEnvar': '1'], ['MyEnvar2': 2]]
            def pc = PodTemplateCreator.newPodTemplateCreator()
            pc.addEnVars(envars)
            Map bt = pc.getTemplate()
        then:
        bt.spec.containers.find {it.name=='jnlp' }.env.contains(['MyEnvar2':2])
    }

    def """Create baseTemplate and addVolume"""() {
        when:
            def pc = PodTemplateCreator.newPodTemplateCreator()
            pc.addVolume("myVolume")
            Map bt = pc.getTemplate()
        then:
            bt.spec.volumes.find{it.name=='myVolume'}
    }

    def """Create baseTemplate and addVolumeMount"""() {
        when:
            def pc = PodTemplateCreator.newPodTemplateCreator()
            pc.addVolumeMount('jnlp','myEtc', '/home/etc')
            Map bt = pc.getTemplate()
        then:
           bt.spec.containers.find{it.name=='jnlp'}.volumeMounts.find{it.name=='myEtc'}
    }

    def """Create baseTemplate and addResource"""() {
        when:
            def pc = PodTemplateCreator.newPodTemplateCreator()
            pc.addResource('jnlp', 600, 800,1200, 1600)
            Map bt = pc.getTemplate()
        then:
            bt.spec.containers.find{it.name=='jnlp'}.resources.limits.memory=='1600Mi'
    }
}