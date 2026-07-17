package fr.easter.brewhome

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.easter.brewhome.ui.BrewHomeApp
import fr.easter.brewhome.ui.BrewHomeTheme
import fr.easter.brewhome.ui.isDarkTheme

class MainActivity : ComponentActivity() {

    /** Route demandée par un raccourci d'app (appui long sur l'icône du lanceur). */
    private val shortcutRoute = mutableStateOf<String?>(null)

    private fun routeOf(intent: Intent?): String? = when (intent?.action) {
        "fr.easter.brewhome.SHORTCUT_SHOPPING" -> "shopping"
        "fr.easter.brewhome.SHORTCUT_INVENTORY" -> "inventory"
        "fr.easter.brewhome.SHORTCUT_STATS" -> "stats"
        else -> null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        shortcutRoute.value = routeOf(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        shortcutRoute.value = routeOf(intent)
        enableEdgeToEdge()
        setContent {
            val vm: BrewViewModel = viewModel(factory = BrewViewModel.Factory)
            val mode by vm.themeMode.collectAsState()
            val dynamic by vm.dynamicColor.collectAsState()
            val dark = isDarkTheme(mode)
            // Icônes des barres système lisibles quand le thème est forcé
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                        else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                        else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                )
            }
            BrewHomeTheme(darkTheme = dark, dynamicColor = dynamic) {
                BrewHomeApp(
                    vm,
                    shortcutRoute = shortcutRoute.value,
                    onShortcutHandled = { shortcutRoute.value = null },
                )
            }
        }
    }
}
