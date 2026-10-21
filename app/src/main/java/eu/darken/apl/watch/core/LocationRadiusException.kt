package eu.darken.apl.watch.core

import android.content.Context
import eu.darken.apl.R
import eu.darken.apl.common.ca.toCaString
import eu.darken.apl.common.error.HasLocalizedError
import eu.darken.apl.common.error.LocalizedError

/** The radius left the allowed range between the editor showing it and the save, e.g. after the limit dropped. */
class LocationRadiusException(radiusInMeters: Float, maxRadiusKm: Int) :
    IllegalArgumentException("Radius $radiusInMeters m is outside 2..$maxRadiusKm km"), HasLocalizedError {

    override fun getLocalizedError(context: Context) = LocalizedError(
        throwable = this,
        label = R.string.watch_location_radius_invalid_title.toCaString(),
        description = R.string.watch_location_radius_invalid_msg.toCaString(),
    )
}
