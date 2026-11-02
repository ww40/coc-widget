package dev.ww40.buildertimer.widget

import android.annotation.SuppressLint
import android.content.Context

/**
 * Looks up bundled item icons in res/drawable-nodpi: icon_<dataId>_<level> for buildings and
 * traps (one per level), falling back to icon_<dataId>.
 */
object ItemIcons {
    private val cache = mutableMapOf<String, Int?>()

    fun drawableRes(context: Context, dataId: Int, level: Int): Int? =
        lookup(context, "icon_${dataId}_$level") ?: lookup(context, "icon_$dataId")

    // Name-based lookup lets icons be added as plain files without a hand-maintained ID table.
    @SuppressLint("DiscouragedApi")
    private fun lookup(context: Context, name: String): Int? = synchronized(cache) {
        cache.getOrPut(name) {
            context.resources.getIdentifier(name, "drawable", context.packageName).takeIf { it != 0 }
        }
    }
}
