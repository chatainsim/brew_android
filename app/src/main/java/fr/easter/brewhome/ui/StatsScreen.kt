package fr.easter.brewhome.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import fr.easter.brewhome.BrewViewModel
import fr.easter.brewhome.R

/** Statistiques de brasserie — même esprit que la page Stats du site. */
@Composable
fun StatsScreen(vm: BrewViewModel) {
    val state by vm.state.collectAsState()
    val consumption by vm.consumption.collectAsState()
    LaunchedEffect(Unit) { vm.loadConsumption() }

    // Comme le site : brassins terminés, non archivés, avec date
    val allDone = state.brews.filter {
        (it.archived ?: 0) == 0 && it.status == "completed" && it.brewDate != null
    }
    val years = allDone.mapNotNull { it.brewDate?.take(4) }.distinct().sortedDescending()
    var year by rememberSaveable { mutableStateOf<String?>(null) }
    val done = year.let { y ->
        if (y == null) allDone else allDone.filter { it.brewDate!!.startsWith(y) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Filtre par année ──
        if (years.size > 1) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = year == null,
                    onClick = { year = null },
                    label = { Text(stringResource(R.string.stat_year_all)) },
                )
                years.forEach { y ->
                    FilterChip(
                        selected = year == y,
                        onClick = { year = y },
                        label = { Text(y) },
                    )
                }
            }
        }
        // ── Grands chiffres ──
        val totalVol = done.sumOf { it.volumeBrewed ?: 0.0 }
        val abvs = done.mapNotNull { it.abv }
        val totalCost = done.sumOf { it.costSnapshot ?: 0.0 }
        val cs = MaterialTheme.colorScheme
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(
                "${done.size}", stringResource(R.string.stat_brews_done),
                cs.primaryContainer, cs.onPrimaryContainer, Modifier.weight(1f),
            )
            StatCard(
                "${fmtQty(totalVol)} L", stringResource(R.string.stat_liters_brewed),
                cs.tertiaryContainer, cs.onTertiaryContainer, Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(
                if (abvs.isEmpty()) "—" else "${fmtQty(kotlin.math.round(abvs.average() * 10) / 10)} %",
                stringResource(R.string.stat_avg_abv),
                cs.secondaryContainer, cs.onSecondaryContainer, Modifier.weight(1f),
            )
            StatCard(
                if (totalCost > 0) "${fmtQty(kotlin.math.round(totalCost))} €" else "—",
                stringResource(R.string.stat_total_cost),
                cs.surfaceVariant, cs.onSurfaceVariant, Modifier.weight(1f),
            )
        }
        if (done.isEmpty()) {
            Text(
                stringResource(R.string.stat_no_brews),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        }

        // ── Production en détail ──
        if (done.isNotEmpty()) {
            val vols = done.mapNotNull { it.volumeBrewed }
            val effs = done.mapNotNull { it.actualEfficiency }
            val fermDays = done.mapNotNull { it.fermTime }
            SectionTitle(stringResource(R.string.stat_prod_details))
            InfoCard {
                InfoLine(
                    stringResource(R.string.stat_avg_volume),
                    vols.takeIf { it.isNotEmpty() }
                        ?.let { "${fmtQty(kotlin.math.round(it.average() * 10) / 10)} L" },
                )
                InfoLine(
                    stringResource(R.string.stat_avg_efficiency),
                    effs.takeIf { it.isNotEmpty() }
                        ?.let { "${fmtQty(kotlin.math.round(it.average() * 10) / 10)} %" },
                )
                InfoLine(
                    stringResource(R.string.stat_avg_ferm),
                    fermDays.takeIf { it.isNotEmpty() }
                        ?.let { "${kotlin.math.round(it.average()).toInt()} jours" },
                )
                InfoLine(
                    stringResource(R.string.stat_avg_cost),
                    if (totalCost > 0 && totalVol > 0)
                        "${fmtQty(kotlin.math.round(totalCost / totalVol * 100) / 100)} €/L"
                    else null,
                )
                InfoLine(
                    stringResource(R.string.stat_avg_og),
                    done.mapNotNull { it.og }.takeIf { it.isNotEmpty() }
                        ?.let { String.format(java.util.Locale.US, "%.3f", it.average()) },
                )
            }
        }

        // ── Volume par année (redondant quand une seule année est affichée) ──
        val byYear = allDone.groupBy { it.brewDate!!.take(4) }
            .mapValues { (_, brews) -> brews.sumOf { it.volumeBrewed ?: 0.0 } }
            .toSortedMap(compareByDescending { it })
        if (year == null && byYear.isNotEmpty()) {
            SectionTitle(stringResource(R.string.stat_volume_by_year))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    val max = byYear.values.max().takeIf { it > 0 } ?: 1.0
                    byYear.forEach { (year, vol) ->
                        BarRow(year, "${fmtQty(vol)} L", (vol / max).toFloat())
                    }
                }
            }
        }

        // ── Styles les plus brassés ──
        val recipeById = state.recipes.associateBy { it.id }
        val styles = done.mapNotNull { b ->
            b.recipeStyle ?: b.recipeId?.let { recipeById[it]?.style }
        }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(6)
        if (styles.isNotEmpty()) {
            SectionTitle(stringResource(R.string.stat_top_styles))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    val max = styles.first().value.toDouble()
                    styles.forEach { (style, n) ->
                        BarRow(style, "$n", (n / max).toFloat(), MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
        }

        // ── Cave actuelle ──
        val beers = state.beers.filter { (it.archived ?: 0) == 0 }
        val n33 = beers.sumOf { it.stock33 ?: 0 }
        val n75 = beers.sumOf { it.stock75 ?: 0 }
        val keg = beers.sumOf { it.kegLiters ?: 0.0 }
        val caveL = n33 * 0.33 + n75 * 0.75 + keg
        SectionTitle(stringResource(R.string.stat_cave))
        InfoCard {
            InfoLine(stringResource(R.string.stat_bottles_33), "$n33")
            InfoLine(stringResource(R.string.stat_bottles_75), "$n75")
            InfoLine(stringResource(R.string.stat_kegs), keg.takeIf { it > 0 }?.let { "${fmtQty(it)} L" })
            InfoLine(stringResource(R.string.stat_total), "${fmtQty(kotlin.math.round(caveL * 100) / 100)} L")
        }

        // ── Dégustations ──
        val rated = beers.filter { (it.tasteRating ?: 0) > 0 }
        if (rated.isNotEmpty()) {
            SectionTitle(stringResource(R.string.stat_tastings))
            InfoCard {
                InfoLine(stringResource(R.string.stat_rated_beers), "${rated.size}")
                InfoLine(
                    stringResource(R.string.stat_avg_rating),
                    "${fmtQty(kotlin.math.round(rated.mapNotNull { it.tasteRating }.average() * 10) / 10)} / 5",
                )
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    rated.sortedByDescending { it.tasteRating }.take(3).forEach { beer ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                beer.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            StarRating(beer.tasteRating)
                        }
                    }
                }
            }
        }

        // ── Stock d'ingrédients ──
        val lowCount = state.inventory.count {
            it.minStock != null && it.quantity < it.minStock!!
        }
        val stockValue = state.inventory
            .mapNotNull { item -> item.pricePerUnit?.let { it * item.quantity } }
            .sum()
        SectionTitle(stringResource(R.string.stat_inventory))
        InfoCard {
            InfoLine(stringResource(R.string.stat_items), "${state.inventory.size}")
            InfoLine(stringResource(R.string.stat_low_stock), if (lowCount > 0) "$lowCount ⚠️" else stringResource(R.string.stat_none))
            InfoLine(
                stringResource(R.string.stat_value),
                stockValue.takeIf { it > 0 }
                    ?.let { "${fmtQty(kotlin.math.round(it))} €" },
            )
            InfoLine(stringResource(R.string.stat_on_shopping), state.shopping.size.takeIf { it > 0 }?.let { "$it" })
        }

        // ── Consommation ──
        val cons = consumption
        if (cons == null) {
            Text(
                stringResource(R.string.stat_loading_consumption),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        } else {
            val consMonths = year.let { y ->
                if (y == null) cons.byMonth else cons.byMonth.filter { it.period.startsWith(y) }
            }
            val months = if (year == null) consMonths.takeLast(12) else consMonths
            if (months.isNotEmpty()) {
                SectionTitle(
                    year?.let { stringResource(R.string.stat_consumption_year, it) }
                        ?: stringResource(R.string.stat_consumption_12m),
                )
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        fun liters(m: fr.easter.brewhome.data.ConsumptionMonth) =
                            (m.total33 ?: 0) * 0.33 + (m.total75 ?: 0) * 0.75 + (m.totalKeg ?: 0.0)
                        val max = months.maxOf { liters(it) }.takeIf { it > 0 } ?: 1.0
                        months.forEach { m ->
                            val l = liters(m)
                            BarRow(m.period, "${fmtQty(kotlin.math.round(l * 10) / 10)} L", (l / max).toFloat())
                        }
                    }
                }
            }
            // Totaux par format (toutes périodes, ou l'année filtrée)
            val t33 = consMonths.sumOf { it.total33 ?: 0 }
            val t75 = consMonths.sumOf { it.total75 ?: 0 }
            val tKeg = consMonths.sumOf { it.totalKeg ?: 0.0 }
            if (t33 + t75 > 0 || tKeg > 0) {
                SectionTitle(
                    year?.let { stringResource(R.string.stat_totals_year, it) }
                        ?: stringResource(R.string.stat_consumption_total),
                )
                InfoCard {
                    InfoLine(stringResource(R.string.stat_bottles_33), t33.takeIf { it > 0 }?.let { "$it" })
                    InfoLine(stringResource(R.string.stat_bottles_75), t75.takeIf { it > 0 }?.let { "$it" })
                    InfoLine(stringResource(R.string.stat_from_keg), tKeg.takeIf { it > 0 }?.let { "${fmtQty(it)} L" })
                    InfoLine(
                        stringResource(R.string.stat_total),
                        "${fmtQty(kotlin.math.round((t33 * 0.33 + t75 * 0.75 + tKeg) * 10) / 10)} L",
                    )
                }
            }

            // Top bières : données toutes périodes uniquement, pas filtrables par année
            val topBeers = cons.byBeer.filter { (it.totalLiters ?: 0.0) > 0 }.take(5)
            if (year == null && topBeers.isNotEmpty()) {
                SectionTitle(stringResource(R.string.stat_top_beers))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        val max = topBeers.first().totalLiters ?: 1.0
                        topBeers.forEach { b ->
                            BarRow(
                                b.beerName ?: "?",
                                "${fmtQty(b.totalLiters)} L",
                                ((b.totalLiters ?: 0.0) / max).toFloat(),
                                MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun StatCard(
    value: String,
    label: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = container)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = content,
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = content.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun BarRow(label: String, value: String, fraction: Float, color: Color = MaterialTheme.colorScheme.primary) {
    // Les barres poussent de zéro vers leur valeur à l'apparition
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val animated by animateFloatAsState(
        targetValue = if (appeared) fraction.coerceIn(0.02f, 1f) else 0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "bar",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 3.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(84.dp),
        )
        Box(
            Modifier
                .weight(1f)
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animated)
                    .clip(RoundedCornerShape(7.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(color.copy(alpha = 0.65f), color),
                        ),
                    ),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 52.dp),
        )
    }
}
