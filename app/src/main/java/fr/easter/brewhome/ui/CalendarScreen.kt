package fr.easter.brewhome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.easter.brewhome.BrewViewModel
import fr.easter.brewhome.R
import fr.easter.brewhome.calc.CalendarEvents
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRANCE)
private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRANCE)

/** Agenda des 6 prochains mois : brassins, dry hops, rappels, fêtes de la bière. */
@Composable
fun CalendarScreen(vm: BrewViewModel) {
    val state by vm.state.collectAsState()
    val customEvents by vm.customEvents.collectAsState()
    LaunchedEffect(Unit) { vm.loadCustomEvents() }

    RefreshableContent(vm) {
        val today = remember { LocalDate.now() }
        val events = remember(state.brews, state.recipes, state.beers, state.drafts, customEvents) {
            CalendarEvents.agenda(
                today = today,
                brews = state.brews,
                recipes = state.recipes.associateBy { it.id },
                beers = state.beers,
                drafts = state.drafts,
                customEvents = customEvents ?: emptyList(),
            )
        }
        if (events.isEmpty()) {
            EmptyHint(stringResource(R.string.cal_empty), Icons.Outlined.CalendarMonth)
            return@RefreshableContent
        }

        val weekEnd = today.plusDays(7)
        val monthEnd = today.withDayOfMonth(today.lengthOfMonth())
        fun groupOf(date: LocalDate): String = when {
            date == today -> "today"
            date < weekEnd -> "week"
            date <= monthEnd -> "month"
            else -> monthFormatter.format(date)
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
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
                item(key = "ev-$i") { EventRow(ev) }
            }
        }
    }
}

@Composable
private fun EventRow(ev: CalendarEvents.Event) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
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
