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
    }

    @Test
    fun `leaves builder timers alone without a builder potion`() {
        val parsed = parser.parse(
            """{"tag":"#A","timestamp":100,"boosts":{"builder_boost":0,"clocktower_cooldown":10304},""" +
                """"buildings":[{"data":1000019,"lvl":16,"timer":30000}]}""",
        )
        assertEquals(30_100L, parsed.builderUpgrades.single().finishesAtEpochSec)
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

    private fun readResource(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource(name)) { "Missing test resource $name" }.readText()

    private companion object {
        const val GAME_DATA_PATH = "src/main/assets/game_data.json"
        const val EXPORTED_AT = 1_793_623_055L
    }
}
