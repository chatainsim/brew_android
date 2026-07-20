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
import fr.easter.brewhome.data.BrewPhoto
import fr.easter.brewhome.data.BrewhomeRepository
import fr.easter.brewhome.data.CatalogItem
import fr.easter.brewhome.data.BjcpStyle
import fr.easter.brewhome.data.Consumption
import fr.easter.brewhome.data.CostSettings
import fr.easter.brewhome.data.Spindle
import fr.easter.brewhome.data.SpindleReading
import fr.easter.brewhome.data.TempReading
import fr.easter.brewhome.data.TempSensor
import fr.easter.brewhome.data.CustomEvent
import fr.easter.brewhome.data.CustomEventPost
import fr.easter.brewhome.data.RecipePost
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
    val photos: List<BrewPhoto> = emptyList(),
    val error: String? = null,
)

/**
 * Action annulable affichée en snackbar avec un bouton « Annuler ».
 * Comparée par identité : chaque publication remplace la précédente.
 */
class UndoNotice(
    val message: String,
    /** Snackbar longue (transfert des courses : plus lourd de conséquences). */
    val long: Boolean = false,
    internal val key: String? = null,
    internal val undo: suspend () -> Unit,
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
            replaceBeer(repo.adjustBeerStock(beer, d33, d75, dKeg))
            val field = if (d33 != 0) "33" else if (d75 != 0) "75" else "keg"
            pushUndo(key = "beer-${beer.id}-$field", message = strings(R.string.undo_stock_updated)) {
                replaceBeer(repo.restoreBeerStock(beer, d33 != 0, d75 != 0, dKeg != 0.0))
            }
        }
    }

    fun setInventoryQty(item: InventoryItem, newQty: Double) {
        launchWithError(R.string.error_qty_update) {
            replaceInventory(repo.setInventoryQty(item.id, newQty))
            pushUndo(key = "inv-${item.id}", message = strings(R.string.undo_qty_updated)) {
                replaceInventory(repo.setInventoryQty(item.id, item.quantity))
            }
        }
    }

    private fun replaceBeer(updated: Beer) {
        _state.value = _state.value.copy(
            beers = _state.value.beers.map { if (it.id == updated.id) updated else it },
            error = null,
        )
    }

    private fun replaceInventory(updated: InventoryItem) {
        _state.value = _state.value.copy(
            inventory = _state.value.inventory.map { if (it.id == updated.id) updated else it },
            error = null,
        )
    }

    fun saveTasting(beerId: Int, tasting: TastingPut, onDone: () -> Unit = {}) {
        launchWithError(R.string.error_tasting_save) {
            replaceBeer(repo.saveTasting(beerId, tasting))
            onDone()
        }
    }

    // ── Annulation ────────────────────────────────────────────────────────

    private val _undo = MutableStateFlow<UndoNotice?>(null)
    val undo: StateFlow<UndoNotice?> = _undo

    /**
     * Publie une action annulable. Une rafale de ± sur la même valeur (même
     * [key]) garde l'annulation de la première pression : « Annuler » défait
     * alors toute la rafale, pas seulement le dernier cran.
     */
    private fun pushUndo(key: String?, message: String, long: Boolean = false, undo: suspend () -> Unit) {
        val burstUndo = key?.let { k -> _undo.value?.takeIf { it.key == k }?.undo }
        _undo.value = UndoNotice(message, long, key, burstUndo ?: undo)
    }

    fun performUndo(notice: UndoNotice) {
        if (_undo.value === notice) _undo.value = null
        launchWithError(R.string.error_undo) { notice.undo() }
    }

    fun dismissUndo(notice: UndoNotice) {
        if (_undo.value === notice) _undo.value = null
    }

    // ── Fiche brassin ─────────────────────────────────────────────────────

    private val _brewExtras = MutableStateFlow<Map<Int, BrewExtras>>(emptyMap())
    val brewExtras: StateFlow<Map<Int, BrewExtras>> = _brewExtras

    fun loadBrewExtras(brewId: Int) {
        if (_brewExtras.value[brewId]?.loading == true) return
        viewModelScope.launch {
            _brewExtras.value += brewId to BrewExtras(loading = true)
            try {
                val (readings, log, photos) = repo.brewExtras(brewId)
                _brewExtras.value += brewId to BrewExtras(readings = readings, log = log, photos = photos)
            } catch (e: Exception) {
                _brewExtras.value += brewId to BrewExtras(
                    error = errorMessage(R.string.error_brew_load, e),
                )
            }
        }
    }

    /** Change le statut d'un brassin (planned/in_progress/fermenting/completed). */
    fun setBrewStatus(brew: Brew, status: String) {
        launchWithError(R.string.error_brew_status) {
            repo.setBrewStatus(brew, status)
            _state.value = _state.value.copy(brews = repo.brews(), error = null)
        }
    }

    // ── Densimètres connectés ─────────────────────────────────────────────

    private val _spindles = MutableStateFlow<List<Spindle>?>(null)
    /** null = pas encore chargé ; liste vide si échec ou aucun densimètre. */
    val spindles: StateFlow<List<Spindle>?> = _spindles

    private val _spindleReadings = MutableStateFlow<Map<Int, List<SpindleReading>>>(emptyMap())
    val spindleReadings: StateFlow<Map<Int, List<SpindleReading>>> = _spindleReadings

    fun loadSpindles() {
        viewModelScope.launch {
            val list = runCatching { repo.spindles() }.getOrDefault(emptyList())
            _spindles.value = list
            // Historique récent de chaque densimètre pour la courbe
            list.forEach { sp -> loadSpindleReadings(sp.id) }
        }
    }

    fun loadSpindleReadings(id: Int) {
        viewModelScope.launch {
            runCatching { repo.spindleReadings(id) }
                .onSuccess { _spindleReadings.value += id to it }
        }
    }

    // ── Sondes de température ──────────────────────────────────────────────

    private val _tempSensors = MutableStateFlow<List<TempSensor>?>(null)
    val tempSensors: StateFlow<List<TempSensor>?> = _tempSensors

    private val _tempReadings = MutableStateFlow<Map<Int, List<TempReading>>>(emptyMap())
    val tempReadings: StateFlow<Map<Int, List<TempReading>>> = _tempReadings

    fun loadTempSensors() {
        viewModelScope.launch {
            val list = runCatching { repo.tempSensors() }.getOrDefault(emptyList())
            _tempSensors.value = list
            list.forEach { s -> loadTempReadings(s.id) }
        }
    }

    fun loadTempReadings(id: Int) {
        viewModelScope.launch {
            runCatching { repo.tempReadings(id) }
                .onSuccess { _tempReadings.value += id to it }
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

    // ── Calendrier ────────────────────────────────────────────────────────

    private val _customEvents = MutableStateFlow<List<CustomEvent>?>(null)
    /** null = pas encore chargé ; liste vide si échec (le reste du calendrier vit sans). */
    val customEvents: StateFlow<List<CustomEvent>?> = _customEvents

    fun loadCustomEvents() {
        viewModelScope.launch {
            _customEvents.value = runCatching { repo.customEvents() }.getOrDefault(emptyList())
        }
    }

    fun addCustomEvent(post: CustomEventPost, onDone: () -> Unit = {}) {
        launchWithError(R.string.error_event_save) {
            repo.addCustomEvent(post)
            _customEvents.value = repo.customEvents()
            onDone()
        }
    }

    fun deleteCustomEvent(event: CustomEvent) {
        launchWithError(R.string.error_event_delete) {
            repo.deleteCustomEvent(event.id)
            _customEvents.value = repo.customEvents()
            pushUndo(key = null, message = strings(R.string.undo_event_deleted)) {
                repo.addCustomEvent(
                    CustomEventPost(
                        title = event.title,
                        emoji = event.emoji ?: "📅",
                        eventDate = event.eventDate ?: "",
                        color = event.color ?: "#f59e0b",
                        notes = event.notes,
                        brewReminder = (event.brewReminder ?: 0) == 1,
                        brewReminderDays = event.brewReminderDays,
                        recurrence = event.recurrence,
                    ),
                )
                _customEvents.value = repo.customEvents()
            }
        }
    }

    // ── Recettes ──────────────────────────────────────────────────────────

    private val _bjcp = MutableStateFlow<List<BjcpStyle>?>(null)
    val bjcp: StateFlow<List<BjcpStyle>?> = _bjcp

    private val _costSettings = MutableStateFlow<CostSettings?>(null)
    val costSettings: StateFlow<CostSettings?> = _costSettings

    /** Styles BJCP + coûts fixes, pour les estimations de recette. */
    fun loadRecipeExtras() {
        if (_bjcp.value == null) {
            viewModelScope.launch {
                _bjcp.value = runCatching { repo.bjcpStyles() }.getOrDefault(emptyList())
            }
        }
        if (_costSettings.value == null) {
            viewModelScope.launch {
                _costSettings.value = runCatching { repo.costSettings() }.getOrDefault(CostSettings())
            }
        }
    }

    fun saveRecipe(id: Int?, post: RecipePost, onDone: () -> Unit = {}) {
        launchWithError(R.string.error_recipe_save) {
            if (id == null) repo.createRecipe(post) else repo.updateRecipe(id, post)
            // Recharge la liste : le serveur calcule des champs (batch_no, stock…)
            _state.value = _state.value.copy(recipes = repo.recipes(), error = null)
            onDone()
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

    fun deleteShoppingItem(item: ShoppingItem) {
        launchWithError(R.string.error_shopping_delete) {
            shoppingRepo.delete(item.id)
            _state.value = _state.value.copy(
                shopping = _state.value.shopping.filterNot { it.id == item.id },
                error = null,
            )
            pushUndo(key = null, message = strings(R.string.undo_item_deleted)) {
                // Re-création : l'article revient avec un nouvel id
                val restored = shoppingRepo.add(
                    ShoppingPost(item.name, item.category, item.quantity, item.unit, item.notes, item.inventoryItemId),
                )
                if ((item.checked ?: 0) == 1) shoppingRepo.setChecked(restored.id, true)
                _state.value = _state.value.copy(shopping = shoppingRepo.list())
            }
        }
    }

    /** Transfère les articles cochés dans l'inventaire, puis recharge tout. */
    fun buyCheckedShopping() {
        launchWithError(R.string.error_shopping_buy) {
            val receipt = shoppingRepo.buyChecked()
            refreshAll()
            if (receipt.count > 0) {
                pushUndo(key = null, message = strings(R.string.undo_bought), long = true) {
                    shoppingRepo.undoBuy(receipt)
                    refreshAll()
                }
            }
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
