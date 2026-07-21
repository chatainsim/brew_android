package fr.easter.brewhome.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.easter.brewhome.BrewViewModel
import fr.easter.brewhome.R

/** Résultat unifié : type, id d'ouverture, titre et sous-titre. */
private data class Hit(val kind: String, val id: Int, val title: String, val subtitle: String?)

/** Recherche globale dans les bières, recettes et brassins (côté client). */
@Composable
fun SearchScreen(
    vm: BrewViewModel,
    onOpenBeer: (Int) -> Unit,
    onOpenRecipe: (Int) -> Unit,
    onOpenBrew: (Int) -> Unit,
) {
    val state by vm.state.collectAsState()
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()

    val hits = remember(q, state.beers, state.recipes, state.brews) {
        if (q.length < 2) emptyList() else buildList {
            state.beers.filter { (it.archived ?: 0) == 0 && it.matches(q) }.forEach {
                add(Hit("beer", it.id, it.name, listOfNotNull(it.type, it.origin).joinToString(" · ").ifBlank { null }))
            }
            state.recipes.filter { it.matches(q) }.forEach {
                add(Hit("recipe", it.id, it.name, it.style))
            }
            state.brews.filter { (it.archived ?: 0) == 0 && it.matches(q) }.forEach {
                add(Hit("brew", it.id, it.name, listOfNotNull(it.recipeName, it.brewDate).joinToString(" · ").ifBlank { null }))
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        when {
            q.length < 2 -> EmptyHint(stringResource(R.string.search_prompt), Icons.Filled.Search)
            hits.isEmpty() -> EmptyHint(stringResource(R.string.search_no_result), Icons.Filled.Search)
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                val groups = hits.groupBy { it.kind }
                listOf(
                    "beer" to R.string.tab_beers,
                    "recipe" to R.string.tab_recipes,
                    "brew" to R.string.tab_brews,
                ).forEach { (kind, headerRes) ->
                    val group = groups[kind].orEmpty()
                    if (group.isNotEmpty()) {
                        item(key = "h_$kind") {
                            Text(
                                stringResource(headerRes),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
                            )
                        }
                        items(group, key = { "${it.kind}_${it.id}" }) { hit ->
                            HitRow(hit) {
                                when (hit.kind) {
                                    "beer" -> onOpenBeer(hit.id)
                                    "recipe" -> onOpenRecipe(hit.id)
                                    else -> onOpenBrew(hit.id)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HitRow(hit: Hit, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
    ) {
        Text(hit.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        hit.subtitle?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

private fun fr.easter.brewhome.data.Beer.matches(q: String): Boolean =
    name.lowercase().contains(q) ||
        (type?.lowercase()?.contains(q) == true) ||
        (origin?.lowercase()?.contains(q) == true) ||
        (recipeName?.lowercase()?.contains(q) == true)

private fun fr.easter.brewhome.data.Recipe.matches(q: String): Boolean =
    name.lowercase().contains(q) || (style?.lowercase()?.contains(q) == true)

private fun fr.easter.brewhome.data.Brew.matches(q: String): Boolean =
    name.lowercase().contains(q) ||
        (recipeName?.lowercase()?.contains(q) == true) ||
        (recipeStyle?.lowercase()?.contains(q) == true)
