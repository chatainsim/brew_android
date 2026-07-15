package fr.easter.brewhome.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Biotech
import androidx.compose.material.icons.outlined.BubbleChart
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Liquor
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import fr.easter.brewhome.R
import fr.easter.brewhome.calc.BrewCalc
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

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

// ── Écran liste ───────────────────────────────────────────────────────────────

@Composable
fun ToolsScreen(onOpenStats: () -> Unit, onOpen: (String) -> Unit) {
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
                onOpenStats,
            )
        }
        items(toolDefs, key = { it.id }) { tool ->
            ToolCard(stringResource(tool.titleRes), stringResource(tool.subtitleRes), tool.icon) {
                onOpen(tool.id)
            }
        }
    }
}

@Composable
private fun ToolCard(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
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

// ── Briques communes ──────────────────────────────────────────────────────────

private fun parseNum(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

private fun fmt(v: Double, decimals: Int): String =
    String.format(Locale.FRANCE, "%.${decimals}f", v)

@Composable
private fun ToolColumn(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun NumField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun TwoFields(left: @Composable () -> Unit, right: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f)) { left() }
        Box(Modifier.weight(1f)) { right() }
    }
}

/** Grande valeur de résultat avec libellé, façon page Outils du serveur. */
@Composable
private fun ResultBig(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ResultRow(vararg results: Pair<String, String>) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            results.forEach { (value, label) ->
                ResultBig(value, label, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HintText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
    )
}

@Composable
private fun NoteCard(text: String, error: Boolean = false) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (error) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = if (error) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = options.getOrElse(selectedIndex) { "" },
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { i, opt ->
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = {
                        onSelect(i)
                        expanded = false
                    },
                )
            }
        }
    }
}

// ── ABV ───────────────────────────────────────────────────────────────────────

@Composable
private fun AbvCalcScreen() {
    var og by rememberSaveable { mutableStateOf("") }
    var fg by rememberSaveable { mutableStateOf("") }

    ToolColumn {
        HintText(stringResource(R.string.hint_abv))
        TwoFields(
            left = { NumField(stringResource(R.string.field_og_full), og, { og = it }, placeholder = "1,050") },
            right = { NumField(stringResource(R.string.field_fg_full), fg, { fg = it }, placeholder = "1,010") },
        )
        val ogV = parseNum(og)
        val fgV = parseNum(fg)
        if (ogV != null && fgV != null) {
            val abv = BrewCalc.abv(ogV, fgV)
            val att = BrewCalc.attenuation(ogV, fgV)
            if (abv != null && att != null) {
                ResultRow(
                    "${fmt(abv, 1)} %" to stringResource(R.string.res_abv),
                    "${att.roundToInt()} %" to stringResource(R.string.res_attenuation),
                )
            } else {
                NoteCard(stringResource(R.string.note_check_fg), error = true)
            }
        }
    }
}

// ── Correction densimètre ─────────────────────────────────────────────────────

@Composable
private fun HydroCalcScreen() {
    var sg by rememberSaveable { mutableStateOf("") }
    var meas by rememberSaveable { mutableStateOf("") }
    var cal by rememberSaveable { mutableStateOf("20") }

    ToolColumn {
        HintText(stringResource(R.string.hint_hydro))
        TwoFields(
            left = { NumField(stringResource(R.string.field_sg_read), sg, { sg = it }, placeholder = "1,050") },
            right = { NumField(stringResource(R.string.field_temp_meas), meas, { meas = it }, placeholder = "25") },
        )
        NumField(stringResource(R.string.field_temp_cal), cal, { cal = it })
        val sgV = parseNum(sg)
        val measV = parseNum(meas)
        val calV = parseNum(cal)
        if (sgV != null && measV != null && calV != null) {
            val corrected = BrewCalc.hydroCorrection(sgV, measV, calV)
            val delta = corrected - sgV
            if (abs(delta) > 0.0001) {
                ResultRow(
                    fmt(corrected, 3) to stringResource(R.string.res_sg_corrected),
                    "${if (delta > 0) "+" else ""}${fmt(delta, 4)}" to stringResource(R.string.res_delta),
                )
            } else {
                ResultRow(fmt(corrected, 3) to stringResource(R.string.res_sg_corrected))
            }
        }
    }
}

