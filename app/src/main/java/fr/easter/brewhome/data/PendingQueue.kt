package fr.easter.brewhome.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Ajustement de stock d'une bière fait hors ligne, à rejouer à la reconnexion. */
@Serializable
data class PendingStockOp(
    val beerId: Int,
    val d33: Int = 0,
    val d75: Int = 0,
    val dKeg: Double = 0.0,
)

/**
 * File persistante des ajustements de stock effectués hors ligne. Appels
 * bloquants — à exécuter sur Dispatchers.IO. Les opérations sont rejouées et
 * regroupées par bière lors du prochain rafraîchissement réussi.
 */
class PendingQueue(dir: File) {
    private val file = File(dir, "pending_ops.json")
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): List<PendingStockOp> = runCatching {
        if (!file.exists()) emptyList()
        else json.decodeFromString<List<PendingStockOp>>(file.readText())
    }.getOrDefault(emptyList())

    fun add(op: PendingStockOp) {
        save(load() + op)
    }

    fun save(ops: List<PendingStockOp>) {
        runCatching { file.writeText(json.encodeToString(ops)) }
    }

    fun clear() {
        file.delete()
    }

    companion object {
        /**
         * Regroupe les opérations par bière en sommant les deltas : rejouer deux
         * « +1 » doit donner « +2 », pas écraser l'un par l'autre.
         */
        fun coalesce(ops: List<PendingStockOp>): List<PendingStockOp> =
            ops.groupBy { it.beerId }.map { (id, list) ->
                PendingStockOp(
                    beerId = id,
                    d33 = list.sumOf { it.d33 },
                    d75 = list.sumOf { it.d75 },
                    dKeg = list.sumOf { it.dKeg },
                )
            }
    }
}
