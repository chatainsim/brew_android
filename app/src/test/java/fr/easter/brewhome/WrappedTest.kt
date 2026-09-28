package fr.easter.brewhome

import fr.easter.brewhome.data.Wrapped
import fr.easter.brewhome.ui.fmtLongDate
import fr.easter.brewhome.ui.weekdayName
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bilan « Wrapped » : réponses réelles de GET /api/wrapped (serveur BrewHome 0.1.24). */
class WrappedTest {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResource(name)!!.readText()

    @Test
    fun parseWrapped() {
        val w = json.decodeFromString<Wrapped>(fixture("wrapped.json"))
        assertEquals(2025, w.year)
        assertFalse(w.empty)
        assertEquals(12, w.brews)
        assertEquals(240.0, w.liters, 1e-9)
        assertEquals(480, w.pints)
        assertNull(w.evolutionPct)
        assertEquals(12, w.byMonth.size)
        assertEquals("American IPA", w.styles[0].name)
        assertEquals(6, w.styles[0].count)
        assertEquals("Hop Rocket", w.topRecipe!!.name)
        assertEquals(1670.0, w.hopsG, 1e-9)            // entier dans le JSON
        assertEquals("Citra", w.topHops[0].name)
        assertEquals(720.0, w.topHops[0].grams, 1e-9)
        assertEquals(592.0, w.cost!!, 1e-9)
        assertEquals(5, w.favWeekday)
        assertEquals(7, w.monthStreak)
        assertEquals("brewery", w.profile)
        assertEquals(listOf(2026, 2025), w.years)
    }

    @Test
    fun parseEmptyYear() {
        val w = json.decodeFromString<Wrapped>(fixture("wrapped_empty.json"))
        assertTrue(w.empty)
        assertEquals(2019, w.year)
        assertEquals(0, w.brews)
        assertTrue(w.styles.isEmpty())
    }

    @Test
    fun weekdayAndDates() {
        assertEquals("Lundi", weekdayName(0))
        assertEquals("Samedi", weekdayName(5))
        assertEquals("10 janvier", fmtLongDate("2026-01-10"))
        assertEquals("2026-13-99", fmtLongDate("2026-13-99"))   // date invalide : texte brut
    }
}
