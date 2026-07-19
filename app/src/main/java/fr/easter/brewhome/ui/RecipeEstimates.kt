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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.est_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.est_ibu_formula, ibuFormula.uppercase()),
                    style = MaterialTheme.typography.bodySmall,
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
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(ebcColor(est.ebc)),
                    )
                    Spacer(Modifier.width(8.dp))
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
                Text(
                    stringResource(
                        R.string.est_water,
                        estFmt(water.mash, 1), estFmt(water.sparge, 1),
                        estFmt(water.preboil, 1), estFmt(water.total, 1),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (cost != null && cost.total > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.est_cost_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${estFmt(cost.total, 2)} €",
                        style = MaterialTheme.typography.titleSmall,
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
                val parts = buildList {
                    cost.byCategory.forEach { (cat, v) ->
                        if (v > 0) add(categoryLabel(cat) to v)
                    }
                    cost.water?.takeIf { it > 0 }?.let { add(stringResource(R.string.est_cost_water) to it) }
                    cost.gas.takeIf { it > 0 }?.let { add(stringResource(R.string.est_cost_gas) to it) }
                    cost.elec.takeIf { it > 0 }?.let { add(stringResource(R.string.est_cost_elec) to it) }
                }
                Text(
                    parts.joinToString("  ·  ") { (label, v) ->
                        "$label ${estFmt(v, 2)} €"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

/** Jauge : plage BJCP en bande, marqueur coloré vert (dans la plage) / rouge (hors). */
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
        inRange -> Color(0xFF16A34A)
        else -> MaterialTheme.colorScheme.error
    }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val rangeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(34.dp),
        )
        Canvas(
            Modifier
                .weight(1f)
                .height(10.dp),
        ) {
            val span = (cfgMax - cfgMin).toFloat()
            fun xOf(v: Double): Float =
                (((v - cfgMin) / span).toFloat().coerceIn(0f, 1f)) * size.width
            drawRoundRect(
                trackColor,
                topLeft = Offset(0f, size.height * 0.3f),
                size = Size(size.width, size.height * 0.4f),
                cornerRadius = CornerRadius(size.height * 0.2f),
            )
            if (hasBjcp) {
                val left = xOf(bjcpMin!!)
                drawRoundRect(
                    rangeColor,
                    topLeft = Offset(left, size.height * 0.15f),
                    size = Size((xOf(bjcpMax!!) - left).coerceAtLeast(2f), size.height * 0.7f),
                    cornerRadius = CornerRadius(size.height * 0.2f),
                )
            }
            if (value != null) {
                drawRoundRect(
                    markerColor,
                    topLeft = Offset((xOf(value) - 2.5f).coerceAtLeast(0f), 0f),
                    size = Size(5f, size.height),
                    cornerRadius = CornerRadius(2.5f),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            if (value != null) estFmt(value, dec) + unit else "–",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = markerColor,
            modifier = Modifier.width(56.dp),
        )
        Text(
            if (hasBjcp) "⌖ ${estFmt(bjcpMin!!, dec)}–${estFmt(bjcpMax!!, dec)}$unit" else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.width(92.dp),
        )
    }
}
