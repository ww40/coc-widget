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
    val builderBaseUpgrades: List<Upgrade> = emptyList(),
    val starLabUpgrades: List<Upgrade> = emptyList(),
    /** True when the Builder Base and Star Lab are at the game's top levels with nothing running. */
    val builderBaseMaxed: Boolean = false,
    /** When an active Builder Potion runs out, or null when none was active at export. */
    val builderBoostEndsAtEpochSec: Long? = null,
    /** When an active Research Potion runs out, or null when none was active at export. */
    val labBoostEndsAtEpochSec: Long? = null,
    /** When an active Pet Potion runs out, or null when none was active at export. */
    val petBoostEndsAtEpochSec: Long? = null,
    /** When an active Clock Tower boost runs out, or null when none was active at export. */
    val clockTowerBoostEndsAtEpochSec: Long? = null,
    /** When the Builder's Apprentice can take a new job, or null when the export doesn't list it. */
    val apprenticeReadyAtEpochSec: Long? = null,
    /** When the Lab Assistant can take a new job, or null when the export doesn't list it. */
    val labAssistantReadyAtEpochSec: Long? = null,
    val timeToMax: Map<MaxTarget, List<MaxEstimate>> = emptyMap(),
) {
    private val homeUpgrades: List<Upgrade> get() = builderUpgrades + labUpgrades + petUpgrades
    val allUpgrades: List<Upgrade> get() = homeUpgrades + builderBaseUpgrades + starLabUpgrades
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

    fun builderBoostRemaining(nowEpochSec: Long): Long = secondsUntil(builderBoostEndsAtEpochSec, nowEpochSec)

    fun labBoostRemaining(nowEpochSec: Long): Long = secondsUntil(labBoostEndsAtEpochSec, nowEpochSec)

    fun petBoostRemaining(nowEpochSec: Long): Long = secondsUntil(petBoostEndsAtEpochSec, nowEpochSec)

    fun clockTowerBoostRemaining(nowEpochSec: Long): Long = secondsUntil(clockTowerBoostEndsAtEpochSec, nowEpochSec)

    fun apprenticeCooldownRemaining(nowEpochSec: Long): Long = secondsUntil(apprenticeReadyAtEpochSec, nowEpochSec)

    fun labAssistantCooldownRemaining(nowEpochSec: Long): Long = secondsUntil(labAssistantReadyAtEpochSec, nowEpochSec)

    private fun secondsUntil(endsAtEpochSec: Long?, nowEpochSec: Long): Long =
        endsAtEpochSec?.let { (it - nowEpochSec).coerceAtLeast(0) } ?: 0

    /** Seconds until the regular lab is free, or 0 when it is; Goblin research runs alongside it. */
    fun labSecondsUntilFree(nowEpochSec: Long): Long =
        labUpgrades.filterNot { it.isGoblin }.maxOfOrNull { it.remainingSeconds(nowEpochSec) } ?: 0

    private fun upgradesShown(showBuilderBase: Boolean): List<Upgrade> = if (showBuilderBase) allUpgrades else homeUpgrades

    /** Soonest unfinished upgrade among those the widgets show. */
    fun nextUpgrade(nowEpochSec: Long, showBuilderBase: Boolean = true): Upgrade? =
        upgradesShown(showBuilderBase).filterNot { it.isDone(nowEpochSec) }.minByOrNull { it.finishesAtEpochSec }

    /** Old exports may predate upgrades started or boosted since, so sync warns before using them. */
    fun isStale(nowEpochSec: Long): Boolean = nowEpochSec - exportedAtEpochSec > STALE_AFTER_SECONDS

    /** The widget flags its footer once the last sync is this old or an upgrade has finished since it. */
    fun isWidgetStale(nowEpochSec: Long, showBuilderBase: Boolean = true): Boolean =
        nowEpochSec - exportedAtEpochSec > WIDGET_STALE_AFTER_SECONDS ||
            upgradesShown(showBuilderBase).any { it.isDone(nowEpochSec) }

    companion object {
        const val STALE_AFTER_SECONDS = 60L * 60
        const val WIDGET_STALE_AFTER_SECONDS = 24L * 60 * 60
    }
}
