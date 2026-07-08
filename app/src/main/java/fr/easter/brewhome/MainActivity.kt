package fr.easter.brewhome

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import fr.easter.brewhome.ui.BrewHomeApp
import fr.easter.brewhome.ui.BrewHomeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BrewHomeTheme {
                BrewHomeApp()
            }
        }
    }
}
