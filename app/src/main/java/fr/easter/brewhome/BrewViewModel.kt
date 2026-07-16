package fr.easter.brewhome

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.easter.brewhome.calc.StockCheck
import fr.easter.brewhome.data.ApiClient
import fr.easter.brewhome.data.AppSettings
import fr.easter.brewhome.data.Beer
import fr.easter.brewhome.data.Brew
import fr.easter.brewhome.data.BrewApi
import fr.easter.brewhome.data.BrewLogEntry
import fr.easter.brewhome.data.BrewhomeRepository
import fr.easter.brewhome.data.CatalogItem
import fr.easter.brewhome.data.Consumption
import fr.easter.brewhome.data.Draft
import fr.easter.brewhome.data.DraftPut
import fr.easter.brewhome.data.DraftsRepository
import fr.easter.brewhome.data.FermReading
import fr.easter.brewhome.data.InventoryItem
import fr.easter.brewhome.data.Recipe
import fr.easter.brewhome.data.SettingsRepository
import fr.easter.brewhome.data.ShoppingItem
import fr.easter.brewhome.data.ShoppingPost
import fr.easter.brewhome.data.ShoppingRepository
import fr.easter.brewhome.data.Snapshot
import fr.easter.brewhome.data.SnapshotCache
import fr.easter.brewhome.data.TastingPut
import fr.easter.brewhome.data.VpnController
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    /** Serveur injoignable : les données affichées viennent du cache disque. */
    val offline: Boolean = false,
    /** Date (epoch ms) des données affichées, pour le bandeau hors ligne. */
    val dataAt: Long? = null,
)

/** Données complémentaires d'un brassin, chargées à l'ouverture de sa fiche. */
data class BrewExtras(
    val loading: Boolean = false,
    val readings: List<FermReading> = emptyList(),
    val log: List<BrewLogEntry> = emptyList(),
    val error: String? = null,
)

/**
 * Façade UI : porte l'état des écrans et traduit les erreurs en messages.
 * Les accès serveur vivent dans les dépôts de `data/` (BrewhomeRepository,
 * DraftsRepository, ShoppingRepository, VpnController).
 *
 * Les dépendances sont injectées pour permettre les tests JVM (voir [Factory]
 * pour le câblage réel : DataStore, WireGuard, cache disque, ressources).
 */
