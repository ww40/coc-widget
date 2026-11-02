package dev.ww40.cocwidget.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class VillageWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VillageWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // Also runs after reboot, which clears alarms.
        WidgetRefresher.startMinuteTicks(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetRefresher.stopMinuteTicks(context)
    }
}
