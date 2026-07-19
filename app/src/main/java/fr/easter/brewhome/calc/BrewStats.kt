package fr.easter.brewhome.calc

import fr.easter.brewhome.data.Beer
import fr.easter.brewhome.data.Brew
import fr.easter.brewhome.data.Recipe

/**
 * Agrégations pures pour l'écran Statistiques — portées de la page Stats du
 * site (bh-core.js) : saisonnalité, moyennes par année, top ingrédients via
 * les recettes des brassins, notes par style. Tout se calcule à partir des
 * données déjà chargées, aucune API supplémentaire.
 */
object BrewStats {

    /** Nombre de brassins par mois calendaire (indices 0..11) d'après brew_date "AAAA-MM-JJ". */
    fun byMonth(brews: List<Brew>): IntArray {
        val counts = IntArray(12)
        brews.forEach { b ->
            val m = b.brewDate?.drop(5)?.take(2)?.toIntOrNull()
            if (m != null && m in 1..12) counts[m - 1]++
        }
        return counts
    }

    /** Moyenne par année d'une valeur de brassin, années décroissantes. */
    fun avgByYear(brews: List<Brew>, value: (Brew) -> Double?): List<Pair<String, Double>> =
        perYear(brews, value).map { (y, vs) -> y to vs.average() }

    /** Somme par année d'une valeur de brassin, années décroissantes. */
    fun sumByYear(brews: List<Brew>, value: (Brew) -> Double?): List<Pair<String, Double>> =
        perYear(brews, value).map { (y, vs) -> y to vs.sum() }

    private fun perYear(brews: List<Brew>, value: (Brew) -> Double?): List<Pair<String, List<Double>>> =
        brews.mapNotNull { b ->
            val y = b.brewDate?.take(4) ?: return@mapNotNull null
            val v = value(b) ?: return@mapNotNull null
            y to v
        }
            .groupBy({ it.first }, { it.second })
            .toList()
            .sortedByDescending { it.first }

    /**
     * Poids total en grammes par ingrédient d'une catégorie, sur les recettes
     * des brassins donnés (une recette brassée deux fois compte deux fois).
     * Les unités non pondérales (pièce, sachet…) sont ignorées.
     */
    fun topByWeight(
        brews: List<Brew>,
        recipes: Map<Int, Recipe>,
        category: String,
        limit: Int = 6,
    ): List<Pair<String, Double>> {
        val names = HashMap<String, String>()
        val totals = HashMap<String, Double>()
        forEachIngredient(brews, recipes, category) { ing ->
            val grams = when (ing.unit.lowercase()) {
                "kg" -> ing.quantity * 1000
                "g" -> ing.quantity
                else -> return@forEachIngredient
            }
            val key = ing.name.trim().lowercase()
            names.putIfAbsent(key, ing.name.trim())
            totals.merge(key, grams, Double::plus)
        }
        return totals.entries.sortedByDescending { it.value }.take(limit)
            .map { names.getValue(it.key) to it.value }
    }

    /** Nombre de brassins utilisant chaque levure (au plus 1 par brassin). */
    fun topYeasts(
        brews: List<Brew>,
        recipes: Map<Int, Recipe>,
        limit: Int = 6,
    ): List<Pair<String, Int>> {
        val names = HashMap<String, String>()
        val counts = HashMap<String, Int>()
        brews.forEach { b ->
            val ings = b.recipeId?.let { recipes[it] }?.ingredients ?: return@forEach
            ings.filter { it.category.equals("levure", ignoreCase = true) }
                .map { it.name.trim() }
                .distinctBy { it.lowercase() }
                .forEach { name ->
                    val key = name.lowercase()
                    names.putIfAbsent(key, name)
                    counts.merge(key, 1, Int::plus)
                }
        }
        return counts.entries.sortedByDescending { it.value }.take(limit)
            .map { names.getValue(it.key) to it.value }
    }

    private inline fun forEachIngredient(
        brews: List<Brew>,
        recipes: Map<Int, Recipe>,
        category: String,
        action: (fr.easter.brewhome.data.RecipeIngredient) -> Unit,
    ) {
        brews.forEach { b ->
            b.recipeId?.let { recipes[it] }?.ingredients
                ?.filter { it.category.equals(category, ignoreCase = true) }
                ?.forEach(action)
        }
    }

    /** Note moyenne et effectif par style de bière, mieux notés d'abord. */
    fun ratingByType(beers: List<Beer>): List<Triple<String, Double, Int>> =
        beers.filter { (it.tasteRating ?: 0) > 0 }
            .groupBy { b -> b.type?.trim()?.takeIf { it.isNotEmpty() } ?: "?" }
            .map { (type, bs) -> Triple(type, bs.mapNotNull { it.tasteRating }.average(), bs.size) }
            .sortedByDescending { it.second }
}
