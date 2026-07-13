package fr.easter.brewhome.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Beer(
    val id: Int,
    val name: String,
    val type: String? = null,
    val abv: Double? = null,
    @SerialName("stock_33cl") val stock33: Int? = 0,
    @SerialName("stock_75cl") val stock75: Int? = 0,
    @SerialName("keg_liters") val kegLiters: Double? = null,
    @SerialName("keg_initial_liters") val kegInitialLiters: Double? = null,
    val origin: String? = null,
    val description: String? = null,
    val photo: String? = null,
    @SerialName("brew_date") val brewDate: String? = null,
    @SerialName("bottling_date") val bottlingDate: String? = null,
    @SerialName("recipe_name") val recipeName: String? = null,
    val refermentation: Int? = 0,
    @SerialName("refermentation_days") val refermentationDays: Int? = null,
    @SerialName("taste_appearance") val tasteAppearance: String? = null,
    @SerialName("taste_aroma") val tasteAroma: String? = null,
    @SerialName("taste_flavor") val tasteFlavor: String? = null,
    @SerialName("taste_bitterness") val tasteBitterness: String? = null,
    @SerialName("taste_mouthfeel") val tasteMouthfeel: String? = null,
    @SerialName("taste_finish") val tasteFinish: String? = null,
    @SerialName("taste_overall") val tasteOverall: String? = null,
    @SerialName("taste_rating") val tasteRating: Int? = null,
    @SerialName("taste_date") val tasteDate: String? = null,
)

@Serializable
data class RecipeIngredient(
    val id: Int,
    val name: String,
    val category: String,
    val quantity: Double,
    val unit: String = "g",
    @SerialName("hop_time") val hopTime: Int? = null,
    @SerialName("hop_type") val hopType: String? = null,
    val ebc: Double? = null,
    val alpha: Double? = null,
    val notes: String? = null,
    @SerialName("stock_qty") val stockQty: Double? = null,
    @SerialName("stock_unit") val stockUnit: String? = null,
)

@Serializable
data class Recipe(
    val id: Int,
    val name: String,
    @SerialName("batch_no") val batchNo: Int? = null,
    val style: String? = null,
    val volume: Double? = null,
    @SerialName("mash_temp") val mashTemp: Double? = null,
    @SerialName("mash_time") val mashTime: Int? = null,
    @SerialName("boil_time") val boilTime: Int? = null,
    @SerialName("ferm_temp") val fermTemp: Double? = null,
    @SerialName("ferm_time") val fermTime: Int? = null,
    val rating: Int? = null,
    val notes: String? = null,
    val ingredients: List<RecipeIngredient> = emptyList(),
)

@Serializable
data class InventoryItem(
    val id: Int,
    val name: String,
    val category: String,
    val quantity: Double = 0.0,
    val unit: String = "kg",
    val origin: String? = null,
    val ebc: Double? = null,
    val alpha: Double? = null,
    val notes: String? = null,
    @SerialName("min_stock") val minStock: Double? = null,
    @SerialName("price_per_unit") val pricePerUnit: Double? = null,
)

@Serializable
data class Brew(
    val id: Int,
    @SerialName("recipe_id") val recipeId: Int? = null,
    val name: String,
    @SerialName("brew_date") val brewDate: String? = null,
    @SerialName("volume_brewed") val volumeBrewed: Double? = null,
    val og: Double? = null,
    val fg: Double? = null,
    val abv: Double? = null,
    val status: String? = null,
    @SerialName("ferm_time") val fermTime: Int? = null,
    val notes: String? = null,
)

@Serializable
data class StockPatch(
    @SerialName("stock_33cl") val stock33: Int? = null,
    @SerialName("stock_75cl") val stock75: Int? = null,
    @SerialName("keg_liters") val kegLiters: Double? = null,
)

@Serializable
data class QtyPatch(val quantity: Double)

@Serializable
data class TastingPut(
    @SerialName("taste_appearance") val tasteAppearance: String? = null,
    @SerialName("taste_aroma") val tasteAroma: String? = null,
    @SerialName("taste_flavor") val tasteFlavor: String? = null,
    @SerialName("taste_bitterness") val tasteBitterness: String? = null,
    @SerialName("taste_mouthfeel") val tasteMouthfeel: String? = null,
    @SerialName("taste_finish") val tasteFinish: String? = null,
    @SerialName("taste_overall") val tasteOverall: String? = null,
    @SerialName("taste_rating") val tasteRating: Int? = null,
    @SerialName("taste_date") val tasteDate: String? = null,
)
