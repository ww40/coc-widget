package dev.ww40.buildertimer

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
import android.widget.Toast
import dev.ww40.buildertimer.data.SettingsStore
import dev.ww40.buildertimer.notify.UpgradeAlarms
import dev.ww40.buildertimer.sync.SyncActivity
import dev.ww40.buildertimer.widget.WidgetRefresher
import kotlinx.coroutines.launch

/** Setup screen: explains the sync flow, edits settings and asks for notification permission. */
class MainActivity : Activity() {

    private val store by lazy { SettingsStore(this) }
    private val playerName by lazy { findViewById<EditText>(R.id.player_name) }
    private val showFinishTime by lazy { findViewById<Switch>(R.id.show_finish_time) }
    private val quietEnabled by lazy { findViewById<Switch>(R.id.quiet_hours_enabled) }
    private val quietStart by lazy { findViewById<Spinner>(R.id.quiet_start) }
    private val quietEnd by lazy { findViewById<Spinner>(R.id.quiet_end) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        acceptWidgetConfiguration()
        setContentView(R.layout.activity_main)
        findViewById<Button>(R.id.sync_button).setOnClickListener {
            startActivity(Intent(this, SyncActivity::class.java))
        }
        findViewById<Button>(R.id.save_settings_button).setOnClickListener { saveSettings() }
        showSettings()
        requestNotificationPermission()
    }

    /**
     * This screen doubles as the widgets' configure activity (long-press → Settings). Before
     * Android 12 it also opens when a widget is added, and the launcher drops the widget unless
     * the result is OK.
     */
    private fun acceptWidgetConfiguration() {
        if (intent.action != AppWidgetManager.ACTION_APPWIDGET_CONFIGURE) return
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
    }

    private fun showSettings() {
        val hours = ArrayAdapter(this, android.R.layout.simple_spinner_item, HOUR_LABELS)
        hours.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        quietStart.adapter = hours
        quietEnd.adapter = hours
        val settings = store.load()
        playerName.setText(settings.playerName.orEmpty())
        showFinishTime.isChecked = settings.showFinishTime
        quietEnabled.isChecked = settings.quietHoursEnabled
        quietStart.setSelection(settings.quietStartHour)
        quietEnd.setSelection(settings.quietEndHour)
    }

    private fun saveSettings() {
        store.save(
            store.load().copy(
                playerName = playerName.text.toString(),
                showFinishTime = showFinishTime.isChecked,
                quietHoursEnabled = quietEnabled.isChecked,
                quietStartHour = quietStart.selectedItemPosition,
                quietEndHour = quietEnd.selectedItemPosition,
            ),
        )
        UpgradeAlarms.rescheduleSaved(this)
        val appContext = applicationContext
        WidgetRefresher.scope.launch { WidgetRefresher.refreshAll(appContext) }
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
    }

    private companion object {
        const val NOTIFICATION_PERMISSION_REQUEST = 1
        val HOUR_LABELS = (0..23).map { "%02d:00".format(it) }
    }
}
