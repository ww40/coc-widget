package dev.ww40.buildertimer.notify

import dev.ww40.buildertimer.data.Settings
import dev.ww40.buildertimer.data.Upgrade
import dev.ww40.buildertimer.data.VillageSnapshot
import java.time.ZoneId

data class PlannedAlert(val requestCode: Int, val title: String, val text: String, val atEpochSec: Long)

private const val UPGRADE_FINISHED_TEXT = "Open Clash of Clans to start the next one."

/** Boost alarms start here so their request codes can't clash with upgrades, which use their index. */
private const val FIRST_BOOST_REQUEST_CODE = 100_000

private class BoostAlert(val name: String, val endedText: String, val endsAtEpochSec: Long?)

private fun VillageSnapshot.boostAlerts() = listOf(
    BoostAlert("Builder Potion", "Your builders are back to normal speed.", builderBoostEndsAtEpochSec),
    BoostAlert("Research Potion", "Your lab is back to normal speed.", labBoostEndsAtEpochSec),
    BoostAlert("Pet Potion", "Your Pet House is back to normal speed.", petBoostEndsAtEpochSec),
    BoostAlert("Clock Tower boost", "Your Builder Base is back to normal speed.", clockTowerBoostEndsAtEpochSec),
    BoostAlert("Builder Bite", "Its speed-up on your builders has ended.", builderBiteEndsAtEpochSec),
    BoostAlert("Study Soup", "Its speed-up on your lab has ended.", studySoupEndsAtEpochSec),
)

/** Every request code [plannedAlerts] could use for [snapshot], whichever alerts are switched on. */
fun alertRequestCodes(snapshot: VillageSnapshot): List<Int> =
    snapshot.allUpgrades.indices + snapshot.boostAlerts().indices.map { FIRST_BOOST_REQUEST_CODE + it }

/** The future alerts [settings] allows, held until the end of quiet hours where needed. */
fun plannedAlerts(snapshot: VillageSnapshot, settings: Settings, nowEpochSec: Long, zone: ZoneId): List<PlannedAlert> {
    val quietHours = settings.quietHours
    fun deliveryTime(finishEpochSec: Long) = quietHours?.deliveryEpochSec(finishEpochSec, zone) ?: finishEpochSec

    // Same order as VillageSnapshot.allUpgrades, so each upgrade keeps its request code.
    val upgradeGroups: List<Pair<List<Upgrade>, Boolean>> = listOf(
        snapshot.builderUpgrades to settings.notifyBuilders,
        snapshot.labUpgrades to settings.notifyLab,
        snapshot.petUpgrades to settings.notifyPets,
        snapshot.builderBaseUpgrades to settings.notifyBuilderBase,
        snapshot.starLabUpgrades to settings.notifyBuilderBase,
    )
    var requestCode = 0
    val upgradeAlerts = upgradeGroups.flatMap { (upgrades, enabled) ->
        upgrades.mapNotNull { upgrade ->
            val code = requestCode++
            if (!enabled || upgrade.finishesAtEpochSec <= nowEpochSec) return@mapNotNull null
            PlannedAlert(
                code,
                "${upgrade.name} → ${upgrade.targetLevel} finished",
                UPGRADE_FINISHED_TEXT,
                deliveryTime(upgrade.finishesAtEpochSec),
            )
        }
    }
    if (!settings.notifyBoostEnded) return upgradeAlerts
    val boostAlerts = snapshot.boostAlerts().mapIndexedNotNull { index, boost ->
        val endsAt = boost.endsAtEpochSec?.takeIf { it > nowEpochSec } ?: return@mapIndexedNotNull null
        PlannedAlert(
            FIRST_BOOST_REQUEST_CODE + index,
            "${boost.name} finished",
            boost.endedText,
            deliveryTime(endsAt),
        )
    }
    return upgradeAlerts + boostAlerts
}
