package fr.easter.brewhome.calc

import fr.easter.brewhome.data.InventoryItem
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.tanh

/**
 * Estimations d'une recette — port des formules de bh-recettes.js :
 * OG depuis le potentiel des malts (gu × efficacité), FG à 75 % d'atténuation,
 * IBU Tinseth (ou Rager), EBC/SRM par la formule Morey, plan d'eau
 * (empâtage/rinçage) et coût matières via les prix de l'inventaire.
 */
object RecipeEstimator {

    /** Ingrédient vu par l'estimateur (gu/ebc/alpha déjà résolus via le catalogue). */
    data class Ing(
        val name: String,
        val category: String,
        val quantity: Double,
        val unit: String,
        val hopTime: Int? = null,
        val hopType: String? = null,
        val alpha: Double? = null,
        val ebc: Double? = null,
        val gu: Double? = null,
        val inventoryItemId: Int? = null,
    )

    data class Estimates(
        val og: Double?,
        val fg: Double?,
        val abv: Double?,
        val ibu: Double?,
        val ebc: Double?,
        val srm: Double?,
    )

    data class Water(
        val mash: Double,
        val sparge: Double,
        val preboil: Double,
        val total: Double,
    )

    data class Cost(
        val byCategory: Map<String, Double>,
        val ingredients: Double,
        val water: Double?,
        val gas: Double,
        val elec: Double,
    ) {
        val total: Double get() = ingredients + (water ?: 0.0) + gas + elec
    }

    // Comme le site : tout ce qui n'est pas en kg est traité comme des grammes
    private fun kgOf(q: Double, unit: String): Double = if (unit == "kg") q else q / 1000

    fun estimates(
        ings: List<Ing>,
        volume: Double?,
        efficiencyPct: Double?,
        ibuFormula: String = "tinseth",
    ): Estimates {
        val vol = volume?.takeIf { it > 0 } ?: 20.0
        val eff = efficiencyPct?.takeIf { it > 0 } ?: 72.0

        var ogPoints = 0.0
        ings.filter { it.category == "malt" && it.quantity > 0 }.forEach { m ->
            val gu = m.gu ?: return@forEach
            ogPoints += kgOf(m.quantity, m.unit) * gu * (eff / 100)
        }
        val og = if (ogPoints > 0) 1 + ogPoints / vol / 1000 else null
        val fg = og?.let { 1 + (it - 1) * 0.25 }
        val abv = if (og != null && fg != null) (og - fg) * 131.25 else null

        val wortOG = og ?: 1.050
        var ibuTotal = 0.0
        ings.filter { it.category == "houblon" && it.quantity > 0 }.forEach { h ->
            val alpha = h.alpha ?: return@forEach
            val ht = h.hopType ?: "ebullition"
            if (ht == "dryhop") return@forEach
            val mins = if (ht == "whirlpool") 15.0 else (h.hopTime ?: 60).toDouble()
            val g = if (h.unit == "kg") h.quantity * 1000 else h.quantity
            ibuTotal += if (ibuFormula == "rager") {
                val util = 18.11 + 13.86 * tanh((mins - 31.32) / 18.27)
                val adj = if (wortOG > 1.050) (wortOG - 1.050) / 0.2 else 0.0
                (g * (util / 100) * (alpha / 100) * 1000) / (vol * (1 + adj))
            } else {
                1.65 * 0.000125.pow(wortOG - 1) * (1 - exp(-0.04 * mins)) / 4.15 *
                    (alpha / 100) * g * 1000 / vol
            }
        }

        var mcu = 0.0
        ings.filter { it.category == "malt" && it.quantity > 0 }.forEach { m ->
            val e = m.ebc ?: return@forEach
            val lovibond = (e / 1.97 + 0.76) / 1.3546
            mcu += (kgOf(m.quantity, m.unit) * 2.20462 * lovibond) / (vol * 0.264172)
        }
        val srm = if (mcu > 0) 1.4922 * mcu.pow(0.6859) else null

        return Estimates(og, fg, abv, ibuTotal.takeIf { it > 0 }, srm?.let { it * 1.97 }, srm)
    }

