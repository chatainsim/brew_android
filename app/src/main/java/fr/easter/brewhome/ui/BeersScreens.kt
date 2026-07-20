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
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.outlined.SportsBar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    val hasKeg = (beer.kegLiters ?: 0.0) > 0.0 || (beer.kegInitialLiters ?: 0.0) > 0.0
    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpen(beer.id) },
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            // Photo pleine hauteur, bord à bord (arrondie par la carte)
            BeerHero(beer, vm)
            Column(Modifier.padding(14.dp)) {
                Text(
                    beer.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = listOfNotNull(
                    beer.type,
                    beer.abv?.let { "${fmtQty(it)} % alc." },
                ).joinToString(" · ")
                if (subtitle.isNotEmpty()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                beer.tasteRating?.takeIf { it > 0 }?.let {
                    Spacer(Modifier.height(5.dp))
                    RatingPill(it)
                }
                Spacer(Modifier.height(12.dp))
                Stepper(
                    stringResource(R.string.unit_33), (beer.stock33 ?: 0).toDouble(),
                    format = { it.toInt().toString() },
                    canDecrement = (beer.stock33 ?: 0) > 0,
                    onDecrement = { vm.adjustBeerStock(beer, d33 = -1) },
                    onIncrement = { vm.adjustBeerStock(beer, d33 = 1) },
                )
                Spacer(Modifier.height(6.dp))
                Stepper(
                    stringResource(R.string.unit_75), (beer.stock75 ?: 0).toDouble(),
                    format = { it.toInt().toString() },
                    canDecrement = (beer.stock75 ?: 0) > 0,
                    onDecrement = { vm.adjustBeerStock(beer, d75 = -1) },
                    onIncrement = { vm.adjustBeerStock(beer, d75 = 1) },
                )
                if (hasKeg) {
                    Spacer(Modifier.height(6.dp))
                    Stepper(
                        stringResource(R.string.keg), beer.kegLiters ?: 0.0,
                        format = { "${fmtQty(it)} L" },
                        canDecrement = (beer.kegLiters ?: 0.0) > 0.0,
                        onDecrement = { vm.adjustBeerStock(beer, dKeg = -0.5) },
                        onIncrement = { vm.adjustBeerStock(beer, dKeg = 0.5) },
                    )
                }
            }
        }
    }
}

/** Photo pleine hauteur de la carte, ou monogramme sur dégradé, avec le
 *  total de bouteilles en pastille en bas. */
@Composable
private fun BeerHero(beer: Beer, vm: BrewViewModel) {
    val photoUrl = vm.photoUrl(beer.photo)
    val totalBottles = (beer.stock33 ?: 0) + (beer.stock75 ?: 0)
    Box(
        Modifier
            .width(104.dp)
            .fillMaxHeight(),
        contentAlignment = Alignment.BottomStart,
    ) {
        if (photoUrl != null) {
            AsyncImage(
                model = photoUrl,
                contentDescription = beer.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Voile sombre en bas pour lisibilité de la pastille
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
                        ),
                    ),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
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
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
                )
            }
        }
        if (totalBottles > 0) {
            val onPhoto = photoUrl != null
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (onPhoto) Color.Black.copy(alpha = 0.55f)
                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                modifier = Modifier.padding(6.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.SportsBar,
                        contentDescription = null,
                        tint = if (onPhoto) Color.White else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "$totalBottles",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (onPhoto) Color.White else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/** Note de dégustation compacte : étoile + valeur sur pastille. */
@Composable
private fun RatingPill(rating: Int) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "$rating/5",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/** Compteur de stock : libellé + boutons tonaux − / + autour du nombre animé. */
@Composable
private fun Stepper(
    label: String,
    value: Double,
    format: (Double) -> String,
    canDecrement: Boolean,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val empty = value <= 0.0
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (empty) MaterialTheme.colorScheme.outline
                    else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        FilledTonalIconButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onDecrement()
            },
            enabled = canDecrement,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                Icons.Filled.Remove,
                contentDescription = stringResource(R.string.cd_remove_one, label),
                modifier = Modifier.size(18.dp),
            )
        }
        Box(
            Modifier
                .widthIn(min = 52.dp)
                .padding(horizontal = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedNumber(
                value = value,
                format = format,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (empty) MaterialTheme.colorScheme.outline
                        else MaterialTheme.colorScheme.onSurface,
            )
        }
        FilledTonalIconButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onIncrement()
            },
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = stringResource(R.string.cd_add_one, label),
                modifier = Modifier.size(18.dp),
            )
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

        val archived = (beer.archived ?: 0) != 0
        OutlinedButton(
            onClick = { vm.setBeerArchived(beer, !archived) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                if (archived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                if (archived) stringResource(R.string.beer_unarchive)
                else stringResource(R.string.beer_archive),
                Modifier.padding(start = 8.dp),
            )
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
