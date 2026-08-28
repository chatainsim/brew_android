package fr.easter.brewhome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.material3.DropdownMenu
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.res.stringResource
import fr.easter.brewhome.BrewViewModel
import fr.easter.brewhome.R
import fr.easter.brewhome.data.CatalogItem
import fr.easter.brewhome.data.Draft
import fr.easter.brewhome.data.DraftIngredient
import fr.easter.brewhome.data.DraftPut
import fr.easter.brewhome.data.InventoryItem
import fr.easter.brewhome.data.parsedIngredients
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Mêmes unités proposées que l'éditeur de brouillons du site
internal val unitsByCategory = mapOf(
    "malt" to listOf("kg", "g"),
    "houblon" to listOf("g", "kg"),
    "levure" to listOf("sachet", "g", "kg"),
    "autre" to listOf("g", "kg", "mL", "L", "pièce", "sachet"),
)

internal val draftCategories = listOf("malt", "houblon", "levure", "autre")
private val statusChoices = listOf("idea", "in_progress", "ready")

private data class EditIng(
    val name: String,
    val category: String,
    val quantity: String,
    val unit: String,
)

internal fun numToField(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

/** En-tête de section d'ingrédients : pastille de couleur + nom de la catégorie. */
@Composable
internal fun IngredientSectionHeader(cat: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 4.dp),
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(categoryColor(cat)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            categoryLabel(cat),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Bouton « Ajouter un malt / houblon / … » sous chaque section. */
@Composable
internal fun AddIngredientButton(cat: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick) {
        Icon(
            Icons.Filled.Add,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Text(
            stringResource(
                when (cat) {
                    "malt" -> R.string.add_malt
                    "houblon" -> R.string.add_hop
                    "levure" -> R.string.add_yeast
                    else -> R.string.add_other
                },
            ),
            Modifier.padding(start = 6.dp),
        )
    }
}

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
        EmptyHint(stringResource(R.string.draft_not_found))
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
    var showAi by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.loadCatalog() }
    val catalog by vm.catalog.collectAsState()
    val aiSuggesting by vm.aiSuggesting.collectAsState()

    if (showAi) {
        AiSuggestDialog(
            initialStyle = style,
            initialVolume = volume,
            busy = aiSuggesting,
            onDismiss = { if (!aiSuggesting) showAi = false },
            onGenerate = { s, v, n ->
                vm.suggestDraft(s, n, v) { result ->
                    if (result.title.isNotBlank() && title.isBlank()) title = result.title
                    result.notes?.takeIf { it.isNotBlank() }?.let {
                        notes = if (notes.isBlank()) it else "$notes\n\n$it"
                    }
                    result.ingredients.filter { it.name.isNotBlank() }.forEach { ing ->
                        val cat = ing.type.lowercase().takeIf { it in draftCategories } ?: "autre"
                        ings.add(EditIng(
                            name = ing.name,
                            category = cat,
                            quantity = ing.qty?.let(::numToField) ?: "",
                            unit = ing.unit ?: unitsByCategory.getValue(cat).first(),
                        ))
                    }
                    showAi = false
                }
            },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(onClick = { showAi = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.ai_suggest), Modifier.padding(start = 8.dp))
        }
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(stringResource(R.string.label_title)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = style,
                onValueChange = { style = it },
                label = { Text(stringResource(R.string.label_style)) },
                singleLine = true,
                modifier = Modifier.weight(2f),
            )
            OutlinedTextField(
                value = volume,
                onValueChange = { volume = it },
                label = { Text(stringResource(R.string.label_volume_l)) },
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
            DateField(
                value = targetDate,
                onValueChange = { targetDate = it },
                label = stringResource(R.string.label_target_date),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = eventLabel,
                onValueChange = { eventLabel = it },
                label = { Text(stringResource(R.string.label_event)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        // Comme le site : une section par catégorie, malts en tête
        draftCategories.forEach { cat ->
            IngredientSectionHeader(cat)
            val indices = ings.withIndex().filter { it.value.category == cat }.map { it.index }
            indices.forEach { i ->
                IngredientEditor(
                    ing = ings[i],
                    suggest = { c, q -> ingredientSuggestions(catalog, state.inventory, c, q) },
                    onChange = { ings[i] = it },
                    onDelete = { ings.removeAt(i) },
                )
            }
            AddIngredientButton(cat) {
                ings.add(EditIng("", cat, "", unitsByCategory.getValue(cat).first()))
            }
        }

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text(stringResource(R.string.notes)) },
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
            Text(if (draftId == null) stringResource(R.string.create_draft) else stringResource(R.string.save))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun IngredientEditor(
    ing: EditIng,
    suggest: (String, String) -> List<String>,
    onChange: (EditIng) -> Unit,
    onDelete: () -> Unit,
) {
    IngredientTile(category = ing.category, onDelete = onDelete) {
        NameFieldWithSuggestions(
            value = ing.name,
            suggestions = { q -> suggest(ing.category, q) },
            onChange = { onChange(ing.copy(name = it)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SoftField(
                value = ing.quantity,
                onChange = { onChange(ing.copy(quantity = it)) },
                placeholder = stringResource(R.string.qty_short),
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
            )
            SmallDropdown(
                value = ing.unit,
                options = unitsByCategory.getValue(ing.category),
                optionLabel = { it },
                onSelect = { onChange(ing.copy(unit = it)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Dialogue de suggestion IA : style + volume + précisions → génère la recette. */
@Composable
private fun AiSuggestDialog(
    initialStyle: String,
    initialVolume: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onGenerate: (style: String, volume: Double?, notes: String) -> Unit,
) {
    var style by remember { mutableStateOf(initialStyle) }
    var volume by remember { mutableStateOf(initialVolume.ifBlank { "20" }) }
    var notes by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ai_suggest_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = style, onValueChange = { style = it },
                    label = { Text(stringResource(R.string.ai_suggest_style)) },
                    singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = volume, onValueChange = { volume = it },
                    label = { Text(stringResource(R.string.ai_suggest_volume)) },
                    singleLine = true, enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.ai_suggest_notes)) },
                    enabled = !busy, modifier = Modifier.fillMaxWidth(),
                )
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.CircularProgressIndicator(
                            Modifier.size(18.dp), strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.ai_suggest_wait),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onGenerate(style.trim(), volume.trim().replace(',', '.').toDoubleOrNull(), notes.trim()) },
                enabled = !busy,
            ) { Text(stringResource(R.string.ai_suggest_go)) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/**
 * Tuile d'un ingrédient : petit bloc arrondi et teinté avec une poignée de
 * couleur de catégorie à gauche et un bouton supprimer discret en haut.
 */
@Composable
internal fun IngredientTile(
    category: String,
    onDelete: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(categoryColor(category)),
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                content()
            }
            IconButton(onClick = onDelete, modifier = Modifier.padding(top = 2.dp)) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.cd_delete_ingredient),
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** Champ texte compact aux bordures douces, pour les lignes d'ingrédients. */
@Composable
internal fun SoftField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    label: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder) },
        label = label?.let { { Text(it) } },
        singleLine = true,
        shape = softFieldShape,
        colors = softFieldColors(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier,
    )
}

internal val softFieldShape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)

@Composable
internal fun softFieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
)

/**
 * Champ nom avec liste de suggestions filtrée à la frappe (catalogue +
 * inventaire BrewHome). Le menu n'est pas focusable pour laisser le clavier
 * ouvert pendant la saisie.
 */
@Composable
internal fun NameFieldWithSuggestions(
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
            placeholder = { Text(stringResource(R.string.label_name)) },
            singleLine = true,
            shape = softFieldShape,
            colors = softFieldColors(),
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
internal fun SmallDropdown(
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
            shape = softFieldShape,
            colors = softFieldColors(),
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
