package dev.ww40.buildertimer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class ExportParserTest {

    private lateinit var parser: ExportParser
    private lateinit var snapshot: VillageSnapshot

    @Before
    fun setUp() {
        parser = ExportParser(GameData.fromJson(File(GAME_DATA_PATH).readText()))
        snapshot = parser.parse(readResource("sample_export.json"))
    }

    @Test
    fun `reads header fields`() {
        assertEquals("#TESTTAG", snapshot.tag)
        assertEquals(18, snapshot.townHallLevel)
        assertEquals(EXPORTED_AT, snapshot.exportedAtEpochSec)
    }

    @Test
    fun `header uses the player name when set and the tag otherwise`() {
        assertEquals("Kuswa  TH18", snapshot.headerTitle("Kuswa"))
        assertEquals("Kuswa  TH18", snapshot.headerTitle("  Kuswa "))
        assertEquals("#TESTTAG  TH18", snapshot.headerTitle(null))
        assertEquals("#TESTTAG  TH18", snapshot.headerTitle("   "))
        assertEquals("#A", parser.parse("""{"tag":"#A","timestamp":100}""").headerTitle(null))
    }

    @Test
    fun `flags exports older than an hour as stale`() {
        val limit = VillageSnapshot.STALE_AFTER_SECONDS
        assertFalse(snapshot.isStale(EXPORTED_AT))
        assertFalse(snapshot.isStale(EXPORTED_AT + limit))
        assertTrue(snapshot.isStale(EXPORTED_AT + limit + 1))
        assertFalse(snapshot.isStale(EXPORTED_AT - 30))
    }

    @Test
    fun `counts builder huts plus BOB`() {
        assertEquals(6, snapshot.builderCount)
        assertEquals(6, snapshot.busyBuilders(EXPORTED_AT))
    }

    @Test
    fun `lists builder upgrades soonest first including the goblin`() {
        val builders = snapshot.builderUpgrades
        assertEquals(7, builders.size)
        assertEquals(builders.sortedBy { it.finishesAtEpochSec }, builders)
        assertEquals("Hidden Tesla", builders.first().name)
        assertEquals(16, builders.first().targetLevel)
        assertEquals(EXPORTED_AT + 128_365, builders.first().finishesAtEpochSec)

        val goblin = builders.single { it.isGoblin }
        assertEquals("Ricochet Cannon", goblin.name)
        assertEquals(4, builders.indexOf(goblin))
    }

    @Test
    fun `marks the item the builder apprentice is assigned to`() {
        val goldStorage = snapshot.builderUpgrades.single { it.name == "Gold Storage" }
        assertTrue(goldStorage.hasHelperAssigned)
        assertEquals(goldStorage, snapshot.apprenticeTarget)
        assertEquals(19, goldStorage.targetLevel)
        assertEquals(1_080_000L, goldStorage.totalSeconds)
    }

    @Test
    fun `marks the lab upgrade the lab assistant is assigned to`() {
        val lab = snapshot.labUpgrades.single()
        assertEquals("Ruin Witch", lab.name)
        assertEquals(2, lab.targetLevel)
        assertEquals(950_400L, lab.totalSeconds)
        assertEquals(lab, snapshot.labAssistantTarget)
    }

    @Test
    fun `has durations for recent heroes and TH18 levels`() {
        val parsed = parser.parse(
            """{"tag":"#A","timestamp":100,"heroes":[{"data":28000007,"lvl":22,"timer":60}],""" +
                """"buildings":[{"data":1000019,"lvl":16,"timer":60}]}""",
        )
        val duke = parsed.builderUpgrades.single { it.name == "Dragon Duke" }
        assertEquals(23, duke.targetLevel)
        assertEquals(691_200L, duke.totalSeconds)
        assertEquals(1_123_200L, parsed.builderUpgrades.single { it.name == "Hidden Tesla" }.totalSeconds)
    }

    @Test
    fun `falls back to a placeholder name for IDs missing from the game data`() {
        val parsed = parser.parse("""{"tag":"#A","timestamp":100,"units":[{"data":4009999,"lvl":1,"timer":60}]}""")
        assertEquals("Unknown (4009999)", parsed.labUpgrades.single().name)
    }

    @Test
    fun `computes pet progress from the full upgrade duration`() {
        val pet = snapshot.petUpgrades.single()
        assertEquals("Sneezy", pet.name)
        assertEquals(10, pet.targetLevel)
        assertEquals(691_200L, pet.totalSeconds)
        assertEquals(1f - 197_587f / 691_200f, pet.progress(EXPORTED_AT)!!, 0.0001f)
        assertEquals(1f, pet.progress(pet.finishesAtEpochSec + 10)!!, 0f)
    }

    @Test
    fun `tolerates surrounding whitespace and missing sections`() {
        val minimal = parser.parse("  {\"tag\":\"#A\",\"timestamp\":100}\n")
        assertEquals("#A", minimal.tag)
        assertNull(minimal.townHallLevel)
        assertTrue(minimal.allUpgrades.isEmpty())
        assertEquals(0, minimal.builderCount)
        assertFalse(minimal.hasActiveGoblin(100))
        assertNull(minimal.apprenticeTarget)
    }

    @Test
    fun `speeds up builder timers while a builder potion is active`() {
        val parsed = parser.parse(
            """{"tag":"#A","timestamp":100,"boosts":{"builder_boost":3600},""" +
                """"buildings":[{"data":1000019,"lvl":16,"timer":30000},{"data":1000013,"lvl":17,"timer":100000}],""" +
                """"heroes":[{"data":28000000,"lvl":94,"timer":36005}],""" +
                """"units":[{"data":4000000,"lvl":1,"timer":30000}]}""",
        )
        val finishes = parsed.builderUpgrades.associate { it.name to it.finishesAtEpochSec - 100 }
        assertEquals(3_000L, finishes["Hidden Tesla"])
        assertEquals(3_605L, finishes["Barbarian King"])
        assertEquals(100_000L - 36_000 + 3_600, finishes["Mortar"])
        assertEquals(30_100L, parsed.labUpgrades.single().finishesAtEpochSec)
        assertNull(parsed.labBoostEndsAtEpochSec)

        assertEquals(3_700L, parsed.builderBoostEndsAtEpochSec)
        assertEquals(3_600L, parsed.builderBoostRemaining(100))
        assertEquals(0L, parsed.builderBoostRemaining(5_000))
    }

    @Test
    fun `leaves builder timers alone without a builder potion`() {
        val parsed = parser.parse(
            """{"tag":"#A","timestamp":100,"boosts":{"builder_boost":0,"clocktower_cooldown":10304},""" +
                """"buildings":[{"data":1000019,"lvl":16,"timer":30000}]}""",
        )
        assertEquals(30_100L, parsed.builderUpgrades.single().finishesAtEpochSec)
        assertNull(parsed.builderBoostEndsAtEpochSec)
        assertEquals(0L, parsed.builderBoostRemaining(100))
    }

    @Test
    fun `speeds up lab timers while a research potion is active`() {
        val parsed = parser.parse(
            """{"tag":"#A","timestamp":100,"boosts":{"lab_boost":3593},""" +
                """"units":[{"data":4000109,"lvl":1,"timer":204269}],""" +
                """"spells":[{"data":26000000,"lvl":1,"timer":24005}],""" +
                """"buildings":[{"data":1000019,"lvl":16,"timer":30000}],""" +
                """"pets":[{"data":73000000,"lvl":1,"timer":30000}]}""",
        )
        val finishes = parsed.labUpgrades.associate { it.dataId to it.finishesAtEpochSec - 100 }
        assertEquals(204_269L - 3_593 * 24 + 3_593, finishes[4000109])
        assertEquals(1_001L, finishes[26000000])
        assertEquals(30_100L, parsed.builderUpgrades.single().finishesAtEpochSec)
        assertEquals(30_100L, parsed.petUpgrades.single().finishesAtEpochSec)

        assertEquals(3_693L, parsed.labBoostEndsAtEpochSec)
        assertEquals(3_593L, parsed.labBoostRemaining(100))
        assertEquals(0L, parsed.labBoostRemaining(5_000))
        assertNull(parsed.builderBoostEndsAtEpochSec)
    }

    @Test
    fun `speeds up pet timers while a pet potion is active`() {
        val parsed = parser.parse(
            """{"tag":"#A","timestamp":100,"boosts":{"pet_boost":3595},""" +
                """"pets":[{"data":73000000,"lvl":1,"timer":106836},{"data":73000001,"lvl":1,"timer":24005}],""" +
                """"units":[{"data":4000000,"lvl":1,"timer":30000}],""" +
                """"buildings":[{"data":1000019,"lvl":16,"timer":30000}]}""",
        )
        val finishes = parsed.petUpgrades.associate { it.dataId to it.finishesAtEpochSec - 100 }
        assertEquals(106_836L - 3_595 * 24 + 3_595, finishes[73000000])
        assertEquals(1_001L, finishes[73000001])
        assertEquals(30_100L, parsed.labUpgrades.single().finishesAtEpochSec)
        assertEquals(30_100L, parsed.builderUpgrades.single().finishesAtEpochSec)

        assertEquals(3_695L, parsed.petBoostEndsAtEpochSec)
        assertEquals(3_595L, parsed.petBoostRemaining(100))
        assertEquals(0L, parsed.petBoostRemaining(5_000))
        assertNull(parsed.labBoostEndsAtEpochSec)
    }

    @Test
    fun `lists guardian upgrades as builder work`() {
        val parsed = parser.parse(
            """{"tag":"#A","timestamp":100,""" +
                """"buildings":[{"data":1000015,"lvl":4,"cnt":2},{"data":1000019,"lvl":15,"timer":70717}],""" +
                """"guardians":[{"data":107000000,"lvl":1},""" +
                """{"data":107000008,"lvl":2,"timer":54865,"helper_timer":0,"helper_recurrent":false}]}""",
        )
        val logger = parsed.builderUpgrades.first()
        assertEquals("Logger", logger.name)
        assertEquals(3, logger.targetLevel)
        assertEquals(777_600L, logger.totalSeconds)
        assertEquals(54_965L, logger.finishesAtEpochSec)
        assertEquals(2, parsed.builderUpgrades.size)
        assertEquals(2, parsed.busyBuilders(100))
        assertEquals(0, parsed.freeBuilders(100))
    }

    @Test
    fun `speeds up guardian timers while a builder potion is active`() {
        val parsed = parser.parse(
            """{"tag":"#A","timestamp":100,"boosts":{"builder_boost":3600},""" +
                """"guardians":[{"data":107000008,"lvl":2,"timer":54865}]}""",
        )
        assertEquals(100L + 54_865 - 36_000 + 3_600, parsed.builderUpgrades.single().finishesAtEpochSec)
    }

    @Test
    fun `tracks each helper's cooldown separately`() {
        val parsed = parser.parse(
            """{"tag":"#A","timestamp":100,"helpers":[{"data":93000000,"lvl":8,"helper_cooldown":81694},""" +
                """{"data":93000001,"lvl":8},{"data":93000002,"lvl":5,"helper_cooldown":90000}]}""",
        )
        assertEquals(81_794L, parsed.apprenticeReadyAtEpochSec)
        assertEquals(100L, parsed.labAssistantReadyAtEpochSec)
    }

    @Test
    fun `has no helper times when the export lists no helpers`() {
        val parsed = parser.parse("""{"tag":"#A","timestamp":100}""")
        assertNull(parsed.apprenticeReadyAtEpochSec)
        assertNull(parsed.labAssistantReadyAtEpochSec)
    }

    @Test
    fun `a helper's shift adds its level to the upgrade's speed`() {
        val parsed = helperExport(""""heroes":[{"data":28000001,"lvl":89,"timer":194891,"helper_timer":2494}]""")
        assertEquals(100L + 194_891 - 2_494 * 9 + 2_494, parsed.builderUpgrades.single().finishesAtEpochSec)
    }

    @Test
    fun `an upgrade can finish during a helper's shift`() {
        val parsed = helperExport(""""heroes":[{"data":28000001,"lvl":89,"timer":900,"helper_timer":2494}]""")
        assertEquals(200L, parsed.builderUpgrades.single().finishesAtEpochSec)
    }

    @Test
    fun `a helper's level adds to a potion only on the upgrade it is assigned to`() {
        val parsed = helperExport(
            """"boosts":{"builder_boost":3432},""" +
                """"heroes":[{"data":28000001,"lvl":89,"timer":194891,"helper_timer":2494},""" +
                """{"data":28000000,"lvl":90,"timer":26696}]""",
        )
        val finishes = parsed.builderUpgrades.associate { it.dataId to it.finishesAtEpochSec - 100 }
        assertEquals(3_432L + 194_891 - 2_494 * 18 - 938 * 10, finishes[28000001])
        assertEquals(2_670L, finishes[28000000])
    }

    @Test
    fun `the potion can outlast the helper or the helper the potion`() {
        val parsed = helperExport(
            """"boosts":{"builder_boost":600},""" +
                """"heroes":[{"data":28000001,"lvl":89,"timer":100000,"helper_timer":3600}]""",
        )
        assertEquals(100L + 3_600 + 100_000 - 600 * 18 - 3_000 * 9, parsed.builderUpgrades.single().finishesAtEpochSec)
    }

    @Test
    fun `the lab assistant speeds up research but not pets`() {
        val parsed = helperExport(
            """"units":[{"data":4000000,"lvl":1,"timer":30000,"helper_timer":1000}],""" +
                """"pets":[{"data":73000000,"lvl":1,"timer":30000,"helper_timer":1000}]""",
        )
        assertEquals(100L + 1_000 + 30_000 - 1_000 * 5, parsed.labUpgrades.single().finishesAtEpochSec)
        assertEquals(30_100L, parsed.petUpgrades.single().finishesAtEpochSec)
    }

    @Test
    fun `a one-off helper counts as assigned`() {
        val parsed = helperExport(""""heroes":[{"data":28000001,"lvl":89,"timer":194891,"helper_timer":2494,"helper_recurrent":false}]""")
        assertEquals(parsed.builderUpgrades.single(), parsed.apprenticeTarget)
    }

    @Test
    fun `keep assigned without an active shift adds no speed`() {
        val parsed = helperExport(""""heroes":[{"data":28000001,"lvl":89,"timer":194891,"helper_recurrent":true}]""")
        assertEquals(194_991L, parsed.builderUpgrades.single().finishesAtEpochSec)
        assertEquals(parsed.builderUpgrades.single(), parsed.apprenticeTarget)
    }

    @Test
    fun `a shift with no listed helper adds no speed`() {
        val parsed = parser.parse("""{"tag":"#A","timestamp":100,"heroes":[{"data":28000001,"lvl":89,"timer":5000,"helper_timer":2494}]}""")
        assertEquals(5_100L, parsed.builderUpgrades.single().finishesAtEpochSec)
    }

    @Test
    fun `lists builder base and star lab upgrades separately`() {
        val parsed = parser.parse(
            """{"tag":"#A","timestamp":100,""" +
                """"buildings":[{"data":1000019,"lvl":16,"timer":60}],""" +
                """"buildings2":[{"data":1000044,"lvl":6,"timer":198177},{"data":1000033,"lvl":3,"cnt":41}],""" +
                """"traps2":[{"data":12000010,"lvl":3,"timer":113176}],""" +
                """"heroes2":[{"data":28000003,"lvl":25,"timer":500}],""" +
                """"units2":[{"data":4000034,"lvl":13,"timer":237783}]}""",
        )
        assertEquals(listOf("Battle Machine", "Spring Trap", "Cannon"), parsed.builderBaseUpgrades.map { it.name })
        assertEquals(7L, parsed.builderBaseUpgrades.last().targetLevel.toLong())
        assertEquals(345_600L, parsed.builderBaseUpgrades.last().totalSeconds)
        assertEquals("Boxer Giant", parsed.starLabUpgrades.single().name)
        assertEquals(237_883L, parsed.starLabUpgrades.single().finishesAtEpochSec)
        assertEquals(1, parsed.builderUpgrades.size)
        assertEquals(1, parsed.busyBuilders(100))
    }

    @Test
    fun `a clock tower boost speeds up only the builder base and star lab`() {
        val parsed = parser.parse(
            """{"tag":"#A","timestamp":100,"boosts":{"clocktower_boost":600,"clocktower_cooldown":5000},""" +
                """"buildings":[{"data":1000019,"lvl":16,"timer":30000}],""" +
                """"buildings2":[{"data":1000044,"lvl":6,"timer":30000}],""" +
                """"units2":[{"data":4000034,"lvl":13,"timer":3000}]}""",
        )
        assertEquals(100L + 30_000 - 6_000 + 600, parsed.builderBaseUpgrades.single().finishesAtEpochSec)
        assertEquals(400L, parsed.starLabUpgrades.single().finishesAtEpochSec)
        assertEquals(30_100L, parsed.builderUpgrades.single().finishesAtEpochSec)
        assertEquals(700L, parsed.clockTowerBoostEndsAtEpochSec)
        assertEquals(600L, parsed.clockTowerBoostRemaining(100))
    }

    @Test
    fun `a clock tower cooldown alone is not a boost`() {
        val parsed = parser.parse(
            """{"tag":"#A","timestamp":100,"boosts":{"clocktower_cooldown":56562},""" +
                """"buildings2":[{"data":1000044,"lvl":6,"timer":198177}]}""",
        )
        assertNull(parsed.clockTowerBoostEndsAtEpochSec)
        assertEquals(198_277L, parsed.builderBaseUpgrades.single().finishesAtEpochSec)
    }

    private fun maxedWith(sections: String) = parser.parse("""{"tag":"#A","timestamp":100,$sections}""").builderBaseMaxed

    @Test
    fun `builder base is maxed once every item is at the top level`() {
        assertTrue(maxedWith(""""buildings2":[{"data":1000034,"lvl":10},{"data":1000044,"lvl":10,"cnt":2}],"traps2":[{"data":12000010,"lvl":5}]"""))
    }

    @Test
    fun `items without levels do not stop builder base being maxed`() {
        assertTrue(maxedWith(""""buildings2":[{"data":1000034,"lvl":10},{"data":1000042,"lvl":1,"cnt":6}]"""))
    }

    @Test
    fun `builder base walls must be at the top level`() {
        assertTrue(maxedWith(""""buildings2":[{"data":1000034,"lvl":10},{"data":1000033,"lvl":10,"cnt":180}]"""))
        assertFalse(maxedWith(""""buildings2":[{"data":1000034,"lvl":10},{"data":1000033,"lvl":9,"cnt":1},{"data":1000033,"lvl":10,"cnt":179}]"""))
    }

    @Test
    fun `builder base is not maxed with an item below the top level`() {
        assertFalse(maxedWith(""""buildings2":[{"data":1000034,"lvl":10},{"data":1000044,"lvl":9}]"""))
    }

    @Test
    fun `builder base is not maxed while its last upgrade is running`() {
        assertFalse(maxedWith(""""buildings2":[{"data":1000044,"lvl":9,"timer":500}]"""))
        assertFalse(maxedWith(""""buildings2":[{"data":1000033,"lvl":3,"timer":500}]"""))
    }

    @Test
    fun `builder base is not maxed while star lab has levels left`() {
        assertFalse(maxedWith(""""buildings2":[{"data":1000034,"lvl":10}],"units2":[{"data":4000034,"lvl":1}]"""))
    }

    @Test
    fun `builder base is not maxed when the export has none`() {
        assertFalse(maxedWith(""""buildings":[{"data":1000001,"lvl":18}]"""))
    }

    @Test
    fun `ignores zero timers`() {
        val parsed = parser.parse("""{"tag":"#A","timestamp":100,"buildings":[{"data":1000005,"lvl":1,"timer":0}]}""")
        assertTrue(parsed.builderUpgrades.isEmpty())
    }

    @Test
    fun `rejects empty, non-JSON and incomplete input`() {
        assertThrows(ExportFormatException::class.java) { parser.parse("   ") }
        assertThrows(ExportFormatException::class.java) { parser.parse("hello from the clipboard") }
        assertThrows(ExportFormatException::class.java) { parser.parse("[1,2,3]") }
        assertThrows(ExportFormatException::class.java) { parser.parse("""{"tag":"#A"}""") }
        assertThrows(ExportFormatException::class.java) { parser.parse("""{"timestamp":100}""") }
    }

    /** Export at timestamp 100 with a level 8 Apprentice and a level 4 Lab Assistant. */
    private fun helperExport(sections: String): VillageSnapshot = parser.parse(
        """{"tag":"#A","timestamp":100,"helpers":[{"data":93000000,"lvl":8},{"data":93000001,"lvl":4}],$sections}""",
    )

    private fun readResource(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource(name)) { "Missing test resource $name" }.readText()

    private companion object {
        const val GAME_DATA_PATH = "src/main/assets/game_data.json"
        const val EXPORTED_AT = 1_793_623_055L
    }
}
