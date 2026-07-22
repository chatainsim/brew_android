package fr.easter.brewhome.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.easter.brewhome.BrewViewModel
import fr.easter.brewhome.R
import fr.easter.brewhome.data.CatalogItem
import fr.easter.brewhome.data.CatalogPost

/** Gestion du catalogue d'ingrédients de référence (autocomplétion des recettes). */
@Composable
fun CatalogScreen(vm: BrewViewModel) {
    val catalog by vm.catalog.collectAsState()
    LaunchedEffect(Unit) { vm.reloadCatalog() }
    var editing by remember { mutableStateOf<CatalogItem?>(null) }
    var creating by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        if (catalog.isEmpty()) {
            EmptyHint(stringResource(R.string.catalog_empty), Icons.Filled.Add)
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                val grouped = catalog.groupBy { it.category.lowercase() }
                val cats = categoryOrder.filter { grouped.containsKey(it) } +
                    grouped.keys.filterNot { it in categoryOrder }.sorted()
                cats.forEach { cat ->
                    item(key = "h_$cat") {
                        Text(
                            categoryLabel(cat),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                        )
                    }
                    items(grouped.getValue(cat).sortedBy { it.name.lowercase() }, key = { it.id }) { item ->
                        CatalogRow(item) { editing = item }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { creating = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.catalog_add))
        }
    }

    if (creating) {
        CatalogItemDialog(
            item = null,
            onDismiss = { creating = false },
            onSave = { post -> vm.saveCatalogItem(null, post) { creating = false } },
            onDelete = null,
        )
    }
    editing?.let { item ->
        CatalogItemDialog(
            item = item,
            onDismiss = { editing = null },
            onSave = { post -> vm.saveCatalogItem(item.id, post) { editing = null } },
            onDelete = { vm.deleteCatalogItem(item.id) { editing = null } },
        )
    }
}

@Composable
private fun CatalogRow(item: CatalogItem, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                val details = listOfNotNull(
                    item.subcategory,
                    item.ebc?.let { "${fmtQty(it)} EBC" },
                    item.alpha?.let { "${fmtQty(it)} % α" },
                    item.gu?.let { "GU ${fmtQty(it)}" },
                ).joinToString(" · ")
                if (details.isNotEmpty()) {
                    Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Composable
private fun CatalogItemDialog(
    item: CatalogItem?,
    onDismiss: () -> Unit,
    onSave: (CatalogPost) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(item?.name ?: "") }
    var category by remember { mutableStateOf(item?.category?.lowercase() ?: "malt") }
    var subcategory by remember { mutableStateOf(item?.subcategory ?: "") }
    var ebc by remember { mutableStateOf(item?.ebc?.let { fmtQty(it).replace(',', '.') } ?: "") }
    var gu by remember { mutableStateOf(item?.gu?.let { fmtQty(it).replace(',', '.') } ?: "") }
    var alpha by remember { mutableStateOf(item?.alpha?.let { fmtQty(it).replace(',', '.') } ?: "") }

    fun num(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (item == null) stringResource(R.string.catalog_add)
                else stringResource(R.string.catalog_edit),
            )
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.label_name)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                if (item == null) {
                    Text(stringResource(R.string.label_category), style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        categoryOrder.forEach { c ->
                            FilterChip(
                                selected = category == c,
                                onClick = { category = c },
                                label = { Text(categoryLabel(c), style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                } else {
                    Text(
                        stringResource(R.string.label_category) + " : " + categoryLabel(category),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                OutlinedTextField(
                    value = subcategory, onValueChange = { subcategory = it },
                    label = { Text(stringResource(R.string.catalog_subcategory)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = ebc, onValueChange = { ebc = it },
                        label = { Text(stringResource(R.string.inv_ebc)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = alpha, onValueChange = { alpha = it },
                        label = { Text(stringResource(R.string.inv_alpha)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = gu, onValueChange = { gu = it },
                    label = { Text(stringResource(R.string.catalog_gu)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(CatalogPost(
                        name = name.trim(),
                        category = category,
                        subcategory = subcategory.trim().ifBlank { null },
                        ebc = num(ebc),
                        gu = num(gu),
                        alpha = num(alpha),
                    ))
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
