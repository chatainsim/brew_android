package fr.easter.brewhome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Sports
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.easter.brewhome.BrewViewModel
import fr.easter.brewhome.R
import fr.easter.brewhome.data.SodaKeg

@Composable
private fun kegStatusColors(status: String): Pair<Color, Color> = MaterialTheme.colorScheme.let {
    when (status) {
        "serving" -> it.primaryContainer to it.onPrimaryContainer
        "fermenting" -> it.tertiaryContainer to it.onTertiaryContainer
        "cleaning" -> it.secondaryContainer to it.onSecondaryContainer
        else -> it.surfaceVariant to it.onSurfaceVariant
    }
}

@Composable
private fun kegStatusLabel(status: String): String = stringResource(
    when (status) {
        "serving" -> R.string.keg_status_serving
        "fermenting" -> R.string.keg_status_fermenting
        "cleaning" -> R.string.keg_status_cleaning
        else -> R.string.keg_status_empty
    },
)

private val kegStatuses = listOf("empty", "fermenting", "serving", "cleaning")

/** Vue des fûts à soda : niveau, état de service, révisions. */
@Composable
fun KegsScreen(vm: BrewViewModel) {
    val kegs by vm.sodaKegs.collectAsState()
    LaunchedEffect(Unit) { vm.loadSodaKegs() }
    var editing by remember { mutableStateOf<SodaKeg?>(null) }
    var creating by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        when {
            kegs == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            kegs!!.none { (it.archived ?: 0) == 0 } -> EmptyHint(stringResource(R.string.kegs_empty), Icons.Outlined.Sports)
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(kegs!!.filter { (it.archived ?: 0) == 0 }, key = { it.id }) { keg ->
                    KegCard(keg) { editing = keg }
                }
            }
        }
        androidx.compose.material3.ExtendedFloatingActionButton(
            onClick = { creating = true },
            icon = { androidx.compose.material3.Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.keg_add)) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
    }

    editing?.let { keg ->
        KegEditDialog(
            keg = keg,
            onDismiss = { editing = null },
            onSave = { status, liters -> vm.updateKeg(keg.id, status, liters) { editing = null } },
            onRevised = { vm.markKegRevised(keg.id) { editing = null } },
            onDelete = { vm.deleteKeg(keg.id) { editing = null } },
        )
    }

    if (creating) {
        KegCreateDialog(
            onDismiss = { creating = false },
            onCreate = { name, type, volume, interval, lastRev ->
                vm.createKeg(name, type, volume, interval, lastRev) { creating = false }
            },
        )
    }
}

@Composable
private fun KegCreateDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, type: String?, volume: Double?, interval: Int, lastRev: String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    var volume by remember { mutableStateOf("") }
    var interval by remember { mutableStateOf("12") }
    var lastRev by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.keg_add)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
            ) {
                androidx.compose.material3.OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.keg_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                androidx.compose.material3.OutlinedTextField(
                    value = type,
                    onValueChange = { type = it },
                    label = { Text(stringResource(R.string.keg_type)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                androidx.compose.material3.OutlinedTextField(
                    value = volume,
                    onValueChange = { volume = it },
                    label = { Text(stringResource(R.string.keg_volume_total)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                androidx.compose.material3.OutlinedTextField(
                    value = interval,
                    onValueChange = { interval = it },
                    label = { Text(stringResource(R.string.keg_interval_months)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                androidx.compose.material3.OutlinedTextField(
                    value = lastRev,
                    onValueChange = { lastRev = it },
                    label = { Text(stringResource(R.string.keg_last_revision)) },
                    placeholder = { Text("AAAA-MM-JJ") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onCreate(
                        name.trim(),
                        type.trim().ifBlank { null },
                        volume.trim().replace(',', '.').toDoubleOrNull(),
                        interval.trim().toIntOrNull() ?: 12,
                        lastRev.trim().ifBlank { null },
                    )
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun KegEditDialog(
    keg: SodaKeg,
    onDismiss: () -> Unit,
    onSave: (status: String, currentLiters: Double?) -> Unit,
    onRevised: () -> Unit,
    onDelete: () -> Unit,
) {
    var status by remember { mutableStateOf(keg.status) }
    var liters by remember { mutableStateOf(keg.currentLiters?.let { fmtQty(it).replace(',', '.') } ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    val statusLabels = kegStatuses.associateWith { kegStatusLabel(it) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(keg.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.keg_status), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    kegStatuses.forEach { s ->
                        androidx.compose.material3.FilterChip(
                            selected = status == s,
                            onClick = { status = s },
                            label = { Text(statusLabels.getValue(s), style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
                androidx.compose.material3.OutlinedTextField(
                    value = liters,
                    onValueChange = { liters = it },
                    label = { Text(stringResource(R.string.keg_level)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                androidx.compose.material3.HorizontalDivider()
                keg.lastRevisionDate?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        stringResource(R.string.keg_last_revision_at, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = onRevised,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.keg_mark_revised)) }
                    androidx.compose.material3.OutlinedButton(
                        onClick = { confirmDelete = true },
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) { Text(stringResource(R.string.delete)) }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                onSave(status, liters.trim().replace(',', '.').toDoubleOrNull())
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )

    if (confirmDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.keg_delete_confirm, keg.name)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun KegCard(keg: SodaKeg, onClick: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
            // Liseré de la couleur du fût
            val accent = keg.color?.let { hex ->
                runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
            } ?: MaterialTheme.colorScheme.primary
            Box(
                Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(accent),
            )
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        keg.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    val (container, content) = kegStatusColors(keg.status)
                    StatusChip(kegStatusLabel(keg.status), container, content)
                }
                val subtitle = listOfNotNull(
                    keg.beerName ?: keg.brewName,
                    keg.kegType,
                ).joinToString(" · ")
                if (subtitle.isNotEmpty()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                // Niveau de remplissage
                val current = keg.currentLiters ?: 0.0
                val total = keg.volumeTotal?.takeIf { it > 0 }
                if (total != null) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth((current / total).toFloat().coerceIn(0f, 1f))
                                .clip(RoundedCornerShape(7.dp))
                                .background(accent),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${fmtQty(current)} / ${fmtQty(total)} L",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                keg.nextRevisionDate?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(6.dp))
                    val overdue = it <= java.time.LocalDate.now().toString()
                    Text(
                        stringResource(R.string.keg_revision, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (overdue) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.outline,
                        fontWeight = if (overdue) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
