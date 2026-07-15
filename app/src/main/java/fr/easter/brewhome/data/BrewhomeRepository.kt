package fr.easter.brewhome.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Instantané complet des données du serveur, chargé en parallèle. */
data class Snapshot(
    val beers: List<Beer>,
    val recipes: List<Recipe>,
    val inventory: List<InventoryItem>,
    val brews: List<Brew>,
    val drafts: List<Draft>,
    val shopping: List<ShoppingItem>,
)

/** Accès aux données principales : cave, recettes, inventaire, brassins. */
class BrewhomeRepository(private val api: suspend () -> BrewApi) {

    suspend fun snapshot(): Snapshot = coroutineScope {
        val api = api()
        // Tout en parallèle : le lancement ne coûte qu'un aller-retour réseau
        val beers = async { api.getBeers() }
        val recipes = async { api.getRecipes() }
        val inventory = async { api.getInventory() }
        val brews = async { api.getBrews() }
        val drafts = async { runCatching { api.getDrafts() }.getOrDefault(emptyList()) }
        val shopping = async { runCatching { api.getShoppingList() }.getOrDefault(emptyList()) }
        Snapshot(
            beers.await(), recipes.await(), inventory.await(),
            brews.await(), drafts.await(), shopping.await(),
        )
    }

    suspend fun brewExtras(brewId: Int): Pair<List<FermReading>, List<BrewLogEntry>> =
        coroutineScope {
            val api = api()
            val readings = async { api.getBrewFermentation(brewId) }
            val log = async { api.getBrewLog(brewId) }
            readings.await() to log.await()
        }

    suspend fun adjustBeerStock(beer: Beer, d33: Int, d75: Int, dKeg: Double): Beer {
        val patch = StockPatch(
            stock33 = if (d33 != 0) maxOf(0, (beer.stock33 ?: 0) + d33) else null,
            stock75 = if (d75 != 0) maxOf(0, (beer.stock75 ?: 0) + d75) else null,
            kegLiters = if (dKeg != 0.0) maxOf(0.0, (beer.kegLiters ?: 0.0) + dKeg) else null,
        )
        return api().patchBeerStock(beer.id, patch)
    }

    suspend fun setInventoryQty(itemId: Int, qty: Double): InventoryItem =
        api().patchInventoryQty(itemId, QtyPatch(maxOf(0.0, qty)))

    suspend fun saveTasting(beerId: Int, tasting: TastingPut): Beer =
        api().putBeerTasting(beerId, tasting)

    suspend fun consumption(): Consumption = api().getConsumption()

    /** Le serveur répond-il ? (utilisé au lancement et par le VPN auto) */
    suspend fun reachable(timeoutMs: Long): Boolean =
        runCatching { withTimeout(timeoutMs) { api().getAppSettings() } }.isSuccess

    /** URL GitHub Pages de la vitrine, lue dans les réglages du serveur. */
    suspend fun vitrineUrl(): String? = runCatching {
        val targets = api().getAppSettings()["gh_vitrine_targets"]
            ?.jsonPrimitive?.contentOrNull
        Vitrine.pagesUrl(targets)
    }.getOrNull()
}

/** Brouillons de recettes et catalogue d'ingrédients (autocomplétion). */
class DraftsRepository(private val api: suspend () -> BrewApi) {

    suspend fun save(id: Int?, draft: DraftPut): Draft =
        if (id == null) api().createDraft(draft) else api().updateDraft(id, draft)

    suspend fun catalog(): List<CatalogItem> = api().getCatalog()
}

/** Liste de courses. */
class ShoppingRepository(private val api: suspend () -> BrewApi) {

    suspend fun list(): List<ShoppingItem> = api().getShoppingList()

    suspend fun setChecked(id: Int, checked: Boolean) {
        api().bulkCheckShopping(BulkCheckPut(listOf(id), checked))
    }

    suspend fun add(post: ShoppingPost): ShoppingItem = api().createShoppingItem(post)

    suspend fun delete(id: Int) {
        api().deleteShoppingItem(id)
    }

    /** Transfère les articles cochés dans l'inventaire (POST /buy). */
    suspend fun buyChecked() {
        api().buyShoppingItems()
    }
}
