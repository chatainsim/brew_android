package fr.easter.brewhome

import fr.easter.brewhome.calc.BrewStats
import fr.easter.brewhome.data.Beer
import fr.easter.brewhome.data.Brew
import fr.easter.brewhome.data.Recipe
import fr.easter.brewhome.data.RecipeIngredient
import org.junit.Assert.assertEquals
import org.junit.Test

class BrewStatsTest {

    private fun brew(id: Int, date: String?, recipeId: Int? = null, abv: Double? = null, cost: Double? = null) =
        Brew(id = id, name = "B$id", brewDate = date, recipeId = recipeId, abv = abv, costSnapshot = cost)

    private fun ing(name: String, cat: String, qty: Double, unit: String) =
        RecipeIngredient(id = 0, name = name, category = cat, quantity = qty, unit = unit)

    @Test
    fun `byMonth compte les brassins par mois calendaire toutes annees confondues`() {
        val counts = BrewStats.byMonth(
            listOf(
                brew(1, "2025-03-10"), brew(2, "2026-03-02"),
                brew(3, "2026-12-25"), brew(4, null), brew(5, "n'importe quoi"),
            ),
        )
        assertEquals(2, counts[2])
        assertEquals(1, counts[11])
        assertEquals(3, counts.sum())
    }

    @Test
    fun `avgByYear et sumByYear regroupent par annee decroissante en ignorant les nulls`() {
        val brews = listOf(
            brew(1, "2025-01-01", abv = 4.0, cost = 10.0),
            brew(2, "2025-06-01", abv = 6.0, cost = 20.0),
            brew(3, "2026-01-01", abv = 5.0),
            brew(4, null, abv = 9.0, cost = 99.0),
        )
        assertEquals(listOf("2026" to 5.0, "2025" to 5.0), BrewStats.avgByYear(brews) { it.abv })
        assertEquals(listOf("2025" to 30.0), BrewStats.sumByYear(brews) { it.costSnapshot }.filter { it.second > 0 })
    }

    @Test
    fun `topByWeight convertit les kg en grammes et ignore les unites non ponderales`() {
        val recipes = mapOf(
            1 to Recipe(
                id = 1, name = "R1",
                ingredients = listOf(
                    ing("Pilsen", "malt", 4.0, "kg"),
                    ing("Munich", "malt", 500.0, "g"),
                    ing("Mystère", "malt", 2.0, "pièce"),
                    ing("Citra", "houblon", 50.0, "g"),
                ),
            ),
        )
        // Recette brassée deux fois : les quantités comptent double
        val brews = listOf(brew(1, "2026-01-01", recipeId = 1), brew(2, "2026-02-01", recipeId = 1))
        val malts = BrewStats.topByWeight(brews, recipes, "malt")
        assertEquals(listOf("Pilsen" to 8000.0, "Munich" to 1000.0), malts)
        assertEquals(listOf("Citra" to 100.0), BrewStats.topByWeight(brews, recipes, "houblon"))
    }

    @Test
    fun `topYeasts compte au plus une fois la meme levure par brassin`() {
        val recipes = mapOf(
            1 to Recipe(
                id = 1, name = "R1",
                ingredients = listOf(
                    ing("US-05", "levure", 1.0, "sachet"),
                    ing("us-05", "levure", 1.0, "sachet"),
                ),
            ),
            2 to Recipe(id = 2, name = "R2", ingredients = listOf(ing("US-05", "levure", 1.0, "sachet"))),
        )
        val brews = listOf(brew(1, "2026-01-01", recipeId = 1), brew(2, "2026-02-01", recipeId = 2))
        assertEquals(listOf("US-05" to 2), BrewStats.topYeasts(brews, recipes))
    }

    @Test
    fun `ratingByType moyenne les notes par style et ecarte les bieres non notees`() {
        val beers = listOf(
            Beer(id = 1, name = "A", type = "IPA", tasteRating = 4),
            Beer(id = 2, name = "B", type = "IPA", tasteRating = 5),
            Beer(id = 3, name = "C", type = "Stout", tasteRating = 3),
            Beer(id = 4, name = "D", type = "Stout"),
            Beer(id = 5, name = "E", type = null, tasteRating = 2),
        )
        val result = BrewStats.ratingByType(beers)
        assertEquals(
            listOf(
                Triple("IPA", 4.5, 2),
                Triple("Stout", 3.0, 1),
                Triple("?", 2.0, 1),
            ),
            result,
        )
    }
}
