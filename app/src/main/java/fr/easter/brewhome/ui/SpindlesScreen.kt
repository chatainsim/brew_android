package fr.easter.brewhome.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bloodtype
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.easter.brewhome.BrewViewModel
import fr.easter.brewhome.R
import fr.easter.brewhome.data.Spindle
import fr.easter.brewhome.data.SpindleReading

private fun fmtGravity(g: Double): String =
    if (g >= 2.0) fmtQty(g) else String.format(java.util.Locale.US, "%.3f", g)

private fun fmtSpindleTime(ts: String): String = ts.take(16).replace('T', ' ')

/** Vue densimètres connectés : densité, température, batterie en direct. */
@Composable
fun SpindlesScreen(vm: BrewViewModel, onOpen: (Int) -> Unit) {
    val spindles by vm.spindles.collectAsState()
    val readings by vm.spindleReadings.collectAsState()
    LaunchedEffect(Unit) { vm.loadSpindles() }

    when {
        spindles == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        spindles!!.isEmpty() -> EmptyHint(
            stringResource(R.string.spindles_empty),
            Icons.Outlined.Bloodtype,
        )
        else -> LazyColumn(
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(spindles!!, key = { it.id }) { spindle ->
                SpindleCard(spindle, readings[spindle.id].orEmpty()) { onOpen(spindle.id) }
            }
        }
    }
}

/** Détail d'un densimètre : grands graphes densité et température dans le temps. */
@Composable
fun SpindleDetailScreen(vm: BrewViewModel, spindleId: Int?) {
    val spindles by vm.spindles.collectAsState()
    val readingsMap by vm.spindleReadings.collectAsState()
    val spindle = spindles?.find { it.id == spindleId }
    var hours by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<Int?>(72) }
    LaunchedEffect(spindleId, hours) {
        if (spindleId != null) {
            if (spindles == null) vm.loadSpindles()
            vm.loadSpindleReadings(spindleId, hours)
        }
    }
    if (spindle == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val readings = readingsMap[spindle.id].orEmpty()
    val gravColor = MaterialTheme.colorScheme.primary
    val tempColor = MaterialTheme.colorScheme.tertiary

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(spindle.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        spindle.brewName?.let {
            Text(stringResource(R.string.spindle_brew, it), color = MaterialTheme.colorScheme.primary)
        }
        var showAssign by androidx.compose.runtime.remember { mutableStateOf(false) }
        androidx.compose.material3.OutlinedButton(onClick = { showAssign = true }) {
            Text(stringResource(R.string.spindle_assign))
        }
        if (showAssign) {
            val brews = vm.state.collectAsState().value.brews
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showAssign = false },
                title = { Text(stringResource(R.string.spindle_assign_title)) },
                text = {
                    Column {
                        androidx.compose.material3.TextButton(
                            onClick = { vm.assignSpindleBrew(spindle.id, null); showAssign = false },
                        ) { Text(stringResource(R.string.spindle_assign_none)) }
                        HorizontalDivider()
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                        ) {
                            brews.forEach { b ->
                                Text(
                                    b.name,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            vm.assignSpindleBrew(spindle.id, b.id)
                                            showAssign = false
                                        }
                                        .padding(vertical = 12.dp),
                                    color = if (b.id == spindle.brewId) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showAssign = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric(stringResource(R.string.spindle_gravity), spindle.lastGravity?.let { fmtGravity(it) } ?: "–", gravColor)
            Metric(stringResource(R.string.spindle_temp), spindle.lastTemperature?.let { "${fmtQty(it)} °C" } ?: "–", tempColor)
            Metric(stringResource(R.string.spindle_battery), spindle.lastBattery?.let { "${fmtQty(it)} V" } ?: "–", MaterialTheme.colorScheme.secondary)
        }
        RangeSelector(hours) { hours = it }
        if (readings.size < 2) {
            Text(
                stringResource(R.string.probe_no_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        } else {
            val ts = readings.map { it.recordedAt }
            SectionTitleLocal(stringResource(R.string.spindle_gravity))
            ProbeChart(
                ts,
                listOf(ChartSeries(stringResource(R.string.spindle_gravity), readings.map { it.gravity }, gravColor, "", 3)),
            )
            if (readings.any { it.temperature != null }) {
                SectionTitleLocal(stringResource(R.string.spindle_temp))
                ProbeChart(
                    ts,
                    listOf(ChartSeries(stringResource(R.string.spindle_temp), readings.map { it.temperature }, tempColor, "°C", 1)),
                )
            }
            spindle.lastReadingAt?.let {
                Text(
                    stringResource(R.string.spindle_last_reading, fmtSpindleTime(it), spindle.readingCount ?: 0),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun SectionTitleLocal(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SpindleCard(spindle: Spindle, readings: List<SpindleReading>, onClick: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    spindle.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if ((spindle.gravityStable ?: 0) == 1) {
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Text(
                            stringResource(R.string.spindle_stable),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
            spindle.brewName?.let {
                Text(
                    stringResource(R.string.spindle_brew, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric(
                    stringResource(R.string.spindle_gravity),
                    spindle.lastGravity?.let { fmtGravity(it) } ?: "–",
                    MaterialTheme.colorScheme.primary,
                )
                Metric(
                    stringResource(R.string.spindle_temp),
                    spindle.lastTemperature?.let { "${fmtQty(it)} °C" } ?: "–",
                    MaterialTheme.colorScheme.tertiary,
                )
                Metric(
                    stringResource(R.string.spindle_battery),
                    spindle.lastBattery?.let { "${fmtQty(it)} V" } ?: "–",
                    MaterialTheme.colorScheme.secondary,
                )
            }

            val gravities = readings.mapNotNull { it.gravity }
            if (gravities.size >= 2) {
                Spacer(Modifier.height(12.dp))
                GravitySparkline(gravities, MaterialTheme.colorScheme.primary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        fmtGravity(gravities.max()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Text(
                        fmtGravity(gravities.min()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            spindle.lastReadingAt?.let {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                val count = spindle.readingCount ?: 0
                Text(
                    stringResource(R.string.spindle_last_reading, fmtSpindleTime(it), count),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
internal fun Metric(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

/** Courbe de densité sur tout l'historique récent du densimètre. */
@Composable
private fun GravitySparkline(gravities: List<Double>, color: Color) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(70.dp),
    ) {
        val min = gravities.min()
        val max = gravities.max()
        val range = (max - min).takeIf { it > 1e-9 } ?: 1.0
        val path = Path()
        gravities.forEachIndexed { i, g ->
            val x = size.width * i / (gravities.size - 1)
            val y = size.height * (0.06f + 0.88f * (1f - ((g - min) / range).toFloat()))
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = color, style = Stroke(width = 5f, cap = StrokeCap.Round))
        // Point final mis en évidence
        val lastX = size.width
        val lastY = size.height * (0.06f + 0.88f * (1f - ((gravities.last() - min) / range).toFloat()))
        drawCircle(color, radius = 6f, center = Offset(lastX - 3f, lastY))
    }
}
