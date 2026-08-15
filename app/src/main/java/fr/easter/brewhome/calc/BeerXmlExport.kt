package fr.easter.brewhome.calc

import fr.easter.brewhome.data.Recipe

/**
 * Génère un document BeerXML 1.0 à partir d'une recette BrewHome, exploitable
 * par les autres logiciels de brassage (Brewfather, BeerSmith, Brewtarget…).
 *
 * Les catégories BrewHome sont « malt » (fermentescibles), « houblon » et
 * « levure » ; tout le reste part dans les MISCS. Les quantités BeerXML sont
 * en kilogrammes.
 */
object BeerXmlExport {

    fun toBeerXml(recipe: Recipe): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("<RECIPES>")
        appendLine("  <RECIPE>")
        tag("NAME", recipe.name, 2)
        tag("VERSION", "1", 2)
        tag("TYPE", "All Grain", 2)
        tag("BREWER", "BrewHome", 2)
        recipe.volume?.let { tag("BATCH_SIZE", fmt(it), 2) }
        recipe.boilTime?.let { tag("BOIL_TIME", it.toString(), 2) }
        recipe.brewhouseEfficiency?.let { tag("EFFICIENCY", fmt(it), 2) }

        appendLine("    <STYLE>")
        tag("NAME", recipe.style ?: "", 3)
        tag("VERSION", "1", 3)
        tag("TYPE", "Ale", 3)
        appendLine("    </STYLE>")

        val byCat = recipe.ingredients.groupBy { it.category.lowercase() }

        appendLine("    <FERMENTABLES>")
        byCat["malt"].orEmpty().forEach { ferm ->
            appendLine("      <FERMENTABLE>")
            tag("NAME", ferm.name, 4)
            tag("VERSION", "1", 4)
            tag("TYPE", "Grain", 4)
            tag("AMOUNT", fmt(kg(ferm.quantity, ferm.unit)), 4)
            // Même conversion EBC → COLOR que le site (bh-recettes.js: m.ebc/1.97)
            tag("COLOR", fmt(ebcToLovibond(ferm.ebc ?: 0.0)), 4)
            appendLine("      </FERMENTABLE>")
        }
        appendLine("    </FERMENTABLES>")

        appendLine("    <HOPS>")
        byCat["houblon"].orEmpty().forEach { hop ->
            val dryHop = hop.hopType?.contains("dry", ignoreCase = true) == true || hop.hopDays != null
            val whirlpool = !dryHop && hop.hopType?.equals("whirlpool", ignoreCase = true) == true
            appendLine("      <HOP>")
            tag("NAME", hop.name, 4)
            tag("VERSION", "1", 4)
            tag("AMOUNT", fmt(kg(hop.quantity, hop.unit)), 4)
            hop.alpha?.let { tag("ALPHA", fmt(it), 4) }
            // Même mapping que hopUseMap() côté site : whirlpool → « Aroma », pas « Boil »
            tag("USE", if (dryHop) "Dry Hop" else if (whirlpool) "Aroma" else "Boil", 4)
            // TIME en minutes : jours × 1440 pour un dry hop
            val time = if (dryHop) (hop.hopDays ?: 0) * 1440 else (hop.hopTime ?: 0)
            tag("TIME", time.toString(), 4)
            appendLine("      </HOP>")
        }
        appendLine("    </HOPS>")

        appendLine("    <YEASTS>")
        byCat["levure"].orEmpty().forEach { yeast ->
            appendLine("      <YEAST>")
            tag("NAME", yeast.name, 4)
            tag("VERSION", "1", 4)
            tag("TYPE", "Ale", 4)
            tag("FORM", "Dry", 4)
            // Même conversion que le site (bh-recettes.js) : un sachet ≈ 0,011 kg
            val amount = if (yeast.unit.equals("sachet", ignoreCase = true)) {
                (if (yeast.quantity != 0.0) yeast.quantity else 1.0) * 0.011
            } else {
                kg(yeast.quantity, yeast.unit).let { if (it != 0.0) it else 0.011 }
            }
            tag("AMOUNT", fmt(amount), 4)
            tag("AMOUNT_IS_WEIGHT", "TRUE", 4)
            appendLine("      </YEAST>")
        }
        appendLine("    </YEASTS>")

        appendLine("    <MISCS>")
        recipe.ingredients
            .filterNot { it.category.lowercase() in setOf("malt", "houblon", "levure") }
            .forEach { misc ->
                appendLine("      <MISC>")
                tag("NAME", misc.name, 4)
                tag("VERSION", "1", 4)
                tag("TYPE", "Other", 4)
                tag("USE", "Boil", 4)
                tag("AMOUNT", fmt(kg(misc.quantity, misc.unit)), 4)
                appendLine("      </MISC>")
            }
        appendLine("    </MISCS>")

        appendLine("    <MASH>")
        tag("NAME", "Mash", 3)
        tag("VERSION", "1", 3)
        appendLine("      <MASH_STEPS>")
        appendLine("        <MASH_STEP>")
        tag("NAME", "Palier", 5)
        tag("VERSION", "1", 5)
        tag("TYPE", "Infusion", 5)
        recipe.mashTemp?.let { tag("STEP_TEMP", fmt(it), 5) }
        recipe.mashTime?.let { tag("STEP_TIME", it.toString(), 5) }
        appendLine("        </MASH_STEP>")
        appendLine("      </MASH_STEPS>")
        appendLine("    </MASH>")

        recipe.fermTime?.let { tag("PRIMARY_AGE", it.toString(), 2) }
        recipe.fermTemp?.let { tag("PRIMARY_TEMP", fmt(it), 2) }
        if (!recipe.notes.isNullOrBlank()) tag("NOTES", recipe.notes, 2)

        appendLine("  </RECIPE>")
        append("</RECIPES>")
    }

    /** Nom de fichier sûr : « ma-recette.xml ». */
    fun fileName(recipe: Recipe): String {
        val slug = recipe.name.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "recette" }
        return "$slug.xml"
    }

    private fun StringBuilder.tag(name: String, value: String, indent: Int) {
        append("  ".repeat(indent))
        append("<").append(name).append(">")
        append(escape(value))
        append("</").append(name).append(">")
        append("\n")
    }

    private fun kg(quantity: Double, unit: String): Double = when (unit.lowercase()) {
        "kg" -> quantity
        "g" -> quantity / 1000.0
        "mg" -> quantity / 1_000_000.0
        else -> quantity // sachet, unité… : renvoyé tel quel
    }

    /** EBC → COLOR BeerXML : même conversion que le site (bh-recettes.js). */
    private fun ebcToLovibond(ebc: Double): Double = ebc / 1.97

    private fun fmt(v: Double): String {
        val r = (Math.round(v * 1000.0) / 1000.0)
        return if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString()
    }

    private fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
