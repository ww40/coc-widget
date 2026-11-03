package dev.ww40.buildertimer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TimeToMaxTest {

    private val gameData = GameData(
        mapOf(
            HERO to entry(2 to 100L to 10, 3 to 200L to 11, 4 to 400L to 12),
            OTHER_HERO to entry(2 to 50L to 10),
            BUILDING to entry(2 to 1_000L to 10, 3 to 2_000L to 11),
            WALL to entry(),
            PET to entry(2 to 300L to 10, 3 to 500L to 10),
            ExportParser.TOWN_HALL_ID to entry(11 to 9_000L to 10, 12 to 9_500L to 11),
        ),
    )

    private fun estimate(
        category: MaxCategory,
        items: List<MaxItem>,
        target: MaxTarget = MaxTarget.TOWN_HALL,
        builders: Int = 1,
        townHall: Int? = 11,
    ): List<MaxEstimate> =
        TimeToMaxCalculator(gameData, builders, townHall).estimates(mapOf(category to items), target)

    private fun seconds(
        category: MaxCategory,
        items: List<MaxItem>,
        target: MaxTarget = MaxTarget.TOWN_HALL,
        builders: Int = 1,
    ): Long = estimate(category, items, target, builders).single().seconds

    @Test
    fun `stops at the current town hall or the game max`() {
        val hero = listOf(MaxItem(HERO, 1, null, 1))
        assertEquals(300L, seconds(MaxCategory.HEROES, hero))
        assertEquals(700L, seconds(MaxCategory.HEROES, hero, MaxTarget.GAME))
    }

    @Test
    fun `uses the timer in place of the level being upgraded`() {
        assertEquals(40L + 200, seconds(MaxCategory.HEROES, listOf(MaxItem(HERO, 1, 40, 1))))
    }

    @Test
    fun `an item upgrading to its last level only has its timer left`() {
        assertEquals(40L, seconds(MaxCategory.HEROES, listOf(MaxItem(HERO, 2, 40, 1))))
    }

    @Test
    fun `an item at the limit is maxed`() {
        val maxed = estimate(MaxCategory.HEROES, listOf(MaxItem(HERO, 3, null, 1))).single()
        assertTrue(maxed.isMaxed)
        assertFalse(estimate(MaxCategory.HEROES, listOf(MaxItem(HERO, 3, null, 1)), MaxTarget.GAME).single().isMaxed)
    }

    @Test
    fun `shares builder work across builders and across copies`() {
        val buildings = listOf(MaxItem(BUILDING, 1, null, 4))
        assertEquals(6_000L, seconds(MaxCategory.BUILDINGS, buildings, builders = 2))
        assertEquals(3_000L, seconds(MaxCategory.BUILDINGS, buildings.map { it.copy(count = 1) }, builders = 3))
    }

    @Test
    fun `rounds shared work up`() {
        val buildings = listOf(MaxItem(BUILDING, 2, null, 3), MaxItem(BUILDING, 1, 2, 1))
        assertEquals(2_668L, seconds(MaxCategory.BUILDINGS, buildings, builders = 3))
    }

    @Test
    fun `one item can't finish faster than its own levels however many builders`() {
        val hero = listOf(MaxItem(HERO, 1, null, 1), MaxItem(OTHER_HERO, 1, null, 1))
        assertEquals(300L, seconds(MaxCategory.HEROES, hero, builders = 6))
    }

    @Test
    fun `pets and lab run one at a time however many builders`() {
        val pets = listOf(MaxItem(PET, 1, null, 1), MaxItem(PET, 2, null, 1))
        assertEquals(1_300L, seconds(MaxCategory.PETS, pets, builders = 6))
    }

    @Test
    fun `counts the town hall only for the game max`() {
        val townHall = listOf(MaxItem(ExportParser.TOWN_HALL_ID, 10, null, 1))
        assertEquals(0L, seconds(MaxCategory.BUILDINGS, townHall))
        assertEquals(18_500L, seconds(MaxCategory.BUILDINGS, townHall, MaxTarget.GAME))
        assertEquals(50L, seconds(MaxCategory.BUILDINGS, listOf(MaxItem(ExportParser.TOWN_HALL_ID, 10, 50, 1))))
    }

    @Test
    fun `ignores upgrades with no duration and unknown items`() {
        assertEquals(0L, seconds(MaxCategory.BUILDINGS, listOf(MaxItem(WALL, 1, null, 300))))
        assertEquals(0L, seconds(MaxCategory.BUILDINGS, listOf(MaxItem(UNKNOWN, 1, null, 1))))
    }

    @Test
    fun `survives an export with no builders`() {
        assertEquals(3_000L, seconds(MaxCategory.BUILDINGS, listOf(MaxItem(BUILDING, 1, null, 1)), builders = 0))
    }

    @Test
    fun `leaves out categories with nothing in the export`() {
        assertTrue(estimate(MaxCategory.GUARDIANS, emptyList()).isEmpty())
    }

    @Test
    fun `has no town hall estimate without a town hall level`() {
        val hero = listOf(MaxItem(HERO, 1, null, 1))
        assertTrue(estimate(MaxCategory.HEROES, hero, townHall = null).isEmpty())
        assertEquals(700L, estimate(MaxCategory.HEROES, hero, MaxTarget.GAME, townHall = null).single().seconds)
    }

    @Test
    fun `parses an export into estimates in display order`() {
        val parser = ExportParser(GameData.fromJson(File(GAME_DATA_PATH).readText()))
        val snapshot = parser.parse(
            """{"tag":"#A","timestamp":100,""" +
                """"buildings":[{"data":1000001,"lvl":18,"cnt":1},{"data":1000015,"lvl":8,"cnt":2},""" +
                """{"data":1000097,"types":[]},{"data":1000010,"lvl":18,"cnt":325}],""" +
                """"guardians":[{"data":107000008,"lvl":4,"timer":100}],""" +
                """"heroes":[{"data":28000000,"lvl":110}],""" +
                """"pets":[{"data":73000000,"lvl":15}],""" +
                """"units":[{"data":4000000,"lvl":13}]}""",
        )
        val estimates = snapshot.timeToMax.getValue(MaxTarget.TOWN_HALL)
        assertEquals(
            listOf(MaxCategory.HEROES, MaxCategory.GUARDIANS, MaxCategory.BUILDINGS, MaxCategory.PETS, MaxCategory.LAB),
            estimates.map { it.category },
        )
        assertEquals(100L, estimates.single { it.category == MaxCategory.GUARDIANS }.seconds)
        assertTrue(estimates.single { it.category == MaxCategory.BUILDINGS }.isMaxed)
        assertTrue(estimates.single { it.category == MaxCategory.HEROES }.isMaxed)
    }

    private fun entry(vararg levels: Pair<Pair<Int, Long>, Int>) = GameData.Entry(
        name = "Item",
        upgradeSeconds = levels.associate { (level, _) -> level },
        requiredTownHall = levels.associate { (level, townHall) -> level.first to townHall },
    )

    private companion object {
        const val GAME_DATA_PATH = "src/main/assets/game_data.json"
        const val HERO = 1
        const val OTHER_HERO = 2
        const val BUILDING = 3
        const val WALL = 4
        const val PET = 5
        const val UNKNOWN = 6
    }
}