// ── Correction réfractomètre ──────────────────────────────────────────────────

@Composable
private fun RefractoCalcScreen() {
    var ogUnitSg by rememberSaveable { mutableStateOf(false) }
    var og by rememberSaveable { mutableStateOf("") }
    var current by rememberSaveable { mutableStateOf("") }
    var wcf by rememberSaveable { mutableStateOf("1,04") }

    ToolColumn {
        HintText(stringResource(R.string.hint_refracto))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !ogUnitSg,
                onClick = { ogUnitSg = false },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text(stringResource(R.string.seg_og_brix)) }
            SegmentedButton(
                selected = ogUnitSg,
                onClick = { ogUnitSg = true },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text(stringResource(R.string.seg_og_sg)) }
        }
        TwoFields(
            left = {
                NumField(
                    if (ogUnitSg) stringResource(R.string.field_og_sg) else stringResource(R.string.field_og_brix),
                    og, { og = it },
                    placeholder = if (ogUnitSg) "1,050" else "12,0",
                )
            },
            right = { NumField(stringResource(R.string.field_current_brix), current, { current = it }, placeholder = "5,0") },
        )
        NumField(stringResource(R.string.field_wcf), wcf, { wcf = it })
        val ogV = parseNum(og)
        val curV = parseNum(current)
        val wcfV = parseNum(wcf) ?: 1.04
        if (ogV != null && curV != null) {
            val brixOg = if (ogUnitSg) BrewCalc.sgToBrix(ogV) else ogV
            val res = BrewCalc.refractoCorrection(brixOg, curV, wcfV)
            if (res != null) {
                if (res.abv != null) {
                    ResultRow(
                        fmt(res.fg, 4) to stringResource(R.string.res_fg_corrected),
                        "${fmt(res.abv, 1)} %" to stringResource(R.string.res_abv),
                    )
                } else {
                    ResultRow(fmt(res.fg, 4) to stringResource(R.string.res_fg_corrected))
                }
            } else {
                NoteCard(stringResource(R.string.note_out_of_range), error = true)
            }
        }
    }
}

// ── Température d'empâtage ────────────────────────────────────────────────────

@Composable
private fun StrikeCalcScreen() {
    var grain by rememberSaveable { mutableStateOf("") }
    var grainTemp by rememberSaveable { mutableStateOf("20") }
    var mashTemp by rememberSaveable { mutableStateOf("65") }
    var ratio by rememberSaveable { mutableStateOf("3,0") }

    ToolColumn {
        HintText(stringResource(R.string.hint_strike))
        TwoFields(
            left = { NumField(stringResource(R.string.field_grain_kg), grain, { grain = it }, placeholder = "5,0") },
            right = { NumField(stringResource(R.string.field_grain_temp), grainTemp, { grainTemp = it }) },
        )
        TwoFields(
            left = { NumField(stringResource(R.string.field_mash_target), mashTemp, { mashTemp = it }) },
            right = { NumField(stringResource(R.string.field_ratio), ratio, { ratio = it }) },
        )
        val res = parseNum(grain)?.let { g ->
            parseNum(grainTemp)?.let { gt ->
                parseNum(mashTemp)?.let { mt ->
                    parseNum(ratio)?.let { r -> BrewCalc.strikeWater(g, gt, mt, r) }
                }
            }
        }
        if (res != null) {
            ResultRow(
                "${fmt(res.strikeTempC, 1)} °C" to stringResource(R.string.res_strike_temp),
                "${fmt(res.waterLiters, 1)} L" to stringResource(R.string.res_water_vol),
            )
            val mt = parseNum(mashTemp)!!
            when {
                res.strikeTempC > 100 ->
                    NoteCard(stringResource(R.string.note_strike_over100), error = true)
                res.strikeTempC <= mt ->
                    NoteCard(stringResource(R.string.note_strike_cold), error = true)
                else ->
                    NoteCard(stringResource(R.string.note_strike_tip, fmt(res.strikeTempC + 2, 1)))
            }
        }
    }
}

// ── Bouteilles ────────────────────────────────────────────────────────────────

