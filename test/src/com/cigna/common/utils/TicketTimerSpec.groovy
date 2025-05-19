import com.cigna.common.utils.TicketTimer
import spock.lang.Specification
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormatter
import org.joda.time.format.DateTimeFormat

class TicketTimerSpec extends Specification {

    protected TicketTimer ticketTimer
    protected DateTimeZone tz = DateTimeZone.forID('US/Eastern')
    protected String timePattern = "yyyy-MM-dd'T'HH:mm:ssZZ"

    def setup() {
        ticketTimer = new TicketTimer()
    }

    def 'startTime'() {
        when:
            DateTime now = new DateTime(tz)
            ticketTimer.startTime(5, now)

        then:
            assert ticketTimer.startTimePlanned == now.toString(timePattern)
            assert ticketTimer.startTimeActual == now.plusSeconds(5).toString(timePattern)
            assert ticketTimer.endTimePlanned == now.plusMinutes(5).toString(timePattern)
    }

    def 'convertTime'() {
        when:
            def convertTz = DateTimeZone.forID('Etc/GMT')
            DateTime now = new DateTime(convertTz)
            ticketTimer = new TicketTimer(convertTo: convertTz)
            ticketTimer.startTime(5, now)
            ticketTimer.stopTime(now)

        then:
            assert ticketTimer.startTimePlanned == now.toString(timePattern)
            assert ticketTimer.startTimeActual == now.plusSeconds(5).toString(timePattern)
            assert ticketTimer.endTimePlanned == now.plusMinutes(5).toString(timePattern)
            assert ticketTimer.endTimeActual == now.toString(timePattern)
    }

    def 'formatDate'() {
        when:
            DateTime now = new DateTime(tz)
            def convertTz = DateTimeZone.forID('Etc/GMT')
            ticketTimer = new TicketTimer(timePattern: 'yyyy-MM-dd HH:mm:ss', convertTo: convertTz)
            ticketTimer.startTime(5, now)
            ticketTimer.stopTime(now)
        
        then:
            assert ticketTimer.startTimePlanned == now.withZone(convertTz).toString('yyyy-MM-dd HH:mm:ss')
            assert ticketTimer.endTimePlanned == now.plusMinutes(5).withZone(convertTz).toString('yyyy-MM-dd HH:mm:ss')
    }

    def 'stopTime'() {
        when:
            DateTime now = new DateTime(tz)
            ticketTimer.stopTime(now)

        then:
            assert ticketTimer.endTimeActual == now.toString(timePattern)
    }
}