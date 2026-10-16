package eu.darken.apl.watch.core

import android.content.Context
import eu.darken.apl.R
import eu.darken.apl.common.ca.toCaString
import eu.darken.apl.common.error.HasLocalizedError
import eu.darken.apl.common.error.LocalizedError

data class WatchLimit(
    val count: Int,
    /** Null is no cap. */
    val max: Int?,
) {
    val canAdd: Boolean
        get() = max == null || count < max
}

class WatchLimitReachedException(val max: Int) : IllegalStateException("Watch limit of $max reached"),
    HasLocalizedError {
    override fun getLocalizedError(context: Context) = LocalizedError(
        throwable = this,
        label = R.string.watch_limit_reached_title.toCaString(),
        description = R.string.watch_limit_reached_msg.toCaString(max),
    )
}
