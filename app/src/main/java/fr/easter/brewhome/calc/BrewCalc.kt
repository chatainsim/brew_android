package fr.easter.brewhome.calc

import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Formules de brassage, identiques à celles de la page « Outils » du serveur
 * BrewHome (page_outils.html / script_inventaire.html / script_recettes.html).
 */
object BrewCalc {

    // ── ABV ───────────────────────────────────────────────────────────────────

    /** ABV en % à partir des densités initiale et finale (SG). */
    fun abv(og: Double, fg: Double): Double? {
        if (og <= 1 || fg <= 0 || fg >= og) return null
        return (og - fg) * 131.25
    }

    /** Atténuation apparente en %. */
    fun attenuation(og: Double, fg: Double): Double? {
        if (og <= 1 || fg <= 0 || fg >= og) return null
        return (og - fg) / (og - 1) * 100
    }

    // ── Correction densimètre / température ───────────────────────────────────

    private fun hydroPoly(tF: Double): Double =
        1.00130346 - 0.000134722124 * tF + 0.00000204052596 * tF * tF -
            0.00000000232820948 * tF * tF * tF

    /**
     * Densité corrigée selon la température de mesure (formule Zymurgy/BeerSmith).
     * Températures en °C.
     */
    fun hydroCorrection(sg: Double, measuredC: Double, calibrationC: Double = 20.0): Double {
        val tF = measuredC * 9 / 5 + 32
        val tfCal = calibrationC * 9 / 5 + 32
        return sg * (hydroPoly(tF) / hydroPoly(tfCal))
    }

    // ── Correction réfractomètre (Novotný) ────────────────────────────────────

    /** SG → Brix (approximation standard). */
    fun sgToBrix(sg: Double): Double = (sg - 1) / 0.004

    /** Brix → SG (approximation standard). */
    fun brixToSg(brix: Double): Double = 1 + brix * 0.004

    data class RefractoResult(val fg: Double, val abv: Double?)

    /**
     * DF corrigée en cours de fermentation à partir des lectures réfractomètre
     * (formule Novotný). [brixOg] et [brixCurrent] en Brix bruts, [wcf] facteur
     * de correction du réfractomètre.
     */
    fun refractoCorrection(brixOg: Double, brixCurrent: Double, wcf: Double = 1.04): RefractoResult? {
        val b1 = brixOg / wcf
        val b2 = brixCurrent / wcf
        val fg = 1.0000 - 0.0044993 * b1 + 0.011774 * b2 +
            0.00027581 * b1 * b1 - 0.0012717 * b2 * b2 -
            0.0000072800 * b1 * b1 * b1 + 0.000063293 * b2 * b2 * b2
        if (fg <= 0.98 || fg >= 1.15) return null
        val ogSg = brixToSg(b1)
        val abv = ((ogSg - fg) * 131.25).takeIf { it > 0 && it < 30 }
        return RefractoResult(fg, abv)
    }

    // ── Température d'empâtage (strike water) ─────────────────────────────────

    /** Capacité thermique du malt (cal/g/°C), valeur standard Palmer/BIAB. */
    private const val CP_GRAIN = 0.38

    data class StrikeResult(val strikeTempC: Double, val waterLiters: Double)

    /**
     * Température de l'eau d'empâtage pour atteindre [mashTempC] avec des malts
     * à [grainTempC] et un ratio eau/malt [ratioLPerKg] (L/kg).
     */
    fun strikeWater(
        grainKg: Double,
        grainTempC: Double,
        mashTempC: Double,
        ratioLPerKg: Double,
    ): StrikeResult? {
        if (grainKg <= 0 || ratioLPerKg <= 0) return null
        val tStrike = mashTempC + (CP_GRAIN / ratioLPerKg) * (mashTempC - grainTempC)
        return StrikeResult(tStrike, ratioLPerKg * grainKg)
    }

    // ── Bouteilles ────────────────────────────────────────────────────────────

    data class BottlesResult(val n33: Int, val n75: Int, val remainderMl: Int)

    /** Répartit [volumeL] : tout en 33 cl par défaut. */
    fun bottlesFromVolume(volumeL: Double): BottlesResult {
        val ml = (volumeL * 1000).roundToInt()
        val n33 = floor(ml / 330.0).toInt()
        return BottlesResult(n33, 0, ml - n33 * 330)
    }

    /** Recalcule les 75 cl après modification du nombre de 33 cl. */
    fun bottlesAfter33(volumeL: Double, n33: Int): BottlesResult {
        val ml = (volumeL * 1000).roundToInt()
        val n = max(0, n33)
        val rem = ml - n * 330
        val n75 = if (rem >= 750) floor(rem / 750.0).toInt() else 0
        return BottlesResult(n, n75, ml - n * 330 - n75 * 750)
    }

