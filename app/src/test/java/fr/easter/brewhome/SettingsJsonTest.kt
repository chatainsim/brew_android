package fr.easter.brewhome

import fr.easter.brewhome.data.mergeJsonSetting
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SettingsJsonTest {

    private fun parse(s: String) = Json.parseToJsonElement(s).jsonObject

    @Test
    fun `les champs saisis sur le site sont conserves`() {
        // Profil d'eau réel : l'app ne connaît que « price »
        val existing = JsonPrimitive("""{"price":0.004,"ph":7.6,"ca":95,"so4":40,"cooling":60}""")
        val out = parse(mergeJsonSetting(existing, mapOf("price" to JsonPrimitive(0.005))))
        assertEquals("0.005", out["price"].toString())
        assertEquals("7.6", out["ph"].toString())
        assertEquals("95", out["ca"].toString())
        assertEquals("40", out["so4"].toString())
        assertEquals("60", out["cooling"].toString())
    }

    @Test
    fun `une valeur nulle retire le champ`() {
        val out = parse(mergeJsonSetting(JsonPrimitive("""{"price":0.004,"ph":7.6}"""), mapOf("price" to null)))
        assertFalse("price" in out)
        assertEquals("7.6", out["ph"].toString())
    }

    @Test
    fun `reglage absent ou illisible part d'un objet vide`() {
        assertEquals("""{"price":0.004}""", mergeJsonSetting(null, mapOf("price" to JsonPrimitive(0.004))))
        assertEquals("""{"price":0.004}""", mergeJsonSetting(JsonPrimitive("pas du json"), mapOf("price" to JsonPrimitive(0.004))))
    }
}
