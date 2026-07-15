package fr.easter.brewhome.data

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
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

    @GET("api/brews")
    suspend fun getBrews(): List<Brew>

    @GET("api/brews/{id}/fermentation")
    suspend fun getBrewFermentation(@Path("id") id: Int): List<FermReading>

    @GET("api/brews/{id}/log")
    suspend fun getBrewLog(@Path("id") id: Int): List<BrewLogEntry>

    @GET("api/app-settings")
    suspend fun getAppSettings(): kotlinx.serialization.json.JsonObject

    @GET("api/drafts")
    suspend fun getDrafts(): List<Draft>

    @POST("api/drafts")
    suspend fun createDraft(@Body body: DraftPut): Draft

    @PUT("api/drafts/{id}")
    suspend fun updateDraft(@Path("id") id: Int, @Body body: DraftPut): Draft

    @GET("api/consumption")
    suspend fun getConsumption(): Consumption
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
