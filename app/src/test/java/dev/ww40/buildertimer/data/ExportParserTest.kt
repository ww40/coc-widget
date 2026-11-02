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
        assertEquals(6, snapshot.busyBuilders)
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
    fun `names troops missing from coc_py via the overrides file`() {
        val lab = snapshot.labUpgrades.single()
        assertEquals("Ruin Witch", lab.name)
        assertEquals(2, lab.targetLevel)
        assertNull(lab.totalSeconds)
        assertNull(lab.progress(EXPORTED_AT))
        assertEquals(lab, snapshot.labAssistantTarget)
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
        assertFalse(minimal.hasGoblin)
        assertNull(minimal.apprenticeTarget)
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
