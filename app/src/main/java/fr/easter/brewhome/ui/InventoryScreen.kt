package fr.easter.brewhome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import fr.easter.brewhome.BrewViewModel
import fr.easter.brewhome.R
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
fun InventoryScreen(vm: BrewViewModel, initialShopping: Boolean = false) {
    val state by vm.state.collectAsState()
    var editing by remember { mutableStateOf<InventoryItem?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var showShopping by rememberSaveable { mutableStateOf(initialShopping) }

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
                ) { Text(stringResource(R.string.inventory_seg, state.inventory.size)) }
                SegmentedButton(
                    selected = showShopping,
                    onClick = { showShopping = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text(stringResource(R.string.shopping_seg, state.shopping.size)) }
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
        EmptyHint(stringResource(R.string.inventory_empty))
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
        SearchField(query, onQuery, stringResource(R.string.inventory_search))
        if (filtered.isEmpty()) {
            EmptyHint(stringResource(R.string.no_results, query))
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
                        stringResource(R.string.shopping_buy, checkedCount),
                        Modifier.padding(start = 8.dp),
                    )
                }
            }
            if (state.shopping.isEmpty()) {
                EmptyHint(stringResource(R.string.shopping_empty))
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
                            onDelete = { vm.deleteShoppingItem(item) },
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
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.shopping_add_title))
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

/** Ligne de courses : glisser à droite pour cocher, à gauche pour supprimer. */
@Composable
private fun ShoppingRow(item: ShoppingItem, onToggle: () -> Unit, onDelete: () -> Unit) {
    val checked = (item.checked ?: 0) == 1
    val haptics = LocalHapticFeedback.current
    // Les lambdas capturées par l'état de balayage doivent suivre l'article
    // (son état coché change sous le même id)
    val currentToggle by rememberUpdatedState(onToggle)
    val currentDelete by rememberUpdatedState(onDelete)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                    currentToggle()
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    currentDelete()
                }
                SwipeToDismissBoxValue.Settled -> {}
            }
            // Toujours revenir en place : la liste se met à jour via l'état
            // serveur, et « Annuler » peut faire réapparaître l'article
            false
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = { SwipeBackground(dismissState.dismissDirection) },
    ) {
        Card(Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(start = 4.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = {
                        haptics.performHapticFeedback(
                            if (checked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn,
                        )
                        onToggle()
                    },
                )
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
            }
        }
    }
}

/** Fond révélé pendant le balayage : coche à droite, corbeille à gauche. */
@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue) {
    if (direction == SwipeToDismissBoxValue.Settled) return
    val toCheck = direction == SwipeToDismissBoxValue.StartToEnd
    Box(
        Modifier
            .fillMaxSize()
            .clip(CardDefaults.shape)
            .background(
                if (toCheck) MaterialTheme.colorScheme.tertiaryContainer
                else MaterialTheme.colorScheme.errorContainer,
            )
            .padding(horizontal = 20.dp),
        contentAlignment = if (toCheck) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Icon(
            if (toCheck) Icons.Filled.Check else Icons.Filled.Delete,
            contentDescription = if (toCheck) null else stringResource(R.string.cd_delete_item),
            tint = if (toCheck) MaterialTheme.colorScheme.onTertiaryContainer
                else MaterialTheme.colorScheme.onErrorContainer,
        )
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
        title = { Text(stringResource(R.string.shopping_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.label_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                DialogDropdown(
                    label = stringResource(R.string.label_category),
                    value = categoryLabel(category),
                    options = categoryOrder,
                    optionLabel = { categoryLabel(it) },
                    onSelect = { category = it },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = qty,
                        onValueChange = { qty = it },
                        label = { Text(stringResource(R.string.label_quantity)) },
                        singleLine = true,
                        isError = parsedQty == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    DialogDropdown(
                        label = stringResource(R.string.label_unit),
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
            ) { Text(stringResource(R.string.add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
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
                            contentDescription = stringResource(R.string.low_stock),
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
            val haptics = LocalHapticFeedback.current
            IconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onAdjust(-step)
                },
                enabled = item.quantity > 0,
                modifier = Modifier.size(34.dp),
            ) {
                Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.cd_decrease))
            }
            Text(
                "${fmtQty(item.quantity)} ${item.unit}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.widthIn(min = 60.dp),
            )
            IconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onAdjust(step)
                },
                modifier = Modifier.size(34.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cd_increase))
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
                label = { Text(stringResource(R.string.qty_with_unit, item.unit)) },
                isError = parsed == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let(onSave) },
                enabled = parsed != null && parsed >= 0,
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
