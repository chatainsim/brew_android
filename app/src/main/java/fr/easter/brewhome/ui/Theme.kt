package fr.easter.brewhome.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Amber = Color(0xFFB86E00)
private val AmberDark = Color(0xFFFFB95C)
private val Malt = Color(0xFF6D4C41)

private val LightColors = lightColorScheme(
    primary = Amber,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDDB5),
    onPrimaryContainer = Color(0xFF2A1700),
    secondary = Malt,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3DDD2),
    onSecondaryContainer = Color(0xFF291812),
    surface = Color(0xFFFFF8F3),
    background = Color(0xFFFFF8F3),
)

private val DarkColors = darkColorScheme(
    primary = AmberDark,
    onPrimary = Color(0xFF462A00),
    primaryContainer = Color(0xFF643F00),
    onPrimaryContainer = Color(0xFFFFDDB5),
    secondary = Color(0xFFE0BFB0),
    onSecondary = Color(0xFF412C22),
    surface = Color(0xFF1D1611),
    background = Color(0xFF1D1611),
)

@Composable
fun BrewHomeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
