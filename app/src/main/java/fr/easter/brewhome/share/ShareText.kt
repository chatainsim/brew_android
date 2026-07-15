package fr.easter.brewhome.share

import fr.easter.brewhome.data.InventoryItem
import fr.easter.brewhome.data.Recipe
import fr.easter.brewhome.data.RecipeIngredient
import fr.easter.brewhome.ui.categoryLabel
import fr.easter.brewhome.ui.categoryOrder
import fr.easter.brewhome.ui.fmtQty

/**
 * Textes de partage (feuille de partage Android : mail, Telegram, WhatsApp…).
 * Format Markdown simple, lisible tel quel et exploitable par une IA.
 */
object ShareText {

    fun recipe(r: Recipe): String = buildString {
        append("🍺 ").append(r.name)
        r.style?.let { append(" (").append(it).append(")") }
        appendLine()
        val meta = listOfNotNull(
            r.batchNo?.let { "brassin n°$it" },
            r.rating?.let { "note $it/5" },
        ).joinToString(" — ")
        if (meta.isNotEmpty()) appendLine(meta)

        appendLine()
        appendLine("Paramètres")
        r.volume?.let { appendLine("- Volume : ${fmtQty(it)} L") }
        r.mashTemp?.let { t ->
            appendLine("- Empâtage : ${fmtQty(t)} °C" + (r.mashTime?.let { " · $it min" } ?: ""))
        }
        r.boilTime?.let { appendLine("- Ébullition : $it min") }
        r.fermTemp?.let { t ->
            appendLine("- Fermentation : ${fmtQty(t)} °C" + (r.fermTime?.let { " · $it jours" } ?: ""))
        }

        val grouped = r.ingredients.groupBy { it.category.lowercase() }
        val cats = categoryOrder.filter { grouped.containsKey(it) } +
            grouped.keys.filterNot { it in categoryOrder }.sorted()
        cats.forEach { cat ->
            appendLine()
            appendLine(categoryLabel(cat))
            grouped.getValue(cat).forEach { appendLine(ingredientLine(it)) }
        }

        if (!r.notes.isNullOrBlank()) {
            appendLine()
            appendLine("Notes")
            appendLine(r.notes.trim())
        }
        appendLine()
        append("Partagé depuis BrewHome Android")
    }

    private fun ingredientLine(ing: RecipeIngredient): String {
        val details = listOfNotNull(
            ing.hopTime?.let { "$it min" },
            ing.hopType,
            ing.alpha?.let { "${fmtQty(it)} % α" },
            ing.ebc?.let { "${fmtQty(it)} EBC" },
            ing.notes?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        return "- ${ing.name} : ${fmtQty(ing.quantity)} ${ing.unit}" +
            (if (details.isNotEmpty()) " ($details)" else "")
    }

    fun inventory(items: List<InventoryItem>, date: String): String = buildString {
        appendLine("📦 Stock d'ingrédients de brasserie (BrewHome) — $date")

        val grouped = items.groupBy { it.category.lowercase() }
        val cats = categoryOrder.filter { grouped.containsKey(it) } +
            grouped.keys.filterNot { it in categoryOrder }.sorted()
        cats.forEach { cat ->
            appendLine()
            appendLine(categoryLabel(cat))
            grouped.getValue(cat)
                .sortedBy { it.name.lowercase() }
                .forEach { appendLine(inventoryLine(it)) }
        }
        appendLine()
        append("Partagé depuis BrewHome Android")
    }

    private fun inventoryLine(item: InventoryItem): String {
        val details = listOfNotNull(
            item.origin?.takeIf { it.isNotBlank() },
            item.alpha?.let { "${fmtQty(it)} % α" },
            item.ebc?.let { "${fmtQty(it)} EBC" },
            item.notes?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        val lowStock = item.minStock?.takeIf { it > 0 && item.quantity <= it } != null
        return "- ${item.name} : ${fmtQty(item.quantity)} ${item.unit}" +
            (if (details.isNotEmpty()) " ($details)" else "") +
            (if (lowStock) " ⚠️ stock bas" else "")
    }
}
