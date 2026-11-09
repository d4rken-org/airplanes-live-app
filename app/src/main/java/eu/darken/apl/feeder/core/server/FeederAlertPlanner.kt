package eu.darken.apl.feeder.core.server

import eu.darken.apl.feeder.core.config.FeederConfig
import eu.darken.apl.feeder.core.server.FeederServerState.Notified
import eu.darken.apl.server.api.FeederMonitorEntry

/**
 * Decides which feeder notifications the server's monitor states call for, from what was notified
 * before. Pure, the caller posts the actions and stores [Plan.notified].
 */
object FeederAlertPlanner {

    sealed interface Action {
        /** Canonical feeder id. */
        val feederId: String

        data class PostOffline(override val feederId: String, val label: String, val offlineSince: Long?) : Action

        /** Replaces the offline notification. */
        data class PostRecovered(override val feederId: String, val label: String, val offlineForMillis: Long?) : Action

        /** [update] is a re-post after some of the notified problems cleared, nothing new to alert about. */
        data class PostProblems(
            override val feederId: String,
            val label: String,
            val problems: List<String>,
            val update: Boolean = false,
        ) : Action

        data class CancelOffline(override val feederId: String) : Action
        data class CancelProblems(override val feederId: String) : Action
    }

    data class Plan(
        val actions: List<Action>,
        val notified: Map<String, Notified>,
        val notifiedInitialized: Boolean,
        /** Canonical ids whose local feeders lose their snooze: online again since it, or without problems. */
        val snoozeClears: Set<String> = emptySet(),
    )

    fun plan(
        state: FeederServerState,
        configs: Collection<FeederConfig>,
        problemAlertsEnabled: Boolean,
        nowMillis: Long,
    ): Plan {
        // Until the list was read once the monitors are unknown, an initial pass now would record nothing
        if (state.cursor == null) return Plan(emptyList(), state.notified, state.notifiedInitialized)

        val desired = configs
            .mapNotNull { config -> config.receiverId.toFeederMonitorId()?.let { it to config } }
            .sortedWith(compareBy({ it.second.addedAt }, { it.first }))
            .distinctBy { it.first }
            .toMap()

        val actions = mutableListOf<Action>()
        val notified = mutableMapOf<String, Notified>()
        val snoozeClears = mutableSetOf<String>()

        state.notified.keys
            .filter { it !in desired || it !in state.monitors }
            .forEach { feederId ->
                actions += Action.CancelOffline(feederId)
                actions += Action.CancelProblems(feederId)
            }

        desired.forEach { (feederId, config) ->
            val monitor = state.monitors[feederId] ?: return@forEach
            val previous = state.notified[feederId]
            val known = previous?.takeIf { it.monitorId == monitor.monitorId }
            if (previous != null && known == null) {
                actions += Action.CancelOffline(feederId)
                actions += Action.CancelProblems(feederId)
            }

            val active = monitor.status == STATUS_ACTIVE
            val offline = active && monitor.health == HEALTH_OFFLINE
            val online = monitor.health == HEALTH_ONLINE
            val snoozedAt = config.offlineCheckSnoozedAt?.toEpochMilli()
            val snoozed = snoozedAt != null
            // A snooze tapped on the problems of an online feeder has to outlast them
            val cameBack = snoozedAt != null && monitor.healthSince?.let { it > snoozedAt } == true
            if (online && snoozed && (cameBack || monitor.problems.isEmpty())) snoozeClears += feederId

            if (!state.notifiedInitialized) {
                notified[feederId] = Notified(
                    monitorId = monitor.monitorId,
                    offline = offline,
                    offlineSince = if (offline) monitor.healthSince else null,
                    problems = if (active) monitor.problems.toSet() else emptySet(),
                )
                return@forEach
            }

            val label = config.label ?: config.receiverId.takeLast(5)
            var entry = known ?: Notified(monitorId = monitor.monitorId)

            if (offline) {
                if (!entry.offline && config.offlineCheckTimeout != null && !snoozed) {
                    actions += Action.PostOffline(feederId, label, monitor.healthSince)
                    entry = entry.copy(offline = true, offlineSince = monitor.healthSince)
                }
            } else if (online && entry.offline) {
                actions += if (snoozed) {
                    Action.CancelOffline(feederId)
                } else {
                    val since = entry.offlineSince
                    val offlineFor = since?.let { (monitor.healthSince ?: nowMillis) - it }?.takeIf { it >= 0 }
                    Action.PostRecovered(feederId, label, offlineFor)
                }
                entry = entry.copy(offline = false, offlineSince = null)
            }

            if (active) entry = planProblems(feederId, label, monitor, entry, problemAlertsEnabled && !snoozed, actions)

            notified[feederId] = entry
        }

        return Plan(
            actions = actions,
            notified = notified,
            notifiedInitialized = true,
            snoozeClears = snoozeClears,
        )
    }

    private fun planProblems(
        feederId: String,
        label: String,
        monitor: FeederMonitorEntry,
        entry: Notified,
        mayAlert: Boolean,
        actions: MutableList<Action>,
    ): Notified {
        val current = monitor.problems.distinct()
        val added = current.filter { it !in entry.problems }
        val remaining = entry.problems.intersect(current.toSet())
        val removed = remaining.size != entry.problems.size
        return when {
            added.isNotEmpty() && mayAlert -> {
                actions += Action.PostProblems(feederId, label, current)
                entry.copy(problems = current.toSet())
            }

            removed -> {
                // Off or snoozed, the notification goes instead of coming back with what is left
                actions += if (remaining.isNotEmpty() && mayAlert) {
                    Action.PostProblems(feederId, label, current.filter { it in remaining }, update = true)
                } else {
                    Action.CancelProblems(feederId)
                }
                entry.copy(problems = remaining)
            }

            else -> entry
        }
    }

    private const val STATUS_ACTIVE = "active"
    private const val HEALTH_ONLINE = "online"
    private const val HEALTH_OFFLINE = "offline"
}
