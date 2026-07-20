package fr.easter.brewhome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sports
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.easter.brewhome.BrewViewModel
import fr.easter.brewhome.R
import fr.easter.brewhome.data.SodaKeg

@Composable
private fun kegStatusColors(status: String): Pair<Color, Color> = MaterialTheme.colorScheme.let {
    when (status) {
        "serving" -> it.primaryContainer to it.onPrimaryContainer
        "fermenting" -> it.tertiaryContainer to it.onTertiaryContainer
        "cleaning" -> it.secondaryContainer to it.onSecondaryContainer
        else -> it.surfaceVariant to it.onSurfaceVariant
    }
}

@Composable
private fun kegStatusLabel(status: String): String = stringResource(
    when (status) {
        "serving" -> R.string.keg_status_serving
        "fermenting" -> R.string.keg_status_fermenting
        "cleaning" -> R.string.keg_status_cleaning
        else -> R.string.keg_status_empty
    },
)

/** Vue des fûts à soda : niveau, état de service, révisions. */
@Composable
fun KegsScreen(vm: BrewViewModel) {
    val kegs by vm.sodaKegs.collectAsState()
    LaunchedEffect(Unit) { vm.loadSodaKegs() }

    when {
        kegs == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        kegs!!.none { (it.archived ?: 0) == 0 } -> EmptyHint(stringResource(R.string.kegs_empty), Icons.Outlined.Sports)
        else -> LazyColumn(
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(kegs!!.filter { (it.archived ?: 0) == 0 }, key = { it.id }) { keg ->
                KegCard(keg)
            }
        }
    }
}

@Composable
private fun KegCard(keg: SodaKeg) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
            // Liseré de la couleur du fût
            val accent = keg.color?.let { hex ->
                runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
            } ?: MaterialTheme.colorScheme.primary
            Box(
                Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(accent),
            )
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        keg.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    val (container, content) = kegStatusColors(keg.status)
                    StatusChip(kegStatusLabel(keg.status), container, content)
                }
                val subtitle = listOfNotNull(
                    keg.beerName ?: keg.brewName,
                    keg.kegType,
                ).joinToString(" · ")
                if (subtitle.isNotEmpty()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                // Niveau de remplissage
                val current = keg.currentLiters ?: 0.0
                val total = keg.volumeTotal?.takeIf { it > 0 }
                if (total != null) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth((current / total).toFloat().coerceIn(0f, 1f))
                                .clip(RoundedCornerShape(7.dp))
                                .background(accent),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${fmtQty(current)} / ${fmtQty(total)} L",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                keg.nextRevisionDate?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(6.dp))
                    val overdue = it <= java.time.LocalDate.now().toString()
                    Text(
                        stringResource(R.string.keg_revision, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (overdue) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.outline,
                        fontWeight = if (overdue) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
