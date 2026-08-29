package fr.easter.brewhome.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.easter.brewhome.BrewViewModel
import fr.easter.brewhome.R
import fr.easter.brewhome.data.ActivityEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private val activityCategories = listOf("recipe", "brew", "beer", "backup", "import", "system")

private fun categoryIcon(category: String): ImageVector = when (category) {
    "recipe" -> Icons.Outlined.MenuBook
    "brew" -> Icons.Outlined.LocalFireDepartment
    "beer" -> Icons.Outlined.LocalDrink
    "backup" -> Icons.Outlined.CloudUpload
    "import" -> Icons.Outlined.FileDownload
    else -> Icons.Outlined.Settings
}

@Composable
private fun categoryLabelRes(category: String): Int = when (category) {
    "recipe" -> R.string.activity_cat_recipe
    "brew" -> R.string.activity_cat_brew
    "beer" -> R.string.activity_cat_beer
    "backup" -> R.string.activity_cat_backup
    "import" -> R.string.activity_cat_import
    else -> R.string.activity_cat_system
}

// Mêmes gabarits que script_locales.html (clé "act.<action>") - un journal partagé
// avec le web, donc les entrées créées depuis le site doivent s'afficher pareil ici.
// "act.recipe_forked" n'a pas de traduction côté web non plus (gabarit non défini
// dans script_locales.html) ; ajouté ici uniquement pour compléter l'affichage mobile.
private val activityTemplates = mapOf(
    "act.recipe_created" to "Recette « \${name} » créée",
    "act.recipe_updated" to "Recette « \${name} » modifiée",
    "act.recipe_deleted" to "Recette « \${name} » supprimée",
    "act.recipe_forked" to "Recette « \${name} » dupliquée",
    "act.brew_created" to "Brassin « \${name} » lancé",
    "act.brew_completed" to "Brassin « \${name} » clôturé",
    "act.brew_deleted" to "Brassin « \${name} » supprimé",
    "act.beer_created" to "« \${name} » ajouté en cave",
    "act.backup_auto" to "Backup auto : \${n} fichier(s) → \${repos}",
    "act.backup_auto_err" to "Backup auto : \${n} fichier(s) → \${repos} (\${e} erreur(s))",
    "act.backup_manual" to "Backup manuel : \${n} fichier(s)",
)

private val activityJson = Json { ignoreUnknownKeys = true }

/** Convertit une valeur JSON en texte affichable - certains champs des entrées
 * de sauvegarde (ex. "repos", "errors" dans act.backup_auto) sont des tableaux,
 * pas des valeurs simples ; jsonPrimitive plante dessus (IllegalArgumentException
 * "is not a JsonPrimitive"), d'où ce parcours explicite plutôt qu'un cast direct. */
private fun jsonElementText(v: JsonElement): String = when (v) {
    is JsonPrimitive -> v.contentOrNull ?: ""
    is JsonArray -> v.joinToString(", ") { jsonElementText(it) }
    is JsonObject -> ""
}

/** Décode un label d'entrée d'activité - soit un JSON `{"_i18n":"act.x", ...params}`
 * généré côté serveur (web), soit du texte brut déjà lisible (entrées créées
 * directement par cette app, voir BrewViewModel.logActivity). */
private fun activityLabel(raw: String): String {
    val obj = runCatching { activityJson.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return raw
    val key = (obj["_i18n"] as? JsonPrimitive)?.contentOrNull ?: return raw
    val template = activityTemplates[key] ?: return raw
    return runCatching {
        var text = template
        obj.forEach { (k, v) -> if (k != "_i18n") text = text.replace("\${$k}", jsonElementText(v)) }
        text
    }.getOrDefault(raw)
}

private fun activityAgo(ts: String?): String {
    if (ts.isNullOrBlank()) return ""
    val instant = runCatching {
        java.time.LocalDateTime.parse(ts.trim().replace(' ', 'T').take(19))
            .toInstant(java.time.ZoneOffset.UTC)
    }.getOrNull() ?: return ""
    val diffSec = (System.currentTimeMillis() - instant.toEpochMilli()) / 1000
    return when {
        diffSec < 60 -> "à l'instant"
        diffSec < 3600 -> "il y a ${diffSec / 60} min"
        diffSec < 86400 -> "il y a ${diffSec / 3600} h"
        else -> "il y a ${diffSec / 86400} j"
    }
}

/** Journal d'activité : historique des créations/modifications/suppressions
 * (recettes, brassins, bières, sauvegardes...), partagé avec le web - voir
 * aussi BrewViewModel.logActivity pour les entrées journalisées côté app. */
@Composable
fun ActivityScreen(vm: BrewViewModel) {
    var category by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(category) { vm.loadActivity(category = category) }
    val log by vm.activity.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = category == null, onClick = { category = null }, label = { Text(stringResource(R.string.activity_cat_all)) })
            activityCategories.forEach { c ->
                FilterChip(selected = category == c, onClick = { category = c }, label = { Text(stringResource(categoryLabelRes(c))) })
            }
        }

        val entries = log?.items
        when {
            log == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            entries.isNullOrEmpty() -> EmptyHint(stringResource(R.string.activity_empty), Icons.Outlined.History)
            else -> LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "clear") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.activity_clear), modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
                items(entries, key = { it.id }) { entry -> ActivityRow(entry) }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            text = { Text(stringResource(R.string.activity_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    vm.clearActivity(category = category)
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun ActivityRow(entry: ActivityEntry) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(32.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        categoryIcon(entry.category),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(activityLabel(entry.label), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
            Text(
                activityAgo(entry.ts),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
