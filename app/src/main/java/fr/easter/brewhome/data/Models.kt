package fr.easter.brewhome.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Beer(
    val id: Int,
    val name: String,
    val type: String? = null,
    val abv: Double? = null,
    val archived: Int? = 0,
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
    @SerialName("hop_days") val hopDays: Int? = null,
    val ebc: Double? = null,
    val alpha: Double? = null,
    val notes: String? = null,
    @SerialName("stock_qty") val stockQty: Double? = null,
    @SerialName("stock_unit") val stockUnit: String? = null,
    @SerialName("inventory_item_id") val inventoryItemId: Int? = null,
    @SerialName("other_type") val otherType: String? = null,
    @SerialName("other_time") val otherTime: Double? = null,
)

/**
 * Corps de POST/PUT /api/recipes. Comme les brouillons, le PUT écrase toutes
 * les colonnes : le dépôt fusionne ces champs dans le JSON brut du serveur
 * pour préserver ceux que l'app n'édite pas (mash_ratio, efficacité…).
 */
@Serializable
data class RecipePost(
    val name: String,
    val style: String? = null,
    val volume: Double? = null,
    @SerialName("mash_temp") val mashTemp: Double? = null,
    @SerialName("mash_time") val mashTime: Double? = null,
    @SerialName("boil_time") val boilTime: Double? = null,
    @SerialName("ferm_temp") val fermTemp: Double? = null,
    @SerialName("ferm_time") val fermTime: Double? = null,
    val notes: String? = null,
    /** Renseignés à la création depuis un brouillon ; jamais écrasés en PUT si null. */
    @SerialName("brew_date") val brewDate: String? = null,
    @SerialName("draft_id") val draftId: Int? = null,
    val ingredients: List<RecipeIngredientPut> = emptyList(),
)

@Serializable
data class RecipeIngredientPut(
    val name: String,
    val category: String,
    val quantity: Double? = null,
    val unit: String = "g",
    @SerialName("hop_time") val hopTime: Int? = null,
    @SerialName("hop_type") val hopType: String? = null,
    @SerialName("hop_days") val hopDays: Int? = null,
    val ebc: Double? = null,
    val alpha: Double? = null,
    val notes: String? = null,
    @SerialName("inventory_item_id") val inventoryItemId: Int? = null,
    @SerialName("other_type") val otherType: String? = null,
    @SerialName("other_time") val otherTime: Double? = null,
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
    @SerialName("mash_ratio") val mashRatio: Double? = null,
    @SerialName("evap_rate") val evapRate: Double? = null,
    @SerialName("grain_absorption") val grainAbsorption: Double? = null,
    @SerialName("brewhouse_efficiency") val brewhouseEfficiency: Double? = null,
    @SerialName("water_mash_override") val waterMashOverride: Double? = null,
    @SerialName("water_sparge_override") val waterSpargeOverride: Double? = null,
    val rating: Int? = null,
    val notes: String? = null,
    val ingredients: List<RecipeIngredient> = emptyList(),
)

/** Style BJCP (plages cibles affichées sur les jauges d'estimation). */
@Serializable
data class BjcpStyle(
    val name: String? = null,
    @SerialName("og_min") val ogMin: Double? = null,
    @SerialName("og_max") val ogMax: Double? = null,
    @SerialName("fg_min") val fgMin: Double? = null,
    @SerialName("fg_max") val fgMax: Double? = null,
    @SerialName("abv_min") val abvMin: Double? = null,
    @SerialName("abv_max") val abvMax: Double? = null,
    @SerialName("ibu_min") val ibuMin: Double? = null,
    @SerialName("ibu_max") val ibuMax: Double? = null,
    @SerialName("ebc_min") val ebcMin: Double? = null,
    @SerialName("ebc_max") val ebcMax: Double? = null,
)

