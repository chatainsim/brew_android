package fr.easter.brewhome

import fr.easter.brewhome.data.Beer
import fr.easter.brewhome.data.Brew
import fr.easter.brewhome.data.BrewLogEntry
import fr.easter.brewhome.data.FermReading
import fr.easter.brewhome.data.InventoryItem
import fr.easter.brewhome.data.Recipe
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Parse des réponses réelles capturées sur le serveur BrewHome 0.0.5
 * avec la même config Json que ApiClient.
 */
class ModelsParseTest {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResource(name)!!.readText()

    @Test
    fun parseBeers() {
        val beers = json.decodeFromString<List<Beer>>(fixture("beers.json"))
        assertEquals(1, beers.size)
        assertEquals("Test IPA", beers[0].name)
        assertEquals(12, beers[0].stock33)
        assertEquals(3, beers[0].stock75)
        assertEquals(6.2, beers[0].abv!!, 1e-9)
    }

    @Test
    fun parsePatchedBeer() {
        val beer = json.decodeFromString<Beer>(fixture("beer_patched.json"))
        assertEquals(11, beer.stock33)
    }

    @Test
    fun parseRecipes() {
        val recipes = json.decodeFromString<List<Recipe>>(fixture("recipes.json"))
        assertEquals(1, recipes.size)
        val r = recipes[0]
        assertEquals("Pale Ale test", r.name)
        assertEquals(2, r.ingredients.size)
        val hop = r.ingredients.first { it.category == "houblon" }
        assertEquals(60, hop.hopTime)
        assertEquals(30.0, hop.quantity, 1e-9)
    }

    @Test
    fun parseInventory() {
        val items = json.decodeFromString<List<InventoryItem>>(fixture("inventory.json"))
        assertEquals("Cascade", items[0].name)
        assertEquals(150.0, items[0].quantity, 1e-9)
        assertEquals("g", items[0].unit)
    }

    @Test
    fun parsePatchedInventory() {
        val item = json.decodeFromString<InventoryItem>(fixture("inventory_patched.json"))
        assertEquals(140.0, item.quantity, 1e-9)
    }

    @Test
    fun parseBrews() {
        val brews = json.decodeFromString<List<Brew>>(fixture("brews.json"))
        assertEquals("Brassin test", brews[0].name)
        assertEquals(1.052, brews[0].og!!, 1e-9)
        assertEquals("fermenting", brews[0].status)
    }

    @Test
    fun parseBrewFermentation() {
        // Forme des lignes renvoyées par GET /api/brews/{id}/fermentation :
        // colonnes SQL complètes pour les mesures stockées, colonnes réduites
        // (sans id/source/notes) pour les lectures live du densimètre.
        val raw = """[
            {"id": 1, "brew_id": 3, "recorded_at": "2026-05-01T08:00:00", "gravity": 1.052,
             "temperature": 19.5, "battery": null, "angle": null, "source": "manual", "notes": "levure ajoutée"},
            {"recorded_at": "2026-05-02T08:00:00", "gravity": 1.031, "temperature": 20.1,
             "battery": 3.9, "angle": 45.2}
        ]"""
        val readings = json.decodeFromString<List<FermReading>>(raw)
        assertEquals(2, readings.size)
        assertEquals(1.052, readings[0].gravity!!, 1e-9)
        assertEquals("manual", readings[0].source)
        assertEquals("2026-05-02T08:00:00", readings[1].recordedAt)
        assertEquals(20.1, readings[1].temperature!!, 1e-9)
    }

    @Test
    fun parseBrewLog() {
        val raw = """[
            {"id": 7, "brew_id": 3, "ts": "2026-05-01 10:30", "step": "Empâtage", "note": "65 °C stable"},
            {"id": 8, "brew_id": 3, "ts": "2026-05-01 12:00", "step": null, "note": "Début ébullition"}
        ]"""
        val log = json.decodeFromString<List<BrewLogEntry>>(raw)
        assertEquals(2, log.size)
        assertEquals("Empâtage", log[0].step)
        assertEquals("Début ébullition", log[1].note)
    }
}
