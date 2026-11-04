package eu.darken.apl.backup.ui

sealed interface BackupRestoreEvents {
    data object RequestNotificationPermission : BackupRestoreEvents
}
