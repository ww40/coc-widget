package dev.ww40.buildertimer.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {

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
}
