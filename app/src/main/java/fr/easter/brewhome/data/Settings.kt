package fr.easter.brewhome.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")
private val KEY_SERVER_URL = stringPreferencesKey("server_url")
private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
private val KEY_WG_AUTO = booleanPreferencesKey("wg_auto")
private val KEY_WG_TUNNEL = stringPreferencesKey("wg_tunnel")

class SettingsRepository(private val context: Context) {
    val serverUrl: Flow<String> = context.dataStore.data.map { it[KEY_SERVER_URL] ?: "" }

    /** "system" (défaut) | "light" | "dark" */
    val themeMode: Flow<String> = context.dataStore.data.map { it[KEY_THEME_MODE] ?: "system" }

    suspend fun setServerUrl(url: String) {
        context.dataStore.edit { it[KEY_SERVER_URL] = url }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode }
    }

    /** Couleurs dynamiques Material You (Android 12+), désactivé par défaut. */
    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { it[KEY_DYNAMIC_COLOR] ?: false }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    /** Monter automatiquement le tunnel WireGuard si le serveur est injoignable. */
    val wgAuto: Flow<Boolean> = context.dataStore.data.map { it[KEY_WG_AUTO] ?: false }
    val wgTunnel: Flow<String> = context.dataStore.data.map { it[KEY_WG_TUNNEL] ?: "" }

    suspend fun setWgAuto(enabled: Boolean) {
        context.dataStore.edit { it[KEY_WG_AUTO] = enabled }
    }

    suspend fun setWgTunnel(name: String) {
        context.dataStore.edit { it[KEY_WG_TUNNEL] = name }
    }
}
