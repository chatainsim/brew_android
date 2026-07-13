package fr.easter.brewhome.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.easter.brewhome.BrewViewModel
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

/**
 * Enveloppe commune des onglets de données : spinner au premier chargement,
 * écran d'erreur avec bouton « Réessayer » si la connexion échoue avant le
 * premier chargement, puis contenu avec tirer-pour-rafraîchir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshableContent(vm: BrewViewModel, content: @Composable () -> Unit) {
    val state by vm.state.collectAsState()
    when {
        !state.loaded && state.error != null -> ErrorRetry(state.error!!) { vm.refreshAll() }
        !state.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        else -> PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = { vm.refreshAll() },
            modifier = Modifier.fillMaxSize(),
        ) {
            content()
        }
    }
}

@Composable
fun ErrorRetry(message: String, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        Button(onClick = onRetry) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Text("Réessayer", Modifier.padding(start = 8.dp))
        }
    }
}

/** Champ de recherche compact utilisé en tête des listes. */
@Composable
fun SearchField(query: String, onChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Effacer la recherche")
                }
            }
        },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
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
