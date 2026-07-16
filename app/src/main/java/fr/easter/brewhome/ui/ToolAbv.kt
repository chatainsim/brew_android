package fr.easter.brewhome.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import fr.easter.brewhome.R
import fr.easter.brewhome.calc.BrewCalc
import kotlin.math.roundToInt

/** Calculateur ABV et atténuation à partir des densités OG/FG. */
@Composable
internal fun AbvCalcScreen() {
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
