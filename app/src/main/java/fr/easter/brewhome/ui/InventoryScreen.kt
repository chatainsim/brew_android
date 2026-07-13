package fr.easter.brewhome.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.easter.brewhome.BrewViewModel
import fr.easter.brewhome.data.InventoryItem

/** Pas d'ajustement rapide selon l'unité (10 g, 0,1 kg, 1 pièce…). */
private fun stepFor(unit: String): Double = when (unit.lowercase()) {
    "g" -> 10.0
    "kg" -> 0.1
    "l" -> 0.5
    else -> 1.0
}

@Composable
fun InventoryScreen(vm: BrewViewModel) {
    val state by vm.state.collectAsState()
    var editing by remember { mutableStateOf<InventoryItem?>(null) }
    var query by rememberSaveable { mutableStateOf("") }

    RefreshableContent(vm) {
        if (state.inventory.isEmpty()) {
            EmptyHint("Aucun ingrédient en stock.")
            return@RefreshableContent
        }

        val filtered = state.inventory.filter { item ->
            query.isBlank() || listOfNotNull(item.name, item.origin)
                .any { it.contains(query, ignoreCase = true) }
        }
        val grouped = filtered.groupBy { it.category.lowercase() }
        val orderedCats = categoryOrder.filter { grouped.containsKey(it) } +
            grouped.keys.filterNot { it in categoryOrder }.sorted()

        Column(Modifier.fillMaxSize()) {
            SearchField(query, { query = it }, "Rechercher un ingrédient…")
            if (filtered.isEmpty()) {
                EmptyHint("Aucun résultat pour « $query ».")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    orderedCats.forEach { cat ->
                        item(key = "header-$cat") {
                            Text(
                                categoryLabel(cat),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                            )
                        }
                        items(grouped.getValue(cat), key = { it.id }) { item ->
                            InventoryRow(
                                item = item,
                                onAdjust = { delta -> vm.setInventoryQty(item, item.quantity + delta) },
                                onClick = { editing = item },
                            )
                        }
                    }
                }
            }
        }
    }

    editing?.let { item ->
        QtyDialog(
            item = item,
            onDismiss = { editing = null },
            onSave = { qty ->
                vm.setInventoryQty(item, qty)
                editing = null
            },
        )
    }
}

@Composable
private fun InventoryRow(item: InventoryItem, onAdjust: (Double) -> Unit, onClick: () -> Unit) {
    val low = item.minStock != null && item.quantity < item.minStock
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (low) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = "Stock bas",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        item.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val details = listOfNotNull(
                    item.origin,
                    item.alpha?.let { "${fmtQty(it)}% α" },
                    item.ebc?.let { "${fmtQty(it)} EBC" },
                ).joinToString(" · ")
                if (details.isNotEmpty()) {
                    Text(
                        details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            val step = stepFor(item.unit)
            IconButton(
                onClick = { onAdjust(-step) },
                enabled = item.quantity > 0,
                modifier = Modifier.size(34.dp),
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "Retirer")
            }
            Text(
                "${fmtQty(item.quantity)} ${item.unit}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.widthIn(min = 60.dp),
            )
            IconButton(onClick = { onAdjust(step) }, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Filled.Add, contentDescription = "Ajouter")
            }
        }
    }
}

@Composable
private fun QtyDialog(item: InventoryItem, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var text by remember { mutableStateOf(fmtQty(item.quantity).replace(',', '.')) }
    val parsed = text.replace(',', '.').toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Quantité (${item.unit})") },
                isError = parsed == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let(onSave) },
                enabled = parsed != null && parsed >= 0,
            ) { Text("Enregistrer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
    )
}
