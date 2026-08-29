package fr.easter.brewhome.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import fr.easter.brewhome.BrewViewModel
import fr.easter.brewhome.R
import fr.easter.brewhome.calc.BrewGuideSchedule
import fr.easter.brewhome.calc.RecipeEstimator
import fr.easter.brewhome.data.BrewGuideState
import fr.easter.brewhome.data.Recipe
import fr.easter.brewhome.data.RecipeIngredient
import fr.easter.brewhome.data.ScaleGuideMalt
import fr.easter.brewhome.notif.BrewGuideAlarms
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val stepTitleRes = listOf(
    R.string.phase_preparation, R.string.phase_crush, R.string.phase_mash,
    R.string.phase_boil, R.string.phase_pitch,
)

/**
 * Guide de brassage pas à pas, généré depuis les données de la recette (comme
 * le guide web `openBrewingGuide()`/`openBrewingGuideFromBrew()`) : 5 étapes
 * navigables, minuteurs d'empâtage/ébullition qui continuent en arrière-plan
 * (AlarmManager, voir BrewGuideAlarms) et planning de houblonnage en direct.
 * État persisté localement uniquement (BrewGuideStore), pas de sync serveur —
 * comme le web, qui ne persiste rien du tout.
 */
@Composable
fun BrewGuideScreen(
    vm: BrewViewModel,
    recipeId: Int?,
    brewId: Int?,
    onOpenPriming: () -> Unit = {},
) {
    val state by vm.state.collectAsState()
    val brew = brewId?.let { id -> state.brews.find { it.id == id } }
    val recipe = state.recipes.find { it.id == (recipeId ?: brew?.recipeId) }
    if (recipe == null) {
        EmptyHint(stringResource(R.string.recipe_not_found))
        return
    }
    val guideKey = if (brewId != null) "brew_$brewId" else "recipe_${recipe.id}"
    LaunchedEffect(guideKey) { vm.loadBrewGuide(guideKey) }
    val g = vm.brewGuideState.collectAsState().value
    if (g == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val context = LocalContext.current

    // Recompose chaque seconde tant qu'un minuteur tourne, pour un compte à
    // rebours en direct — état recalculé depuis l'horloge murale (endAt), pas
    // un compteur en mémoire, donc reste juste après réouverture de l'app.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(g.mashTimerEndAt, g.boilTimerEndAt) {
        while (g.mashTimerEndAt != null || g.boilTimerEndAt != null) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    fun update(block: (BrewGuideState) -> BrewGuideState) = vm.updateBrewGuide(guideKey, block)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        GuideStepBar(step = g.step, onStepClick = { step -> update { it.copy(step = step) } })
        when (g.step) {
            0 -> GuidePreparationStep(recipe, g.checkedItems) { key -> update { it.toggle(key) } }
            1 -> GuideCrushStep(vm, recipe, brew?.name ?: recipe.name, g.checkedItems) { key -> update { it.toggle(key) } }
            2 -> GuideMashStep(recipe, g, context, now, ::update)
            3 -> GuideBoilStep(recipe, g, context, now, ::update)
            else -> GuidePitchStep(recipe, g.checkedItems, onOpenPriming) { key -> update { it.toggle(key) } }
        }
        GuideStepFooter(
            step = g.step,
            totalSteps = stepTitleRes.size,
            onBack = { update { it.copy(step = it.step - 1) } },
            onNext = { update { it.copy(step = it.step + 1) } },
        )
    }
}

@Composable
private fun GuideStepFooter(step: Int, totalSteps: Int, onBack: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (step > 0) {
            OutlinedButton(onClick = onBack) { Text(stringResource(R.string.guide_step_back)) }
        } else {
            Spacer(Modifier)
        }
        Text(
            stringResource(R.string.guide_step_counter, step + 1, totalSteps),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
        )
        if (step < totalSteps - 1) {
            Button(onClick = onNext) { Text(stringResource(R.string.guide_step_next)) }
        } else {
            Spacer(Modifier)
        }
    }
}

private fun BrewGuideState.toggle(key: String): BrewGuideState =
    copy(checkedItems = if (key in checkedItems) checkedItems - key else checkedItems + key)

@Composable
private fun GuideStepBar(step: Int, onStepClick: (Int) -> Unit) {
    Column {
        LinearProgressIndicator(
            progress = { (step + 1) / stepTitleRes.size.toFloat() },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            stepTitleRes.forEachIndexed { i, res ->
                Text(
                    stringResource(res),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (i == step) FontWeight.Bold else FontWeight.Normal,
                    color = if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.clickable { onStepClick(i) },
                )
            }
        }
    }
}

