package fr.easter.brewhome.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.easter.brewhome.BrewViewModel
import fr.easter.brewhome.data.Brew

@Composable
fun BrewsScreen(vm: BrewViewModel) {
    val state by vm.state.collectAsState()
    RefreshableContent(vm) {
        if (state.brews.isEmpty()) {
            EmptyHint("Aucun brassin.")
            return@RefreshableContent
        }
        LazyColumn(
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.brews, key = { it.id }) { brew ->
                BrewCard(brew)
            }
        }
    }
}

@Composable
private fun BrewCard(brew: Brew) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    brew.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                val statusColor = brewStatusColor(brew.status)
                AssistChip(
                    onClick = {},
                    label = { Text(brewStatusLabel(brew.status), color = statusColor) },
                    border = AssistChipDefaults.assistChipBorder(
                        enabled = true,
                        borderColor = statusColor,
                    ),
                )
            }
            val line1 = listOfNotNull(
                brew.brewDate?.let { "Brassé le $it" },
                brew.volumeBrewed?.let { "${fmtQty(it)} L" },
            ).joinToString(" · ")
            if (line1.isNotEmpty()) {
                Text(
                    line1,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            val line2 = listOfNotNull(
                brew.og?.let { "DI ${fmtGravity(it)}" },
                brew.fg?.let { "DF ${fmtGravity(it)}" },
                brew.abv?.let { "${fmtQty(it)}% alc." },
            ).joinToString(" · ")
            if (line2.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(line2, style = MaterialTheme.typography.bodyMedium)
            }
            if (!brew.notes.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    brew.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun fmtGravity(g: Double): String =
    if (g >= 2.0) fmtQty(g) else String.format(java.util.Locale.US, "%.3f", g)
