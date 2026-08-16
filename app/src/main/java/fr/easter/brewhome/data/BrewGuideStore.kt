package fr.easter.brewhome.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * État persisté d'une session de guide de brassage : étape courante, minuteurs
 * (bornes en epoch ms, pas un compteur, pour rester exacts après fermeture de
 * l'app) et items cochés (ingrédients de préparation/concassage, levures/dry-hops
 * d'ensemencement). Le statut "à venir/maintenant/ajouté" du houblonnage pendant
 * l'ébullition se déduit du temps écoulé depuis boilTimerEndAt - durée, pas d'un
 * état séparé à persister.
 */
@Serializable
data class BrewGuideState(
    val step: Int = 0,
    val mashTimerEndAt: Long? = null,
    val mashTimerPausedRemainingMs: Long? = null,
    val boilTimerEndAt: Long? = null,
    val boilTimerPausedRemainingMs: Long? = null,
    val checkedItems: Set<String> = emptySet(),
)

/**
 * Persistance locale uniquement (pas de synchronisation serveur, comme le
 * guide web qui ne persiste rien du tout) des sessions de guide de brassage
 * en cours, une par recette ou brassin (clé "recipe_<id>" ou "brew_<id>").
 * Suffit à survivre à la fermeture de l'app pendant UN brassage en cours ;
 * les alertes elles-mêmes sont programmées via AlarmManager (BrewGuideAlarms),
 * indépendamment de cette persistance. Appels bloquants — Dispatchers.IO.
 */
class BrewGuideStore(dir: File) {
    private val file = File(dir, "brew_guide.json")
    private val json = Json { ignoreUnknownKeys = true }

    private fun loadAll(): Map<String, BrewGuideState> = runCatching {
        if (!file.exists()) emptyMap()
        else json.decodeFromString<Map<String, BrewGuideState>>(file.readText())
    }.getOrDefault(emptyMap())

    fun load(key: String): BrewGuideState? = loadAll()[key]

    fun save(key: String, state: BrewGuideState) {
        runCatching { file.writeText(json.encodeToString(loadAll() + (key to state))) }
    }

    fun clear(key: String) {
        runCatching { file.writeText(json.encodeToString(loadAll() - key)) }
    }
}
