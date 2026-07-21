package fr.easter.brewhome

import fr.easter.brewhome.calc.BeerXmlExport
import fr.easter.brewhome.data.Recipe
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
}
