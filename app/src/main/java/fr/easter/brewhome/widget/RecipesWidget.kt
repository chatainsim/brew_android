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
import androidx.glance.ImageProvider
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
import fr.easter.brewhome.R
import fr.easter.brewhome.data.Recipe
import fr.easter.brewhome.data.SnapshotCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Widget écran d'accueil : accès rapide aux recettes les mieux notées/récentes. */
class RecipesWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val rows = withContext(Dispatchers.IO) { computeRecipesData(context) }
        provideContent { WidgetContent(context, rows) }
    }

    @Composable
    private fun WidgetContent(context: Context, rows: List<RecipeRow>) {
        val amber = ColorProvider(Color(0xFFB86E00))
        Column(
            GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .padding(vertical = 10.dp, horizontal = 14.dp),
        ) {
            Text(
                "📖 Recettes",
                style = TextStyle(color = amber, fontWeight = FontWeight.Bold, fontSize = 21.sp),
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionStartActivity(openRoute(context, "fr.easter.brewhome.SHORTCUT_RECIPES"))),
            )
            if (rows.isEmpty()) {
                Text(
                    "Aucune recette",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 17.sp),
                    modifier = GlanceModifier.padding(top = 10.dp),
                )
            } else {
                LazyColumn(GlanceModifier.fillMaxSize().padding(top = 6.dp)) {
                    items(rows, itemId = { it.id.toLong() }) { row -> RecipeRowContent(context, row) }
                }
            }
        }
    }

    @Composable
    private fun RecipeRowContent(context: Context, row: RecipeRow) {
        Column(
            GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .background(ImageProvider(R.drawable.widget_row_bg))
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .clickable(
                    actionStartActivity(
                        openRoute(context, "fr.easter.brewhome.OPEN_RECIPE").putExtra("id", row.id),
                    ),
                ),
        ) {
            Text(
                row.name,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 19.sp),
            )
            Text(
                row.subtitle,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 15.sp),
                modifier = GlanceModifier.padding(top = 2.dp),
            )
        }
    }
}

class RecipesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RecipesWidget()
}

private fun openRoute(context: Context, action: String): Intent =
    Intent(context, MainActivity::class.java).setAction(action)

private data class RecipeRow(val id: Int, val name: String, val subtitle: String)

private const val MAX_RECIPES = 6

/** Meilleures notes d'abord (non notées en dernier), puis les plus récemment créées (id décroissant). */
private fun computeRecipesData(context: Context): List<RecipeRow> {
    val cached = SnapshotCache(context.filesDir).load() ?: return emptyList()
    return cached.snapshot.recipes
        .sortedWith(compareByDescending<Recipe> { it.rating ?: -1 }.thenByDescending { it.id })
        .take(MAX_RECIPES)
        .map { r -> RecipeRow(r.id, r.name, subtitleOf(r)) }
}

private fun subtitleOf(r: Recipe): String {
    val stars = r.rating?.let { "★".repeat(it.coerceIn(0, 5)) }
    return listOfNotNull(r.style, stars).joinToString(" · ").ifEmpty { "—" }
}
