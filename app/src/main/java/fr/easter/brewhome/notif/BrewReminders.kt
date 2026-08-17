package fr.easter.brewhome.notif

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import fr.easter.brewhome.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Notifications locales des échéances de brassage. Les rappels sont programmés
 * via AlarmManager avec leur texte embarqué : au déclenchement, aucun accès
 * réseau n'est nécessaire (le serveur peut être hors de portée).
 *
 * Un redémarrage du téléphone efface toutes les alarmes programmées ; la
 * liste servie à [schedule] est donc aussi persistée en JSON pour que
 * [rescheduleAfterBoot] puisse les reprogrammer sans réseau ni recalcul.
 */
object BrewReminders {
    const val CHANNEL_ID = "brew_reminders"
    private const val PREFS = "brew_reminders"
    private const val KEY_CODES = "scheduled_codes"
    private const val KEY_REMINDERS = "scheduled_reminders"
    private const val BASE_CODE = 20_000
    private val json = Json { ignoreUnknownKeys = true }

    /** Un rappel à programmer : quand (epoch ms), et quoi afficher. */
    @Serializable
    data class Reminder(val whenMillis: Long, val title: String, val text: String)

    // Types d'événements qui méritent une notification (les autres sont
    // informatifs ou déjà planifiés par l'utilisateur).
    private val notifiableTypes = setOf(
        fr.easter.brewhome.calc.CalendarEvents.Type.FERM_END,
        fr.easter.brewhome.calc.CalendarEvents.Type.DRYHOP,
        fr.easter.brewhome.calc.CalendarEvents.Type.REMIND,
        fr.easter.brewhome.calc.CalendarEvents.Type.REFERM,
        fr.easter.brewhome.calc.CalendarEvents.Type.CUSTOM,
        fr.easter.brewhome.calc.CalendarEvents.Type.BREW,
        fr.easter.brewhome.calc.CalendarEvents.Type.BOTTLE,
    )

    private fun typeTitle(type: fr.easter.brewhome.calc.CalendarEvents.Type): String =
        when (type) {
            fr.easter.brewhome.calc.CalendarEvents.Type.FERM_END -> "Fin de fermentation prévue"
            fr.easter.brewhome.calc.CalendarEvents.Type.DRYHOP -> "Dry hop à ajouter"
            fr.easter.brewhome.calc.CalendarEvents.Type.REMIND -> "Rappel brassage"
            fr.easter.brewhome.calc.CalendarEvents.Type.REFERM -> "Fin de refermentation"
            fr.easter.brewhome.calc.CalendarEvents.Type.BREW -> "Brassage prévu"
            fr.easter.brewhome.calc.CalendarEvents.Type.BOTTLE -> "Mise en bouteille prévue"
            else -> "Rappel"
        }

    /** Rappels à [hour]h le jour de chaque échéance notifiable (dry hop faits exclus). */
    fun remindersFrom(
        events: List<fr.easter.brewhome.calc.CalendarEvents.Event>,
        hour: Int = 9,
    ): List<Reminder> = events
        .filter { it.type in notifiableTypes && !it.dryhopDone }
        .map { ev ->
            val millis = ev.date.atTime(hour, 0)
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli()
            Reminder(millis, "${ev.emoji} ${typeTitle(ev.type)}", ev.label)
        }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = context.getString(R.string.notif_channel_desc) },
            )
        }
    }

    /** POST_NOTIFICATIONS accordée ? (toujours vrai avant Android 13.) */
    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /** Remplace la liste des rappels programmés par [reminders] (les passés sont ignorés). */
    fun schedule(context: Context, reminders: List<Reminder>) {
        cancelAll(context)
        ensureChannel(context)
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val now = System.currentTimeMillis()
        val future = reminders.filter { it.whenMillis > now }
        val codes = mutableListOf<Int>()
        future.forEachIndexed { i, r ->
            val code = BASE_CODE + i
            val pi = PendingIntent.getBroadcast(
                context, code, fireIntent(context, r.title, r.text, code),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            // Inexact volontairement : pas besoin de SCHEDULE_EXACT_ALARM
            am.set(AlarmManager.RTC_WAKEUP, r.whenMillis, pi)
            codes += code
        }
        prefs(context).edit()
            .putString(KEY_CODES, codes.joinToString(","))
            .putString(KEY_REMINDERS, runCatching { json.encodeToString(future) }.getOrDefault("[]"))
            .apply()
    }

    /** Annule tous les rappels précédemment programmés. */
    fun cancelAll(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        savedCodes(context).forEach { code ->
            val pi = PendingIntent.getBroadcast(
                context, code, fireIntent(context, "", "", code),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            am.cancel(pi)
        }
        prefs(context).edit().remove(KEY_CODES).remove(KEY_REMINDERS).apply()
    }

    /**
     * Reprogramme les rappels persistés lors du dernier [schedule] : à appeler
     * au démarrage du téléphone (AlarmManager efface tout au reboot), sans
     * réseau ni recalcul de l'agenda.
     */
    fun rescheduleAfterBoot(context: Context) {
        if (!hasPermission(context)) return
        val saved = runCatching {
            prefs(context).getString(KEY_REMINDERS, null)?.let { json.decodeFromString<List<Reminder>>(it) }
        }.getOrNull() ?: return
        schedule(context, saved)
    }

    private fun fireIntent(context: Context, title: String, text: String, code: Int): Intent =
        Intent(context, BrewReminderReceiver::class.java).apply {
            action = "fr.easter.brewhome.REMINDER"
            putExtra("title", title)
            putExtra("text", text)
            putExtra("code", code)
        }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun savedCodes(context: Context): List<Int> =
        prefs(context).getString(KEY_CODES, "").orEmpty()
            .split(",").mapNotNull { it.toIntOrNull() }
}
