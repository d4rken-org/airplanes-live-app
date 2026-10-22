package eu.darken.apl.backup.core

import android.content.Context
import android.util.Log
import eu.darken.apl.common.debug.logging.logTag
import java.io.File
import java.io.IOException

/**
 * Left by [SystemRestoreAgent] when Android restored the app's data, one per database that holds state
 * from before the restore. Each database consumes its own on its next open.
 */
enum class SystemRestoreMarker(private val fileName: String) {
    WATCH_CHECKS("watch_restored_by_system"),
    PENDING_OPERATIONS("operations_restored_by_system"),
    ;

    // Context.getNoBackupFilesDir() is never backed up, so a marker can't come back with a later restore
    private fun file(context: Context) = File(context.noBackupFilesDir, fileName)

    fun set(context: Context) {
        try {
            file(context).createNewFile()
        } catch (e: IOException) {
            // The app's loggers are not installed while Android restores, only the platform log is
            Log.e(TAG, "set($this) failed", e)
        }
    }

    fun isSet(context: Context): Boolean = file(context).exists()

    fun clear(context: Context): Boolean = file(context).delete()

    companion object {
        fun setAll(context: Context) = entries.forEach { it.set(context) }

        private val TAG = logTag("Backup", "SystemRestore")
    }
}
