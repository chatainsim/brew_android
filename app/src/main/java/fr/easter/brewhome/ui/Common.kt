package fr.easter.brewhome.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import java.util.Locale

fun fmtQty(q: Double?): String {
    if (q == null) return "–"
    return if (q % 1.0 == 0.0) q.toInt().toString()
    else String.format(Locale.FRANCE, "%.2f", q).trimEnd('0').trimEnd(',')
}

fun categoryLabel(cat: String): String = when (cat.lowercase()) {
    "malt" -> "Malts"
    "houblon" -> "Houblons"
    "levure" -> "Levures"
    "autre" -> "Autres"
    else -> cat.replaceFirstChar { it.uppercase() }
}

val categoryOrder = listOf("malt", "houblon", "levure", "autre")

fun brewStatusLabel(status: String?): String = when (status) {
    "planned" -> "Planifié"
    "in_progress" -> "En cours"
    "fermenting" -> "Fermentation"
    "completed" -> "Terminé"
    else -> status ?: "?"
}

@Composable
fun brewStatusColor(status: String?): Color = when (status) {
    "planned" -> MaterialTheme.colorScheme.outline
    "in_progress" -> MaterialTheme.colorScheme.primary
    "fermenting" -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.secondary
}

@Composable
fun StarRating(rating: Int?, max: Int = 5, onSelect: ((Int) -> Unit)? = null) {
    Row {
        for (i in 1..max) {
            val filled = rating != null && i <= rating
            Icon(
                imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = "$i étoiles",
                tint = if (filled) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.outline,
                modifier = if (onSelect != null)
                    Modifier.clickable { onSelect(i) }
                else Modifier,
            )
        }
    }
}
