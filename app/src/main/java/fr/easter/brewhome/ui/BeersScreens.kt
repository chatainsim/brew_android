package fr.easter.brewhome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.SportsBar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.ui.res.stringResource
import fr.easter.brewhome.BrewViewModel
import fr.easter.brewhome.R
import fr.easter.brewhome.data.Beer
import fr.easter.brewhome.data.TastingPut

@Composable
fun BeersScreen(vm: BrewViewModel, onOpen: (Int) -> Unit) {
    val state by vm.state.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var showArchived by rememberSaveable { mutableStateOf(false) }

    RefreshableContent(vm) {
        if (state.beers.isEmpty()) {
            EmptyHint(stringResource(R.string.beers_empty))
            return@RefreshableContent
        }
        // Comme le site : les bières archivées sont masquées par défaut
        val archivedCount = state.beers.count { (it.archived ?: 0) != 0 }
        val visible = state.beers.filter { showArchived || (it.archived ?: 0) == 0 }
        val filtered = visible.filter { beer ->
            query.isBlank() || listOfNotNull(beer.name, beer.type, beer.origin, beer.recipeName)
                .any { it.contains(query, ignoreCase = true) }
        }
        Column(Modifier.fillMaxSize()) {
            SearchField(query, { query = it }, stringResource(R.string.beers_search))
            if (archivedCount > 0) {
                FilterChip(
                    selected = showArchived,
                    onClick = { showArchived = !showArchived },
                    label = {
                        Text(
                            if (showArchived) stringResource(R.string.beers_hide_archived, archivedCount)
                            else stringResource(R.string.beers_show_archived, archivedCount),
                        )
                    },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            if (filtered.isEmpty()) {
                EmptyHint(stringResource(R.string.no_results, query))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(filtered, key = { it.id }) { beer ->
                        BeerCard(beer, vm, onOpen, Modifier.animateItem())
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyHint(text: String, icon: ImageVector = Icons.Outlined.SportsBar) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(text, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun BeerCard(beer: Beer, vm: BrewViewModel, onOpen: (Int) -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpen(beer.id) },
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val photoUrl = vm.photoUrl(beer.photo)
            if (photoUrl != null) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = beer.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
            } else {
                // Pas de photo : monogramme sur dégradé ambre → olive
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.tertiaryContainer,
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        beer.name.trim().take(1).uppercase(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    beer.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = listOfNotNull(
                    beer.type,
                    beer.abv?.let { "${fmtQty(it)}%" },
                ).joinToString(" · ")
                if (subtitle.isNotEmpty()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                Spacer(Modifier.height(6.dp))
                StockRow(stringResource(R.string.unit_33), beer.stock33 ?: 0) { d -> vm.adjustBeerStock(beer, d33 = d) }
                StockRow(stringResource(R.string.unit_75), beer.stock75 ?: 0) { d -> vm.adjustBeerStock(beer, d75 = d) }
                if ((beer.kegLiters ?: 0.0) > 0.0 || (beer.kegInitialLiters ?: 0.0) > 0.0) {
                    KegRow(beer.kegLiters ?: 0.0) { d -> vm.adjustBeerStock(beer, dKeg = d) }
                }
            }
        }
    }
}

@Composable
private fun StockRow(label: String, count: Int, onAdjust: (Int) -> Unit) {
    val haptics = LocalHapticFeedback.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(44.dp),
        )
        IconButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onAdjust(-1)
            },
            enabled = count > 0,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.cd_remove_one, label))
        }
        AnimatedNumber(
            value = count.toDouble(),
            format = { it.toInt().toString() },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.widthIn(min = 28.dp),
            color = if (count == 0) MaterialTheme.colorScheme.outline
                    else MaterialTheme.colorScheme.onSurface,
        )
        IconButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onAdjust(1)
            },
            modifier = Modifier.size(32.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cd_add_one, label))
        }
    }
}

@Composable
private fun KegRow(liters: Double, onAdjust: (Double) -> Unit) {
    val haptics = LocalHapticFeedback.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.keg), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(44.dp))
        IconButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onAdjust(-0.5)
            },
            enabled = liters > 0,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.cd_keg_remove))
        }
        AnimatedNumber(
            value = liters,
            format = { "${fmtQty(it)} L" },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.widthIn(min = 28.dp),
        )
        IconButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onAdjust(0.5)
            },
            modifier = Modifier.size(32.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cd_keg_add))
        }
    }
}

