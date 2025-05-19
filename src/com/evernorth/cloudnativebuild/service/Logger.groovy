package com.evernorth.cloudnativebuild.service

import com.cloudbees.groovy.cps.NonCPS
import com.evernorth.cloudnativebuild.data.PipelineConstants
import com.evernorth.cloudnativebuild.model.logging.LogLevel

/**
 * Wraps logging functionality and controls via log level
 */
class Logger {
    def scriptContext
    LogLevel logLevelConfigured = LogLevel.INFO

    Logger(def scriptContext) {
        this.scriptContext = scriptContext
        this.logLevelConfigured = scriptContext.env.CNP_LOG_LEVEL ? LogLevel.valueOf(scriptContext.env.CNP_LOG_LEVEL) : LogLevel.INFO
    }

    void log(Object message, LogLevel level = LogLevel.INFO) {
        writeMessage("$message", level)
    }

    void logError(Object message, Exception ex, LogLevel level = LogLevel.ERROR) {
        writeMessage(CreateLogMessageFromError(message, ex, level), level)
    }

    void writeMessage(String message, LogLevel level) {
        if (level >= logLevelConfigured) {
            scriptContext.echo "$message"
        }
    }

    void logShellCommand(String command, LogLevel level){
        if (level >= logLevelConfigured) {
            scriptContext.sh command
        }
    }

    @NonCPS
    static String CreateLogMessageFromError(Object message, Exception error, LogLevel level) {
        def levelStr = ( level == LogLevel.CRITICAL ) ? 'Critical ' : ''
        "${System.getProperty('line.separator')}$message${System.getProperty('line.separator')}" +
            "${PipelineConstants.ERROR_SEP}${System.getProperty('line.separator')}" +
            "${levelStr}ERROR: ${System.getProperty('line.separator')}" +
            "Message: ${error?.message}${System.getProperty('line.separator')}"
    }
}
