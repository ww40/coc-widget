package dev.ww40.buildertimer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class QuietHoursTest {

    private val zone = ZoneId.of("Europe/London")
    private fun epoch(text: String) = LocalDateTime.parse(text).atZone(zone).toEpochSecond()

    @Test
    fun `window wrapping past midnight covers late night and early morning`() {
        val overnight = QuietHours(23, 7)
        assertTrue(overnight.contains(23))
        assertTrue(overnight.contains(3))
        assertFalse(overnight.contains(7))
        assertFalse(overnight.contains(12))
    }

    @Test
    fun `same-day window and empty window`() {
        assertTrue(QuietHours(9, 17).contains(9))
        assertFalse(QuietHours(9, 17).contains(17))
        assertFalse(QuietHours(8, 8).contains(8))
    }

    @Test
    fun `finishes outside the window are delivered on time`() {
        val finish = epoch("2026-11-02T14:30:00")
        assertEquals(finish, QuietHours(23, 7).deliveryEpochSec(finish, zone))
    }

    @Test
    fun `late-night finish waits for the next morning`() {
        val finish = epoch("2026-11-02T23:45:00")
        assertEquals(epoch("2026-11-03T07:00:00"), QuietHours(23, 7).deliveryEpochSec(finish, zone))
    }

    @Test
    fun `early-morning finish waits until the window ends the same day`() {
        val finish = epoch("2026-11-03T03:10:00")
        assertEquals(epoch("2026-11-03T07:00:00"), QuietHours(23, 7).deliveryEpochSec(finish, zone))
    }

    @Test
    fun `rejects hours outside 0 to 23`() {
        assertThrows(IllegalArgumentException::class.java) { QuietHours(24, 7) }
        assertThrows(IllegalArgumentException::class.java) { QuietHours(23, -1) }
    }
}
