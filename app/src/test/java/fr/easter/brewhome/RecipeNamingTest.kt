package fr.easter.brewhome

import fr.easter.brewhome.calc.RecipeNaming
import org.junit.Assert.assertEquals
import org.junit.Test

class RecipeNamingTest {

    @Test
    fun `premiere copie devient v2`() {
        assertEquals("TripHops v2", RecipeNaming.duplicateName("TripHops", listOf("TripHops")))
    }

    @Test
    fun `repart de la version max existante`() {
        val names = listOf("TripHops", "TripHops v2", "TripHops v4", "Autre")
        assertEquals("TripHops v5", RecipeNaming.duplicateName("TripHops v2", names))
    }

    @Test
    fun `retire le suffixe copie avant de versionner`() {
        assertEquals("Blonde v2", RecipeNaming.duplicateName("Blonde (copie)", listOf("Blonde")))
    }

    @Test
    fun `base sans homonyme donne v2`() {
        assertEquals("Stout v2", RecipeNaming.duplicateName("Stout v9", listOf("IPA", "Saison")))
    }
}
