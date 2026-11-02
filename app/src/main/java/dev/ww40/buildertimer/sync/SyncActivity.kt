package dev.ww40.buildertimer.sync

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipboardManager
import android.util.Log
import android.widget.Toast
import dev.ww40.buildertimer.ClashApp
import dev.ww40.buildertimer.R
import dev.ww40.buildertimer.data.ExportFormatException
import dev.ww40.buildertimer.data.ExportParser
import dev.ww40.buildertimer.data.GameData
import dev.ww40.buildertimer.data.SnapshotStore
import dev.ww40.buildertimer.data.VillageSnapshot
import dev.ww40.buildertimer.data.formatAgo
import dev.ww40.buildertimer.notify.UpgradeAlarms
import dev.ww40.buildertimer.widget.WidgetRefresher
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
        syncFromClipboard()
    }

    private fun syncFromClipboard() {
        val text = readClipboard()
        val snapshot = try {
            ExportParser(GameData.load(this)).parse(text)
        } catch (e: ExportFormatException) {
            Log.w(TAG, "Clipboard isn't a village export (${text.length} chars)", e)
            showInvalidExportDialog()
            return
        }
        val nowEpochSec = System.currentTimeMillis() / 1000
        if (snapshot.isStale(nowEpochSec)) {
            showStaleExportDialog(text, snapshot, nowEpochSec)
            return
        }
        save(text, snapshot)
        finish()
    }

    private fun save(text: String, snapshot: VillageSnapshot) {
        try {
            val store = SnapshotStore(this)
            store.load()?.let { UpgradeAlarms.cancel(this, it) }
            store.save(text)
            UpgradeAlarms.schedule(this, snapshot)
            val appContext = applicationContext
            WidgetRefresher.scope.launch { WidgetRefresher.refreshAll(appContext) }
            toast("Synced ${snapshot.allUpgrades.size} upgrades")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to save village export", e)
            toast("Couldn't save village data: ${e.message}")
        }
    }

    private fun showInvalidExportDialog() {
        clashDialog()
            .setTitle(R.string.sync_error_title)
            .setMessage(R.string.sync_error_message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showStaleExportDialog(text: String, snapshot: VillageSnapshot, nowEpochSec: Long) {
        val age = formatAgo(nowEpochSec - snapshot.exportedAtEpochSec)
        clashDialog()
            .setTitle(R.string.sync_stale_title)
            .setMessage(getString(R.string.sync_stale_message, age))
            .setPositiveButton(R.string.sync_use_anyway) { _, _ -> save(text, snapshot) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Dialog that closes this activity when dismissed and offers to open Clash if it's installed. */
    private fun clashDialog(): AlertDialog.Builder {
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setOnDismissListener { finish() }
        ClashApp.launchIntent(this)?.let { launchClash ->
            dialog.setNeutralButton(R.string.open_clash) { _, _ -> startActivity(launchClash) }
        }
        return dialog
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
