package fr.easter.brewhome.ui

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.easter.brewhome.BrewViewModel

private val themeModes = listOf(
    "system" to "Auto",
    "light" to "Clair",
    "dark" to "Sombre",
)

@Composable
fun SettingsScreen(vm: BrewViewModel, onSaved: () -> Unit) {
    val serverUrl by vm.serverUrl.collectAsState()
    var url by remember(serverUrl) { mutableStateOf(serverUrl ?: "") }
    val themeMode by vm.themeMode.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Serveur BrewHome", style = MaterialTheme.typography.titleLarge)
        Text(
            "Adresse du serveur sur ton réseau local (ou via VPN). " +
                "Exemple : http://192.168.1.50:5000",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
        )
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("URL du serveur") },
            placeholder = { Text("http://192.168.1.50:5000") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { vm.saveServerUrl(url) { onSaved() } },
            enabled = url.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Enregistrer et se connecter")
        }

        Spacer(Modifier.height(8.dp))
        Text("Apparence", style = MaterialTheme.typography.titleLarge)
        Text(
            "« Auto » suit le thème clair/sombre du téléphone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            themeModes.forEachIndexed { i, (mode, label) ->
                SegmentedButton(
                    selected = themeMode == mode,
                    onClick = { vm.setThemeMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = themeModes.size),
                ) { Text(label) }
            }
        }

        // Couleurs dynamiques Material You (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val dynamicColor by vm.dynamicColor.collectAsState()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Couleurs du téléphone", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Material You : palette dérivée de ton fond d'écran " +
                            "au lieu de l'ambre BrewHome.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                Switch(
                    checked = dynamicColor,
                    onCheckedChange = { vm.setDynamicColor(it) },
                )
            }
        }
    }
}
