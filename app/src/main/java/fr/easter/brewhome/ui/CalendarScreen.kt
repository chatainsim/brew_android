package fr.easter.brewhome.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.easter.brewhome.BrewViewModel
import fr.easter.brewhome.R
import fr.easter.brewhome.calc.CalendarEvents
import fr.easter.brewhome.data.CustomEventPost
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRANCE)
private val fullFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRANCE)
private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRANCE)

/** Agenda : 6 mois à venir ou 12 mois passés, brassins, rappels, fêtes de la bière. */
@Composable
fun CalendarScreen(vm: BrewViewModel, onOpenBrew: (Int) -> Unit, onOpenDraft: (Int) -> Unit) {
    val state by vm.state.collectAsState()
    val customEvents by vm.customEvents.collectAsState()
    LaunchedEffect(Unit) { vm.loadCustomEvents() }

    var showPast by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<CalendarEvents.Event?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    RefreshableContent(vm) {
        val today = remember { LocalDate.now() }
        val events = remember(state.brews, state.recipes, state.beers, state.drafts, customEvents, showPast) {
            val list = CalendarEvents.agenda(
                from = if (showPast) today.minusMonths(12) else today,
                to = if (showPast) today.minusDays(1) else today.plusDays(180),
                brews = state.brews,
                recipes = state.recipes.associateBy { it.id },
                beers = state.beers,
                drafts = state.drafts,
                customEvents = customEvents ?: emptyList(),
            )
            // Passé : les plus récents d'abord
            if (showPast) list.reversed() else list
        }

        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) {
                    FilterChip(
                        selected = !showPast,
                        onClick = { showPast = false },
                        label = { Text(stringResource(R.string.cal_upcoming)) },
                    )
                    FilterChip(
                        selected = showPast,
                        onClick = { showPast = true },
                        label = { Text(stringResource(R.string.cal_past)) },
                    )
                }
                if (events.isEmpty()) {
                    EmptyHint(stringResource(R.string.cal_empty), Icons.Outlined.CalendarMonth)
                } else {
                    val weekEnd = today.plusDays(7)
                    val monthEnd = today.withDayOfMonth(today.lengthOfMonth())
                    fun groupOf(date: LocalDate): String = when {
                        showPast -> monthFormatter.format(date)
                        date == today -> "today"
                        date < weekEnd -> "week"
                        date <= monthEnd -> "month"
                        else -> monthFormatter.format(date)
                    }

                    LazyColumn(
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 88.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        var lastGroup: String? = null
                        events.forEachIndexed { i, ev ->
                            val group = groupOf(ev.date)
                            if (group != lastGroup) {
                                lastGroup = group
                                item(key = "head-$group") {
                                    Text(
                                        when (group) {
                                            "today" -> stringResource(R.string.cal_today)
                                            "week" -> stringResource(R.string.cal_this_week)
                                            "month" -> stringResource(R.string.cal_this_month)
                                            else -> group.replaceFirstChar { it.uppercase() }
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (group == "today") MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                                    )
                                }
                            }
                            item(key = "ev-$i") { EventRow(ev) { selected = ev } }
                        }
                    }
                }
            }
            FloatingActionButton(
                onClick = { showAdd = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cal_add_event))
            }
        }
    }

    selected?.let { ev ->
        EventDetailDialog(
            ev = ev,
            onDismiss = { selected = null },
            onOpenBrew = ev.brewId?.let { id -> { selected = null; onOpenBrew(id) } },
            onOpenDraft = ev.draftId?.let { id -> { selected = null; onOpenDraft(id) } },
            // Dry hop non encore fait : bouton pour le marquer (déduit le stock)
            onDryhopDone = if (ev.type == CalendarEvents.Type.DRYHOP && !ev.dryhopDone && ev.brewId != null) {
                {
                    vm.markDryhopDone(ev.brewId, ev.date.toString())
                    selected = null
                }
            } else null,
            onDelete = if (ev.type == CalendarEvents.Type.CUSTOM) {
                {
                    val custom = (vm.customEvents.value ?: emptyList()).find { it.id == ev.customId }
                    if (custom != null) vm.deleteCustomEvent(custom)
                    selected = null
                }
            } else null,
        )
    }

    if (showAdd) {
        AddEventDialog(
            onAdd = { post -> vm.addCustomEvent(post) { showAdd = false } },
            onDismiss = { showAdd = false },
        )
    }
}

@Composable
private fun EventRow(ev: CalendarEvents.Event, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
    ) {
        Text(
            dayFormatter.format(ev.date),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.width(78.dp),
        )
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(eventColor(ev)),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "${ev.emoji} ${ev.label}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun eventTypeLabel(type: CalendarEvents.Type): String = stringResource(
    when (type) {
        CalendarEvents.Type.BREW -> R.string.cal_type_brew
        CalendarEvents.Type.BOTTLE -> R.string.cal_type_bottle
        CalendarEvents.Type.FERM_END -> R.string.cal_type_ferm
        CalendarEvents.Type.DRYHOP -> R.string.cal_type_dryhop
        CalendarEvents.Type.REFERM -> R.string.cal_type_referm
        CalendarEvents.Type.DRAFT -> R.string.cal_type_draft
        CalendarEvents.Type.CUSTOM -> R.string.cal_type_custom
        CalendarEvents.Type.REMIND -> R.string.cal_type_remind
        CalendarEvents.Type.WORLD -> R.string.cal_type_world
    },
)

