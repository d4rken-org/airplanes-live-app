package eu.darken.apl.common.error

import android.content.Context
import eu.darken.apl.R
import eu.darken.apl.common.ca.CaString
import eu.darken.apl.common.ca.caString
import eu.darken.apl.common.ca.toCaString

interface HasLocalizedError {
    fun getLocalizedError(context: Context): LocalizedError
}

data class LocalizedError(
    val throwable: Throwable,
    val label: CaString,
    val description: CaString,
) {
    fun asText() = "$label:\n$description"
}

fun Throwable.localized(c: Context): LocalizedError = when (this) {
    is HasLocalizedError -> this.getLocalizedError(c)
    else -> LocalizedError(
        throwable = this,
        label = R.string.common_error_label.toCaString(),
        description = caString {
            c.getString(R.string.common_error_unexpected_msg_x, localizedMessage ?: getStackTracePeek())
        },
    )
}

private fun Throwable.getStackTracePeek() = this.stackTraceToString()
    .lines()
    .filterIndexed { index, _ -> index > 1 }
    .take(3)
    .joinToString("\n")