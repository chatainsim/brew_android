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
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
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
import fr.easter.brewhome.data.ApiClient
import fr.easter.brewhome.data.SettingsRepository
import fr.easter.brewhome.data.ShoppingItem
import fr.easter.brewhome.data.ShoppingRepository
import fr.easter.brewhome.data.SnapshotCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Widget écran d'accueil : liste de courses, cochable directement sans ouvrir l'app. */
class ShoppingWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = withContext(Dispatchers.IO) { computeShoppingData(context) }
        provideContent { WidgetContent(context, data) }
    }

    @Composable
    private fun WidgetContent(context: Context, data: ShoppingData) {
        val amber = ColorProvider(Color(0xFFB86E00))
        Column(
            GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .padding(vertical = 10.dp, horizontal = 14.dp),
        ) {
            Text(
                "Liste de courses",
                style = TextStyle(color = amber, fontWeight = FontWeight.Bold, fontSize = 15.sp),
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionStartActivity(openRoute(context, "fr.easter.brewhome.SHORTCUT_SHOPPING"))),
            )
            if (data.rows.isEmpty()) {
                Text(
                    "Liste de courses vide 🎉",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                    modifier = GlanceModifier.padding(top = 8.dp),
                )
            } else {
                LazyColumn(GlanceModifier.fillMaxSize().padding(top = 4.dp)) {
                    items(data.rows, itemId = { it.id.toLong() }) { row -> ShoppingRowContent(row) }
                    if (data.moreCount > 0) {
                        item {
                            Text(
                                "+${data.moreCount} autres",
                                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                                modifier = GlanceModifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ShoppingRowContent(row: ShoppingRow) {
        CheckBox(
            checked = false,
            onCheckedChange = actionRunCallback<ToggleShoppingItemAction>(
                actionParametersOf(ToggleShoppingItemAction.itemIdKey to row.id),
            ),
            text = row.label,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp),
            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
        )
    }
}

class ShoppingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ShoppingWidget()
}

/** Coche un article depuis le widget, sans ouvrir l'app. Appel réseau direct (pas de file d'attente hors ligne, comme BrewViewModel.toggleShoppingChecked). */
class ToggleShoppingItemAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[itemIdKey] ?: return
        val url = SettingsRepository(context).serverUrl.first()
        val ok = runCatching {
            ShoppingRepository { ApiClient.api(url) }.setChecked(id, true)
        }.isSuccess
        if (ok) {
            val cache = SnapshotCache(context.filesDir)
            cache.load()?.let { cached ->
                cache.save(
                    cached.snapshot.copy(
                        shopping = cached.snapshot.shopping.map {
                            if (it.id == id) it.copy(checked = 1) else it
                        },
                    ),
                )
            }
        }
        ShoppingWidget().update(context, glanceId)
    }

    companion object {
        val itemIdKey = ActionParameters.Key<Int>("item_id")
    }
}

private fun openRoute(context: Context, action: String): Intent =
    Intent(context, MainActivity::class.java).setAction(action)

private data class ShoppingRow(val id: Int, val label: String)
private data class ShoppingData(val rows: List<ShoppingRow>, val moreCount: Int)

private const val MAX_SHOPPING_ROWS = 8

private fun computeShoppingData(context: Context): ShoppingData {
    val cached = SnapshotCache(context.filesDir).load() ?: return ShoppingData(emptyList(), 0)
    val pending = cached.snapshot.shopping.filter { (it.checked ?: 0) == 0 }
    val rows = pending.take(MAX_SHOPPING_ROWS).map { ShoppingRow(it.id, labelOf(it)) }
    return ShoppingData(rows, (pending.size - rows.size).coerceAtLeast(0))
}

private fun labelOf(item: ShoppingItem): String {
    val qty = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else item.quantity.toString()
    return "${item.name} — $qty ${item.unit}"
}
