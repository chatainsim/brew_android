package fr.easter.brewhome.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import fr.easter.brewhome.R
import fr.easter.brewhome.calc.BrewCalc
import kotlin.math.abs
import kotlin.math.roundToInt

/** Répartition d'un volume en bouteilles de 33 et 75 cl. */
@Composable
internal fun BottlesCalcScreen() {
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