@Composable
private fun EventDetailDialog(
    ev: CalendarEvents.Event,
    onDismiss: () -> Unit,
    onOpenBrew: (() -> Unit)?,
    onOpenDraft: (() -> Unit)?,
    onDryhopDone: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${ev.emoji} ${ev.label}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    fullFormatter.format(ev.date).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(eventColor(ev)),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        eventTypeLabel(ev.type),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                if (!ev.notes.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(ev.notes, style = MaterialTheme.typography.bodyMedium)
                }
                if (ev.type == CalendarEvents.Type.DRYHOP && ev.dryhopDone) {
                    Text(
                        stringResource(R.string.cal_dryhop_done),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
        confirmButton = {
            when {
                onDryhopDone != null -> TextButton(onClick = onDryhopDone) {
                    Text(stringResource(R.string.cal_dryhop_mark))
                }
                onOpenBrew != null -> TextButton(onClick = onOpenBrew) {
                    Text(stringResource(R.string.cal_open_brew))
                }
                onOpenDraft != null -> TextButton(onClick = onOpenDraft) {
                    Text(stringResource(R.string.cal_open_draft))
                }
                else -> TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            }
        },
        dismissButton = {
            if (onDelete != null) {
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            }
        },
    )
}

// Palette du site pour les événements personnalisés
private val eventColors = listOf(
    "#f59e0b", "#dc2626", "#16a34a", "#3b82f6", "#8b5cf6", "#ec4899", "#06b6d4", "#6b7280",
)

private val recurrenceChoices = listOf(
    null to R.string.cal_rec_none,
    """{"type":"weekly"}""" to R.string.cal_rec_weekly,
    """{"type":"monthly"}""" to R.string.cal_rec_monthly,
    """{"type":"yearly"}""" to R.string.cal_rec_yearly,
)

@Composable
private fun AddEventDialog(onAdd: (CustomEventPost) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("📅") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var notes by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(eventColors.first()) }
    var recurrence by remember { mutableStateOf<String?>(null) }
    var reminder by remember { mutableStateOf(false) }
    var reminderDays by remember { mutableStateOf("45") }
    val dateOk = runCatching { LocalDate.parse(date.trim()) }.isSuccess

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cal_add_event)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { emoji = it },
                        label = { Text(stringResource(R.string.cal_emoji)) },
                        singleLine = true,
                        modifier = Modifier.width(76.dp),
                    )
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(R.string.label_title)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                DateField(
                    value = date,
                    onValueChange = { date = it },
                    label = stringResource(R.string.cal_date),
                    isError = !dateOk,
                    modifier = Modifier.fillMaxWidth(),
                )
                // Couleur de la pastille
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    eventColors.forEach { hex ->
                        val c = Color(android.graphics.Color.parseColor(hex))
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(c)
                                .clickable { color = hex },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (color == hex) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
                Text(
                    stringResource(R.string.cal_recurrence),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    recurrenceChoices.forEach { (value, labelRes) ->
                        FilterChip(
                            selected = recurrence == value,
                            onClick = { recurrence = value },
                            label = { Text(stringResource(labelRes)) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.cal_reminder),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (reminder) {
                        OutlinedTextField(
                            value = reminderDays,
                            onValueChange = { reminderDays = it },
                            label = { Text(stringResource(R.string.cal_reminder_days)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(90.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Switch(checked = reminder, onCheckedChange = { reminder = it })
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.notes)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onAdd(CustomEventPost(
                        title = title.trim(),
                        emoji = emoji.trim().ifBlank { "📅" },
                        eventDate = date.trim(),
                        color = color,
                        notes = notes.trim().ifBlank { null },
                        brewReminder = reminder,
                        brewReminderDays = reminderDays.trim().toIntOrNull(),
                        recurrence = recurrence,
                    ))
                },
                enabled = title.isNotBlank() && dateOk,
            ) { Text(stringResource(R.string.add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun eventColor(ev: CalendarEvents.Event): Color {
    val fromHex = ev.colorHex?.let { hex ->
        runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
    }
    if (fromHex != null) return fromHex
    return when (ev.type) {
        CalendarEvents.Type.BREW -> MaterialTheme.colorScheme.primary
        CalendarEvents.Type.BOTTLE, CalendarEvents.Type.DRYHOP -> MaterialTheme.colorScheme.tertiary
        CalendarEvents.Type.FERM_END, CalendarEvents.Type.REFERM,
        CalendarEvents.Type.DRAFT, CalendarEvents.Type.REMIND,
        -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.outline
    }
}
