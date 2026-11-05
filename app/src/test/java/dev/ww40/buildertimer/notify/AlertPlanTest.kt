package dev.ww40.buildertimer.notify

import dev.ww40.buildertimer.data.QuietHours
import dev.ww40.buildertimer.data.Settings
import dev.ww40.buildertimer.data.Upgrade
import dev.ww40.buildertimer.data.VillageSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class AlertPlanTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val now = LocalDateTime.of(2026, 11, 3, 12, 0).toEpochSecond(ZoneOffset.UTC)

    private fun upgrade(name: String, finishesIn: Long) =
        Upgrade(dataId = 1, name = name, targetLevel = 2, finishesAtEpochSec = now + finishesIn, totalSeconds = 100)

    private val snapshot = VillageSnapshot(
        tag = "#A",
        townHallLevel = 18,
        exportedAtEpochSec = now,
        builderCount = 2,
        builderUpgrades = listOf(upgrade("Cannon", 100), upgrade("Mortar", -5)),
        labUpgrades = listOf(upgrade("Barbarian", 200)),
        petUpgrades = listOf(upgrade("Sneezy", 300)),
        builderBaseUpgrades = listOf(upgrade("Crusher", 400)),
        starLabUpgrades = listOf(upgrade("Boxer Giant", 500)),
        builderBoostEndsAtEpochSec = now + 600,
        clockTowerBoostEndsAtEpochSec = now - 1,
    )

    private fun titles(settings: Settings) = plannedAlerts(snapshot, settings, now, zone).map { it.title }

    @Test
    fun `plans every future upgrade and boost by default, skipping what has already ended`() {
        assertEquals(
            listOf(
                "Cannon → 2 finished",
                "Barbarian → 2 finished",
                "Sneezy → 2 finished",
                "Crusher → 2 finished",
                "Boxer Giant → 2 finished",
                "Builder Potion finished",
            ),
            titles(Settings()),
        )
    }

    @Test
    fun `each switch turns off only its own alerts`() {
        assertEquals(5, titles(Settings(notifyBuilders = false)).size)
        assertTrue("Cannon → 2 finished" !in titles(Settings(notifyBuilders = false)))
        assertTrue("Barbarian → 2 finished" !in titles(Settings(notifyLab = false)))
        assertTrue("Sneezy → 2 finished" !in titles(Settings(notifyPets = false)))
        assertEquals(4, titles(Settings(notifyBuilderBase = false)).size)
        assertTrue("Builder Potion finished" !in titles(Settings(notifyBoostEnded = false)))
        assertEquals(5, titles(Settings(notifyBoostEnded = false)).size)
    }

    @Test
    fun `boost alerts say what is back to normal speed`() {
        val alert = plannedAlerts(snapshot.copy(labBoostEndsAtEpochSec = now + 60), Settings(), now, zone)
            .single { it.title == "Research Potion finished" }
        assertEquals("Your lab is back to normal speed.", alert.text)
    }

    @Test
    fun `a request code stays the same whichever alerts are switched on`() {
        val all = plannedAlerts(snapshot, Settings(), now, zone).associate { it.title to it.requestCode }
        val fewer = plannedAlerts(snapshot, Settings(notifyBuilders = false, notifyLab = false), now, zone)
        fewer.forEach { assertEquals(all[it.title], it.requestCode) }
    }

    @Test
    fun `snack alerts fire when the snack runs out and stay accurate while a potion continues`() {
        val alerts = plannedAlerts(
            snapshot.copy(builderBiteEndsAtEpochSec = now + 60, studySoupEndsAtEpochSec = now + 90),
            Settings(),
            now,
            zone,
        )
        val bite = alerts.single { it.title == "Builder Bite finished" }
        assertEquals(now + 60, bite.atEpochSec)
        assertEquals("Its speed-up on your builders has ended.", bite.text)
        assertEquals(now + 90, alerts.single { it.title == "Study Soup finished" }.atEpochSec)
        assertEquals(alerts.size, alerts.map { it.requestCode }.toSet().size)
    }

    @Test
    fun `snack alerts follow the boost switch`() {
        val alerts = plannedAlerts(
            snapshot.copy(builderBiteEndsAtEpochSec = now + 60),
            Settings(notifyBoostEnded = false),
            now,
            zone,
        )
        assertTrue(alerts.none { it.title == "Builder Bite finished" })
    }

    @Test
    fun `every planned alert can be cancelled and no two share a request code`() {
        val codes = alertRequestCodes(snapshot.copy(labBoostEndsAtEpochSec = now + 60, petBoostEndsAtEpochSec = now + 60))
        assertEquals(codes.size, codes.toSet().size)
        plannedAlerts(snapshot, Settings(), now, zone).forEach { assertTrue(it.requestCode in codes) }
    }

    @Test
    fun `quiet hours hold upgrades and boost alerts until the window ends`() {
        val quiet = QuietHours(11, 13)
        val settings = Settings(quietHoursEnabled = true, quietStartHour = quiet.startHour, quietEndHour = quiet.endHour)
        val end = LocalDateTime.of(2026, 11, 3, 13, 0).toEpochSecond(ZoneOffset.UTC)
        plannedAlerts(snapshot, settings, now, zone).forEach { assertEquals(end, it.atEpochSec) }
    }
}
