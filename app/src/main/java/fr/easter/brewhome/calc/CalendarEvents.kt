package fr.easter.brewhome.calc

import fr.easter.brewhome.data.Beer
import fr.easter.brewhome.data.Brew
import fr.easter.brewhome.data.CustomEvent
import fr.easter.brewhome.data.Draft
import fr.easter.brewhome.data.Recipe
import fr.easter.brewhome.data.parsedDryhopDone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate

/**
 * Agenda du calendrier — port de la vue agenda de bh-calendrier.js : brassins
 * (brassage, embouteillage, fin de fermentation, dry hops), refermentations,
 * brouillons datés, événements personnalisés (récurrences + rappels brassage)
 * et journées mondiales de la bière.
 */
object CalendarEvents {

    enum class Type { BREW, BOTTLE, FERM_END, DRYHOP, REFERM, DRAFT, CUSTOM, REMIND, WORLD }

    data class Event(
        val date: LocalDate,
        val type: Type,
        val label: String,
        val emoji: String,
        /** Couleur hex "#rrggbb" si l'événement en porte une (perso / mondial). */
        val colorHex: String? = null,
        val notes: String? = null,
        /** id de l'événement personnalisé (pour la suppression). */
        val customId: Int? = null,
        /** id du brassin / brouillon lié (pour ouvrir sa fiche). */
        val brewId: Int? = null,
        val draftId: Int? = null,
        /** true si ce dry hop a déjà été marqué comme ajouté. */
        val dryhopDone: Boolean = false,
    )

    private val json = Json { ignoreUnknownKeys = true }

    private fun parseDate(s: String?): LocalDate? =
        s?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** n-ième jour de semaine du mois ; dow en convention JS (0=dimanche..6=samedi), nth<=0 = dernier. */
    internal fun nthDow(year: Int, month: Int, dow: Int, nth: Int): LocalDate {
        fun jsDow(d: LocalDate) = d.dayOfWeek.value % 7
        return if (nth > 0) {
            var d = LocalDate.of(year, month, 1)
            while (jsDow(d) != dow) d = d.plusDays(1)
            d.plusDays((nth - 1) * 7L)
        } else {
            var d = LocalDate.of(year, month, 1).plusMonths(1).minusDays(1)
            while (jsDow(d) != dow) d = d.minusDays(1)
            d
        }
    }

    /** Occurrences d'un événement personnalisé sur [refYear-1, refYear+1]. */
    internal fun expand(ev: CustomEvent, refYear: Int): List<LocalDate> {
        val base = parseDate(ev.eventDate) ?: return emptyList()
        val rec = ev.recurrence?.let {
            runCatching { json.decodeFromString<JsonObject>(it) }.getOrNull()
        }
        val type = rec?.get("type")?.jsonPrimitive?.content ?: return listOf(base)
        val years = (refYear - 1)..(refYear + 1)

        fun recInt(key: String, default: Int = 0) =
            rec[key]?.jsonPrimitive?.intOrNull ?: default

        return when (type) {
            "yearly" -> years.mapNotNull { y ->
                runCatching { LocalDate.of(y, base.monthValue, base.dayOfMonth) }.getOrNull()
            }
            "yearly_nth_dow" -> years.map { y ->
                nthDow(y, base.monthValue, recInt("dow"), recInt("nth", 1))
            }
            "monthly" -> years.flatMap { y ->
                (1..12).mapNotNull { m ->
                    runCatching { LocalDate.of(y, m, base.dayOfMonth) }.getOrNull()
                }
            }
            "monthly_nth_dow" -> years.flatMap { y ->
                (1..12).map { m -> nthDow(y, m, recInt("dow"), recInt("nth", 1)) }
            }
            "weekly" -> {
                val interval = maxOf(1, recInt("interval", 1)).toLong()
                val rangeStart = LocalDate.of(refYear - 1, 1, 1)
                val rangeEnd = LocalDate.of(refYear + 1, 12, 31)
                var cur = base
                while (cur > rangeStart) cur = cur.minusWeeks(interval)
                cur = cur.plusWeeks(interval)
                val out = mutableListOf<LocalDate>()
                while (cur <= rangeEnd) {
                    out += cur
                    cur = cur.plusWeeks(interval)
                }
                out
            }
            else -> listOf(base)
        }
    }

