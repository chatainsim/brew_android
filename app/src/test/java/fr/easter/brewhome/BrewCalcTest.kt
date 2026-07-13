package fr.easter.brewhome

import fr.easter.brewhome.calc.BrewCalc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vérifie que les formules Kotlin donnent les mêmes résultats que les
 * calculateurs JavaScript de la page « Outils » du serveur BrewHome.
 */
class BrewCalcTest {

    @Test
    fun `abv et attenuation`() {
        // 1.050 → 1.010 : (0.040) × 131.25 = 5.25 %
        assertEquals(5.25, BrewCalc.abv(1.050, 1.010)!!, 1e-9)
        assertEquals(80.0, BrewCalc.attenuation(1.050, 1.010)!!, 1e-9)
        // valeurs invalides
        assertNull(BrewCalc.abv(1.010, 1.050))
        assertNull(BrewCalc.abv(1.0, 1.010))
    }

    @Test
    fun `correction densimetre`() {
        // Lecture à la température d'étalonnage : aucune correction
        assertEquals(1.050, BrewCalc.hydroCorrection(1.050, 20.0, 20.0), 1e-9)
        // Moût plus chaud que l'étalonnage : densité corrigée plus haute
        val corrected = BrewCalc.hydroCorrection(1.050, 30.0, 20.0)
        assertTrue(corrected > 1.050)
        assertEquals(1.0524, corrected, 0.0005)
    }

    @Test
    fun `correction refractometre novotny`() {
        // DI 12 Brix (≈1.048), lecture 5 Brix, WCF 1.04
        val res = BrewCalc.refractoCorrection(12.0, 5.0, 1.04)
        assertNotNull(res)
        // FG attendue autour de 1.008–1.012, ABV plausible
        assertTrue(res!!.fg in 1.000..1.020)
        assertNotNull(res.abv)
        assertTrue(res.abv!! in 3.0..7.0)
        // conversions SG ↔ Brix
        assertEquals(12.5, BrewCalc.sgToBrix(1.050), 1e-9)
        assertEquals(1.050, BrewCalc.brixToSg(12.5), 1e-9)
    }

    @Test
    fun `temperature empatage`() {
        // 5 kg à 20 °C, cible 65 °C, ratio 3 L/kg
        // T = 65 + (0.38/3) × (65−20) = 70.7 °C ; volume 15 L
        val res = BrewCalc.strikeWater(5.0, 20.0, 65.0, 3.0)!!
        assertEquals(70.7, res.strikeTempC, 0.05)
        assertEquals(15.0, res.waterLiters, 1e-9)
        assertNull(BrewCalc.strikeWater(0.0, 20.0, 65.0, 3.0))
    }

    @Test
    fun bouteilles() {
        // 20 L → 60 × 33 cl, reste 200 mL
        val all33 = BrewCalc.bottlesFromVolume(20.0)
        assertEquals(60, all33.n33)
        assertEquals(0, all33.n75)
        assertEquals(200, all33.remainderMl)
        // 20 L avec 10 × 33 cl → reste 16.7 L → 22 × 75 cl
        val mix = BrewCalc.bottlesAfter33(20.0, 10)
        assertEquals(22, mix.n75)
        // 20 L avec 20 × 75 cl → reste 5 L → 15 × 33 cl
        val mix75 = BrewCalc.bottlesAfter75(20.0, 20)
        assertEquals(15, mix75.n33)
    }

    @Test
    fun primage() {
        // Brewer's Friend : à 20 °C le CO₂ résiduel ≈ 0.85–0.90 vol
        val residual = BrewCalc.primingResidualCo2(20.0)
        assertEquals(0.87, residual, 0.03)
        // 20 L, 20 °C, cible 2.5 vol, dextrose monohydraté (4.64)
        val res = BrewCalc.priming(20.0, 20.0, 2.5, 4.64)
        assertTrue(res.co2ToAdd > 0)
        assertEquals((2.5 - residual) * 20.0 * 4.64, res.gramsTotal, 1e-9)
        // ≈ 150 g au total, ≈ 2.5 g par 33 cl
        assertEquals(151.0, res.gramsTotal, 5.0)
        assertEquals(res.gramsTotal * 0.33 / 20.0, res.per33cl, 1e-9)
        // cible sous le résiduel : rien à ajouter
        assertEquals(0.0, BrewCalc.priming(20.0, 20.0, 0.5, 4.64).gramsTotal, 1e-9)
    }

    @Test
    fun `starter viabilite et taille`() {
        // Paquet neuf : viabilité 97 %
        assertEquals(0.97, BrewCalc.yeastViability(0), 1e-9)
        // 3 mois : 0.97 × e^(−0.0684 × 3) ≈ 0.79
        assertEquals(0.79, BrewCalc.yeastViability(90), 0.01)
        // plancher à 5 %
        assertEquals(0.05, BrewCalc.yeastViability(365 * 5), 1e-9)
        // Plato : 1.050 → ≈ 12.33 °P
        assertEquals(12.33, BrewCalc.sgToPlato(1.050), 0.01)

        // Paquet neuf de 100 Mds, 20 L à 1.050, ale : requis ≈ 185 Mds → starter
        val res = BrewCalc.starter(0, 100.0, 20.0, 1.050, 0.75, stirPlate = true, twoSteps = false)
        assertEquals(185.0, res.requiredCells, 1.0)
        assertEquals(1, res.steps.size)
        // DME = cellules manquantes / 1.4 ; volume = DME / 100
        val step = res.steps[0]
        assertEquals((res.requiredCells - res.viableCells) / 1.4, step.dmeGrams, 1e-9)
        assertEquals(step.dmeGrams / 100.0, step.volumeL, 1e-9)
        assertEquals(1.037, res.starterGravity, 1e-9)

        // Petit brassin : le paquet suffit, pas de starter
        val ok = BrewCalc.starter(0, 100.0, 10.0, 1.040, 0.75, stirPlate = true, twoSteps = false)
        assertTrue(ok.steps.isEmpty())

        // Gros besoin en 2 étapes
        val two = BrewCalc.starter(180, 100.0, 25.0, 1.080, 1.5, stirPlate = true, twoSteps = true)
        assertEquals(2, two.steps.size)
    }
}
