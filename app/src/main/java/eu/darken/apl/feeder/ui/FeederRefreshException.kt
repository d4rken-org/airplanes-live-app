package eu.darken.apl.feeder.ui

import android.content.Context
import eu.darken.apl.R
import eu.darken.apl.common.ca.toCaString
import eu.darken.apl.common.error.HasLocalizedError
import eu.darken.apl.common.error.LocalizedError
import eu.darken.apl.feeder.core.FeederRefreshFailure
import retrofit2.HttpException
import java.io.IOException

class FeederRefreshException(cause: Throwable) :
    RuntimeException("Feeder refresh failed", cause), HasLocalizedError {

    override fun getLocalizedError(context: Context) = LocalizedError(
        throwable = this,
        label = R.string.feeder_refresh_failed_title.toCaString(),
        description = when (val error = cause) {
            is FeederRefreshFailure -> R.string.feeder_refresh_failed_some_x_of_y.toCaString(
                error.failed.size,
                error.total,
            )
            is HttpException -> R.string.feeder_refresh_failed_http_x.toCaString(error.code())
            else -> R.string.feeder_refresh_failed_msg.toCaString()
        },
    )

    companion object {
        /**
         * Network failures get a localized message, anything else stays as it is. When only some
         * feeders failed, the others were updated, and the message says so.
         */
        fun wrap(error: Throwable): Throwable = when (error) {
            is FeederRefreshFailure -> if (error.isPartial) FeederRefreshException(error) else wrap(error.cause!!)
            is IOException, is HttpException -> FeederRefreshException(error)
            else -> error
        }
    }
}
