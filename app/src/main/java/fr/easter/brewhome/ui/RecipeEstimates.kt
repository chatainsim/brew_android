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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

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
    val c = estColors()
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = c.amber.copy(alpha = if (c.dark) 0.05f else 0.06f),
        border = BorderStroke(1.dp, c.amber.copy(alpha = if (c.dark) 0.18f else 0.22f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            // En-tête façon site : « ESTIMATIONS — 21A. American IPA   IBU: TINSETH »
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 3.dp)) {
                Icon(
                    Icons.Default.BarChart, null,
                    tint = c.amber, modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = c.amber, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp)) {
                            append(stringResource(R.string.est_title).uppercase(Locale.FRENCH))
                        }
                        style?.name?.takeIf { it.isNotBlank() }?.let {
                            withStyle(SpanStyle(color = c.muted)) { append(" — $it") }
                        }
                    },
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.est_ibu_formula, ibuFormula.uppercase()),
                    fontSize = 11.sp,
                    color = c.muted,
                    letterSpacing = 0.5.sp,
                )
            }
            EstRow("OG", est.og, 1.020, 1.130, 3, "", style?.ogMin, style?.ogMax)
            EstRow("FG", est.fg, 1.002, 1.030, 3, "", style?.fgMin, style?.fgMax)
            EstRow("ABV", est.abv, 0.0, 14.0, 1, " %", style?.abvMin, style?.abvMax)
            EstRow("IBU", est.ibu, 0.0, 120.0, 0, "", style?.ibuMin, style?.ibuMax)
            EstRow("EBC", est.ebc, 0.0, 120.0, 0, "", style?.ebcMin, style?.ebcMax)

            if (est.ebc != null && est.srm != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(ebcColor(est.ebc))
                            .border(2.dp, Color.White.copy(alpha = 0.18f), CircleShape),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            stringResource(R.string.est_color_values, est.ebc.roundToInt(), est.srm.roundToInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            stringResource(R.string.est_color_morey_label),
                            fontSize = 11.sp,
                            color = c.muted,
                        )
                    }
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
                    cost.water?.takeIf { it > 0 }?.let {
                        stringResource(
                            if (cost.waterCoolingL > 0) R.string.est_cost_water_detail else R.string.est_cost_water_brew_only,
                            estFmt(cost.waterBrewL, 1), estFmt(cost.waterCoolingL, 0),
                        ) to it
                    },
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

/** Couleurs du site (styles.html) : ambre, vert, rouge et gris selon le thème. */
private data class EstColors(
    val dark: Boolean,
    val amber: Color,
    val success: Color,
    val danger: Color,
    val muted: Color,
    val track: Color,
)

@Composable
private fun estColors(): EstColors {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return if (dark) EstColors(
        dark = true,
        amber = Color(0xFFFF9500), success = Color(0xFF22C55E), danger = Color(0xFFEF4444),
        muted = Color(0xFF888888), track = Color.White.copy(alpha = 0.07f),
    ) else EstColors(
        dark = false,
        amber = Color(0xFFC2710C), success = Color(0xFF16A34A), danger = Color(0xFFDC2626),
        muted = Color(0xFF78716C), track = Color.Black.copy(alpha = 0.08f),
    )
}

/**
 * Jauge façon site : piste pleine largeur, plage du style BJCP en bande ambrée,
 * trait vertical = valeur estimée (vert dans la plage, rouge en dehors, ambre
 * sans style). Libellé, piste, valeur et cible sur une seule ligne.
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
    val c = estColors()
    val hasBjcp = bjcpMin != null && bjcpMax != null
    val inRange = hasBjcp && value != null && value >= bjcpMin!! && value <= bjcpMax!!
    val valueColor = when {
        value == null -> c.muted
        !hasBjcp -> c.amber
        inRange -> c.success
        else -> c.danger
    }
    val bandColor = c.amber.copy(alpha = 0.3f)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = c.muted,
            modifier = Modifier.width(34.dp),
        )
        Canvas(
            Modifier
                .weight(1f)
                .height(14.dp),
        ) {
            val span = (cfgMax - cfgMin).toFloat()
            fun xOf(v: Double): Float =
                (((v - cfgMin) / span).toFloat().coerceIn(0f, 1f)) * size.width
            val trackH = 8.dp.toPx()
            val top = (size.height - trackH) / 2
            val radius = CornerRadius(4.dp.toPx())
            drawRoundRect(c.track, topLeft = Offset(0f, top), size = Size(size.width, trackH), cornerRadius = radius)
            if (hasBjcp) {
                val left = xOf(bjcpMin!!)
                drawRoundRect(
                    bandColor,
                    topLeft = Offset(left, top),
                    size = Size((xOf(bjcpMax!!) - left).coerceAtLeast(2.dp.toPx()), trackH),
                    cornerRadius = radius,
                )
            }
            if (value != null) {
                val w = 3.dp.toPx()
                val x = xOf(value).coerceIn(w / 2, size.width - w / 2)
                drawRoundRect(
                    valueColor,
                    topLeft = Offset(x - w / 2, 0f),
                    size = Size(w, size.height),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            if (value != null) estFmt(value, dec) + unit else "–",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.widthIn(min = 58.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            if (hasBjcp) "⌖ ${estFmt(bjcpMin!!, dec)}–${estFmt(bjcpMax!!, dec)}${unit.trim()}" else "",
            fontSize = 11.sp,
            color = c.muted,
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
            // Largeur minimale commune : les pistes restent alignées d'une ligne
            // à l'autre, sans tronquer la cible avec une grande taille de police.
            modifier = Modifier.widthIn(min = 88.dp),
        )
    }
}
