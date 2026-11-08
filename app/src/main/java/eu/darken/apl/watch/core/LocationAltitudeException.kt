package eu.darken.apl.watch.core

import android.content.Context
import eu.darken.apl.R
import eu.darken.apl.common.ca.toCaString
import eu.darken.apl.common.error.HasLocalizedError
import eu.darken.apl.common.error.LocalizedError

/** See [eu.darken.apl.watch.core.types.LocationWatch.altitudeBandAllowed]. */
class LocationAltitudeException(minAltitudeFt: Int?, maxAltitudeFt: Int?) :
    IllegalArgumentException("Altitude band $minAltitudeFt..$maxAltitudeFt ft is invalid"), HasLocalizedError {

    override fun getLocalizedError(context: Context) = LocalizedError(
        throwable = this,
        label = R.string.watch_location_altitude_invalid_title.toCaString(),
        description = R.string.watch_location_altitude_invalid_msg.toCaString(),
    )
}