@Composable
private fun BottlesCalcScreen() {
    var vol by rememberSaveable { mutableStateOf("") }
    var n33 by rememberSaveable { mutableStateOf("") }
    var n75 by rememberSaveable { mutableStateOf("") }

    ToolColumn {
        HintText(stringResource(R.string.hint_bottles))
        NumField(
            stringResource(R.string.label_volume_l), vol,
            onChange = { v ->
                vol = v
                val volV = parseNum(v)
                if (volV != null && volV > 0) {
                    val r = BrewCalc.bottlesFromVolume(volV)
                    n33 = r.n33.toString()
                    n75 = r.n75.toString()
                } else {
                    n33 = ""; n75 = ""
                }
            },
            placeholder = "20",
        )
        val volV = parseNum(vol)
        TwoFields(
            left = {
                NumField(stringResource(R.string.stat_bottles_33), n33, onChange = { v ->
                    n33 = v
                    if (volV != null && volV > 0) {
                        n75 = BrewCalc.bottlesAfter33(volV, v.toIntOrNull() ?: 0).n75.toString()
                    }
                })
            },
            right = {
                NumField(stringResource(R.string.stat_bottles_75), n75, onChange = { v ->
                    n75 = v
                    if (volV != null && volV > 0) {
                        n33 = BrewCalc.bottlesAfter75(volV, v.toIntOrNull() ?: 0).n33.toString()
                    }
                })
            },
        )
        if (volV != null && volV > 0) {
            val used = (n33.toIntOrNull() ?: 0) * 330 + (n75.toIntOrNull() ?: 0) * 750
            val diff = (volV * 1000).roundToInt() - used
            when {
                diff == 0 -> NoteCard(stringResource(R.string.note_exact))
                diff > 0 -> NoteCard(stringResource(R.string.note_rest, diff))
                else -> NoteCard(stringResource(R.string.note_overflow, abs(diff)), error = true)
            }
        }
    }
}

// ── Primage ───────────────────────────────────────────────────────────────────

private val primingStyles = listOf<Pair<Int, Double?>>(
    R.string.priming_style_none to null,
    R.string.priming_style_uk to 1.7,
    R.string.priming_style_us to 2.5,
    R.string.priming_style_stout to 1.8,
    R.string.priming_style_weizen to 3.5,
    R.string.priming_style_belgian to 3.2,
    R.string.priming_style_lager to 2.5,
    R.string.priming_style_sour to 3.0,
    R.string.priming_style_cider to 3.0,
)

private val primingSugars = listOf(
    R.string.sugar_sucrose to 3.97,
    R.string.sugar_dextrose_anh to 4.21,
    R.string.sugar_dextrose_mono to 4.64,
    R.string.sugar_dme to 6.14,
    R.string.sugar_honey to 8.57,
)

@Composable
private fun PrimingCalcScreen() {
    var styleIdx by rememberSaveable { mutableStateOf(0) }
    var vol by rememberSaveable { mutableStateOf("20") }
    var temp by rememberSaveable { mutableStateOf("20") }
    var co2 by rememberSaveable { mutableStateOf("2,5") }
    var sugarIdx by rememberSaveable { mutableStateOf(2) }

    ToolColumn {
        HintText(stringResource(R.string.hint_priming))
        DropdownField(
            stringResource(R.string.field_style_ref),
            primingStyles.map { stringResource(it.first) },
            styleIdx,
        ) { i ->
            styleIdx = i
            primingStyles[i].second?.let { co2 = fmt(it, 1) }
        }
        TwoFields(
            left = { NumField(stringResource(R.string.label_volume_l), vol, { vol = it }) },
            right = { NumField(stringResource(R.string.field_temp), temp, { temp = it }) },
        )
        NumField(stringResource(R.string.field_co2_target), co2, { co2 = it })
        DropdownField(stringResource(R.string.field_sugar_type), primingSugars.map { stringResource(it.first) }, sugarIdx) { sugarIdx = it }

        val volV = parseNum(vol)
        val tempV = parseNum(temp)
        val co2V = parseNum(co2)
        if (volV != null && volV > 0 && tempV != null && co2V != null) {
            val res = BrewCalc.priming(volV, tempV, co2V, primingSugars[sugarIdx].second)
            if (res.co2ToAdd <= 0.0) {
                NoteCard(stringResource(R.string.note_no_sugar, fmt(res.residualCo2, 2)), error = true)
            } else {
                ResultRow("${fmt(res.gramsTotal, 1)} g" to stringResource(R.string.res_sugar_total))
                ResultRow(
                    "${fmt(res.per33cl, 1)} g" to stringResource(R.string.res_per_33),
                    "${fmt(res.per50cl, 1)} g" to stringResource(R.string.res_per_50),
                    "${fmt(res.per75cl, 1)} g" to stringResource(R.string.res_per_75),
                )
                NoteCard(stringResource(R.string.note_priming_info, fmt(tempV, 0), fmt(res.residualCo2, 2), fmt(res.co2ToAdd, 2)))
            }
        }
    }
}

