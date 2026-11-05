package dev.ww40.buildertimer.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class FinishedLabelTest {

    @Test
    fun `a regular finished upgrade says what came free`() {
        assertEquals("✓ Builder free", finishedLabel("Builder free", isGoblin = false))
        assertEquals("✓ Lab free", finishedLabel("Lab free", isGoblin = false))
    }

    @Test
    fun `a finished goblin upgrade never claims anything came free`() {
        assertEquals("✓ Finished", finishedLabel("Builder free", isGoblin = true))
        assertEquals("✓ Finished", finishedLabel("Lab free", isGoblin = true))
    }
}
