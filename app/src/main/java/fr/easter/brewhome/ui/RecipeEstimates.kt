package fr.easter.brewhome.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.easter.brewhome.R
import fr.easter.brewhome.calc.RecipeEstimator
import fr.easter.brewhome.data.BjcpStyle
import fr.easter.brewhome.data.CatalogItem
import fr.easter.brewhome.data.RecipeIngredient
import java.util.Locale

/** RecipeIngredient → ingrédient d'estimation, gu/ebc/alpha complétés par le catalogue. */
fun estIngredients(
    ings: List<RecipeIngredient>,
    catalog: List<CatalogItem>,
): List<RecipeEstimator.Ing> = ings.map { ing ->
    val cat = catalog.find { it.name.equals(ing.name.trim(), ignoreCase = true) }
    RecipeEstimator.Ing(
        name = ing.name,
        category = ing.category.lowercase(),
        quantity = ing.quantity,
        unit = ing.unit,
        hopTime = ing.hopTime,
        hopType = ing.hopType,
        alpha = ing.alpha ?: cat?.alpha,
        ebc = ing.ebc ?: cat?.ebc,
        gu = cat?.gu,
        inventoryItemId = ing.inventoryItemId,
    )
}

private fun estFmt(v: Double, dec: Int): String =
    String.format(Locale.FRANCE, "%.${dec}f", v)

private val InRangeGreen = Color(0xFF16A34A)

/**
 * Carte « Estimations » : jauges OG/FG/ABV/IBU/EBC avec plage du style BJCP,
 * pastille de couleur Morey, plan d'eau et coût matières estimé.
 */
@Composable
fun RecipeEstimatesCard(
    est: RecipeEstimator.Estimates,
    style: BjcpStyle?,
    water: RecipeEstimator.Water?,
    cost: RecipeEstimator.Cost?,
    volume: Double?,
    ibuFormula: String,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.est_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.est_ibu_formula, ibuFormula.uppercase()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            EstRow("OG", est.og, 1.020, 1.130, 3, "", style?.ogMin, style?.ogMax)
            EstRow("FG", est.fg, 1.002, 1.030, 3, "", style?.fgMin, style?.fgMax)
            EstRow("ABV", est.abv, 0.0, 14.0, 1, " %", style?.abvMin, style?.abvMax)
            EstRow("IBU", est.ibu, 0.0, 120.0, 0, "", style?.ibuMin, style?.ibuMax)
            EstRow("EBC", est.ebc, 0.0, 120.0, 0, "", style?.ebcMin, style?.ebcMax)

            if (est.ebc != null && est.srm != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(ebcColor(est.ebc)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(
                            R.string.est_color_morey,
                            est.ebc.toInt(), est.srm.toInt(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            if (water != null) {
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    WaterStat(stringResource(R.string.est_water_mash), water.mash)
                    WaterStat(stringResource(R.string.est_water_sparge), water.sparge)
                    WaterStat(stringResource(R.string.est_water_preboil), water.preboil)
                    WaterStat(stringResource(R.string.est_water_total), water.total)
                }
            }

            if (cost != null && cost.total > 0) {
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.est_cost_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${estFmt(cost.total, 2)} €",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    volume?.takeIf { it > 0 }?.let {
                        Text(
                            "  ${estFmt(cost.total / it, 2)} €/L",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
                // Tuiles par catégorie, dans l'ordre malt/houblon/levure/autre
                val tiles = categoryOrder.mapNotNull { cat ->
                    cost.byCategory[cat]?.takeIf { it > 0 }?.let { cat to it }
                }
                tiles.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { (cat, v) ->
                            CostTile(
                                label = categoryLabel(cat),
                                value = v,
                                total = cost.total,
                                accent = categoryColor(cat),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                // Coûts fixes : eau, gaz, électricité
                listOfNotNull(
                    cost.water?.takeIf { it > 0 }?.let { stringResource(R.string.est_cost_water) to it },
                    cost.gas.takeIf { it > 0 }?.let { stringResource(R.string.est_cost_gas) to it },
                    cost.elec.takeIf { it > 0 }?.let { stringResource(R.string.est_cost_elec) to it },
                ).forEach { (label, v) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${estFmt(v, 2)} €",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "  " + stringResource(
                                R.string.est_pct_total,
                                (v / cost.total * 100).toInt(),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WaterStat(label: String, liters: Double) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "${estFmt(liters, 1)} L",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun CostTile(
    label: String,
    value: Double,
    total: Double,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier,
    ) {
        Row {
            Box(
                Modifier
                    .width(4.dp)
                    .height(58.dp)
                    .background(accent),
            )
            Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                )
                Text(
                    "${estFmt(value, 2)} €",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.est_pct_total, (value / total * 100).toInt()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

/**
 * Jauge : bande verte = plage cible du style BJCP, rond = valeur estimée
 * (vert dans la plage, rouge en dehors, ambre sans style).
 */
@Composable
private fun EstRow(
    label: String,
    value: Double?,
    cfgMin: Double,
    cfgMax: Double,
    dec: Int,
    unit: String,
    bjcpMin: Double?,
    bjcpMax: Double?,
) {
    val hasBjcp = bjcpMin != null && bjcpMax != null
    val inRange = hasBjcp && value != null && value >= bjcpMin!! && value <= bjcpMax!!
    val markerColor = when {
        value == null -> MaterialTheme.colorScheme.outline
        !hasBjcp -> MaterialTheme.colorScheme.primary
        inRange -> InRangeGreen
        else -> MaterialTheme.colorScheme.error
    }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val bandColor = InRangeGreen.copy(alpha = 0.22f)
    val ringColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(40.dp),
        )
        Canvas(
            Modifier
                .weight(1f)
                .height(16.dp),
        ) {
            val span = (cfgMax - cfgMin).toFloat()
            fun xOf(v: Double): Float =
                (((v - cfgMin) / span).toFloat().coerceIn(0f, 1f)) * size.width
            val midY = size.height / 2
            drawRoundRect(
                trackColor,
                topLeft = Offset(0f, midY - 3.dp.toPx() / 2),
                size = Size(size.width, 3.dp.toPx()),
                cornerRadius = CornerRadius(1.5.dp.toPx()),
            )
            if (hasBjcp) {
                val left = xOf(bjcpMin!!)
                drawRoundRect(
                    bandColor,
                    topLeft = Offset(left, midY - 5.dp.toPx()),
                    size = Size(
                        (xOf(bjcpMax!!) - left).coerceAtLeast(4.dp.toPx()),
                        10.dp.toPx(),
                    ),
                    cornerRadius = CornerRadius(5.dp.toPx()),
                )
            }
            if (value != null) {
                val x = xOf(value)
                drawCircle(ringColor, radius = 8.dp.toPx(), center = Offset(x, midY))
                drawCircle(markerColor, radius = 5.5.dp.toPx(), center = Offset(x, midY))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(min = 88.dp)) {
            Text(
                if (value != null) estFmt(value, dec) + unit else "–",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = markerColor,
            )
            if (hasBjcp) {
                Text(
                    "⌖ ${estFmt(bjcpMin!!, dec)}–${estFmt(bjcpMax!!, dec)}$unit",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}
