package fr.easter.brewhome

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.easter.brewhome.data.ApiClient
import fr.easter.brewhome.data.Beer
import fr.easter.brewhome.data.Brew
import fr.easter.brewhome.data.BrewApi
import fr.easter.brewhome.data.BrewLogEntry
import fr.easter.brewhome.data.CatalogItem
import fr.easter.brewhome.data.Consumption
import fr.easter.brewhome.data.Draft
import fr.easter.brewhome.data.DraftPut
import fr.easter.brewhome.data.FermReading
import fr.easter.brewhome.data.InventoryItem
import fr.easter.brewhome.data.QtyPatch
import fr.easter.brewhome.data.Recipe
import fr.easter.brewhome.data.SettingsRepository
import fr.easter.brewhome.data.ShoppingItem
import fr.easter.brewhome.data.ShoppingPost
import fr.easter.brewhome.data.BulkCheckPut
import fr.easter.brewhome.data.StockPatch
import fr.easter.brewhome.data.TastingPut
import fr.easter.brewhome.data.Vitrine
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class UiState(
    val loading: Boolean = false,
    val error: String? = null,
    val beers: List<Beer> = emptyList(),
    val recipes: List<Recipe> = emptyList(),
    val inventory: List<InventoryItem> = emptyList(),
    val brews: List<Brew> = emptyList(),
    val drafts: List<Draft> = emptyList(),
    val shopping: List<ShoppingItem> = emptyList(),
    val loaded: Boolean = false,
)

/** Données complémentaires d'un brassin, chargées à l'ouverture de sa fiche. */
data class BrewExtras(
    val loading: Boolean = false,
    val readings: List<FermReading> = emptyList(),
    val log: List<BrewLogEntry> = emptyList(),
    val error: String? = null,
)

class BrewViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = SettingsRepository(app)

    /** null = pas encore lu depuis DataStore, "" = jamais configuré */
    val serverUrl: StateFlow<String?> = settings.serverUrl
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** "system" | "light" | "dark" */
    val themeMode: StateFlow<String> = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, "system")

    fun setThemeMode(mode: String) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    /** Couleurs dynamiques Material You (Android 12+). */
    val dynamicColor: StateFlow<Boolean> = settings.dynamicColor
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settings.setDynamicColor(enabled) }
    }

    /** VPN WireGuard automatique quand le serveur est injoignable. */
    val wgAuto: StateFlow<Boolean> = settings.wgAuto
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val wgTunnel: StateFlow<String> = settings.wgTunnel
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun setWgAuto(enabled: Boolean) {
        viewModelScope.launch { settings.setWgAuto(enabled) }
    }

    fun setWgTunnel(name: String) {
        viewModelScope.launch { settings.setWgTunnel(name) }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private suspend fun api(): BrewApi = ApiClient.api(settings.serverUrl.first())

    /** Base URL normalisée pour construire les URLs d'images. */
    fun photoUrl(path: String?): String? {
        val base = serverUrl.value?.let { ApiClient.normalizeUrl(it) } ?: return null
        if (path == null) return null
        return base.trimEnd('/') + path
    }

    fun saveServerUrl(url: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            settings.setServerUrl(ApiClient.normalizeUrl(url))
            _state.value = UiState()
            refreshAll()
            onDone()
        }
    }

    private suspend fun loadAll() = coroutineScope {
        val api = api()
        // Tout en parallèle : le lancement ne coûte qu'un aller-retour réseau
        val beers = async { api.getBeers() }
        val recipes = async { api.getRecipes() }
        val inventory = async { api.getInventory() }
        val brews = async { api.getBrews() }
        val drafts = async { runCatching { api.getDrafts() }.getOrDefault(emptyList()) }
        val shopping = async { runCatching { api.getShoppingList() }.getOrDefault(emptyList()) }
        _state.value = UiState(
            beers = beers.await(), recipes = recipes.await(),
            inventory = inventory.await(), brews = brews.await(),
            drafts = drafts.await(), shopping = shopping.await(), loaded = true,
        )
    }

    private suspend fun serverReachable(timeoutMs: Long): Boolean =
        runCatching { withTimeout(timeoutMs) { api().getAppSettings() } }.isSuccess

    /**
     * Monte le tunnel WireGuard via l'API « remote control » de l'app
     * officielle, puis attend (max ~8 s) que le serveur réponde.
     * Nécessite côté WireGuard : Réglages → « Autoriser le contrôle à distance »,
     * et la permission CONTROL_TUNNELS accordée à BrewHome.
     */
    private suspend fun tryWireGuard(): Boolean {
        val tunnel = settings.wgTunnel.first().trim()
        if (tunnel.isEmpty()) return false
        val intent = Intent("com.wireguard.android.action.SET_TUNNEL_UP")
            .setPackage("com.wireguard.android")
            .putExtra("tunnel", tunnel)
        runCatching { getApplication<Application>().sendBroadcast(intent) }
        repeat(16) {
            delay(500)
            if (serverReachable(1500)) return true
        }
        return false
    }

    fun refreshAll() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                loadAll()
            } catch (first: Exception) {
                val retryAfterVpn = settings.wgAuto.first() && tryWireGuard()
                if (retryAfterVpn) {
                    try {
                        loadAll()
                        return@launch
                    } catch (e: Exception) {
                        // le message d'erreur d'origine reste le plus parlant
                    }
                }
                _state.value = _state.value.copy(
                    loading = false,
                    error = "Connexion impossible : ${first.message ?: first.javaClass.simpleName}",
                )
            }
        }
    }

    fun adjustBeerStock(beer: Beer, d33: Int = 0, d75: Int = 0, dKeg: Double = 0.0) {
        viewModelScope.launch {
            try {
                val patch = StockPatch(
                    stock33 = if (d33 != 0) maxOf(0, (beer.stock33 ?: 0) + d33) else null,
                    stock75 = if (d75 != 0) maxOf(0, (beer.stock75 ?: 0) + d75) else null,
                    kegLiters = if (dKeg != 0.0) maxOf(0.0, (beer.kegLiters ?: 0.0) + dKeg) else null,
                )
                val updated = api().patchBeerStock(beer.id, patch)
                _state.value = _state.value.copy(
                    beers = _state.value.beers.map { if (it.id == updated.id) updated else it },
                    error = null,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Échec de mise à jour du stock : ${e.message}")
            }
        }
    }

    fun setInventoryQty(item: InventoryItem, newQty: Double) {
        viewModelScope.launch {
            try {
                val updated = api().patchInventoryQty(item.id, QtyPatch(maxOf(0.0, newQty)))
                _state.value = _state.value.copy(
                    inventory = _state.value.inventory.map { if (it.id == updated.id) updated else it },
                    error = null,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Échec de mise à jour de la quantité : ${e.message}")
            }
        }
    }

    fun saveTasting(beerId: Int, tasting: TastingPut, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val updated = api().putBeerTasting(beerId, tasting)
                _state.value = _state.value.copy(
                    beers = _state.value.beers.map { if (it.id == updated.id) updated else it },
                    error = null,
                )
                onDone()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Échec d'enregistrement de la dégustation : ${e.message}")
            }
        }
    }

    private val _brewExtras = MutableStateFlow<Map<Int, BrewExtras>>(emptyMap())
    val brewExtras: StateFlow<Map<Int, BrewExtras>> = _brewExtras

    fun loadBrewExtras(brewId: Int) {
        if (_brewExtras.value[brewId]?.loading == true) return
        viewModelScope.launch {
            _brewExtras.value += brewId to BrewExtras(loading = true)
            try {
                val api = api()
                coroutineScope {
                    val readings = async { api.getBrewFermentation(brewId) }
                    val log = async { api.getBrewLog(brewId) }
                    _brewExtras.value += brewId to
                        BrewExtras(readings = readings.await(), log = log.await())
                }
            } catch (e: Exception) {
                _brewExtras.value += brewId to BrewExtras(
                    error = "Chargement impossible : ${e.message ?: e.javaClass.simpleName}",
                )
            }
        }
    }

    private val _catalog = MutableStateFlow<List<CatalogItem>>(emptyList())
    val catalog: StateFlow<List<CatalogItem>> = _catalog
    private var catalogLoaded = false

    /** Charge le catalogue d'ingrédients (autocomplétion de l'éditeur de brouillons). */
    fun loadCatalog() {
        if (catalogLoaded) return
        viewModelScope.launch {
            runCatching { api().getCatalog() }.onSuccess {
                _catalog.value = it
                catalogLoaded = true
            }
        }
    }

    // ── Liste de courses ──────────────────────────────────────────────────

    private suspend fun reloadShopping() {
        runCatching { api().getShoppingList() }.onSuccess {
            _state.value = _state.value.copy(shopping = it)
        }
    }

    fun toggleShoppingChecked(item: ShoppingItem) {
        viewModelScope.launch {
            val newChecked = (item.checked ?: 0) == 0
            try {
                api().bulkCheckShopping(BulkCheckPut(listOf(item.id), newChecked))
                _state.value = _state.value.copy(
                    shopping = _state.value.shopping.map {
                        if (it.id == item.id) it.copy(checked = if (newChecked) 1 else 0) else it
                    },
                    error = null,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Échec de la coche : ${e.message}")
            }
        }
    }

    fun addShoppingItem(post: ShoppingPost) {
        viewModelScope.launch {
            try {
                api().createShoppingItem(post)
                reloadShopping()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Échec de l'ajout : ${e.message}")
            }
        }
    }

    fun deleteShoppingItem(id: Int) {
        viewModelScope.launch {
            try {
                api().deleteShoppingItem(id)
                _state.value = _state.value.copy(
                    shopping = _state.value.shopping.filterNot { it.id == id },
                    error = null,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Échec de la suppression : ${e.message}")
            }
        }
    }

    /** Transfère les articles cochés dans l'inventaire, puis recharge tout. */
    fun buyCheckedShopping() {
        viewModelScope.launch {
            try {
                api().buyShoppingItems()
                refreshAll()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Échec du transfert : ${e.message}")
            }
        }
    }

    /** Crée (id == null) ou met à jour un brouillon. */
    fun saveDraft(id: Int?, draft: DraftPut, onDone: (Draft) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val saved = if (id == null) api().createDraft(draft)
                    else api().updateDraft(id, draft)
                _state.value = _state.value.copy(
                    drafts = if (id == null) listOf(saved) + _state.value.drafts
                        else _state.value.drafts.map { if (it.id == saved.id) saved else it },
                    error = null,
                )
                onDone(saved)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = "Échec d'enregistrement du brouillon : ${e.message}",
                )
            }
        }
    }

    private val _consumption = MutableStateFlow<Consumption?>(null)
    val consumption: StateFlow<Consumption?> = _consumption

    /** Charge les stats de consommation (écran Statistiques). */
    fun loadConsumption() {
        viewModelScope.launch {
            runCatching { api().getConsumption() }
                .onSuccess { _consumption.value = it }
        }
    }

    private var vitrineUrl: String? = null

    /**
     * Ouvre la cave en ligne : la vitrine GitHub Pages si elle est configurée
     * sur le serveur (réglage `gh_vitrine_targets`), sinon la page Cave de
     * l'interface web.
     */
    fun openCaveOnline(open: (String) -> Unit) {
        viewModelScope.launch {
            val url = vitrineUrl ?: runCatching {
                val targets = api().getAppSettings()["gh_vitrine_targets"]
                    ?.jsonPrimitive?.contentOrNull
                Vitrine.pagesUrl(targets)
            }.getOrNull()?.also { vitrineUrl = it }
            open(url ?: (ApiClient.normalizeUrl(serverUrl.value ?: "") + "#cave"))
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
