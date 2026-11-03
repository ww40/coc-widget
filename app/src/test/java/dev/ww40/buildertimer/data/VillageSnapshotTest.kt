package dev.ww40.buildertimer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VillageSnapshotTest {

    private fun upgrade(name: String, finishesAt: Long, isGoblin: Boolean = false) =
        Upgrade(dataId = 1, name = name, targetLevel = 2, finishesAtEpochSec = finishesAt, totalSeconds = 100, isGoblin = isGoblin)

    private val snapshot = VillageSnapshot(
        tag = "#A",
        townHallLevel = 18,
        exportedAtEpochSec = 0,
        builderCount = 3,
        builderUpgrades = listOf(upgrade("Cannon", 100), upgrade("Mortar", 300), upgrade("Goblin Tower", 200, isGoblin = true)),
        labUpgrades = listOf(upgrade("Barbarian", 150)),
        petUpgrades = emptyList(),
    )

    @Test
    fun `counts busy and free builders as upgrades finish, ignoring the goblin`() {
        assertEquals(2, snapshot.busyBuilders(0))
        assertEquals(1, snapshot.freeBuilders(0))
        assertEquals(1, snapshot.busyBuilders(100))
        assertEquals(2, snapshot.freeBuilders(100))
        assertEquals(3, snapshot.freeBuilders(1_000))
    }

    @Test
    fun `goblin counts as active only until it finishes`() {
        assertTrue(snapshot.hasActiveGoblin(199))
        assertFalse(snapshot.hasActiveGoblin(200))
    }

    @Test
    fun `next upgrade is the soonest unfinished across all sections`() {
        assertEquals("Cannon", snapshot.nextUpgrade(0)?.name)
        assertEquals("Barbarian", snapshot.nextUpgrade(100)?.name)
        assertEquals("Goblin Tower", snapshot.nextUpgrade(150)?.name)
        assertNull(snapshot.nextUpgrade(300))
    }

    @Test
    fun `flags the widget footer once the last sync is over a day old`() {
        val idle = snapshot.copy(builderUpgrades = emptyList(), labUpgrades = emptyList())
        val day = VillageSnapshot.WIDGET_STALE_AFTER_SECONDS
        assertFalse(idle.isWidgetStale(day))
        assertTrue(idle.isWidgetStale(day + 1))
    }

    @Test
    fun `flags the widget footer once an upgrade has finished since the last sync`() {
        assertFalse(snapshot.isWidgetStale(99))
        assertTrue(snapshot.isWidgetStale(100))
    }

    @Test
    fun `counts down to each helper being ready and stops at zero`() {
        val waiting = snapshot.copy(apprenticeReadyAtEpochSec = 500, labAssistantReadyAtEpochSec = 200)
        assertEquals(400L, waiting.apprenticeCooldownRemaining(100))
        assertEquals(100L, waiting.labAssistantCooldownRemaining(100))
        assertEquals(0L, waiting.apprenticeCooldownRemaining(500))
        assertEquals(0L, waiting.labAssistantCooldownRemaining(900))
    }

    @Test
    fun `free count never goes negative when the export lists more upgrades than huts`() {
        assertEquals(0, snapshot.copy(builderCount = 1).freeBuilders(0))
    }
}
