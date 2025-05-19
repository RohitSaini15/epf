package com.cigna.freestyle

import com.cigna.SinglePodTest
import com.cigna.common.notification.Notification

class FreestyleSpec extends SinglePodTest {

    def setup() {
        explicitlyMockPipelineStep('readProperties')
        explicitlyMockPipelineStep('updateGitStatus')
        initScriptAndPsc()
    }

    def """When validate is called and required configuration items are missing, issues are raised """() {
        when:
        def freestyle = new Freestyle(script: script, psc: psc)

        freestyle.config = [
            freestyleType: freestyleType,
            branchPattern: 'stuff',
            container    : [
                image: containerImage,
                version: 'latest'
            ],
            script       : script
        ]

        freestyle.prePodConfig()
        def issues = freestyle.validate()

        then:
        issues.size() == numberOfIssues

        where:
        freestyleType << [null, 'stuff']
        containerImage << [null, 'stuff']
        script << [null, 'stuff']
        numberOfIssues << [3, 0]
    }

    def """When readPropertiesFile is called, read properties in the script and assert key value pairs in PipelineMetadata"""() {
        given:
        def freestyle = new Freestyle(script: script, psc: psc)

        freestyle.config = [
            freestyleType  : 'stuff',
            branchPattern  : 'stuff',
            outputPropsFile: 'rpm',
            container      : [
                image  : 'image',
                version: 'latest'
            ]
        ]

        freestyle.prePodConfig()

        when:
        freestyle.addMetadataFromPropertiesFile()

        then:
        1 * getPipelineMock("readProperties")(*_) >> ['key': 'value']
        assert psc.metadata.get('rpm:key') == 'value'
    }

    def """When withEnv is configured in the phase, validate if it is propagated to the execution environment"""() {
        given:
        def freestyle = new Freestyle(script: script, psc: psc)
        explicitlyMockPipelineStep('override')
        explicitlyMockPipelineStep('updateGitlabCommitStatus')
        explicitlyMockPipelineStep('updateGitStatus')
        freestyle.notification = Mock(Notification)
        freestyle.config = [
                freestyleType: 'stuff',
                branchPattern: 'stuff',
                outputPropsFile: 'rpm',
                container    : [
                        image: 'cnp/image',
                        version: 'latest'
                ],
                script       : script
        ]
        freestyle.config += whereConfig
        freestyle.prePodConfig()

        when:
        simulatePodTemplate(psc, freestyle)
        freestyle.run()

        then:
        if (withenv)
            1 * getPipelineMock("withEnv").call(actualEnv, _)
        else
            0 * getPipelineMock("withEnv").call(actualEnv, _)
        where:
        withenv << [true, false]
        whereConfig << [
                [withEnv        : ['somevar=somevalue']],
                [:]
        ]
        actualEnv << [
              ['somevar=somevalue'],
              [null]
        ]
    }

    def """When freestyle image is jnlp, do not add a new container, instead repurpose the default jnlp container """() {
        given:
        def freestyle = new Freestyle(script: script, psc: psc)
        explicitlyMockPipelineStep('override')
        explicitlyMockPipelineStep('updateGitlabCommitStatus')
        explicitlyMockPipelineStep('updateGitStatus')
        freestyle.notification = Mock(Notification)

        freestyle.config = [
            freestyleType: freestyleType,
            branchPattern: 'stuff',
            container    : [
                image: containerImage,
                version: 'latest'
            ],
            script       : script
        ]
        freestyle.prePodConfig()

        when:
        simulatePodTemplate(psc, freestyle)
        freestyle.run()

        then:
        freestyle.basePodConfig.containers.size() == containerSize
        // no extra container is added to the list if jnlp will be there by default in every pod

        where:
        freestyleType << ['use-jnlp', 'non-jnlp']
        containerImage << ['cnp/use-jnlp', 'cnp/image']
        containerSize << [0, 1]
    }
}
