package fr.easter.brewhome.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.easter.brewhome.BrewViewModel
import fr.easter.brewhome.R
import fr.easter.brewhome.data.TempReading
import fr.easter.brewhome.data.TempSensor

private fun fmtTempTime(ts: String): String = ts.take(16).replace('T', ' ')

/** Vue sondes de température : température, humidité, consigne en direct. */
@Composable
fun TempScreen(vm: BrewViewModel, onOpen: (Int) -> Unit) {
    val sensors by vm.tempSensors.collectAsState()
    LaunchedEffect(Unit) { vm.loadTempSensors() }

    when {
        sensors == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        sensors!!.isEmpty() -> EmptyHint(stringResource(R.string.temps_empty), Icons.Outlined.Thermostat)
        else -> LazyColumn(
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(sensors!!, key = { it.id }) { sensor ->
                TempCard(sensor) { onOpen(sensor.id) }
            }
        }
    }
}

@Composable
private fun TempCard(sensor: TempSensor, onClick: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                sensor.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            sensor.brewName?.let {
                Text(
                    stringResource(R.string.spindle_brew, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric(
                    stringResource(R.string.temp_current),
                    sensor.lastTemperature?.let { "${fmtQty(it)} °C" } ?: "–",
                    MaterialTheme.colorScheme.primary,
                )
                sensor.lastHumidity?.let {
                    Metric(stringResource(R.string.temp_humidity), "${fmtQty(it)} %", MaterialTheme.colorScheme.tertiary)
                }
                sensor.lastTargetTemp?.let {
                    Metric(stringResource(R.string.temp_target), "${fmtQty(it)} °C", MaterialTheme.colorScheme.secondary)
                }
            }
            sensor.lastReadingAt?.let {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.spindle_last_reading, fmtTempTime(it), sensor.readingCount ?: 0),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

/** Détail d'une sonde : grand graphe température (et humidité) dans le temps. */
@Composable
fun TempDetailScreen(vm: BrewViewModel, sensorId: Int?) {
    val sensors by vm.tempSensors.collectAsState()
    val readingsMap by vm.tempReadings.collectAsState()
    val sensor = sensors?.find { it.id == sensorId }
    LaunchedEffect(sensorId) {
        if (sensorId != null) {
            if (sensors == null) vm.loadTempSensors()
            vm.loadTempReadings(sensorId)
        }
    }
    if (sensor == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val readings = readingsMap[sensor.id].orEmpty()
    val tempColor = MaterialTheme.colorScheme.primary
    val humColor = MaterialTheme.colorScheme.tertiary
    val targetColor = MaterialTheme.colorScheme.secondary

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(sensor.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        sensor.brewName?.let {
            Text(stringResource(R.string.spindle_brew, it), color = MaterialTheme.colorScheme.primary)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric(stringResource(R.string.temp_current), sensor.lastTemperature?.let { "${fmtQty(it)} °C" } ?: "–", tempColor)
            sensor.lastHumidity?.let { Metric(stringResource(R.string.temp_humidity), "${fmtQty(it)} %", humColor) }
            sensor.lastTargetTemp?.let { Metric(stringResource(R.string.temp_target), "${fmtQty(it)} °C", targetColor) }
        }
        if (readings.size < 2) {
            Text(
                stringResource(R.string.probe_no_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        } else {
            val ts = readings.map { it.recordedAt }
            SectionTitleLocal(stringResource(R.string.temp_current))
            val series = buildList {
                add(ChartSeries(stringResource(R.string.temp_current), readings.map { it.temperature }, tempColor, "°C", 1))
                if (readings.any { it.targetTemp != null }) {
                    add(ChartSeries(stringResource(R.string.temp_target), readings.map { it.targetTemp }, targetColor, "°C", 1))
                }
            }
            ProbeChart(ts, series)
            if (readings.any { it.humidity != null }) {
                SectionTitleLocal(stringResource(R.string.temp_humidity))
                ProbeChart(
                    ts,
                    listOf(ChartSeries(stringResource(R.string.temp_humidity), readings.map { it.humidity }, humColor, "%", 0)),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
