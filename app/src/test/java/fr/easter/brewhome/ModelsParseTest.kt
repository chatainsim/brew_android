package fr.easter.brewhome

import fr.easter.brewhome.data.Beer
import fr.easter.brewhome.data.Brew
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
}
