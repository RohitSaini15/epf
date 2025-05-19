package com.cigna.base

import com.splunk.splunkjenkins.utils.LogEventHelper
import hudson.EnvVars

/**
 * This class is meant to hold static methods that primarily interact with Jenkins.
 * These methods can be used from multiple pipeline entry points.
 */
class JenkinsIO {

    /**
     * Method initializes a splunkins build object.  The build object has three methods from the LogEventHelper
     * class called on it which returns environment variables, metadata, and causes.  After that, a Map object is
     * created containing all of the information listed above along with the following:  (1) an event tag used
     * to identify the logs in Splunk, (2) a Jenkins environment variables used for alerting in Splunk, (3) additonal
     * fields added by the user (none if left blank).
     *
     * @return a Map object containing all of the information to send to Splunk via splunkins.send(splunkEvent).
     */
    static Map<String, Object> initializeSplunkins(def script) {
        // technically its a "hudson.model.Run" object but there isn't an easy way to test a Run object
        Object splunkBuild = script.splunkins.build

        EnvVars splunkBuildEnv = LogEventHelper.getEnvironment(splunkBuild)
        Map<String, Object> splunkMetadata = LogEventHelper.getBuildVariables(splunkBuild)
        String splunkCauses = LogEventHelper.getBuildCauses(splunkBuild)
        List<Object> scmData = script.scm.userRemoteConfigs

        [
            'event_tag': 'epf_event',          // unique tag used in Splunk
            'build_url': script.env.BUILD_URL,
            'build_env': splunkBuildEnv,
            'metadata' : splunkMetadata,
            'causes'   : splunkCauses,
            'scm_data' : scmData,
            'phases'   : [],
            'outcome_message': ''
        ]
    }

    /**
     * Set properties configured by the user via the top-level configuration. These include log history age
     * and count, and the Gitlab connection property which allows Jenkins to report build status to
     * Gitlab.
     *
     * @param config The pipeline-level configuration map
     */
    static void configureProperties(def script, Map<String, Object> config) {
        boolean customProperties = config?.customProperties ?: false
        List<Object> additionalProperties = config?.additionalProperties ?: []
        
        if (customProperties && additionalProperties) {
            throw new UnsupportedOperationException(
                'additionalProperties and customProperties options are mutually exclusive'
            )
        }

        // all this work only matters if customProperties is false
        if (!customProperties) {

            String logHistoryAge = config?.logHistoryAge ?: '14'
            String logHistoryCount = config?.logHistoryCount ?: '5'

            List<Object> mergedProperties = [
                script.buildDiscarder(
                    script.logRotator(
                        artifactDaysToKeepStr: logHistoryAge,
                        artifactNumToKeepStr: logHistoryCount,
                        daysToKeepStr: logHistoryAge,
                        numToKeepStr: logHistoryCount
                    )
                ),
            ] + additionalProperties

            if (config?.gitlabConnectionName) {
                String gitLabCredentials = config?.gitLabCredentials ?: 'GitlabJenkins'
                mergedProperties.add([
                    $class                  : 'GitLabConnectionProperty',
                    gitLabConnection        : "${config.gitlabConnectionName}",
                    jobCredentialId         : "${gitLabCredentials}",
                    useAlternativeCredential: true,
                ])
            }

            script.properties(mergedProperties)
        }
    }
    
}
