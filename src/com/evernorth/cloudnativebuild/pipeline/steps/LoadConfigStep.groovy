package com.evernorth.cloudnativebuild.pipeline.steps

import com.cloudbees.groovy.cps.NonCPS
import com.evernorth.cloudnativebuild.model.BuildConfiguration
import com.evernorth.cloudnativebuild.service.Logger

class LoadConfigStep {
    def scriptContext
    Logger logger

    LoadConfigStep(def scriptContext){
        this.scriptContext=scriptContext
        this.logger =  new Logger(scriptContext)
    }

    /**
     * Replaces configuration defaults with values from in Environment Variables
     * @param configuration - Configuration object that contains default values
     */
    @NonCPS
    void updateDefaultBuildConfigurationFromEnvironment(BuildConfiguration configuration){
        scriptContext.env.getEnvironment().each { name, value ->
            if (EnvironmentOverrides[name as String]) {
                String propName = envToPropName(name as String)
                def propValue = EnvironmentOverrides[name as String](value)
                configuration?.setProperty(propName, propValue)
            }
        }
    }

    /**
     * Creates a list of Strings in format name=value for use in passing to Jenkins withEnv closure
     * @param configuration - Build configuration
     * @return List
     */
    @NonCPS
    static List createEnvListFromBuildConfiguration(BuildConfiguration configuration){
        def envList = []
        configuration.properties.each { key, value ->
            if (value != '' && value != null && (!key.toString().startsWith("class"))){
                def keyFormatted = propNameToEnv(key)
                envList.add("${keyFormatted}=${value}")
            }
         }
        return envList
    }
    /**
     * Converts fooBar to FOO_BAR
     * @param name - property name
     * @return - property name in snake case
     */
    @NonCPS
    static String propNameToEnv(String name){
        return "${name in BuildConfiguration.nonPrefixedProperties ? '': 'CNP_'}${name.replaceAll('[A-Z]',{"${it}${(it as String)?.toLowerCase()}"}).split('[A-Z]').collect{"$it"}.join('_')}".toUpperCase()
    }

    /**
     * Converts string in FOO_BAR format to fooBar
     */
    @NonCPS
    static String envToPropName(String name) {
        String newName = name.replaceFirst('CNP_', '').split('_').collect { it.toLowerCase().capitalize() }.join()
        return newName[0].toLowerCase() + newName[1..-1]
    }

    /**
     * List of properties that can be overridden with environment
     * variables defined in Jenkins
     */
    private Map<String, Closure> EnvironmentOverrides = [
            "CNP_SIMULATE_ONLY"    : { value -> Boolean.valueOf(value as String) }, // if enabled - print commands but don't execute
            "CNP_DISABLE_ALL_TESTS": { value -> Boolean.valueOf(value as String) }, // if enabled - tests are skipped
            "CNP_DISABLE_ALERTS"     : { value -> Boolean.valueOf(value as String) }, // if enabled - no alerts sent
            "CNP_DEB_LOCAL"    : { value -> value },
            "CNP_DOCKER_REGISTRY" : { value -> value},
            "CNP_DOCKER_DEV_REGISTRY" : { value -> value},
            "CNP_DEB_VIRTUAL" : {value -> value},
            "CNP_DEB_PUBLIC_MIRROR": {value -> value},
            "CNP_DEB_PRIVATE": {value -> value},
            "CNP_DEFAULT_DOCKER_IMAGE": {value -> value},
            "CNP_DEFAULT_K8S_IMAGE": {value -> value},
            "CNP_DEFAULT_DOCKER_BUILDER_IMAGE": {value -> value},
            "CNP_DEFAULT_GO_IMAGE": {value -> value},
            "CNP_DEFAULT_NPM_IMAGE": {value -> value},
            "CNP_DEFAULT_AWS_IMAGE": {value -> value},
            "CNP_DEFAULT_PCF_IMAGE": {value -> value},
            "EPF_DEFAULT_ANSIBLE_IMAGE": {value -> value},        
            "CNP_DEFAULT_JAVA_IMAGE": {value -> value},
            "CNP_DEFAULT_GRADLE_IMAGE": {value -> value},
            "CNP_DEFAULT_TERRAFORM_IMAGE": {value -> value},
            "CNP_DEFAULT_DOJO_MSG_IMAGE": {value -> value},
            "CNP_DOJO_MSG_ENDPOINT": {value -> value},
            "CNP_IMAGE_REPO": {value -> value},
            "CNP_LOG_LEVEL": {value -> value},
            "CNP_IGNORE_CONFTEST_ANALYSIS_FAILURE": {value -> Boolean.valueOf(value as String)},
            "CNP_XLR_TEMPLATE_URL": {value -> value},
            "CNP_CALLBACK_JOB": {value -> value},
            "CNP_DISABLE_JIRASCAN": {value -> Boolean.valueOf(value as String)},
            "CNP_CANDIDATE_JOB": {value -> value},
            "CNP_OVERRIDE_COMMON" : {value -> Boolean.valueOf(value as String)}, // disables common preflight checks
            "CNP_DEPLOYABLE_BRANCHES": {value -> value},
            "CNP_RELEASE_INFO_FILE_NAME": {value -> value},
            "CNP_POD_IDLE_MINUTES": {value -> value},
            "CNP_POD_NODE_SELECTOR": {value -> value},
            "CNP_POD_WORKING_DIR": {value -> value},
            "CNP_POD_COMMAND": {value -> value},
            "CNP_POD_COMMAND_ARGS": {value -> value},
            "CNP_POD_SERVICE_ACCOUNT": {value -> value},
            "CNP_POD_CLOUD": {value -> value},
            "CNP_POD_JNLP_IMAGE": {value -> value},
            "CNP_JENKINS_DOLLAR_HACK_ENABLED": {value -> Boolean.valueOf(value as String)},
            "CNP_ENABLE_EVENTS"     : { value -> Boolean.valueOf(value as String) }, // if disabled events are removed
            "CNP_ENABLE_RESOURCE_REQUESTS"     : { value -> Boolean.valueOf(value as String) },
            "CNP_AWS_IDP_PROVIDER"     : { value -> value },
            "CNP_RELEASE_STASH_PATTERN":{value -> value}
    ]

}
