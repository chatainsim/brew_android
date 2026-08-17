package fr.easter.brewhome.notif

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Reprogramme les rappels de calendrier et les alertes du guide de brassage
 * au démarrage du téléphone — AlarmManager efface toutes les alarmes au
 * reboot, ce qui sinon les fait disparaître silencieusement jusqu'à la
 * prochaine ouverture manuelle de l'app.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        BrewReminders.rescheduleAfterBoot(context)
        BrewGuideAlarms.rescheduleAfterBoot(context)
    }
}
