package fr.easter.brewhome.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Corps complets des PUT « tout ou rien » (bière, article de stock).
 *
 * Le JSON de Retrofit omet les valeurs par défaut et les null : une case décochée
 * (`refermentation = 0`), une quantité à 0, une unité « kg » ou un champ vidé
 * n'étaient donc pas envoyés, et le serveur — qui garde la valeur actuelle d'un
 * champ absent — ne les enregistrait jamais. Ici, tout est envoyé, null compris.
 */
private val fullJson = Json { encodeDefaults = true; explicitNulls = true }

internal fun beerPutBody(put: BeerPut): JsonObject {
    val body = fullJson.encodeToJsonElement(BeerPut.serializer(), put).jsonObject
    // Photo inconnue = conservée : un null effacerait la photo et son fichier sur le serveur
    return if (put.photo == null) JsonObject(body - "photo") else body
}

internal fun inventoryPutBody(post: InventoryPost): JsonObject =
    fullJson.encodeToJsonElement(InventoryPost.serializer(), post).jsonObject
