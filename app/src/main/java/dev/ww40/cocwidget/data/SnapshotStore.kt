package dev.ww40.cocwidget.data

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException

/** Persists the raw export text; it's re-parsed on read so game-data updates apply to old syncs. */
class SnapshotStore(private val context: Context) {

    private val file: File get() = File(context.filesDir, FILE_NAME)

    fun save(exportJson: String) {
        file.writeText(exportJson)
    }

    fun load(): VillageSnapshot? {
        if (!file.exists()) return null
        return try {
            ExportParser(GameData.load(context)).parse(file.readText())
        } catch (e: IOException) {
            Log.e(TAG, "Failed to read saved export at ${file.path}", e)
            null
        } catch (e: ExportFormatException) {
            Log.e(TAG, "Saved export at ${file.path} no longer parses", e)
            null
        }
    }

    private companion object {
        const val FILE_NAME = "village_export.json"
        const val TAG = "SnapshotStore"
    }
}
