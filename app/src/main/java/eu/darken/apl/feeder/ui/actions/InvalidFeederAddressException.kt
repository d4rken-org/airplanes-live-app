package eu.darken.apl.feeder.ui.actions

import android.content.Context
import eu.darken.apl.R
import eu.darken.apl.common.ca.toCaString
import eu.darken.apl.common.error.HasLocalizedError
import eu.darken.apl.common.error.LocalizedError

class InvalidFeederAddressException(address: String, cause: Throwable? = null) :
    IllegalArgumentException("Invalid address: $address", cause), HasLocalizedError {

    override fun getLocalizedError(context: Context) = LocalizedError(
        throwable = this,
        label = R.string.feeder_address_invalid_title.toCaString(),
        description = R.string.feeder_address_invalid_msg.toCaString(),
    )
}
