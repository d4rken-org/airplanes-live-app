package eu.darken.apl.feeder.core.monitor

import eu.darken.apl.feeder.core.Feeder
import eu.darken.apl.feeder.core.FeederStatus
import eu.darken.apl.feeder.core.monitor.FeederTransitionEngine.Command
import eu.darken.apl.feeder.core.stats.HealthMetric
import eu.darken.apl.feeder.core.stats.HealthObservation
import eu.darken.apl.feeder.core.stats.HealthSeverity
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class FeederTransitionEngineTest : BaseTest() {

    private fun feeder(id: String, status: FeederStatus) = Feeder(id = id, status = status)

    private fun evaluate(
        state: FeederMonitorState = FeederMonitorState(),
        feeders: List<Feeder>,
        muted: Set<String> = emptySet(),
        health: Map<String, HealthObservation> = emptyMap(),
    ) = FeederTransitionEngine.evaluate(state, feeders, muted, health)

    // --- connectivity ---

    @Test
    fun `first observation of an offline feeder notifies`() {
        val result = evaluate(feeders = listOf(feeder("a", FeederStatus.INACTIVE)))

        result.commands shouldContain Command.ShowOffline(feeder("a", FeederStatus.INACTIVE))
        result.nextState.feeders.getValue("a").outageActive shouldBe true
        result.nextState.feeders.getValue("a").offlineNotificationPosted shouldBe true
    }

    @Test
    fun `steady offline state does not re-notify`() {
        val first = evaluate(feeders = listOf(feeder("a", FeederStatus.INACTIVE)))
        val second = evaluate(state = first.nextState, feeders = listOf(feeder("a", FeederStatus.INACTIVE)))

        second.commands.filterIsInstance<Command.ShowOffline>().shouldBeEmpty()
    }

    @Test
    fun `recovery cancels the offline notification and announces once`() {
        val outage = evaluate(feeders = listOf(feeder("a", FeederStatus.INACTIVE)))
        val recovery = evaluate(state = outage.nextState, feeders = listOf(feeder("a", FeederStatus.ACTIVE)))

        recovery.commands shouldContain Command.CancelOffline("a")
        recovery.commands shouldContain Command.ShowRecovered(feeder("a", FeederStatus.ACTIVE))

        val steady = evaluate(state = recovery.nextState, feeders = listOf(feeder("a", FeederStatus.ACTIVE)))
        steady.commands.shouldBeEmpty()
    }

    @Test
    fun `idle counts as online for recovery`() {
        val outage = evaluate(feeders = listOf(feeder("a", FeederStatus.INACTIVE)))
        val recovery = evaluate(state = outage.nextState, feeders = listOf(feeder("a", FeederStatus.IDLE)))

        recovery.commands shouldContain Command.ShowRecovered(feeder("a", FeederStatus.IDLE))
    }

    @Test
    fun `indeterminate statuses preserve an active outage`() {
        val outage = evaluate(feeders = listOf(feeder("a", FeederStatus.INACTIVE)))

        listOf(FeederStatus.UNKNOWN, FeederStatus.DATA_BLOCKED).forEach { status ->
            val result = evaluate(state = outage.nextState, feeders = listOf(feeder("a", status)))
            result.commands.filterIsInstance<Command.ShowRecovered>().shouldBeEmpty()
            result.commands.filterIsInstance<Command.CancelOffline>().shouldBeEmpty()
            result.nextState.feeders.getValue("a").outageActive shouldBe true
        }
    }

    @Test
    fun `new outage cancels a stale recovery notification`() {
        val outage = evaluate(feeders = listOf(feeder("a", FeederStatus.INACTIVE)))
        val recovered = evaluate(state = outage.nextState, feeders = listOf(feeder("a", FeederStatus.ACTIVE)))
        val secondOutage = evaluate(state = recovered.nextState, feeders = listOf(feeder("a", FeederStatus.INACTIVE)))

        secondOutage.commands shouldContain Command.CancelRecovery("a")
        secondOutage.commands shouldContain Command.ShowOffline(feeder("a", FeederStatus.INACTIVE))
    }

    // --- mute ---

    @Test
    fun `muted outage is tracked but silent`() {
        val result = evaluate(feeders = listOf(feeder("a", FeederStatus.INACTIVE)), muted = setOf("a"))

        result.commands.filterIsInstance<Command.ShowOffline>().shouldBeEmpty()
        result.nextState.feeders.getValue("a").outageActive shouldBe true
        result.nextState.feeders.getValue("a").offlineNotificationPosted shouldBe false
    }

    @Test
    fun `unmute before recovery does not fabricate a recovery notification`() {
        val mutedOutage = evaluate(feeders = listOf(feeder("a", FeederStatus.INACTIVE)), muted = setOf("a"))
        // Unmuted now, feeder recovers — the outage was never announced, so neither is the recovery.
        val recovery = evaluate(state = mutedOutage.nextState, feeders = listOf(feeder("a", FeederStatus.ACTIVE)))

        recovery.commands.filterIsInstance<Command.ShowRecovered>().shouldBeEmpty()
        recovery.commands shouldContain Command.CancelOffline("a")
    }

    @Test
    fun `mute after the offline notification suppresses the recovery announcement`() {
        val outage = evaluate(feeders = listOf(feeder("a", FeederStatus.INACTIVE)))
        val recovery = evaluate(
            state = outage.nextState,
            feeders = listOf(feeder("a", FeederStatus.ACTIVE)),
            muted = setOf("a"),
        )

        recovery.commands shouldContain Command.CancelOffline("a")
        recovery.commands.filterIsInstance<Command.ShowRecovered>().shouldBeEmpty()
    }

    // --- health ---

    private val cpuWarn = mapOf(HealthMetric.CPU_TEMPERATURE to HealthSeverity.WARN)
    private val cpuCrit = mapOf(HealthMetric.CPU_TEMPERATURE to HealthSeverity.CRIT)

    @Test
    fun `new health warning notifies and persists`() {
        val result = evaluate(
            feeders = listOf(feeder("a", FeederStatus.ACTIVE)),
            health = mapOf("a" to HealthObservation.Evaluated(cpuWarn)),
        )

        result.commands shouldContain Command.ShowHealth(feeder("a", FeederStatus.ACTIVE), cpuWarn)
        result.nextState.feeders.getValue("a").healthWarnings shouldBe cpuWarn
    }

    @Test
    fun `unchanged warnings do not re-notify but escalation does`() {
        val warn = evaluate(
            feeders = listOf(feeder("a", FeederStatus.ACTIVE)),
            health = mapOf("a" to HealthObservation.Evaluated(cpuWarn)),
        )
        val same = evaluate(
            state = warn.nextState,
            feeders = listOf(feeder("a", FeederStatus.ACTIVE)),
            health = mapOf("a" to HealthObservation.Evaluated(cpuWarn)),
        )
        same.commands.shouldBeEmpty()

        val escalated = evaluate(
            state = warn.nextState,
            feeders = listOf(feeder("a", FeederStatus.ACTIVE)),
            health = mapOf("a" to HealthObservation.Evaluated(cpuCrit)),
        )
        escalated.commands shouldContain Command.ShowHealth(feeder("a", FeederStatus.ACTIVE), cpuCrit)
    }

    @Test
    fun `healthy evaluation cancels a previous warning`() {
        val warn = evaluate(
            feeders = listOf(feeder("a", FeederStatus.ACTIVE)),
            health = mapOf("a" to HealthObservation.Evaluated(cpuWarn)),
        )
        val healthy = evaluate(
            state = warn.nextState,
            feeders = listOf(feeder("a", FeederStatus.ACTIVE)),
            health = mapOf("a" to HealthObservation.Evaluated(emptyMap())),
        )

        healthy.commands shouldContainExactly listOf(Command.CancelHealth("a"))
        healthy.nextState.feeders.getValue("a").healthWarnings shouldBe emptyMap()
    }

    @Test
    fun `inconclusive observation carries the previous warning state forward`() {
        val warn = evaluate(
            feeders = listOf(feeder("a", FeederStatus.ACTIVE)),
            health = mapOf("a" to HealthObservation.Evaluated(cpuWarn)),
        )
        val failed = evaluate(
            state = warn.nextState,
            feeders = listOf(feeder("a", FeederStatus.ACTIVE)),
            health = mapOf("a" to HealthObservation.Inconclusive),
        )

        failed.commands.shouldBeEmpty()
        failed.nextState.feeders.getValue("a").healthWarnings shouldBe cpuWarn
    }

    @Test
    fun `absent observation behaves like inconclusive`() {
        val warn = evaluate(
            feeders = listOf(feeder("a", FeederStatus.ACTIVE)),
            health = mapOf("a" to HealthObservation.Evaluated(cpuWarn)),
        )
        val absent = evaluate(
            state = warn.nextState,
            feeders = listOf(feeder("a", FeederStatus.ACTIVE)),
            health = emptyMap(),
        )

        absent.commands.shouldBeEmpty()
        absent.nextState.feeders.getValue("a").healthWarnings shouldBe cpuWarn
    }

    @Test
    fun `diagnostics turned off clears warnings and cancels the notification`() {
        val warn = evaluate(
            feeders = listOf(feeder("a", FeederStatus.ACTIVE)),
            health = mapOf("a" to HealthObservation.Evaluated(cpuWarn)),
        )
        val disabled = evaluate(
            state = warn.nextState,
            feeders = listOf(feeder("a", FeederStatus.ACTIVE)),
            health = mapOf("a" to HealthObservation.DiagnosticsUnavailable),
        )

        disabled.commands shouldContainExactly listOf(Command.CancelHealth("a"))
        disabled.nextState.feeders.getValue("a").healthWarnings shouldBe emptyMap()
    }

    @Test
    fun `muted health change is tracked but silent`() {
        val result = evaluate(
            feeders = listOf(feeder("a", FeederStatus.ACTIVE)),
            muted = setOf("a"),
            health = mapOf("a" to HealthObservation.Evaluated(cpuWarn)),
        )

        result.commands.filterIsInstance<Command.ShowHealth>().shouldBeEmpty()
        result.nextState.feeders.getValue("a").healthWarnings shouldBe cpuWarn
    }

    // --- pruning ---

    @Test
    fun `unowned feeders are pruned and their notifications cancelled`() {
        val outage = evaluate(feeders = listOf(feeder("a", FeederStatus.INACTIVE), feeder("b", FeederStatus.ACTIVE)))
        val pruned = evaluate(state = outage.nextState, feeders = listOf(feeder("b", FeederStatus.ACTIVE)))

        pruned.commands shouldContain Command.CancelAll("a")
        pruned.commands shouldNotContain Command.CancelAll("b")
        pruned.nextState.feeders shouldNotContainKey "a"
    }
}