/** Coûts fixes et formule IBU, extraits de /api/app-settings (clés water/energy). */
data class CostSettings(
    val waterPricePerL: Double? = null,
    val gasPerBrew: Double = 0.0,
    val elecPerBrew: Double = 0.0,
    val ibuFormula: String = "tinseth",
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
    val archived: Int? = 0,
    @SerialName("batch_number") val batchNumber: Int? = null,
    @SerialName("brew_date") val brewDate: String? = null,
    @SerialName("bottling_date") val bottlingDate: String? = null,
    @SerialName("volume_brewed") val volumeBrewed: Double? = null,
    val og: Double? = null,
    val fg: Double? = null,
    val abv: Double? = null,
    val status: String? = null,
    @SerialName("ferm_time") val fermTime: Int? = null,
    @SerialName("fermenting_since") val fermentingSince: String? = null,
    @SerialName("recipe_name") val recipeName: String? = null,
    @SerialName("recipe_style") val recipeStyle: String? = null,
    @SerialName("actual_efficiency") val actualEfficiency: Double? = null,
    @SerialName("cost_snapshot") val costSnapshot: Double? = null,
    @SerialName("cost_per_liter_snapshot") val costPerLiter: Double? = null,
    @SerialName("cave_liters") val caveLiters: Double? = null,
    @SerialName("fermentation_count") val fermentationCount: Int? = null,
    @SerialName("log_count") val logCount: Int? = null,
    @SerialName("photo_count") val photoCount: Int? = null,
    /** Champ hérité renvoyé tel quel au PUT pour ne pas l'effacer. */
    @SerialName("photos_url") val photosUrl: String? = null,
    /** JSON : dates de dry hop déjà marquées comme faites (["AAAA-MM-JJ", …]). */
    @SerialName("dryhop_done_dates") val dryhopDoneDates: String? = null,
    val notes: String? = null,
)

/** Étape planifiée d'un brassin (cold crash, ajout, contrôle…). */
@Serializable
data class BrewStep(
    val id: Int,
    @SerialName("scheduled_date") val scheduledDate: String,
    val title: String,
    val notes: String? = null,
    val done: Int = 0,
)

@Serializable
data class BrewLogPost(val ts: String, val step: String? = null, val note: String)

@Serializable
data class FermReadingPost(
    @SerialName("recorded_at") val recordedAt: String,
    val gravity: Double,
    val temperature: Double? = null,
    val notes: String? = null,
)

@Serializable
data class BrewStepPost(
    @SerialName("scheduled_date") val scheduledDate: String,
    val title: String,
    val notes: String? = null,
)

@Serializable
data class BrewStepPut(val done: Boolean)

@Serializable
data class DryhopDonePost(val date: String)

/** Dates de dry hop déjà faites (parsées du champ JSON du brassin). */
fun Brew.parsedDryhopDone(): Set<String> {
    val raw = dryhopDoneDates
    if (raw.isNullOrBlank()) return emptySet()
    return runCatching { draftJson.decodeFromString<List<String>>(raw) }
        .getOrDefault(emptyList())
        .toSet()
}

/**
 * Corps du PUT /api/brews/{id} — écrase toutes les colonnes. On repasse tous
 * les champs du brassin existant en ne changeant que `status` ; le serveur gère
 * seul `fermenting_since` et `actual_efficiency`.
 */
@Serializable
data class BrewPut(
    val name: String,
    val status: String,
    @SerialName("brew_date") val brewDate: String? = null,
    @SerialName("volume_brewed") val volumeBrewed: Double? = null,
    val og: Double? = null,
    val fg: Double? = null,
    val abv: Double? = null,
    val notes: String? = null,
    @SerialName("ferm_time") val fermTime: Int? = null,
    @SerialName("photos_url") val photosUrl: String? = null,
    @SerialName("cost_snapshot") val costSnapshot: Double? = null,
    @SerialName("cost_per_liter_snapshot") val costPerLiter: Double? = null,
    @SerialName("batch_number") val batchNumber: Int? = null,
)

/** Corps de POST /api/brews : démarre un brassin depuis une recette. */
@Serializable
data class BrewCreatePost(
    @SerialName("recipe_id") val recipeId: Int,
    val name: String? = null,
    @SerialName("brew_date") val brewDate: String? = null,
    @SerialName("volume_brewed") val volumeBrewed: Double? = null,
    val status: String = "in_progress",
    @SerialName("batch_number") val batchNumber: Int? = null,
)

