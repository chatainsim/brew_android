package fr.easter.brewhome.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Réécrit un réglage stocké côté serveur en JSON sérialisé dans une chaîne
 * (clés « water » et « energy » de /api/app-settings) en ne modifiant que les
 * champs de [updates] ; une valeur nulle retire le champ.
 *
 * Le site range dans ces mêmes objets des champs que l'app ne connaît pas
 * (profil d'eau pH/Ca/Mg/Na/SO4/Cl/HCO3, eau de refroidissement…). Les
 * reconstruire à partir des seuls champs de l'app les effaçait.
 */
fun mergeJsonSetting(existing: JsonElement?, updates: Map<String, JsonElement?>): String {
    val current = (existing as? JsonPrimitive)?.contentOrNull
        ?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
        ?: JsonObject(emptyMap())
    val merged = current.toMutableMap()
    updates.forEach { (key, value) ->
        if (value == null || value is JsonNull) merged.remove(key) else merged[key] = value
    }
    return JsonObject(merged).toString()
}
