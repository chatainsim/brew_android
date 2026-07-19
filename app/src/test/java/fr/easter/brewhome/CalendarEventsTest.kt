package fr.easter.brewhome

import fr.easter.brewhome.calc.CalendarEvents
import fr.easter.brewhome.data.Beer
import fr.easter.brewhome.data.Brew
import fr.easter.brewhome.data.CustomEvent
import fr.easter.brewhome.data.Recipe
import fr.easter.brewhome.data.RecipeIngredient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CalendarEventsTest {

    private val today = LocalDate.of(2026, 7, 19)

    private fun agenda(
        brews: List<Brew> = emptyList(),
        recipes: Map<Int, Recipe> = emptyMap(),
        beers: List<Beer> = emptyList(),
        customEvents: List<CustomEvent> = emptyList(),
    ) = CalendarEvents.agenda(today, brews, recipes, beers, emptyList(), customEvents)

    @Test
    fun `nthDow calcule le n-ieme jour de semaine en convention JS`() {
        // 1er jeudi d'août 2026 = 6 août (IPA Day)
        assertEquals(LocalDate.of(2026, 8, 6), CalendarEvents.nthDow(2026, 8, 4, 1))
        // dernier dimanche de juillet 2026 = 26 juillet
        assertEquals(LocalDate.of(2026, 7, 26), CalendarEvents.nthDow(2026, 7, 0, -1))
    }

    @Test
    fun `agenda place brassage embouteillage et fin de fermentation dans l'horizon`() {
        val brews = listOf(
            Brew(
                id = 1, name = "Blonde", brewDate = "2026-07-20",
                bottlingDate = "2026-08-10", fermTime = 14,
            ),
            Brew(id = 2, name = "Vieille", brewDate = "2025-01-01"), // passée : exclue
        )
        val types = agenda(brews = brews).filter { it.label.startsWith("Blonde") }
            .map { it.type to it.date }
        assertTrue(CalendarEvents.Type.BREW to LocalDate.of(2026, 7, 20) in types)
        assertTrue(CalendarEvents.Type.BOTTLE to LocalDate.of(2026, 8, 10) in types)
        assertTrue(CalendarEvents.Type.FERM_END to LocalDate.of(2026, 8, 3) in types)
        assertTrue(agenda(brews = brews).none { it.label == "Vieille" })
    }

    @Test
    fun `agenda calcule les dry hops depuis le debut de fermentation`() {
        val recipe = Recipe(
            id = 5, name = "R",
            ingredients = listOf(
                RecipeIngredient(
                    id = 1, name = "Citra", category = "houblon",
                    quantity = 50.0, unit = "g", hopType = "dryhop", hopDays = 4,
                ),
            ),
        )
        val brew = Brew(
            id = 1, name = "IPA", recipeId = 5, status = "fermenting",
            brewDate = "2026-07-10", fermentingSince = "2026-07-12T08:00:00", fermTime = 14,
        )
        val dryhop = agenda(brews = listOf(brew), recipes = mapOf(5 to recipe))
            .single { it.type == CalendarEvents.Type.DRYHOP }
        // J10 = ferm_time 14 - hop_days 4, depuis fermenting_since (12/07)
        assertEquals(LocalDate.of(2026, 7, 22), dryhop.date)
        assertTrue(dryhop.label.contains("50 g Citra"))
    }

    @Test
    fun `recurrence annuelle et rappel brassage genere a J moins N`() {
        val ev = CustomEvent(
            id = 1, title = "Fête de la bière", eventDate = "2020-09-01",
            recurrence = """{"type":"yearly"}""", brewReminder = 1, brewReminderDays = 30,
        )
        val events = agenda(customEvents = listOf(ev))
        assertTrue(events.any { it.type == CalendarEvents.Type.CUSTOM && it.date == LocalDate.of(2026, 9, 1) })
        assertTrue(events.any { it.type == CalendarEvents.Type.REMIND && it.date == LocalDate.of(2026, 8, 2) })
    }

    @Test
    fun `recurrence hebdomadaire respecte l'intervalle`() {
        val ev = CustomEvent(
            id = 1, title = "Club brassage", eventDate = "2026-07-01",
            recurrence = """{"type":"weekly","interval":2}""",
        )
        val dates = CalendarEvents.expand(ev, 2026)
        assertTrue(LocalDate.of(2026, 7, 15) in dates)
        assertTrue(LocalDate.of(2026, 7, 29) in dates)
        assertTrue(LocalDate.of(2026, 7, 8) !in dates)
    }

    @Test
    fun `journees mondiales incluent IPA Day et le debut d'Oktoberfest`() {
        val days = CalendarEvents.worldBeerDays(2026)
        assertEquals(LocalDate.of(2026, 8, 6), days.single { it.label == "IPA Day" }.date)
        // Samedi précédant le 22 septembre 2026 (mardi) = 19 septembre
        assertEquals(LocalDate.of(2026, 9, 19), days.single { it.label == "Début Oktoberfest" }.date)
    }
}
