package fr.easter.brewhome.data

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

interface BrewApi {
    @GET("api/beers")
    suspend fun getBeers(): List<Beer>

    @PATCH("api/beers/{id}/stock")
    suspend fun patchBeerStock(@Path("id") id: Int, @Body body: StockPatch): Beer

    @PUT("api/beers/{id}/tasting")
    suspend fun putBeerTasting(@Path("id") id: Int, @Body body: TastingPut): Beer

    @GET("api/recipes")
    suspend fun getRecipes(): List<Recipe>

    @GET("api/recipes/{id}")
    suspend fun getRecipe(@Path("id") id: Int): Recipe

    @GET("api/inventory")
    suspend fun getInventory(): List<InventoryItem>

    @PATCH("api/inventory/{id}/qty")
    suspend fun patchInventoryQty(@Path("id") id: Int, @Body body: QtyPatch): InventoryItem

    @POST("api/inventory")
    suspend fun createInventoryItem(@Body body: InventoryPost): InventoryItem

    @PUT("api/inventory/{id}")
    suspend fun updateInventoryItem(@Path("id") id: Int, @Body body: InventoryPost): InventoryItem

    @DELETE("api/inventory/{id}")
    suspend fun deleteInventoryItem(@Path("id") id: Int): kotlinx.serialization.json.JsonObject

    @GET("api/inventory/{id}/history")
    suspend fun getInventoryHistory(@Path("id") id: Int): InventoryHistory

    @GET("api/brews")
    suspend fun getBrews(): List<Brew>

    @POST("api/brews")
    suspend fun createBrew(@Body body: BrewCreatePost): Brew

    @DELETE("api/brews/{id}")
    suspend fun deleteBrew(@Path("id") id: Int): kotlinx.serialization.json.JsonObject

    @DELETE("api/recipes/{id}")
    suspend fun deleteRecipe(@Path("id") id: Int): kotlinx.serialization.json.JsonObject

    @GET("api/trash")
    suspend fun getTrash(): Trash

    @POST("api/recipes/{id}/restore")
    suspend fun restoreRecipe(@Path("id") id: Int): kotlinx.serialization.json.JsonObject

    @POST("api/brews/{id}/restore")
    suspend fun restoreBrew(@Path("id") id: Int): kotlinx.serialization.json.JsonObject

    @POST("api/beers/{id}/restore")
    suspend fun restoreBeer(@Path("id") id: Int): kotlinx.serialization.json.JsonObject

    @POST("api/inventory/{id}/restore")
    suspend fun restoreInventoryItem(@Path("id") id: Int): kotlinx.serialization.json.JsonObject

    @DELETE("api/drafts/{id}")
    suspend fun deleteDraft(@Path("id") id: Int): kotlinx.serialization.json.JsonObject

    @GET("api/brews/{id}/fermentation")
    suspend fun getBrewFermentation(@Path("id") id: Int): List<FermReading>

    @GET("api/brews/{id}/log")
    suspend fun getBrewLog(@Path("id") id: Int): List<BrewLogEntry>

    @GET("api/brews/{id}/photos")
    suspend fun getBrewPhotos(@Path("id") id: Int): List<BrewPhoto>

    @POST("api/brews/{id}/photos")
    suspend fun addBrewPhoto(@Path("id") id: Int, @Body body: BrewPhotoPost): BrewPhoto

    @DELETE("api/brews/{id}/photos/{photoId}")
    suspend fun deleteBrewPhoto(@Path("id") id: Int, @Path("photoId") photoId: Int): kotlinx.serialization.json.JsonObject

    @PATCH("api/brews/{id}/photos/{photoId}")
    suspend fun patchBrewPhoto(
        @Path("id") id: Int,
        @Path("photoId") photoId: Int,
        @Body body: kotlinx.serialization.json.JsonObject,
    ): kotlinx.serialization.json.JsonObject

    @POST("api/brews/{id}/log")
    suspend fun addBrewLog(@Path("id") id: Int, @Body body: BrewLogPost): kotlinx.serialization.json.JsonObject

