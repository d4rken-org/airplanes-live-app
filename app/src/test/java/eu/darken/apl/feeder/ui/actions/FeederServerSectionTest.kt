package eu.darken.apl.feeder.ui.actions

import androidx.compose.ui.test.onNodeWithText
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.feeder.core.server.FeederServerView
import eu.darken.apl.feeder.ui.preview.mockFeeder
import eu.darken.apl.server.api.FeederMonitorEntry
import org.junit.Test
import testhelper.BaseComposeTest

class FeederServerSectionTest : BaseComposeTest() {

    private fun entry(
        status: String = "active",
        health: String = "online",
        problems: List<String> = emptyList(),
        insufficientEvidence: List<String> = emptyList(),
    ) = FeederMonitorEntry(
        feederId = FEEDER_ID,
        monitorId = "m",
        offlineAfterMinutes = 60,
        status = status,
        createdAt = 0,
        updatedAt = 0,
        health = health,
        problems = problems,
        insufficientEvidence = insufficientEvidence,
        eventSeq = 0,
    )

    private fun setSection(server: FeederServerView, sampleIntervalSeconds: Int? = null) = composeRule.setContent {
        PreviewWrapper {
            FeederServerSection(
                feeder = mockFeeder(id = FEEDER_ID).copy(server = server),
                sampleIntervalSeconds = sampleIntervalSeconds,
            )
        }
    }

    @Test
    fun `current problems are listed by name`() {
        setSection(FeederServerView(FEEDER_ID, entry(problems = listOf("mlat_missing", "flapping"))))

        composeRule.onNodeWithText("MLAT client missing").assertExists()
        composeRule.onNodeWithText("Reconnecting repeatedly").assertExists()
    }

    @Test
    fun `checks without enough evidence are named`() {
        setSection(
            FeederServerView(FEEDER_ID, entry(insufficientEvidence = listOf("deaf", "mlat_bad_sync", "mlat_outliers")))
        )

        composeRule
            .onNodeWithText("Not enough data yet for: message rate check, MLAT sync check, MLAT outlier check")
            .assertExists()
    }

    @Test
    fun `an unknown check gets a generic name`() {
        setSection(FeederServerView(FEEDER_ID, entry(insufficientEvidence = listOf("future_check"))))

        composeRule.onNodeWithText("Not enough data yet for: another check").assertExists()
    }

    @Test
    fun `the status uses the server's status text`() {
        setSection(FeederServerView(FEEDER_ID, entry(status = "paused", health = "unknown")))

        composeRule.onNodeWithText("Monitoring paused").assertExists()
    }

    @Test
    fun `the sampling interval is shown in minutes`() {
        setSection(FeederServerView(FEEDER_ID, entry()), sampleIntervalSeconds = 300)

        composeRule.onNodeWithText("Checked every 5 minutes").assertExists()
    }

    companion object {
        private const val FEEDER_ID = "0a1b2c3d-0000-4000-8000-000000000001"
    }
}
