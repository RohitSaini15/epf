package com.cigna.testing

import com.cigna.SinglePodTest

class QEAMavenTestSpec extends SinglePodTest {
    
    def setup() {
        explicitlyMockPipelineStep('override')
        explicitlyMockPipelineVariable("MAVEN_SETTINGS")
        initScriptAndPsc()
    }

    def """When simple configuration is defined, maven is called with the correct m2 repo path"""() {
        when:
        def qeaMavenTest = new QEAMavenTest(
            script: script,
            psc: psc
        )

        qeaMavenTest.testingConfiguration = [           
             commandString: 'mvn clean install'                
        ]
        simulatePodTemplate(psc, qeaMavenTest)
        qeaMavenTest.run()

        then:
        1 * getPipelineMock("sh").call('mvn clean install -Dmaven.repo.local=\'/var/maven/.m2/repository\'')
    }

    def '''when QEA Maven Test is configured to use different settings.xml, the correct command line is constructed'''() {
        given:
        // We assign these dynamic test variables here so that we can interact with them during testing/debugging,
        // otherwise we get an exception when trying to inspect the current values.
        def configBlock = [
            useOrchestratorMavenSettings: orchestratorSettingsFlag,
            commandString: 'mvn clean install'
        ]
        when:
        def qeaMavenTest = new QEAMavenTest(
            script: script,
            config: configBlock,
            psc: psc
        )
        qeaMavenTest.testingConfiguration = configBlock
        simulatePodTemplate(psc, qeaMavenTest)
        qeaMavenTest.run()
        then:
        1 * getPipelineMock("sh").call("mvn ${settingsDirective}")
        where:
        orchestratorSettingsFlag << [ true, false ]
        settingsDirective << [ 'clean install -Dmaven.repo.local=\'/var/maven/.m2/repository\' -s Mock Generator for [MAVEN_SETTINGS] ', 'clean install -Dmaven.repo.local=\'/var/maven/.m2/repository\'']
    }
}