    /**
     * Tous les événements de [from, to], triés par date.
     * @param defaultReminderDays rappel « penser à brasser » par défaut (J-45 sur le site)
     */
    fun agenda(
        from: LocalDate,
        to: LocalDate,
        brews: List<Brew>,
        recipes: Map<Int, Recipe>,
        beers: List<Beer>,
        drafts: List<Draft>,
        customEvents: List<CustomEvent>,
        defaultReminderDays: Int = 45,
    ): List<Event> {
        val out = mutableListOf<Event>()
        fun add(ev: Event?) {
            if (ev != null && ev.date >= from && ev.date <= to) out += ev
        }

        // Brassins : brassage, embouteillage, fin de fermentation, dry hops
        brews.filter { (it.archived ?: 0) == 0 }.forEach { b ->
            val brewDate = parseDate(b.brewDate)
            brewDate?.let { add(Event(it, Type.BREW, b.name, "🍺", brewId = b.id)) }
            parseDate(b.bottlingDate)?.let { add(Event(it, Type.BOTTLE, b.name, "🍾", brewId = b.id)) }
            val fermDays = b.fermTime
            if (brewDate != null && fermDays != null) {
                add(Event(brewDate.plusDays(fermDays.toLong()), Type.FERM_END, b.name, "🌡️", brewId = b.id))
            }
            if (fermDays != null && b.recipeId != null &&
                (b.status == "fermenting" || b.status == "completed")
            ) {
                val fermStart = parseDate(b.fermentingSince) ?: brewDate
                val dryHops = recipes[b.recipeId]?.ingredients.orEmpty()
                    .filter {
                        it.category.equals("houblon", true) &&
                            it.hopType == "dryhop" && (it.hopDays ?: 0) > 0
                    }
                if (fermStart != null) {
                    val doneDates = b.parsedDryhopDone()
                    dryHops.groupBy { it.hopDays!! }.forEach { (days, hops) ->
                        val offset = fermDays - days
                        if (offset >= 0) {
                            val what = hops.joinToString(", ") { "${fmtNum(it.quantity)} ${it.unit} ${it.name}" }
                            val date = fermStart.plusDays(offset.toLong())
                            add(Event(
                                date, Type.DRYHOP,
                                "${b.name} — Dry Hop (J$offset) : $what", "🌿", brewId = b.id,
                                dryhopDone = date.toString() in doneDates,
                            ))
                        }
                    }
                }
            }
        }

        // Fin de refermentation des bières en cave
        beers.filter {
            (it.refermentation ?: 0) == 1 && it.bottlingDate != null && it.refermentationDays != null
        }.forEach { b ->
            parseDate(b.bottlingDate)?.let {
                add(Event(it.plusDays(b.refermentationDays!!.toLong()), Type.REFERM, b.name, "🔄"))
            }
        }

        // Brouillons avec date cible
        drafts.forEach { d ->
            parseDate(d.targetDate)?.let { add(Event(it, Type.DRAFT, d.title, "📖", draftId = d.id)) }
        }

        // Événements personnalisés (récurrences) + rappels « penser à brasser »
        customEvents.forEach { ev ->
            expand(ev, from.year).forEach { date ->
                add(Event(
                    date, Type.CUSTOM, ev.title, ev.emoji ?: "📅", ev.color,
                    notes = ev.notes, customId = ev.id,
                ))
                if ((ev.brewReminder ?: 0) == 1) {
                    val days = ev.brewReminderDays ?: defaultReminderDays
                    add(Event(date.minusDays(days.toLong()), Type.REMIND, ev.title, "🔔", customId = ev.id))
                }
            }
        }

        // Journées mondiales de la bière
        (from.year..to.year).forEach { y ->
            worldBeerDays(y).forEach { add(it) }
        }

        return out.sortedBy { it.date }
    }

    private fun fmtNum(q: Double): String =
        if (q % 1.0 == 0.0) q.toInt().toString() else q.toString()

