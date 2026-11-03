package dev.ww40.buildertimer.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class TimeFormatTest {

    @Test
    fun `formats estimates coarsely`() {
        assertEquals("344d", formatEstimate(344 * 86_400L + 7_200))
        assertEquals("1d", formatEstimate(86_400))
        assertEquals("23h", formatEstimate(86_399))
        assertEquals("1h", formatEstimate(3_600))
        assertEquals("<1h", formatEstimate(3_599))
    }

    @Test
    fun `formats remaining time at minute precision`() {
        assertEquals("Done", formatRemaining(0))
        assertEquals("Done", formatRemaining(-5))
        assertEquals("<1m", formatRemaining(59))
        assertEquals("1m", formatRemaining(60))
        assertEquals("2h 8m", formatRemaining(2 * 3600 + 8 * 60 + 18))
        assertEquals("1d 0h 0m", formatRemaining(86_400))
        assertEquals("2d 5h 54m", formatRemaining(2 * 86_400 + 5 * 3600 + 54 * 60 + 47))
    }

    @Test
    fun `formats sync age coarsely`() {
        assertEquals("just now", formatAgo(-3))
        assertEquals("just now", formatAgo(59))
        assertEquals("2m ago", formatAgo(150))
        assertEquals("3h ago", formatAgo(3 * 3600 + 10))
        assertEquals("1d ago", formatAgo(86_400))
    }

    @Test
    fun `formats finish time relative to today`() {
        val zone = ZoneId.of("Europe/London")
        fun epoch(text: String) = LocalDateTime.parse(text).atZone(zone).toEpochSecond()
        val now = epoch("2026-11-02T10:00:00") // a Monday
        fun format(text: String, use24Hour: Boolean = true) =
            formatFinishTime(epoch(text), now, zone, use24Hour, Locale.UK)

        assertEquals("14:30", format("2026-11-02T14:30:00"))
        assertEquals("Tue 09:05", format("2026-11-03T09:05:00"))
        assertEquals("Sun 23:00", format("2026-11-08T23:00:00"))
        assertEquals("9 Nov 08:00", format("2026-11-09T08:00:00"))
        assertEquals("2:30 pm", format("2026-11-02T14:30:00", use24Hour = false))
    }
}
