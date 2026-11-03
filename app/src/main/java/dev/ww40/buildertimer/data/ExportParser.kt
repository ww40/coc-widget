package dev.ww40.buildertimer.data

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class ExportFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Parses the JSON from Clash of Clans' Settings → More Settings → Data Export. */
class ExportParser(private val gameData: GameData) {

    fun parse(text: String): VillageSnapshot {
        val root = parseRoot(text.trim())
        val exportedAt = root.long("timestamp")
            ?: throw ExportFormatException("Export has no timestamp")
        val tag = root["tag"]?.jsonPrimitive?.content
            ?: throw ExportFormatException("Export has no player tag")

        val buildings = root.entries("buildings")
        val boosts = root["boosts"] as? JsonObject
        val builderBoost = boosts?.long("builder_boost")?.coerceAtLeast(0) ?: 0
        val labBoost = boosts?.long("lab_boost")?.coerceAtLeast(0) ?: 0
        val petBoost = boosts?.long("pet_boost")?.coerceAtLeast(0) ?: 0
        val clockTowerBoost = boosts?.long("clocktower_boost")?.coerceAtLeast(0) ?: 0
        val townHallLevel = buildings.firstOrNull { it.int("data") == TOWN_HALL_ID }?.int("lvl")
        val builderCount = builderCount(buildings)
        val helpers = root.entries("helpers")
        val apprentice = helpers.firstOrNull { it.int("data") == BUILDERS_APPRENTICE_ID }
        val labAssistant = helpers.firstOrNull { it.int("data") == LAB_ASSISTANT_ID }
        val maxItems = MAX_SECTIONS.mapValues { (_, sections) -> sections.flatMap { root.entries(it) }.mapNotNull(::maxItem) }
        val maxCalculator = TimeToMaxCalculator(gameData, builderCount, townHallLevel)
        return VillageSnapshot(
            tag = tag,
            townHallLevel = townHallLevel,
            exportedAtEpochSec = exportedAt,
            builderCount = builderCount,
            builderUpgrades = upgrades(root, BUILDER_SECTIONS, exportedAt, builderBoost, BUILDER_BOOST_SPEEDUP, apprentice.level())
                .sortedBy { it.finishesAtEpochSec },
            labUpgrades = upgrades(root, LAB_SECTIONS, exportedAt, labBoost, LAB_BOOST_SPEEDUP, labAssistant.level())
                .sortedBy { it.finishesAtEpochSec },
            petUpgrades = upgrades(root, PET_SECTIONS, exportedAt, petBoost, PET_BOOST_SPEEDUP, helperLevel = 0)
                .sortedBy { it.finishesAtEpochSec },
            builderBaseUpgrades = upgrades(root, BUILDER_BASE_SECTIONS, exportedAt, clockTowerBoost, CLOCK_TOWER_SPEEDUP, helperLevel = 0)
                .sortedBy { it.finishesAtEpochSec },
            starLabUpgrades = upgrades(root, STAR_LAB_SECTIONS, exportedAt, clockTowerBoost, CLOCK_TOWER_SPEEDUP, helperLevel = 0)
                .sortedBy { it.finishesAtEpochSec },
            builderBaseMaxed = allMaxed((BUILDER_BASE_SECTIONS + STAR_LAB_SECTIONS).flatMap { root.entries(it) }),
            builderBoostEndsAtEpochSec = (exportedAt + builderBoost).takeIf { builderBoost > 0 },
            labBoostEndsAtEpochSec = (exportedAt + labBoost).takeIf { labBoost > 0 },
            petBoostEndsAtEpochSec = (exportedAt + petBoost).takeIf { petBoost > 0 },
            clockTowerBoostEndsAtEpochSec = (exportedAt + clockTowerBoost).takeIf { clockTowerBoost > 0 },
            apprenticeReadyAtEpochSec = apprentice?.readyAt(exportedAt),
            labAssistantReadyAtEpochSec = labAssistant?.readyAt(exportedAt),
            timeToMax = MaxTarget.entries.associateWith { maxCalculator.estimates(maxItems, it) },
        )
    }

    private fun parseRoot(text: String): JsonObject {
        if (text.isEmpty()) throw ExportFormatException("Clipboard is empty")
        val element = try {
            Json.parseToJsonElement(text)
        } catch (e: SerializationException) {
            throw ExportFormatException("Not valid JSON", e)
        }
        return element as? JsonObject ?: throw ExportFormatException("Export is not a JSON object")
    }

