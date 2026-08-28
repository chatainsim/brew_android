package fr.easter.brewhome.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import fr.easter.brewhome.R

// Manrope (Google Fonts, licence OFL - voir licenses/manrope-OFL.txt) : police
// variable à graisse réglable, utilisée uniquement pour les rôles
// display/headline/title (en-têtes d'écran, gros titres, titre de la barre
// du haut). Le corps de texte garde la police système par défaut : sur les
// petits libellés (degrés, volumes, dates) la lisibilité prime sur le style.
// FontVariation (instanciation d'une police variable par graisse) est encore
// marquée expérimentale côté Compose, mais stable en pratique depuis plusieurs
// versions - c'est l'API recommandée par Google pour ce cas d'usage précis.
@OptIn(ExperimentalTextApi::class)
private fun manrope(weight: FontWeight) = Font(
    R.font.manrope,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

private val ManropeFamily = FontFamily(
    manrope(FontWeight.Normal),
    manrope(FontWeight.Medium),
    manrope(FontWeight.SemiBold),
    manrope(FontWeight.Bold),
    manrope(FontWeight.ExtraBold),
)

private val Default = Typography()

val BrewHomeTypography = Typography(
    displayLarge = Default.displayLarge.copy(fontFamily = ManropeFamily, fontWeight = FontWeight.ExtraBold),
    displayMedium = Default.displayMedium.copy(fontFamily = ManropeFamily, fontWeight = FontWeight.ExtraBold),
    displaySmall = Default.displaySmall.copy(fontFamily = ManropeFamily, fontWeight = FontWeight.Bold),
    headlineLarge = Default.headlineLarge.copy(fontFamily = ManropeFamily, fontWeight = FontWeight.Bold),
    headlineMedium = Default.headlineMedium.copy(fontFamily = ManropeFamily, fontWeight = FontWeight.Bold),
    headlineSmall = Default.headlineSmall.copy(fontFamily = ManropeFamily, fontWeight = FontWeight.SemiBold),
    titleLarge = Default.titleLarge.copy(fontFamily = ManropeFamily, fontWeight = FontWeight.SemiBold),
    titleMedium = Default.titleMedium.copy(fontFamily = ManropeFamily, fontWeight = FontWeight.SemiBold),
    titleSmall = Default.titleSmall.copy(fontFamily = ManropeFamily, fontWeight = FontWeight.Medium),
)
