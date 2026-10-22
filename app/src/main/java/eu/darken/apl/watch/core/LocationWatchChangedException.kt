package eu.darken.apl.watch.core

import android.content.Context
import eu.darken.apl.R
import eu.darken.apl.common.ca.toCaString
import eu.darken.apl.common.error.HasLocalizedError
import eu.darken.apl.common.error.LocalizedError

/** The area was changed elsewhere after an editor opened, saving the editor's would undo that. */
class LocationWatchChangedException(id: WatchId) :
    IllegalStateException("Location watch $id changed since editing began"), HasLocalizedError {

    override fun getLocalizedError(context: Context) = LocalizedError(
        throwable = this,
        label = R.string.watch_location_changed_title.toCaString(),
        description = R.string.watch_location_changed_msg.toCaString(),
    )
}
