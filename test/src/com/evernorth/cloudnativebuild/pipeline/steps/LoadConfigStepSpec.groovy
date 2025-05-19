package com.evernorth.cloudnativebuild.pipeline.steps

import com.evernorth.cloudnativebuild.mocks.MockJenkins
import com.evernorth.cloudnativebuild.model.BuildConfiguration
import com.evernorth.cloudnativebuild.model.logging.LogLevel
import spock.lang.Specification

class LoadConfigStepSpec extends Specification{
    def "when prop name in camel case return in all caps with underscores"(){
        setup:
        def propName = "thisIsAProperty"
        def expected = "CNP_THIS_IS_A_PROPERTY"
        when:
        def actual = LoadConfigStep.propNameToEnv(propName)
        then:
        expected==actual
    }


    def "when prop name in camel case return in all caps when no caps"(){
        setup:
        def propName = "property"
        def expected = "CNP_PROPERTY"
        when:
        def actual = LoadConfigStep.propNameToEnv(propName)
        then:
        expected==actual
    }

    def "prop name in snake case convert to camel case"(){
        setup:
        def envVarName = "CNP_FOO_BAR"
        def expected = "fooBar"
        when:
        def actual = LoadConfigStep.envToPropName(envVarName)
        then:
        expected==actual
    }

    def "when updateDefaultBuildConfigurationFromEnvironment variables in environment override default settings"(){
        setup:
        def jenkins = new MockJenkins()
        def defaultConfig = new BuildConfiguration()
        def step = new LoadConfigStep(jenkins)
        when:
        step.updateDefaultBuildConfigurationFromEnvironment(defaultConfig)
        then:
        // settings in the Jenkins mock is defaulted to error
        defaultConfig.logLevel == LogLevel.ERROR
    }

    def "environment settings contain all values from buildConfiguration"(){
        setup:
        def jenkins = new MockJenkins()
        jenkins.env.addValue("CNP_JENKINS_DOLLAR_HACK_ENABLED","true")
        jenkins.env.addValue("CNP_ENABLE_EVENTS","true")
        jenkins.env.addValue("CNP_ENABLE_RESOURCE_REQUESTS","false")

        jenkins.env.addValue("CNP_AWS_IDP_PROVIDER","okta")
        jenkins.env.addValue("CNP_RELEASE_STASH_PATTERN","**")
        jenkins.env.addValue("CNP_DEFAULT_DOJO_MSG_IMAGE","foo")
        jenkins.env.addValue("CNP_DOJO_MSG_ENDPOINT","foo")
        def defaultConfig = new BuildConfiguration()
        def step = new LoadConfigStep(jenkins)
        step.updateDefaultBuildConfigurationFromEnvironment(defaultConfig)
        when:
        def list = step.createEnvListFromBuildConfiguration(defaultConfig)
        then:
        list.find{it.toString().startsWith("CNP_LOG_LEVEL=")}!=null
        list.find{it.toString().startsWith("CNP_DEPLOYABLE_BRANCHES=")}=="CNP_DEPLOYABLE_BRANCHES=^develop\$,^release.*,^hotfix.*"
    }



}
