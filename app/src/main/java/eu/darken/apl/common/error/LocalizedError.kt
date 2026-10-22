package eu.darken.apl.common.error

import android.content.Context
import eu.darken.apl.R
import eu.darken.apl.common.ca.CaString
import eu.darken.apl.common.ca.caString
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerCodes

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

fun Throwable.localized(c: Context): LocalizedError = when {
    this is ServerApiException && code == ServerCodes.APP_UPDATE_REQUIRED -> LocalizedError(
        throwable = this,
        label = caString { c.getString(R.string.update_prompt_required_title) },
        description = caString { c.getString(R.string.update_required_inline_msg) },
    )

    this is HasLocalizedError -> this.getLocalizedError(c)
    localizedMessage != null -> LocalizedError(
        throwable = this,
        label = caString { "${c.getString(R.string.common_error_label)}: ${this@localized::class.simpleName!!}" },
        description = caString { localizedMessage ?: getStackTracePeek() },
    )

    else -> LocalizedError(
        throwable = this,
        label = caString { "${c.getString(R.string.common_error_label)}: ${this@localized::class.simpleName!!}" },
        description = caString { getStackTracePeek() },
    )
}

private fun Throwable.getStackTracePeek() = this.stackTraceToString()
    .lines()
    .filterIndexed { index, _ -> index > 1 }
    .take(3)
    .joinToString("\n")