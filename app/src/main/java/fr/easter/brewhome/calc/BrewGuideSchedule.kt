package fr.easter.brewhome.calc

import fr.easter.brewhome.data.RecipeIngredient

/**
 * Planning de houblonnage pendant l'ébullition, pour le guide de brassage pas
 * à pas : à quel instant écoulé (depuis le début du minuteur d'ébullition,
 * en millisecondes) chaque houblon doit être ajouté.
 *
 * Mêmes trois types que ceux proposés à la saisie (RecipeEditScreen.kt,
 * `hopTypes`) : "ebullition" (par défaut) ajouté à T = durée - hopTime,
 * "whirlpool" ajouté à la toute fin (flamme éteinte), "dryhop" exclu — ajouté
 * après refroidissement/ensemencement, une autre étape du guide.
 */
object BrewGuideSchedule {

    data class HopAddition(val ingredientId: Int, val name: String, val elapsedMs: Long)

    /** [boilMinutes] = durée totale de l'ébullition, en minutes. */
    fun boilSchedule(ingredients: List<RecipeIngredient>, boilMinutes: Int): List<HopAddition> {
        val boilMs = boilMinutes.coerceAtLeast(0) * 60_000L
        return ingredients
            .filter { it.category == "houblon" }
            .mapNotNull { hop ->
                val type = hop.hopType ?: "ebullition"
                if (type == "dryhop") return@mapNotNull null
                val elapsed = if (type == "whirlpool") {
                    boilMs
                } else {
                    boilMs - (hop.hopTime ?: 60) * 60_000L
                }
                HopAddition(hop.id, hop.name, elapsed.coerceIn(0L, boilMs))
            }
            .sortedBy { it.elapsedMs }
    }
}
