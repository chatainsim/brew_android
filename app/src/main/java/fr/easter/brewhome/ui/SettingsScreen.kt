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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import fr.easter.brewhome.BrewViewModel
import fr.easter.brewhome.R
import fr.easter.brewhome.data.VpnController

private const val WG_PERMISSION = "${VpnController.WIREGUARD_PACKAGE}.permission.CONTROL_TUNNELS"

private val themeModes = listOf(
    "system" to R.string.theme_auto,
    "light" to R.string.theme_light,
    "dark" to R.string.theme_dark,
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
        Text(stringResource(R.string.settings_server_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.settings_server_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
        )
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text(stringResource(R.string.settings_server_url_label)) },
            placeholder = { Text(stringResource(R.string.settings_server_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { vm.saveServerUrl(url) { onSaved() } },
            enabled = url.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_save_connect))
        }
        // Fraîcheur des données affichées (cache ou dernier rafraîchissement)
        val dataAt = vm.state.collectAsState().value.dataAt
        if (dataAt != null) {
            Text(
                stringResource(R.string.settings_last_sync, fmtInstant(dataAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.settings_appearance), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.settings_appearance_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            themeModes.forEachIndexed { i, (mode, labelRes) ->
                SegmentedButton(
                    selected = themeMode == mode,
                    onClick = { vm.setThemeMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = themeModes.size),
                ) { Text(stringResource(labelRes)) }
            }
        }

        // Couleurs dynamiques Material You (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val dynamicColor by vm.dynamicColor.collectAsState()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_dynamic_title),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        stringResource(R.string.settings_dynamic_help),
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
        Text(stringResource(R.string.settings_vpn_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.settings_vpn_help),
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
                stringResource(R.string.settings_vpn_switch),
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
            label = { Text(stringResource(R.string.settings_vpn_tunnel_label)) },
            placeholder = { Text(stringResource(R.string.settings_vpn_tunnel_placeholder)) },
            singleLine = true,
            enabled = wgAuto,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
