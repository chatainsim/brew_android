package fr.easter.brewhome.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.material3.DropdownMenu
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.window.PopupProperties
import fr.easter.brewhome.BrewViewModel
import fr.easter.brewhome.data.CatalogItem
import fr.easter.brewhome.data.Draft
import fr.easter.brewhome.data.DraftIngredient
import fr.easter.brewhome.data.DraftPut
import fr.easter.brewhome.data.InventoryItem
import fr.easter.brewhome.data.parsedIngredients
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Mêmes unités proposées que l'éditeur de brouillons du site
private val unitsByCategory = mapOf(
    "malt" to listOf("kg", "g"),
    "houblon" to listOf("g", "kg"),
    "levure" to listOf("sachet", "g", "kg"),
    "autre" to listOf("g", "kg", "mL", "L", "pièce", "sachet"),
)

private val draftCategories = listOf("malt", "houblon", "levure", "autre")
private val statusChoices = listOf("idea", "in_progress", "ready")

private data class EditIng(
    val name: String,
    val category: String,
    val quantity: String,
    val unit: String,
)

private fun numToField(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

/**
 * Suggestions de noms pour une catégorie : catalogue d'ingrédients filtré à la
 * frappe, complété par les articles de l'inventaire absents du catalogue —
 * même logique que draftIngSearch() dans bh-brouillons.js.
 */
fun ingredientSuggestions(
    catalog: List<CatalogItem>,
    inventory: List<InventoryItem>,
    category: String,
    query: String,
): List<String> {
    val q = query.trim().lowercase()
    fun matches(name: String) = q.isEmpty() || name.lowercase().contains(q)
    val fromCatalog = catalog
        .filter { it.category == category && matches(it.name) }
        .map { it.name }
    val fromInventory = inventory
        .filter { it.category == category && matches(it.name) }
        .map { it.name }
        .filter { inv -> fromCatalog.none { it.equals(inv, ignoreCase = true) } }
    return (fromCatalog + fromInventory).distinctBy { it.lowercase() }
}

private val putJson = Json { encodeDefaults = true }

/** Éditeur de brouillon : draftId == null → création. */
@Composable
fun DraftEditScreen(vm: BrewViewModel, draftId: Int?, onSaved: (Draft) -> Unit) {
    val state by vm.state.collectAsState()
    val existing = draftId?.let { id -> state.drafts.find { it.id == id } }
    if (draftId != null && existing == null) {
        EmptyHint("Brouillon introuvable.")
        return
    }

    var title by rememberSaveable { mutableStateOf(existing?.title ?: "") }
    var style by rememberSaveable { mutableStateOf(existing?.style ?: "") }
    var volume by rememberSaveable { mutableStateOf(existing?.volume?.let(::numToField) ?: "") }
    var status by rememberSaveable { mutableStateOf(existing?.status ?: "idea") }
    var targetDate by rememberSaveable { mutableStateOf(existing?.targetDate ?: "") }
    var eventLabel by rememberSaveable { mutableStateOf(existing?.eventLabel ?: "") }
    var notes by rememberSaveable { mutableStateOf(existing?.notes ?: "") }
    val ings = remember {
        mutableStateListOf<EditIng>().apply {
            existing?.parsedIngredients()?.forEach {
                add(EditIng(
                    it.name,
                    it.category.takeIf { c -> c in draftCategories } ?: "autre",
                    it.quantity?.let(::numToField) ?: "",
                    it.unit ?: unitsByCategory.getValue(it.category.takeIf { c -> c in draftCategories } ?: "autre").first(),
                ))
            }
        }
    }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.loadCatalog() }
    val catalog by vm.catalog.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Titre") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = style,
                onValueChange = { style = it },
                label = { Text("Style") },
                singleLine = true,
                modifier = Modifier.weight(2f),
            )
            OutlinedTextField(
                value = volume,
                onValueChange = { volume = it },
                label = { Text("Volume (L)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            statusChoices.forEachIndexed { i, s ->
                SegmentedButton(
                    selected = status == s,
                    onClick = { status = s },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = statusChoices.size),
                ) { Text(draftStatusLabel(s)) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = targetDate,
                onValueChange = { targetDate = it },
                label = { Text("Date cible") },
                placeholder = { Text("2026-08-01") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = eventLabel,
                onValueChange = { eventLabel = it },
                label = { Text("Événement") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        Text("Ingrédients", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        ings.forEachIndexed { i, ing ->
            IngredientEditor(
                ing = ing,
                suggest = { cat, q -> ingredientSuggestions(catalog, state.inventory, cat, q) },
                onChange = { ings[i] = it },
                onDelete = { ings.removeAt(i) },
            )
        }
        OutlinedButton(
            onClick = { ings.add(EditIng("", "malt", "", "kg")) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("Ajouter un ingrédient", Modifier.padding(start = 8.dp))
        }

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = {
                saving = true
                val kept = ings.filter { it.name.isNotBlank() }.map {
                    DraftIngredient(
                        name = it.name.trim(),
                        category = it.category,
                        quantity = it.quantity.trim().replace(',', '.').toDoubleOrNull(),
                        unit = it.unit,
                    )
                }
                val put = DraftPut(
                    title = title.trim(),
                    style = style.trim().ifBlank { null },
                    volume = volume.trim().replace(',', '.').toDoubleOrNull(),
                    ingredients = putJson.encodeToString(kept),
                    notes = notes.trim().ifBlank { null },
                    // Le PUT écrase tout : on repasse la couleur et les photos existantes
                    color = existing?.color,
                    images = existing?.images,
                    targetDate = targetDate.trim().ifBlank { null },
                    eventLabel = eventLabel.trim().ifBlank { null },
                    status = status,
                )
                vm.saveDraft(draftId, put) { onSaved(it) }
                saving = false
            },
            enabled = title.isNotBlank() && !saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (draftId == null) "Créer le brouillon" else "Enregistrer")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IngredientEditor(
    ing: EditIng,
    suggest: (String, String) -> List<String>,
    onChange: (EditIng) -> Unit,
    onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallDropdown(
                    value = categoryLabel(ing.category),
                    options = draftCategories,
                    optionLabel = { categoryLabel(it) },
                    onSelect = { cat ->
                        val units = unitsByCategory.getValue(cat)
                        onChange(ing.copy(
                            category = cat,
                            unit = if (ing.unit in units) ing.unit else units.first(),
                        ))
                    },
                    modifier = Modifier.weight(1f),
                )
                NameFieldWithSuggestions(
                    value = ing.name,
                    suggestions = { q -> suggest(ing.category, q) },
                    onChange = { onChange(ing.copy(name = it)) },
                    modifier = Modifier.weight(2f),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = ing.quantity,
                    onValueChange = { onChange(ing.copy(quantity = it)) },
                    placeholder = { Text("Qté") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                SmallDropdown(
                    value = ing.unit,
                    options = unitsByCategory.getValue(ing.category),
                    optionLabel = { it },
                    onSelect = { onChange(ing.copy(unit = it)) },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Supprimer l'ingrédient",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/**
 * Champ nom avec liste de suggestions filtrée à la frappe (catalogue +
 * inventaire BrewHome). Le menu n'est pas focusable pour laisser le clavier
 * ouvert pendant la saisie.
 */
@Composable
private fun NameFieldWithSuggestions(
    value: String,
    suggestions: (String) -> List<String>,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val items = if (focused) {
        suggestions(value).filterNot { it.equals(value.trim(), ignoreCase = true) }.take(10)
    } else emptyList()
    Box(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            placeholder = { Text("Nom") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
        )
        DropdownMenu(
            expanded = items.isNotEmpty(),
            onDismissRequest = {},
            properties = PopupProperties(focusable = false),
            modifier = Modifier.heightIn(max = 260.dp),
        ) {
            items.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { onChange(name) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmallDropdown(
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
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
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
