package fr.easter.brewhome

import fr.easter.brewhome.calc.StockCheck
import fr.easter.brewhome.data.Draft
import fr.easter.brewhome.data.InventoryItem
import fr.easter.brewhome.data.RecipeIngredient
import fr.easter.brewhome.data.parsedIngredients
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Même logique que checkRecipeStock() dans bh-recettes.js du serveur. */
class StockCheckTest {

    private fun ing(name: String, qty: Double, unit: String, cat: String = "malt") =
        RecipeIngredient(id = 0, name = name, category = cat, quantity = qty, unit = unit)

    private fun inv(name: String, qty: Double, unit: String, cat: String = "malt") =
        InventoryItem(id = 0, name = name, category = cat, quantity = qty, unit = unit)

    @Test
    fun `stock complet avec conversion kg vers g`() {
        val res = StockCheck.check(
            listOf(ing("Pilsner", 4.5, "kg"), ing("Cascade", 30.0, "g", "houblon")),
            listOf(inv("pilsner", 5000.0, "g"), inv("Cascade", 150.0, "g", "houblon")),
        )
        assertTrue(res.allOk)
        assertEquals(2, res.nOk)
        // correspondance insensible à la casse, besoin converti en grammes
        assertEquals(4500.0, res.lines[0].needed, 1e-9)
        assertEquals(5000.0, res.lines[0].available, 1e-9)
    }

    @Test
    fun `stock insuffisant et manquant`() {
        val res = StockCheck.check(
            listOf(ing("Pilsner", 4.5, "kg"), ing("Munich", 1.0, "kg")),
            listOf(inv("Pilsner", 2.0, "kg")),
        )
        assertFalse(res.allOk)
        assertEquals(StockCheck.Status.LOW, res.lines[0].status)
        assertEquals(StockCheck.Status.MISSING, res.lines[1].status)
        assertEquals(1, res.nLow)
        assertEquals(1, res.nMissing)
    }

    @Test
    fun `unites incompatibles`() {
        // Levure en sachets dans l'inventaire, en grammes dans la recette
        val res = StockCheck.check(
            listOf(ing("US-05", 11.5, "g", "levure")),
            listOf(inv("US-05", 3.0, "sachet", "levure")),
        )
        assertEquals(StockCheck.Status.UNIT_MISMATCH, res.lines[0].status)
        assertEquals(1, res.nMismatch)
    }

    @Test
    fun `ingredients de meme nom agreges`() {
        // Cascade à 60 min + Cascade à 5 min = 50 g requis, 40 g dispo
        val res = StockCheck.check(
            listOf(ing("Cascade", 30.0, "g", "houblon"), ing("Cascade", 20.0, "g", "houblon")),
            listOf(inv("Cascade", 40.0, "g", "houblon")),
        )
        assertEquals(1, res.lines.size)
        assertEquals(50.0, res.lines[0].needed, 1e-9)
        assertEquals(StockCheck.Status.LOW, res.lines[0].status)
    }

    @Test
    fun formatBase() {
        assertEquals("1,5 kg", StockCheck.formatBase(1500.0, "g"))
        assertEquals("500 g", StockCheck.formatBase(500.0, "g"))
        assertEquals("2 L", StockCheck.formatBase(2000.0, "ml"))
        assertEquals("3 sachet", StockCheck.formatBase(3.0, "sachet"))
    }

    @Test
    fun `parsing des ingredients de brouillon`() {
        // Chaîne JSON telle que stockée par le serveur (avec la clé interne _rid)
        val draft = Draft(
            id = 1, title = "NEIPA d'été",
            ingredients = """[
                {"name":"Citra","category":"houblon","quantity":50,"unit":"g","_rid":3},
                {"name":"Pilsner","category":"malt","quantity":4.2,"unit":"kg"}
            ]""",
        )
        val ings = draft.parsedIngredients()
        assertEquals(2, ings.size)
        assertEquals("Citra", ings[0].name)
        assertEquals(50.0, ings[0].quantity!!, 1e-9)
        // JSON invalide ou absent → liste vide, pas d'exception
        assertTrue(Draft(id = 2, title = "x", ingredients = "pas du json").parsedIngredients().isEmpty())
        assertTrue(Draft(id = 3, title = "x").parsedIngredients().isEmpty())
    }
}
