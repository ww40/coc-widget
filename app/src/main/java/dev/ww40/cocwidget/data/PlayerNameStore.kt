package dev.ww40.cocwidget.data

import android.content.Context

/** The export has no player name, so the user types it in once on the setup screen. */
class PlayerNameStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): String? = prefs.getString(KEY_NAME, null)?.takeIf { it.isNotBlank() }

    fun save(name: String) {
        prefs.edit().putString(KEY_NAME, name.trim()).apply()
    }

    private companion object {
        const val PREFS_NAME = "settings"
        const val KEY_NAME = "player_name"
    }
}
