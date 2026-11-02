package dev.ww40.buildertimer.data

import android.content.Context

data class Settings(
    /** The export has no player name, so the user types it in once on the setup screen. */
    val playerName: String? = null,
    val quietHoursEnabled: Boolean = false,
    val quietStartHour: Int = DEFAULT_QUIET_START,
    val quietEndHour: Int = DEFAULT_QUIET_END,
    val showFinishTime: Boolean = false,
) {
    val quietHours: QuietHours? get() = if (quietHoursEnabled) QuietHours(quietStartHour, quietEndHour) else null

    companion object {
        const val DEFAULT_QUIET_START = 23
        const val DEFAULT_QUIET_END = 7
    }
}

class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): Settings = Settings(
        playerName = prefs.getString(KEY_NAME, null)?.takeIf { it.isNotBlank() },
        quietHoursEnabled = prefs.getBoolean(KEY_QUIET_ENABLED, false),
        quietStartHour = prefs.getInt(KEY_QUIET_START, Settings.DEFAULT_QUIET_START),
        quietEndHour = prefs.getInt(KEY_QUIET_END, Settings.DEFAULT_QUIET_END),
        showFinishTime = prefs.getBoolean(KEY_SHOW_FINISH_TIME, false),
    )

    fun save(settings: Settings) {
        prefs.edit()
            .putString(KEY_NAME, settings.playerName?.trim())
            .putBoolean(KEY_QUIET_ENABLED, settings.quietHoursEnabled)
            .putInt(KEY_QUIET_START, settings.quietStartHour)
            .putInt(KEY_QUIET_END, settings.quietEndHour)
            .putBoolean(KEY_SHOW_FINISH_TIME, settings.showFinishTime)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "settings"
        const val KEY_NAME = "player_name"
        const val KEY_QUIET_ENABLED = "quiet_hours_enabled"
        const val KEY_QUIET_START = "quiet_start_hour"
        const val KEY_QUIET_END = "quiet_end_hour"
        const val KEY_SHOW_FINISH_TIME = "show_finish_time"
    }
}
