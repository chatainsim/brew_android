package fr.easter.brewhome.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

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

    /** Regroupe mesures, journal, photos et étapes d'un brassin. */
    data class BrewExtrasData(
        val readings: List<FermReading>,
        val log: List<BrewLogEntry>,
        val photos: List<BrewPhoto>,
        val steps: List<BrewStep>,
    )

    suspend fun brewExtras(brewId: Int): BrewExtrasData = coroutineScope {
        val api = api()
        val readings = async { api.getBrewFermentation(brewId) }
        val log = async { api.getBrewLog(brewId) }
        // Endpoints plus récents : dégradés en liste vide si le serveur ne les a pas
        val photos = async { runCatching { api.getBrewPhotos(brewId) }.getOrDefault(emptyList()) }
        val steps = async { runCatching { api.getBrewSteps(brewId) }.getOrDefault(emptyList()) }
        BrewExtrasData(readings.await(), log.await(), photos.await(), steps.await())
    }

    suspend fun addBrewLog(brewId: Int, note: String, step: String?) {
        api().addBrewLog(brewId, BrewLogPost(ts = nowTimestamp(), step = step, note = note))
    }

    suspend fun addFermReading(brewId: Int, gravity: Double, temperature: Double?, notes: String?) {
        api().addBrewFermentation(
            brewId,
            FermReadingPost(recordedAt = nowTimestamp(), gravity = gravity, temperature = temperature, notes = notes),
        )
    }

    suspend fun addBrewPhoto(brewId: Int, dataUrl: String, caption: String?) {
        api().addBrewPhoto(brewId, BrewPhotoPost(photo = dataUrl, caption = caption))
    }

    suspend fun deleteBrewPhoto(brewId: Int, photoId: Int) {
        api().deleteBrewPhoto(brewId, photoId)
    }

    suspend fun addBrewStep(brewId: Int, date: String, title: String, notes: String?): BrewStep =
        api().addBrewStep(brewId, BrewStepPost(scheduledDate = date, title = title, notes = notes))

    suspend fun setStepDone(stepId: Int, done: Boolean) {
        api().updateBrewStep(stepId, BrewStepPut(done))
    }

    suspend fun deleteBrewStep(stepId: Int) {
        api().deleteBrewStep(stepId)
    }

    suspend fun markDryhopDone(brewId: Int, date: String) {
        api().markDryhopDone(brewId, DryhopDonePost(date))
    }

    private fun nowTimestamp(): String = java.time.LocalDateTime.now()
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

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

    suspend fun brews(): List<Brew> = api().getBrews()

    suspend fun beers(): List<Beer> = api().getBeers()

    suspend fun inventory(): List<InventoryItem> = api().getInventory()

    suspend fun createInventoryItem(post: InventoryPost): InventoryItem = api().createInventoryItem(post)

    suspend fun updateInventoryItem(id: Int, post: InventoryPost): InventoryItem =
        api().updateInventoryItem(id, post)

    suspend fun deleteInventoryItem(id: Int) {
        api().deleteInventoryItem(id)
    }

    suspend fun bjcpStyles(): List<BjcpStyle> = api().getBjcpStyles()

    /** Change le statut d'un brassin en repassant tous ses champs (PUT). */
    suspend fun setBrewStatus(brew: Brew, status: String) {
        api().updateBrew(
            brew.id,
            BrewPut(
                name = brew.name,
                status = status,
                brewDate = brew.brewDate,
                volumeBrewed = brew.volumeBrewed,
                og = brew.og,
                fg = brew.fg,
                abv = brew.abv,
                notes = brew.notes,
                fermTime = brew.fermTime,
                photosUrl = brew.photosUrl,
                costSnapshot = brew.costSnapshot,
                costPerLiter = brew.costPerLiter,
                batchNumber = brew.batchNumber,
            ),
        )
    }

    suspend fun spindles(): List<Spindle> = api().getSpindles()

    suspend fun spindleReadings(id: Int, hours: Int? = null): List<SpindleReading> =
        api().getSpindleReadings(id, hours)

    suspend fun tempSensors(): List<TempSensor> = api().getTempSensors()

    suspend fun tempReadings(id: Int, hours: Int? = null): List<TempReading> =
        api().getTempReadings(id, hours)

    suspend fun sodaKegs(): List<SodaKeg> = api().getSodaKegs()

    /** Met à jour statut et/ou niveau d'un fût en préservant ses autres champs. */
    suspend fun updateKeg(id: Int, status: String, currentLiters: Double?) {
        val api = api()
        val raw = api.getSodaKegsRaw().firstOrNull {
            (it["id"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() == id
        } ?: return
        val body = kotlinx.serialization.json.buildJsonObject {
            raw.forEach { (k, v) -> if (k != "id" && k != "beer_name" && k != "brew_name") put(k, v) }
            put("status", status)
            if (currentLiters != null) put("current_liters", currentLiters)
        }
        api.updateSodaKeg(id, body)
    }

    suspend fun setBeerArchived(id: Int, archived: Boolean): Beer =
        api().patchBeerArchived(id, BeerArchivePatch(archived))

    suspend fun updateBeer(id: Int, put: BeerPut): Beer = api().updateBeer(id, put)

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

    /** Duplique une recette sous [newName] en préservant tous ses champs. Renvoie le nouvel id. */
    suspend fun duplicateRecipe(source: Recipe, newName: String): Int {
        val ings = mergeJson.encodeToJsonElement(
            kotlinx.serialization.builtins.ListSerializer(RecipeIngredient.serializer()),
            source.ingredients,
        )
        val body = kotlinx.serialization.json.buildJsonObject {
            put("name", newName)
            source.style?.let { put("style", it) }
            put("volume", source.volume ?: 20.0)
            source.mashTemp?.let { put("mash_temp", it) }
            source.mashTime?.let { put("mash_time", it) }
            source.boilTime?.let { put("boil_time", it) }
            source.mashRatio?.let { put("mash_ratio", it) }
            source.evapRate?.let { put("evap_rate", it) }
            source.grainAbsorption?.let { put("grain_absorption", it) }
            source.brewhouseEfficiency?.let { put("brewhouse_efficiency", it) }
            source.fermTemp?.let { put("ferm_temp", it) }
            source.fermTime?.let { put("ferm_time", it) }
            source.notes?.let { put("notes", it) }
            put("ingredients", ings)
        }
        val created = api().createRecipeRaw(body)
        return (created["id"] as kotlinx.serialization.json.JsonPrimitive).content.toInt()
    }

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