    private fun upgrades(
        root: JsonObject,
        sections: List<String>,
        exportedAt: Long,
        boostSeconds: Long,
        speedup: Long,
        helperLevel: Int,
    ): List<Upgrade> =
        sections.flatMap { root.entries(it) }.mapNotNull { entry ->
            val timer = entry.long("timer")?.takeIf { it > 0 } ?: return@mapNotNull null
            val dataId = entry.int("data") ?: return@mapNotNull null
            val targetLevel = (entry.int("lvl") ?: 0) + 1
            val helperSeconds = entry.long("helper_timer")?.coerceAtLeast(0) ?: 0
            Upgrade(
                dataId = dataId,
                name = gameData.name(dataId),
                targetLevel = targetLevel,
                finishesAtEpochSec = exportedAt + realSeconds(timer, boostSeconds, speedup, helperSeconds, helperLevel),
                totalSeconds = gameData.upgradeSeconds(dataId, targetLevel),
                isGoblin = entry.bool("extra"),
                hasHelperAssigned = helperSeconds > 0 || entry.bool("helper_recurrent"),
            )
        }

    /**
     * Export timers ignore active speed-ups. A potion makes work go [speedup]x faster for
     * [boostSeconds] of real time, and a helper's shift adds [helperLevel] to the rate for
     * [helperSeconds], since potions don't affect helpers. Later "keep assigned" shifts aren't counted.
     */
    private fun realSeconds(timer: Long, boostSeconds: Long, speedup: Long, helperSeconds: Long, helperLevel: Int): Long {
        var workLeft = timer
        var elapsed = 0L
        for (rateChange in listOf(boostSeconds, helperSeconds).filter { it > 0 }.sorted()) {
            if (rateChange == elapsed) continue
            val rate = (if (elapsed < boostSeconds) speedup else 1) + (if (elapsed < helperSeconds) helperLevel else 0)
            val phaseWork = (rateChange - elapsed) * rate
            if (workLeft <= phaseWork) return elapsed + (workLeft + rate - 1) / rate
            workLeft -= phaseWork
            elapsed = rateChange
        }
        return elapsed + workLeft
    }

    /** Items with no levels in the game data, such as Builder Base army camps, can't hold this back. */
    private fun allMaxed(entries: List<JsonObject>): Boolean = entries.isNotEmpty() && entries.all { entry ->
        if ((entry.long("timer") ?: 0) > 0) return@all false
        val topLevel = entry.int("data")?.let(gameData::topLevel) ?: return@all true
        (entry.int("lvl") ?: 0) >= topLevel
    }

    private fun maxItem(entry: JsonObject): MaxItem? {
        val dataId = entry.int("data") ?: return null
        val level = entry.int("lvl") ?: return null
        return MaxItem(dataId, level, entry.long("timer")?.takeIf { it > 0 }, entry.int("cnt") ?: 1)
    }

    private fun JsonObject?.level(): Int = this?.int("lvl") ?: 0

    /** A helper with no cooldown is ready now. */
    private fun JsonObject.readyAt(exportedAt: Long): Long = exportedAt + (long("helper_cooldown")?.coerceAtLeast(0) ?: 0)

    private fun builderCount(buildings: List<JsonObject>): Int =
        buildings.filter { it.int("data") in BUILDER_HOME_IDS }.sumOf { it.int("cnt") ?: 1 }

    private fun JsonObject.entries(key: String): List<JsonObject> =
        (this[key] as? JsonArray)?.filterIsInstance<JsonObject>().orEmpty()

    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull
    private fun JsonObject.long(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull
    private fun JsonObject.bool(key: String): Boolean = this[key]?.jsonPrimitive?.booleanOrNull ?: false

    companion object {
        const val TOWN_HALL_ID = 1000001
        const val BUILDERS_HUT_ID = 1000015
        const val BOB_HUT_ID = 1000064
        private val BUILDER_HOME_IDS = setOf(BUILDERS_HUT_ID, BOB_HUT_ID)
        private const val BUILDERS_APPRENTICE_ID = 93000000
        private const val LAB_ASSISTANT_ID = 93000001
        const val BUILDER_BOOST_SPEEDUP = 10L
        const val LAB_BOOST_SPEEDUP = 24L
        const val PET_BOOST_SPEEDUP = 24L
        const val CLOCK_TOWER_SPEEDUP = 10L

        private val BUILDER_SECTIONS = listOf("buildings", "traps", "heroes", "guardians")
        private val LAB_SECTIONS = listOf("units", "spells", "siege_machines")
        private val PET_SECTIONS = listOf("pets")
        private val BUILDER_BASE_SECTIONS = listOf("buildings2", "traps2", "heroes2")
        private val STAR_LAB_SECTIONS = listOf("units2")
        private val MAX_SECTIONS = mapOf(
            MaxCategory.HEROES to listOf("heroes"),
            MaxCategory.GUARDIANS to listOf("guardians"),
            MaxCategory.BUILDINGS to listOf("buildings", "traps"),
            MaxCategory.PETS to PET_SECTIONS,
            MaxCategory.LAB to LAB_SECTIONS,
        )
    }
}
