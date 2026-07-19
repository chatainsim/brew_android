package fr.easter.brewhome

import fr.easter.brewhome.calc.RecipeEstimator
import fr.easter.brewhome.data.InventoryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecipeEstimatorTest {

    private fun malt(name: String, qty: Double, unit: String = "kg", gu: Double? = null, ebc: Double? = null) =
        RecipeEstimator.Ing(name, "malt", qty, unit, gu = gu, ebc = ebc)

    private fun hop(qty: Double, alpha: Double, time: Int?, type: String? = null) =
        RecipeEstimator.Ing("Houblon", "houblon", qty, "g", hopTime = time, hopType = type, alpha = alpha)

    @Test
    fun `og fg abv suivent la formule du site`() {
        // 5 kg à 384 pts/kg/L, 72 %, 20 L → OG 1 + 5*384*0.72/20/1000 = 1.06912
        val est = RecipeEstimator.estimates(listOf(malt("Pilsen", 5.0, gu = 384.0)), 20.0, 72.0)
        assertEquals(1.06912, est.og!!, 1e-9)
        // FG = 1 + (OG-1)*0.25 ; ABV = (OG-FG)*131.25
        assertEquals(1.01728, est.fg!!, 1e-9)
        assertEquals((est.og!! - est.fg!!) * 131.25, est.abv!!, 1e-9)
    }

    @Test
    fun `ibu tinseth ignore le dry hop et fixe le whirlpool a 15 min`() {
        val ings = listOf(
            malt("Pilsen", 5.0, gu = 384.0),
            hop(50.0, 5.0, 60),
            hop(100.0, 10.0, null, type = "dryhop"),
        )
        val est = RecipeEstimator.estimates(ings, 20.0, 72.0)
        // Tinseth : 1.65×0.000125^(OG-1) × (1-e^(-0.04×60))/4.15 × 0.05 × 50 × 1000/20
        val og = est.og!!
        val expected = 1.65 * Math.pow(0.000125, og - 1) *
            (1 - Math.exp(-0.04 * 60)) / 4.15 * 0.05 * 50 * 1000 / 20
        assertEquals(expected, est.ibu!!, 1e-9)

        // Whirlpool compté 15 min quel que soit hop_time
        val wp = RecipeEstimator.estimates(
            listOf(malt("Pilsen", 5.0, gu = 384.0), hop(50.0, 5.0, 60, type = "whirlpool")),
            20.0, 72.0,
        )
        val expectedWp = 1.65 * Math.pow(0.000125, og - 1) *
            (1 - Math.exp(-0.04 * 15)) / 4.15 * 0.05 * 50 * 1000 / 20
        assertEquals(expectedWp, wp.ibu!!, 1e-9)
    }

    @Test
    fun `ebc utilise la formule Morey`() {
        val est = RecipeEstimator.estimates(listOf(malt("Pilsen", 5.0, ebc = 4.0)), 20.0, 72.0)
        val lovibond = (4.0 / 1.97 + 0.76) / 1.3546
        val mcu = (5.0 * 2.20462 * lovibond) / (20.0 * 0.264172)
        val srm = 1.4922 * Math.pow(mcu, 0.6859)
        assertEquals(srm, est.srm!!, 1e-9)
        assertEquals(srm * 1.97, est.ebc!!, 1e-9)
    }

    @Test
    fun `plan d'eau respecte le minimum de 55 pourcent a l'empatage`() {
        // 6.54 kg, ratio 3.5, 20 L, ébu 60 min, évap 3, absorption 0.8
        val w = RecipeEstimator.water(20.0, 60.0, 3.5, 3.0, 0.8, 6.54)!!
        assertEquals(23.0, w.preboil, 1e-9)
        assertEquals(6.54 * 3.5, w.mash, 1e-9) // 22.89 > min 55 %
        assertEquals(23.0 + 6.54 * 0.8 - w.mash, w.sparge, 1e-9)
        // Petit empâtement : le minimum 55 % s'applique
        val w2 = RecipeEstimator.water(20.0, 60.0, 2.0, 3.0, 0.8, 3.0)!!
        assertEquals((23.0 + 2.4) * 0.55, w2.mash, 1e-9)
        assertNull(RecipeEstimator.water(20.0, 60.0, 3.0, 3.0, 0.8, 0.0))
    }

    @Test
    fun `cout convertit vers l'unite canonique du prix`() {
        val inventory = listOf(
            InventoryItem(id = 1, name = "Pilsen", category = "malt", unit = "kg", pricePerUnit = 2.0),
            InventoryItem(id = 2, name = "Citra", category = "houblon", unit = "g", pricePerUnit = 0.05),
            InventoryItem(id = 3, name = "S-33", category = "levure", unit = "sachet", pricePerUnit = 4.5),
        )
        val ings = listOf(
            RecipeEstimator.Ing("Pilsen", "malt", 500.0, "g"), // 0.5 kg × 2 € = 1 €
            RecipeEstimator.Ing("Citra", "houblon", 50.0, "g"), // 50 g × 0.05 = 2.5 €
            RecipeEstimator.Ing("S-33", "levure", 2.0, "sachet"), // 2 × 4.5 = 9 €
            RecipeEstimator.Ing("Mystère", "autre", 1.0, "pièce"), // pas de prix → ignoré
        )
        val cost = RecipeEstimator.cost(ings, inventory, 0.03, 29.5, 3.5, 0.2)!!
        assertEquals(1.0, cost.byCategory["malt"]!!, 1e-9)
        assertEquals(2.5, cost.byCategory["houblon"]!!, 1e-9)
        assertEquals(9.0, cost.byCategory["levure"]!!, 1e-9)
        assertEquals(12.5, cost.ingredients, 1e-9)
        assertEquals(0.03 * 29.5, cost.water!!, 1e-9)
        assertEquals(12.5 + 0.03 * 29.5 + 3.5 + 0.2, cost.total, 1e-9)
    }
}
