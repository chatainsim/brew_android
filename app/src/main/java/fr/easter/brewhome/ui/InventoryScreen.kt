package fr.easter.brewhome.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.easter.brewhome.BrewViewModel
import fr.easter.brewhome.data.InventoryItem
import fr.easter.brewhome.data.ShoppingItem
import fr.easter.brewhome.data.ShoppingPost

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
    var showShopping by rememberSaveable { mutableStateOf(false) }

    RefreshableContent(vm) {
        Column(Modifier.fillMaxSize()) {
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                SegmentedButton(
                    selected = !showShopping,
                    onClick = { showShopping = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("Stock (${state.inventory.size})") }
                SegmentedButton(
                    selected = showShopping,
                    onClick = { showShopping = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("Courses (${state.shopping.size})") }
            }
            if (showShopping) {
                ShoppingContent(vm)
            } else {
                InventoryContent(state.inventory, query, { query = it }, vm) { editing = it }
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
private fun InventoryContent(
    inventory: List<InventoryItem>,
    query: String,
    onQuery: (String) -> Unit,
    vm: BrewViewModel,
    onEdit: (InventoryItem) -> Unit,
) {
    if (inventory.isEmpty()) {
        EmptyHint("Aucun ingrédient en stock.")
        return
    }
    val filtered = inventory.filter { item ->
        query.isBlank() || listOfNotNull(item.name, item.origin)
            .any { it.contains(query, ignoreCase = true) }
    }
    val grouped = filtered.groupBy { it.category.lowercase() }
    val orderedCats = categoryOrder.filter { grouped.containsKey(it) } +
        grouped.keys.filterNot { it in categoryOrder }.sorted()

    Column(Modifier.fillMaxSize()) {
        SearchField(query, onQuery, "Rechercher un ingrédient…")
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
                            onClick = { onEdit(item) },
                        )
                    }
                }
            }
        }
    }
}

// ── Liste de courses ──────────────────────────────────────────────────────────

@Composable
private fun ShoppingContent(vm: BrewViewModel) {
    val state by vm.state.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    val checkedCount = state.shopping.count { (it.checked ?: 0) == 1 }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (checkedCount > 0) {
                Button(
                    onClick = { vm.buyCheckedShopping() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Icon(Icons.Filled.ShoppingCart, contentDescription = null)
                    Text(
                        "Transférer $checkedCount acheté(s) dans le stock",
                        Modifier.padding(start = 8.dp),
                    )
                }
            }
            if (state.shopping.isEmpty()) {
                EmptyHint("Liste de courses vide. Ajoute un article avec le bouton +.")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 12.dp, end = 12.dp, top = 8.dp, bottom = 88.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.shopping, key = { it.id }) { item ->
                        ShoppingRow(
                            item = item,
                            onToggle = { vm.toggleShoppingChecked(item) },
                            onDelete = { vm.deleteShoppingItem(item.id) },
                        )
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Ajouter un article")
        }
    }

    if (showAdd) {
        AddShoppingDialog(
            onAdd = {
                vm.addShoppingItem(it)
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }
}

@Composable
private fun ShoppingRow(item: ShoppingItem, onToggle: () -> Unit, onDelete: () -> Unit) {
    val checked = (item.checked ?: 0) == 1
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (checked) TextDecoration.LineThrough else null,
                    color = if (checked) MaterialTheme.colorScheme.outline
                        else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val details = listOfNotNull(
                    "${fmtQty(item.quantity)} ${item.unit}",
                    categoryLabel(item.category),
                    item.notes?.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                Text(
                    details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Supprimer l'article",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private val shoppingUnits = listOf("g", "kg", "mL", "L", "sachet", "pièce")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddShoppingDialog(onAdd: (ShoppingPost) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("malt") }
    var qty by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf("kg") }
    val parsedQty = qty.trim().replace(',', '.').toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter aux courses") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                DialogDropdown(
                    label = "Catégorie",
                    value = categoryLabel(category),
                    options = categoryOrder,
                    optionLabel = { categoryLabel(it) },
                    onSelect = { category = it },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = qty,
                        onValueChange = { qty = it },
                        label = { Text("Quantité") },
                        singleLine = true,
                        isError = parsedQty == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    DialogDropdown(
                        label = "Unité",
                        value = unit,
                        options = shoppingUnits,
                        optionLabel = { it },
                        onSelect = { unit = it },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onAdd(ShoppingPost(
                        name = name.trim(),
                        category = category,
                        quantity = parsedQty ?: 1.0,
                        unit = unit,
                    ))
                },
                enabled = name.isNotBlank() && parsedQty != null && parsedQty >= 0,
            ) { Text("Ajouter") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogDropdown(
    label: String,
    value: String,
    options: List<String>,
    optionLabel: (String) -> String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(optionLabel(opt)) },
                    onClick = {
                        onSelect(opt)
                        expanded = false
                    },
                )
            }
        }
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
