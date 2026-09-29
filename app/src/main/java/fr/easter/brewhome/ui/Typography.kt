package fr.easter.brewhome.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import fr.easter.brewhome.R

// Mêmes polices que le site (Google Fonts, licence OFL — voir licenses/) :
// Playfair Display (serif) pour les gros titres, comme les h1–h3 et le nom
// « BrewHome » du site ; Work Sans pour tout le reste. Polices variables :
// une seule police par famille, la graisse est réglée par FontVariation
// (API encore marquée expérimentale côté Compose, stable en pratique).
@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: FontWeight) = Font(
    res,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

private val weights = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold, FontWeight.Black)

val PlayfairFamily = FontFamily(weights.map { variable(R.font.playfair_display, it) })
val WorkSansFamily = FontFamily(weights.map { variable(R.font.work_sans, it) })

private val Default = Typography()

// Titres serif resserrés comme sur le site (letter-spacing: -.02em)
private fun TextStyle.serif(weight: FontWeight) =
    copy(fontFamily = PlayfairFamily, fontWeight = weight, letterSpacing = (-0.02).em)
private fun TextStyle.sans(weight: FontWeight? = null) =
    copy(fontFamily = WorkSansFamily, fontWeight = weight ?: fontWeight)

val BrewHomeTypography = Typography(
    displayLarge = Default.displayLarge.serif(FontWeight.Black),
    displayMedium = Default.displayMedium.serif(FontWeight.Black),
    displaySmall = Default.displaySmall.serif(FontWeight.ExtraBold),
    headlineLarge = Default.headlineLarge.serif(FontWeight.ExtraBold),
    headlineMedium = Default.headlineMedium.serif(FontWeight.Bold),
    headlineSmall = Default.headlineSmall.serif(FontWeight.Bold),
    titleLarge = Default.titleLarge.serif(FontWeight.Bold),
    // En dessous, texte courant : Work Sans (lisible sur les petits libellés)
    titleMedium = Default.titleMedium.sans(FontWeight.SemiBold),
    titleSmall = Default.titleSmall.sans(FontWeight.SemiBold),
    bodyLarge = Default.bodyLarge.sans(),
    bodyMedium = Default.bodyMedium.sans(),
    bodySmall = Default.bodySmall.sans(),
    labelLarge = Default.labelLarge.sans(FontWeight.SemiBold),
    labelMedium = Default.labelMedium.sans(FontWeight.Medium),
    labelSmall = Default.labelSmall.sans(FontWeight.Medium),
)
