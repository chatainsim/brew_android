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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.easter.brewhome.BrewViewModel
import fr.easter.brewhome.R
import fr.easter.brewhome.calc.RecipeEstimator
import fr.easter.brewhome.data.Draft
import fr.easter.brewhome.data.Recipe
import fr.easter.brewhome.data.RecipeIngredientPut
import fr.easter.brewhome.data.RecipePost
import fr.easter.brewhome.data.parsedIngredients

// Types d'ajout du houblon, mêmes valeurs que le site
private val hopTypes = listOf("ebullition", "whirlpool", "dryhop")

// Moments d'ajout d'un ingrédient « autre » (sucre, miel, épices…)
private val otherTypes =
    listOf("empatage", "sparge", "ebullition", "whirlpool", "flameout", "fermentation", "packaging")

/** Ces moments demandent une durée (minutes restantes d'ébullition). */
private fun needsMinutes(type: String) = type == "ebullition" || type == "whirlpool"

@Composable
private fun hopTypeLabel(type: String): String = stringResource(
    when (type) {
        "whirlpool" -> R.string.hop_whirlpool
        "dryhop" -> R.string.hop_dryhop
        else -> R.string.hop_boil
    },
)

/** Ingrédient en cours d'édition ; les champs invisibles du serveur sont conservés. */
private data class EditRecipeIng(
    val name: String,
    val category: String,
    val quantity: String,
    val unit: String,
    val hopTime: String = "",
    val hopType: String = "ebullition",
    val hopDays: String = "",
    val otherType: String = "ebullition",
    val otherTime: String = "",
    // Conservés tels quels pour ne pas les perdre au PUT
    val ebc: Double? = null,
    val alpha: Double? = null,
    val notes: String? = null,
    val inventoryItemId: Int? = null,
)

/**
 * Éditeur de recette : recipeId == null → création. [fromDraftId] pré-remplit
 * la nouvelle recette depuis un brouillon (transfert, comme sur le site).
 */
