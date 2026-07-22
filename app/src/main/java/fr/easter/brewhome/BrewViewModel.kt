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
import fr.easter.brewhome.data.BrewStep
import fr.easter.brewhome.data.BrewhomeRepository
import fr.easter.brewhome.data.CatalogItem
import fr.easter.brewhome.data.BjcpStyle
import fr.easter.brewhome.data.Consumption
import fr.easter.brewhome.data.CostSettings
import fr.easter.brewhome.data.SodaKeg
import fr.easter.brewhome.data.Spindle
import fr.easter.brewhome.data.SpindleReading
import fr.easter.brewhome.data.TempReading
import fr.easter.brewhome.data.TempSensor
import fr.easter.brewhome.data.CustomEvent
import fr.easter.brewhome.data.CustomEventPost
import fr.easter.brewhome.data.RecipePost
import fr.easter.brewhome.data.Draft
import fr.easter.brewhome.data.DraftPut
import fr.easter.brewhome.data.Trash
import fr.easter.brewhome.data.DraftsRepository
import fr.easter.brewhome.data.FermReading
import fr.easter.brewhome.data.InventoryItem
import fr.easter.brewhome.data.Recipe
import fr.easter.brewhome.data.SettingsRepository
import fr.easter.brewhome.data.ShoppingItem
import fr.easter.brewhome.data.ShoppingPost
import fr.easter.brewhome.data.ShoppingRepository
import fr.easter.brewhome.data.Snapshot
import fr.easter.brewhome.data.PendingQueue
import fr.easter.brewhome.data.PendingStockOp
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
    val steps: List<BrewStep> = emptyList(),
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
    private val pending: PendingQueue,
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

    /** Notifications locales des échéances de brassage. */
    val notifsEnabled: StateFlow<Boolean> = settings.notifsEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setNotifsEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setNotifsEnabled(enabled) }
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
        // Reconnexion : pousser d'abord les ajustements de stock faits hors ligne
        replayPending()
        val s = repo.snapshot()
        _state.value = uiStateOf(s).copy(dataAt = System.currentTimeMillis())
        withContext(io) { cache.save(s) }
    }

    private fun uiStateOf(s: Snapshot) = UiState(
        beers = s.beers, recipes = s.recipes, inventory = s.inventory,
        brews = s.brews, drafts = s.drafts, shopping = s.shopping, loaded = true,
    )

    fun adjustBeerStock(beer: Beer, d33: Int = 0, d75: Int = 0, dKeg: Double = 0.0) {
        viewModelScope.launch {
            try {
                replaceBeer(repo.adjustBeerStock(beer, d33, d75, dKeg))
                val field = if (d33 != 0) "33" else if (d75 != 0) "75" else "keg"
                pushUndo(key = "beer-${beer.id}-$field", message = strings(R.string.undo_stock_updated)) {
                    replaceBeer(repo.restoreBeerStock(beer, d33 != 0, d75 != 0, dKeg != 0.0))
                }
            } catch (e: java.io.IOException) {
                // Hors ligne : appliquer localement et mettre en file pour rejeu
                replaceBeer(optimisticStock(beer, d33, d75, dKeg))
                withContext(io) { pending.add(PendingStockOp(beer.id, d33, d75, dKeg)) }
                _state.value = _state.value.copy(offline = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = errorMessage(R.string.error_stock_update, e))
            }
        }
    }

    /** Applique un delta de stock localement (mêmes bornes que le serveur : ≥ 0). */
    private fun optimisticStock(beer: Beer, d33: Int, d75: Int, dKeg: Double): Beer = beer.copy(
        stock33 = if (d33 != 0) maxOf(0, (beer.stock33 ?: 0) + d33) else beer.stock33,
        stock75 = if (d75 != 0) maxOf(0, (beer.stock75 ?: 0) + d75) else beer.stock75,
        kegLiters = if (dKeg != 0.0) maxOf(0.0, (beer.kegLiters ?: 0.0) + dKeg) else beer.kegLiters,
    )

    /** Rejoue les ajustements de stock faits hors ligne, regroupés par bière. */
    private suspend fun replayPending() {
        val ops = withContext(io) { pending.load() }
        if (ops.isEmpty()) return
        val beers = repo.beers()
        PendingQueue.coalesce(ops).forEach { op ->
            beers.find { it.id == op.beerId }?.let {
                runCatching { repo.adjustBeerStock(it, op.d33, op.d75, op.dKeg) }
            }
        }
        withContext(io) { pending.clear() }
    }

    fun setInventoryQty(item: InventoryItem, newQty: Double) {
        launchWithError(R.string.error_qty_update) {
            replaceInventory(repo.setInventoryQty(item.id, newQty))
            pushUndo(key = "inv-${item.id}", message = strings(R.string.undo_qty_updated)) {
                replaceInventory(repo.setInventoryQty(item.id, item.quantity))
            }
        }
    }

    /** Crée (id == null) ou met à jour un article d'inventaire (nom, prix, seuil…). */
    fun saveInventoryItem(id: Int?, post: fr.easter.brewhome.data.InventoryPost, onDone: () -> Unit = {}) {
        launchWithError(R.string.error_qty_update) {
            if (id == null) repo.createInventoryItem(post) else repo.updateInventoryItem(id, post)
            _state.value = _state.value.copy(inventory = repo.inventory(), error = null)
            onDone()
        }
    }

    fun deleteInventoryItem(item: InventoryItem) {
        launchWithError(R.string.error_shopping_delete) {
            repo.deleteInventoryItem(item.id)
            _state.value = _state.value.copy(inventory = repo.inventory(), error = null)
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
                val e = repo.brewExtras(brewId)
                _brewExtras.value += brewId to BrewExtras(
                    readings = e.readings, log = e.log, photos = e.photos, steps = e.steps,
                )
            } catch (e: Exception) {
                _brewExtras.value += brewId to BrewExtras(
                    error = errorMessage(R.string.error_brew_load, e),
                )
            }
        }
    }

    /** Ajoute une note au journal de brassage puis recharge la fiche. */
    fun addBrewLog(brewId: Int, note: String, step: String?, onDone: () -> Unit = {}) {
        launchWithError(R.string.error_log_add) {
            repo.addBrewLog(brewId, note, step?.ifBlank { null })
            reloadBrewExtras(brewId)
            onDone()
        }
    }

    /** Ajoute un relevé de fermentation (densité obligatoire) puis recharge la fiche. */
    fun addFermReading(brewId: Int, gravity: Double, temperature: Double?, notes: String?, onDone: () -> Unit = {}) {
        launchWithError(R.string.error_ferm_add) {
            repo.addFermReading(brewId, gravity, temperature, notes?.ifBlank { null })
            reloadBrewExtras(brewId)
            onDone()
        }
    }
    fun deleteFermReading(brewId: Int, readingId: Int) {
        launchWithError(R.string.error_delete) {
            repo.deleteFermReading(brewId, readingId)
            reloadBrewExtras(brewId)
        }
    }
    fun deleteBrewLogEntry(brewId: Int, entryId: Int) {
        launchWithError(R.string.error_delete) {
            repo.deleteBrewLog(brewId, entryId)
            reloadBrewExtras(brewId)
        }
    }
    fun setBrewPhotoCaption(brewId: Int, photoId: Int, caption: String?, step: String?, onDone: () -> Unit = {}) {
        launchWithError(R.string.error_photo_add) {
            repo.setBrewPhotoCaption(brewId, photoId, caption?.ifBlank { null }, step)
            reloadBrewExtras(brewId)
            onDone()
        }
    }

    private val _photoUploading = MutableStateFlow(false)
    /** true pendant l'envoi d'une photo (spinner). */
    val photoUploading: StateFlow<Boolean> = _photoUploading

    fun addBrewPhoto(brewId: Int, dataUrl: String, caption: String?, onDone: () -> Unit = {}) {
        _photoUploading.value = true
        launchWithError(R.string.error_photo_add) {
            try {
                repo.addBrewPhoto(brewId, dataUrl, caption?.ifBlank { null })
                reloadBrewExtras(brewId)
                onDone()
            } finally {
                _photoUploading.value = false
            }
        }
    }

    fun deleteBrewPhoto(brewId: Int, photoId: Int) {
        launchWithError(R.string.error_photo_delete) {
            repo.deleteBrewPhoto(brewId, photoId)
            reloadBrewExtras(brewId)
        }
    }

    fun addBrewStep(brewId: Int, date: String, title: String, notes: String?, onDone: () -> Unit = {}) {
        launchWithError(R.string.error_step_add) {
            repo.addBrewStep(brewId, date, title, notes?.ifBlank { null })
            reloadBrewExtras(brewId)
            onDone()
        }
    }

    fun setStepDone(brewId: Int, stepId: Int, done: Boolean) {
        launchWithError(R.string.error_step_update) {
            repo.setStepDone(stepId, done)
            reloadBrewExtras(brewId)
        }
    }

    fun deleteBrewStep(brewId: Int, stepId: Int) {
        launchWithError(R.string.error_step_delete) {
            repo.deleteBrewStep(stepId)
            reloadBrewExtras(brewId)
        }
    }

    /** Marque un dry hop comme ajouté (déduit le stock côté serveur, une seule fois). */
    fun markDryhopDone(brewId: Int, date: String, onDone: () -> Unit = {}) {
        launchWithError(R.string.error_dryhop) {
            repo.markDryhopDone(brewId, date)
            _state.value = _state.value.copy(brews = repo.brews(), error = null)
            onDone()
        }
    }

    private suspend fun reloadBrewExtras(brewId: Int) {
        val e = repo.brewExtras(brewId)
        _brewExtras.value += brewId to BrewExtras(
            readings = e.readings, log = e.log, photos = e.photos, steps = e.steps,
        )
    }

    /** Démarre un brassin depuis une recette (déduit le stock côté serveur). */
    fun createBrew(post: fr.easter.brewhome.data.BrewCreatePost, onDone: (Int) -> Unit = {}) {
        launchWithError(R.string.error_brew_create) {
            val created = repo.createBrew(post)
            _state.value = _state.value.copy(
                brews = repo.brews(),
                inventory = repo.inventory(),
                error = null,
            )
            onDone(created.id)
        }
    }

    /** Change le statut d'un brassin (planned/in_progress/fermenting/completed). */
    fun setBrewStatus(brew: Brew, status: String) {
        launchWithError(R.string.error_brew_status) {
            repo.setBrewStatus(brew, status)
            _state.value = _state.value.copy(brews = repo.brews(), error = null)
        }
    }

    /** Enregistre les champs édités d'un brassin (mesures réelles, dates, notes…). */
    fun saveBrew(brewId: Int, put: fr.easter.brewhome.data.BrewPut, onDone: () -> Unit = {}) {
        launchWithError(R.string.error_brew_save) {
            repo.updateBrew(put, brewId)
            _state.value = _state.value.copy(brews = repo.brews(), error = null)
            onDone()
        }
    }

    /** Enregistre le lien d'album photo externe d'un brassin (vide = retirer). */
    fun setBrewPhotosUrl(brew: Brew, url: String?, onDone: () -> Unit = {}) {
        launchWithError(R.string.error_brew_status) {
            repo.setBrewPhotosUrl(brew, url?.trim()?.ifBlank { null })
            _state.value = _state.value.copy(brews = repo.brews(), error = null)
            onDone()
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

    /** Assigne (ou détache si null) un brassin à un densimètre, puis recharge. */
    fun assignSpindleBrew(spindleId: Int, brewId: Int?, onDone: () -> Unit = {}) {
        launchWithError(R.string.error_spindle_assign) {
            repo.assignSpindleBrew(spindleId, brewId)
            val list = runCatching { repo.spindles() }.getOrDefault(_spindles.value.orEmpty())
            _spindles.value = list
            onDone()
        }
    }

    fun loadSpindleReadings(id: Int, hours: Int? = null) {
        viewModelScope.launch {
            runCatching { repo.spindleReadings(id, hours) }
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

    fun loadTempReadings(id: Int, hours: Int? = null) {
        viewModelScope.launch {
            runCatching { repo.tempReadings(id, hours) }
                .onSuccess { _tempReadings.value += id to it }
        }
    }

    // ── Fûts à soda ───────────────────────────────────────────────────────

    private val _sodaKegs = MutableStateFlow<List<SodaKeg>?>(null)
    val sodaKegs: StateFlow<List<SodaKeg>?> = _sodaKegs

    fun loadSodaKegs() {
        viewModelScope.launch {
            _sodaKegs.value = runCatching { repo.sodaKegs() }.getOrDefault(emptyList())
        }
    }

    /** Change le statut et/ou le niveau d'un fût puis recharge la liste. */
    fun updateKeg(id: Int, status: String, currentLiters: Double?, onDone: () -> Unit = {}) {
        launchWithError(R.string.error_keg_update) {
            repo.updateKeg(id, status, currentLiters)
            _sodaKegs.value = repo.sodaKegs()
            onDone()
        }
    }

    /** Crée un fût puis recharge la liste. */
    fun createKeg(
        name: String,
        kegType: String?,
        volumeTotal: Double?,
        intervalMonths: Int,
        lastRevisionDate: String?,
        onDone: () -> Unit = {},
    ) {
        launchWithError(R.string.error_keg_create) {
            repo.createKeg(name, kegType, volumeTotal, intervalMonths, lastRevisionDate)
            _sodaKegs.value = repo.sodaKegs()
            onDone()
        }
    }

    /** Supprime un fût puis recharge la liste. */
    fun deleteKeg(id: Int, onDone: () -> Unit = {}) {
        launchWithError(R.string.error_delete) {
            repo.deleteKeg(id)
            _sodaKegs.value = repo.sodaKegs()
            onDone()
        }
    }

    /** Enregistre une révision effectuée aujourd'hui puis recharge la liste. */
    fun markKegRevised(id: Int, onDone: () -> Unit = {}) {
        launchWithError(R.string.error_keg_update) {
            repo.markKegRevised(id)
            _sodaKegs.value = repo.sodaKegs()
            onDone()
        }
    }

    /** Archive ou désarchive une bière puis recharge la Cave. */
    fun setBeerArchived(beer: Beer, archived: Boolean) {
        launchWithError(R.string.error_beer_archive) {
            repo.setBeerArchived(beer.id, archived)
            _state.value = _state.value.copy(beers = repo.beers(), error = null)
        }
    }

    /** Enregistre les champs édités d'une bière (nom, degré, photo, dates…). */
    fun saveBeer(id: Int, put: fr.easter.brewhome.data.BeerPut, onDone: () -> Unit = {}) {
        launchWithError(R.string.error_beer_save) {
            replaceBeer(repo.updateBeer(id, put))
            onDone()
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

    private val _aiSuggesting = MutableStateFlow(false)
    /** true pendant l'appel à l'IA (peut durer plusieurs secondes). */
    val aiSuggesting: StateFlow<Boolean> = _aiSuggesting

    /** Demande une suggestion de recette à l'IA (Gemini côté serveur). */
    fun suggestDraft(style: String?, notes: String?, volume: Double?, onResult: (fr.easter.brewhome.data.AiSuggestResult) -> Unit) {
        _aiSuggesting.value = true
        launchWithError(R.string.error_ai_suggest) {
            try {
                onResult(repo.aiDraftSuggest(style?.ifBlank { null }, notes?.ifBlank { null }, volume))
            } finally {
                _aiSuggesting.value = false
            }
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

    /** Enregistre les coûts fixes et la formule IBU, puis rafraîchit l'état. */
    fun saveCostSettings(cs: CostSettings, onDone: () -> Unit = {}) {
        launchWithError(R.string.error_settings_save) {
            repo.saveCostSettings(cs)
            _costSettings.value = repo.costSettings()
            onDone()
        }
    }

    /** Duplique une recette (nom versionné « vN ») et renvoie le nouvel id. */
    fun duplicateRecipe(recipe: fr.easter.brewhome.data.Recipe, onDone: (Int) -> Unit = {}) {
        launchWithError(R.string.error_recipe_save) {
            val names = _state.value.recipes.map { it.name }
            val newName = fr.easter.brewhome.calc.RecipeNaming.duplicateName(recipe.name, names)
            val newId = repo.duplicateRecipe(recipe, newName)
            _state.value = _state.value.copy(recipes = repo.recipes(), error = null)
            onDone(newId)
        }
    }

    /** Importe des recettes depuis du BeerXML puis recharge la liste. */
    fun importBeerXml(xml: String) {
        launchWithError(R.string.error_import) {
            val n = repo.importBeerXml(xml)
            _state.value = _state.value.copy(
                recipes = repo.recipes(),
                error = if (n == 0) strings(R.string.import_none) else null,
            )
        }
    }

    fun deleteRecipe(id: Int, onDone: () -> Unit = {}) {
        launchWithError(R.string.error_delete) {
            repo.deleteRecipe(id)
            _state.value = _state.value.copy(recipes = repo.recipes(), error = null)
            onDone()
        }
    }

    fun deleteDraft(id: Int, onDone: () -> Unit = {}) {
        launchWithError(R.string.error_delete) {
            repo.deleteDraft(id)
            _state.value = _state.value.copy(drafts = repo.drafts(), error = null)
            onDone()
        }
    }

    fun deleteBrew(id: Int, onDone: () -> Unit = {}) {
        launchWithError(R.string.error_delete) {
            repo.deleteBrew(id)
            _state.value = _state.value.copy(brews = repo.brews(), error = null)
            onDone()
        }
    }

    // ── Checklists de brassage ────────────────────────────────────────────

    private val _checklistTemplates = MutableStateFlow<List<fr.easter.brewhome.data.ChecklistTemplate>?>(null)
    val checklistTemplates: StateFlow<List<fr.easter.brewhome.data.ChecklistTemplate>?> = _checklistTemplates

    private val _brewChecklist = MutableStateFlow<fr.easter.brewhome.data.BrewChecklist?>(null)
    val brewChecklist: StateFlow<fr.easter.brewhome.data.BrewChecklist?> = _brewChecklist

    fun loadChecklist(brewId: Int) {
        _brewChecklist.value = null
        viewModelScope.launch {
            _checklistTemplates.value = runCatching { repo.checklistTemplates() }.getOrDefault(emptyList())
            _brewChecklist.value = runCatching { repo.brewChecklist(brewId) }
                .getOrDefault(fr.easter.brewhome.data.BrewChecklist())
        }
    }

    /** Enregistre l'état coché de la checklist d'un brassin. */
    fun saveChecklist(brewId: Int, templateId: Int?, checkedItems: List<String>) {
        _brewChecklist.value = fr.easter.brewhome.data.BrewChecklist(templateId, checkedItems)
        launchWithError(R.string.error_checklist_save) {
            repo.saveBrewChecklist(brewId, templateId, checkedItems)
        }
    }

    fun createChecklistTemplate(name: String, description: String?, texts: List<String>, onDone: () -> Unit = {}) {
        launchWithError(R.string.error_checklist_save) {
            repo.createChecklistTemplate(name, description, texts)
            _checklistTemplates.value = runCatching { repo.checklistTemplates() }.getOrDefault(_checklistTemplates.value.orEmpty())
            onDone()
        }
    }

    fun deleteChecklistTemplate(id: Int) {
        launchWithError(R.string.error_delete) {
            repo.deleteChecklistTemplate(id)
            _checklistTemplates.value = repo.checklistTemplates()
        }
    }

    // ── Historique des mouvements de stock ────────────────────────────────

    private val _inventoryHistory = MutableStateFlow<fr.easter.brewhome.data.InventoryHistory?>(null)
    val inventoryHistory: StateFlow<fr.easter.brewhome.data.InventoryHistory?> = _inventoryHistory

    fun loadInventoryHistory(id: Int) {
        _inventoryHistory.value = null
        viewModelScope.launch {
            _inventoryHistory.value = runCatching { repo.inventoryHistory(id) }
                .getOrDefault(fr.easter.brewhome.data.InventoryHistory())
        }
    }

    // ── Corbeille ─────────────────────────────────────────────────────────

    private val _trash = MutableStateFlow<Trash?>(null)
    val trash: StateFlow<Trash?> = _trash

    fun loadTrash() {
        viewModelScope.launch {
            _trash.value = runCatching { repo.trash() }.getOrDefault(Trash())
        }
    }

    /** Restaure un élément supprimé puis recharge la corbeille et la liste concernée. */
    fun restoreFromTrash(kind: String, id: Int) {
        launchWithError(R.string.error_restore) {
            when (kind) {
                "recipe" -> {
                    repo.restoreRecipe(id)
                    _state.value = _state.value.copy(recipes = repo.recipes())
                }
                "brew" -> {
                    repo.restoreBrew(id)
                    _state.value = _state.value.copy(brews = repo.brews())
                }
                "beer" -> {
                    repo.restoreBeer(id)
                    _state.value = _state.value.copy(beers = repo.beers())
                }
                "inventory" -> {
                    repo.restoreInventoryItem(id)
                    _state.value = _state.value.copy(inventory = repo.inventory())
                }
            }
            _trash.value = repo.trash()
        }
    }

    /** Applique le nouvel ordre des recettes (optimiste) et le persiste. */
    fun reorderRecipes(reordered: List<fr.easter.brewhome.data.Recipe>) {
        _state.value = _state.value.copy(recipes = reordered)
        launchWithError(R.string.error_recipe_save) {
            repo.reorderRecipes(reordered.map { it.id })
        }
    }

    /** Persiste le nouvel ordre des bières puis recharge (préserve les archivées). */
    fun reorderBeers(reordered: List<Beer>) {
        launchWithError(R.string.error_reorder) {
            repo.reorderBeers(reordered.map { it.id })
            _state.value = _state.value.copy(beers = repo.beers())
        }
    }

    fun reorderBrews(reordered: List<fr.easter.brewhome.data.Brew>) {
        launchWithError(R.string.error_reorder) {
            repo.reorderBrews(reordered.map { it.id })
            _state.value = _state.value.copy(brews = repo.brews())
        }
    }

    fun reorderDrafts(reordered: List<Draft>) {
        launchWithError(R.string.error_reorder) {
            repo.reorderDrafts(reordered.map { it.id })
            _state.value = _state.value.copy(drafts = repo.drafts())
        }
    }

    fun reorderKegs(reordered: List<SodaKeg>) {
        launchWithError(R.string.error_reorder) {
            repo.reorderSodaKegs(reordered.map { it.id })
            _sodaKegs.value = repo.sodaKegs()
        }
    }

    fun reorderSpindles(reordered: List<Spindle>) {
        _spindles.value = reordered
        launchWithError(R.string.error_reorder) {
            repo.reorderSpindles(reordered.map { it.id })
        }
    }

    fun reorderTempSensors(reordered: List<TempSensor>) {
        _tempSensors.value = reordered
        launchWithError(R.string.error_reorder) {
            repo.reorderTempSensors(reordered.map { it.id })
        }
    }

    fun reorderShopping(reordered: List<ShoppingItem>) {
        _state.value = _state.value.copy(shopping = reordered)
        launchWithError(R.string.error_reorder) {
            repo.reorderShopping(reordered.map { it.id })
        }
    }

    // ── Historique des versions de recette ────────────────────────────────

    private val _recipeHistory = MutableStateFlow<List<fr.easter.brewhome.data.RecipeVersion>?>(null)
    val recipeHistory: StateFlow<List<fr.easter.brewhome.data.RecipeVersion>?> = _recipeHistory

    fun loadRecipeHistory(id: Int) {
        _recipeHistory.value = null
        viewModelScope.launch {
            _recipeHistory.value = runCatching { repo.recipeHistory(id) }.getOrDefault(emptyList())
        }
    }

    /** Restaure une version passée d'une recette puis recharge la liste et l'historique. */
    fun restoreRecipeVersion(recipeId: Int, versionId: Int, onDone: () -> Unit = {}) {
        launchWithError(R.string.error_restore) {
            repo.restoreRecipeVersion(recipeId, versionId)
            _state.value = _state.value.copy(recipes = repo.recipes(), error = null)
            _recipeHistory.value = repo.recipeHistory(recipeId)
            onDone()
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

    /** Crée (id == null) ou met à jour un item du catalogue, puis recharge. */
    fun saveCatalogItem(id: Int?, post: fr.easter.brewhome.data.CatalogPost, onDone: () -> Unit = {}) {
        launchWithError(R.string.error_catalog_save) {
            if (id == null) repo.createCatalogItem(post) else repo.updateCatalogItem(id, post)
            _catalog.value = repo.catalog()
            onDone()
        }
    }

    fun deleteCatalogItem(id: Int, onDone: () -> Unit = {}) {
        launchWithError(R.string.error_delete) {
            repo.deleteCatalogItem(id)
            _catalog.value = repo.catalog()
            onDone()
        }
    }

    /** Force le rechargement du catalogue (après une modification). */
    fun reloadCatalog() {
        viewModelScope.launch {
            runCatching { repo.catalog() }.onSuccess { _catalog.value = it }
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
                    pending = PendingQueue(app.filesDir),
                    strings = app::getString,
                )
            }
        }
    }
}
