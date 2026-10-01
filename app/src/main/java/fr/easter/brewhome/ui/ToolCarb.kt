package fr.easter.brewhome.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import fr.easter.brewhome.R
import fr.easter.brewhome.calc.BrewCalc

/** Températures du tableau, comme sur le site. */
private val carbTableTemps = listOf(1, 2, 4, 6, 8, 10, 12)

/** Pression à régler sur le détendeur d'un fût selon la température et le CO₂ visé. */
@Composable
internal fun CarbCalcScreen() {
    var styleIdx by rememberSaveable { mutableStateOf(0) }
    var temp by rememberSaveable { mutableStateOf("4") }
    var co2 by rememberSaveable { mutableStateOf("2,5") }

    ToolColumn {
        HintText(stringResource(R.string.hint_carb))
        DropdownField(
            stringResource(R.string.field_style_ref),
            primingStyles.map { stringResource(it.first) },
            styleIdx,
        ) { i ->
            styleIdx = i
            primingStyles[i].second?.let { co2 = fmt(it, 1) }
        }
        TwoFields(
            left = { NumField(stringResource(R.string.field_keg_temp), temp, { temp = it }) },
            right = { NumField(stringResource(R.string.field_co2_target), co2, { co2 = it }) },
        )

        val tempV = parseNum(temp)
        val co2V = parseNum(co2)
        if (tempV != null && co2V != null && co2V > 0) {
            val psi = BrewCalc.carbPressurePsi(tempV, co2V)
            if (psi <= 0.0) {
                NoteCard(stringResource(R.string.note_carb_low), error = true)
            } else {
                ResultRow(
                    "${fmt(psi * BrewCalc.PSI_TO_BAR, 2)} bar" to stringResource(R.string.res_carb_pressure),
                    "${fmt(psi, 1)} psi" to stringResource(R.string.res_carb_psi),
                )
                HintText(stringResource(R.string.carb_table, fmt(co2V, 1)))
                // Deux lignes pour rester lisible sur un téléphone
                carbTableTemps.chunked(4).forEach { row ->
                    ResultRow(*row.map { tc ->
                        val p = BrewCalc.carbPressurePsi(tc.toDouble(), co2V)
                        (if (p > 0) fmt(p * BrewCalc.PSI_TO_BAR, 2) else "—") to "$tc °C"
                    }.toTypedArray())
                }
                NoteCard(stringResource(R.string.note_carb_info))
            }
        }
    }
}
