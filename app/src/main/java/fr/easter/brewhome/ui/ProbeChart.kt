package fr.easter.brewhome.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale

/** Plages proposées sous les courbes ; null = tout l'historique. */
val probeRanges: List<Int?> = listOf(24, 48, 72, null)

/** Sélecteur de plage 24 h / 48 h / 72 h / Tout au-dessus d'un graphe. */
@Composable
fun RangeSelector(selected: Int?, onSelect: (Int?) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        probeRanges.forEachIndexed { i, hours ->
            SegmentedButton(
                selected = selected == hours,
                onClick = { onSelect(hours) },
                shape = SegmentedButtonDefaults.itemShape(i, probeRanges.size),
            ) {
                Text(hours?.let { "$it h" } ?: stringResource(fr.easter.brewhome.R.string.range_all))
            }
        }
    }
}

/** Une série de la courbe : valeurs alignées sur les horodatages, avec sa couleur. */
data class ChartSeries(
    val label: String,
    val values: List<Double?>,
    val color: Color,
    val unit: String,
    val decimals: Int,
)

private fun fmtSeries(v: Double, decimals: Int): String =
    if (decimals == 0) v.toInt().toString()
    else String.format(Locale.FRANCE, "%.${decimals}f", v)

/**
 * Graphe temporel multi-séries : X proportionnel au temps réel (timeFractions),
 * chaque série mise à l'échelle sur ses propres bornes pour remplir la hauteur.
 * La première série reçoit un aplat dégradé. Légende min–max sous le graphe.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProbeChart(
    timestamps: List<String>,
    series: List<ChartSeries>,
    height: Dp = 200.dp,
) {
    val xs = timeFractions(timestamps)
    Column {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height),
        ) {
            series.forEachIndexed { i, s ->
                val pts = s.values.mapIndexedNotNull { idx, v -> v?.let { xs[idx] to it } }
                drawSeries(pts, s.color, fill = i == 0)
            }
        }
        TimeAxis(timestamps)
        FlowRow(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            series.forEach { s ->
                val vals = s.values.filterNotNull()
                if (vals.isNotEmpty()) {
                    Text(
                        "${s.label} : ${fmtSeries(vals.min(), s.decimals)}–${fmtSeries(vals.max(), s.decimals)} ${s.unit}".trim(),
                        style = MaterialTheme.typography.labelSmall,
                        color = s.color,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/** Labels temporels sous le graphe : 4 repères espacés régulièrement en temps. */
@Composable
private fun TimeAxis(timestamps: List<String>) {
    val times = timestamps.map { parseFermTimestamp(it) }
    val tMin = times.filterNotNull().minOrNull()
    val tMax = times.filterNotNull().maxOrNull()
    if (tMin == null || tMax == null || tMax <= tMin) return
    val spanSec = tMax - tMin
    // "HH:mm" si la plage tient sur ~1,5 jour, sinon "dd/MM HH:mm"
    val pattern = if (spanSec <= 36 * 3600) "HH:mm" else "dd/MM HH:mm"
    val fmt = java.time.format.DateTimeFormatter.ofPattern(pattern)
    fun label(sec: Long): String = java.time.Instant.ofEpochSecond(sec)
        .atZone(java.time.ZoneOffset.UTC).format(fmt)

    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        for (k in 0..3) {
            Text(
                label(tMin + spanSec * k / 3),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

private fun DrawScope.drawSeries(points: List<Pair<Float, Double>>, color: Color, fill: Boolean) {
    if (points.size < 2) return
    val min = points.minOf { it.second }
    val max = points.maxOf { it.second }
    val range = (max - min).takeIf { it > 1e-9 } ?: 1.0
    fun y(v: Double) = size.height * (0.06f + 0.88f * (1f - ((v - min) / range).toFloat()))
    val path = Path()
    points.forEachIndexed { i, (xFrac, v) ->
        val x = size.width * xFrac
        if (i == 0) path.moveTo(x, y(v)) else path.lineTo(x, y(v))
    }
    if (fill) {
        val area = Path().apply {
            addPath(path)
            lineTo(size.width * points.last().first, size.height)
            lineTo(size.width * points.first().first, size.height)
            close()
        }
        drawPath(
            area,
            brush = Brush.verticalGradient(
                listOf(color.copy(alpha = 0.25f), color.copy(alpha = 0f)),
                endY = size.height,
            ),
        )
    }
    drawPath(path, color = color, style = Stroke(width = 5f, cap = StrokeCap.Round))
}
