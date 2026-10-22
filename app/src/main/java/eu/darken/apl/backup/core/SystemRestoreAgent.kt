package eu.darken.apl.backup.core

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.os.ParcelFileDescriptor

/**
 * Only learns that Android restored the app's data, the backup itself stays Android's file backup
 * (pinned by SystemRestoreAgentTest). Runs without the app's Application and Hilt, so it leaves
 * markers and touches no database.
 */
class SystemRestoreAgent : BackupAgent() {

    override fun onBackup(oldState: ParcelFileDescriptor?, data: BackupDataOutput?, newState: ParcelFileDescriptor?) = Unit

    override fun onRestore(data: BackupDataInput?, appVersionCode: Int, newState: ParcelFileDescriptor?) = Unit

    override fun onRestoreFinished() {
        SystemRestoreMarker.setAll(this)
    }
}