    @POST("api/brews/{id}/fermentation")
    suspend fun addBrewFermentation(@Path("id") id: Int, @Body body: FermReadingPost): kotlinx.serialization.json.JsonObject

    @DELETE("api/brews/{id}/fermentation/{readingId}")
    suspend fun deleteBrewFermentation(
        @Path("id") id: Int,
        @Path("readingId") readingId: Int,
    ): kotlinx.serialization.json.JsonObject

    @DELETE("api/brews/{id}/log/{entryId}")
    suspend fun deleteBrewLog(
        @Path("id") id: Int,
        @Path("entryId") entryId: Int,
    ): kotlinx.serialization.json.JsonObject

    @GET("api/brews/{id}/steps")
    suspend fun getBrewSteps(@Path("id") id: Int): List<BrewStep>

    @POST("api/brews/{id}/steps")
    suspend fun addBrewStep(@Path("id") id: Int, @Body body: BrewStepPost): BrewStep

    @PUT("api/brew-steps/{stepId}")
    suspend fun updateBrewStep(@Path("stepId") stepId: Int, @Body body: BrewStepPut): BrewStep

    @DELETE("api/brew-steps/{stepId}")
    suspend fun deleteBrewStep(@Path("stepId") stepId: Int): kotlinx.serialization.json.JsonObject

    @POST("api/brews/{id}/dryhop_done")
    suspend fun markDryhopDone(@Path("id") id: Int, @Body body: DryhopDonePost): kotlinx.serialization.json.JsonObject

    @GET("api/app-settings")
    suspend fun getAppSettings(): kotlinx.serialization.json.JsonObject

    @PUT("api/app-settings")
    suspend fun saveAppSettings(@Body body: kotlinx.serialization.json.JsonObject): kotlinx.serialization.json.JsonObject

    @GET("api/catalog")
    suspend fun getCatalog(): List<CatalogItem>

    @GET("api/shopping-list")
    suspend fun getShoppingList(): List<ShoppingItem>

    @POST("api/shopping-list")
    suspend fun createShoppingItem(@Body body: ShoppingPost): ShoppingItem

    @PUT("api/shopping-list/bulk-check")
    suspend fun bulkCheckShopping(@Body body: BulkCheckPut): kotlinx.serialization.json.JsonObject

    /** Transfère les articles cochés dans l'inventaire (soft-delete via bought_at). */
    @POST("api/shopping-list/buy")
    suspend fun buyShoppingItems(): BuyResult

    /** Annule un transfert récent en lui repassant le reçu de /buy. */
    @POST("api/shopping-list/undo-buy")
    suspend fun undoBuyShopping(@Body body: UndoBuyPost): kotlinx.serialization.json.JsonObject

    @DELETE("api/shopping-list/{id}")
    suspend fun deleteShoppingItem(@Path("id") id: Int): kotlinx.serialization.json.JsonObject

    @GET("api/drafts")
    suspend fun getDrafts(): List<Draft>

    @POST("api/drafts")
    suspend fun createDraft(@Body body: DraftPut): Draft

    @PUT("api/drafts/{id}")
    suspend fun updateDraft(@Path("id") id: Int, @Body body: DraftPut): Draft

    @POST("api/ai/draft-suggest")
    suspend fun aiDraftSuggest(@Body body: AiSuggestPost): AiSuggestResult

    @POST("api/import/beerxml")
    suspend fun importBeerXml(@Body body: okhttp3.RequestBody): kotlinx.serialization.json.JsonObject

    @GET("api/consumption")
    suspend fun getConsumption(): Consumption

    @GET("api/custom_events")
    suspend fun getCustomEvents(): List<CustomEvent>

    @POST("api/custom_events")
    suspend fun createCustomEvent(@Body body: CustomEventPost): CustomEvent

    @DELETE("api/custom_events/{id}")
    suspend fun deleteCustomEvent(@Path("id") id: Int): kotlinx.serialization.json.JsonObject

    /** Recette brute : sert de base au PUT fusionné (le PUT écrase toutes les colonnes). */
    @GET("api/recipes/{id}")
    suspend fun getRecipeRaw(@Path("id") id: Int): kotlinx.serialization.json.JsonObject

