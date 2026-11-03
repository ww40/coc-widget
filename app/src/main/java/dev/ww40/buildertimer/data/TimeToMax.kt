package dev.ww40.buildertimer.data

/** What counts as maxed: every level open at the player's Town Hall, or the game's highest levels. */
enum class MaxTarget { TOWN_HALL, GAME }

enum class MaxCategory(val label: String, val usesBuilders: Boolean) {
    HEROES("Heroes", usesBuilders = true),
    GUARDIANS("Guardians", usesBuilders = true),
    BUILDINGS("Buildings", usesBuilders = true),
    PETS("Pets", usesBuilders = false),
    LAB("Lab", usesBuilders = false),
}

data class MaxEstimate(val category: MaxCategory, val seconds: Long) {
    val isMaxed: Boolean get() = seconds <= 0
}

/** One export entry: [count] copies at [level], with [timer] seconds left when one is upgrading. */
data class MaxItem(val dataId: Int, val level: Int, val timer: Long?, val count: Int)

/** Estimates time to max per category. Categories with no items in the export are left out. */
class TimeToMaxCalculator(
    private val gameData: GameData,
    private val builderCount: Int,
    private val townHallLevel: Int?,
) {

    fun estimates(items: Map<MaxCategory, List<MaxItem>>, target: MaxTarget): List<MaxEstimate> {
        if (target == MaxTarget.TOWN_HALL && townHallLevel == null) return emptyList()
        return MaxCategory.entries.mapNotNull { category ->
            val categoryItems = items[category].orEmpty()
            if (categoryItems.isEmpty()) return@mapNotNull null
            MaxEstimate(category, categorySeconds(category, categoryItems, target))
        }
    }

    /** Builders share work but can't split one item's levels; the lab and Pet House do one at a time. */
    private fun categorySeconds(category: MaxCategory, items: List<MaxItem>, target: MaxTarget): Long {
        val total = items.sumOf { itemSeconds(it, target) * it.count }
        if (!category.usesBuilders) return total
        val builders = builderCount.coerceAtLeast(1)
        val sharedOut = (total + builders - 1) / builders
        return maxOf(sharedOut, items.maxOf { itemSeconds(it, target) })
    }

    private fun itemSeconds(item: MaxItem, target: MaxTarget): Long {
        val inProgress = item.timer ?: 0
        // Each Town Hall level requires the one before, so it would otherwise always have a next level.
        if (target == MaxTarget.TOWN_HALL && item.dataId == ExportParser.TOWN_HALL_ID) return inProgress
        val limit = townHallLevel.takeIf { target == MaxTarget.TOWN_HALL }
        val firstLevelLeft = item.level + if (item.timer != null) 2 else 1
        return inProgress + gameData.upgradeSecondsByLevel(item.dataId, limit)
            .filterKeys { it >= firstLevelLeft }.values.sum()
    }
}
