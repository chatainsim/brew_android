package fr.easter.brewhome

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.easter.brewhome.data.ApiClient
import fr.easter.brewhome.data.Beer
import fr.easter.brewhome.data.Brew
import fr.easter.brewhome.data.BrewApi
import fr.easter.brewhome.data.InventoryItem
import fr.easter.brewhome.data.QtyPatch
import fr.easter.brewhome.data.Recipe
import fr.easter.brewhome.data.SettingsRepository
import fr.easter.brewhome.data.StockPatch
import fr.easter.brewhome.data.TastingPut
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

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
