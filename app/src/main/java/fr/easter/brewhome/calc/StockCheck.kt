package fr.easter.brewhome.calc

import fr.easter.brewhome.data.InventoryItem
import fr.easter.brewhome.data.RecipeIngredient

/**
 * Vérification de la disponibilité du stock pour une recette — même logique
 * que checkRecipeStock() dans bh-recettes.js du serveur : quantités ramenées
 * en unité de base (g, ml, ou l'unité telle quelle), correspondance par nom
 * insensible à la casse, ingrédients de même nom agrégés.
 */
object StockCheck {

    enum class Status { OK, LOW, MISSING, UNIT_MISMATCH }

    data class Line(
        val name: String,
        val category: String,
        val needed: Double,
        val base: String,
        val available: Double,
        val status: Status,
    )

    data class Result(val lines: List<Line>) {
        val allOk: Boolean get() = lines.isNotEmpty() && lines.all { it.status == Status.OK }
        val nOk: Int get() = lines.count { it.status == Status.OK }
        val nLow: Int get() = lines.count { it.status == Status.LOW }
        val nMissing: Int get() = lines.count { it.status == Status.MISSING }
        val nMismatch: Int get() = lines.count { it.status == Status.UNIT_MISMATCH }
    }

    private fun toBase(qty: Double, unit: String): Pair<Double, String> = when (unit) {
        "kg" -> qty * 1000 to "g"
        "g" -> qty to "g"
        "L", "l" -> qty * 1000 to "ml"
        "ml", "mL" -> qty to "ml"
        else -> qty to unit
    }

    fun check(ingredients: List<RecipeIngredient>, inventory: List<InventoryItem>): Result {
        val invMap = mutableMapOf<String, MutableMap<String, Double>>()
        inventory.forEach { item ->
            val key = item.name.trim().lowercase()
            val (v, b) = toBase(item.quantity, item.unit)
            invMap.getOrPut(key) { mutableMapOf() }.merge(b, v, Double::plus)
        }

        class Agg(val name: String, val category: String, val base: String) {
            var needed = 0.0
        }
        val agg = LinkedHashMap<String, Agg>()
        ingredients.forEach { ing ->
            val key = ing.name.trim().lowercase()
            val (v, b) = toBase(ing.quantity, ing.unit)
            agg.getOrPut(key) { Agg(ing.name, ing.category, b) }.needed += v
        }

        val lines = agg.map { (key, a) ->
            val inv = invMap[key]
            val available = inv?.get(a.base) ?: 0.0
            val status = when {
                inv == null -> Status.MISSING
                available == 0.0 -> Status.UNIT_MISMATCH
                available >= a.needed -> Status.OK
                else -> Status.LOW
            }
            Line(a.name, a.category, a.needed, a.base, available, status)
        }
        return Result(lines)
    }

    /** Article à acheter pour compléter une recette. */
    data class Need(val name: String, val category: String, val quantity: Double, val unit: String)

    private fun fromBase(v: Double, base: String): Pair<Double, String> = when {
        base == "g" && v >= 1000 -> v / 1000 to "kg"
        base == "ml" && v >= 1000 -> v / 1000 to "L"
        else -> v to base
    }

    /**
     * Articles à ajouter à la liste de courses : ingrédients manquants et
     * compléments de stock bas, hors noms déjà présents dans la liste
     * (`alreadyListed` : noms en minuscules).
     */
    fun needs(result: Result, alreadyListed: Set<String> = emptySet()): List<Need> =
        result.lines
            .filter { it.status == Status.MISSING || it.status == Status.LOW }
            .filterNot { it.name.trim().lowercase() in alreadyListed }
            .map { line ->
                val (q, u) = fromBase(line.needed - line.available, line.base)
                Need(line.name, line.category, q, u)
            }

    /** "1500 g" → "1,5 kg", "500 ml" → "500 ml", etc. */
    fun formatBase(v: Double, base: String): String {
        fun num(x: Double): String =
            if (x % 1.0 == 0.0) x.toInt().toString()
            else String.format(java.util.Locale.FRANCE, "%.3f", x).trimEnd('0').trimEnd(',')
        return when {
            base == "g" && v >= 1000 -> "${num(v / 1000)} kg"
            base == "ml" && v >= 1000 -> "${num(v / 1000)} L"
            else -> "${num(v)} $base"
        }
    }
}
