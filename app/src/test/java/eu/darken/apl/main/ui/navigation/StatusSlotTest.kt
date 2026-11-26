package eu.darken.apl.main.ui.navigation

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.R
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.server.status.PlannedMaintenance
import eu.darken.apl.server.status.formatServerStatusUntil
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelper.BaseComposeTest
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class StatusSlotTest : BaseComposeTest() {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val locale get() = context.resources.configuration.locales[0]
    private val zone = ZoneId.systemDefault()

    private val now = Instant.parse("2026-11-26T10:00:00Z")
    private val notice = PlannedMaintenance(
        id = "notice-1",
        startsAt = Instant.parse("2026-12-01T02:00:00Z"),
        endsAt = Instant.parse("2026-12-01T04:00:00Z"),
    )

    private var state by mutableStateOf<StatusSlotState>(StatusSlotState.Hidden)
    private var isInternetAvailable by mutableStateOf(true)
    private val dismissed = mutableListOf<String>()

    private val dismiss get() = context.getString(R.string.common_dismiss_action)

    private fun setSlot() {
        composeRule.setContent {
            PreviewWrapper {
                StatusSlot(
                    state = state,
                    isInternetAvailable = isInternetAvailable,
                    now = now,
                    onDismiss = { dismissed.add(it) },
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun show(slotState: StatusSlotState, online: Boolean = true) {
        state = slotState
        isInternetAvailable = online
        composeRule.waitForIdle()
    }

    private fun plannedText(): String {
        val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
        return context.getString(
            R.string.server_status_planned_msg_x,
            date.format(notice.startsAt.atZone(zone)),
            time.format(notice.startsAt.atZone(zone)),
            time.format(notice.endsAt.atZone(zone)),
        )
    }

    private val outages = listOf(
        StatusSlotState.Maintenance(null) to R.string.server_status_maintenance_msg,
        StatusSlotState.Incident(null) to R.string.server_status_incident_msg,
        StatusSlotState.ServerProblem to R.string.server_status_problem_msg,
        StatusSlotState.Unreachable to R.string.server_status_unreachable_msg,
    )

    @Test
    fun `each state shows its message`() {
        setSlot()

        outages.forEach { (slotState, message) ->
            show(slotState)
            composeRule.onNodeWithText(context.getString(message)).assertExists()
        }

        show(StatusSlotState.Planned(notice))
        composeRule.onNodeWithText(plannedText()).assertExists()

        show(StatusSlotState.Hidden, online = false)
        composeRule.onNodeWithText(context.getString(R.string.common_internet_unavailable_message)).assertExists()
    }

    @Test
    fun `offline replaces any other message`() {
        setSlot()

        show(StatusSlotState.Planned(notice), online = false)

        composeRule.onNodeWithText(context.getString(R.string.common_internet_unavailable_message)).assertExists()
        composeRule.onAllNodesWithContentDescription(dismiss).assertCountEquals(0)
    }

    @Test
    fun `a future end shows its time`() {
        val until = now.plus(Duration.ofHours(2))
        val formatted = formatServerStatusUntil(until, now, zone, locale).shouldNotBeNull()
        setSlot()

        show(StatusSlotState.Maintenance(until))
        composeRule.onNodeWithText(context.getString(R.string.server_status_maintenance_until_msg_x, formatted))
            .assertExists()

        show(StatusSlotState.Incident(until))
        composeRule.onNodeWithText(context.getString(R.string.server_status_incident_until_msg_x, formatted))
            .assertExists()
    }

    @Test
    fun `a passed end falls back to the plain message`() {
        val until = now.minus(Duration.ofMinutes(1))
        setSlot()

        show(StatusSlotState.Maintenance(until))
        composeRule.onNodeWithText(context.getString(R.string.server_status_maintenance_msg)).assertExists()

        show(StatusSlotState.Incident(until))
        composeRule.onNodeWithText(context.getString(R.string.server_status_incident_msg)).assertExists()
    }

    @Test
    fun `the planned notice can be dismissed`() {
        setSlot()
        show(StatusSlotState.Planned(notice))

        composeRule.onNodeWithContentDescription(dismiss).performClick()

        dismissed shouldBe listOf(notice.id)
    }

    @Test
    fun `outage messages can't be dismissed`() {
        setSlot()

        outages.forEach { (slotState, _) ->
            show(slotState)
            composeRule.onAllNodesWithContentDescription(dismiss).assertCountEquals(0)
        }

        show(StatusSlotState.Hidden, online = false)
        composeRule.onAllNodesWithContentDescription(dismiss).assertCountEquals(0)
    }
}