@Composable
private fun IngredientChecklist(
    ingredients: List<RecipeIngredient>,
    keyPrefix: String,
    checked: Set<String>,
    onToggle: (String) -> Unit,
) {
    val byCategory = ingredients.groupBy { it.category.lowercase() }
    byCategory.forEach { (cat, ings) ->
        Text(
            categoryLabel(cat),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 6.dp),
        )
        ings.forEach { ing ->
            val itemKey = "${keyPrefix}_${ing.id}"
            val isChecked = itemKey in checked
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onToggle(itemKey) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = isChecked, onCheckedChange = { onToggle(itemKey) })
                Text(
                    "${ing.name} — ${fmtQty(ing.quantity)} ${ing.unit}",
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (isChecked) TextDecoration.LineThrough else null,
                    color = if (isChecked) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun GuidePreparationStep(recipe: Recipe, checked: Set<String>, onToggle: (String) -> Unit) {
    val estIngs = remember(recipe) {
        recipe.ingredients.map {
            RecipeEstimator.Ing(name = it.name, category = it.category.lowercase(), quantity = it.quantity, unit = it.unit)
        }
    }
    val water = remember(estIngs, recipe) {
        RecipeEstimator.water(
            recipe.volume, recipe.boilTime?.toDouble(), recipe.mashRatio,
            recipe.evapRate, recipe.grainAbsorption,
            RecipeEstimator.grainKg(estIngs),
            recipe.waterMashOverride, recipe.waterSpargeOverride,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (water != null) {
            InfoCard {
                InfoLine(stringResource(R.string.est_water_mash), "${fmtQty(water.mash)} L")
                InfoLine(stringResource(R.string.est_water_sparge), "${fmtQty(water.sparge)} L")
                InfoLine(stringResource(R.string.est_water_preboil), "${fmtQty(water.preboil)} L")
                InfoLine(stringResource(R.string.est_water_total), "${fmtQty(water.total)} L")
            }
        }
        IngredientChecklist(recipe.ingredients, "prep", checked, onToggle)
    }
}

@Composable
private fun GuideCrushStep(
    vm: BrewViewModel,
    recipe: Recipe,
    brewName: String,
    checked: Set<String>,
    onToggle: (String) -> Unit,
) {
    val malts = remember(recipe) { recipe.ingredients.filter { it.category.lowercase() == "malt" } }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.guide_crush_tip),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
        )
        if (malts.isNotEmpty()) {
            ScaleGuidePanel(vm, recipe.id, brewName, malts, onToggle)
        }
        IngredientChecklist(malts, "crush", checked, onToggle)
    }
}

/**
 * Guide de pesée connecté (voir /api/scale-guide côté serveur) : démarre une
 * session partagée que le web (ou une balance connectée) peut piloter, et
 * coche automatiquement les malts au fur et à mesure - comme
 * `_scaleGuidePollFn()` dans script_brassins.html, avec le même sondage
 * toutes les 2 s tant que la session est active. La session vit côté
 * serveur (app_settings) : elle survit à la fermeture de cet écran, pour
 * pouvoir être reprise depuis un autre appareil.
 */