// ── Starter de levure ─────────────────────────────────────────────────────────

private val pitchRates = listOf(
    R.string.pitch_ale to 0.75,
    R.string.pitch_high to 1.0,
    R.string.pitch_lager to 1.5,
)

@Composable
private fun StarterCalcScreen() {
    var age by rememberSaveable { mutableStateOf("0") }
    var cells by rememberSaveable { mutableStateOf("100") }
    var pitchIdx by rememberSaveable { mutableStateOf(0) }
    var vol by rememberSaveable { mutableStateOf("20") }
    var og by rememberSaveable { mutableStateOf("1,050") }
    var twoSteps by rememberSaveable { mutableStateOf(false) }
    var stir by rememberSaveable { mutableStateOf(true) }

    ToolColumn {
        HintText(stringResource(R.string.hint_starter))
        TwoFields(
            left = { NumField(stringResource(R.string.field_age), age, { age = it }) },
            right = { NumField(stringResource(R.string.field_cells), cells, { cells = it }) },
        )
        DropdownField(stringResource(R.string.field_pitch), pitchRates.map { stringResource(it.first) }, pitchIdx) { pitchIdx = it }
        TwoFields(
            left = { NumField(stringResource(R.string.field_brew_vol), vol, { vol = it }) },
            right = { NumField(stringResource(R.string.field_og_target), og, { og = it }) },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = stir, onCheckedChange = { stir = it })
            Text(stringResource(R.string.check_stir), Modifier.clickable { stir = !stir })
            Spacer(Modifier.width(16.dp))
            Checkbox(checked = twoSteps, onCheckedChange = { twoSteps = it })
            Text(stringResource(R.string.check_two_steps), Modifier.clickable { twoSteps = !twoSteps })
        }

        val ageV = age.trim().toIntOrNull()
        val cellsV = parseNum(cells)
        val volV = parseNum(vol)
        val ogV = parseNum(og)
        if (ageV != null && cellsV != null && volV != null && ogV != null && ogV > 1) {
            val res = BrewCalc.starter(
                ageDays = ageV,
                pkgCells = cellsV,
                volumeL = volV,
                og = ogV,
                pitchRate = pitchRates[pitchIdx].second,
                stirPlate = stir,
                twoSteps = twoSteps,
            )
            ResultRow(
                "${(res.viability * 100).roundToInt()} %" to stringResource(R.string.res_viability),
                "${res.viableCells.roundToInt()} Mds" to stringResource(R.string.res_viable_cells),
                "${res.requiredCells.roundToInt()} Mds" to stringResource(R.string.res_required),
            )
            if (res.steps.isEmpty()) {
                NoteCard(stringResource(R.string.note_pack_enough))
            } else {
                res.steps.forEachIndexed { i, step ->
                    val volStr = if (step.volumeL >= 1) "${fmt(step.volumeL, 2)} L"
                                 else "${(step.volumeL * 1000).roundToInt()} mL"
                    ResultRow(
                        volStr to if (res.steps.size > 1) stringResource(R.string.res_step_wort, i + 1) else stringResource(R.string.res_wort_vol),
                        "${step.dmeGrams.roundToInt()} g" to stringResource(R.string.res_dme),
                        fmt(res.starterGravity, 3) to stringResource(R.string.res_gravity),
                    )
                    if (res.steps.size > 1 && i == 0) {
                        HintText(stringResource(R.string.hint_two_steps))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
