package dev.ww40.buildertimer.data

import android.content.Context

data class Settings(
    /** The export has no player name, so the user types it in once on the setup screen. */
    val playerName: String? = null,
    val quietHoursEnabled: Boolean = false,
    val quietStartHour: Int = DEFAULT_QUIET_START,
    val quietEndHour: Int = DEFAULT_QUIET_END,
    val showFinishTime: Boolean = false,
    val showTimeToMax: Boolean = false,
    val maxForWholeGame: Boolean = false,
    val darkMode: Boolean = false,
) {
    val maxTarget: MaxTarget get() = if (maxForWholeGame) MaxTarget.GAME else MaxTarget.TOWN_HALL

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
        showTimeToMax = prefs.getBoolean(KEY_SHOW_TIME_TO_MAX, false),
        maxForWholeGame = prefs.getBoolean(KEY_MAX_FOR_WHOLE_GAME, false),
        darkMode = prefs.getBoolean(KEY_DARK_MODE, false),
    )

    fun save(settings: Settings) {
        prefs.edit()
            .putString(KEY_NAME, settings.playerName?.trim())
            .putBoolean(KEY_QUIET_ENABLED, settings.quietHoursEnabled)
            .putInt(KEY_QUIET_START, settings.quietStartHour)
            .putInt(KEY_QUIET_END, settings.quietEndHour)
            .putBoolean(KEY_SHOW_FINISH_TIME, settings.showFinishTime)
            .putBoolean(KEY_SHOW_TIME_TO_MAX, settings.showTimeToMax)
            .putBoolean(KEY_MAX_FOR_WHOLE_GAME, settings.maxForWholeGame)
            .putBoolean(KEY_DARK_MODE, settings.darkMode)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "settings"
        const val KEY_NAME = "player_name"
        const val KEY_QUIET_ENABLED = "quiet_hours_enabled"
        const val KEY_QUIET_START = "quiet_start_hour"
        const val KEY_QUIET_END = "quiet_end_hour"
        const val KEY_SHOW_FINISH_TIME = "show_finish_time"
        const val KEY_SHOW_TIME_TO_MAX = "show_time_to_max"
        const val KEY_MAX_FOR_WHOLE_GAME = "max_for_whole_game"
        const val KEY_DARK_MODE = "dark_mode"
    }
}