@Composable
fun RecipeEditScreen(
    vm: BrewViewModel,
    recipeId: Int?,
    fromDraftId: Int? = null,
    onSaved: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val existing = recipeId?.let { id -> state.recipes.find { it.id == id } }
    if (recipeId != null && existing == null) {
        EmptyHint(stringResource(R.string.recipe_not_found))
        return
    }
    val fromDraft = remember(fromDraftId) {
        fromDraftId?.let { id -> state.drafts.find { it.id == id } }
    }

    var name by rememberSaveable { mutableStateOf(existing?.name ?: fromDraft?.title ?: "") }
    var style by rememberSaveable { mutableStateOf(existing?.style ?: fromDraft?.style ?: "") }
    var volume by rememberSaveable {
        mutableStateOf((existing?.volume ?: fromDraft?.volume)?.let(::numToField) ?: "20")
    }
    var mashTemp by rememberSaveable { mutableStateOf(existing?.mashTemp?.let(::numToField) ?: "66") }
    var mashTime by rememberSaveable { mutableStateOf(existing?.mashTime?.toString() ?: "60") }
    var boilTime by rememberSaveable { mutableStateOf(existing?.boilTime?.toString() ?: "60") }
    var fermTemp by rememberSaveable { mutableStateOf(existing?.fermTemp?.let(::numToField) ?: "20") }
    var fermTime by rememberSaveable { mutableStateOf(existing?.fermTime?.toString() ?: "14") }
    var notes by rememberSaveable {
        mutableStateOf(existing?.notes ?: fromDraft?.let(::draftNotes) ?: "")
    }
    val ings = remember {
        mutableStateListOf<EditRecipeIng>().apply {
            if (existing != null) seedFrom(existing) else seedFromDraft(fromDraft)
        }
    }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.loadCatalog()
        vm.loadRecipeExtras()
    }
    val catalog by vm.catalog.collectAsState()
    val bjcp by vm.bjcp.collectAsState()
    val costSettings by vm.costSettings.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.label_name)) },
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
            NumField(volume, { volume = it }, stringResource(R.string.label_volume_l), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumField(mashTemp, { mashTemp = it }, stringResource(R.string.recipe_mash_temp), Modifier.weight(1f))
            NumField(mashTime, { mashTime = it }, stringResource(R.string.recipe_mash_time), Modifier.weight(1f))
            NumField(boilTime, { boilTime = it }, stringResource(R.string.recipe_boil_time), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumField(fermTemp, { fermTemp = it }, stringResource(R.string.recipe_ferm_temp), Modifier.weight(1f))
            NumField(fermTime, { fermTime = it }, stringResource(R.string.recipe_ferm_time), Modifier.weight(1f))
        }

        // ── Estimations en direct : recalculées à chaque frappe ──
        run {
            val settings = costSettings
            fun num(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()
            val estIngs = ings.filter { it.name.isNotBlank() }.map { ing ->
                val cat = catalog.find { it.name.equals(ing.name.trim(), ignoreCase = true) }
                RecipeEstimator.Ing(
                    name = ing.name.trim(),
                    category = ing.category,
                    quantity = num(ing.quantity) ?: 0.0,
                    unit = ing.unit,
                    hopTime = ing.hopTime.trim().toIntOrNull(),
                    hopType = ing.hopType,
                    alpha = ing.alpha ?: cat?.alpha,
                    ebc = ing.ebc ?: cat?.ebc,
                    gu = cat?.gu,
                    inventoryItemId = ing.inventoryItemId,
                )
            }
            if (estIngs.isNotEmpty()) {
                val vol = num(volume)
                val est = RecipeEstimator.estimates(
                    estIngs, vol, existing?.brewhouseEfficiency,
                    settings?.ibuFormula ?: "tinseth",
                )
                val waterPlan = RecipeEstimator.water(
                    vol, num(boilTime), existing?.mashRatio,
                    existing?.evapRate, existing?.grainAbsorption,
                    RecipeEstimator.grainKg(estIngs),
                    existing?.waterMashOverride, existing?.waterSpargeOverride,
                )
                val cost = RecipeEstimator.cost(
                    estIngs, state.inventory,
                    settings?.waterPricePerL, waterPlan?.total,
                    settings?.gasPerBrew ?: 0.0, settings?.elecPerBrew ?: 0.0,
                )
                RecipeEstimatesCard(
                    est = est,
                    style = bjcp?.find { it.name == style.trim() },
                    water = waterPlan,
                    cost = cost,
                    volume = vol,
                    ibuFormula = settings?.ibuFormula ?: "tinseth",
                )
            }
        }

        // Comme le site : une section par catégorie, malts en tête
        draftCategories.forEach { cat ->
            IngredientSectionHeader(cat)
            val indices = ings.withIndex().filter { it.value.category == cat }.map { it.index }
            if (indices.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        indices.forEachIndexed { pos, i ->
                            if (pos > 0) HorizontalDivider(Modifier.padding(vertical = 10.dp))
                            RecipeIngredientEditor(
                                ing = ings[i],
                                suggest = { c, q -> ingredientSuggestions(catalog, state.inventory, c, q) },
                                onChange = { ings[i] = it },
                                onDelete = { ings.removeAt(i) },
                            )
                        }
                    }
                }
            }
            AddIngredientButton(cat) {
                ings.add(EditRecipeIng("", cat, "", unitsByCategory.getValue(cat).first()))
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
                vm.saveRecipe(recipeId, buildPost(
                    name, style, volume, mashTemp, mashTime, boilTime, fermTemp, fermTime, notes, ings,
                    draftId = fromDraftId,
                    brewDate = fromDraft?.targetDate,
                )) { onSaved() }
                saving = false
            },
            enabled = name.isNotBlank() && !saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (recipeId == null) stringResource(R.string.create_recipe)
                else stringResource(R.string.save),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Notes de la recette créée depuis un brouillon : ligne « objectif » + notes. */
private fun draftNotes(draft: Draft): String {
    val eventLine = if (!draft.eventLabel.isNullOrBlank() && !draft.targetDate.isNullOrBlank()) {
        val date = runCatching {
            java.time.LocalDate.parse(draft.targetDate)
                .format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale.FRANCE))
        }.getOrDefault(draft.targetDate)
        "📅 Objectif : ${draft.eventLabel} — $date"
    } else null
    return listOfNotNull(eventLine, draft.notes?.trim()?.ifBlank { null })
        .joinToString("\n\n")
}

/** Ingrédients du brouillon → lignes d'édition (houblons en ébullition par défaut). */
private fun MutableList<EditRecipeIng>.seedFromDraft(draft: Draft?) {
    draft?.parsedIngredients()?.filter { it.name.isNotBlank() }?.forEach {
        val cat = it.category.lowercase().takeIf { c -> c in draftCategories } ?: "autre"
        add(EditRecipeIng(
            name = it.name,
            category = cat,
            quantity = it.quantity?.let(::numToField) ?: "",
            unit = it.unit ?: unitsByCategory.getValue(cat).first(),
        ))
    }
}

