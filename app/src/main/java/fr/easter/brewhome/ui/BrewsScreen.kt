package fr.easter.brewhome.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import fr.easter.brewhome.BrewViewModel
import fr.easter.brewhome.R
import fr.easter.brewhome.calc.BrewCalc
import fr.easter.brewhome.data.Brew
import fr.easter.brewhome.data.BrewLogEntry
import fr.easter.brewhome.data.BrewPhoto
import fr.easter.brewhome.data.FermReading

@Composable
fun BrewsScreen(vm: BrewViewModel, onOpen: (Int) -> Unit) {
    val state by vm.state.collectAsState()
    RefreshableContent(vm) {
        if (state.brews.isEmpty()) {
            EmptyHint(stringResource(R.string.brews_empty))
            return@RefreshableContent
        }
        LazyColumn(
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.brews, key = { it.id }) { brew ->
                BrewCard(brew, onOpen, Modifier.animateItem())
            }
        }
    }
}

@Composable
private fun BrewCard(brew: Brew, onOpen: (Int) -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpen(brew.id) },
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    brew.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                val (container, content) = brewStatusColors(brew.status)
                StatusChip(brewStatusLabel(brew.status), container, content)
            }
            val line1 = listOfNotNull(
                brew.brewDate?.let { stringResource(R.string.brewed_on, it) },
                brew.volumeBrewed?.let { "${fmtQty(it)} L" },
            ).joinToString(" · ")
            if (line1.isNotEmpty()) {
                Text(
                    line1,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            val line2 = listOfNotNull(
                brew.og?.let { "DI ${fmtGravity(it)}" },
                brew.fg?.let { "DF ${fmtGravity(it)}" },
                brew.abv?.let { "${fmtQty(it)}% alc." },
            ).joinToString(" · ")
            if (line2.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(line2, style = MaterialTheme.typography.bodyMedium)
            }
            val extras = listOfNotNull(
                brew.fermentationCount?.takeIf { it > 0 }?.let { stringResource(R.string.n_readings, it) },
                brew.logCount?.takeIf { it > 0 }?.let { stringResource(R.string.n_log_entries, it) },
                brew.photoCount?.takeIf { it > 0 }?.let { stringResource(R.string.n_photos, it) },
            ).joinToString(" · ")
            if (extras.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    extras,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
fun BrewDetailScreen(vm: BrewViewModel, brewId: Int?, onOpenRecipe: (Int) -> Unit) {
    val state by vm.state.collectAsState()
    val brew = state.brews.find { it.id == brewId }
    if (brew == null) {
        EmptyHint(stringResource(R.string.brew_not_found))
        return
    }
    LaunchedEffect(brew.id) { vm.loadBrewExtras(brew.id) }
    val extras = vm.brewExtras.collectAsState().value[brew.id]
    var viewing by remember { mutableStateOf<BrewPhoto?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                brew.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            brew.batchNumber?.let {
                Text(
                    "#$it",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrewStatusSelector(brew.status) { vm.setBrewStatus(brew, it) }
            if (brew.recipeId != null) {
                AssistChip(
                    onClick = { onOpenRecipe(brew.recipeId) },
                    label = { Text(brew.recipeName ?: stringResource(R.string.see_recipe)) },
                    leadingIcon = {
                        Icon(
                            Icons.AutoMirrored.Outlined.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(AssistChipDefaults.IconSize),
                        )
                    },
                )
            }
        }
        brew.recipeStyle?.let { Text(it, color = MaterialTheme.colorScheme.outline) }

        InfoCard {
            InfoLine(stringResource(R.string.label_brew_date), brew.brewDate)
            InfoLine(stringResource(R.string.label_bottling_date), brew.bottlingDate?.take(10))
            InfoLine(stringResource(R.string.label_volume_brewed), brew.volumeBrewed?.let { "${fmtQty(it)} L" })
            InfoLine(stringResource(R.string.label_og), brew.og?.let { fmtGravity(it) })
            InfoLine(stringResource(R.string.label_fg), brew.fg?.let { fmtGravity(it) })
            InfoLine(stringResource(R.string.label_abv), brew.abv?.let { "${fmtQty(it)} %" })
            InfoLine(
                stringResource(R.string.label_attenuation),
                if (brew.og != null && brew.fg != null)
                    BrewCalc.attenuation(brew.og, brew.fg)?.let { "${fmtQty(kotlin.math.round(it * 10) / 10)} %" }
                else null,
            )
            InfoLine(stringResource(R.string.label_efficiency), brew.actualEfficiency?.let { "${fmtQty(it)} %" })
            InfoLine(stringResource(R.string.label_ferm), brew.fermTime?.let { "$it jours" })
            InfoLine(
                stringResource(R.string.label_cost),
                brew.costSnapshot?.let { c ->
                    "${fmtQty(c)} €" + (brew.costPerLiter?.let { " · ${fmtQty(it)} €/L" } ?: "")
                },
            )
            InfoLine(stringResource(R.string.label_cave_left), brew.caveLiters?.takeIf { it > 0 }?.let { "${fmtQty(it)} L" })
        }

        when {
            extras == null || extras.loading -> Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            }
            extras.error != null -> Text(
                extras.error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            else -> {
                if (extras.photos.isNotEmpty()) {
                    Text(
                        stringResource(R.string.photos_section, extras.photos.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    PhotosRow(vm, extras.photos) { viewing = it }
                }
                if (extras.readings.isNotEmpty()) {
                    Text(
                        stringResource(R.string.ferm_section, extras.readings.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    FermentationCard(extras.readings)
                }
                if (extras.log.isNotEmpty()) {
                    Text(
                        stringResource(R.string.log_section),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    LogCard(extras.log)
                }
            }
        }

        if (!brew.notes.isNullOrBlank()) {
            Text(stringResource(R.string.notes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(brew.notes, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(24.dp))
    }

    viewing?.let { photo -> PhotoViewer(vm, photo) { viewing = null } }
}

private val brewStatuses = listOf("planned", "in_progress", "fermenting", "completed")

/** Pastille de statut cliquable : ouvre un menu pour faire avancer le brassin. */
@Composable
private fun BrewStatusSelector(status: String?, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val (container, content) = brewStatusColors(status)
    Box {
        androidx.compose.material3.Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            color = container,
            onClick = { expanded = true },
        ) {
            Row(
                Modifier.padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    brewStatusLabel(status),
                    style = MaterialTheme.typography.labelLarge,
                    color = content,
                )
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = stringResource(R.string.brew_change_status),
                    tint = content,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            brewStatuses.forEach { s ->
                DropdownMenuItem(
                    text = { Text(brewStatusLabel(s)) },
                    leadingIcon = {
                        val (c, _) = brewStatusColors(s)
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(c),
                        )
                    },
                    trailingIcon = if (s == status) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null,
                    onClick = {
                        expanded = false
                        if (s != status) onSelect(s)
                    },
                )
            }
        }
    }
}

/** /api/brew-photos/{uid}_t.jpg → chemin de la photo plein format {uid}.jpg */
private fun fullPhotoPath(thumb: String?): String? =
    thumb?.replace(Regex("_t\\.jpg$"), ".jpg")

@Composable
private fun PhotosRow(vm: BrewViewModel, photos: List<BrewPhoto>, onOpen: (BrewPhoto) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(photos, key = { it.id }) { photo ->
            Column(Modifier.width(110.dp)) {
                AsyncImage(
                    model = vm.photoUrl(photo.thumb),
                    contentDescription = photo.caption ?: photo.step,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(110.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onOpen(photo) },
                )
                val label = photo.caption ?: photo.step
                if (!label.isNullOrBlank()) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Photo plein format dans une boîte de dialogue, fermée d'un tap. */
@Composable
private fun PhotoViewer(vm: BrewViewModel, photo: BrewPhoto, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onDismiss),
        ) {
            AsyncImage(
                model = vm.photoUrl(fullPhotoPath(photo.thumb)),
                contentDescription = photo.caption ?: photo.step,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(),
            )
            val label = listOfNotNull(photo.step, photo.caption).joinToString(" · ")
            if (label.isNotBlank()) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun FermentationCard(readings: List<FermReading>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            val gravities = readings.mapNotNull { it.gravity }
            if (gravities.size >= 2) {
                GravityChart(readings)
                Spacer(Modifier.height(8.dp))
            }
            val last = readings.last()
            val summary = listOfNotNull(
                last.gravity?.let { "densité ${fmtGravity(it)}" },
                last.temperature?.let { "${fmtQty(it)} °C" },
            ).joinToString(" · ")
            Text(
                stringResource(R.string.last_reading, summary),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                fmtTimestamp(last.recordedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/** "2026-05-01T08:00:00" ou "2026-05-01 08:00" → secondes epoch, null si illisible. */
internal fun parseFermTimestamp(ts: String): Long? = runCatching {
    var s = ts.take(19).replace(' ', 'T')
    if (s.length == 16) s += ":00"
    java.time.LocalDateTime.parse(s).toEpochSecond(java.time.ZoneOffset.UTC)
}.getOrNull()

/**
 * Positions X (0..1) des mesures : proportionnelles au temps réel quand toutes
 * les dates sont lisibles, sinon repli sur un espacement régulier. Sans ça, un
 * densimètre qui mesure toutes les 15 min écraserait visuellement les mesures
 * manuelles espacées d'une journée.
 */
internal fun timeFractions(timestamps: List<String>): List<Float> {
    val n = timestamps.size
    if (n < 2) return List(n) { 0f }
    val times = timestamps.map { parseFermTimestamp(it) }
    val tMin = times.minOfOrNull { it ?: Long.MAX_VALUE } ?: 0L
    val tMax = times.maxOfOrNull { it ?: Long.MIN_VALUE } ?: 0L
    val timed = times.all { it != null } && tMax > tMin
    return if (timed) {
        val range = (tMax - tMin).toFloat()
        times.map { (it!! - tMin) / range }
    } else {
        List(n) { it / (n - 1).toFloat() }
    }
}

/** Courbe de densité (et température si dispo) sur toute la fermentation. */
@Composable
private fun GravityChart(readings: List<FermReading>) {
    val gravityColor = MaterialTheme.colorScheme.primary
    val tempColor = MaterialTheme.colorScheme.tertiary
    val xs = timeFractions(readings.map { it.recordedAt })
    val gravityPts = readings.mapIndexedNotNull { i, r -> r.gravity?.let { xs[i] to it } }
    val tempPts = readings.mapIndexedNotNull { i, r -> r.temperature?.let { xs[i] to it } }
    val gravities = gravityPts.map { it.second }

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(120.dp),
    ) {
        drawSeries(gravityPts, gravityColor, fill = true)
        if (tempPts.size >= 2) drawSeries(tempPts, tempColor)
    }
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Legend(stringResource(R.string.legend_gravity, fmtGravity(gravities.max()), fmtGravity(gravities.min())), gravityColor)
        if (tempPts.size >= 2) {
            val temps = tempPts.map { it.second }
            Legend(stringResource(R.string.legend_temp, fmtQty(temps.min()), fmtQty(temps.max())), tempColor)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSeries(
    points: List<Pair<Float, Double>>,
    color: Color,
    fill: Boolean = false,
) {
    if (points.size < 2) return
    val min = points.minOf { it.second }
    val max = points.maxOf { it.second }
    val range = (max - min).takeIf { it > 1e-9 } ?: 1.0
    val path = Path()
    points.forEachIndexed { i, (xFrac, v) ->
        val x = size.width * xFrac
        // marge de 4 % en haut/bas pour ne pas couper le trait
        val y = size.height * (0.04f + 0.92f * (1f - ((v - min) / range).toFloat()))
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    if (fill) {
        // Aplat dégradé sous la courbe, qui s'estompe vers le bas
        val area = Path().apply {
            addPath(path)
            lineTo(size.width * points.last().first, size.height)
            lineTo(size.width * points.first().first, size.height)
            close()
        }
        drawPath(
            area,
            brush = Brush.verticalGradient(
                listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0f)),
                endY = size.height,
            ),
        )
    }
    drawPath(path, color = color, style = Stroke(width = 5f, cap = StrokeCap.Round))
}

@Composable
private fun Legend(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}

@Composable
private fun LogCard(log: List<BrewLogEntry>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            log.forEachIndexed { i, entry ->
                if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                val header = listOfNotNull(fmtTimestamp(entry.ts), entry.step).joinToString(" · ")
                Text(
                    header,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                if (!entry.note.isNullOrBlank()) {
                    Text(entry.note, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** "2026-05-01T12:34:56" ou "2026-05-01 12:34" → "2026-05-01 12:34". */
private fun fmtTimestamp(ts: String): String = ts.take(16).replace('T', ' ')

private fun fmtGravity(g: Double): String =
    if (g >= 2.0) fmtQty(g) else String.format(java.util.Locale.US, "%.3f", g)
