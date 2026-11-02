package dev.ww40.buildertimer.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** Keeps the shared minute tick running while either widget is on the home screen. */
abstract class TickingWidgetReceiver : GlanceAppWidgetReceiver() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // Also runs after reboot, which clears alarms.
        WidgetRefresher.startMinuteTicks(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetRefresher.stopMinuteTicksIfUnused(context)
    }
}

class VillageWidgetReceiver : TickingWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VillageWidget()
}
