package eu.darken.apl.feeder.ui

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.feeder.core.Feeder
import eu.darken.apl.feeder.core.link.FeederLinkRepo.FeederLinkState
import eu.darken.apl.feeder.core.server.FeederServerState
import eu.darken.apl.feeder.core.server.FeederServerView
import eu.darken.apl.server.api.FeederMonitorEntry
import eu.darken.apl.server.api.FeederMonitorLatest
import eu.darken.apl.server.api.LinkedFeeder
import eu.darken.apl.upgrade.UpgradeRepo
import io.kotest.matchers.shouldBe
import eu.darken.apl.feeder.ui.preview.mockFeeder
import org.junit.Test
import testhelper.BaseComposeTest
import java.time.Instant

class FeederListScreenTest : BaseComposeTest() {

    private val now = Instant.now()

    private fun setUnavailableScreen() = composeRule.setContent {
        val feeder = mockFeeder()
        PreviewWrapper {
            FeederListScreen(
                state = FeederListViewModel.State(
                    feeders = listOf(
                        FeederListViewModel.FeederItem(
                            feeder = feeder,
                            isOffline = false,
                        )
                    ),
                    feederCount = 1,
                    header = FeederListViewModel.headerOf(listOf(feeder), mapOf(feeder.id to false)),
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
        latest: FeederMonitorLatest? = null,
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
        latest = latest,
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

        composeRule.onNodeWithText("Not monitored, receiver limit reached").assertExists()
        composeRule.onNodeWithText("Raise limit").performClick()
        linkClicks shouldBe 1
    }

    @Test
    fun `a feeder the server refused for the stored limit reads the same`() {
        setServerScreen(FeederServerView(FEEDER_ID, refusal = FeederServerState.Refusal.STORAGE_LIMIT))

        composeRule.onNodeWithText("Not monitored, receiver limit reached").assertExists()
        composeRule.onNodeWithText("Raise limit").assertExists()
    }

    @Test
    fun `a Pro user's feeder over the limit only says so`() {
        setServerScreen(
            FeederServerView(FEEDER_ID, entry(status = "paused", health = "unknown", pauseReason = "over_limit")),
            isPro = true,
        )

        composeRule.onNodeWithText("Not monitored, receiver limit reached").assertExists()
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

        composeRule.onNodeWithText("No data from this receiver yet", substring = true).assertExists()
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

        composeRule.onNodeWithText("Some receivers have no result yet").assertExists()
    }

    @Test
    fun `a feeder without server data says monitoring is unavailable`() {
        setUnavailableScreen()

        composeRule.onNodeWithText("Monitoring unavailable").assertExists()
        composeRule.onNodeWithText("Some receivers could not be checked").assertExists()
    }

    @Test
    fun `a feeder without server data shows no rates and no monitored icon`() {
        setUnavailableScreen()

        composeRule.onNodeWithText("BEAST").assertDoesNotExist()
        composeRule.onNodeWithText("MSG/s", substring = true).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Monitored").assertDoesNotExist()
    }

    @Test
    fun `a monitored feeder shows its rate and the monitored icon`() {
        setServerScreen(FeederServerView(FEEDER_ID, entry()))

        composeRule.onNodeWithText("BEAST").assertExists()
        composeRule.onNodeWithContentDescription("Monitored").assertExists()
    }

    @Test
    fun `a feeder without a latency shows none`() {
        setServerScreen(FeederServerView(FEEDER_ID, entry(latest = FeederMonitorLatest(avgKbitPerSecond = 12.5))))

        composeRule.onNodeWithText(" ms", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a latency of zero is shown`() {
        setServerScreen(FeederServerView(FEEDER_ID, entry(latest = FeederMonitorLatest(rttMs = 0))))

        composeRule.onNodeWithText("0 ms").assertExists()
    }

    @Test
    fun `a feeder's latency is shown`() {
        setServerScreen(
            FeederServerView(FEEDER_ID, entry(latest = FeederMonitorLatest(avgKbitPerSecond = 12.5, rttMs = 37)))
        )

        composeRule.onNodeWithText("37 ms").assertExists()
    }

    private fun setTierScreen(
        isPro: Boolean,
        source: UpgradeRepo.Source?,
        linkState: FeederLinkState,
        feeders: List<FeederListViewModel.FeederItem> = emptyList(),
        registerable: Feeder? = null,
    ) = composeRule.setContent {
        PreviewWrapper {
            FeederListScreen(
                state = FeederListViewModel.State(
                    linkState = linkState,
                    feeders = feeders,
                    feederCount = feeders.size,
                    isPro = isPro,
                    source = source,
                    registerableFeeder = registerable,
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

    private val linked = FeederLinkState.Linked(
        feeder = LinkedFeeder(feederId = FEEDER_ID, status = "active", linkedAt = 0, lastActiveAt = 0),
        tier = "pro",
    )

    @Test
    fun `a subscriber without a receiver gets the neutral empty state`() {
        setTierScreen(isPro = true, source = UpgradeRepo.Source.SUBSCRIPTION, linkState = FeederLinkState.Unlinked("pro"))

        composeRule.onNodeWithText("No receivers yet").assertExists()
        composeRule.onNodeWithText("Add").assertExists()
        composeRule.onNodeWithText("Thanks for feeding").assertDoesNotExist()
        composeRule.onNodeWithText("Start feeding").assertDoesNotExist()
    }

    @Test
    fun `a linked receiver keeps the Pro empty state`() {
        setTierScreen(isPro = true, source = UpgradeRepo.Source.FEEDER, linkState = linked)

        composeRule.onNodeWithText("Thanks for feeding").assertExists()
        composeRule.onNodeWithText("Add it").assertExists()
        composeRule.onNodeWithText("No receivers yet").assertDoesNotExist()
    }

    @Test
    fun `a subscriber who also linked a receiver keeps the Pro empty state`() {
        setTierScreen(isPro = true, source = UpgradeRepo.Source.SUBSCRIPTION, linkState = linked)

        composeRule.onNodeWithText("Thanks for feeding").assertExists()
        composeRule.onNodeWithText("No receivers yet").assertDoesNotExist()
    }

    @Test
    fun `a free installation keeps the free empty state`() {
        setTierScreen(isPro = false, source = null, linkState = FeederLinkState.Unlinked("free"))

        composeRule.onNodeWithText("No feeders yet").assertExists()
        composeRule.onNodeWithText("Start feeding").assertExists()
    }

    @Test
    fun `a subscriber without a receiver is offered to register one found on this network`() {
        val feeder = mockFeeder(id = FEEDER_ID)
        setTierScreen(
            isPro = true,
            source = UpgradeRepo.Source.SUBSCRIPTION,
            linkState = FeederLinkState.Unlinked("pro"),
            feeders = listOf(FeederListViewModel.FeederItem(feeder = feeder, isOffline = false)),
            registerable = feeder,
        )

        composeRule.onNodeWithText("Upgrade available").assertExists()
    }

    @Test
    fun `a linked installation is not offered to register again`() {
        val feeder = mockFeeder(id = FEEDER_ID)
        setTierScreen(
            isPro = true,
            source = UpgradeRepo.Source.FEEDER,
            linkState = linked,
            feeders = listOf(FeederListViewModel.FeederItem(feeder = feeder, isOffline = false)),
            registerable = feeder,
        )

        composeRule.onNodeWithText("Upgrade available").assertDoesNotExist()
    }

    companion object {
        private const val FEEDER_ID = "0a1b2c3d-0000-4000-8000-000000000001"
    }
}
