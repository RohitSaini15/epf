package com.cigna.testing

import com.cigna.jenkins_spock.JenkinsPipelineSpecification

public class TestingSpec extends JenkinsPipelineSpecification {

  def script = {
    def JOB_NAME = 'job/name/here'
  }

  def setup() {
    
  }

  def """When testType is missing from the config a validation error is generated"""() {
    when:
      def build = new ValidTesting(
        config: [:],
        script: script,
        testingConfiguration: configToVerify
      )
      def issues = build.validate()

    then:
      assert build.testingConfiguration == configToVerify
      assert issues.size() == numberOfIssues
    
    where:
      configToVerify << [[other: "stuff"], [testType: "stuff"]]
      numberOfIssues << [1, 0]
  }

}