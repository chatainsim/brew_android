package fr.easter.brewhome.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.easter.brewhome.BrewViewModel
import fr.easter.brewhome.R
import fr.easter.brewhome.data.TrashItem

private data class TrashRow(val kind: String, val item: TrashItem, val subtitle: String?)

/** Corbeille : éléments supprimés, restaurables tant qu'ils ne sont pas purgés. */
@Composable
fun TrashScreen(vm: BrewViewModel) {
    val trash by vm.trash.collectAsState()
    LaunchedEffect(Unit) { vm.loadTrash() }

    if (trash == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val t = trash!!
    val rows = buildList {
        t.beers.forEach { add(TrashRow("beer", it, it.type)) }
        t.recipes.forEach { add(TrashRow("recipe", it, it.style)) }
        t.brews.forEach { add(TrashRow("brew", it, it.brewDate)) }
        t.inventory.forEach {
            val qty = it.quantity?.let { q -> "${fmtQty(q)} ${it.unit ?: ""}".trim() }
            add(TrashRow("inventory", it, listOfNotNull(it.category, qty).joinToString(" · ").ifBlank { null }))
        }
    }

    if (rows.isEmpty()) {
        EmptyHint(stringResource(R.string.trash_empty), Icons.Outlined.DeleteOutline)
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        t.retentionDays?.let { days ->
            item(key = "retention") {
                Text(
                    stringResource(R.string.trash_retention, days),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
        items(rows, key = { "${it.kind}_${it.item.id}" }) { row ->
            TrashCard(row) { vm.restoreFromTrash(row.kind, row.item.id) }
        }
    }
}

@Composable
private fun kindLabel(kind: String): String = stringResource(
    when (kind) {
        "beer" -> R.string.trash_kind_beer
        "recipe" -> R.string.trash_kind_recipe
        "brew" -> R.string.trash_kind_brew
        else -> R.string.trash_kind_inventory
    },
)

@Composable
private fun TrashCard(row: TrashRow, onRestore: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${kindLabel(row.kind)} · ${row.item.name}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                val sub = listOfNotNull(
                    row.subtitle,
                    row.item.deletedAt?.take(10),
                ).joinToString(" — ").ifBlank { null }
                sub?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            IconButton(onClick = onRestore) {
                Icon(
                    Icons.Outlined.RestoreFromTrash,
                    contentDescription = stringResource(R.string.trash_restore),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
