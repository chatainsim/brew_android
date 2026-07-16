package fr.easter.brewhome.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import fr.easter.brewhome.R
import fr.easter.brewhome.calc.BrewCalc

/** Température et volume d'eau d'empâtage. */
@Composable
internal fun StrikeCalcScreen() {
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
