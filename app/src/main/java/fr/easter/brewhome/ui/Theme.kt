package fr.easter.brewhome.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Palettes reprises du site (templates/parts/styles.html, variables CSS) :
// fond quasi noir et cartes grises en sombre, crème et cartes blanches en
// clair, accent ambre (#ff9500 / #c2710c) et or. Tous les rôles Material 3
// sont définis pour que rien ne retombe sur les violets par défaut.
//
// Cartes : Card() de Material peint sa surface en surfaceContainerHighest.
// On y met donc la couleur des cartes du site (--card), et le reste des tons
// de surface autour (la barre de navigation utilise surfaceContainer).

private val LightColors = lightColorScheme(
    primary = Color(0xFFC2710C),              // --amber (clair)
    onPrimary = Color(0xFF000000),            // texte noir sur les boutons, comme .btn-primary
    primaryContainer = Color(0xFFFCE7C8),
    onPrimaryContainer = Color(0xFF92400E),   // --gold (clair)
    inversePrimary = Color(0xFFFF9500),
    secondary = Color(0xFF92400E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF6E3C6),
    onSecondaryContainer = Color(0xFF5C2A09),
    tertiary = Color(0xFF047857),             // vert houblon, assombri pour le fond clair
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD1FAE5),
    onTertiaryContainer = Color(0xFF064E3B),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    background = Color(0xFFF5F2EC),           // --bg
    onBackground = Color(0xFF1C1712),         // --text
    surface = Color(0xFFF5F2EC),
    onSurface = Color(0xFF1C1712),
    surfaceVariant = Color(0xFFEBE7DE),       // --bg2
    onSurfaceVariant = Color(0xFF78716C),     // --muted
    outline = Color(0xFF78716C),
    outlineVariant = Color(0xFFDDD8CE),       // --border
    inverseSurface = Color(0xFF2A231C),
    inverseOnSurface = Color(0xFFF5F2EC),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE5E0D6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF9F7F3),
    surfaceContainer = Color(0xFFF0ECE4),
    surfaceContainerHigh = Color(0xFFF4F1EB),  // --card2
    surfaceContainerHighest = Color(0xFFFFFFFF), // --card : cartes blanches sur fond crème
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF9500),              // --amber
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF4A3110),     // ambre à 25 % sur une carte, comme les badges du site
    onPrimaryContainer = Color(0xFFFFC940),   // --gold
    inversePrimary = Color(0xFFC2710C),
    secondary = Color(0xFFFFC940),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF3A2A0F),
    onSecondaryContainer = Color(0xFFFFD97A),
    tertiary = Color(0xFF10B981),             // --hop
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF0F3A2C),
    onTertiaryContainer = Color(0xFF6EE7B7),
    error = Color(0xFFEF4444),                // --danger
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF3F1515),
    onErrorContainer = Color(0xFFFCA5A5),
    background = Color(0xFF0A0A0A),           // --bg
    onBackground = Color(0xFFFFFFFF),         // --text
    surface = Color(0xFF0A0A0A),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF222222),       // --card2
    onSurfaceVariant = Color(0xFF9A9A9A),     // --muted, un peu éclairci pour les petits textes
    outline = Color(0xFF888888),
    outlineVariant = Color(0xFF2A2A2A),       // --border
    inverseSurface = Color(0xFFEDEDED),
    inverseOnSurface = Color(0xFF1A1A1A),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF2A2A2A),
    surfaceDim = Color(0xFF0A0A0A),
    surfaceContainerLowest = Color(0xFF050505),
    surfaceContainerLow = Color(0xFF111111),  // --bg2
    surfaceContainer = Color(0xFF141414),     // --nav-bg
    surfaceContainerHigh = Color(0xFF171717),
    surfaceContainerHighest = Color(0xFF1A1A1A), // --card
)

/** true si le thème effectif doit être sombre pour ce mode ("system"|"light"|"dark"). */
@Composable
fun isDarkTheme(mode: String): Boolean = when (mode) {
    "dark" -> true
    "light" -> false
    else -> isSystemInDarkTheme()
}

/**
 * Thème de l'app : palette du site BrewHome, ou couleurs dynamiques Material You
 * (dérivées du fond d'écran, Android 12+) si l'option est activée.
 */
@Composable
fun BrewHomeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = BrewHomeTypography,
        shapes = BrewHomeShapes,
        content = content,
    )
}
