package fr.easter.brewhome.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.easter.brewhome.R
import fr.easter.brewhome.calc.BrewCalc
import kotlin.math.roundToInt

private val pitchRates = listOf(
    R.string.pitch_ale to 0.75,
    R.string.pitch_high to 1.0,
    R.string.pitch_lager to 1.5,
)

/** Starter de levure : viabilité, cellules requises et paliers de propagation. */
@Composable
internal fun StarterCalcScreen() {
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
