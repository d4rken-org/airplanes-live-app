package eu.darken.apl.feeder.ui

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.feeder.core.FeederObservation
import eu.darken.apl.feeder.core.server.FeederServerState
import eu.darken.apl.feeder.core.server.FeederServerView
import eu.darken.apl.server.api.FeederMonitorEntry
import io.kotest.matchers.shouldBe
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

    private fun setServerScreen(
        server: FeederServerView,
        isPro: Boolean = false,
        onLinkFeeder: () -> Unit = {},
        header: FeederListViewModel.Header = FeederListViewModel.Header.ALL_ONLINE,
    ) = composeRule.setContent {
        PreviewWrapper {
            FeederListScreen(
                state = FeederListViewModel.State(
                    feeders = listOf(
                        FeederListViewModel.FeederItem(
                            feeder = mockFeeder(id = FEEDER_ID).copy(server = server),
                            isOffline = false,
                        )
                    ),
                    feederCount = 1,
                    isPro = isPro,
                    header = header,
                ),
                onRefresh = {},
                onAddFeeder = {},
                onFeederClick = {},
                onSortModeSelected = {},
                onLinkFeeder = onLinkFeeder,
                onShowOnMap = {},
                onStartFeeding = {},
            )
        }
    }

    private fun entry(
        status: String = "active",
        health: String = "online",
        pauseReason: String? = null,
        healthSince: Long? = null,
        problems: List<String> = emptyList(),
        unknownReason: String? = null,
        offlineAfterMinutes: Int = 60,
        createdAt: Long = 0,
        lastSampleAt: Long? = null,
        lastSuccessAt: Long? = null,
        lastConnectedAt: Long? = null,
    ) = FeederMonitorEntry(
        feederId = FEEDER_ID,
        monitorId = "m",
        offlineAfterMinutes = offlineAfterMinutes,
        status = status,
        pauseReason = pauseReason,
        createdAt = createdAt,
        updatedAt = createdAt,
        health = health,
        unknownReason = unknownReason,
        healthSince = healthSince,
        problems = problems,
        lastSampleAt = lastSampleAt,
        lastSuccessAt = lastSuccessAt,
        lastConnectedAt = lastConnectedAt,
        eventSeq = 0,
    )

    private fun pending(
        offlineAfterMinutes: Int = 60,
        lastSampleAt: Long? = null,
        lastSuccessAt: Long? = null,
    ) = entry(
        health = "unknown",
        unknownReason = "pending",
        offlineAfterMinutes = offlineAfterMinutes,
        createdAt = now.minusSeconds(600).toEpochMilli(),
        lastSampleAt = lastSampleAt,
        lastSuccessAt = lastSuccessAt,
    )

    @Test
    fun `a free user's feeder over the limit is not monitored and offers to raise the limit`() {
        var linkClicks = 0
        setServerScreen(
            FeederServerView(FEEDER_ID, entry(status = "paused", health = "unknown", pauseReason = "over_limit")),
            onLinkFeeder = { linkClicks++ },
        )

        composeRule.onNodeWithText("Not monitored, feeder limit reached").assertExists()
        composeRule.onNodeWithText("Raise limit").performClick()
        linkClicks shouldBe 1
    }

    @Test
    fun `a feeder the server refused for the stored limit reads the same`() {
        setServerScreen(FeederServerView(FEEDER_ID, refusal = FeederServerState.Refusal.STORAGE_LIMIT))

        composeRule.onNodeWithText("Not monitored, feeder limit reached").assertExists()
        composeRule.onNodeWithText("Raise limit").assertExists()
    }

    @Test
    fun `a Pro user's feeder over the limit only says so`() {
        setServerScreen(
            FeederServerView(FEEDER_ID, entry(status = "paused", health = "unknown", pauseReason = "over_limit")),
            isPro = true,
        )

        composeRule.onNodeWithText("Not monitored, feeder limit reached").assertExists()
        composeRule.onNodeWithText("Raise limit").assertDoesNotExist()
    }

    @Test
    fun `a feeder the server reports offline says since when`() {
        setServerScreen(
            FeederServerView(FEEDER_ID, entry(health = "offline", healthSince = now.minusSeconds(7200).toEpochMilli()))
        )

        composeRule.onNodeWithText("Offline since", substring = true).assertExists()
    }

    @Test
    fun `a feeder with problems lists their readable names`() {
        setServerScreen(FeederServerView(FEEDER_ID, entry(problems = listOf("mlat_missing", "flapping"))))

        composeRule.onNodeWithText("MLAT client missing, reconnecting repeatedly").assertExists()
    }

    @Test
    fun `a feeder never checked by anyone is promised a check soon`() {
        setServerScreen(FeederServerView(FEEDER_ID, pending()))

        composeRule.onNodeWithText("Not checked yet, this normally takes about a minute").assertExists()
    }

    @Test
    fun `a feeder only checked before this monitor is not checked yet without a promise`() {
        setServerScreen(FeederServerView(FEEDER_ID, pending(lastSampleAt = now.minusSeconds(3600).toEpochMilli())))

        composeRule.onNodeWithText("Not checked yet").assertExists()
    }

    @Test
    fun `a checked feeder without data says when it counts as offline`() {
        val checkedAt = now.minusSeconds(120).toEpochMilli()
        setServerScreen(
            FeederServerView(FEEDER_ID, pending(offlineAfterMinutes = 2880, lastSampleAt = checkedAt, lastSuccessAt = checkedAt))
        )

        composeRule.onNodeWithText("No data from this feeder yet", substring = true).assertExists()
        composeRule.onNodeWithText("Reported offline after 48 hours", substring = true).assertExists()
    }

    @Test
    fun `a check timestamp ahead of the device clock reads as just checked`() {
        val checkedAt = now.plusSeconds(120).toEpochMilli()
        setServerScreen(
            FeederServerView(FEEDER_ID, pending(offlineAfterMinutes = 2880, lastSampleAt = checkedAt, lastSuccessAt = checkedAt))
        )

        composeRule.onNodeWithText("Checked 0 minutes ago", substring = true).assertExists()
    }

    @Test
    fun `a threshold with hours and minutes keeps both`() {
        val checkedAt = now.minusSeconds(120).toEpochMilli()
        setServerScreen(
            FeederServerView(FEEDER_ID, pending(offlineAfterMinutes = 90, lastSampleAt = checkedAt, lastSuccessAt = checkedAt))
        )

        composeRule.onNodeWithText("1 hour", substring = true).assertExists()
        composeRule.onNodeWithText("30 minutes", substring = true).assertExists()
    }

    @Test
    fun `a feeder whose checks have not succeeded says so`() {
        setServerScreen(FeederServerView(FEEDER_ID, pending(lastSampleAt = now.toEpochMilli())))

        composeRule.onNodeWithText("No check has succeeded yet").assertExists()
    }

    @Test
    fun `a waiting feeder gets the waiting header`() {
        val feeder = mockFeeder(id = FEEDER_ID).copy(server = FeederServerView(FEEDER_ID, pending()))
        setServerScreen(
            feeder.server!!,
            header = FeederListViewModel.headerOf(listOf(feeder), mapOf(feeder.id to false)),
        )

        composeRule.onNodeWithText("Some feeders have no result yet").assertExists()
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

    companion object {
        private const val FEEDER_ID = "0a1b2c3d-0000-4000-8000-000000000001"
    }
}
