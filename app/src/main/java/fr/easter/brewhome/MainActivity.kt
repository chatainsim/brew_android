package fr.easter.brewhome

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.easter.brewhome.ui.BrewHomeApp
import fr.easter.brewhome.ui.BrewHomeTheme
import fr.easter.brewhome.ui.isDarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: BrewViewModel = viewModel()
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
                BrewHomeApp(vm)
            }
        }
    }
}