@Composable
fun BeerDetailScreen(vm: BrewViewModel, beerId: Int?) {
    val state by vm.state.collectAsState()
    val beer = state.beers.find { it.id == beerId }
    if (beer == null) {
        EmptyHint(stringResource(R.string.beer_not_found))
        return
    }
    var showTastingDialog by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val photoUrl = vm.photoUrl(beer.photo)
        if (photoUrl != null) {
            AsyncImage(
                model = photoUrl,
                contentDescription = beer.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
        }
        Text(beer.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        val subtitle = listOfNotNull(beer.type, beer.abv?.let { "${fmtQty(it)}% alc." }, beer.origin)
            .joinToString(" · ")
        if (subtitle.isNotEmpty()) Text(subtitle, color = MaterialTheme.colorScheme.outline)

        InfoCard {
            InfoLine(stringResource(R.string.label_recipe), beer.recipeName)
            InfoLine(stringResource(R.string.label_brewed_on_f), beer.brewDate)
            InfoLine(stringResource(R.string.label_bottled_on_f), beer.bottlingDate)
            InfoLine(stringResource(R.string.label_stock_33), (beer.stock33 ?: 0).toString())
            InfoLine(stringResource(R.string.label_stock_75), (beer.stock75 ?: 0).toString())
            if ((beer.kegLiters ?: 0.0) > 0.0)
                InfoLine(stringResource(R.string.keg), "${fmtQty(beer.kegLiters)} L")
        }

        if (!beer.description.isNullOrBlank()) {
            Text(stringResource(R.string.label_description), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(beer.description, style = MaterialTheme.typography.bodyMedium)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.label_tasting), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showTastingDialog = true }) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.cd_edit_tasting))
            }
        }
        StarRating(beer.tasteRating)
        InfoCard {
            InfoLine(stringResource(R.string.taste_appearance), beer.tasteAppearance)
            InfoLine(stringResource(R.string.taste_aroma), beer.tasteAroma)
            InfoLine(stringResource(R.string.taste_flavor), beer.tasteFlavor)
            InfoLine(stringResource(R.string.taste_bitterness), beer.tasteBitterness)
            InfoLine(stringResource(R.string.taste_mouthfeel), beer.tasteMouthfeel)
            InfoLine(stringResource(R.string.taste_finish), beer.tasteFinish)
            InfoLine(stringResource(R.string.taste_overall_short), beer.tasteOverall)
            InfoLine(stringResource(R.string.taste_date), beer.tasteDate)
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showTastingDialog) {
        TastingDialog(
            beer = beer,
            onDismiss = { showTastingDialog = false },
            onSave = { tasting ->
                vm.saveTasting(beer.id, tasting) { showTastingDialog = false }
            },
        )
    }
}

@Composable
fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content,
        )
    }
}

@Composable
fun InfoLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.width(120.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TastingDialog(beer: Beer, onDismiss: () -> Unit, onSave: (TastingPut) -> Unit) {
    var rating by remember { mutableStateOf(beer.tasteRating) }
    var appearance by remember { mutableStateOf(beer.tasteAppearance ?: "") }
    var aroma by remember { mutableStateOf(beer.tasteAroma ?: "") }
    var flavor by remember { mutableStateOf(beer.tasteFlavor ?: "") }
    var bitterness by remember { mutableStateOf(beer.tasteBitterness ?: "") }
    var mouthfeel by remember { mutableStateOf(beer.tasteMouthfeel ?: "") }
    var finish by remember { mutableStateOf(beer.tasteFinish ?: "") }
    var overall by remember { mutableStateOf(beer.tasteOverall ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tasting_dialog_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StarRating(rating) { rating = it }
                OutlinedTextField(value = appearance, onValueChange = { appearance = it }, label = { Text(stringResource(R.string.taste_appearance)) })
                OutlinedTextField(value = aroma, onValueChange = { aroma = it }, label = { Text(stringResource(R.string.taste_aroma)) })
                OutlinedTextField(value = flavor, onValueChange = { flavor = it }, label = { Text(stringResource(R.string.taste_flavor)) })
                OutlinedTextField(value = bitterness, onValueChange = { bitterness = it }, label = { Text(stringResource(R.string.taste_bitterness)) })
                OutlinedTextField(value = mouthfeel, onValueChange = { mouthfeel = it }, label = { Text(stringResource(R.string.taste_mouthfeel)) })
                OutlinedTextField(value = finish, onValueChange = { finish = it }, label = { Text(stringResource(R.string.taste_finish)) })
                OutlinedTextField(value = overall, onValueChange = { overall = it }, label = { Text(stringResource(R.string.taste_overall)) })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // Le serveur écrase toutes les colonnes de dégustation à chaque
                // PUT : il faut renvoyer tous les champs, pas seulement ceux édités.
                onSave(
                    TastingPut(
                        tasteRating = rating,
                        tasteAppearance = appearance.ifBlank { null },
                        tasteAroma = aroma.ifBlank { null },
                        tasteFlavor = flavor.ifBlank { null },
                        tasteBitterness = bitterness.ifBlank { null },
                        tasteMouthfeel = mouthfeel.ifBlank { null },
                        tasteFinish = finish.ifBlank { null },
                        tasteOverall = overall.ifBlank { null },
                        tasteDate = java.time.LocalDate.now().toString(),
                    )
                )
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
