package fr.easter.brewhome.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
