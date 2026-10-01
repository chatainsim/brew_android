package fr.easter.brewhome.calc

import fr.easter.brewhome.data.Beer
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** État de la refermentation en bouteille d'une bière, comme le badge de la cave du site. */
sealed interface RefermStatus {
    /** Refermentation cochée, sans date d'embouteillage ou sans durée. */
    data object InProgress : RefermStatus
    data class ReadyIn(val days: Long, val end: LocalDate) : RefermStatus
    data class ReadyToday(val end: LocalDate) : RefermStatus
    data class ReadySince(val days: Long, val end: LocalDate) : RefermStatus
}

/** null si la bière n'est pas en refermentation. Fin = embouteillage + durée. */
fun refermStatus(beer: Beer, today: LocalDate = LocalDate.now()): RefermStatus? {
    if ((beer.refermentation ?: 0) == 0) return null
    val bottled = beer.bottlingDate?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val days = beer.refermentationDays
    if (bottled == null || days == null || days <= 0) return RefermStatus.InProgress
    val end = bottled.plusDays(days.toLong())
    val delta = ChronoUnit.DAYS.between(today, end)
    return when {
        delta > 0 -> RefermStatus.ReadyIn(delta, end)
        delta == 0L -> RefermStatus.ReadyToday(end)
        else -> RefermStatus.ReadySince(-delta, end)
    }
}
