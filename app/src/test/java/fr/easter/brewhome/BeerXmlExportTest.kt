package fr.easter.brewhome

import fr.easter.brewhome.calc.BeerXmlExport
import fr.easter.brewhome.data.Recipe
import fr.easter.brewhome.data.RecipeIngredient
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BeerXmlExportTest {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResource(name)!!.readText()

    private val recipe: Recipe by lazy {
        json.decodeFromString<List<Recipe>>(fixture("recipes.json"))[0]
    }

    @Test
    fun `document BeerXML bien formé`() {
        val xml = BeerXmlExport.toBeerXml(recipe)
        assertTrue(xml.startsWith("""<?xml version="1.0" encoding="UTF-8"?>"""))
        assertTrue("<RECIPES>" in xml)
        assertTrue(xml.trimEnd().endsWith("</RECIPES>"))
        assertTrue("<NAME>Pale Ale test</NAME>" in xml)
        assertTrue("<TYPE>All Grain</TYPE>" in xml)
    }

    @Test
    fun `malts en fermentescibles et grammes convertis en kg`() {
        val xml = BeerXmlExport.toBeerXml(recipe)
        // 4,5 kg de Pilsner : la quantité reste en kg
        assertTrue("<FERMENTABLE>" in xml)
        assertTrue("<AMOUNT>4.5</AMOUNT>" in xml)
        // 30 g de Cascade → 0,03 kg dans les houblons
        assertTrue("<HOP>" in xml)
        assertTrue("<AMOUNT>0.03</AMOUNT>" in xml)
        assertTrue("<USE>Boil</USE>" in xml)
    }

    @Test
    fun `caracteres speciaux echappes`() {
        val evil = recipe.copy(name = "IPA <maison> & \"co\"")
        val xml = BeerXmlExport.toBeerXml(evil)
        assertTrue("<NAME>IPA &lt;maison&gt; &amp; &quot;co&quot;</NAME>" in xml)
        assertTrue("<maison>" !in xml)
    }

    @Test
    fun `nom de fichier normalise`() {
        assertEquals("pale-ale-test.xml", BeerXmlExport.fileName(recipe))
    }

    @Test
    fun `couleur du malt convertie comme sur le site`() {
        // Pilsner à 3,5 EBC : le site exporte COLOR = EBC/1.97 (bh-recettes.js)
        val xml = BeerXmlExport.toBeerXml(recipe)
        assertTrue("<COLOR>1.777</COLOR>" in xml)
    }

    @Test
    fun `houblon whirlpool exporte en Aroma, pas en Boil`() {
        val whirlpool = RecipeIngredient(
            id = 3, name = "Citra", category = "houblon",
            quantity = 20.0, unit = "g", hopType = "whirlpool", hopTime = 15,
        )
        val xml = BeerXmlExport.toBeerXml(recipe.copy(ingredients = recipe.ingredients + whirlpool))
        assertTrue("<NAME>Citra</NAME>" in xml)
        assertTrue("<USE>Aroma</USE>" in xml)
    }

    @Test
    fun `levure exportee avec une quantite, jamais vide`() {
        val yeastSachet = RecipeIngredient(
            id = 4, name = "US-05", category = "levure", quantity = 0.0, unit = "sachet",
        )
        val yeastGrams = RecipeIngredient(
            id = 5, name = "S-04", category = "levure", quantity = 11.5, unit = "g",
        )
        val xml = BeerXmlExport.toBeerXml(
            recipe.copy(ingredients = recipe.ingredients + listOf(yeastSachet, yeastGrams))
        )
        // Sachet sans quantité renseignée → 1 sachet par défaut ≈ 0,011 kg
        assertTrue("<AMOUNT>0.011</AMOUNT>" in xml)
        // 11,5 g convertis en kg (0,0115, arrondi à 3 décimales par fmt())
        assertTrue("<AMOUNT>0.012</AMOUNT>" in xml)
        assertTrue("<AMOUNT_IS_WEIGHT>TRUE</AMOUNT_IS_WEIGHT>" in xml)
    }
}
