package fr.easter.brewhome.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import fr.easter.brewhome.MainActivity
import fr.easter.brewhome.data.Brew
import fr.easter.brewhome.data.SnapshotCache
import fr.easter.brewhome.ui.brewStatusLabel
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
                "Brassins en cours",
                style = TextStyle(color = amber, fontWeight = FontWeight.Bold, fontSize = 15.sp),
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionStartActivity(openRoute(context, "fr.easter.brewhome.SHORTCUT_BREWS"))),
            )
            if (rows.isEmpty()) {
                Text(
                    "Aucun brassin en cours",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                    modifier = GlanceModifier.padding(top = 8.dp),
                )
            } else {
                LazyColumn(GlanceModifier.fillMaxSize().padding(top = 4.dp)) {
                    items(rows, itemId = { it.id.toLong() }) { row -> BrewRowContent(context, row) }
                }
            }
        }
    }

    @Composable
    private fun BrewRowContent(context: Context, row: BrewRow) {
        Column(
            GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clickable(
                    actionStartActivity(
                        openRoute(context, "fr.easter.brewhome.OPEN_BREW").putExtra("id", row.id),
                    ),
                ),
        ) {
            Text(
                row.name,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp),
            )
            Text(
                "${row.statusLabel} · ${row.dayLabel}",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
        }
    }
}

class ActiveBrewsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ActiveBrewsWidget()
}

private fun openRoute(context: Context, action: String): Intent =
    Intent(context, MainActivity::class.java).setAction(action)

private data class BrewRow(val id: Int, val name: String, val statusLabel: String, val dayLabel: String)

private val activeStatuses = setOf("in_progress", "fermenting")

private fun computeActiveBrewsData(context: Context): List<BrewRow> {
    val cached = SnapshotCache(context.filesDir).load() ?: return emptyList()
    val today = LocalDate.now()
    return cached.snapshot.brews
        .filter { (it.archived ?: 0) == 0 && it.status in activeStatuses }
        .sortedBy { it.brewDate ?: "" }
        .map { b -> BrewRow(b.id, b.name, brewStatusLabel(b.status), dayLabelOf(b, today)) }
}

private fun dayLabelOf(b: Brew, today: LocalDate): String {
    val brewDate = b.brewDate?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: return "?"
    val days = ChronoUnit.DAYS.between(brewDate, today)
    return "J+$days"
}
