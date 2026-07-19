package fr.easter.brewhome.ui

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.easter.brewhome.BrewViewModel
import fr.easter.brewhome.R
import java.util.Locale

/** Ouvre la feuille de partage Android (mail, Telegram, WhatsApp…) avec un texte. */
fun shareText(context: Context, text: String, subject: String? = null) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        if (subject != null) putExtra(Intent.EXTRA_SUBJECT, subject)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, null)) }
}

/** Epoch ms → « 16/07 à 22:40 » dans le fuseau du téléphone. */
fun fmtInstant(epochMs: Long): String = java.time.Instant.ofEpochMilli(epochMs)
    .atZone(java.time.ZoneId.systemDefault())
    .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM 'à' HH:mm"))

fun fmtQty(q: Double?): String {
    if (q == null) return "–"
    return if (q % 1.0 == 0.0) q.toInt().toString()
    else String.format(Locale.FRANCE, "%.2f", q).trimEnd('0').trimEnd(',')
}

fun categoryLabel(cat: String): String = when (cat.lowercase()) {
    "malt" -> "Malts"
    "houblon" -> "Houblons"
    "levure" -> "Levures"
    "autre" -> "Autres"
    else -> cat.replaceFirstChar { it.uppercase() }
}

val categoryOrder = listOf("malt", "houblon", "levure", "autre")

fun brewStatusLabel(status: String?): String = when (status) {
    "planned" -> "Planifié"
    "in_progress" -> "En cours"
    "fermenting" -> "Fermentation"
    "completed" -> "Terminé"
    else -> status ?: "?"
}

/** Couleurs (fond, texte) de la pastille de statut d'un brassin. */
@Composable
fun brewStatusColors(status: String?): Pair<Color, Color> = MaterialTheme.colorScheme.let {
    when (status) {
        "planned" -> it.surfaceVariant to it.onSurfaceVariant
        "in_progress" -> it.primaryContainer to it.onPrimaryContainer
        "fermenting" -> it.tertiaryContainer to it.onTertiaryContainer
        else -> it.secondaryContainer to it.onSecondaryContainer
    }
}

/** Couleur d'accent d'une catégorie d'ingrédients (malt, houblon, levure…). */
@Composable
fun categoryColor(cat: String): Color = when (cat.lowercase()) {
    "malt" -> MaterialTheme.colorScheme.primary
    "houblon" -> MaterialTheme.colorScheme.tertiary
    "levure" -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.outline
}

/** Petite pastille remplie et arrondie : statut d'un brassin ou d'un brouillon. */
@Composable
fun StatusChip(label: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(8.dp), color = container, modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

// Nuancier SRM 1..40 (référence brassicole standard), du blond paille au noir.
private val SrmColors = intArrayOf(
    0xFFE699, 0xFFD878, 0xFFCA5A, 0xFFBF42, 0xFBB123, 0xF8A600, 0xF39C00, 0xEA8F00,
    0xE58500, 0xDE7C00, 0xD77200, 0xCF6900, 0xCB6200, 0xC35900, 0xBB5100, 0xB54C00,
    0xB04500, 0xA63E00, 0xA13700, 0x9B3200, 0x952D00, 0x8E2900, 0x882300, 0x821E00,
    0x7B1A00, 0x771900, 0x701400, 0x6A0E00, 0x660D00, 0x5E0B00, 0x5A0A02, 0x560A05,
    0x520907, 0x4C0505, 0x470606, 0x440607, 0x3F0708, 0x3B0607, 0x3A070B, 0x36080A,
)

/** Couleur réelle d'un malt / d'une bière d'après sa valeur EBC. */
fun ebcColor(ebc: Double): Color {
    val srm = (ebc / 1.97).toInt().coerceIn(1, SrmColors.size)
    return Color(0xFF000000.toInt() or SrmColors[srm - 1])
}

/** Rond de couleur EBC : montre la teinte du malt d'un coup d'œil. */
@Composable
fun EbcDot(ebc: Double, size: Dp = 12.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(ebcColor(ebc))
            .border(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant, CircleShape),
    )
}

/**
 * Nombre animé façon odomètre : la nouvelle valeur pousse l'ancienne vers le
 * haut quand elle augmente, vers le bas quand elle diminue.
 */
@Composable
fun AnimatedNumber(
    value: Double,
    format: (Double) -> String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
) {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            val up = targetState > initialState
            val enter = slideInVertically { h -> if (up) h else -h } + fadeIn()
            val exit = slideOutVertically { h -> if (up) -h else h } + fadeOut()
            (enter togetherWith exit).using(SizeTransform(clip = true))
        },
        label = "number",
        modifier = modifier,
    ) { v ->
        Text(format(v), style = style, color = color, fontWeight = fontWeight)
    }
}

/**
 * Enveloppe commune des onglets de données : spinner au premier chargement,
 * écran d'erreur avec bouton « Réessayer » si la connexion échoue avant le
 * premier chargement, puis contenu avec tirer-pour-rafraîchir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshableContent(vm: BrewViewModel, content: @Composable () -> Unit) {
    val state by vm.state.collectAsState()
    when {
        !state.loaded && state.error != null -> ErrorRetry(state.error!!) { vm.refreshAll() }
        !state.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        else -> Column(Modifier.fillMaxSize()) {
            if (state.offline) OfflineBanner(state.dataAt)
            PullToRefreshBox(
                isRefreshing = state.loading,
                onRefresh = { vm.refreshAll() },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                content()
            }
        }
    }
}

/** Bandeau affiché quand le serveur est injoignable : données du cache disque. */
@Composable
private fun OfflineBanner(dataAt: Long?) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        val date = dataAt?.let { fmtInstant(it) }
        Text(
            if (date != null) stringResource(R.string.offline_banner_dated, date)
            else stringResource(R.string.offline_banner),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
fun ErrorRetry(message: String, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        Button(onClick = onRetry) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Text(stringResource(R.string.retry), Modifier.padding(start = 8.dp))
        }
    }
}

/** Champ de recherche compact utilisé en tête des listes. */
@Composable
fun SearchField(query: String, onChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_clear_search))
                }
            }
        },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
fun StarRating(rating: Int?, max: Int = 5, onSelect: ((Int) -> Unit)? = null) {
    Row {
        for (i in 1..max) {
            val filled = rating != null && i <= rating
            // Rebond élastique quand une étoile s'allume ou s'éteint
            val scale by animateFloatAsState(
                targetValue = if (filled) 1f else 0.82f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "starScale",
            )
            val tint by animateColorAsState(
                if (filled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                label = "starTint",
            )
            Icon(
                imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = stringResource(R.string.cd_stars, i),
                tint = tint,
                modifier = (if (onSelect != null) Modifier.clickable { onSelect(i) } else Modifier)
                    .scale(scale),
            )
        }
    }
}
