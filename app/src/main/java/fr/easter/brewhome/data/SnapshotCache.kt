package fr.easter.brewhome.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Instantané mis en cache, avec sa date d'enregistrement (epoch ms). */
@Serializable
data class CachedSnapshot(val savedAt: Long, val snapshot: Snapshot)

/**
 * Cache disque du dernier instantané serveur : l'app affiche ces données dès
 * le lancement puis se rafraîchit en arrière-plan, et reste consultable hors
 * ligne. Appels bloquants — à exécuter sur Dispatchers.IO.
 */
class SnapshotCache(dir: File) {
    private val file = File(dir, "snapshot.json")
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): CachedSnapshot? = runCatching {
        json.decodeFromString<CachedSnapshot>(file.readText())
    }.getOrNull()

    fun save(snapshot: Snapshot, savedAt: Long = System.currentTimeMillis()) {
        runCatching { file.writeText(json.encodeToString(CachedSnapshot(savedAt, snapshot))) }
    }

    fun clear() {
        file.delete()
    }
}