/** Densimètre connecté (iSpindel…) avec sa dernière mesure. */
@Serializable
data class Spindle(
    val id: Int,
    val name: String,
    @SerialName("brew_id") val brewId: Int? = null,
    @SerialName("brew_name") val brewName: String? = null,
    @SerialName("device_type") val deviceType: String? = null,
    @SerialName("last_gravity") val lastGravity: Double? = null,
    @SerialName("last_temperature") val lastTemperature: Double? = null,
    @SerialName("last_battery") val lastBattery: Double? = null,
    @SerialName("last_reading_at") val lastReadingAt: String? = null,
    @SerialName("reading_count") val readingCount: Int? = 0,
    @SerialName("gravity_stable") val gravityStable: Int? = 0,
    @SerialName("stable_gravity_avg") val stableGravityAvg: Double? = null,
)

/** Une mesure historisée d'un densimètre. */
@Serializable
data class SpindleReading(
    @SerialName("recorded_at") val recordedAt: String,
    val gravity: Double? = null,
    val temperature: Double? = null,
    val battery: Double? = null,
    val angle: Double? = null,
)

/** Sonde de température (ESP, Home Assistant…) avec sa dernière mesure. */
@Serializable
data class TempSensor(
    val id: Int,
    val name: String,
    @SerialName("brew_id") val brewId: Int? = null,
    @SerialName("brew_name") val brewName: String? = null,
    @SerialName("temp_min") val tempMin: Double? = null,
    @SerialName("temp_max") val tempMax: Double? = null,
    @SerialName("last_temperature") val lastTemperature: Double? = null,
    @SerialName("last_humidity") val lastHumidity: Double? = null,
    @SerialName("last_target_temp") val lastTargetTemp: Double? = null,
    @SerialName("last_reading_at") val lastReadingAt: String? = null,
    @SerialName("reading_count") val readingCount: Int? = 0,
)

/** Une mesure historisée d'une sonde de température. */
@Serializable
data class TempReading(
    @SerialName("recorded_at") val recordedAt: String,
    val temperature: Double? = null,
    val humidity: Double? = null,
    @SerialName("target_temp") val targetTemp: Double? = null,
)

/** Fût à soda / keg (post-mix) et son état de service. */
@Serializable
data class SodaKeg(
    val id: Int,
    val name: String,
    @SerialName("keg_type") val kegType: String? = null,
    val manufacturer: String? = null,
    val status: String = "empty",
    @SerialName("current_liters") val currentLiters: Double? = null,
    @SerialName("volume_total") val volumeTotal: Double? = null,
    @SerialName("beer_name") val beerName: String? = null,
    @SerialName("brew_name") val brewName: String? = null,
    @SerialName("next_revision_date") val nextRevisionDate: String? = null,
    val color: String? = null,
    val notes: String? = null,
    val archived: Int? = 0,
)

/** Corps de PATCH /api/beers/{id} : archive/désarchive une bière. */
@Serializable
data class BeerArchivePatch(val archived: Boolean)

/**
 * Corps du PUT /api/beers/{id} — écrase toutes les colonnes. On repasse tous
 * les champs existants ; `photo` = l'URL /api/beer-photos/… existante pour la
 * conserver, ou un data URL base64 pour la remplacer.
 */
@Serializable
data class BeerPut(
    val name: String,
    val type: String? = null,
    val abv: Double? = null,
    @SerialName("stock_33cl") val stock33: Int = 0,
    @SerialName("stock_75cl") val stock75: Int = 0,
    @SerialName("keg_liters") val kegLiters: Double? = null,
    val origin: String? = null,
    val description: String? = null,
    val photo: String? = null,
    @SerialName("brew_date") val brewDate: String? = null,
    @SerialName("bottling_date") val bottlingDate: String? = null,
    val refermentation: Int = 0,
    @SerialName("refermentation_days") val refermentationDays: Int? = null,
)

