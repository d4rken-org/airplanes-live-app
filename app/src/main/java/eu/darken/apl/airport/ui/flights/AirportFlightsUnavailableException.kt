package eu.darken.apl.airport.ui.flights

import android.content.Context
import eu.darken.apl.R
import eu.darken.apl.common.ca.toCaString
import eu.darken.apl.common.error.HasLocalizedError
import eu.darken.apl.common.error.LocalizedError

/** No list could be had, for a reason the user can do nothing about. */
class AirportFlightsUnavailableException(message: String) : RuntimeException(message), HasLocalizedError {

    override fun getLocalizedError(context: Context) = LocalizedError(
        throwable = this,
        label = R.string.server_error_title.toCaString(),
        description = R.string.server_error_unavailable_msg.toCaString(),
    )
}
