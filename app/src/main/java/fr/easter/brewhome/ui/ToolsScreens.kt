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
import fr.easter.brewhome.calc.BrewCalc
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class ToolDef(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
)

val toolDefs = listOf(
    ToolDef("abv", "Calculateur ABV", "DI / DF → alcool et atténuation", Icons.Outlined.Percent),
    ToolDef("hydro", "Correction densimètre", "Densité corrigée selon la température", Icons.Outlined.Thermostat),
    ToolDef("refracto", "Correction réfractomètre", "Lecture en fermentation (Novotný)", Icons.Outlined.Colorize),
    ToolDef("strike", "Température d'empâtage", "Eau à ajouter aux malts", Icons.Outlined.LocalFireDepartment),
    ToolDef("bottles", "Nombre de bouteilles", "Répartir un volume en 33 / 75 cl", Icons.Outlined.Liquor),
    ToolDef("priming", "Primage", "Sucre de refermentation en bouteille", Icons.Outlined.BubbleChart),
    ToolDef("starter", "Starter de levure", "Viabilité et taille du starter", Icons.Outlined.Biotech),
)

fun toolTitle(id: String?): String = toolDefs.find { it.id == id }?.title ?: "Outils"

// ── Écran liste ───────────────────────────────────────────────────────────────

@Composable
fun ToolsScreen(onOpen: (String) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(toolDefs, key = { it.id }) { tool ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(tool.id) },
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                tool.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            tool.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            tool.subtitle,
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
        else -> EmptyHint("Calculateur introuvable.")
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
        HintText("Alcool et atténuation apparente à partir des densités (SG).")
        TwoFields(
            left = { NumField("DI (densité initiale)", og, { og = it }, placeholder = "1,050") },
            right = { NumField("DF (densité finale)", fg, { fg = it }, placeholder = "1,010") },
        )
        val ogV = parseNum(og)
        val fgV = parseNum(fg)
        if (ogV != null && fgV != null) {
            val abv = BrewCalc.abv(ogV, fgV)
            val att = BrewCalc.attenuation(ogV, fgV)
            if (abv != null && att != null) {
                ResultRow(
                    "${fmt(abv, 1)} %" to "ABV",
                    "${att.roundToInt()} %" to "Atténuation app.",
                )
            } else {
                NoteCard("Vérifie les valeurs : la DF doit être inférieure à la DI.", error = true)
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
        HintText(
            "Corrige la lecture du densimètre quand le moût n'est pas à la " +
                "température d'étalonnage.",
        )
        TwoFields(
            left = { NumField("Densité lue", sg, { sg = it }, placeholder = "1,050") },
            right = { NumField("T° mesure (°C)", meas, { meas = it }, placeholder = "25") },
        )
        NumField("T° étalonnage (°C)", cal, { cal = it })
        val sgV = parseNum(sg)
        val measV = parseNum(meas)
        val calV = parseNum(cal)
        if (sgV != null && measV != null && calV != null) {
            val corrected = BrewCalc.hydroCorrection(sgV, measV, calV)
            val delta = corrected - sgV
            if (abs(delta) > 0.0001) {
                ResultRow(
                    fmt(corrected, 3) to "Densité corrigée",
                    "${if (delta > 0) "+" else ""}${fmt(delta, 4)}" to "Écart",
                )
            } else {
                ResultRow(fmt(corrected, 3) to "Densité corrigée")
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
        HintText(
            "Corrige l'erreur introduite par l'alcool sur un réfractomètre en " +
                "cours de fermentation (formule Novotný).",
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !ogUnitSg,
                onClick = { ogUnitSg = false },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text("DI en Brix") }
            SegmentedButton(
                selected = ogUnitSg,
                onClick = { ogUnitSg = true },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text("DI en SG") }
        }
        TwoFields(
            left = {
                NumField(
                    if (ogUnitSg) "DI (SG)" else "DI (Brix)",
                    og, { og = it },
                    placeholder = if (ogUnitSg) "1,050" else "12,0",
                )
            },
            right = { NumField("Lecture actuelle (Brix)", current, { current = it }, placeholder = "5,0") },
        )
        NumField("Facteur de correction (WCF)", wcf, { wcf = it })
        val ogV = parseNum(og)
        val curV = parseNum(current)
        val wcfV = parseNum(wcf) ?: 1.04
        if (ogV != null && curV != null) {
            val brixOg = if (ogUnitSg) BrewCalc.sgToBrix(ogV) else ogV
            val res = BrewCalc.refractoCorrection(brixOg, curV, wcfV)
            if (res != null) {
                if (res.abv != null) {
                    ResultRow(
                        fmt(res.fg, 4) to "DF corrigée",
                        "${fmt(res.abv, 1)} %" to "ABV",
                    )
                } else {
                    ResultRow(fmt(res.fg, 4) to "DF corrigée")
                }
            } else {
                NoteCard("Valeurs hors plage : vérifie les lectures.", error = true)
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
        HintText("Température de l'eau à ajouter aux malts pour atteindre la température de mash cible.")
        TwoFields(
            left = { NumField("Poids des malts (kg)", grain, { grain = it }, placeholder = "5,0") },
            right = { NumField("T° malts (°C)", grainTemp, { grainTemp = it }) },
        )
        TwoFields(
            left = { NumField("T° mash cible (°C)", mashTemp, { mashTemp = it }) },
            right = { NumField("Ratio eau/malt (L/kg)", ratio, { ratio = it }) },
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
                "${fmt(res.strikeTempC, 1)} °C" to "T° d'empâtage",
                "${fmt(res.waterLiters, 1)} L" to "Volume d'eau",
            )
            val mt = parseNum(mashTemp)!!
            when {
                res.strikeTempC > 100 ->
                    NoteCard(
                        "T° au-dessus de 100 °C : impossible. Augmente le ratio eau/malt " +
                            "ou préchauffe les malts.",
                        error = true,
                    )
                res.strikeTempC <= mt ->
                    NoteCard("L'eau serait plus froide que la cible : vérifie les températures.", error = true)
                else ->
                    NoteCard(
                        "Astuce : vise ${fmt(res.strikeTempC + 2, 1)} °C pour compenser les " +
                            "pertes d'une cuve froide.",
                    )
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
        HintText("Modifie un nombre de bouteilles : l'autre est recalculé automatiquement.")
        NumField(
            "Volume (L)", vol,
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
                NumField("Bouteilles 33 cl", n33, onChange = { v ->
                    n33 = v
                    if (volV != null && volV > 0) {
                        n75 = BrewCalc.bottlesAfter33(volV, v.toIntOrNull() ?: 0).n75.toString()
                    }
                })
            },
            right = {
                NumField("Bouteilles 75 cl", n75, onChange = { v ->
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
                diff == 0 -> NoteCard("Volume réparti exactement.")
                diff > 0 -> NoteCard("Reste ${diff} mL non embouteillés.")
                else -> NoteCard("Dépassement de ${abs(diff)} mL : trop de bouteilles.", error = true)
            }
        }
    }
}

// ── Primage ───────────────────────────────────────────────────────────────────

private val primingStyles = listOf(
    "— Choisir un style —" to null,
    "Ales britanniques (1,5–2,0)" to 1.7,
    "Ales américaines / IPA (2,2–2,8)" to 2.5,
    "Stout / Porter (1,5–2,1)" to 1.8,
    "Weizen / Hefeweizen (3,3–4,5)" to 3.5,
    "Belges — Tripel, Saison (2,8–3,8)" to 3.2,
    "Lager / Pilsner (2,3–2,7)" to 2.5,
    "Sour / Lambic (3,0–4,0)" to 3.0,
    "Cidre (2,5–4,0)" to 3.0,
)

private val primingSugars = listOf(
    "Sucrose (sucre de table)" to 3.97,
    "Dextrose anhydre" to 4.21,
    "Dextrose monohydraté (corn sugar)" to 4.64,
    "Extrait de malt sec (DME)" to 6.14,
    "Miel" to 8.57,
)

@Composable
private fun PrimingCalcScreen() {
    var styleIdx by rememberSaveable { mutableStateOf(0) }
    var vol by rememberSaveable { mutableStateOf("20") }
    var temp by rememberSaveable { mutableStateOf("20") }
    var co2 by rememberSaveable { mutableStateOf("2,5") }
    var sugarIdx by rememberSaveable { mutableStateOf(2) }

    ToolColumn {
        HintText("Quantité de sucre pour la refermentation en bouteille.")
        DropdownField(
            "Style de référence",
            primingStyles.map { it.first },
            styleIdx,
        ) { i ->
            styleIdx = i
            primingStyles[i].second?.let { co2 = fmt(it, 1) }
        }
        TwoFields(
            left = { NumField("Volume (L)", vol, { vol = it }) },
            right = { NumField("Température (°C)", temp, { temp = it }) },
        )
        NumField("CO₂ cible (volumes)", co2, { co2 = it })
        DropdownField("Type de sucre", primingSugars.map { it.first }, sugarIdx) { sugarIdx = it }

        val volV = parseNum(vol)
        val tempV = parseNum(temp)
        val co2V = parseNum(co2)
        if (volV != null && volV > 0 && tempV != null && co2V != null) {
            val res = BrewCalc.priming(volV, tempV, co2V, primingSugars[sugarIdx].second)
            if (res.co2ToAdd <= 0.0) {
                NoteCard(
                    "La bière contient déjà ${fmt(res.residualCo2, 2)} volumes de CO₂ " +
                        "résiduel à cette température : aucun sucre à ajouter.",
                    error = true,
                )
            } else {
                ResultRow("${fmt(res.gramsTotal, 1)} g" to "Sucre total")
                ResultRow(
                    "${fmt(res.per33cl, 1)} g" to "par 33 cl",
                    "${fmt(res.per50cl, 1)} g" to "par 50 cl",
                    "${fmt(res.per75cl, 1)} g" to "par 75 cl",
                )
                NoteCard(
                    "CO₂ résiduel à ${fmt(tempV, 0)} °C : ${fmt(res.residualCo2, 2)} vol — " +
                        "à ajouter : ${fmt(res.co2ToAdd, 2)} vol. Dissoudre le sucre dans un " +
                        "peu d'eau bouillie avant de l'ajouter au fût de soutirage.",
                )
            }
        }
    }
}

// ── Starter de levure ─────────────────────────────────────────────────────────

private val pitchRates = listOf(
    "Ale standard (0,75 M/mL/°P)" to 0.75,
    "Haute densité (1,0 M/mL/°P)" to 1.0,
    "Lager (1,5 M/mL/°P)" to 1.5,
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
        HintText(
            "Starter pour levure liquide : viabilité selon l'âge du paquet " +
                "(Mr. Malty) et taille du starter au DME.",
        )
        TwoFields(
            left = { NumField("Âge du paquet (jours)", age, { age = it }) },
            right = { NumField("Cellules initiales (Mds)", cells, { cells = it }) },
        )
        DropdownField("Taux de pitch", pitchRates.map { it.first }, pitchIdx) { pitchIdx = it }
        TwoFields(
            left = { NumField("Volume à brasser (L)", vol, { vol = it }) },
            right = { NumField("DI cible", og, { og = it }) },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = stir, onCheckedChange = { stir = it })
            Text("Stir plate (agitation)", Modifier.clickable { stir = !stir })
            Spacer(Modifier.width(16.dp))
            Checkbox(checked = twoSteps, onCheckedChange = { twoSteps = it })
            Text("2 étapes", Modifier.clickable { twoSteps = !twoSteps })
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
                "${(res.viability * 100).roundToInt()} %" to "Viabilité",
                "${res.viableCells.roundToInt()} Mds" to "Cellules viables",
                "${res.requiredCells.roundToInt()} Mds" to "Requises",
            )
            if (res.steps.isEmpty()) {
                NoteCard("Le paquet suffit : aucun starter nécessaire.")
            } else {
                res.steps.forEachIndexed { i, step ->
                    val volStr = if (step.volumeL >= 1) "${fmt(step.volumeL, 2)} L"
                                 else "${(step.volumeL * 1000).roundToInt()} mL"
                    ResultRow(
                        volStr to if (res.steps.size > 1) "Étape ${i + 1} — moût" else "Volume de moût",
                        "${step.dmeGrams.roundToInt()} g" to "DME",
                        fmt(res.starterGravity, 3) to "Densité",
                    )
                    if (res.steps.size > 1 && i == 0) {
                        HintText("Laisser fermenter, décanter au froid, puis relancer l'étape 2.")
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
