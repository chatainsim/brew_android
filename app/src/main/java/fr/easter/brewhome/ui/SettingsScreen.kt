package fr.easter.brewhome.ui

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import fr.easter.brewhome.BrewViewModel

private const val WG_PERMISSION = "com.wireguard.android.permission.CONTROL_TUNNELS"

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
            .verticalScroll(rememberScrollState())
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

        Spacer(Modifier.height(8.dp))
        Text("VPN WireGuard", style = MaterialTheme.typography.titleLarge)
        Text(
            "Si le serveur ne répond pas au lancement (hors du réseau local), " +
                "l'app monte le tunnel WireGuard puis réessaie. Nécessite l'app " +
                "WireGuard avec « Autoriser les applications de contrôle à " +
                "distance » activé dans ses réglages avancés.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
        )
        val wgAuto by vm.wgAuto.collectAsState()
        val wgTunnel by vm.wgTunnel.collectAsState()
        var tunnel by remember(wgTunnel) { mutableStateOf(wgTunnel) }
        val context = LocalContext.current
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted -> vm.setWgAuto(granted) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "VPN automatique si serveur injoignable",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = wgAuto,
                onCheckedChange = { on ->
                    if (!on) {
                        vm.setWgAuto(false)
                    } else if (ContextCompat.checkSelfPermission(context, WG_PERMISSION) ==
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        vm.setWgAuto(true)
                    } else {
                        permissionLauncher.launch(WG_PERMISSION)
                    }
                },
            )
        }
        OutlinedTextField(
            value = tunnel,
            onValueChange = {
                tunnel = it
                vm.setWgTunnel(it)
            },
            label = { Text("Nom du tunnel WireGuard") },
            placeholder = { Text("maison") },
            singleLine = true,
            enabled = wgAuto,
            modifier = Modifier.fillMaxWidth(),
        )

    }
}
