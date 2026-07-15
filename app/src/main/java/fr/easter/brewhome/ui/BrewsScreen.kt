package fr.easter.brewhome.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.easter.brewhome.BrewViewModel
import fr.easter.brewhome.calc.BrewCalc
import fr.easter.brewhome.data.Brew
import fr.easter.brewhome.data.BrewLogEntry
import fr.easter.brewhome.data.FermReading

@Composable
fun BrewsScreen(vm: BrewViewModel, onOpen: (Int) -> Unit) {
    val state by vm.state.collectAsState()
    RefreshableContent(vm) {
        if (state.brews.isEmpty()) {
            EmptyHint("Aucun brassin.")
            return@RefreshableContent
        }
        LazyColumn(
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.brews, key = { it.id }) { brew ->
                BrewCard(brew, onOpen)
            }
        }
    }
}

@Composable
private fun BrewCard(brew: Brew, onOpen: (Int) -> Unit) {
    Card(
        modifier = Modifier
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
                val statusColor = brewStatusColor(brew.status)
                AssistChip(
                    onClick = { onOpen(brew.id) },
                    label = { Text(brewStatusLabel(brew.status), color = statusColor) },
                    border = AssistChipDefaults.assistChipBorder(
                        enabled = true,
                        borderColor = statusColor,
                    ),
                )
            }
            val line1 = listOfNotNull(
                brew.brewDate?.let { "Brassé le $it" },
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
                brew.fermentationCount?.takeIf { it > 0 }?.let { "$it mesures" },
                brew.logCount?.takeIf { it > 0 }?.let { "$it notes de journal" },
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
        EmptyHint("Brassin introuvable.")
        return
    }
    LaunchedEffect(brew.id) { vm.loadBrewExtras(brew.id) }
    val extras = vm.brewExtras.collectAsState().value[brew.id]

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
        val statusColor = brewStatusColor(brew.status)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssistChip(
                onClick = {},
                label = { Text(brewStatusLabel(brew.status), color = statusColor) },
                border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = statusColor),
            )
            if (brew.recipeId != null) {
                AssistChip(
                    onClick = { onOpenRecipe(brew.recipeId) },
                    label = { Text(brew.recipeName ?: "Voir la recette") },
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
            InfoLine("Brassé le", brew.brewDate)
            InfoLine("Embouteillé le", brew.bottlingDate?.take(10))
            InfoLine("Volume obtenu", brew.volumeBrewed?.let { "${fmtQty(it)} L" })
            InfoLine("Densité initiale", brew.og?.let { fmtGravity(it) })
            InfoLine("Densité finale", brew.fg?.let { fmtGravity(it) })
            InfoLine("Alcool", brew.abv?.let { "${fmtQty(it)} %" })
            InfoLine(
                "Atténuation",
                if (brew.og != null && brew.fg != null)
                    BrewCalc.attenuation(brew.og, brew.fg)?.let { "${fmtQty(kotlin.math.round(it * 10) / 10)} %" }
                else null,
            )
            InfoLine("Efficacité", brew.actualEfficiency?.let { "${fmtQty(it)} %" })
            InfoLine("Fermentation", brew.fermTime?.let { "$it jours" })
            InfoLine(
                "Coût",
                brew.costSnapshot?.let { c ->
                    "${fmtQty(c)} €" + (brew.costPerLiter?.let { " · ${fmtQty(it)} €/L" } ?: "")
                },
            )
            InfoLine("Reste en cave", brew.caveLiters?.takeIf { it > 0 }?.let { "${fmtQty(it)} L" })
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
                if (extras.readings.isNotEmpty()) {
                    Text(
                        "Fermentation (${extras.readings.size} mesures)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    FermentationCard(extras.readings)
                }
                if (extras.log.isNotEmpty()) {
                    Text(
                        "Journal",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    LogCard(extras.log)
                }
            }
        }

        if (!brew.notes.isNullOrBlank()) {
            Text("Notes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(brew.notes, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(24.dp))
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
                "Dernière mesure : $summary",
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

/** Courbe de densité (et température si dispo) sur toute la fermentation. */
@Composable
private fun GravityChart(readings: List<FermReading>) {
    val gravityColor = MaterialTheme.colorScheme.primary
    val tempColor = MaterialTheme.colorScheme.tertiary
    val gravities = readings.mapNotNull { it.gravity }
    val temps = readings.mapNotNull { it.temperature }

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(120.dp),
    ) {
        drawSeries(gravities, gravityColor)
        if (temps.size >= 2) drawSeries(temps, tempColor)
    }
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Legend("Densité ${fmtGravity(gravities.max())} → ${fmtGravity(gravities.min())}", gravityColor)
        if (temps.size >= 2) {
            Legend("Temp. ${fmtQty(temps.min())}–${fmtQty(temps.max())} °C", tempColor)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSeries(
    values: List<Double>,
    color: Color,
) {
    if (values.size < 2) return
    val min = values.min()
    val max = values.max()
    val range = (max - min).takeIf { it > 1e-9 } ?: 1.0
    val path = Path()
    values.forEachIndexed { i, v ->
        val x = size.width * i / (values.size - 1)
        // marge de 4 % en haut/bas pour ne pas couper le trait
        val y = size.height * (0.04f + 0.92f * (1f - ((v - min) / range).toFloat()))
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
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
