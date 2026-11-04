package eu.darken.apl.feeder.ui

import androidx.compose.ui.test.onNodeWithText
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.feeder.core.FeederObservation
import eu.darken.apl.feeder.ui.preview.mockFeeder
import org.junit.Test
import testhelper.BaseComposeTest
import java.time.Instant

class FeederListScreenTest : BaseComposeTest() {

    private val now = Instant.now()

    private fun setScreen(observation: FeederObservation?) = composeRule.setContent {
        PreviewWrapper {
            FeederListScreen(
                state = FeederListViewModel.State(
                    feeders = listOf(
                        FeederListViewModel.FeederItem(
                            feeder = mockFeeder().copy(observation = observation),
                            isOffline = false,
                        )
                    ),
                    feederCount = 1,
                ),
                onRefresh = {},
                onAddFeeder = {},
                onFeederClick = {},
                onSortModeSelected = {},
                onShowOnMap = {},
                onStartFeeding = {},
            )
        }
    }

    @Test
    fun `a failing check says so instead of a last seen time`() {
        setScreen(FeederObservation(lastFeedingAt = now).withFailure(now))

        composeRule.onNodeWithText("Last check failed").assertExists()
    }

    @Test
    fun `a connected feeder without messages is shown as idle`() {
        setScreen(FeederObservation(checkedAt = now, status = FeederObservation.Status.IDLE, lastFeedingAt = now))

        composeRule.onNodeWithText("Connected, no messages").assertExists()
    }

    @Test
    fun `a feeder that is not connected says when it last sent data`() {
        setScreen(
            FeederObservation(
                checkedAt = now,
                status = FeederObservation.Status.NOT_SEEN,
                lastFeedingAt = now.minusSeconds(7200),
            )
        )

        composeRule.onNodeWithText("Not connected, last data", substring = true).assertExists()
    }

    @Test
    fun `a feeder never seen sending is just not connected`() {
        setScreen(FeederObservation(checkedAt = now, status = FeederObservation.Status.NOT_SEEN))

        composeRule.onNodeWithText("Not connected").assertExists()
    }
}