class BrewViewModel(
    private val settings: AppSettings,
    apiProvider: suspend () -> BrewApi,
    private val vpn: VpnController,
    private val cache: SnapshotCache,
    private val strings: (Int) -> String,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val repo = BrewhomeRepository(apiProvider)
    private val draftsRepo = DraftsRepository(apiProvider)
    private val shoppingRepo = ShoppingRepository(apiProvider)

    // ── Réglages ──────────────────────────────────────────────────────────

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

    fun saveServerUrl(url: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            settings.setServerUrl(ApiClient.normalizeUrl(url))
            // Les données en cache viennent peut-être d'un autre serveur
            withContext(io) { cache.clear() }
            _state.value = UiState()
            refreshAll()
            onDone()
        }
    }

    // ── Données principales ───────────────────────────────────────────────

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    /** Base URL normalisée pour construire les URLs d'images. */
    fun photoUrl(path: String?): String? {
        val base = serverUrl.value?.let { ApiClient.normalizeUrl(it) } ?: return null
        if (path == null) return null
        return base.trimEnd('/') + path
    }

    fun refreshAll() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            // Premier lancement : afficher tout de suite le dernier instantané
            // connu pendant que le rafraîchissement continue en arrière-plan.
            if (!_state.value.loaded) {
                withContext(io) { cache.load() }?.let { c ->
                    _state.value = uiStateOf(c.snapshot).copy(loading = true, dataAt = c.savedAt)
                }
            }
            try {
                applySnapshot()
            } catch (first: Exception) {
                // Serveur injoignable : tentative via le tunnel WireGuard
                val retryAfterVpn = vpn.connectIfConfigured { repo.reachable(it) }
                if (retryAfterVpn) {
                    try {
                        applySnapshot()
                        return@launch
                    } catch (e: Exception) {
                        // le message d'erreur d'origine reste le plus parlant
                    }
                }
                _state.value = _state.value.copy(
                    loading = false,
                    // les données du cache restent affichées, avec le bandeau hors ligne
                    offline = _state.value.loaded,
                    error = errorMessage(R.string.error_connect, first),
                )
            }
        }
    }

    private suspend fun applySnapshot() {
        val s = repo.snapshot()
        _state.value = uiStateOf(s).copy(dataAt = System.currentTimeMillis())
        withContext(io) { cache.save(s) }
    }

    private fun uiStateOf(s: Snapshot) = UiState(
        beers = s.beers, recipes = s.recipes, inventory = s.inventory,
        brews = s.brews, drafts = s.drafts, shopping = s.shopping, loaded = true,
    )

    fun adjustBeerStock(beer: Beer, d33: Int = 0, d75: Int = 0, dKeg: Double = 0.0) {
        launchWithError(R.string.error_stock_update) {
            val updated = repo.adjustBeerStock(beer, d33, d75, dKeg)
            _state.value = _state.value.copy(
                beers = _state.value.beers.map { if (it.id == updated.id) updated else it },
                error = null,
            )
        }
    }

    fun setInventoryQty(item: InventoryItem, newQty: Double) {
        launchWithError(R.string.error_qty_update) {
            val updated = repo.setInventoryQty(item.id, newQty)
            _state.value = _state.value.copy(
                inventory = _state.value.inventory.map { if (it.id == updated.id) updated else it },
                error = null,
            )
        }
    }

    fun saveTasting(beerId: Int, tasting: TastingPut, onDone: () -> Unit = {}) {
        launchWithError(R.string.error_tasting_save) {
            val updated = repo.saveTasting(beerId, tasting)
            _state.value = _state.value.copy(
                beers = _state.value.beers.map { if (it.id == updated.id) updated else it },
                error = null,
            )
            onDone()
        }
    }

    // ── Fiche brassin ─────────────────────────────────────────────────────

    private val _brewExtras = MutableStateFlow<Map<Int, BrewExtras>>(emptyMap())
    val brewExtras: StateFlow<Map<Int, BrewExtras>> = _brewExtras

    fun loadBrewExtras(brewId: Int) {
        if (_brewExtras.value[brewId]?.loading == true) return
        viewModelScope.launch {
            _brewExtras.value += brewId to BrewExtras(loading = true)
            try {
                val (readings, log) = repo.brewExtras(brewId)
                _brewExtras.value += brewId to BrewExtras(readings = readings, log = log)
            } catch (e: Exception) {
                _brewExtras.value += brewId to BrewExtras(
                    error = errorMessage(R.string.error_brew_load, e),
                )
            }
        }
    }

    // ── Statistiques ──────────────────────────────────────────────────────

    private val _consumption = MutableStateFlow<Consumption?>(null)
    val consumption: StateFlow<Consumption?> = _consumption

    /** Charge les stats de consommation (écran Statistiques). */
    fun loadConsumption() {
        viewModelScope.launch {
            runCatching { repo.consumption() }
                .onSuccess { _consumption.value = it }
        }
    }

    // ── Brouillons et catalogue ───────────────────────────────────────────

    private val _catalog = MutableStateFlow<List<CatalogItem>>(emptyList())
    val catalog: StateFlow<List<CatalogItem>> = _catalog
    private var catalogLoaded = false

    /** Charge le catalogue d'ingrédients (autocomplétion de l'éditeur de brouillons). */
    fun loadCatalog() {
        if (catalogLoaded) return
        viewModelScope.launch {
            runCatching { draftsRepo.catalog() }.onSuccess {
                _catalog.value = it
                catalogLoaded = true
            }
        }
    }

    /** Crée (id == null) ou met à jour un brouillon. */
    fun saveDraft(id: Int?, draft: DraftPut, onDone: (Draft) -> Unit = {}) {
        launchWithError(R.string.error_draft_save) {
            val saved = draftsRepo.save(id, draft)
            _state.value = _state.value.copy(
                drafts = if (id == null) listOf(saved) + _state.value.drafts
                    else _state.value.drafts.map { if (it.id == saved.id) saved else it },
                error = null,
            )
            onDone(saved)
        }
    }

    // ── Liste de courses ──────────────────────────────────────────────────

    fun toggleShoppingChecked(item: ShoppingItem) {
        launchWithError(R.string.error_shopping_check) {
            val newChecked = (item.checked ?: 0) == 0
            shoppingRepo.setChecked(item.id, newChecked)
            _state.value = _state.value.copy(
                shopping = _state.value.shopping.map {
                    if (it.id == item.id) it.copy(checked = if (newChecked) 1 else 0) else it
                },
                error = null,
            )
        }
    }

    /** Ajoute d'un coup les ingrédients manquants d'une recette aux courses. */
    fun addNeedsToShopping(needs: List<StockCheck.Need>) {
        launchWithError(R.string.error_shopping_add) {
            try {
                needs.forEach {
                    shoppingRepo.add(ShoppingPost(it.name, it.category, it.quantity, it.unit))
                }
            } finally {
                // Même en cas d'échec partiel, refléter ce que le serveur a accepté
                runCatching { shoppingRepo.list() }
                    .onSuccess { _state.value = _state.value.copy(shopping = it) }
            }
            _state.value = _state.value.copy(error = null)
        }
    }

    fun addShoppingItem(post: ShoppingPost) {
        launchWithError(R.string.error_shopping_add) {
            shoppingRepo.add(post)
            _state.value = _state.value.copy(shopping = shoppingRepo.list(), error = null)
        }
    }

    fun deleteShoppingItem(id: Int) {
        launchWithError(R.string.error_shopping_delete) {
            shoppingRepo.delete(id)
            _state.value = _state.value.copy(
                shopping = _state.value.shopping.filterNot { it.id == id },
                error = null,
            )
        }
    }

    /** Transfère les articles cochés dans l'inventaire, puis recharge tout. */
    fun buyCheckedShopping() {
        launchWithError(R.string.error_shopping_buy) {
            shoppingRepo.buyChecked()
            refreshAll()
        }
    }

    // ── Divers ────────────────────────────────────────────────────────────

    private var vitrineUrl: String? = null

    /**
     * Ouvre la cave en ligne : la vitrine GitHub Pages si elle est configurée
     * sur le serveur, sinon la page Cave de l'interface web.
     */
    fun openCaveOnline(open: (String) -> Unit) {
        viewModelScope.launch {
            val url = vitrineUrl ?: repo.vitrineUrl()?.also { vitrineUrl = it }
            open(url ?: (ApiClient.normalizeUrl(serverUrl.value ?: "") + "#cave"))
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    /** Lance une action et route son échec vers la snackbar d'erreur. */
    private fun launchWithError(@StringRes prefixRes: Int, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = errorMessage(prefixRes, e))
            }
        }
    }

    /** « Préfixe : détail », avec le nom de l'exception quand elle n'a pas de message. */
    private fun errorMessage(@StringRes prefixRes: Int, e: Exception): String =
        "${strings(prefixRes)} : ${e.message ?: e.javaClass.simpleName}"

    companion object {
        /** Câblage réel : DataStore, broadcast WireGuard, cache disque, ressources. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                val settings = SettingsRepository(app)
                BrewViewModel(
                    settings = settings,
                    apiProvider = { ApiClient.api(settings.serverUrl.first()) },
                    vpn = VpnController(settings, VpnController.broadcaster(app)),
                    cache = SnapshotCache(app.filesDir),
                    strings = app::getString,
                )
            }
        }
    }
}
