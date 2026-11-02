package dev.ww40.buildertimer.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object WidgetRefresher {
    private val REFRESHED_AT_KEY = longPreferencesKey("refreshed_at")
    private const val TICK_INTERVAL_MS = 60_000L

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Bumps each widget's state so its composition re-reads the snapshot and clock. */
    suspend fun refreshAll(context: Context) {
        GlanceAppWidgetManager(context).getGlanceIds(VillageWidget::class.java).forEach { id ->
            updateAppWidgetState(context, id) { it[REFRESHED_AT_KEY] = System.currentTimeMillis() }
            VillageWidget().update(context, id)
        }
    }

    /** Non-wakeup repeating alarm: ticks while the device is awake, pauses while it sleeps. */
    fun startMinuteTicks(context: Context) {
        context.getSystemService(AlarmManager::class.java).setRepeating(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + TICK_INTERVAL_MS,
            TICK_INTERVAL_MS,
            tickIntent(context),
        )
    }

    fun stopMinuteTicks(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(tickIntent(context))
    }

    private fun tickIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, MinuteTickReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

class MinuteTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        WidgetRefresher.scope.launch {
            try {
                WidgetRefresher.refreshAll(context)
            } finally {
                pending.finish()
            }
        }
    }
}
