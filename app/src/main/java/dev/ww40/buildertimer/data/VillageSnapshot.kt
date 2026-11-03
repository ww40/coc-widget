package dev.ww40.buildertimer.data

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

    fun isDone(nowEpochSec: Long): Boolean = remainingSeconds(nowEpochSec) == 0L

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
    /** When an active Builder Potion runs out, or null when none was active at export. */
    val builderBoostEndsAtEpochSec: Long? = null,
    /** When an active Research Potion runs out, or null when none was active at export. */
    val labBoostEndsAtEpochSec: Long? = null,
) {
    val allUpgrades: List<Upgrade> get() = builderUpgrades + labUpgrades + petUpgrades
    val apprenticeTarget: Upgrade? get() = builderUpgrades.firstOrNull { it.hasHelperAssigned }
    val labAssistantTarget: Upgrade? get() = labUpgrades.firstOrNull { it.hasHelperAssigned }

    /** Widget header, e.g. "Kuswa  TH18"; falls back to the tag when no name is set. */
    fun headerTitle(playerName: String?): String {
        val name = playerName?.trim()?.takeIf { it.isNotEmpty() } ?: tag
        return listOfNotNull(name, townHallLevel?.let { "TH$it" }).joinToString("  ")
    }

    /** Regular builders still working; the Goblin is a temporary extra and never counts. */
    fun busyBuilders(nowEpochSec: Long): Int = builderUpgrades.count { !it.isGoblin && !it.isDone(nowEpochSec) }

    fun freeBuilders(nowEpochSec: Long): Int = (builderCount - busyBuilders(nowEpochSec)).coerceAtLeast(0)

    fun hasActiveGoblin(nowEpochSec: Long): Boolean = builderUpgrades.any { it.isGoblin && !it.isDone(nowEpochSec) }

    fun builderBoostRemaining(nowEpochSec: Long): Long =
        builderBoostEndsAtEpochSec?.let { (it - nowEpochSec).coerceAtLeast(0) } ?: 0

    fun labBoostRemaining(nowEpochSec: Long): Long =
        labBoostEndsAtEpochSec?.let { (it - nowEpochSec).coerceAtLeast(0) } ?: 0

    /** Soonest unfinished upgrade across builders, lab and pets. */
    fun nextUpgrade(nowEpochSec: Long): Upgrade? =
        allUpgrades.filterNot { it.isDone(nowEpochSec) }.minByOrNull { it.finishesAtEpochSec }

    /** Old exports may predate upgrades started or boosted since, so sync warns before using them. */
    fun isStale(nowEpochSec: Long): Boolean = nowEpochSec - exportedAtEpochSec > STALE_AFTER_SECONDS

    /** The widget flags its footer once the last sync is this old. */
    fun isWidgetStale(nowEpochSec: Long): Boolean = nowEpochSec - exportedAtEpochSec > WIDGET_STALE_AFTER_SECONDS

    companion object {
        const val STALE_AFTER_SECONDS = 60L * 60
        const val WIDGET_STALE_AFTER_SECONDS = 24L * 60 * 60
    }
}
