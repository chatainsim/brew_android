package fr.easter.brewhome

import fr.easter.brewhome.data.Draft
import fr.easter.brewhome.data.DraftIngredient
import fr.easter.brewhome.data.beerXmlErrorMessage
import fr.easter.brewhome.data.parsedIngredients
import fr.easter.brewhome.ui.draftIngDetail
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Brouillons importés en BeerXML : le détail des ingrédients survit à l'appli. */
class DraftIngredientTest {
    // Ingrédients tels que le serveur les écrit pour un brouillon importé
    private val imported = """[
        {"category":"malt","name":"Pilsner","quantity":5.0,"unit":"kg","ebc":3.9},
        {"category":"houblon","name":"Citra","quantity":30,"unit":"g","hop_type":"whirlpool","hop_time":20,"alpha":12.0},
        {"category":"houblon","name":"Mosaic","quantity":40,"unit":"g","hop_type":"dryhop","hop_days":4.0,"alpha":11.5},
        {"category":"autre","name":"Hibiscus","quantity":15,"unit":"g","other_type":"ebullition","other_time":5},
        {"category":"levure","name":"US-05","quantity":1,"unit":"sachet","champ_futur":"ignoré"}
    ]"""

    @Test
    fun detail_lu_meme_avec_des_nombres_decimaux() {
        val ings = Draft(id = 1, title = "IPA", ingredients = imported).parsedIngredients()
        assertEquals(5, ings.size)                       // un « 4.0 » ne fait pas tout échouer
        assertEquals("whirlpool", ings[1].hopType)
        assertEquals(20.0, ings[1].hopTime!!, 1e-9)
        assertEquals(4.0, ings[2].hopDays!!, 1e-9)
        assertEquals(3.9, ings[0].ebc!!, 1e-9)
        assertEquals("ebullition", ings[3].otherType)
    }

    @Test
    fun detail_reecrit_sans_nulls() {
        val json = Json { encodeDefaults = true; explicitNulls = false }
        val out = json.encodeToString(listOf(
            DraftIngredient("Citra", "houblon", 30.0, "g", hopType = "whirlpool", hopTime = 20.0, alpha = 12.0),
            DraftIngredient("Pale", "malt", 4.0, "kg"),
        ))
        assertTrue(out, out.contains("\"hop_type\":\"whirlpool\""))
        assertTrue(out, out.contains("\"hop_time\":20.0"))
        assertFalse(out, out.contains("null"))
        // relu à l'identique
        val back = Draft(id = 1, title = "x", ingredients = out).parsedIngredients()
        assertEquals("whirlpool", back[0].hopType)
        assertNull(back[1].hopType)
    }

    @Test
    fun libelle_du_detail() {
        val ings = Draft(id = 1, title = "IPA", ingredients = imported).parsedIngredients()
        assertEquals("EBC 3,9", draftIngDetail(ings[0]))
        assertEquals("whirlpool 20 min · 12 % AA", draftIngDetail(ings[1]))
        assertEquals("dry hop 4 j · 11,5 % AA", draftIngDetail(ings[2]))
        assertEquals("ébullition 5 min", draftIngDetail(ings[3]))
        assertNull(draftIngDetail(ings[4]))
        assertNull(draftIngDetail(DraftIngredient("Saaz", "houblon", 20.0, "g")))   // saisi à la main
    }

    @Test
    fun reponse_reelle_du_serveur() {
        // Réponse de POST /api/import/beerxml/drafts (BrewHome 0.1.26), même config Json qu'ApiClient
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }
        val res = json.decodeFromString<fr.easter.brewhome.data.DraftImportResult>(
            javaClass.classLoader!!.getResource("draft_import.json")!!.readText())
        assertEquals(2, res.imported)
        assertTrue(res.repaired)                                  // « Citra & Mosaic »
        val d = res.drafts[0]
        assertEquals("Session IPA (Citra & Mosaic)", d.title)
        assertEquals("idea", d.status)
        val ings = d.parsedIngredients()
        assertEquals(7, ings.size)
        val mosaic = ings.single { it.name == "Mosaic" }
        assertEquals("dry hop 3 j · 11,5 % AA", draftIngDetail(mosaic))
        assertTrue(d.notes!!.contains("Houblonnage"))
    }

    @Test
    fun message_d_erreur_beerxml() {
        assertEquals(
            "XML invalide · ligne 4, colonne 43 · « <NAME>A & B</NAME> »",
            beerXmlErrorMessage("""{"error":"xml_parse_error","line":4,"column":43,"excerpt":"<NAME>A & B</NAME>"}"""),
        )
        // Serveur < 0.1.26 : pas de position
        assertEquals("XML invalide", beerXmlErrorMessage("""{"error":"xml_parse_error","detail":"x"}"""))
        assertTrue(beerXmlErrorMessage("""{"error":"xml_forbidden"}""")!!.contains("DOCTYPE"))
        assertNull(beerXmlErrorMessage("""{"error":"autre"}"""))
        assertNull(beerXmlErrorMessage("pas du json"))
        assertNull(beerXmlErrorMessage(null))
    }
}
