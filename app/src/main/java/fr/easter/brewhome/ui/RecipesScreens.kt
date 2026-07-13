package fr.easter.brewhome.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.easter.brewhome.BrewViewModel
import fr.easter.brewhome.data.Recipe
import fr.easter.brewhome.data.RecipeIngredient

@Composable
fun RecipesScreen(vm: BrewViewModel, onOpen: (Int) -> Unit) {
    val state by vm.state.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }

    RefreshableContent(vm) {
        if (state.recipes.isEmpty()) {
            EmptyHint("Aucune recette.")
            return@RefreshableContent
        }
        val filtered = state.recipes.filter { recipe ->
            query.isBlank() || listOfNotNull(recipe.name, recipe.style)
                .any { it.contains(query, ignoreCase = true) }
        }
        Column(Modifier.fillMaxSize()) {
            SearchField(query, { query = it }, "Rechercher une recette…")
            if (filtered.isEmpty()) {
                EmptyHint("Aucun résultat pour « $query ».")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(filtered, key = { it.id }) { recipe ->
                        RecipeCard(recipe, onOpen)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecipeCard(recipe: Recipe, onOpen: (Int) -> Unit) {
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
                "${recipe.ingredients.size} ingrédients",
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

@Composable
fun RecipeDetailScreen(vm: BrewViewModel, recipeId: Int?) {
    val state by vm.state.collectAsState()
    val recipe = state.recipes.find { it.id == recipeId }
    if (recipe == null) {
        EmptyHint("Recette introuvable.")
        return
    }
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
            recipe.batchNo?.let { "brassin n°$it" },
        ).joinToString(" · ")
        if (subtitle.isNotEmpty()) Text(subtitle, color = MaterialTheme.colorScheme.outline)
        if (recipe.rating != null) StarRating(recipe.rating)

        InfoCard {
            InfoLine("Volume", recipe.volume?.let { "${fmtQty(it)} L" })
            InfoLine("Empâtage", recipe.mashTemp?.let { t ->
                "${fmtQty(t)} °C" + (recipe.mashTime?.let { " · $it min" } ?: "")
            })
            InfoLine("Ébullition", recipe.boilTime?.let { "$it min" })
            InfoLine("Fermentation", recipe.fermTemp?.let { t ->
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
                        IngredientLine(ing)
                    }
                }
            }
        }

        if (!recipe.notes.isNullOrBlank()) {
            Text("Notes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(recipe.notes, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun IngredientLine(ing: RecipeIngredient) {
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
        }
        Text(
            "${fmtQty(ing.quantity)} ${ing.unit}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
