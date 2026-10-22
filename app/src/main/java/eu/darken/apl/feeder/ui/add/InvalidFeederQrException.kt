package eu.darken.apl.feeder.ui.add

import android.content.Context
import eu.darken.apl.R
import eu.darken.apl.common.ca.toCaString
import eu.darken.apl.common.error.HasLocalizedError
import eu.darken.apl.common.error.LocalizedError

class InvalidFeederQrException(cause: Throwable) :
    IllegalArgumentException("Invalid feeder QR code", cause), HasLocalizedError {

    override fun getLocalizedError(context: Context) = LocalizedError(
        throwable = this,
        label = R.string.feeder_list_invalid_qr_code_title.toCaString(),
        description = R.string.feeder_list_invalid_qr_code_message.toCaString(),
    )
}
