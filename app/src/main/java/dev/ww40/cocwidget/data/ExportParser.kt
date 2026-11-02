package dev.ww40.cocwidget.data

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
        return VillageSnapshot(
            tag = tag,
            townHallLevel = buildings.firstOrNull { it.int("data") == TOWN_HALL_ID }?.int("lvl"),
            exportedAtEpochSec = exportedAt,
            builderCount = builderCount(buildings),
            builderUpgrades = upgrades(root, BUILDER_SECTIONS, exportedAt)
                .sortedWith(compareByDescending<Upgrade> { it.isGoblin }.thenBy { it.finishesAtEpochSec }),
            labUpgrades = upgrades(root, LAB_SECTIONS, exportedAt).sortedBy { it.finishesAtEpochSec },
            petUpgrades = upgrades(root, PET_SECTIONS, exportedAt).sortedBy { it.finishesAtEpochSec },
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

    private fun upgrades(root: JsonObject, sections: List<String>, exportedAt: Long): List<Upgrade> =
        sections.flatMap { root.entries(it) }.mapNotNull { entry ->
            val timer = entry.long("timer")?.takeIf { it > 0 } ?: return@mapNotNull null
            val dataId = entry.int("data") ?: return@mapNotNull null
            val targetLevel = (entry.int("lvl") ?: 0) + 1
            Upgrade(
                dataId = dataId,
                name = gameData.name(dataId),
                targetLevel = targetLevel,
                finishesAtEpochSec = exportedAt + timer,
                totalSeconds = gameData.upgradeSeconds(dataId, targetLevel),
                isGoblin = entry.bool("extra"),
                hasHelperAssigned = entry.bool("helper_recurrent"),
            )
        }

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

        private val BUILDER_SECTIONS = listOf("buildings", "traps", "heroes")
        private val LAB_SECTIONS = listOf("units", "spells", "siege_machines")
        private val PET_SECTIONS = listOf("pets")
    }
}
