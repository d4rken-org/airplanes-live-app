package eu.darken.apl.search.ui

import android.content.Context
import androidx.compose.material3.SnackbarDuration
import eu.darken.apl.R
import eu.darken.apl.common.error.localized
import eu.darken.apl.main.core.request.OperationFailedException
import eu.darken.apl.search.core.SearchRepo
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerCodes
import java.io.IOException

internal data class SearchErrorMessage(
    val text: String,
    val duration: SnackbarDuration,
)

internal fun searchErrorMessage(context: Context, searchError: SearchViewModel.SearchError): SearchErrorMessage {
    val apiError = searchError.error as? ServerApiException
    // The nearby lookup reports a used up allowance as a 429 too
    val isExhausted = apiError?.code == ServerCodes.DAILY_ALLOWANCE_EXHAUSTED
    val isRateLimited = !isExhausted && apiError?.status == 429
    val text = when {
        isExhausted -> context.getString(
            when (searchError.charged) {
                SearchRepo.Charged.VIEWING -> R.string.search_banner_exhausted_title_viewing
                SearchRepo.Charged.SEARCH -> R.string.search_banner_exhausted_title
            }
        )

        isRateLimited -> context.getString(R.string.search_error_rate_limited)
        else -> when (val error = searchError.error) {
            is ServerApiException -> context.getString(
                R.string.search_error_generic,
                error.localized(context).description.get(context),
            )

            is IOException -> context.getString(
                R.string.search_error_generic,
                context.getString(R.string.common_internet_unavailable_message),
            )

            is OperationFailedException -> context.getString(R.string.search_error_operation_failed_msg)
            else -> context.getString(R.string.search_error_failed_msg)
        }
    }
    return SearchErrorMessage(
        text = text,
        duration = if (isRateLimited || isExhausted) SnackbarDuration.Long else SnackbarDuration.Short,
    )
}
