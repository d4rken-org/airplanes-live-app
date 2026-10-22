package eu.darken.apl.search.ui

import android.content.Context
import androidx.compose.material3.SnackbarDuration
import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.R
import eu.darken.apl.main.core.request.OperationFailedException
import eu.darken.apl.search.core.SearchRepo
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerCodes
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class SearchErrorMessageTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun message(error: Throwable, charged: SearchRepo.Charged = SearchRepo.Charged.SEARCH) =
        searchErrorMessage(context, SearchViewModel.SearchError(error, charged))

    @Test
    fun `a network failure says the internet is unavailable`() {
        message(IOException("reset")) shouldBe SearchErrorMessage(
            text = context.getString(
                R.string.search_error_generic,
                context.getString(R.string.common_internet_unavailable_message),
            ),
            duration = SnackbarDuration.Short,
        )
    }

    @Test
    fun `an operation the server could not finish`() {
        message(OperationFailedException(ServerCodes.OPERATION_EXPIRED)) shouldBe SearchErrorMessage(
            text = context.getString(R.string.search_error_operation_failed_msg),
            duration = SnackbarDuration.Short,
        )
    }

    @Test
    fun `a used up allowance names what was charged, even when it came as a 429`() {
        val exhausted = ServerApiException(code = ServerCodes.DAILY_ALLOWANCE_EXHAUSTED, status = 429)

        message(exhausted, SearchRepo.Charged.SEARCH) shouldBe SearchErrorMessage(
            text = context.getString(R.string.search_banner_exhausted_title),
            duration = SnackbarDuration.Long,
        )
        message(exhausted, SearchRepo.Charged.VIEWING) shouldBe SearchErrorMessage(
            text = context.getString(R.string.search_banner_exhausted_title_viewing),
            duration = SnackbarDuration.Long,
        )
    }

    @Test
    fun `any other 429 is a rate limit`() {
        message(ServerApiException(code = ServerCodes.QUOTA_EXCEEDED, status = 429)) shouldBe SearchErrorMessage(
            text = context.getString(R.string.search_error_rate_limited),
            duration = SnackbarDuration.Long,
        )
    }

    @Test
    fun `another server refusal shows the server's message`() {
        val error = ServerApiException(code = ServerCodes.TIER_RESTRICTED, status = 403)

        message(error) shouldBe SearchErrorMessage(
            text = context.getString(
                R.string.search_error_generic,
                context.getString(R.string.server_error_tier_restricted_msg),
            ),
            duration = SnackbarDuration.Short,
        )
    }

    @Test
    fun `anything else is a failed search`() {
        message(IllegalStateException("bug")) shouldBe SearchErrorMessage(
            text = context.getString(R.string.search_error_failed_msg),
            duration = SnackbarDuration.Short,
        )
    }
}
