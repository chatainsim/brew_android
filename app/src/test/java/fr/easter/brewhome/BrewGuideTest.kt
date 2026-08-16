package fr.easter.brewhome

import fr.easter.brewhome.calc.BrewGuideSchedule
import fr.easter.brewhome.data.BrewGuideState
import fr.easter.brewhome.data.BrewGuideStore
import fr.easter.brewhome.data.RecipeIngredient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BrewGuideTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun hop(
        id: Int,
        name: String,
        hopTime: Int? = null,
        hopType: String? = null,
    ) = RecipeIngredient(id = id, name = name, category = "houblon", quantity = 10.0, hopTime = hopTime, hopType = hopType)

    // ── BrewGuideSchedule ────────────────────────────────────────────────

    @Test
    fun `houblon ebullition place a duree moins hopTime`() {
        val schedule = BrewGuideSchedule.boilSchedule(
            listOf(hop(1, "Cascade", hopTime = 15, hopType = "ebullition")),
            boilMinutes = 60,
        )
        assertEquals(1, schedule.size)
        assertEquals(45 * 60_000L, schedule[0].elapsedMs)
    }

    @Test
    fun `houblon sans hopType est traite comme ebullition par defaut`() {
        val schedule = BrewGuideSchedule.boilSchedule(
            listOf(hop(1, "Cascade", hopTime = 20, hopType = null)),
            boilMinutes = 60,
        )
        assertEquals(40 * 60_000L, schedule[0].elapsedMs)
    }

    @Test
    fun `houblon whirlpool place a la fin de l'ebullition`() {
        val schedule = BrewGuideSchedule.boilSchedule(
            listOf(hop(1, "Citra", hopType = "whirlpool")),
            boilMinutes = 60,
        )
        assertEquals(60 * 60_000L, schedule[0].elapsedMs)
    }

    @Test
    fun `houblon dryhop est exclu du planning d'ebullition`() {
        val schedule = BrewGuideSchedule.boilSchedule(
            listOf(hop(1, "Mosaic", hopType = "dryhop")),
            boilMinutes = 60,
        )
        assertTrue(schedule.isEmpty())
    }

    @Test
    fun `deux houblons au meme instant sont tous les deux presents et tries`() {
        val schedule = BrewGuideSchedule.boilSchedule(
            listOf(
                hop(1, "Citra", hopTime = 60, hopType = "ebullition"),
                hop(2, "Cascade", hopTime = 60, hopType = "ebullition"),
                hop(3, "Saaz", hopTime = 10, hopType = "ebullition"),
            ),
            boilMinutes = 60,
        )
        assertEquals(3, schedule.size)
        assertEquals(0L, schedule[0].elapsedMs)
        assertEquals(0L, schedule[1].elapsedMs)
        assertEquals(50 * 60_000L, schedule[2].elapsedMs)
    }

    @Test
    fun `houblonnage ignore par la categorie`() {
        val schedule = BrewGuideSchedule.boilSchedule(
            listOf(RecipeIngredient(id = 1, name = "Pilsner", category = "malt", quantity = 5.0)),
            boilMinutes = 60,
        )
        assertTrue(schedule.isEmpty())
    }

    // ── BrewGuideStore ───────────────────────────────────────────────────

    @Test
    fun `save et load persistent l'etat par cle`() {
        val store = BrewGuideStore(tmp.root)
        val state = BrewGuideState(step = 2, checkedItems = setOf("prep_1", "crush_2"))
        store.save("recipe_1", state)

        assertEquals(state, store.load("recipe_1"))
        assertNull(store.load("recipe_2"))
        // Une nouvelle instance relit le même fichier
        assertEquals(state, BrewGuideStore(tmp.root).load("recipe_1"))
    }

    @Test
    fun `save conserve les autres cles et clear ne retire que la sienne`() {
        val store = BrewGuideStore(tmp.root)
        store.save("recipe_1", BrewGuideState(step = 1))
        store.save("brew_5", BrewGuideState(step = 3))

        store.clear("recipe_1")

        assertNull(store.load("recipe_1"))
        assertEquals(3, store.load("brew_5")?.step)
    }

}
