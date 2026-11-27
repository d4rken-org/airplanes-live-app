package eu.darken.apl.search.ui

import android.content.Context
import androidx.compose.material3.SnackbarDuration
import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.R
import eu.darken.apl.watch.core.WatchLimitReachedException
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class SearchWatchMessageTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun message(event: SearchEvents.WatchOutcome) = searchWatchMessage(context, event)

    private val limitMessage get() = context.resources.getQuantityString(R.plurals.watch_limit_reached_msg, 10, 10)

    @Test
    fun `only new watches offer an undo`() {
        message(SearchEvents.WatchesAdded(setOf("a", "b"), existingCount = 0)) shouldBe SearchWatchMessage(
            text = "Added 2 to watchlist",
            action = SearchWatchMessage.Action.UNDO,
            duration = SnackbarDuration.Long,
        )
    }

    @Test
    fun `new watches beside one already watched aircraft say so`() {
        message(SearchEvents.WatchesAdded(setOf("a", "b"), existingCount = 1)) shouldBe SearchWatchMessage(
            text = "Added 2 to watchlist, 1 was already watched",
            action = SearchWatchMessage.Action.UNDO,
            duration = SnackbarDuration.Long,
        )
    }

    @Test
    fun `new watches beside several already watched aircraft say so`() {
        message(SearchEvents.WatchesAdded(setOf("a"), existingCount = 2)) shouldBe SearchWatchMessage(
            text = "Added 1 to watchlist, 2 were already watched",
            action = SearchWatchMessage.Action.UNDO,
            duration = SnackbarDuration.Long,
        )
    }

    @Test
    fun `one aircraft that was already watched has nothing to undo`() {
        message(SearchEvents.WatchesAdded(emptySet(), existingCount = 1)) shouldBe SearchWatchMessage(
            text = "1 was already watched",
            action = SearchWatchMessage.Action.NONE,
            duration = SnackbarDuration.Short,
        )
    }

    @Test
    fun `several aircraft that were all already watched have nothing to undo`() {
        message(SearchEvents.WatchesAdded(emptySet(), existingCount = 2)) shouldBe SearchWatchMessage(
            text = "All 2 were already watched",
            action = SearchWatchMessage.Action.NONE,
            duration = SnackbarDuration.Short,
        )
    }

    @Test
    fun `a limit reached before anything was added shows the limit message`() {
        message(SearchEvents.WatchLimitReached(WatchLimitReachedException(10), addedCount = 0)) shouldBe
            SearchWatchMessage(
                text = limitMessage,
                action = SearchWatchMessage.Action.UPGRADE,
                duration = SnackbarDuration.Long,
            )
    }

    @Test
    fun `a limit reached after some were added says how many made it`() {
        message(SearchEvents.WatchLimitReached(WatchLimitReachedException(10), addedCount = 3)) shouldBe
            SearchWatchMessage(
                text = "Added 3 to watchlist. $limitMessage",
                action = SearchWatchMessage.Action.UPGRADE,
                duration = SnackbarDuration.Long,
            )
    }
}