    /** Journées mondiales bière/brassage de l'année (même liste que le site). */
    fun worldBeerDays(year: Int): List<Event> {
        val ev = mutableListOf<Event>()
        fun add(month: Int, day: Int, label: String, emoji: String, color: String) {
            ev += Event(LocalDate.of(year, month, day), Type.WORLD, label, emoji, color)
        }
        fun add(date: LocalDate, label: String, emoji: String, color: String) {
            ev += Event(date, Type.WORLD, label, emoji, color)
        }
        add(1, 1, "National Hangover Day", "🤕", "#6b7280")
        add(1, 17, "Baltic Porter Day", "🍺", "#6366f1")
        add(1, 17, "National Bootlegger's Day", "🥃", "#78350f")
        add(1, 24, "National Beer Can Day", "🥫", "#f59e0b")
        add(2, 24, "World Bartender Day", "🍸", "#a855f7")
        add(2, 28, "Open That Bottle Night", "🍾", "#a855f7")
        add(3, 8, "Pink Boots Collaboration Brew Day", "👢", "#ec4899")
        add(3, 16, "Orval International Day", "🍺", "#d97706")
        add(3, 17, "St. Patrick's Day", "🍀", "#16a34a")
        add(3, 20, "National Bock Day", "🐐", "#92400e")
        add(4, 6, "New Beer's Eve", "🍺", "#f59e0b")
        add(4, 7, "National Beer Day", "🍺", "#f59e0b")
        add(4, 11, "King Gambrinus Day", "👑", "#d97706")
        add(4, 23, "German Beer Day / Reinheitsgebot", "🇩🇪", "#ef4444")
        add(4, 25, "Beer-Clean Glass Day", "🥃", "#06b6d4")
        add(4, 26, "Saison Day", "🌾", "#84cc16")
        add(5, 1, "National Rotate Your Beer Day", "🔄", "#f59e0b")
        add(5, 2, "Beer Pong Day", "🏓", "#84cc16")
        add(5, 5, "Cinco de Mayo", "🌮", "#16a34a")
        add(5, 7, "National Homebrew Day", "🍻", "#8b5cf6")
        add(5, 11, "American Craft Beer Week (début)", "🇺🇸", "#3b82f6")
        add(6, 8, "Name Your Poison Day", "☠️", "#6b7280")
        add(6, 15, "Beer Day Britain", "🍺", "#3b82f6")
        add(7, 7, "National Dive Bar Day", "🍺", "#92400e")
        add(7, 12, "National Michelada Day", "🌶️", "#dc2626")
        add(7, 23, "National Refreshment Day", "🥤", "#06b6d4")
        add(9, 7, "National Beer Lover's Day", "🍺", "#f59e0b")
        add(9, 20, "Sour Beer Day", "🍋", "#facc15")
        add(9, 24, "Arthur Guinness Day", "🖤", "#374151")
        add(9, 27, "National Crush-A-Can Day", "🥫", "#6b7280")
        add(9, 28, "National Drink A Beer Day", "🍺", "#f59e0b")
        add(10, 2, "Barrel-Aged Beer Day", "🛢️", "#92400e")
        add(10, 4, "Buy A Stranger A Drink Day", "🍺", "#f59e0b")
        add(10, 9, "Beer & Pizza Day", "🍕", "#ef4444")
        add(10, 10, "National Black Brewers Day", "✊", "#374151")
        add(10, 14, "Homebrewing Legalization Day", "⚖️", "#8b5cf6")
        add(10, 27, "National American Beer Day", "🇺🇸", "#3b82f6")
        add(11, 5, "International Stout Day", "🖤", "#374151")
        add(nthDow(year, 11, 6, 1), "Learn to Homebrew Day", "🏠", "#8b5cf6")
        add(11, 12, "National Happy Hour Day", "🍺", "#f59e0b")
        add(11, 17, "International Happy Gose Day", "🧂", "#06b6d4")
        add(11, 29, "Small Brewery Sunday", "🏠", "#8b5cf6")
        add(12, 4, "National Bartender Day", "🍸", "#a855f7")
        add(12, 5, "National Repeal Day", "🗽", "#3b82f6")
        add(12, 10, "National Lager Day", "🍺", "#f59e0b")
        add(12, 25, "Noël — Bière de Noël", "🎄", "#dc2626")
        // Dates calculées
        add(nthDow(year, 8, 4, 1), "IPA Day", "🌿", "#84cc16")
        add(nthDow(year, 8, 5, 1), "International Beer Day", "🍺", "#f59e0b")
        add(nthDow(year, 11, 4, 3), "Beaujolais Nouveau", "🍷", "#dc2626")
        // Oktoberfest : du samedi précédant le 22 septembre au 1er dimanche d'octobre
        var okt = LocalDate.of(year, 9, 22)
        while (okt.dayOfWeek.value % 7 != 6) okt = okt.minusDays(1)
        add(okt, "Début Oktoberfest", "🥨", "#d97706")
        add(nthDow(year, 10, 0, 1), "Fin Oktoberfest", "🥨", "#d97706")
        return ev
    }
}
