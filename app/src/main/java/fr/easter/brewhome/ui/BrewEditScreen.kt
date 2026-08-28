package fr.easter.brewhome.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.easter.brewhome.BrewViewModel
import fr.easter.brewhome.R
import fr.easter.brewhome.data.BrewPut

/** Édition des champs d'un brassin (mesures réelles OG/FG, volume, dates, notes). */
@Composable
fun BrewEditScreen(vm: BrewViewModel, brewId: Int?, onSaved: () -> Unit) {
    val state by vm.state.collectAsState()
    val brew = state.brews.find { it.id == brewId }
    if (brew == null) {
        EmptyHint(stringResource(R.string.brew_not_found))
        return
    }

    var name by rememberSaveable { mutableStateOf(brew.name) }
    var brewDate by rememberSaveable { mutableStateOf(brew.brewDate ?: "") }
    var volume by rememberSaveable { mutableStateOf(brew.volumeBrewed?.let { fmtQty(it).replace(',', '.') } ?: "") }
    var og by rememberSaveable { mutableStateOf(brew.og?.let { fmtGravity3(it) } ?: "") }
    var fg by rememberSaveable { mutableStateOf(brew.fg?.let { fmtGravity3(it) } ?: "") }
    var abv by rememberSaveable { mutableStateOf(brew.abv?.let { fmtQty(it).replace(',', '.') } ?: "") }
    var fermTime by rememberSaveable { mutableStateOf(brew.fermTime?.toString() ?: "") }
    var batch by rememberSaveable { mutableStateOf(brew.batchNumber?.toString() ?: "") }
    var notes by rememberSaveable { mutableStateOf(brew.notes ?: "") }
    var saving by remember { mutableStateOf(false) }

    fun num(s: String) = s.trim().replace(',', '.').toDoubleOrNull()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text(stringResource(R.string.label_name)) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DateField(
                value = brewDate, onValueChange = { brewDate = it },
                label = stringResource(R.string.label_brew_date),
                modifier = Modifier.weight(1f),
            )
            NumField2(volume, { volume = it }, stringResource(R.string.label_volume_brewed), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumField2(og, { og = it }, stringResource(R.string.label_og), Modifier.weight(1f))
            NumField2(fg, { fg = it }, stringResource(R.string.label_fg), Modifier.weight(1f))
            NumField2(abv, { abv = it }, stringResource(R.string.label_abv), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumField2(fermTime, { fermTime = it }, stringResource(R.string.brew_ferm_days), Modifier.weight(1f))
            NumField2(batch, { batch = it }, stringResource(R.string.brew_batch), Modifier.weight(1f))
        }
        OutlinedTextField(
            value = notes, onValueChange = { notes = it },
            label = { Text(stringResource(R.string.notes)) },
            minLines = 3, modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = {
                saving = true
                vm.saveBrew(brew.id, BrewPut(
                    name = name.trim(),
                    status = brew.status ?: "completed",
                    brewDate = brewDate.trim().ifBlank { null },
                    volumeBrewed = num(volume),
                    og = num(og),
                    fg = num(fg),
                    abv = num(abv),
                    notes = notes.trim().ifBlank { null },
                    fermTime = fermTime.trim().toIntOrNull(),
                    photosUrl = brew.photosUrl,
                    costSnapshot = brew.costSnapshot,
                    costPerLiter = brew.costPerLiter,
                    batchNumber = batch.trim().toIntOrNull(),
                )) { onSaved() }
                saving = false
            },
            enabled = name.isNotBlank() && !saving,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.save), Modifier.padding(start = 8.dp), fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun fmtGravity3(g: Double): String =
    if (g >= 2.0) fmtQty(g) else String.format(java.util.Locale.US, "%.3f", g)

@Composable
private fun NumField2(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}