/** Photo d'un brassin ; `thumb` est un chemin relatif servi par le serveur. */
@Serializable
data class BrewPhoto(
    val id: Int,
    val step: String? = null,
    val caption: String? = null,
    val thumb: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

/** Envoi d'une photo de brassin : `photo` = data URL base64 (data:image/jpeg;base64,…). */
@Serializable
data class BrewPhotoPost(
    val photo: String,
    val step: String? = null,
    val caption: String? = null,
)

/** Mesure de fermentation (manuelle ou densimètre connecté). */
@Serializable
data class FermReading(
    @SerialName("recorded_at") val recordedAt: String,
    val gravity: Double? = null,
    val temperature: Double? = null,
    val source: String? = null,
    val notes: String? = null,
)

/** Article de la liste de courses (actif tant que bought_at est null). */
@Serializable
data class ShoppingItem(
    val id: Int,
    val name: String,
    val category: String,
    val quantity: Double = 1.0,
    val unit: String = "g",
    val notes: String? = null,
    val checked: Int? = 0,
    @SerialName("inventory_item_id") val inventoryItemId: Int? = null,
)

@Serializable
data class ShoppingPost(
    val name: String,
    val category: String,
    val quantity: Double = 1.0,
    val unit: String = "g",
    val notes: String? = null,
    @SerialName("inventory_item_id") val inventoryItemId: Int? = null,
)

/** Corps de POST/PUT /api/inventory : création ou édition d'un article. */
@Serializable
data class InventoryPost(
    val name: String,
    val category: String,
    val quantity: Double = 0.0,
    val unit: String = "kg",
    val origin: String? = null,
    val ebc: Double? = null,
    val alpha: Double? = null,
    @SerialName("min_stock") val minStock: Double? = null,
    @SerialName("price_per_unit") val pricePerUnit: Double? = null,
    val notes: String? = null,
)

@Serializable
data class BulkCheckPut(val ids: List<Int>, val checked: Boolean)

/**
 * Reçu du transfert courses → stock (POST /buy). Le serveur le renvoie pour
 * permettre l'annulation : il suffit de le repasser tel quel à /undo-buy.
 */
@Serializable
data class BuyResult(
    val count: Int = 0,
    @SerialName("bought_ids") val boughtIds: List<Int> = emptyList(),
    @SerialName("inv_changes") val invChanges: List<InvChange> = emptyList(),
)

/** Changement d'inventaire causé par un transfert (delta dans l'unité du stock). */
@Serializable
data class InvChange(
    val id: Int,
    val delta: Double = 0.0,
    @SerialName("was_created") val wasCreated: Boolean = false,
)

@Serializable
data class UndoBuyPost(
    @SerialName("bought_ids") val boughtIds: List<Int>,
    @SerialName("inv_changes") val invChanges: List<InvChange>,
)

/** Entrée du catalogue d'ingrédients (référentiel pour l'autocomplétion). */
@Serializable
data class CatalogItem(
    val id: Int,
    val name: String,
    val category: String,
    val subcategory: String? = null,
    val ebc: Double? = null,
    /** Potentiel gravimétrique du malt (points/kg/L), utilisé pour l'OG estimé. */
    val gu: Double? = null,
    val alpha: Double? = null,
)

/** Brouillon de recette (idées en cours). */
@Serializable
data class Draft(
    val id: Int,
    val title: String,
    val style: String? = null,
    val volume: Double? = null,
    /** JSON : liste de [DraftIngredient] sérialisée en chaîne côté serveur. */
    val ingredients: String? = null,
    val notes: String? = null,
    @SerialName("target_date") val targetDate: String? = null,
    @SerialName("event_label") val eventLabel: String? = null,
    /** idea | in_progress | ready */
    val status: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val image: String? = null,
    /** JSON : liste d'URLs des photos — à renvoyer telle quelle en PUT sinon elles sont supprimées. */
    val images: String? = null,
    val color: String? = null,
)

/**
 * Corps de POST/PUT /api/drafts. Le PUT écrase toutes les colonnes : toujours
 * renvoyer tous les champs, y compris `images` et `color` (repris du brouillon
 * existant), sous peine de les perdre.
 */
@Serializable
data class DraftPut(
    val title: String,
    val style: String? = null,
    val volume: Double? = null,
    val ingredients: String? = null,
    val notes: String? = null,
    val color: String? = null,
    @SerialName("target_date") val targetDate: String? = null,
    @SerialName("event_label") val eventLabel: String? = null,
    val status: String = "idea",
    val images: String? = null,
)

@Serializable
data class DraftIngredient(
    val name: String = "",
    val category: String = "autre",
    val quantity: Double? = null,
    val unit: String? = null,
)

/** Corps de POST /api/ai/draft-suggest (suggestion de recette par IA). */
@Serializable
data class AiSuggestPost(
    val style: String? = null,
    @SerialName("event_label") val eventLabel: String? = null,
    val notes: String? = null,
    val volume: Double? = null,
)

/** Recette suggérée par l'IA : titre, ingrédients (type/name/qty/unit), notes. */
@Serializable
data class AiSuggestResult(
    val title: String = "",
    val ingredients: List<AiIngredient> = emptyList(),
    val notes: String? = null,
)

@Serializable
data class AiIngredient(
    val type: String = "autre",
    val name: String = "",
    val qty: Double? = null,
    val unit: String? = null,
)

private val draftJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

fun Draft.parsedIngredients(): List<DraftIngredient> {
    val raw = ingredients
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching { draftJson.decodeFromString<List<DraftIngredient>>(raw) }
        .getOrDefault(emptyList())
}

/**
 * Chemins des photos du brouillon (`images` est un tableau JSON de chemins
 * `/api/draft-images/…`). Les entrées base64 héritées sont écartées.
 */
fun Draft.parsedImages(): List<String> {
    val raw = images
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching { draftJson.decodeFromString<List<String>>(raw) }
        .getOrDefault(emptyList())
        .filter { it.startsWith("/") }
}

/** Événement personnalisé du calendrier (fête, concours, brassage prévu…). */
@Serializable
data class CustomEvent(
    val id: Int,
    val title: String = "Événement",
    val emoji: String? = null,
    @SerialName("event_date") val eventDate: String? = null,
    val color: String? = null,
    val notes: String? = null,
    @SerialName("brew_reminder") val brewReminder: Int? = 0,
    @SerialName("brew_reminder_days") val brewReminderDays: Int? = null,
    /** JSON : {"type":"yearly"|"monthly"|"weekly"|"yearly_nth_dow"|"monthly_nth_dow", …} */
    val recurrence: String? = null,
    val style: String? = null,
    @SerialName("recipe_id") val recipeId: Int? = null,
    @SerialName("draft_id") val draftId: Int? = null,
)

/** Corps de POST /api/custom_events (le serveur remplit les défauts). */
@Serializable
data class CustomEventPost(
    val title: String,
    val emoji: String = "📅",
    @SerialName("event_date") val eventDate: String,
    val color: String = "#f59e0b",
    val notes: String? = null,
    @SerialName("brew_reminder") val brewReminder: Boolean = false,
    @SerialName("brew_reminder_days") val brewReminderDays: Int? = null,
    val recurrence: String? = null,
)

@Serializable
data class ConsumptionMonth(
    val period: String,
    @SerialName("total_33cl") val total33: Int? = 0,
    @SerialName("total_75cl") val total75: Int? = 0,
    @SerialName("total_keg") val totalKeg: Double? = 0.0,
)

@Serializable
data class ConsumptionBeer(
    @SerialName("beer_name") val beerName: String? = null,
    @SerialName("total_liters") val totalLiters: Double? = 0.0,
)

@Serializable
data class Consumption(
    @SerialName("by_month") val byMonth: List<ConsumptionMonth> = emptyList(),
    @SerialName("by_beer") val byBeer: List<ConsumptionBeer> = emptyList(),
)

/** Entrée du journal de brassage. */
@Serializable
data class BrewLogEntry(
    val id: Int,
    val ts: String,
    val step: String? = null,
    val note: String? = null,
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
