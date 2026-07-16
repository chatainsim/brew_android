package fr.easter.brewhome.data

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * Montée automatique du tunnel WireGuard via l'API « remote control » de
 * l'app officielle. Nécessite côté WireGuard : Réglages → « Autoriser les
 * applications de contrôle à distance », et la permission CONTROL_TUNNELS
 * accordée à BrewHome (demandée à l'activation de l'option).
 */
class VpnController(
    private val settings: AppSettings,
    private val sendTunnelUp: (tunnel: String) -> Unit,
) {
    /**
     * Si l'option est activée et un tunnel configuré : envoie SET_TUNNEL_UP
     * puis attend (max ~8 s) que [reachable] passe au vert.
     */
    suspend fun connectIfConfigured(reachable: suspend (timeoutMs: Long) -> Boolean): Boolean {
        if (!settings.wgAuto.first()) return false
        val tunnel = settings.wgTunnel.first().trim()
        if (tunnel.isEmpty()) return false
        sendTunnelUp(tunnel)
        repeat(16) {
            delay(500)
            if (reachable(1500)) return true
        }
        return false
    }

    companion object {
        const val WIREGUARD_PACKAGE = "com.wireguard.android"
        const val ACTION_SET_TUNNEL_UP = "com.wireguard.android.action.SET_TUNNEL_UP"

        /** Émetteur réel : broadcast vers l'app WireGuard officielle. */
        fun broadcaster(context: Context): (String) -> Unit = { tunnel ->
            val intent = Intent(ACTION_SET_TUNNEL_UP)
                .setPackage(WIREGUARD_PACKAGE)
                .putExtra("tunnel", tunnel)
            runCatching { context.sendBroadcast(intent) }
        }
    }
}
