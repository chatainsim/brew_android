package fr.easter.brewhome.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import fr.easter.brewhome.BrewViewModel
import fr.easter.brewhome.R
import fr.easter.brewhome.data.ApiClient
import fr.easter.brewhome.data.VpnController

private const val WG_PERMISSION = "${VpnController.WIREGUARD_PACKAGE}.permission.CONTROL_TUNNELS"

private val themeModes = listOf(
    "system" to R.string.theme_auto,
    "light" to R.string.theme_light,
    "dark" to R.string.theme_dark,
)

private val ibuFormulas = listOf(
    "tinseth" to R.string.ibu_tinseth,
    "rager" to R.string.ibu_rager,
)

/** Affiche un nombre sans « .0 » superflu pour préremplir un champ. */
private fun fmtNum(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

@Composable
fun SettingsScreen(vm: BrewViewModel, onSaved: () -> Unit) {
    val serverUrl by vm.serverUrl.collectAsState()
    var url by remember(serverUrl) { mutableStateOf(serverUrl ?: "") }
    val themeMode by vm.themeMode.collectAsState()
    val context = LocalContext.current

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

        // La synchro vitrine (GitHub Pages) elle-même reste 100% navigateur
        // (pushVitrine() dans script_ui.html génère les pages et pousse les
        // fichiers depuis le JS de l'onglet ouvert - pas d'endpoint serveur
        // équivalent à appeler directement depuis l'app). Ce bouton ouvre donc
        // le navigateur directement sur l'onglet GitHub des Réglages web
        // (deep-link /#settings-github côté serveur) plutôt que de dupliquer
        // cette logique en Kotlin.
        if (!serverUrl.isNullOrBlank()) {
            Button(
                onClick = {
                    val target = ApiClient.normalizeUrl(serverUrl ?: "") + "#settings-github"
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target))) }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_sync_cave_github))
            }
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
        Text(stringResource(R.string.settings_notifs_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.settings_notifs_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
        )
        val notifsEnabled by vm.notifsEnabled.collectAsState()
        val notifContext = LocalContext.current
        val notifPermLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted -> vm.setNotifsEnabled(granted) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.settings_notifs_switch),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = notifsEnabled,
                onCheckedChange = { on ->
                    if (!on) {
                        vm.setNotifsEnabled(false)
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            notifContext, android.Manifest.permission.POST_NOTIFICATIONS,
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        vm.setNotifsEnabled(true)
                    }
                },
            )
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

        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.settings_costs_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.settings_costs_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
        )
        LaunchedEffect(Unit) { vm.loadRecipeExtras() }
        val costs by vm.costSettings.collectAsState()
        var gas by remember(costs) { mutableStateOf(costs?.gasPerBrew?.let { fmtNum(it) } ?: "") }
        var elec by remember(costs) { mutableStateOf(costs?.elecPerBrew?.let { fmtNum(it) } ?: "") }
        var water by remember(costs) { mutableStateOf(costs?.waterPricePerL?.let { fmtNum(it) } ?: "") }
        var ibuFormula by remember(costs) { mutableStateOf(costs?.ibuFormula ?: "tinseth") }
        OutlinedTextField(
            value = gas,
            onValueChange = { gas = it },
            label = { Text(stringResource(R.string.settings_cost_gas)) },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = elec,
            onValueChange = { elec = it },
            label = { Text(stringResource(R.string.settings_cost_elec)) },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = water,
            onValueChange = { water = it },
            label = { Text(stringResource(R.string.settings_cost_water)) },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.settings_ibu_formula),
            style = MaterialTheme.typography.bodyMedium,
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ibuFormulas.forEachIndexed { i, (value, labelRes) ->
                SegmentedButton(
                    selected = ibuFormula == value,
                    onClick = { ibuFormula = value },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = ibuFormulas.size),
                ) { Text(stringResource(labelRes)) }
            }
        }
        Button(
            onClick = {
                vm.saveCostSettings(
                    fr.easter.brewhome.data.CostSettings(
                        waterPricePerL = water.replace(',', '.').toDoubleOrNull(),
                        gasPerBrew = gas.replace(',', '.').toDoubleOrNull() ?: 0.0,
                        elecPerBrew = elec.replace(',', '.').toDoubleOrNull() ?: 0.0,
                        ibuFormula = ibuFormula,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_costs_save))
        }

        Spacer(Modifier.height(8.dp))
        val version = remember {
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull() ?: "?"
        }
        Text(
            stringResource(R.string.settings_version, version),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        // Vérifié une seule fois au démarrage de l'app (BrewViewModel.init),
        // partagé avec le badge sur l'icône Réglages de la barre du haut - un
        // échec réseau (pas de connexion, GitHub injoignable) ne montre juste
        // rien ici, ne bloque jamais le reste des réglages.
        val updateStatus by vm.updateStatus.collectAsState()
        val update = updateStatus
        if (update != null && update.updateAvailable) {
            Text(
                stringResource(R.string.settings_update_available, update.latestVersion ?: "?"),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.releaseUrl))) }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_update_button))
            }
        }
    }
}
