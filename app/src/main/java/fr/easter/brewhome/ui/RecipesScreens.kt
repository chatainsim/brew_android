package fr.easter.brewhome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import fr.easter.brewhome.BrewViewModel
import fr.easter.brewhome.R
import fr.easter.brewhome.calc.RecipeEstimator
import fr.easter.brewhome.calc.StockCheck
import fr.easter.brewhome.data.Draft
import fr.easter.brewhome.data.Recipe
import fr.easter.brewhome.data.RecipeIngredient
import fr.easter.brewhome.data.ShoppingItem
import fr.easter.brewhome.data.parsedIngredients
import fr.easter.brewhome.data.parsedImages

// Couleurs des états de stock (mêmes teintes que le site)
private val StockOk = Color(0xFF10B981)
private val StockLow = Color(0xFFF59E0B)
private val StockMissing = Color(0xFFEF4444)
private val StockMismatch = Color(0xFFA78BFA)

private fun stockColor(status: StockCheck.Status): Color = when (status) {
    StockCheck.Status.OK -> StockOk
    StockCheck.Status.LOW -> StockLow
    StockCheck.Status.MISSING -> StockMissing
    StockCheck.Status.UNIT_MISMATCH -> StockMismatch
}

@Composable
fun RecipesScreen(
    vm: BrewViewModel,
    onOpen: (Int) -> Unit,
    onOpenDraft: (Int) -> Unit,
    onNewDraft: () -> Unit,
    onNewRecipe: () -> Unit = {},
) {
    val state by vm.state.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var showDrafts by rememberSaveable { mutableStateOf(false) }

    RefreshableContent(vm) {
        Column(Modifier.fillMaxSize()) {
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                SegmentedButton(
                    selected = !showDrafts,
                    onClick = { showDrafts = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text(stringResource(R.string.recipes_seg, state.recipes.size)) }
                SegmentedButton(
                    selected = showDrafts,
                    onClick = { showDrafts = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text(stringResource(R.string.drafts_seg, state.drafts.size)) }
            }
            if (showDrafts) DraftsList(state.drafts, query, { query = it }, onOpenDraft, onNewDraft)
            else RecipesList(state.recipes, state, query, { query = it }, onOpen, onNewRecipe)
        }
    }
}

@Composable
private fun RecipesList(
    recipes: List<Recipe>,
    state: fr.easter.brewhome.UiState,
    query: String,
    onQuery: (String) -> Unit,
    onOpen: (Int) -> Unit,
    onNew: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (recipes.isEmpty()) {
                EmptyHint(stringResource(R.string.recipes_empty))
            } else {
                val filtered = recipes.filter { recipe ->
                    query.isBlank() || listOfNotNull(recipe.name, recipe.style)
                        .any { it.contains(query, ignoreCase = true) }
                }
                SearchField(query, onQuery, stringResource(R.string.recipes_search))
                if (filtered.isEmpty()) {
                    EmptyHint(stringResource(R.string.no_results, query))
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = 12.dp, end = 12.dp, top = 12.dp, bottom = 88.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(filtered, key = { it.id }) { recipe ->
                            RecipeCard(recipe, state.inventory, onOpen, Modifier.animateItem())
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = onNew,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.title_recipe_new))
        }
    }
}

@Composable
private fun RecipeCard(
    recipe: Recipe,
    inventory: List<fr.easter.brewhome.data.InventoryItem>,
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stock = remember(recipe.ingredients, inventory) {
        if (recipe.ingredients.isEmpty()) null else StockCheck.check(recipe.ingredients, inventory)
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpen(recipe.id) },
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    recipe.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (stock != null) {
                    StockBadge(stock)
                    Spacer(Modifier.width(6.dp))
                }
                recipe.batchNo?.let {
                    Text(
                        "#$it",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            val subtitle = listOfNotNull(
                recipe.style,
                recipe.volume?.let { "${fmtQty(it)} L" },
                stringResource(R.string.n_ingredients, recipe.ingredients.size),
            ).joinToString(" · ")
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            if (recipe.rating != null) {
                Spacer(Modifier.height(4.dp))
                StarRating(recipe.rating)
            }
        }
    }
}

