package fr.easter.brewhome.notif

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import fr.easter.brewhome.MainActivity
import fr.easter.brewhome.R

/** Reçoit l'alarme d'un rappel et poste la notification correspondante. */
class BrewReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: context.getString(R.string.app_name)
        val text = intent.getStringExtra("text") ?: return
        val code = intent.getIntExtra("code", 0)

        BrewReminders.ensureChannel(context)

        val tap = PendingIntent.getActivity(
            context, code,
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notif = NotificationCompat.Builder(context, BrewReminders.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(code, notif) }
    }
}
