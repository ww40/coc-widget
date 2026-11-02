package dev.ww40.cocwidget.sync

import android.app.Activity
import android.content.ClipboardManager
import android.util.Log
import android.widget.Toast
import dev.ww40.cocwidget.data.ExportFormatException
import dev.ww40.cocwidget.data.ExportParser
import dev.ww40.cocwidget.data.GameData
import dev.ww40.cocwidget.data.SnapshotStore
import dev.ww40.cocwidget.notify.UpgradeAlarms
import dev.ww40.cocwidget.widget.WidgetRefresher
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Invisible activity launched by the widget's Sync button. Android 10+ only allows clipboard
 * reads from the focused app, so the read waits for window focus.
 */
class SyncActivity : Activity() {

    private var handled = false

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || handled) return
        handled = true
        toast(syncFromClipboard())
        finish()
    }

    private fun syncFromClipboard(): String {
        val text = readClipboard()
        return try {
            val snapshot = ExportParser(GameData.load(this)).parse(text)
            val store = SnapshotStore(this)
            store.load()?.let { UpgradeAlarms.cancel(this, it) }
            store.save(text)
            UpgradeAlarms.schedule(this, snapshot)
            val appContext = applicationContext
            WidgetRefresher.scope.launch { WidgetRefresher.refreshAll(appContext) }
            "Synced ${snapshot.allUpgrades.size} upgrades"
        } catch (e: ExportFormatException) {
            Log.w(TAG, "Clipboard isn't a village export (${text.length} chars)", e)
            "No village data on clipboard (${e.message}). In Clash: Settings → More Settings → Data Export → Copy"
        } catch (e: IOException) {
            Log.e(TAG, "Failed to save village export", e)
            "Couldn't save village data: ${e.message}"
        }
    }

    private fun readClipboard(): String {
        val clip = getSystemService(ClipboardManager::class.java).primaryClip ?: return ""
        if (clip.itemCount == 0) return ""
        return clip.getItemAt(0).coerceToText(this).toString()
    }

    private fun toast(message: String) = Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()

    private companion object {
        const val TAG = "SyncActivity"
    }
}
