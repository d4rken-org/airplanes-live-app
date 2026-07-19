package eu.darken.apl.feeder.core.monitor

import eu.darken.apl.feeder.core.Feeder
import eu.darken.apl.feeder.core.FeederStatus
import eu.darken.apl.feeder.core.stats.HealthMetric
import eu.darken.apl.feeder.core.stats.HealthObservation
import eu.darken.apl.feeder.core.stats.HealthSeverity

/**
 * Pure notification-transition logic: previous [FeederMonitorState] + current observations in,
 * next state + notification commands out. No IO, no clocks — fully unit-testable.
 */
object FeederTransitionEngine {

    sealed interface Command {
        data class ShowOffline(val feeder: Feeder) : Command
        data class ShowRecovered(val feeder: Feeder) : Command
        data class ShowHealth(val feeder: Feeder, val warnings: Map<HealthMetric, HealthSeverity>) : Command
        data class CancelOffline(val feederId: String) : Command
        data class CancelRecovery(val feederId: String) : Command
        data class CancelHealth(val feederId: String) : Command
        data class CancelAll(val feederId: String) : Command
    }

    data class Result(
        val nextState: FeederMonitorState,
        val commands: List<Command>,
    )

    /**
     * Statuses collapse into three lanes: only INACTIVE is an outage, only ACTIVE/IDLE prove the
     * feeder is back. UNKNOWN and DATA_BLOCKED prove neither — they preserve the previous outage
     * state so a moderation action or a glitch can't fabricate a "back online" notification.
     */
    private enum class Lane { OFFLINE, ONLINE, INDETERMINATE }

    private fun FeederStatus.lane(): Lane = when (this) {
        FeederStatus.INACTIVE -> Lane.OFFLINE
        FeederStatus.ACTIVE, FeederStatus.IDLE -> Lane.ONLINE
        FeederStatus.UNKNOWN, FeederStatus.DATA_BLOCKED -> Lane.INDETERMINATE
    }

    fun evaluate(
        state: FeederMonitorState,
        feeders: List<Feeder>,
        muted: Set<String>,
        health: Map<String, HealthObservation>,
    ): Result {
        val commands = mutableListOf<Command>()
        val nextFeeders = mutableMapOf<String, FeederTransitionState>()

        feeders.forEach { feeder ->
            val previous = state.feeders[feeder.id] ?: FeederTransitionState()
            val isMuted = feeder.id in muted

            var outageActive = previous.outageActive
            var posted = previous.offlineNotificationPosted
            when (feeder.status.lane()) {
                Lane.OFFLINE -> if (!outageActive) {
                    outageActive = true
                    // A lingering "back online" from the previous outage is now misinformation.
                    commands += Command.CancelRecovery(feeder.id)
                    if (!isMuted) {
                        commands += Command.ShowOffline(feeder)
                        posted = true
                    }
                }

                Lane.ONLINE -> if (outageActive) {
                    outageActive = false
                    commands += Command.CancelOffline(feeder.id)
                    // Only announce a recovery whose outage was actually announced — an outage
                    // that happened entirely while muted stays silent even if unmuted since.
                    if (posted && !isMuted) commands += Command.ShowRecovered(feeder)
                    posted = false
                }

                Lane.INDETERMINATE -> Unit
            }

            var healthWarnings = previous.healthWarnings
            when (val observation = health[feeder.id] ?: HealthObservation.FetchFailed) {
                HealthObservation.FetchFailed -> Unit // no evidence either way, carry forward

                HealthObservation.DiagnosticsUnavailable -> {
                    if (healthWarnings.isNotEmpty()) commands += Command.CancelHealth(feeder.id)
                    healthWarnings = emptyMap()
                }

                is HealthObservation.Evaluated -> {
                    val warnings = observation.warnings
                    when {
                        warnings.isEmpty() && healthWarnings.isNotEmpty() ->
                            commands += Command.CancelHealth(feeder.id)

                        warnings.isNotEmpty() && warnings != healthWarnings && !isMuted ->
                            commands += Command.ShowHealth(feeder, warnings)
                    }
                    healthWarnings = warnings
                }
            }

            nextFeeders[feeder.id] = FeederTransitionState(
                lastStatus = feeder.status,
                outageActive = outageActive,
                offlineNotificationPosted = posted,
                healthWarnings = healthWarnings,
            )
        }

        // Feeders no longer owned: drop their state and take down whatever is still showing.
        (state.feeders.keys - feeders.map { it.id }.toSet()).forEach { removedId ->
            commands += Command.CancelAll(removedId)
        }

        return Result(nextState = FeederMonitorState(feeders = nextFeeders), commands = commands)
    }
}
