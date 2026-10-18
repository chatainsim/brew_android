package fr.easter.brewhome.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import fr.easter.brewhome.MainActivity
import fr.easter.brewhome.R
import fr.easter.brewhome.data.ACTIVE_BREW_STATUSES
import fr.easter.brewhome.data.Brew
import fr.easter.brewhome.data.LastReading
import fr.easter.brewhome.data.SnapshotCache
import fr.easter.brewhome.ui.brewStatusLabel
import fr.easter.brewhome.ui.fmtQty
import fr.easter.brewhome.ui.readingLocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Widget écran d'accueil : liste des brassins actuellement en cours (en cuve/en fermentation). */
class ActiveBrewsWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val rows = withContext(Dispatchers.IO) { computeActiveBrewsData(context) }
        provideContent { WidgetContent(context, rows) }
    }

    @Composable
    private fun WidgetContent(context: Context, rows: List<BrewRow>) {
        val amber = ColorProvider(Color(0xFFB86E00))
        Column(
            GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .padding(vertical = 10.dp, horizontal = 14.dp),
        ) {
            Text(
                "🧪 Brassins en cours",
                style = TextStyle(color = amber, fontWeight = FontWeight.Bold, fontSize = 22.sp),
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionStartActivity(openRoute(context, "fr.easter.brewhome.SHORTCUT_BREWS"))),
            )
            if (rows.isEmpty()) {
                Text(
                    "Aucun brassin en cours",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 18.sp),
                    modifier = GlanceModifier.padding(top = 10.dp),
                )
            } else {
                LazyColumn(GlanceModifier.fillMaxSize().padding(top = 8.dp)) {
                    items(rows, itemId = { it.id.toLong() }) { row -> BrewRowContent(context, row) }
                }
            }
        }
    }

    @Composable
    private fun BrewRowContent(context: Context, row: BrewRow) {
        Row(
            GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 5.dp)
                .background(ImageProvider(R.drawable.widget_row_bg))
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .clickable(
                    actionStartActivity(
                        openRoute(context, "fr.easter.brewhome.OPEN_BREW").putExtra("id", row.id),
                    ),
                ),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Column(GlanceModifier.defaultWeight()) {
                Text(
                    row.name,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 22.sp),
                )
                Text(
                    row.dayLabel,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 18.sp),
                    modifier = GlanceModifier.padding(top = 4.dp),
                )
                row.reading?.let {
                    Text(
                        it,
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 19.sp),
                        modifier = GlanceModifier.padding(top = 4.dp),
                    )
                }
            }
            Text(
                row.statusLabel,
                style = TextStyle(color = ColorProvider(Color.White), fontWeight = FontWeight.Bold, fontSize = 16.sp),
                modifier = GlanceModifier
                    .background(ImageProvider(R.drawable.widget_chip_bg), colorFilter = ColorFilter.tint(ColorProvider(statusChipColor(row.status))))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

class ActiveBrewsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ActiveBrewsWidget()
}

private fun openRoute(context: Context, action: String): Intent =
    Intent(context, MainActivity::class.java).setAction(action)

/** Couleur de la pastille de statut (teinte appliquée à widget_chip_bg via ColorFilter). */
private fun statusChipColor(status: String): Color = when (status) {
    "fermenting" -> Color(0xFF2E7D5B)
    else -> Color(0xFFB86E00)
}

private data class BrewRow(
    val id: Int,
    val name: String,
    val status: String,
    val statusLabel: String,
    val dayLabel: String,
    /** « 🧪 1.012 · 🌡️ 19,5 °C · 12/10 16:30 », null sans mesure. */
    val reading: String?,
)

private fun computeActiveBrewsData(context: Context): List<BrewRow> {
    val cached = SnapshotCache(context.filesDir).load() ?: return emptyList()
    val today = LocalDate.now()
    return cached.snapshot.brews
        .filter { (it.archived ?: 0) == 0 && it.status in ACTIVE_BREW_STATUSES }
        .sortedBy { it.brewDate ?: "" }
        .map { b ->
            BrewRow(
                b.id, b.name, b.status ?: "", brewStatusLabel(b.status), dayLabelOf(b, today),
                cached.snapshot.lastReadings[b.id]?.let(::readingLabel),
            )
        }
}

internal fun readingLabel(r: LastReading): String? = listOfNotNull(
    r.gravity?.let { "🧪 " + if (it >= 2.0) fmtQty(it) else String.format(java.util.Locale.US, "%.3f", it) },
    r.temperature?.let { "🌡️ ${fmtQty(it)} °C" },
    r.recordedAt?.let(::readingTime),
).joinToString(" · ").ifEmpty { null }

/** « 12/10 16:30 » : relevé stocké en UTC par le serveur, affiché à l'heure du téléphone. */
private fun readingTime(ts: String): String? = readingLocalDateTime(ts)
    ?.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm"))

private fun dayLabelOf(b: Brew, today: LocalDate): String {
    val brewDate = b.brewDate?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: return "?"
    val days = ChronoUnit.DAYS.between(brewDate, today)
    return "J+$days"
}