private fun MutableList<EditRecipeIng>.seedFrom(existing: Recipe?) {
    existing?.ingredients?.forEach {
        val cat = it.category.lowercase().takeIf { c -> c in draftCategories } ?: "autre"
        add(EditRecipeIng(
            name = it.name,
            category = cat,
            quantity = numToField(it.quantity),
            unit = it.unit,
            hopTime = it.hopTime?.toString() ?: "",
            hopType = it.hopType ?: "ebullition",
            hopDays = it.hopDays?.toString() ?: "",
            otherType = it.otherType ?: "ebullition",
            otherTime = it.otherTime?.let(::numToField) ?: "",
            ebc = it.ebc,
            alpha = it.alpha,
            notes = it.notes,
            inventoryItemId = it.inventoryItemId,
        ))
    }
}

private fun buildPost(
    name: String,
    style: String,
    volume: String,
    mashTemp: String,
    mashTime: String,
    boilTime: String,
    fermTemp: String,
    fermTime: String,
    notes: String,
    ings: List<EditRecipeIng>,
    draftId: Int? = null,
    brewDate: String? = null,
): RecipePost {
    fun num(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()
    return RecipePost(
        name = name.trim(),
        style = style.trim().ifBlank { null },
        volume = num(volume),
        mashTemp = num(mashTemp),
        mashTime = num(mashTime),
        boilTime = num(boilTime),
        fermTemp = num(fermTemp),
        fermTime = num(fermTime),
        notes = notes.trim().ifBlank { null },
        brewDate = brewDate,
        draftId = draftId,
        ingredients = ings.filter { it.name.isNotBlank() }.map { ing ->
            val isHop = ing.category == "houblon"
            val isOther = ing.category == "autre"
            RecipeIngredientPut(
                name = ing.name.trim(),
                category = ing.category,
                quantity = num(ing.quantity),
                unit = ing.unit,
                hopTime = if (isHop && ing.hopType != "dryhop") ing.hopTime.trim().toIntOrNull() else null,
                hopType = if (isHop) ing.hopType else null,
                hopDays = if (isHop && ing.hopType == "dryhop") ing.hopDays.trim().toIntOrNull() else null,
                ebc = ing.ebc,
                alpha = ing.alpha,
                notes = ing.notes,
                inventoryItemId = ing.inventoryItemId,
                otherType = if (isOther) ing.otherType else null,
                otherTime = if (isOther && needsMinutes(ing.otherType)) num(ing.otherTime) else null,
            )
        },
    )
}

@Composable
private fun NumField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
private fun RecipeIngredientEditor(
    ing: EditRecipeIng,
    suggest: (String, String) -> List<String>,
    onChange: (EditRecipeIng) -> Unit,
    onDelete: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NameFieldWithSuggestions(
                value = ing.name,
                suggestions = { q -> suggest(ing.category, q) },
                onChange = { onChange(ing.copy(name = it)) },
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.cd_delete_ingredient),
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
        }
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
        if (ing.category == "houblon") {
            // Libellés résolus ici : optionLabel n'est pas un contexte composable
            val hopLabels = hopTypes.associateWith { hopTypeLabel(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallDropdown(
                    value = hopLabels[ing.hopType] ?: ing.hopType,
                    options = hopTypes,
                    optionLabel = { hopLabels.getValue(it) },
                    onSelect = { onChange(ing.copy(hopType = it)) },
                    modifier = Modifier.weight(1.4f),
                )
                if (ing.hopType == "dryhop") {
                    SoftField(
                        value = ing.hopDays,
                        onChange = { onChange(ing.copy(hopDays = it)) },
                        placeholder = stringResource(R.string.hop_days),
                        label = stringResource(R.string.hop_days),
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    SoftField(
                        value = ing.hopTime,
                        onChange = { onChange(ing.copy(hopTime = it)) },
                        placeholder = stringResource(R.string.hop_minutes),
                        label = stringResource(R.string.hop_minutes),
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (ing.category == "autre") {
            // Moment d'ajout + minutes restantes (pour ébullition / whirlpool)
            val otherLabels = otherTypes.associateWith { additionLabel(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallDropdown(
                    value = otherLabels[ing.otherType] ?: ing.otherType,
                    options = otherTypes,
                    optionLabel = { otherLabels.getValue(it) },
                    onSelect = { onChange(ing.copy(otherType = it)) },
                    modifier = Modifier.weight(1.4f),
                )
                if (needsMinutes(ing.otherType)) {
                    SoftField(
                        value = ing.otherTime,
                        onChange = { onChange(ing.copy(otherTime = it)) },
                        placeholder = stringResource(R.string.hop_minutes),
                        label = stringResource(R.string.hop_minutes),
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