@Composable
private fun ScaleGuidePanel(
    vm: BrewViewModel,
    recipeId: Int,
    brewName: String,
    malts: List<RecipeIngredient>,
    onToggle: (String) -> Unit,
) {
    var active by remember { mutableStateOf(false) }
    var lastStep by remember { mutableStateOf(0) }
    val status by vm.scaleGuide.collectAsState()
    val scope = rememberCoroutineScope()

    // Reprend le suivi si une session est déjà en cours (démarrée depuis un
    // autre appareil) plutôt que de forcer un nouveau départ.
    LaunchedEffect(Unit) {
        vm.refreshScaleGuide()
        if (vm.scaleGuide.value.active) {
            active = true
            lastStep = ((vm.scaleGuide.value.step ?: 1) - 1).coerceAtLeast(0)
        }
    }

    LaunchedEffect(active) {
        while (active) {
            delay(2000)
            vm.refreshScaleGuide()
            val s = vm.scaleGuide.value
            if (!s.active) {
                for (i in lastStep until malts.size) onToggle("crush_${malts[i].id}")
                active = false
                continue
            }
            val newStep = (s.step ?: 1) - 1
            if (newStep > lastStep) {
                for (i in lastStep until newStep) onToggle("crush_${malts[i].id}")
                lastStep = newStep
            }
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.scale_guide_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (active) {
                    OutlinedButton(onClick = {
                        active = false
                        scope.launch { vm.stopScaleGuide() }
                    }) { Text(stringResource(R.string.scale_guide_stop)) }
                } else {
                    Button(onClick = {
                        scope.launch {
                            val sgMalts = malts.map { ScaleGuideMalt(it.name, it.quantity, it.unit) }
                            if (vm.startScaleGuide(sgMalts, recipeId, brewName)) {
                                lastStep = 0
                                active = true
                            }
                        }
                    }) { Text(stringResource(R.string.scale_guide_start)) }
                }
            }
            if (active && status.active) {
                Text(
                    stringResource(
                        R.string.scale_guide_active,
                        status.maltName ?: "?",
                        fmtQty(status.targetKg),
                        status.step ?: 1,
                        status.total ?: malts.size,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** Millisecondes restantes (minuteur en cours ou en pause), null si jamais démarré. */
private fun remainingMs(endAt: Long?, pausedRemainingMs: Long?, now: Long): Long? =
    endAt?.let { (it - now).coerceAtLeast(0) } ?: pausedRemainingMs

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

@Composable
private fun TimerControls(
    running: Boolean,
    paused: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!running && !paused) {
            Button(onClick = onStart) { Text(stringResource(R.string.guide_timer_start)) }
        }
        if (running) {
            OutlinedButton(onClick = onPause) { Text(stringResource(R.string.guide_timer_pause)) }
        }
        if (paused) {
            Button(onClick = onResume) { Text(stringResource(R.string.guide_timer_resume)) }
        }
        if (running || paused) {
            OutlinedButton(onClick = onReset) { Text(stringResource(R.string.guide_timer_reset)) }
        }
    }
}

@Composable
private fun GuideMashStep(
    recipe: Recipe,
    g: BrewGuideState,
    context: Context,
    now: Long,
    update: ((BrewGuideState) -> BrewGuideState) -> Unit,
) {
    val durationMs = (recipe.mashTime ?: 60) * 60_000L
    val remaining = remainingMs(g.mashTimerEndAt, g.mashTimerPausedRemainingMs, now)
    val done = remaining == 0L && g.mashTimerEndAt != null

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        InfoCard {
            InfoLine(stringResource(R.string.recipe_mash_temp), recipe.mashTemp?.let { fmtQty(it) })
            InfoLine(stringResource(R.string.recipe_mash_time), recipe.mashTime?.toString())
        }
        Text(
            if (done) stringResource(R.string.guide_timer_done) else formatDuration(remaining ?: durationMs),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        TimerControls(
            running = g.mashTimerEndAt != null,
            paused = g.mashTimerPausedRemainingMs != null,
            onStart = {
                val endAt = now + durationMs
                update { it.copy(mashTimerEndAt = endAt, mashTimerPausedRemainingMs = null) }
                BrewGuideAlarms.scheduleMashDone(
                    context, endAt,
                    context.getString(R.string.guide_notif_mash_done_title),
                    context.getString(R.string.guide_notif_mash_done_text),
                )
            },
            onPause = {
                val left = (g.mashTimerEndAt!! - now).coerceAtLeast(0)
                update { it.copy(mashTimerEndAt = null, mashTimerPausedRemainingMs = left) }
                BrewGuideAlarms.cancelMashDone(context)
            },
            onResume = {
                val endAt = now + (g.mashTimerPausedRemainingMs ?: 0L)
                update { it.copy(mashTimerEndAt = endAt, mashTimerPausedRemainingMs = null) }
                BrewGuideAlarms.scheduleMashDone(
                    context, endAt,
                    context.getString(R.string.guide_notif_mash_done_title),
                    context.getString(R.string.guide_notif_mash_done_text),
                )
            },
            onReset = {
                update { it.copy(mashTimerEndAt = null, mashTimerPausedRemainingMs = null) }
                BrewGuideAlarms.cancelMashDone(context)
            },
        )
    }
}

private enum class HopStatus { UPCOMING, NOW, ADDED }

private fun hopStatus(elapsedMs: Long?, hopElapsedMs: Long): HopStatus {
    if (elapsedMs == null) return HopStatus.UPCOMING
    val diff = elapsedMs - hopElapsedMs
    return when {
        diff < 0 -> HopStatus.UPCOMING
        diff <= 60_000L -> HopStatus.NOW
        else -> HopStatus.ADDED
    }
}

@Composable
private fun GuideBoilStep(
    recipe: Recipe,
    g: BrewGuideState,
    context: Context,
    now: Long,
    update: ((BrewGuideState) -> BrewGuideState) -> Unit,
) {
    val boilMinutes = recipe.boilTime ?: 60
    val durationMs = boilMinutes * 60_000L
    val remaining = remainingMs(g.boilTimerEndAt, g.boilTimerPausedRemainingMs, now)
    val done = remaining == 0L && g.boilTimerEndAt != null
    val elapsed = remaining?.let { (durationMs - it).coerceIn(0L, durationMs) }
    val schedule = remember(recipe) { BrewGuideSchedule.boilSchedule(recipe.ingredients, boilMinutes) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        InfoCard {
            InfoLine(stringResource(R.string.recipe_boil_time), recipe.boilTime?.toString())
        }
        Text(
            if (done) stringResource(R.string.guide_timer_done) else formatDuration(remaining ?: durationMs),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        TimerControls(
            running = g.boilTimerEndAt != null,
            paused = g.boilTimerPausedRemainingMs != null,
            onStart = {
                update { it.copy(boilTimerEndAt = now + durationMs, boilTimerPausedRemainingMs = null) }
                scheduleBoilAlarms(context, now, schedule, alreadyElapsedMs = 0L, boilDurationMs = durationMs)
            },
            onPause = {
                val left = (g.boilTimerEndAt!! - now).coerceAtLeast(0)
                update { it.copy(boilTimerEndAt = null, boilTimerPausedRemainingMs = left) }
                BrewGuideAlarms.cancelBoilAll(context)
            },
            onResume = {
                val left = g.boilTimerPausedRemainingMs ?: 0L
                update { it.copy(boilTimerEndAt = now + left, boilTimerPausedRemainingMs = null) }
                scheduleBoilAlarms(context, now, schedule, alreadyElapsedMs = durationMs - left, boilDurationMs = durationMs)
            },
            onReset = {
                update { it.copy(boilTimerEndAt = null, boilTimerPausedRemainingMs = null) }
                BrewGuideAlarms.cancelBoilAll(context)
            },
        )
        if (schedule.isNotEmpty()) {
            Text(
                stringResource(R.string.guide_hop_schedule),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp),
            )
            schedule.forEach { hop ->
                val status = hopStatus(elapsed, hop.elapsedMs)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(hop.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(
                            when (status) {
                                HopStatus.UPCOMING -> R.string.guide_hop_upcoming
                                HopStatus.NOW -> R.string.guide_hop_now
                                HopStatus.ADDED -> R.string.guide_hop_added
                            },
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (status == HopStatus.NOW) FontWeight.Bold else FontWeight.Normal,
                        color = when (status) {
                            HopStatus.NOW -> MaterialTheme.colorScheme.error
                            HopStatus.ADDED -> MaterialTheme.colorScheme.outline
                            HopStatus.UPCOMING -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}

private fun scheduleBoilAlarms(
    context: Context,
    now: Long,
    schedule: List<BrewGuideSchedule.HopAddition>,
    alreadyElapsedMs: Long,
    boilDurationMs: Long,
) {
    val boilEndAt = now + (boilDurationMs - alreadyElapsedMs).coerceAtLeast(0)
    BrewGuideAlarms.scheduleBoilDone(
        context, boilEndAt,
        context.getString(R.string.guide_notif_boil_done_title),
        context.getString(R.string.guide_notif_boil_done_text),
    )
    schedule.forEachIndexed { i, hop ->
        if (hop.elapsedMs > alreadyElapsedMs) {
            val whenMillis = now + (hop.elapsedMs - alreadyElapsedMs)
            BrewGuideAlarms.scheduleHopAddition(
                context, i, whenMillis,
                context.getString(R.string.guide_notif_hop_title),
                context.getString(R.string.guide_notif_hop_text, hop.name),
            )
        }
    }
}

@Composable
private fun GuidePitchStep(
    recipe: Recipe,
    checked: Set<String>,
    onOpenPriming: () -> Unit,
    onToggle: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        InfoCard {
            InfoLine(stringResource(R.string.guide_ferm_temp_target), recipe.fermTemp?.let { "${fmtQty(it)} °C" })
        }
        val pitchIngredients = recipe.ingredients.filter { ing ->
            ing.category.lowercase() == "levure" ||
                (ing.category.lowercase() == "houblon" && ing.hopType == "dryhop")
        }
        IngredientChecklist(pitchIngredients, "pitch", checked, onToggle)
        OutlinedButton(onClick = onOpenPriming, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.guide_priming_link))
        }
    }
}