    /** Poids total de grain (kg) des malts. */
    fun grainKg(ings: List<Ing>): Double =
        ings.filter { it.category == "malt" }.sumOf { kgOf(it.quantity, it.unit) }

    /**
     * Plan d'eau : empâtage au ratio voulu mais au minimum 55 % du total (pour
     * garantir empâtage ≥ rinçage), rinçage en complément, pré-ébullition
     * déduite de l'eau réelle quand un volume manuel est saisi.
     */
    fun water(
        volume: Double?,
        boilTimeMin: Double?,
        mashRatio: Double?,
        evapRatePerHour: Double?,
        absorptionPerKg: Double?,
        grainKg: Double,
        mashOverride: Double? = null,
        spargeOverride: Double? = null,
    ): Water? {
        if (grainKg <= 0) return null
        val vol = volume?.takeIf { it > 0 } ?: 20.0
        val boil = boilTimeMin?.takeIf { it > 0 } ?: 60.0
        val ratio = mashRatio?.takeIf { it > 0 } ?: 3.0
        val evap = evapRatePerHour?.takeIf { it > 0 } ?: 3.0
        val abs = absorptionPerKg?.takeIf { it > 0 } ?: 0.8

        val preboil = vol + evap * (boil / 60)
        val grainAbs = grainKg * abs
        val autoTotal = preboil + grainAbs
        val autoMash = max(grainKg * ratio, autoTotal * 0.55)

        val mash = mashOverride?.takeIf { it > 0 } ?: autoMash
        val sparge = spargeOverride?.takeIf { it > 0 } ?: max(0.0, autoTotal - mash)
        val total = mash + sparge
        val manual = (mashOverride ?: 0.0) > 0 || (spargeOverride ?: 0.0) > 0
        val effectivePreboil = if (manual) max(0.0, total - grainAbs) else preboil
        return Water(mash, sparge, effectivePreboil, total)
    }

    // Unité canonique dans laquelle le prix de l'inventaire est saisi
    private val canonicalPriceUnit = mapOf("malt" to "kg", "houblon" to "g", "levure" to "sachet")

    private fun convertQtyForCost(qty: Double, from: String, to: String): Double? = when {
        from == to -> qty
        from == "g" && to == "kg" -> qty / 1000
        from == "kg" && to == "g" -> qty * 1000
        (from == "ml" || from == "mL") && to == "L" -> qty / 1000
        from == "L" && (to == "ml" || to == "mL") -> qty * 1000
        else -> null
    }

    /**
     * Coût matières : chaque ingrédient est valorisé au prix de l'article
     * d'inventaire lié (par id, sinon par nom+catégorie). Retourne null si
     * aucun coût n'est calculable.
     */
    fun cost(
        ings: List<Ing>,
        inventory: List<InventoryItem>,
        waterPricePerL: Double?,
        totalWaterL: Double?,
        gasPerBrew: Double,
        elecPerBrew: Double,
    ): Cost? {
        val byCat = linkedMapOf<String, Double>()
        var ingTotal = 0.0
        var has = false
        ings.forEach { ing ->
            val inv = ing.inventoryItemId?.let { id -> inventory.find { it.id == id } }
                ?: inventory.find {
                    it.name.equals(ing.name, ignoreCase = true) && it.category == ing.category
                }
            val price = inv?.pricePerUnit ?: return@forEach
            val priceUnit = canonicalPriceUnit[ing.category] ?: inv.unit
            val converted = convertQtyForCost(ing.quantity, ing.unit, priceUnit) ?: return@forEach
            val c = converted * price
            ingTotal += c
            byCat.merge(ing.category, c, Double::plus)
            has = true
        }
        val water = if (waterPricePerL != null && waterPricePerL > 0 &&
            totalWaterL != null && totalWaterL > 0
        ) waterPricePerL * totalWaterL else null
        if (water != null || gasPerBrew > 0 || elecPerBrew > 0) has = true
        if (!has) return null
        return Cost(byCat, ingTotal, water, gasPerBrew, elecPerBrew)
    }
}
