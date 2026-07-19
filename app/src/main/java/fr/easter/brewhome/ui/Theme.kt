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

// Palettes tonales Material 3 complètes, générées depuis la couleur graine
// ambre (#B86E00) façon Material Theme Builder : tous les rôles sont définis,
// rien ne retombe sur les violets par défaut de Material.

private val LightColors = lightColorScheme(
    primary = Color(0xFF8B5000),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDCBE),
    onPrimaryContainer = Color(0xFF2C1600),
    inversePrimary = Color(0xFFFFB870),
    secondary = Color(0xFF725A42),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFEDDBE),
    onSecondaryContainer = Color(0xFF291806),
    tertiary = Color(0xFF58633A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDCE8B4),
    onTertiaryContainer = Color(0xFF161E01),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF8F4),
    onBackground = Color(0xFF221A11),
    surface = Color(0xFFFFF8F4),
    onSurface = Color(0xFF221A11),
    surfaceVariant = Color(0xFFF2DFD1),
    onSurfaceVariant = Color(0xFF51443A),
    outline = Color(0xFF837469),
    outlineVariant = Color(0xFFD5C3B5),
    inverseSurface = Color(0xFF382F26),
    inverseOnSurface = Color(0xFFFEEEE0),
    scrim = Color(0xFF000000),
    // Tons de surface : sans eux, cartes et barre de navigation retombent sur
    // les gris neutres par défaut de Material au lieu des beiges ambrés.
    surfaceBright = Color(0xFFFFF8F4),
    surfaceDim = Color(0xFFE6D8CC),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF1E6),
    surfaceContainer = Color(0xFFFAEBDD),
    surfaceContainerHigh = Color(0xFFF4E6D8),
    surfaceContainerHighest = Color(0xFFEFE0D2),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB870),
    onPrimary = Color(0xFF4A2800),
    primaryContainer = Color(0xFF693C00),
    onPrimaryContainer = Color(0xFFFFDCBE),
    inversePrimary = Color(0xFF8B5000),
    secondary = Color(0xFFE1C1A4),
    onSecondary = Color(0xFF402C18),
    secondaryContainer = Color(0xFF59422C),
    onSecondaryContainer = Color(0xFFFEDDBE),
    tertiary = Color(0xFFC0CC9A),
    onTertiary = Color(0xFF2A3310),
    tertiaryContainer = Color(0xFF404A24),
    onTertiaryContainer = Color(0xFFDCE8B4),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF19120C),
    onBackground = Color(0xFFEFE0D5),
    surface = Color(0xFF19120C),
    onSurface = Color(0xFFEFE0D5),
    surfaceVariant = Color(0xFF51443A),
    onSurfaceVariant = Color(0xFFD5C3B5),
    outline = Color(0xFF9D8E81),
    outlineVariant = Color(0xFF51443A),
    inverseSurface = Color(0xFFEFE0D5),
    inverseOnSurface = Color(0xFF382F26),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF413729),
    surfaceDim = Color(0xFF19120C),
    surfaceContainerLowest = Color(0xFF140D07),
    surfaceContainerLow = Color(0xFF221A11),
    surfaceContainer = Color(0xFF261E15),
    surfaceContainerHigh = Color(0xFF31281F),
    surfaceContainerHighest = Color(0xFF3D3329),
)

/** true si le thème effectif doit être sombre pour ce mode ("system"|"light"|"dark"). */
@Composable
fun isDarkTheme(mode: String): Boolean = when (mode) {
    "dark" -> true
    "light" -> false
    else -> isSystemInDarkTheme()
}

/**
 * Thème de l'app : palette ambre BrewHome, ou couleurs dynamiques Material You
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
    MaterialTheme(colorScheme = colorScheme, content = content)
}
