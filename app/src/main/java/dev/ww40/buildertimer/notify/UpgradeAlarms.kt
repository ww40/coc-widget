package dev.ww40.buildertimer.notify

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.ww40.buildertimer.ClashApp
import dev.ww40.buildertimer.data.SettingsStore
import dev.ww40.buildertimer.data.SnapshotStore
import dev.ww40.buildertimer.data.VillageSnapshot
import java.time.ZoneId

/**
 * One alarm per planned alert, keyed by its request code.
 * Inexact (setAndAllowWhileIdle) to avoid the exact-alarm permission; may fire a few minutes late in Doze.
 */
object UpgradeAlarms {
    const val EXTRA_TITLE = "title"
    const val EXTRA_TEXT = "text"
    private const val TAG = "UpgradeAlarms"

    /** Re-arms alarms from the saved export; call after settings change so switched-off alerts are dropped. */
    fun rescheduleSaved(context: Context) {
        val snapshot = SnapshotStore(context).load() ?: return
        cancel(context, snapshot)
        schedule(context, snapshot)
    }

    fun schedule(context: Context, snapshot: VillageSnapshot) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val alerts = plannedAlerts(snapshot, SettingsStore(context).load(), System.currentTimeMillis() / 1000, ZoneId.systemDefault())
        alerts.forEach { alert ->
            val intent = alarmIntent(context).putExtra(EXTRA_TITLE, alert.title).putExtra(EXTRA_TEXT, alert.text)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                alert.atEpochSec * 1000,
                PendingIntent.getBroadcast(
                    context, alert.requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        }
        Log.i(TAG, "Scheduled ${alerts.size} alerts")
    }

    fun cancel(context: Context, snapshot: VillageSnapshot) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alertRequestCodes(snapshot).forEach { requestCode ->
            // PendingIntent matching ignores extras, so a bare intent with the same request code cancels it.
            alarmManager.cancel(
                PendingIntent.getBroadcast(
                    context, requestCode, alarmIntent(context), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        }
    }

    private fun alarmIntent(context: Context) = Intent(context, UpgradeFinishedReceiver::class.java)
}

object UpgradeNotifier {
    private const val CHANNEL_ID = "upgrades"
    private const val DEFAULT_TEXT = "Open Clash of Clans to start the next one."

    /** False when the permission, the app's notifications or its channel are switched off. */
    fun canNotify(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return false
        return manager.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun show(context: Context, title: String, text: String?) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Upgrade finished", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text ?: DEFAULT_TEXT)
            .setAutoCancel(true)
        ClashApp.launchIntent(context)?.let { launchClash ->
            notification.setContentIntent(PendingIntent.getActivity(context, 0, launchClash, PendingIntent.FLAG_IMMUTABLE))
        }
        manager.notify(System.currentTimeMillis().toInt(), notification.build())
    }
}

class UpgradeFinishedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!UpgradeNotifier.canNotify(context)) {
            Log.w(TAG, "Notifications are off; dropping alert")
            return
        }
        UpgradeNotifier.show(
            context,
            intent.getStringExtra(UpgradeAlarms.EXTRA_TITLE) ?: "Upgrade finished",
            intent.getStringExtra(UpgradeAlarms.EXTRA_TEXT),
        )
    }

    private companion object {
        const val TAG = "UpgradeFinished"
    }
}

/** Alarms don't survive reboots or app updates, so re-arm them from the saved export. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        UpgradeAlarms.rescheduleSaved(context)
    }
}
