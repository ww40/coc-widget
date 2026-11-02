package dev.ww40.buildertimer

import android.content.Context
import android.content.Intent

object ClashApp {
    const val PACKAGE = "com.supercell.clashofclans"

    /** Null when Clash of Clans isn't installed. */
    fun launchIntent(context: Context): Intent? = context.packageManager.getLaunchIntentForPackage(PACKAGE)
}
