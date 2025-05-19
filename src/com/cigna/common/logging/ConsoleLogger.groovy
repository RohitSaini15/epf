package com.cigna.common.logging


import com.cigna.common.phases.PhaseListUtils
import com.cigna.common.utils.FeatureFlags
import groovy.json.JsonOutput
import hudson.Functions

import java.util.logging.Level
import java.util.logging.Logger

/**
 * JSON structured log of: the current job available in the env of the build, the stage given, the message
 * given, the duration of the build as of the time of calling, and the properties passed into
 * cignaBuildFlow initially
 *
 * @param script The script object from cignaBuildFlow's 'this'
 * @param stage The stage the build is currently in when LogLevel.groovy
 * @param message Any sort of message that should be included in the log
 * @param config The configuration passed into cignaBuildFlow initially
 * @param ex Any exception that should be logged, the message and stacktrace will be logged
 */
@SuppressWarnings(['StaticMethodsBeforeInstanceMethods', 'ParameterCount', 'ParameterReassignment'])
static <T> T logJobInfo(Object script, String stage, String message, Map<String, Object> config, Exception ex = null) {
    if (ex) {
        String stackTraceString = Functions.printThrowable(ex)
        script.echo(stackTraceString)
    }

    Map<String, Map> logMap = [:]

    // All the things we want to know about the instance of the pipeline job
    logMap['scm'] = [
        repo     : script.env.GIT_URL,
        gitCommit: script.env.GIT_COMMIT,
    ]
    logMap['orchestrator'] = [
        jobName       : script.env.JOB_NAME,
        buildNumber   : script.env.BUILD_NUMBER,
        branchName    : script.env.BRANCH_NAME,
        node          : script.env.NODE_NAME,
        timeInPipeline: script.currentBuild.durationString,
    ]

    Map configCopy = config.clone() as Map
    if (FeatureFlags.debug) {
        configCopy.phases = PhaseListUtils.sanitizeForJson(configCopy.phases)
    } else {
        configCopy.remove('phases')
    }

    logMap['epf'] = [
        stage             : stage,
        message           : message,
        userProvidedConfig: configCopy,
    ]

    String logJson = JsonOutput.toJson(logMap)
    script.echo(logJson)
}

static void configBasedLog(Object script, Map<String, String> config, String logLevel, String message) {
    String configLogLevel = config?.logLevel ?: 'INFO'
    String identifier = script.env.BUILD_TAG

    Logger logger = Logger.getLogger('org.cigna.epf')
    logger.setLevel(Level.parse(configLogLevel))

    switch (logLevel) {
        case 'SEVERE':
            logger.severe(identifier + ': ' + message)
            break
        case 'WARNING':
            logger.warning(identifier + ': ' + message)
            break
        case 'INFO':
            logger.info(identifier + ': ' + message)
            break
        case 'CONFIG':
            logger.config(identifier + ': ' + message)
            break
        case 'FINE':
            logger.fine(identifier + ': ' + message)
            break
        case 'FINER':
            logger.finer(identifier + ': ' + message)
            break
        case 'FINEST':
            logger.finest(identifier + ': ' + message)
            break
    }
}

@SuppressWarnings(['StaticMethodsBeforeInstanceMethods'])
static void printMetricsUrl(Object script, String namespace) {
    String startTime = script.currentBuild.startTimeInMillis
    String currentTime = System.currentTimeMillis()

    String grafanaUrl = 'https://o-grafana.apps.devops-1-prod.openshift.cignacloud.com'

    script.echo(
        """\
        |Follow the below link to see resource metrics for this pipeline:
        |$grafanaUrl/d/xnyVI_3Mk/namespace-container-metrics?orgId=1&refresh=10s\
        &from=$startTime&to=$currentTime&var-namespace=$namespace
        """.stripMargin().replaceAll(/\s\s/, '')
    )
}
