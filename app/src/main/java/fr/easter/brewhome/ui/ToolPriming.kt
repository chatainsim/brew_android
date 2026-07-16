package fr.easter.brewhome.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import fr.easter.brewhome.R
import fr.easter.brewhome.calc.BrewCalc

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

/** Sucre de refermentation en bouteille selon le style et la température. */
@Composable
internal fun PrimingCalcScreen() {
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
