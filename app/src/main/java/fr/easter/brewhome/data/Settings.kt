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
private val KEY_NOTIFS = booleanPreferencesKey("notifs_enabled")

/** Réglages de l'app, abstraits pour pouvoir tester le ViewModel sans DataStore. */
interface AppSettings {
    val serverUrl: Flow<String>

    /** "system" (défaut) | "light" | "dark" */
    val themeMode: Flow<String>

    /** Couleurs dynamiques Material You (Android 12+), désactivé par défaut. */
    val dynamicColor: Flow<Boolean>

    /** Monter automatiquement le tunnel WireGuard si le serveur est injoignable. */
    val wgAuto: Flow<Boolean>
    val wgTunnel: Flow<String>

    /** Notifications locales des échéances de brassage (dry hop, fin de ferm…). */
    val notifsEnabled: Flow<Boolean>

    suspend fun setServerUrl(url: String)
    suspend fun setThemeMode(mode: String)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setWgAuto(enabled: Boolean)
    suspend fun setWgTunnel(name: String)
    suspend fun setNotifsEnabled(enabled: Boolean)
}

class SettingsRepository(private val context: Context) : AppSettings {
    override val serverUrl: Flow<String> = context.dataStore.data.map { it[KEY_SERVER_URL] ?: "" }
    override val themeMode: Flow<String> = context.dataStore.data.map { it[KEY_THEME_MODE] ?: "system" }
    override val dynamicColor: Flow<Boolean> = context.dataStore.data.map { it[KEY_DYNAMIC_COLOR] ?: false }
    override val wgAuto: Flow<Boolean> = context.dataStore.data.map { it[KEY_WG_AUTO] ?: false }
    override val wgTunnel: Flow<String> = context.dataStore.data.map { it[KEY_WG_TUNNEL] ?: "" }
    override val notifsEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_NOTIFS] ?: false }

    override suspend fun setServerUrl(url: String) {
        context.dataStore.edit { it[KEY_SERVER_URL] = url }
    }

    override suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    override suspend fun setWgAuto(enabled: Boolean) {
        context.dataStore.edit { it[KEY_WG_AUTO] = enabled }
    }

    override suspend fun setWgTunnel(name: String) {
        context.dataStore.edit { it[KEY_WG_TUNNEL] = name }
    }

    override suspend fun setNotifsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_NOTIFS] = enabled }
    }
}
