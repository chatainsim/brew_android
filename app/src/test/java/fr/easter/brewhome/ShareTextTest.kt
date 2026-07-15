package fr.easter.brewhome

import fr.easter.brewhome.data.InventoryItem
import fr.easter.brewhome.data.Recipe
import fr.easter.brewhome.share.ShareText
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareTextTest {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResource(name)!!.readText()

    @Test
    fun `partage recette`() {
        val recipe = json.decodeFromString<List<Recipe>>(fixture("recipes.json"))[0]
        val text = ShareText.recipe(recipe)
        assertTrue(text.startsWith("🍺 Pale Ale test (APA)"))
        assertTrue("- Volume : 20 L" in text)
        assertTrue("- Empâtage : 66 °C · 60 min" in text)
        assertTrue("- Ébullition : 60 min" in text)
        assertTrue("- Fermentation : 20 °C · 14 jours" in text)
        // Malts avant houblons, avec détails entre parenthèses
        assertTrue("Malts\n- Pilsner : 4,5 kg (3,5 EBC)" in text)
        assertTrue("Houblons\n- Cascade : 30 g (60 min · 6,5 % α)" in text)
        assertTrue(text.indexOf("Malts") < text.indexOf("Houblons"))
        assertTrue(text.endsWith("Partagé depuis BrewHome Android"))
    }

    @Test
    fun `partage stock`() {
        val items = json.decodeFromString<List<InventoryItem>>(fixture("inventory.json"))
        val text = ShareText.inventory(items, "15/07/2026")
        assertTrue(text.startsWith("📦 Stock d'ingrédients de brasserie (BrewHome) — 15/07/2026"))
        assertTrue("Houblons\n- Cascade : 150 g (6,5 % α)" in text)
        assertTrue(text.endsWith("Partagé depuis BrewHome Android"))
    }

    @Test
    fun `stock bas signale`() {
        val item = InventoryItem(
            id = 1, name = "Pilsner", category = "malt",
            quantity = 0.5, unit = "kg", minStock = 1.0,
        )
        val text = ShareText.inventory(listOf(item), "15/07/2026")
        assertTrue("- Pilsner : 0,5 kg ⚠️ stock bas" in text)
        // Pas d'alerte si au-dessus du seuil
        val ok = ShareText.inventory(listOf(item.copy(quantity = 2.0)), "15/07/2026")
        assertEquals(false, "stock bas" in ok)
    }
}
