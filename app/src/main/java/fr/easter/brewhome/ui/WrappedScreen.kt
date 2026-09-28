package fr.easter.brewhome.ui

import android.content.Context
import android.graphics.Bitmap
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.easter.brewhome.BrewViewModel
import fr.easter.brewhome.BrewViewModel.WrappedState
import fr.easter.brewhome.R
import fr.easter.brewhome.data.Wrapped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val WrBg = Color(0xFF0C0A09)

/**
 * Bilan annuel « Wrapped » — mêmes cartes que sur le site (serveur ≥ 0.1.23).
 * Le bouton Partager capture la colonne des cartes (en-tête compris, sans les
 * contrôles) en PNG et ouvre la feuille de partage Android.
 */
@Composable
fun WrappedScreen(vm: BrewViewModel, initialYear: Int) {
    var year by rememberSaveable { mutableStateOf(initialYear) }
    val state by vm.wrapped.collectAsState()
    LaunchedEffect(year) { vm.loadWrapped(year) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    var sharing by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(WrBg)
            .verticalScroll(rememberScrollState()),
    ) {
        val ready = (state as? WrappedState.Ready)?.data
        // ── Contrôles (hors image) ──
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val years = ready?.years?.takeIf { it.isNotEmpty() } ?: listOf(year)
                years.forEach { y ->
                    FilterChip(
                        selected = y == year, onClick = { year = y }, label = { Text("$y") },
                        colors = FilterChipDefaults.filterChipColors(
                            labelColor = Color(0xFFE7E5E4),
                            selectedContainerColor = Color(0xFFD97706),
                            selectedLabelColor = Color.White,
                        ),
                    )
                }
            }
            if (ready != null && !ready.empty) {
                Button(
                    enabled = !sharing,
                    onClick = {
                        sharing = true
                        scope.launch {
                            runCatching {
                                val bmp = layer.toImageBitmap().asAndroidBitmap()
                                val uri = withContext(Dispatchers.IO) { writeWrappedPng(context, bmp, ready.year) }
                                openImageShareSheet(context, uri)
                            }
                            sharing = false
                        }
                    },
                ) {
                    Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.wrap_share))
                }
            }
        }

        when (val s = state) {
            WrappedState.Loading -> Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) {
                CircularProgressIndicator()
            }
            is WrappedState.Failed -> Text(
                stringResource(if (s.serverTooOld) R.string.wrap_server_too_old else R.string.wrap_err_load),
                color = Color(0xFFE7E5E4), modifier = Modifier.padding(24.dp),
            )
            is WrappedState.Ready -> {
                val w = s.data
                // ── Zone capturée pour l'image ──
                Column(
                    Modifier
                        .fillMaxWidth()
                        .drawWithContent {
                            layer.record { this@drawWithContent.drawContent() }
                            drawLayer(layer)
                        }
                        .background(WrBg)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "🍺 BrewHome · " + stringResource(R.string.wrap_title, w.year),
                        color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                    if (w.empty) {
                        Text(
                            stringResource(R.string.wrap_empty, w.year),
                            color = Color(0xFFA8A29E), textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                        )
                    } else {
                        WrappedCards(w)
                        if (w.inProgress && w.until != null) {
                            Text(
                                stringResource(R.string.wrap_in_progress, fmtLongDate(w.until)),
                                color = Color(0xFFA8A29E), fontSize = 12.sp,
                                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WrappedCards(w: Wrapped) {
    // Année
    Slide(listOf(Color(0xFFD97706), Color(0xFFB45309))) {
        Kicker(stringResource(R.string.wrap_k_year, w.year))
        Big("${fmt1(w.liters)} L")
        Sub(stringResource(R.string.wrap_liters_sub, w.brews, w.pints))
        w.evolutionPct?.let { pct ->
            Chip(
                (if (pct >= 0) "▲ " else "▼ ") + stringResource(
                    if (w.prevSameDate) R.string.wrap_evo_same_date else R.string.wrap_evo,
                    kotlin.math.abs(pct), w.prevYear ?: (w.year - 1), fmt1(w.prevLiters),
                )
            )
        }
        if (w.firstBrew != null && w.lastBrew != null) {
            Lbl(stringResource(
                R.string.wrap_first_last,
                w.firstBrew.name, fmtLongDate(w.firstBrew.date), w.lastBrew.name, fmtLongDate(w.lastBrew.date),
            ))
        }
    }

    // Rythme
    if (w.brews > 0) Slide(listOf(Color(0xFF2563EB), Color(0xFF0E7490))) {
        Kicker(stringResource(R.string.wrap_k_rhythm))
        Figures {
            w.bestMonth?.let { Figure(monthName(it), stringResource(R.string.wrap_best_month)) }
            w.favWeekday?.let { Figure(weekdayName(it), stringResource(R.string.wrap_fav_weekday)) }
            Figure("${w.monthsActive} / 12", stringResource(R.string.wrap_months_active))
            if (w.monthStreak > 1) Figure("🔥 ${w.monthStreak}", stringResource(R.string.wrap_month_streak))
        }
        MonthBars(w.byMonth)
    }

    // Style
    if (w.styles.isNotEmpty()) Slide(listOf(Color(0xFF15803D), Color(0xFF0E7490))) {
        Kicker(stringResource(R.string.wrap_k_style))
        Big(w.styles[0].name, 24)
        Sub(stringResource(R.string.wrap_style_sub, w.styles[0].count, w.nStyles))
        w.styles.drop(1).forEachIndexed { i, st -> ListLine(i + 2, st.name, "× ${st.count}") }
        w.topRecipe?.let { Sub("🎯 " + stringResource(R.string.wrap_top_recipe, it.name, it.count)) }
        if (w.newRecipes > 0) Lbl(stringResource(R.string.wrap_new_recipes, w.newRecipes))
    }

    // Ingrédients
    if (w.maltKg > 0 || w.hopsG > 0) Slide(listOf(Color(0xFF65A30D), Color(0xFF15803D))) {
        Kicker(stringResource(R.string.wrap_k_ingredients))
        Figures {
            Figure(
                "🌾 ${fmt1(w.maltKg)} kg",
                stringResource(R.string.wrap_malt) + (w.topMalt?.let { " · $it" } ?: ""),
            )
            Figure(
                "🌿 ${fmt0(w.hopsG)} g",
                stringResource(R.string.wrap_hops) +
                    (w.hopsPerLiter?.let { " · " + stringResource(R.string.wrap_per_liter, fmt1(it)) } ?: ""),
            )
        }
        w.topHops.firstOrNull()?.let { Sub(stringResource(R.string.wrap_top_hop, it.name, fmt0(it.grams))) }
        w.topYeast?.let { Lbl(stringResource(R.string.wrap_top_yeast, it)) }
    }

    // Chiffres
    if (w.avgAbv != null || w.avgEfficiency != null || w.cost != null) Slide(listOf(Color(0xFFB91C1C), Color(0xFF9D174D))) {
        Kicker(stringResource(R.string.wrap_k_numbers))
        Figures {
            w.avgAbv?.let { Figure("${fmt1(it)} %", stringResource(R.string.wrap_avg_abv)) }
            w.avgEfficiency?.let { Figure("${fmt1(it)} %", stringResource(R.string.wrap_avg_eff)) }
            w.cost?.let {
                Figure(
                    "${fmt0(it)} €",
                    stringResource(R.string.wrap_cost) + (w.costPerLiter?.let { c -> " · ${fmt2(c)} €/L" } ?: ""),
                )
            }
        }
        w.strongest?.let { Sub("💪 " + stringResource(R.string.wrap_strongest, it.name, fmt1(it.abv))) }
        if (w.readings > 0) Lbl("📈 " + stringResource(R.string.wrap_readings, w.readings))
    }

    // Cave
    if (w.bottledBeers > 0 || w.drunkLiters > 0) Slide(listOf(Color(0xFF7C3AED), Color(0xFF9D174D))) {
        Kicker(stringResource(R.string.wrap_k_cellar))
        Figures {
            if (w.bottledBeers > 0) Figure("🍾 ${w.bottles}", stringResource(R.string.wrap_bottled, fmt1(w.bottledLiters)))
            if (w.drunkLiters > 0) Figure("🍻 ${fmt1(w.drunkLiters)} L", stringResource(R.string.wrap_drunk))
        }
        w.favBeer?.let { Sub(stringResource(R.string.wrap_fav_beer, it.name, fmt1(it.liters))) }
        w.bestTasted?.let { Sub("⭐ " + stringResource(R.string.wrap_best_tasted, it.name, it.rating)) }
    }

    // Profil
    val (icon, name, text) = profileStrings(w.profile)
    Slide(listOf(Color(0xFF27272A), Color(0xFF09090B)), border = true) {
        Kicker(stringResource(R.string.wrap_k_profile, w.year))
        Big("$icon ${stringResource(name)}", 26)
        Sub(stringResource(text))
    }
}

private fun profileStrings(key: String): Triple<String, Int, Int> = when (key) {
    "brewery" -> Triple("🏭", R.string.wrap_p_brewery, R.string.wrap_p_brewery_txt)
    "hophead" -> Triple("🌿", R.string.wrap_p_hophead, R.string.wrap_p_hophead_txt)
    "perfection" -> Triple("🎯", R.string.wrap_p_perfection, R.string.wrap_p_perfection_txt)
    "explorer" -> Triple("🧭", R.string.wrap_p_explorer, R.string.wrap_p_explorer_txt)
    "strong" -> Triple("💪", R.string.wrap_p_strong, R.string.wrap_p_strong_txt)
    "regular" -> Triple("📅", R.string.wrap_p_regular, R.string.wrap_p_regular_txt)
    else -> Triple("🍺", R.string.wrap_p_passion, R.string.wrap_p_passion_txt)
}

// ── Briques visuelles ──────────────────────────────────────────────────────

@Composable
private fun Slide(colors: List<Color>, border: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(colors))
            .then(if (border) Modifier.border(1.dp, Color(0xFF3F3F46), shape) else Modifier)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
private fun Kicker(text: String) = Text(
    text.uppercase(Locale.FRENCH), color = Color.White.copy(alpha = .85f),
    fontSize = 11.sp, letterSpacing = 1.sp,
)

@Composable
private fun Big(text: String, size: Int = 32) = Text(
    text, color = Color.White, fontSize = size.sp, fontWeight = FontWeight.ExtraBold, lineHeight = (size * 1.15).sp,
)

@Composable
private fun Sub(text: String) = Text(text, color = Color.White.copy(alpha = .95f), fontSize = 14.sp)

@Composable
private fun Lbl(text: String) = Text(text, color = Color.White.copy(alpha = .85f), fontSize = 12.sp)

@Composable
private fun Chip(text: String) = Text(
    text, color = Color.White, fontSize = 12.sp,
    modifier = Modifier
        .padding(top = 6.dp)
        .clip(RoundedCornerShape(50))
        .background(Color.White.copy(alpha = .18f))
        .padding(horizontal = 10.dp, vertical = 3.dp),
)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Figures(content: @Composable () -> Unit) = FlowRow(
    Modifier.fillMaxWidth().padding(top = 6.dp),
    horizontalArrangement = Arrangement.spacedBy(22.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
) { content() }

@Composable
private fun Figure(value: String, label: String) = Column {
    Text(value, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
    Text(label, color = Color.White.copy(alpha = .85f), fontSize = 12.sp)
}

@Composable
private fun ListLine(rank: Int, name: String, value: String) = Row(
    Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
) {
    Text("$rank", color = Color.White.copy(alpha = .7f), fontSize = 14.sp, modifier = Modifier.width(22.dp))
    Text(name, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun MonthBars(values: List<Double>) {
    val peak = (values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    Row(
        Modifier.fillMaxWidth().height(96.dp).padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.take(12).forEachIndexed { i, v ->
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                // Espace vide au-dessus, barre en bas : hauteur proportionnelle au volume du mois
                if (v < peak) Spacer(Modifier.weight((1 - v / peak).toFloat().coerceAtLeast(0.001f)))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight((v / peak).toFloat().coerceAtLeast(0.02f), fill = true)
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(Color.White.copy(alpha = .85f))
                )
                Text(
                    Month.of(i + 1).getDisplayName(TextStyle.NARROW, Locale.FRENCH),
                    color = Color.White.copy(alpha = .85f), fontSize = 9.sp,
                )
            }
        }
    }
}

// ── Formats ────────────────────────────────────────────────────────────────

private fun fmtN(v: Double, digits: Int): String =
    java.text.NumberFormat.getNumberInstance(Locale.FRANCE).apply {
        maximumFractionDigits = digits
        minimumFractionDigits = 0
    }.format(v)

private fun fmt0(v: Double) = fmtN(v, 0)
private fun fmt1(v: Double) = fmtN(v, 1)
private fun fmt2(v: Double) = fmtN(v, 2)

private fun monthName(i: Int) =
    Month.of(i + 1).getDisplayName(TextStyle.FULL_STANDALONE, Locale.FRENCH).replaceFirstChar { it.titlecase(Locale.FRENCH) }

/** Lundi = 0, comme côté serveur. */
internal fun weekdayName(i: Int) =
    DayOfWeek.of(i + 1).getDisplayName(TextStyle.FULL, Locale.FRENCH).replaceFirstChar { it.titlecase(Locale.FRENCH) }

internal fun fmtLongDate(iso: String): String = runCatching {
    LocalDate.parse(iso.take(10)).format(DateTimeFormatter.ofPattern("d MMMM", Locale.FRENCH))
}.getOrDefault(iso)

/** Écrit le PNG dans le cache partagé (FileProvider) et renvoie son URI content://. */
private fun writeWrappedPng(context: Context, bitmap: Bitmap, year: Int): android.net.Uri {
    val soft = if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
    val dir = java.io.File(context.cacheDir, "shared").apply { mkdirs() }
    val file = java.io.File(dir, "brewhome-wrapped-$year.png")
    file.outputStream().use { soft.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/** Feuille de partage Android (WhatsApp, Telegram, Messages…) pour une image. */
private fun openImageShareSheet(context: Context, uri: android.net.Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = android.content.ClipData.newRawUri(null, uri)   // aperçu dans la feuille de partage
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null))
}
