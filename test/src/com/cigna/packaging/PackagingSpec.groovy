package com.cigna.packaging

import com.cigna.jenkins_spock.JenkinsPipelineSpecification

public class PackagingSpec extends JenkinsPipelineSpecification {
    def script = {}

    def setup() {
        
        explicitlyMockPipelineStep('updateGitStatus')
}

    def """When """() {
        when:
            def packaging = new ValidPackaging(
                config: whereConfig,
                script: script
            )
            def issues = packaging.validate()
        then:
            assert issues.size() == whereNumberOfIssues
        where:
            whereConfig << [
                [:],
                [branchPattern: 'something']
            ]
            whereNumberOfIssues << [1, 0]
    }

    def """When createPackage is called for minimal valid, echo once"""() {
        when:
            def packaging = new ValidPackaging(config: [:], script: {})
            packaging.packageApplication()
        then:
            1 * getPipelineMock("echo")('test echo')
    }
}
