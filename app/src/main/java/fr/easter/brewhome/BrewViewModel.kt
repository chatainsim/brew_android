package fr.easter.brewhome

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.easter.brewhome.data.ApiClient
import fr.easter.brewhome.data.Beer
import fr.easter.brewhome.data.Brew
import fr.easter.brewhome.data.BrewApi
import fr.easter.brewhome.data.BrewLogEntry
import fr.easter.brewhome.data.FermReading
import fr.easter.brewhome.data.InventoryItem
import fr.easter.brewhome.data.QtyPatch
import fr.easter.brewhome.data.Recipe
import fr.easter.brewhome.data.SettingsRepository
import fr.easter.brewhome.data.StockPatch
import fr.easter.brewhome.data.TastingPut
import fr.easter.brewhome.data.Vitrine
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UiState(
    val loading: Boolean = false,
    val error: String? = null,
    val beers: List<Beer> = emptyList(),
    val recipes: List<Recipe> = emptyList(),
    val inventory: List<InventoryItem> = emptyList(),
    val brews: List<Brew> = emptyList(),
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

    fun refreshAll() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val api = api()
                val beers = api.getBeers()
                val recipes = api.getRecipes()
                val inventory = api.getInventory()
                val brews = api.getBrews()
                _state.value = UiState(
                    beers = beers, recipes = recipes,
                    inventory = inventory, brews = brews, loaded = true,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = "Connexion impossible : ${e.message ?: e.javaClass.simpleName}",
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
                val readings = api.getBrewFermentation(brewId)
                val log = api.getBrewLog(brewId)
                _brewExtras.value += brewId to BrewExtras(readings = readings, log = log)
            } catch (e: Exception) {
                _brewExtras.value += brewId to BrewExtras(
                    error = "Chargement impossible : ${e.message ?: e.javaClass.simpleName}",
                )
            }
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
