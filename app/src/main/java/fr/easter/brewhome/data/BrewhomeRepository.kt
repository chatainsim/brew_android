package fr.easter.brewhome.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Instantané complet des données du serveur, chargé en parallèle. */
@Serializable
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

    suspend fun brewExtras(brewId: Int): Triple<List<FermReading>, List<BrewLogEntry>, List<BrewPhoto>> =
        coroutineScope {
            val api = api()
            val readings = async { api.getBrewFermentation(brewId) }
            val log = async { api.getBrewLog(brewId) }
            // Endpoint plus récent : fiche sans photos si le serveur ne l'a pas
            val photos = async { runCatching { api.getBrewPhotos(brewId) }.getOrDefault(emptyList()) }
            Triple(readings.await(), log.await(), photos.await())
        }

    suspend fun adjustBeerStock(beer: Beer, d33: Int, d75: Int, dKeg: Double): Beer {
        val patch = StockPatch(
            stock33 = if (d33 != 0) maxOf(0, (beer.stock33 ?: 0) + d33) else null,
            stock75 = if (d75 != 0) maxOf(0, (beer.stock75 ?: 0) + d75) else null,
            kegLiters = if (dKeg != 0.0) maxOf(0.0, (beer.kegLiters ?: 0.0) + dKeg) else null,
        )
        return api().patchBeerStock(beer.id, patch)
    }

    /** Remet le stock d'une bière à ses valeurs d'avant ajustement (annulation). */
    suspend fun restoreBeerStock(beer: Beer, r33: Boolean, r75: Boolean, rKeg: Boolean): Beer =
        api().patchBeerStock(
            beer.id,
            StockPatch(
                stock33 = if (r33) beer.stock33 ?: 0 else null,
                stock75 = if (r75) beer.stock75 ?: 0 else null,
                kegLiters = if (rKeg) beer.kegLiters ?: 0.0 else null,
            ),
        )

    suspend fun setInventoryQty(itemId: Int, qty: Double): InventoryItem =
        api().patchInventoryQty(itemId, QtyPatch(maxOf(0.0, qty)))

    suspend fun saveTasting(beerId: Int, tasting: TastingPut): Beer =
        api().putBeerTasting(beerId, tasting)

    suspend fun consumption(): Consumption = api().getConsumption()

    suspend fun customEvents(): List<CustomEvent> = api().getCustomEvents()

    suspend fun addCustomEvent(post: CustomEventPost): CustomEvent = api().createCustomEvent(post)

    suspend fun deleteCustomEvent(id: Int) {
        api().deleteCustomEvent(id)
    }

    suspend fun recipes(): List<Recipe> = api().getRecipes()

    suspend fun bjcpStyles(): List<BjcpStyle> = api().getBjcpStyles()

    /** Prix eau/gaz/électricité et formule IBU depuis /api/app-settings. */
    suspend fun costSettings(): CostSettings {
        val settings = api().getAppSettings()
        // Les clés "water" et "energy" contiennent du JSON sérialisé en chaîne
        fun nested(key: String): kotlinx.serialization.json.JsonObject? =
            (settings[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.let {
                runCatching {
                    kotlinx.serialization.json.Json.parseToJsonElement(it)
                        as? kotlinx.serialization.json.JsonObject
                }.getOrNull()
            }
        fun num(obj: kotlinx.serialization.json.JsonObject?, key: String): Double? =
            (obj?.get(key) as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
        val water = nested("water")
        val energy = nested("energy")
        return CostSettings(
            waterPricePerL = num(water, "price"),
            gasPerBrew = num(energy, "gas_per_brew") ?: 0.0,
            elecPerBrew = num(energy, "elec_per_brew") ?: 0.0,
            ibuFormula = (energy?.get("ibu_formula") as? kotlinx.serialization.json.JsonPrimitive)
                ?.contentOrNull ?: "tinseth",
        )
    }

    suspend fun createRecipe(post: RecipePost): Recipe = api().createRecipe(post)

    /**
     * Mise à jour d'une recette : le PUT du serveur écrase toutes les colonnes,
     * donc on repart du JSON brut de la recette et on n'y remplace que les
     * champs édités par l'app — mash_ratio, efficacité, historique d'eau…
     * restent intacts.
     */
    suspend fun updateRecipe(id: Int, edited: RecipePost) {
        val api = api()
        val raw = api.getRecipeRaw(id)
        val editedJson = mergeJson.encodeToJsonElement(RecipePost.serializer(), edited)
            as kotlinx.serialization.json.JsonObject
        val body = kotlinx.serialization.json.buildJsonObject {
            raw.forEach { (k, v) -> put(k, v) }
            editedJson.forEach { (k, v) ->
                // brew_date/draft_id ne sont posés qu'à la création depuis un
                // brouillon : null ici = « garder la valeur existante »
                val keepRaw = v is kotlinx.serialization.json.JsonNull &&
                    (k == "brew_date" || k == "draft_id")
                if (!keepRaw) put(k, v)
            }
        }
        api.updateRecipe(id, body)
    }

    private companion object {
        // explicitNulls : un champ vidé (style, notes…) doit écraser la valeur
        // du JSON brut par null, pas être omis
        val mergeJson = kotlinx.serialization.json.Json { explicitNulls = true }
    }

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

    /** Transfère les articles cochés dans l'inventaire ; le reçu permet d'annuler. */
    suspend fun buyChecked(): BuyResult = api().buyShoppingItems()

    /** Annule un transfert récent : rétablit la liste et l'inventaire. */
    suspend fun undoBuy(receipt: BuyResult) {
        api().undoBuyShopping(UndoBuyPost(receipt.boughtIds, receipt.invChanges))
    }
}
