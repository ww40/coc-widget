package dev.ww40.buildertimer

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.StatusBarManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
import android.widget.Toast
import dev.ww40.buildertimer.data.Settings
import dev.ww40.buildertimer.data.SettingsStore
import dev.ww40.buildertimer.notify.UpgradeAlarms
import dev.ww40.buildertimer.notify.UpgradeNotifier
import dev.ww40.buildertimer.sync.SyncActivity
import dev.ww40.buildertimer.sync.SyncTileService
import dev.ww40.buildertimer.widget.CompactWidgetReceiver
import dev.ww40.buildertimer.widget.VillageWidgetReceiver
import dev.ww40.buildertimer.widget.WidgetRefresher
import kotlinx.coroutines.launch

/** Setup screen: explains the sync flow, edits settings and asks for notification permission. */
class MainActivity : Activity() {

    private val store by lazy { SettingsStore(this) }
    private val playerName by lazy { findViewById<EditText>(R.id.player_name) }
    private val showFinishTime by lazy { findViewById<Switch>(R.id.show_finish_time) }
    private val darkMode by lazy { findViewById<Switch>(R.id.dark_mode) }
    private val showTimeToMax by lazy { findViewById<Switch>(R.id.show_time_to_max) }
    private val maxForWholeGame by lazy { findViewById<Switch>(R.id.max_for_whole_game) }
    private val showBuilderBase by lazy { findViewById<Switch>(R.id.show_builder_base) }
    private val showLabOnCompact by lazy { findViewById<Switch>(R.id.show_lab_on_compact) }
    private val notifyBuilders by lazy { findViewById<Switch>(R.id.notify_builders) }
    private val notifyLab by lazy { findViewById<Switch>(R.id.notify_lab) }
    private val notifyPets by lazy { findViewById<Switch>(R.id.notify_pets) }
    private val notifyBuilderBase by lazy { findViewById<Switch>(R.id.notify_builder_base) }
    private val notifyBoostEnded by lazy { findViewById<Switch>(R.id.notify_boost_ended) }
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
        findViewById<Button>(R.id.add_full_widget_button).setOnClickListener { addWidget(VillageWidgetReceiver::class.java) }
        findViewById<Button>(R.id.add_small_widget_button).setOnClickListener { addWidget(CompactWidgetReceiver::class.java) }
        findViewById<Button>(R.id.add_sync_tile_button).setOnClickListener { addSyncTile() }
        findViewById<Button>(R.id.test_notification_button).setOnClickListener { sendTestNotification() }
        findViewById<Button>(R.id.save_settings_button).setOnClickListener { saveSettings() }
        showTimeToMax.setOnCheckedChangeListener { _, isChecked -> maxForWholeGame.isEnabled = isChecked }
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
        darkMode.isChecked = settings.darkMode
        showTimeToMax.isChecked = settings.showTimeToMax
        maxForWholeGame.isChecked = settings.maxForWholeGame
        maxForWholeGame.isEnabled = settings.showTimeToMax
        showBuilderBase.isChecked = settings.showBuilderBase
        showLabOnCompact.isChecked = settings.showLabOnCompact
        notifyBuilders.isChecked = settings.notifyBuilders
        notifyLab.isChecked = settings.notifyLab
        notifyPets.isChecked = settings.notifyPets
        notifyBuilderBase.isChecked = settings.notifyBuilderBase
        notifyBoostEnded.isChecked = settings.notifyBoostEnded
        quietEnabled.isChecked = settings.quietHoursEnabled
        quietStart.setSelection(settings.quietStartHour)
        quietEnd.setSelection(settings.quietEndHour)
    }

    private fun enteredSettings(): Settings = store.load().copy(
        playerName = playerName.text.toString().trim().takeIf { it.isNotEmpty() },
        showFinishTime = showFinishTime.isChecked,
        darkMode = darkMode.isChecked,
        showTimeToMax = showTimeToMax.isChecked,
        maxForWholeGame = maxForWholeGame.isChecked,
        showBuilderBase = showBuilderBase.isChecked,
        showLabOnCompact = showLabOnCompact.isChecked,
        notifyBuilders = notifyBuilders.isChecked,
        notifyLab = notifyLab.isChecked,
        notifyPets = notifyPets.isChecked,
        notifyBuilderBase = notifyBuilderBase.isChecked,
        notifyBoostEnded = notifyBoostEnded.isChecked,
        quietHoursEnabled = quietEnabled.isChecked,
        quietStartHour = quietStart.selectedItemPosition,
        quietEndHour = quietEnd.selectedItemPosition,
    )

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (enteredSettings() == store.load()) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
            return
        }
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.unsaved_changes_title)
            .setMessage(R.string.unsaved_changes_message)
            .setPositiveButton(R.string.save_changes) { _, _ ->
                saveSettings()
                finish()
            }
            .setNegativeButton(R.string.keep_existing) { _, _ -> finish() }
            .show()
    }

    private fun saveSettings() {
        store.save(enteredSettings())
        UpgradeAlarms.rescheduleSaved(this)
        val appContext = applicationContext
        WidgetRefresher.scope.launch { WidgetRefresher.refreshAll(appContext) }
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
    }

    private fun addWidget(receiver: Class<*>) {
        val widgets = AppWidgetManager.getInstance(this)
        val requested = widgets.isRequestPinAppWidgetSupported &&
            widgets.requestPinAppWidget(ComponentName(this, receiver), null, null)
        if (!requested) Toast.makeText(this, R.string.add_widget_unsupported, Toast.LENGTH_LONG).show()
    }

    private fun addSyncTile() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            showTileSteps()
            return
        }
        getSystemService(StatusBarManager::class.java).requestAddTileService(
            ComponentName(this, SyncTileService::class.java),
            getString(R.string.sync_tile_label),
            Icon.createWithResource(this, R.drawable.ic_sync_tile),
            mainExecutor,
        ) { result ->
            when (result) {
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ->
                    Toast.makeText(this, R.string.sync_tile_added, Toast.LENGTH_SHORT).show()
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED ->
                    Toast.makeText(this, R.string.sync_tile_already_added, Toast.LENGTH_SHORT).show()
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> Unit
                else -> showTileSteps()
            }
        }
    }

    private fun showTileSteps() {
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.sync_tile_steps_title)
            .setMessage(R.string.sync_tile_steps)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun sendTestNotification() {
        if (!UpgradeNotifier.canNotify(this)) {
            showNotificationsOffDialog()
            return
        }
        UpgradeNotifier.show(this, getString(R.string.test_notification_title), getString(R.string.test_notification_text))
    }

    private fun showNotificationsOffDialog() {
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.notifications_off_title)
            .setMessage(R.string.notifications_off_message)
            .setPositiveButton(R.string.open_notification_settings) { _, _ ->
                startActivity(
                    Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, packageName),
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
