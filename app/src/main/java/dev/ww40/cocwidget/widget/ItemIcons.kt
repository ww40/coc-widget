package dev.ww40.cocwidget.widget

import android.annotation.SuppressLint
import android.content.Context

/** Looks up bundled item icons, which are named res/drawable-nodpi/icon_<dataId>.png. */
object ItemIcons {
    private val cache = mutableMapOf<Int, Int?>()

    // Name-based lookup lets icons be added as plain files without a hand-maintained ID table.
    @SuppressLint("DiscouragedApi")
    fun drawableRes(context: Context, dataId: Int): Int? = synchronized(cache) {
        cache.getOrPut(dataId) {
            context.resources.getIdentifier("icon_$dataId", "drawable", context.packageName).takeIf { it != 0 }
        }
    }
}
