package fr.easter.brewhome.notif

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import fr.easter.brewhome.MainActivity
import fr.easter.brewhome.R

/**
 * Alertes du guide de brassage pas à pas (fin d'empâtage/d'ébullition, ajout
 * d'un houblon) : contrairement à BrewReminders (rappels programmés en bloc,
 * reprogrammés à chaque changement de données), chaque alarme ici est gérée
 * individuellement par la session de guide en cours (démarrage/pause/reset
 * d'un minuteur).
 *
 * setAlarmClock() plutôt que set()/setExactAndAllowWhileIdle : une précision
 * à la minute est nécessaire (rater l'ajout d'un houblon change le profil de
 * la bière), mais setAlarmClock() ne demande aucune permission
 * SCHEDULE_EXACT_ALARM — juste une icône réveil dans la barre de statut tant
 * qu'une alarme est programmée, ce qui est un signal utile ici.
 */
object BrewGuideAlarms {
    const val CHANNEL_ID = "brew_guide"

    // Plage dédiée pour ne pas collisionner avec BrewReminders (BASE_CODE = 20_000).
    private const val BASE_CODE = 30_000
    private const val CODE_MASH_DONE = 0
    private const val CODE_BOIL_DONE = 1
    private const val CODE_HOP_OFFSET = 2

    // Grande marge : aucune recette n'a réellement plus de houblons que ça.
    private const val MAX_HOPS = 30

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notif_guide_channel_name),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply { description = context.getString(R.string.notif_guide_channel_desc) },
            )
        }
    }

    fun scheduleMashDone(context: Context, whenMillis: Long, title: String, text: String) =
        schedule(context, BASE_CODE + CODE_MASH_DONE, whenMillis, title, text)

    fun cancelMashDone(context: Context) = cancel(context, BASE_CODE + CODE_MASH_DONE)

    fun scheduleBoilDone(context: Context, whenMillis: Long, title: String, text: String) =
        schedule(context, BASE_CODE + CODE_BOIL_DONE, whenMillis, title, text)

    /** [hopIndex] = position dans la liste triée de BrewGuideSchedule.boilSchedule(). */
    fun scheduleHopAddition(context: Context, hopIndex: Int, whenMillis: Long, title: String, text: String) {
        if (hopIndex !in 0 until MAX_HOPS) return
        schedule(context, BASE_CODE + CODE_HOP_OFFSET + hopIndex, whenMillis, title, text)
    }

    /** Annule la fin d'ébullition et toutes les alertes de houblonnage. */
    fun cancelBoilAll(context: Context) {
        cancel(context, BASE_CODE + CODE_BOIL_DONE)
        for (i in 0 until MAX_HOPS) cancel(context, BASE_CODE + CODE_HOP_OFFSET + i)
    }

    private fun schedule(context: Context, requestCode: Int, whenMillis: Long, title: String, text: String) {
        ensureChannel(context)
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            context, requestCode, fireIntent(context, title, text, requestCode),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val show = PendingIntent.getActivity(
            context, requestCode,
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(whenMillis, show), pi)
    }

    private fun cancel(context: Context, requestCode: Int) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            context, requestCode, fireIntent(context, "", "", requestCode),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.cancel(pi)
    }

    private fun fireIntent(context: Context, title: String, text: String, code: Int): Intent =
        Intent(context, BrewGuideAlarmReceiver::class.java).apply {
            action = "fr.easter.brewhome.BREW_GUIDE_ALERT"
            putExtra("title", title)
            putExtra("text", text)
            putExtra("code", code)
        }
}
