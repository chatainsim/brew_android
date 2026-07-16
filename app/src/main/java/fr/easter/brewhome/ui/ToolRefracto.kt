package fr.easter.brewhome.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.easter.brewhome.R
import fr.easter.brewhome.calc.BrewCalc

/** Correction réfractomètre en cours de fermentation (Novotný). */
@Composable
internal fun RefractoCalcScreen() {
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
