package com.cigna.common.utils

import com.cloudbees.groovy.cps.NonCPS
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormatter
import org.joda.time.format.DateTimeFormat

/**
 * This is an object used to keep start/end-time actual/planned for tickets opened within the pipeline.
 */
class TicketTimer implements Serializable {

    static final protected Integer START_TIME_SECONDS = 5
    protected String timePattern
    protected DateTime now
    protected DateTimeZone tz
    protected DateTimeZone convertTo
    DateTime startTimeActual
    DateTime startTimePlanned
    DateTime endTimeActual
    DateTime endTimePlanned

    @NonCPS
    void setStartTimePlanned (String timeString) {
        DateTimeFormatter fmt = DateTimeFormat.forPattern('yyyy-MM-dd HH:mm:ss')
        if (convertTo) {
            this.startTimePlanned = fmt.parseDateTime(timeString).withZone(convertTo)
        } else {
            this.startTimePlanned = fmt.parseDateTime(timeString)
        }
    }

    @NonCPS
    void setStartTimePlanned(DateTime dt) {
        if (convertTo) {
            this.startTimePlanned = dt.withZone(convertTo)
        } else {
            this.startTimePlanned = dt
        }
    }

    @NonCPS
    void setEndTimePlanned (String timeString) {
        DateTimeFormatter fmt = DateTimeFormat.forPattern('yyyy-MM-dd HH:mm:ss')
        if (convertTo) {
            this.endTimePlanned = fmt.parseDateTime(timeString).withZone(convertTo)
        } else {
            this.endTimePlanned = fmt.parseDateTime(timeString)
        }
    }

    @NonCPS
    void setEndTimePlanned(DateTime dt) {
        if (convertTo) {
            this.endTimePlanned = dt.withZone(convertTo)
        } else {
            this.endTimePlanned = dt
        }
    }

    @NonCPS
    Object getStartTimePlanned() {
        Object result
        if (startTimePlanned) {
            result = this.startTimePlanned.toString(timePattern)
        } else {
            result = null
        }
        result
    }

    @NonCPS
    Object getEndTimePlanned() {
        Object result
        if (endTimePlanned) {
            result = this.endTimePlanned.toString(timePattern)
        } else {
            result = null
        }
        result
    }

    @NonCPS
    Object getEndTimeActual() {
        Object result
        if (endTimeActual) {
            result = this.endTimeActual.toString(timePattern)
        } else {
            result = null
        }
        result
    }

    @NonCPS
    Object getStartTimeActual() {
        Object result
        if (startTimeActual) {
            result = this.startTimeActual.toString(timePattern)
        } else {
            result = null
        }
        result
    }

    TicketTimer(TimeZone timeZone = null, String timePattern = null, DateTime convertTo = null) {
        tz = timeZone ?: DateTimeZone.forID('US/Eastern')
        this.timePattern = timePattern ?: "yyyy-MM-dd'T'HH:mm:ssZZ"
        this.convertTo = convertTo ?: null
    }

    String rightNow(DateTime nowIn = null) {
        now = nowIn ?: new DateTime(tz)
    }

    void startTime(Integer plannedDuration = START_TIME_SECONDS, DateTime nowIn = null) {
        rightNow(nowIn)
        if (convertTo) {
            startTimePlanned = now.withZone(convertTo)
            startTimeActual = now.plusSeconds(START_TIME_SECONDS).withZone(convertTo)
            endTimePlanned = now.plusMinutes(plannedDuration).withZone(convertTo)
        } else {
            startTimePlanned = now
            startTimeActual = now.plusSeconds(START_TIME_SECONDS)
            endTimePlanned = now.plusMinutes(plannedDuration)
        }
    }

    void stopTime(DateTime nowIn = null) {
        rightNow(nowIn)
        if (convertTo) {
            endTimeActual = now.withZone(convertTo)
        } else {
            endTimeActual = now
        }
    }
}
