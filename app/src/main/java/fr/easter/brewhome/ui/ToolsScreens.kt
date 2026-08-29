package fr.easter.brewhome.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Biotech
import androidx.compose.material.icons.outlined.Bloodtype
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.BubbleChart
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Liquor
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Sports
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.easter.brewhome.R

// Liste et routage des outils ; chaque calculateur vit dans son fichier Tool*.kt
// et leurs briques communes dans ToolWidgets.kt.

data class ToolDef(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val icon: ImageVector,
)

val toolDefs = listOf(
    ToolDef("abv", R.string.tool_abv_title, R.string.tool_abv_sub, Icons.Outlined.Percent),
    ToolDef("hydro", R.string.tool_hydro_title, R.string.tool_hydro_sub, Icons.Outlined.Thermostat),
    ToolDef("refracto", R.string.tool_refracto_title, R.string.tool_refracto_sub, Icons.Outlined.Colorize),
    ToolDef("strike", R.string.tool_strike_title, R.string.tool_strike_sub, Icons.Outlined.LocalFireDepartment),
    ToolDef("bottles", R.string.tool_bottles_title, R.string.tool_bottles_sub, Icons.Outlined.Liquor),
    ToolDef("priming", R.string.tool_priming_title, R.string.tool_priming_sub, Icons.Outlined.BubbleChart),
    ToolDef("starter", R.string.tool_starter_title, R.string.tool_starter_sub, Icons.Outlined.Biotech),
)

@Composable
fun toolTitle(id: String?): String =
    toolDefs.find { it.id == id }?.let { stringResource(it.titleRes) }
        ?: stringResource(R.string.tab_tools)

/** Couleurs (fond, texte) de la pastille d'icône, en alternance par position. */
@Composable
private fun toolAccent(index: Int): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> =
    MaterialTheme.colorScheme.let {
        when (index % 3) {
            0 -> it.primaryContainer to it.onPrimaryContainer
            1 -> it.tertiaryContainer to it.onTertiaryContainer
            else -> it.secondaryContainer to it.onSecondaryContainer
        }
    }

@Composable
fun ToolsScreen(
    onOpenStats: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenSpindles: () -> Unit,
    onOpenTemps: () -> Unit,
    onOpenKegs: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenActivity: () -> Unit,
    onOpen: (String) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "stats") {
            ToolCard(
                stringResource(R.string.title_stats),
                stringResource(R.string.tools_stats_sub),
                Icons.Outlined.BarChart,
                accent = toolAccent(0),
                onClick = onOpenStats,
            )
        }
        item(key = "calendar") {
            ToolCard(
                stringResource(R.string.title_calendar),
                stringResource(R.string.tools_calendar_sub),
                Icons.Outlined.CalendarMonth,
                accent = toolAccent(1),
                onClick = onOpenCalendar,
            )
        }
        item(key = "spindles") {
            ToolCard(
                stringResource(R.string.title_spindles),
                stringResource(R.string.tools_spindles_sub),
                Icons.Outlined.Bloodtype,
                accent = toolAccent(2),
                onClick = onOpenSpindles,
            )
        }
        item(key = "temps") {
            ToolCard(
                stringResource(R.string.title_temps),
                stringResource(R.string.tools_temps_sub),
                Icons.Outlined.Thermostat,
                accent = toolAccent(3),
                onClick = onOpenTemps,
            )
        }
        item(key = "kegs") {
            ToolCard(
                stringResource(R.string.title_kegs),
                stringResource(R.string.tools_kegs_sub),
                Icons.Outlined.Sports,
                accent = toolAccent(4),
                onClick = onOpenKegs,
            )
        }
        item(key = "trash") {
            ToolCard(
                stringResource(R.string.title_trash),
                stringResource(R.string.tools_trash_sub),
                Icons.Outlined.DeleteOutline,
                accent = toolAccent(5),
                onClick = onOpenTrash,
            )
        }
        item(key = "catalog") {
            ToolCard(
                stringResource(R.string.title_catalog),
                stringResource(R.string.tools_catalog_sub),
                Icons.Outlined.MenuBook,
                accent = toolAccent(6),
                onClick = onOpenCatalog,
            )
        }
        item(key = "activity") {
            ToolCard(
                stringResource(R.string.title_activity),
                stringResource(R.string.tools_activity_sub),
                Icons.Outlined.History,
                accent = toolAccent(7),
                onClick = onOpenActivity,
            )
        }
        itemsIndexed(toolDefs, key = { _, tool -> tool.id }) { i, tool ->
            ToolCard(
                stringResource(tool.titleRes),
                stringResource(tool.subtitleRes),
                tool.icon,
                accent = toolAccent(i + 8),
            ) { onOpen(tool.id) }
        }
    }
}

@Composable
private fun ToolCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color>,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = accent.first,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = accent.second,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
fun ToolScreen(toolId: String?) {
    when (toolId) {
        "abv" -> AbvCalcScreen()
        "hydro" -> HydroCalcScreen()
        "refracto" -> RefractoCalcScreen()
        "strike" -> StrikeCalcScreen()
        "bottles" -> BottlesCalcScreen()
        "priming" -> PrimingCalcScreen()
        "starter" -> StarterCalcScreen()
        else -> EmptyHint(stringResource(R.string.tool_not_found))
    }
}
