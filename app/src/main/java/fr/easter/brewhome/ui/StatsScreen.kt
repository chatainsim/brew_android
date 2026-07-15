package fr.easter.brewhome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.easter.brewhome.BrewViewModel

/** Statistiques de brasserie — même esprit que la page Stats du site. */
@Composable
fun StatsScreen(vm: BrewViewModel) {
    val state by vm.state.collectAsState()
    val consumption by vm.consumption.collectAsState()
    LaunchedEffect(Unit) { vm.loadConsumption() }

    // Comme le site : brassins terminés, non archivés, avec date
    val done = state.brews.filter {
        (it.archived ?: 0) == 0 && it.status == "completed" && it.brewDate != null
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Grands chiffres ──
        val totalVol = done.sumOf { it.volumeBrewed ?: 0.0 }
        val abvs = done.mapNotNull { it.abv }
        val totalCost = done.sumOf { it.costSnapshot ?: 0.0 }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("${done.size}", "brassins terminés", Modifier.weight(1f))
            StatCard("${fmtQty(totalVol)} L", "litres brassés", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(
                if (abvs.isEmpty()) "—" else "${fmtQty(kotlin.math.round(abvs.average() * 10) / 10)} %",
                "alcool moyen", Modifier.weight(1f),
            )
            StatCard(
                if (totalCost > 0) "${fmtQty(kotlin.math.round(totalCost))} €" else "—",
                "coût total", Modifier.weight(1f),
            )
        }
        if (done.isEmpty()) {
            Text(
                "Aucun brassin terminé pour l'instant.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        }

        // ── Volume par année ──
        val byYear = done.groupBy { it.brewDate!!.take(4) }
            .mapValues { (_, brews) -> brews.sumOf { it.volumeBrewed ?: 0.0 } }
            .toSortedMap(compareByDescending { it })
        if (byYear.isNotEmpty()) {
            SectionTitle("Volume brassé par année")
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
            SectionTitle("Styles les plus brassés")
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
        SectionTitle("Cave actuelle")
        InfoCard {
            InfoLine("Bouteilles 33 cl", "$n33")
            InfoLine("Bouteilles 75 cl", "$n75")
            InfoLine("Fûts", keg.takeIf { it > 0 }?.let { "${fmtQty(it)} L" })
            InfoLine("Total", "${fmtQty(kotlin.math.round(caveL * 100) / 100)} L")
        }

        // ── Consommation ──
        val cons = consumption
        if (cons == null) {
            Text(
                "Chargement de la consommation…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        } else {
            val months = cons.byMonth.takeLast(12)
            if (months.isNotEmpty()) {
                SectionTitle("Consommation (12 derniers mois)")
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
            val topBeers = cons.byBeer.filter { (it.totalLiters ?: 0.0) > 0 }.take(5)
            if (topBeers.isNotEmpty()) {
                SectionTitle("Bières les plus consommées")
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
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Card(modifier) {
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
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun BarRow(label: String, value: String, fraction: Float, color: Color = MaterialTheme.colorScheme.primary) {
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
                    .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                    .clip(RoundedCornerShape(7.dp))
                    .background(color),
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
