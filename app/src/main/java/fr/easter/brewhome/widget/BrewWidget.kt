package fr.easter.brewhome.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.LocalContext
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.easter.brewhome.MainActivity
import fr.easter.brewhome.calc.CalendarEvents
import fr.easter.brewhome.data.SnapshotCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Widget écran d'accueil : total de la cave et prochaine échéance de brassage. */
class BrewWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = withContext(Dispatchers.IO) { computeWidgetData(context) }
        provideContent { WidgetContent(data) }
    }

    @Composable
    private fun WidgetContent(data: WidgetData) {
        val amber = ColorProvider(Color(0xFFB86E00))
        val ctx = LocalContext.current
        Box(
            GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .clickable(actionStartActivity(Intent(ctx, MainActivity::class.java))),
            contentAlignment = Alignment.Center,
        ) {
            Row(GlanceModifier.padding(14.dp), verticalAlignment = Alignment.Vertical.CenterVertically) {
                Text("🍺", style = TextStyle(fontSize = 48.sp))
                Column(GlanceModifier.padding(start = 12.dp)) {
                    Text(
                        data.caveLiters,
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 42.sp),
                    )
                    Text(
                        "en cave",
                        style = TextStyle(color = amber, fontWeight = FontWeight.Bold, fontSize = 19.sp),
                    )
                    Text(
                        data.next,
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 17.sp),
                        modifier = GlanceModifier.padding(top = 5.dp),
                    )
                }
            }
        }
    }
}

class BrewWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BrewWidget()
}

private data class WidgetData(val caveLiters: String, val next: String)

private fun computeWidgetData(context: Context): WidgetData {
    val cached = SnapshotCache(context.filesDir).load()
        ?: return WidgetData("—", "Ouvre l'app pour synchroniser")
    val snap = cached.snapshot
    val beers = snap.beers.filter { (it.archived ?: 0) == 0 }
    val n33 = beers.sumOf { it.stock33 ?: 0 }
    val n75 = beers.sumOf { it.stock75 ?: 0 }
    val keg = beers.sumOf { it.kegLiters ?: 0.0 }
    val caveL = n33 * 0.33 + n75 * 0.75 + keg
    val cave = "${(kotlin.math.round(caveL * 10) / 10)} L"

    val today = LocalDate.now()
    val ev = CalendarEvents.agenda(
        from = today, to = today.plusDays(90),
        brews = snap.brews,
        recipes = snap.recipes.associateBy { it.id },
        beers = snap.beers,
        drafts = snap.drafts,
        customEvents = emptyList(),
    ).firstOrNull { it.type != CalendarEvents.Type.WORLD }

    val next = if (ev == null) "Aucune échéance proche" else {
        val days = ChronoUnit.DAYS.between(today, ev.date)
        val whenTxt = when {
            days <= 0L -> "aujourd'hui"
            days == 1L -> "demain"
            else -> "dans $days j"
        }
        "${ev.emoji} ${ev.label.take(40)} · $whenTxt"
    }
    return WidgetData(cave, next)
}
