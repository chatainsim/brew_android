package fr.easter.brewhome

import fr.easter.brewhome.data.BeerPut
import fr.easter.brewhome.data.InventoryPost
import fr.easter.brewhome.data.beerPutBody
import fr.easter.brewhome.data.inventoryPutBody
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Les PUT doivent envoyer les valeurs par défaut et les null, sinon le serveur garde l'ancienne valeur. */
class PutBodiesTest {
    @Test
    fun `refermentation decochee envoyee a 0, duree videe envoyee a null`() {
        val body = beerPutBody(BeerPut(name = "Pink'Hops", refermentation = 0, refermentationDays = null))
        assertEquals(JsonPrimitive(0), body["refermentation"])
        assertEquals(JsonNull, body["refermentation_days"])
    }

    @Test
    fun `champs vides et stocks a 0 envoyes`() {
        val body = beerPutBody(BeerPut(name = "Saison", description = null, stock33 = 0))
        assertEquals(JsonNull, body["description"])
        assertEquals(JsonPrimitive(0), body["stock_33cl"])
    }

    @Test
    fun `photo inconnue jamais envoyee a null`() {
        assertFalse("photo" in beerPutBody(BeerPut(name = "Saison", photo = null)))
        assertTrue("photo" in beerPutBody(BeerPut(name = "Saison", photo = "/api/beer-photos/a.jpg")))
    }

    @Test
    fun `stock a 0 et unite kg envoyes`() {
        val body = inventoryPutBody(InventoryPost(name = "Pale", category = "malt", quantity = 0.0, unit = "kg"))
        assertEquals(JsonPrimitive(0.0), body["quantity"])
        assertEquals(JsonPrimitive("kg"), body["unit"])
        assertEquals(JsonNull, body["notes"])
    }
}
