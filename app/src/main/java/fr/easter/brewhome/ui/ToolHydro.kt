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

/** Correction de la lecture du densimètre selon la température. */
@Composable
internal fun HydroCalcScreen() {
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
