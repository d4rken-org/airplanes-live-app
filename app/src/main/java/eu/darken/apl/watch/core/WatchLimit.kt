package eu.darken.apl.watch.core

import android.content.Context
import eu.darken.apl.R
import eu.darken.apl.common.ca.toCaString
import eu.darken.apl.common.error.HasLocalizedError
import eu.darken.apl.common.error.LocalizedError

data class WatchLimit(
    /** What [max] counts. */
    val count: Int,
    /** The active cap when the user is shown it, null is none shown. */
    val max: Int?,
    /** With the server's watch list: watches the tier evaluates, against its active limit. */
    val active: Cap? = null,
    /** With the server's watch list: every watch, against its stored limit. */
    val stored: Cap? = null,
) {
    data class Cap(val count: Int, val max: Int) {
        val isReached: Boolean
            get() = count >= max
    }

    enum class Kind {
        ACTIVE,
        STORED,
        ;
    }

    /** [isShown] is false for a cap the user is not shown a number for. */
    data class Reached(val kind: Kind, val max: Int, val isShown: Boolean)

    /** The cap that stops adding, also one that is not shown; null while adding is possible. */
    val reached: Reached?
        get() = when {
            max != null && count >= max -> Reached(Kind.ACTIVE, max, isShown = true)
            active?.isReached == true -> Reached(Kind.ACTIVE, active.max, isShown = false)
            stored?.isReached == true -> Reached(Kind.STORED, stored.max, isShown = max != null)
            else -> null
        }

    val canAdd: Boolean
        get() = reached == null
}

class WatchLimitReachedException(
    val max: Int,
    val kind: WatchLimit.Kind = WatchLimit.Kind.ACTIVE,
    val isShown: Boolean = true,
) : IllegalStateException("Watch limit of $max reached ($kind)"), HasLocalizedError {

    constructor(reached: WatchLimit.Reached) : this(reached.max, reached.kind, reached.isShown)

    override fun getLocalizedError(context: Context) = LocalizedError(
        throwable = this,
        label = R.string.watch_limit_reached_title.toCaString(),
        description = when (kind) {
            WatchLimit.Kind.ACTIVE -> if (isShown) {
                R.string.watch_limit_reached_msg.toCaString(max)
            } else {
                R.string.watch_limit_active_reached_unnumbered_msg.toCaString()
            }

            WatchLimit.Kind.STORED -> if (isShown) {
                R.string.watch_limit_stored_reached_msg.toCaString(max)
            } else {
                R.string.watch_limit_stored_reached_unnumbered_msg.toCaString()
            }
        },
    )
}
