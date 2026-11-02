package dev.ww40.cocwidget.data

data class Upgrade(
    val dataId: Int,
    val name: String,
    val targetLevel: Int,
    val finishesAtEpochSec: Long,
    /** Full duration of this level's upgrade, or null when the game data doesn't know it. */
    val totalSeconds: Long?,
    val isGoblin: Boolean = false,
    val hasHelperAssigned: Boolean = false,
) {
    fun remainingSeconds(nowEpochSec: Long): Long = (finishesAtEpochSec - nowEpochSec).coerceAtLeast(0)

    fun progress(nowEpochSec: Long): Float? {
        val total = totalSeconds?.takeIf { it > 0 } ?: return null
        return (1f - remainingSeconds(nowEpochSec).toFloat() / total).coerceIn(0f, 1f)
    }
}

data class VillageSnapshot(
    val tag: String,
    val townHallLevel: Int?,
    val exportedAtEpochSec: Long,
    val builderCount: Int,
    val builderUpgrades: List<Upgrade>,
    val labUpgrades: List<Upgrade>,
    val petUpgrades: List<Upgrade>,
) {
    val allUpgrades: List<Upgrade> get() = builderUpgrades + labUpgrades + petUpgrades
    val busyBuilders: Int get() = builderUpgrades.count { !it.isGoblin }
    val hasGoblin: Boolean get() = builderUpgrades.any { it.isGoblin }
    val apprenticeTarget: Upgrade? get() = builderUpgrades.firstOrNull { it.hasHelperAssigned }
    val labAssistantTarget: Upgrade? get() = labUpgrades.firstOrNull { it.hasHelperAssigned }
}