    @POST("api/recipes")
    suspend fun createRecipe(@Body body: RecipePost): Recipe

    /** Création avec corps JSON libre (duplication : préserve tous les champs). */
    @POST("api/recipes")
    suspend fun createRecipeRaw(@Body body: kotlinx.serialization.json.JsonObject): kotlinx.serialization.json.JsonObject

    @PUT("api/recipes/{id}")
    suspend fun updateRecipe(
        @Path("id") id: Int,
        @Body body: kotlinx.serialization.json.JsonObject,
    ): kotlinx.serialization.json.JsonObject

    @GET("api/bjcp")
    suspend fun getBjcpStyles(): List<BjcpStyle>

    @GET("api/recipes/{id}/history")
    suspend fun getRecipeHistory(@Path("id") id: Int): List<RecipeVersion>

    @POST("api/recipes/{id}/history/{versionId}/restore")
    suspend fun restoreRecipeVersion(
        @Path("id") id: Int,
        @Path("versionId") versionId: Int,
    ): Recipe

    @PUT("api/brews/{id}")
    suspend fun updateBrew(@Path("id") id: Int, @Body body: BrewPut): kotlinx.serialization.json.JsonObject

    @GET("api/spindles")
    suspend fun getSpindles(): List<Spindle>

    @PATCH("api/spindles/{id}")
    suspend fun patchSpindle(
        @Path("id") id: Int,
        @Body body: kotlinx.serialization.json.JsonObject,
    ): kotlinx.serialization.json.JsonObject

    @GET("api/spindles/{id}/readings")
    suspend fun getSpindleReadings(
        @Path("id") id: Int,
        @retrofit2.http.Query("hours") hours: Int? = null,
    ): List<SpindleReading>

    @GET("api/temperature")
    suspend fun getTempSensors(): List<TempSensor>

    @GET("api/temperature/{id}/readings")
    suspend fun getTempReadings(
        @Path("id") id: Int,
        @retrofit2.http.Query("hours") hours: Int? = null,
    ): List<TempReading>

    @GET("api/soda-kegs")
    suspend fun getSodaKegs(): List<SodaKeg>

    /** Fûts en JSON brut : base du PUT fusionné (le PUT écrase toutes les colonnes). */
    @GET("api/soda-kegs")
    suspend fun getSodaKegsRaw(): List<kotlinx.serialization.json.JsonObject>

    @POST("api/soda-kegs")
    suspend fun createSodaKeg(
        @Body body: kotlinx.serialization.json.JsonObject,
    ): kotlinx.serialization.json.JsonObject

    @DELETE("api/soda-kegs/{id}")
    suspend fun deleteSodaKeg(@Path("id") id: Int): kotlinx.serialization.json.JsonObject

    @PUT("api/soda-kegs/{id}")
    suspend fun updateSodaKeg(
        @Path("id") id: Int,
        @Body body: kotlinx.serialization.json.JsonObject,
    ): kotlinx.serialization.json.JsonObject

    @PATCH("api/beers/{id}")
    suspend fun patchBeerArchived(@Path("id") id: Int, @Body body: BeerArchivePatch): Beer

    @PUT("api/beers/{id}")
    suspend fun updateBeer(@Path("id") id: Int, @Body body: BeerPut): Beer
}

object ApiClient {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cached: Pair<String, BrewApi>? = null

    /** Normalise l'URL saisie par l'utilisateur : ajoute http:// et le / final. */
    fun normalizeUrl(raw: String): String {
        var url = raw.trim()
        if (url.isEmpty()) return ""
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://$url"
        if (!url.endsWith("/")) url += "/"
        return url
    }

    fun api(baseUrl: String): BrewApi {
        val normalized = normalizeUrl(baseUrl)
        cached?.let { (url, api) -> if (url == normalized) return api }
        val api = Retrofit.Builder()
            .baseUrl(normalized)
            .client(okHttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(BrewApi::class.java)
        cached = normalized to api
        return api
    }
}
