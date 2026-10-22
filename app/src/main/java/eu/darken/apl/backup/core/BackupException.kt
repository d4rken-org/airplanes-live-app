package eu.darken.apl.backup.core

import android.content.Context
import eu.darken.apl.R
import eu.darken.apl.common.ca.CaString
import eu.darken.apl.common.ca.toCaString
import eu.darken.apl.common.error.HasLocalizedError
import eu.darken.apl.common.error.LocalizedError

sealed class BackupException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause), HasLocalizedError {

    class Invalid(message: String, cause: Throwable? = null) : BackupException(message, cause)

    class CannotOpen(message: String, cause: Throwable? = null) : BackupException(message, cause)

    class NewerVersion(val version: Int) : BackupException("Unsupported backup version $version")

    class TooLarge(val maxSizeMb: Long) : BackupException("Backup file exceeds maximum size of ${maxSizeMb}MB")

    private val description: CaString
        get() = when (this) {
            is Invalid -> R.string.backup_error_invalid.toCaString()
            is CannotOpen -> R.string.backup_error_cannot_open.toCaString()
            is NewerVersion -> R.string.backup_error_unsupported_version.toCaString()
            is TooLarge -> R.string.backup_error_file_too_large_x.toCaString(maxSizeMb)
        }

    override fun getLocalizedError(context: Context) = LocalizedError(
        throwable = this,
        label = R.string.common_error_label.toCaString(),
        description = description,
    )
}