/** Coche verte si tout le stock est disponible, triangle coloré sinon. */
@Composable
private fun StockBadge(stock: StockCheck.Result) {
    if (stock.allOk) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = stringResource(R.string.stock_banner_ok),
            tint = StockOk,
            modifier = Modifier.size(20.dp),
        )
    } else {
        val tint = when {
            stock.nMissing > 0 -> StockMissing
            stock.nLow > 0 -> StockLow
            else -> StockMismatch
        }
        Icon(
            Icons.Filled.WarningAmber,
            contentDescription = stringResource(R.string.stock_incomplete),
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun RecipeDetailScreen(vm: BrewViewModel, recipeId: Int?, onOpenBrew: (Int) -> Unit = {}) {
    val state by vm.state.collectAsState()
    val recipe = state.recipes.find { it.id == recipeId }
    if (recipe == null) {
        EmptyHint(stringResource(R.string.recipe_not_found))
        return
    }
    var showBrew by remember { mutableStateOf(false) }
    if (showBrew) {
        BrewFromRecipeDialog(
            recipe = recipe,
            onDismiss = { showBrew = false },
            onCreate = { post -> vm.createBrew(post) { id -> showBrew = false; onOpenBrew(id) } },
        )
    }
    LaunchedEffect(Unit) {
        vm.loadCatalog()
        vm.loadRecipeExtras()
    }
    val catalog by vm.catalog.collectAsState()
    val bjcp by vm.bjcp.collectAsState()
    val costSettings by vm.costSettings.collectAsState()
    val stock = remember(recipe.ingredients, state.inventory) {
        if (recipe.ingredients.isEmpty()) null
        else StockCheck.check(recipe.ingredients, state.inventory)
    }
    val stockByName = stock?.lines?.associateBy { it.name.trim().lowercase() } ?: emptyMap()
    var showStockDetail by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(recipe.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        val subtitle = listOfNotNull(
            recipe.style,
            recipe.batchNo?.let { stringResource(R.string.recipe_batch_no, it) },
        ).joinToString(" · ")
        if (subtitle.isNotEmpty()) Text(subtitle, color = MaterialTheme.colorScheme.outline)
        if (recipe.rating != null) StarRating(recipe.rating)

        Button(onClick = { showBrew = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Science, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.recipe_brew), Modifier.padding(start = 8.dp))
        }

        if (stock != null) {
            StockBanner(
                stock, state.shopping,
                onClick = { showStockDetail = true },
                onAddToShopping = { vm.addNeedsToShopping(it) },
            )
        }

        // ── Estimations OG/FG/ABV/IBU/EBC + eau + coût, comme le site ──
        if (recipe.ingredients.isNotEmpty()) {
            val settings = costSettings
            val estIngs = remember(recipe, catalog) { estIngredients(recipe.ingredients, catalog) }
            val est = remember(estIngs, recipe, settings) {
                RecipeEstimator.estimates(
                    estIngs, recipe.volume, recipe.brewhouseEfficiency,
                    settings?.ibuFormula ?: "tinseth",
                )
            }
            val waterPlan = remember(estIngs, recipe) {
                RecipeEstimator.water(
                    recipe.volume, recipe.boilTime?.toDouble(), recipe.mashRatio,
                    recipe.evapRate, recipe.grainAbsorption,
                    RecipeEstimator.grainKg(estIngs),
                    recipe.waterMashOverride, recipe.waterSpargeOverride,
                )
            }
            val cost = remember(estIngs, state.inventory, settings, waterPlan) {
                RecipeEstimator.cost(
                    estIngs, state.inventory,
                    settings?.waterPricePerL, waterPlan?.total,
                    settings?.gasPerBrew ?: 0.0, settings?.elecPerBrew ?: 0.0,
                )
            }
            RecipeEstimatesCard(
                est = est,
                style = bjcp?.find { it.name == recipe.style?.trim() },
                water = waterPlan,
                cost = cost,
                volume = recipe.volume,
                ibuFormula = settings?.ibuFormula ?: "tinseth",
            )
        }

        InfoCard {
            InfoLine(stringResource(R.string.label_volume), recipe.volume?.let { "${fmtQty(it)} L" })
            InfoLine(stringResource(R.string.label_mash), recipe.mashTemp?.let { t ->
                "${fmtQty(t)} °C" + (recipe.mashTime?.let { " · $it min" } ?: "")
            })
            InfoLine(stringResource(R.string.label_boil), recipe.boilTime?.let { "$it min" })
            InfoLine(stringResource(R.string.label_ferm), recipe.fermTemp?.let { t ->
                "${fmtQty(t)} °C" + (recipe.fermTime?.let { " · $it jours" } ?: "")
            })
        }

        val grouped = recipe.ingredients.groupBy { it.category.lowercase() }
        val orderedCats = categoryOrder.filter { grouped.containsKey(it) } +
            grouped.keys.filterNot { it in categoryOrder }.sorted()
        // Part de chaque malt dans l'empâtement (en % du poids total)
        val totalMaltKg = grouped["malt"].orEmpty()
            .sumOf { if (it.unit == "kg") it.quantity else it.quantity / 1000 }
        orderedCats.forEach { cat ->
            Text(
                categoryLabel(cat),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    grouped.getValue(cat).forEachIndexed { i, ing ->
                        if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        val gristPct = if (cat == "malt" && totalMaltKg > 0) {
                            val kg = if (ing.unit == "kg") ing.quantity else ing.quantity / 1000
                            kg / totalMaltKg * 100
                        } else null
                        IngredientLine(ing, stockByName[ing.name.trim().lowercase()], gristPct)
                    }
                }
            }
        }

        if (!recipe.notes.isNullOrBlank()) {
            Text(stringResource(R.string.notes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(recipe.notes, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showStockDetail && stock != null) {
        StockDetailDialog(stock) { showStockDetail = false }
    }
}

/** Détail du stock : chaque ingrédient avec ce qu'on a et ce qu'il manque. */
@Composable
private fun StockDetailDialog(stock: StockCheck.Result, onDismiss: () -> Unit) {
    // Manquants et bas d'abord, puis le reste
    val order = listOf(
        StockCheck.Status.MISSING, StockCheck.Status.LOW,
        StockCheck.Status.UNIT_MISMATCH, StockCheck.Status.OK,
    )
    val lines = stock.lines.sortedBy { order.indexOf(it.status) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stock_detail_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                lines.forEach { line ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(stockColor(line.status)),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(line.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(
                                    R.string.stock_detail_need,
                                    StockCheck.formatBase(line.needed, line.base),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        Text(
                            stockLineLabel(line),
                            style = MaterialTheme.typography.bodySmall,
                            color = stockColor(line.status),
                            textAlign = TextAlign.End,
                            modifier = Modifier.widthIn(max = 150.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

private val brewStartStatuses = listOf("planned", "in_progress", "fermenting")

@Composable
private fun BrewFromRecipeDialog(
    recipe: Recipe,
    onDismiss: () -> Unit,
    onCreate: (fr.easter.brewhome.data.BrewCreatePost) -> Unit,
) {
    var name by remember { mutableStateOf(recipe.name) }
    var date by remember { mutableStateOf(java.time.LocalDate.now().toString()) }
    var volume by remember { mutableStateOf(recipe.volume?.let { fmtQty(it).replace(',', '.') } ?: "") }
    var status by remember { mutableStateOf("in_progress") }
    val statusLabels = brewStartStatuses.associateWith { brewStatusLabel(it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.brew_create_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.label_name)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = date, onValueChange = { date = it },
                        label = { Text(stringResource(R.string.label_brew_date)) },
                        placeholder = { Text("2026-08-01") },
                        singleLine = true, modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = volume, onValueChange = { volume = it },
                        label = { Text(stringResource(R.string.label_volume_l)) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(stringResource(R.string.brew_create_status), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    brewStartStatuses.forEach { s ->
                        FilterChip(
                            selected = status == s,
                            onClick = { status = s },
                            label = { Text(statusLabels.getValue(s), style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onCreate(fr.easter.brewhome.data.BrewCreatePost(
                        recipeId = recipe.id,
                        name = name.trim().ifBlank { null },
                        brewDate = date.trim().ifBlank { null },
                        volumeBrewed = volume.trim().replace(',', '.').toDoubleOrNull(),
                        status = status,
                    ))
                },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.recipe_brew)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun StockBanner(
    stock: StockCheck.Result,
    shopping: List<ShoppingItem>,
    onClick: () -> Unit,
    onAddToShopping: (List<StockCheck.Need>) -> Unit,
) {
    val needs = remember(stock, shopping) {
        StockCheck.needs(stock, shopping.map { it.name.trim().lowercase() }.toSet())
    }
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StockBadge(stock)
                Spacer(Modifier.width(10.dp))
                if (stock.allOk) {
                    Text(
                        stringResource(R.string.stock_banner_ok_recipe),
                        color = StockOk,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    val parts = listOfNotNull(
                        stock.nOk.takeIf { it > 0 }?.let { stringResource(R.string.stock_n_ok, it) },
                        stock.nLow.takeIf { it > 0 }?.let { stringResource(R.string.stock_n_low, it) },
                        stock.nMissing.takeIf { it > 0 }?.let { stringResource(R.string.stock_n_missing, it) },
                        stock.nMismatch.takeIf { it > 0 }?.let { stringResource(R.string.stock_n_mismatch, it) },
                    )
                    Text(
                        parts.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.stock_detail_title),
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
            if (needs.isNotEmpty()) {
                TextButton(onClick = { onAddToShopping(needs) }) {
                    Icon(
                        Icons.Filled.AddShoppingCart,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.stock_add_missing, needs.size))
                }
            } else if (stock.nMissing + stock.nLow > 0) {
                Text(
                    stringResource(R.string.stock_all_listed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/** Libellé français d'un moment d'ajout (other_type / hop_type du serveur). */
@Composable
fun additionLabel(type: String): String = when (type) {
    "empatage" -> stringResource(R.string.add_empatage)
    "sparge" -> stringResource(R.string.add_sparge)
    "ebullition" -> stringResource(R.string.hop_boil)
    "flameout" -> stringResource(R.string.add_flameout)
    "whirlpool" -> stringResource(R.string.hop_whirlpool)
    "dryhop" -> stringResource(R.string.hop_dryhop)
    "fermentation" -> stringResource(R.string.add_fermentation)
    "packaging" -> stringResource(R.string.add_packaging)
    else -> type
}

/** Texte de disponibilité d'un ingrédient (« manque 200 g (dispo 1 kg) »…). */
@Composable
private fun stockLineLabel(stock: StockCheck.Line): String = when (stock.status) {
    StockCheck.Status.OK -> stringResource(
        R.string.stock_line_ok,
        StockCheck.formatBase(stock.available, stock.base),
    )
    StockCheck.Status.LOW -> stringResource(
        R.string.stock_line_low,
        StockCheck.formatBase(stock.needed - stock.available, stock.base),
        StockCheck.formatBase(stock.available, stock.base),
    )
    StockCheck.Status.MISSING -> stringResource(R.string.stock_line_missing)
    StockCheck.Status.UNIT_MISMATCH -> stringResource(R.string.stock_line_mismatch)
}

@Composable
private fun IngredientLine(ing: RecipeIngredient, stock: StockCheck.Line?, gristPct: Double? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ing.ebc?.let {
            EbcDot(it)
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(ing.name, style = MaterialTheme.typography.bodyLarge)
            val details = listOfNotNull(
                gristPct?.let { "${fmtQty(kotlin.math.round(it * 10) / 10)} %" },
                ing.hopType?.let { additionLabel(it) },
                if (ing.hopType == "dryhop") ing.hopDays?.let { "$it j" }
                else ing.hopTime?.let { "$it min" },
                ing.otherType?.let { additionLabel(it) },
                ing.otherTime?.let { "${fmtQty(it)} min" },
                ing.alpha?.let { "${fmtQty(it)}% α" },
                ing.ebc?.let { "${fmtQty(it)} EBC" },
                ing.notes,
            ).joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(
                    details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            if (stock != null) {
                Text(
                    stockLineLabel(stock),
                    style = MaterialTheme.typography.bodySmall,
                    color = stockColor(stock.status),
                )
            }
        }
        Text(
            "${fmtQty(ing.quantity)} ${ing.unit}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ── Brouillons ────────────────────────────────────────────────────────────────

fun draftStatusLabel(status: String?): String = when (status) {
    "in_progress" -> "En cours"
    "ready" -> "Prête"
    else -> "Idée"
}

/** Couleurs (fond, texte) de la pastille de statut d'un brouillon. */
@Composable
fun draftStatusColors(status: String?): Pair<Color, Color> = MaterialTheme.colorScheme.let {
    when (status) {
        "in_progress" -> it.primaryContainer to it.onPrimaryContainer
        "ready" -> it.tertiaryContainer to it.onTertiaryContainer
        else -> it.surfaceVariant to it.onSurfaceVariant
    }
}

@Composable
private fun DraftsList(
    drafts: List<Draft>,
    query: String,
    onQuery: (String) -> Unit,
    onOpen: (Int) -> Unit,
    onNew: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (drafts.isEmpty()) {
                EmptyHint(stringResource(R.string.drafts_empty))
            } else {
                val filtered = drafts.filter { d ->
                    query.isBlank() || listOfNotNull(d.title, d.style, d.eventLabel)
                        .any { it.contains(query, ignoreCase = true) }
                }
                SearchField(query, onQuery, stringResource(R.string.drafts_search))
                if (filtered.isEmpty()) {
                    EmptyHint(stringResource(R.string.no_results, query))
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = 12.dp, end = 12.dp, top = 12.dp, bottom = 88.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(filtered, key = { it.id }) { draft ->
                            DraftCard(draft, onOpen, Modifier.animateItem())
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = onNew,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.title_draft_new))
        }
    }
}

@Composable
private fun DraftCard(draft: Draft, onOpen: (Int) -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpen(draft.id) },
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    draft.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                val (container, content) = draftStatusColors(draft.status)
                StatusChip(draftStatusLabel(draft.status), container, content)
            }
            val nIng = draft.parsedIngredients().size
            val subtitle = listOfNotNull(
                draft.style,
                draft.volume?.let { "${fmtQty(it)} L" },
                nIng.takeIf { it > 0 }?.let { stringResource(R.string.n_ingredients, it) },
                draft.targetDate?.let { stringResource(R.string.draft_target, it) },
            ).joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
fun DraftDetailScreen(vm: BrewViewModel, draftId: Int?, onToRecipe: (Int) -> Unit = {}) {
    val state by vm.state.collectAsState()
    val draft = state.drafts.find { it.id == draftId }
    if (draft == null) {
        EmptyHint(stringResource(R.string.draft_not_found))
        return
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(draft.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val (container, content) = draftStatusColors(draft.status)
            StatusChip(draftStatusLabel(draft.status), container, content)
        }
        draft.style?.let { Text(it, color = MaterialTheme.colorScheme.outline) }

        // Photos du brouillon (servies par /api/draft-images/…)
        val images = remember(draft.images) { draft.parsedImages() }
        if (images.isNotEmpty()) {
            var viewing by remember { mutableStateOf<String?>(null) }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(images, key = { it }) { path ->
                    AsyncImage(
                        model = vm.photoUrl(path),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { viewing = path },
                    )
                }
            }
            viewing?.let { path ->
                Dialog(onDismissRequest = { viewing = null }) {
                    AsyncImage(
                        model = vm.photoUrl(path),
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { viewing = null },
                    )
                }
            }
        }

        // Transfert en recette, comme sur le site : éditeur pré-rempli
        OutlinedButton(
            onClick = { onToRecipe(draft.id) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.MenuBook,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(stringResource(R.string.draft_to_recipe), Modifier.padding(start = 8.dp))
        }

        InfoCard {
            InfoLine(stringResource(R.string.label_volume), draft.volume?.let { "${fmtQty(it)} L" })
            InfoLine(stringResource(R.string.label_target_date), draft.targetDate)
            InfoLine(stringResource(R.string.label_event), draft.eventLabel)
            InfoLine(stringResource(R.string.label_updated_on), draft.updatedAt?.take(10))
        }

        val ings = draft.parsedIngredients().filter { it.name.isNotBlank() }
        if (ings.isNotEmpty()) {
            val grouped = ings.groupBy { it.category.lowercase() }
            val cats = categoryOrder.filter { grouped.containsKey(it) } +
                grouped.keys.filterNot { it in categoryOrder }.sorted()
            cats.forEach { cat ->
                Text(
                    categoryLabel(cat),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        grouped.getValue(cat).forEachIndexed { i, ing ->
                            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    ing.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                if (ing.quantity != null) {
                                    Text(
                                        "${fmtQty(ing.quantity)} ${ing.unit ?: ""}".trim(),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!draft.notes.isNullOrBlank()) {
            Text(stringResource(R.string.notes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(draft.notes, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(24.dp))
    }
}
