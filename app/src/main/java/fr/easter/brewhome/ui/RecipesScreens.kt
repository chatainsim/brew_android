package fr.easter.brewhome.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import fr.easter.brewhome.BrewViewModel
import fr.easter.brewhome.R
import fr.easter.brewhome.calc.StockCheck
import fr.easter.brewhome.data.Draft
import fr.easter.brewhome.data.Recipe
import fr.easter.brewhome.data.RecipeIngredient
import fr.easter.brewhome.data.ShoppingItem
import fr.easter.brewhome.data.parsedIngredients

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
            else RecipesList(state.recipes, state, query, { query = it }, onOpen)
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
) {
    if (recipes.isEmpty()) {
        EmptyHint(stringResource(R.string.recipes_empty))
        return
    }
    val filtered = recipes.filter { recipe ->
        query.isBlank() || listOfNotNull(recipe.name, recipe.style)
            .any { it.contains(query, ignoreCase = true) }
    }
    SearchField(query, onQuery, stringResource(R.string.recipes_search))
    if (filtered.isEmpty()) {
        EmptyHint(stringResource(R.string.no_results, query))
    } else {
        LazyColumn(
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(filtered, key = { it.id }) { recipe ->
                RecipeCard(recipe, state.inventory, onOpen)
            }
        }
    }
}

@Composable
private fun RecipeCard(
    recipe: Recipe,
    inventory: List<fr.easter.brewhome.data.InventoryItem>,
    onOpen: (Int) -> Unit,
) {
    val stock = remember(recipe.ingredients, inventory) {
        if (recipe.ingredients.isEmpty()) null else StockCheck.check(recipe.ingredients, inventory)
    }
    Card(
        modifier = Modifier
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
fun RecipeDetailScreen(vm: BrewViewModel, recipeId: Int?) {
    val state by vm.state.collectAsState()
    val recipe = state.recipes.find { it.id == recipeId }
    if (recipe == null) {
        EmptyHint(stringResource(R.string.recipe_not_found))
        return
    }
    val stock = remember(recipe.ingredients, state.inventory) {
        if (recipe.ingredients.isEmpty()) null
        else StockCheck.check(recipe.ingredients, state.inventory)
    }
    val stockByName = stock?.lines?.associateBy { it.name.trim().lowercase() } ?: emptyMap()

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

        if (stock != null) {
            StockBanner(stock, state.shopping) { vm.addNeedsToShopping(it) }
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
                        IngredientLine(ing, stockByName[ing.name.trim().lowercase()])
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
}

@Composable
private fun StockBanner(
    stock: StockCheck.Result,
    shopping: List<ShoppingItem>,
    onAddToShopping: (List<StockCheck.Need>) -> Unit,
) {
    val needs = remember(stock, shopping) {
        StockCheck.needs(stock, shopping.map { it.name.trim().lowercase() }.toSet())
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StockBadge(stock)
                Spacer(Modifier.width(10.dp))
                if (stock.allOk) {
                    Text(stringResource(R.string.stock_banner_ok_recipe), color = StockOk, fontWeight = FontWeight.SemiBold)
                } else {
                    val parts = listOfNotNull(
                        stock.nOk.takeIf { it > 0 }?.let { stringResource(R.string.stock_n_ok, it) },
                        stock.nLow.takeIf { it > 0 }?.let { stringResource(R.string.stock_n_low, it) },
                        stock.nMissing.takeIf { it > 0 }?.let { stringResource(R.string.stock_n_missing, it) },
                        stock.nMismatch.takeIf { it > 0 }?.let { stringResource(R.string.stock_n_mismatch, it) },
                    )
                    Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                }
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

@Composable
private fun IngredientLine(ing: RecipeIngredient, stock: StockCheck.Line?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(ing.name, style = MaterialTheme.typography.bodyLarge)
            val details = listOfNotNull(
                ing.hopTime?.let { "$it min" },
                ing.hopType,
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
                val label = when (stock.status) {
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
                Text(
                    label,
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

@Composable
fun draftStatusColor(status: String?): Color = when (status) {
    "in_progress" -> MaterialTheme.colorScheme.primary
    "ready" -> StockOk
    else -> MaterialTheme.colorScheme.outline
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
                            DraftCard(draft, onOpen)
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
private fun DraftCard(draft: Draft, onOpen: (Int) -> Unit) {
    Card(
        modifier = Modifier
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
                val color = draftStatusColor(draft.status)
                AssistChip(
                    onClick = { onOpen(draft.id) },
                    label = { Text(draftStatusLabel(draft.status), color = color) },
                    border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = color),
                )
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
fun DraftDetailScreen(vm: BrewViewModel, draftId: Int?) {
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
            val color = draftStatusColor(draft.status)
            AssistChip(
                onClick = {},
                label = { Text(draftStatusLabel(draft.status), color = color) },
                border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = color),
            )
        }
        draft.style?.let { Text(it, color = MaterialTheme.colorScheme.outline) }

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
