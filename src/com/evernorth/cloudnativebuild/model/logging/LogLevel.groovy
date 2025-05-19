package com.evernorth.cloudnativebuild.model.logging

/**
 * Logging levels
 */
enum LogLevel {
    TRACE(0, 'Fine Grain logging'),
    INFO(1, 'General information outlining overall task progress'),
    WARNING(2, 'Potential error or impending error'),
    ERROR(3, 'Task cannot continue'),
    CRITICAL(4, 'Component, application, and pipeline cannot function')

    int logLevel
    String logDescription

    LogLevel(int logLevel, String logDescription) {
        this.logLevel = logLevel
        this.logDescription = logDescription
    }
}
