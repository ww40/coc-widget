package dev.ww40.buildertimer.notify

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import dev.ww40.buildertimer.data.SnapshotStore
import dev.ww40.buildertimer.data.VillageSnapshot

/**
 * One alarm per upgrade, keyed by its index in [VillageSnapshot.allUpgrades].
 * Inexact (setAndAllowWhileIdle) to avoid the exact-alarm permission; may fire a few minutes late in Doze.
 */
object UpgradeAlarms {
    const val EXTRA_TITLE = "title"
    private const val TAG = "UpgradeAlarms"

    fun schedule(context: Context, snapshot: VillageSnapshot) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val nowEpochSec = System.currentTimeMillis() / 1000
        snapshot.allUpgrades.forEachIndexed { index, upgrade ->
            if (upgrade.finishesAtEpochSec <= nowEpochSec) return@forEachIndexed
            val intent = alarmIntent(context).putExtra(EXTRA_TITLE, "${upgrade.name} → ${upgrade.targetLevel} finished")
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                upgrade.finishesAtEpochSec * 1000,
                PendingIntent.getBroadcast(
                    context, index, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        }
        Log.i(TAG, "Scheduled alarms for ${snapshot.allUpgrades.size} upgrades")
    }

    fun cancel(context: Context, snapshot: VillageSnapshot) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        snapshot.allUpgrades.indices.forEach { index ->
            // PendingIntent matching ignores extras, so a bare intent with the same request code cancels it.
            alarmManager.cancel(
                PendingIntent.getBroadcast(
                    context, index, alarmIntent(context), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        }
    }

    private fun alarmIntent(context: Context) = Intent(context, UpgradeFinishedReceiver::class.java)
}

class UpgradeFinishedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Notification permission not granted; dropping upgrade-finished alert")
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Upgrade finished", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(intent.getStringExtra(UpgradeAlarms.EXTRA_TITLE) ?: "Upgrade finished")
            .setContentText("Open Clash of Clans to start the next one.")
            .setAutoCancel(true)
            .build()
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private companion object {
        const val CHANNEL_ID = "upgrades"
        const val TAG = "UpgradeFinished"
    }
}

/** Alarms don't survive reboots or app updates, so re-arm them from the saved export. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        SnapshotStore(context).load()?.let { UpgradeAlarms.schedule(context, it) }
    }
}