    /** Recalcule les 33 cl après modification du nombre de 75 cl. */
    fun bottlesAfter75(volumeL: Double, n75: Int): BottlesResult {
        val ml = (volumeL * 1000).roundToInt()
        val n = max(0, n75)
        val rem = ml - n * 750
        val n33 = if (rem >= 330) floor(rem / 330.0).toInt() else 0
        return BottlesResult(n33, n, ml - n33 * 330 - n * 750)
    }

    // ── Primage ───────────────────────────────────────────────────────────────

    /** CO₂ résiduel (volumes) selon la température de fermentation (Brewer's Friend). */
    fun primingResidualCo2(tempC: Double): Double {
        val tempF = tempC * 9 / 5 + 32
        return 3.0378 - 0.050062 * tempF + 0.00026555 * tempF * tempF
    }

    data class PrimingResult(
        val gramsTotal: Double,
        val residualCo2: Double,
        val co2ToAdd: Double,
        val per33cl: Double,
        val per50cl: Double,
        val per75cl: Double,
    )

    /**
     * Grammes de sucre de primage pour [volumeL] à [tempC] visant [targetCo2]
     * volumes. [sugarFactor] : g/L par volume de CO₂ (3,97 sucrose, 4,21 dextrose
     * anhydre, 4,64 dextrose monohydraté, 6,14 DME, 8,57 miel).
     */
    fun priming(volumeL: Double, tempC: Double, targetCo2: Double, sugarFactor: Double): PrimingResult {
        val residual = primingResidualCo2(tempC)
        val toAdd = max(0.0, targetCo2 - residual)
        val grams = toAdd * volumeL * sugarFactor
        return PrimingResult(
            gramsTotal = grams,
            residualCo2 = residual,
            co2ToAdd = toAdd,
            per33cl = grams * 0.33 / volumeL,
            per50cl = grams * 0.50 / volumeL,
            per75cl = grams * 0.75 / volumeL,
        )
    }

    // ── Starter de levure ─────────────────────────────────────────────────────

    /** Viabilité (0–1) d'un paquet de levure liquide selon son âge (Mr. Malty). */
    fun yeastViability(ageDays: Int): Double {
        val ageMonths = ageDays / 30.0
        return max(0.05, 0.97 * exp(-0.0684 * ageMonths))
    }

    /** SG → degrés Plato (approximation 259 × (1 − 1/OG)). */
    fun sgToPlato(og: Double): Double = 259 * (1 - 1 / og)

    data class StarterStep(val volumeL: Double, val dmeGrams: Double)

    data class StarterResult(
        val viability: Double,
        val viableCells: Double,
        val requiredCells: Double,
        /** Vide si le paquet suffit sans starter. */
        val steps: List<StarterStep>,
        val starterGravity: Double,
    )

    /**
     * Calcul de starter (modèle simple type Mr. Malty) : [pkgCells] milliards de
     * cellules à la fabrication, [pitchRate] en M cellules/mL/°P (0,75 ale,
     * 1,0 haute densité, 1,5 lager), croissance ×1,4 avec agitation.
     */
    fun starter(
        ageDays: Int,
        pkgCells: Double,
        volumeL: Double,
        og: Double,
        pitchRate: Double,
        stirPlate: Boolean,
        twoSteps: Boolean,
    ): StarterResult {
        val viability = yeastViability(ageDays)
        val viableCells = pkgCells * viability
        val requiredCells = pitchRate * volumeL * sgToPlato(og)
        val growthFactor = if (stirPlate) 1.4 else 1.0
        val dmeRatio = 100.0 // g/L de DME → densité ≈ 1.037
        val starterGravity = 1 + dmeRatio * 0.00037

        val cellsNeeded = requiredCells - viableCells
        if (cellsNeeded <= 0) {
            return StarterResult(viability, viableCells, requiredCells, emptyList(), starterGravity)
        }

        val dmeTotalG = cellsNeeded / growthFactor
        val volTotalL = dmeTotalG / dmeRatio

        val steps = if (!twoSteps) {
            listOf(StarterStep(volTotalL, dmeTotalG))
        } else {
            val vol1 = max(0.5, (volTotalL / 3 * 10).roundToInt() / 10.0)
            val vol2 = ((volTotalL - vol1) * 100).roundToInt() / 100.0
            if (vol2 < 0.2) listOf(StarterStep(vol1, vol1 * dmeRatio))
            else listOf(StarterStep(vol1, vol1 * dmeRatio), StarterStep(vol2, vol2 * dmeRatio))
        }
        return StarterResult(viability, viableCells, requiredCells, steps, starterGravity)
    }
}
