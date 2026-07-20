package fr.easter.brewhome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.easter.brewhome.BrewViewModel
import fr.easter.brewhome.R
import fr.easter.brewhome.calc.CalendarEvents
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val evDayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRANCE)

/** Écran d'accueil : brassins actifs, prochaines échéances, stock bas, cave. */
@Composable
fun DashboardScreen(vm: BrewViewModel, onOpenBrew: (Int) -> Unit, onOpen: (String) -> Unit) {
    val state by vm.state.collectAsState()
    val customEvents by vm.customEvents.collectAsState()
    LaunchedEffect(Unit) { vm.loadCustomEvents() }

    RefreshableContent(vm) {
        val today = remember { LocalDate.now() }
        val events = remember(state.brews, state.recipes, state.beers, state.drafts, customEvents) {
            CalendarEvents.agenda(
                from = today, to = today.plusDays(60),
                brews = state.brews,
                recipes = state.recipes.associateBy { it.id },
                beers = state.beers,
                drafts = state.drafts,
                customEvents = customEvents ?: emptyList(),
            ).filter { it.type != CalendarEvents.Type.WORLD }.take(4)
        }
        val activeBrews = state.brews.filter {
            (it.archived ?: 0) == 0 && (it.status == "in_progress" || it.status == "fermenting")
        }
        val lowStock = state.inventory.filter { it.minStock != null && it.quantity < it.minStock!! }
        val beersInStock = state.beers.filter { (it.archived ?: 0) == 0 }
        val n33 = beersInStock.sumOf { it.stock33 ?: 0 }
        val n75 = beersInStock.sumOf { it.stock75 ?: 0 }
        val keg = beersInStock.sumOf { it.kegLiters ?: 0.0 }
        val caveL = n33 * 0.33 + n75 * 0.75 + keg

        LazyColumn(
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Cave en bref
            item(key = "cave") {
                Card(Modifier.fillMaxWidth().clickable { onOpen("beers") }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.dash_cave),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline,
                            )
                            Text(
                                "${fmtQty(kotlin.math.round(caveL * 10) / 10)} L",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            stringResource(R.string.dash_cave_detail, n33, n75),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }

            // Brassins en cours
            if (activeBrews.isNotEmpty()) {
                item(key = "brews-title") { SectionTitleLocal(stringResource(R.string.dash_active_brews)) }
                items(activeBrews, key = { "brew-${it.id}" }) { brew ->
                    Card(Modifier.fillMaxWidth().clickable { onOpenBrew(brew.id) }) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            val (container, content) = brewStatusColors(brew.status)
                            Column(Modifier.weight(1f)) {
                                Text(brew.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                brew.brewDate?.let {
                                    Text(
                                        stringResource(R.string.brewed_on, it),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
                            }
                            StatusChip(brewStatusLabel(brew.status), container, content)
                        }
                    }
                }
            }

            // Prochaines échéances
            if (events.isNotEmpty()) {
                item(key = "ev-title") { SectionTitleLocal(stringResource(R.string.dash_upcoming)) }
                item(key = "ev-card") {
                    Card(Modifier.fillMaxWidth().clickable { onOpen("calendar") }) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            events.forEach { ev ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        evDayFmt.format(ev.date),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.width(78.dp),
                                    )
                                    Text(
                                        "${ev.emoji} ${ev.label}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Stock bas
            if (lowStock.isNotEmpty()) {
                item(key = "low-title") { SectionTitleLocal(stringResource(R.string.dash_low_stock)) }
                item(key = "low-card") {
                    Card(Modifier.fillMaxWidth().clickable { onOpen("inventory") }) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            lowStock.take(6).forEach { item ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    androidx.compose.material3.Icon(
                                        Icons.Filled.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(item.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${fmtQty(item.quantity)} ${item.unit}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(key = "spacer") { Spacer(Modifier.height(12.dp)) }
        }
    }
}
