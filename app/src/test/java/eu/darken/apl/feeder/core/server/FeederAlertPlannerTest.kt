package eu.darken.apl.feeder.core.server

import eu.darken.apl.feeder.core.config.FeederConfig
import eu.darken.apl.feeder.core.server.FeederAlertPlanner.Action.CancelOffline
import eu.darken.apl.feeder.core.server.FeederAlertPlanner.Action.CancelProblems
import eu.darken.apl.feeder.core.server.FeederAlertPlanner.Action.PostOffline
import eu.darken.apl.feeder.core.server.FeederAlertPlanner.Action.PostProblems
import eu.darken.apl.feeder.core.server.FeederAlertPlanner.Action.PostRecovered
import eu.darken.apl.feeder.core.server.FeederAlertPlanner.Plan
import eu.darken.apl.feeder.core.server.FeederProblemCategory.MESSAGE_RATE
import eu.darken.apl.feeder.core.server.FeederServerState.Notified
import eu.darken.apl.server.api.FeederMonitorEntry
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Duration
import java.time.Instant

class FeederAlertPlannerTest : BaseTest() {

    private fun monitor(
        health: String = "online",
        healthSince: Long? = ONLINE_SINCE,
        problems: List<String> = emptyList(),
        status: String = "active",
        unknownReason: String? = null,
        monitorId: String = MONITOR_1,
        feederId: String = ID_1,
    ) = FeederMonitorEntry(
        feederId = feederId,
        monitorId = monitorId,
        offlineAfterMinutes = 2880,
        status = status,
        createdAt = 0,
        updatedAt = 0,
        health = health,
        unknownReason = unknownReason,
        healthSince = healthSince,
        problems = problems,
        eventSeq = 1,
    )

    private fun offline(problems: List<String> = emptyList()) =
        monitor(health = "offline", healthSince = OFFLINE_SINCE, problems = problems)

    private fun paused(problems: List<String> = emptyList()) =
        monitor(health = "unknown", unknownReason = "paused", status = "paused", problems = problems)

    private fun config(
        receiverId: String = ID_1,
        label: String? = "Roof",
        order: Long = 0,
        timeout: Duration? = Duration.ofHours(48),
        snoozedAt: Instant? = null,
        // The problem categories to notify about, null keeps the defaults
        notify: Set<FeederProblemCategory>? = null,
    ) = FeederConfig(
        receiverId = receiverId,
        addedAt = Instant.ofEpochSecond(1_700_000_000 + order),
        label = label,
        offlineCheckTimeout = timeout,
        offlineCheckSnoozedAt = snoozedAt,
    ).let { config ->
        if (notify == null) return@let config
        FeederProblemCategory.entries.fold(config) { c, category -> c.withProblemCategory(category, category in notify) }
    }

    private fun state(
        monitors: Map<String, FeederMonitorEntry>,
        notified: Map<String, Notified> = emptyMap(),
        initialized: Boolean = true,
    ) = FeederServerState(
        installationId = "installation-a",
        cursor = 10,
        monitors = monitors,
        notified = notified,
        notifiedInitialized = initialized,
    )

    private fun plan(
        state: FeederServerState,
        vararg configs: FeederConfig = arrayOf(config()),
        problemAlerts: Boolean = true,
    ) = FeederAlertPlanner.plan(state, configs.toList(), problemAlerts, NOW)

    /** The state after the plan was carried out, with the server's monitors replaced by [monitors]. */
    private fun Plan.then(previous: FeederServerState, monitors: Map<String, FeederMonitorEntry>) =
        previous.copy(monitors = monitors, notified = notified, notifiedInitialized = notifiedInitialized)

    @Test
    fun `nothing is planned before the server's monitors were read once`() {
        val unread = state(mapOf(ID_1 to offline()), initialized = false).copy(cursor = null)

        val plan = plan(unread)

        plan shouldBe Plan(emptyList(), emptyMap(), notifiedInitialized = false)
    }

    @Test
    fun `the first pass of an installation records the current state without posting`() {
        val first = state(
            mapOf(ID_1 to offline(problems = listOf("deaf")), ID_2 to monitor(feederId = ID_2, monitorId = MONITOR_2)),
            initialized = false,
        )

        val configs = arrayOf(config(), config(ID_2, order = 1))
        val plan = plan(first, *configs)

        plan.actions.shouldBeEmpty()
        plan.notifiedInitialized shouldBe true
        plan.notified shouldBe mapOf(
            ID_1 to Notified(MONITOR_1, offline = true, offlineSince = OFFLINE_SINCE, problems = setOf("deaf")),
            ID_2 to Notified(MONITOR_2),
        )

        val again = plan(plan.then(first, first.monitors), *configs)
        again.actions.shouldBeEmpty()

        val recovered = plan(plan.then(first, first.monitors + (ID_1 to monitor())), *configs)
        recovered.actions shouldContainExactly listOf(
            PostRecovered(ID_1, "Roof", ONLINE_SINCE - OFFLINE_SINCE),
            CancelProblems(ID_1),
        )
    }

    @Test
    fun `going offline posts once`() {
        val online = state(mapOf(ID_1 to monitor()), mapOf(ID_1 to Notified(MONITOR_1)))

        val first = plan(online.copy(monitors = mapOf(ID_1 to offline())))
        first.actions shouldContainExactly listOf(PostOffline(ID_1, "Roof", OFFLINE_SINCE))
        first.notified.getValue(ID_1) shouldBe Notified(MONITOR_1, offline = true, offlineSince = OFFLINE_SINCE)

        val second = plan(first.then(online, mapOf(ID_1 to offline())))
        second.actions.shouldBeEmpty()
        second.notified shouldBe first.notified
    }

    @Test
    fun `offline alerts turned off post nothing, turning them on while still offline posts`() {
        val offlineState = state(mapOf(ID_1 to offline()), mapOf(ID_1 to Notified(MONITOR_1)))

        val off = plan(offlineState, config(timeout = null))
        off.actions.shouldBeEmpty()
        off.notified.getValue(ID_1).offline shouldBe false

        val on = plan(off.then(offlineState, offlineState.monitors), config())
        on.actions shouldContainExactly listOf(PostOffline(ID_1, "Roof", OFFLINE_SINCE))
    }

    @Test
    fun `offline alerts turned off after an offline notification cancel it, coming back online posts nothing`() {
        val notified = mapOf(ID_1 to Notified(MONITOR_1, offline = true, offlineSince = OFFLINE_SINCE))
        val posted = state(mapOf(ID_1 to offline()), notified)

        val off = plan(posted, config(timeout = null))
        off.actions shouldContainExactly listOf(CancelOffline(ID_1))
        off.notified.getValue(ID_1).offline shouldBe false
        off.notified.getValue(ID_1).offlineSince shouldBe null

        val back = plan(off.then(posted, mapOf(ID_1 to monitor())), config(timeout = null))
        back.actions.shouldBeEmpty()
    }

    @Test
    fun `the first pass with offline alerts off records no outage, coming back online posts nothing`() {
        val first = state(mapOf(ID_1 to offline()), initialized = false)

        val plan = plan(first, config(timeout = null))
        plan.actions.shouldBeEmpty()
        plan.notified.getValue(ID_1).offline shouldBe false
        plan.notified.getValue(ID_1).offlineSince shouldBe null

        val back = plan(plan.then(first, mapOf(ID_1 to monitor())), config(timeout = null))
        back.actions.shouldBeEmpty()
    }

    @Test
    fun `a snoozed feeder going offline posts nothing, unsnoozing while still offline posts`() {
        val offlineState = state(mapOf(ID_1 to offline()), mapOf(ID_1 to Notified(MONITOR_1)))

        val snoozed = plan(offlineState, config(snoozedAt = SNOOZED_AT))
        snoozed.actions.shouldBeEmpty()
        snoozed.notified.getValue(ID_1).offline shouldBe false
        snoozed.snoozeClears.shouldBeEmpty()

        val unsnoozed = plan(snoozed.then(offlineState, offlineState.monitors), config())
        unsnoozed.actions shouldContainExactly listOf(PostOffline(ID_1, "Roof", OFFLINE_SINCE))
    }

    @Test
    fun `back online after a posted offline notification posts the recovery and resets`() {
        val notified = mapOf(ID_1 to Notified(MONITOR_1, offline = true, offlineSince = OFFLINE_SINCE))

        val plan = plan(state(mapOf(ID_1 to monitor()), notified))

        plan.actions shouldContainExactly listOf(PostRecovered(ID_1, "Roof", ONLINE_SINCE - OFFLINE_SINCE))
        plan.notified.getValue(ID_1) shouldBe Notified(MONITOR_1)
    }

    @Test
    fun `a recovery without known times counts until now, or posts without a duration`() {
        val notified = mapOf(ID_1 to Notified(MONITOR_1, offline = true, offlineSince = OFFLINE_SINCE))
        plan(state(mapOf(ID_1 to monitor(healthSince = null)), notified)).actions shouldContainExactly
                listOf(PostRecovered(ID_1, "Roof", NOW - OFFLINE_SINCE))

        val noSince = mapOf(ID_1 to Notified(MONITOR_1, offline = true))
        plan(state(mapOf(ID_1 to monitor()), noSince)).actions shouldContainExactly
                listOf(PostRecovered(ID_1, "Roof", null))
    }

    @Test
    fun `back online while snoozed cancels the offline notification, posts nothing and clears the snooze`() {
        val notified = mapOf(ID_1 to Notified(MONITOR_1, offline = true, offlineSince = OFFLINE_SINCE))

        val plan = plan(state(mapOf(ID_1 to monitor()), notified), config(snoozedAt = SNOOZED_AT))

        plan.actions shouldContainExactly listOf(CancelOffline(ID_1))
        plan.notified.getValue(ID_1) shouldBe Notified(MONITOR_1)
        plan.snoozeClears shouldBe setOf(ID_1)
    }

    @Test
    fun `a snoozed feeder that is online without problems loses its snooze`() {
        val plan = plan(state(mapOf(ID_1 to monitor()), mapOf(ID_1 to Notified(MONITOR_1))), config(snoozedAt = SNOOZED_AT))

        plan.actions.shouldBeEmpty()
        plan.snoozeClears shouldBe setOf(ID_1)
    }

    @Test
    fun `an online feeder snoozed over its problems stays snoozed and gets no problem notification`() {
        val troubled = state(
            mapOf(ID_1 to monitor(problems = listOf("deaf"))),
            mapOf(ID_1 to Notified(MONITOR_1, problems = setOf("deaf"))),
        )
        val snoozed = config(snoozedAt = SNOOZED_WHILE_ONLINE)

        val first = plan(troubled, snoozed)
        first.actions.shouldBeEmpty()
        first.snoozeClears.shouldBeEmpty()

        val second = plan(first.then(troubled, mapOf(ID_1 to monitor(problems = listOf("deaf", "flapping")))), snoozed)
        second.actions.shouldBeEmpty()
        second.snoozeClears.shouldBeEmpty()
    }

    @Test
    fun `an online feeder snoozed over its problems loses the snooze once they are all cleared`() {
        val troubled = state(
            mapOf(ID_1 to monitor()),
            mapOf(ID_1 to Notified(MONITOR_1, problems = setOf("deaf"))),
        )

        val plan = plan(troubled, config(snoozedAt = SNOOZED_WHILE_ONLINE))

        plan.actions shouldContainExactly listOf(CancelProblems(ID_1))
        plan.snoozeClears shouldBe setOf(ID_1)
    }

    @Test
    fun `an online feeder snoozed over its problems loses the snooze when it goes offline and comes back`() {
        val troubled = state(
            mapOf(ID_1 to monitor(problems = listOf("deaf"))),
            mapOf(ID_1 to Notified(MONITOR_1, problems = setOf("deaf"))),
        )
        val snoozed = config(snoozedAt = SNOOZED_WHILE_ONLINE)
        val wentOffline = SNOOZED_WHILE_ONLINE.toEpochMilli() + 3_600_000L

        val offline = plan(
            troubled.copy(monitors = mapOf(ID_1 to monitor(health = "offline", healthSince = wentOffline, problems = listOf("deaf")))),
            snoozed,
        )
        offline.actions.shouldBeEmpty()
        offline.snoozeClears.shouldBeEmpty()

        val back = monitor(healthSince = wentOffline + 3_600_000L, problems = listOf("deaf"))
        val recovered = plan(offline.then(troubled, mapOf(ID_1 to back)), snoozed)
        recovered.actions.shouldBeEmpty()
        recovered.snoozeClears shouldBe setOf(ID_1)
    }

    @Test
    fun `offline taken from a list read without any event alerts`() {
        // The last stored state was online, a list read replaced it with offline
        val reread = state(mapOf(ID_1 to offline()), mapOf(ID_1 to Notified(MONITOR_1)))

        plan(reread).actions shouldContainExactly listOf(PostOffline(ID_1, "Roof", OFFLINE_SINCE))
    }

    @Test
    fun `a monitor the server replaced cancels the old notifications and alerts as a new one`() {
        val replaced = state(
            mapOf(ID_1 to offline().copy(monitorId = MONITOR_2)),
            mapOf(ID_1 to Notified(MONITOR_1, offline = true, offlineSince = 1L, problems = setOf("deaf"))),
        )

        val plan = plan(replaced)

        plan.actions shouldContainExactly listOf(
            CancelOffline(ID_1),
            CancelProblems(ID_1),
            PostOffline(ID_1, "Roof", OFFLINE_SINCE),
        )
        plan.notified.getValue(ID_1) shouldBe Notified(MONITOR_2, offline = true, offlineSince = OFFLINE_SINCE)
    }

    @Test
    fun `new problems post every current one, more of them post again`() {
        val base = state(mapOf(ID_1 to monitor()), mapOf(ID_1 to Notified(MONITOR_1)))

        val first = plan(base.copy(monitors = mapOf(ID_1 to monitor(problems = listOf("deaf")))))
        first.actions shouldContainExactly listOf(PostProblems(ID_1, "Roof", listOf("deaf")))
        first.notified.getValue(ID_1).problems shouldBe setOf("deaf")

        val more = mapOf(ID_1 to monitor(problems = listOf("deaf", "flapping")))
        val second = plan(first.then(base, more))
        second.actions shouldContainExactly listOf(PostProblems(ID_1, "Roof", listOf("deaf", "flapping")))
        second.notified.getValue(ID_1).problems shouldBe setOf("deaf", "flapping")

        plan(second.then(base, more)).actions.shouldBeEmpty()
    }

    @Test
    fun `cleared problems update the notification, the last one cancels it, a returning one alerts again`() {
        val base = state(
            mapOf(ID_1 to monitor()),
            mapOf(ID_1 to Notified(MONITOR_1, problems = setOf("deaf", "flapping"))),
        )

        val partly = plan(base.copy(monitors = mapOf(ID_1 to monitor(problems = listOf("flapping")))))
        partly.actions shouldContainExactly listOf(PostProblems(ID_1, "Roof", listOf("flapping"), update = true))
        partly.notified.getValue(ID_1).problems shouldBe setOf("flapping")

        val all = plan(partly.then(base, mapOf(ID_1 to monitor())))
        all.actions shouldContainExactly listOf(CancelProblems(ID_1))
        all.notified.getValue(ID_1).problems shouldBe emptySet()

        val back = plan(all.then(base, mapOf(ID_1 to monitor(problems = listOf("deaf")))))
        back.actions shouldContainExactly listOf(PostProblems(ID_1, "Roof", listOf("deaf")))
    }

    @Test
    fun `problems with problem alerts off post nothing and are not counted as notified`() {
        val base = state(mapOf(ID_1 to monitor(problems = listOf("deaf"))), mapOf(ID_1 to Notified(MONITOR_1)))

        val off = plan(base, problemAlerts = false)
        off.actions.shouldBeEmpty()
        off.notified.getValue(ID_1).problems shouldBe emptySet()

        plan(off.then(base, base.monitors)).actions shouldContainExactly listOf(PostProblems(ID_1, "Roof", listOf("deaf")))
    }

    @Test
    fun `problems of a snoozed feeder post nothing`() {
        val offlineWithProblem = state(
            mapOf(ID_1 to offline(problems = listOf("mlat_missing"))),
            mapOf(ID_1 to Notified(MONITOR_1, offline = true, offlineSince = OFFLINE_SINCE)),
        )

        val plan = plan(offlineWithProblem, config(snoozedAt = SNOOZED_AT, notify = ALL))

        plan.actions.shouldBeEmpty()
        plan.notified.getValue(ID_1).problems shouldBe emptySet()
    }

    @Test
    fun `a new problem of a muted category posts nothing and is not counted as notified`() {
        val plan = plan(state(mapOf(ID_1 to monitor(problems = listOf("mlat_no_peers"))), mapOf(ID_1 to Notified(MONITOR_1))))

        plan.actions.shouldBeEmpty()
        plan.notified.getValue(ID_1).problems shouldBe emptySet()
    }

    @Test
    fun `muting the category of a notified problem updates the notification with the rest, muting the last cancels it`() {
        val base = state(
            mapOf(ID_1 to monitor(problems = listOf("deaf", "flapping"))),
            mapOf(ID_1 to Notified(MONITOR_1, problems = setOf("deaf", "flapping"))),
        )

        val partly = plan(base, config(notify = setOf(MESSAGE_RATE)))
        partly.actions shouldContainExactly listOf(PostProblems(ID_1, "Roof", listOf("deaf"), update = true))
        partly.notified.getValue(ID_1).problems shouldBe setOf("deaf")

        val all = plan(partly.then(base, base.monitors), config(notify = emptySet()))
        all.actions shouldContainExactly listOf(CancelProblems(ID_1))
        all.notified.getValue(ID_1).problems shouldBe emptySet()
    }

    @Test
    fun `muting categories of a paused monitor updates or cancels its notification and never alerts`() {
        val base = state(
            mapOf(ID_1 to paused(problems = listOf("deaf", "flapping", "mlat_no_peers"))),
            mapOf(ID_1 to Notified(MONITOR_1, problems = setOf("deaf", "flapping"))),
        )

        val partly = plan(base, config(notify = setOf(MESSAGE_RATE)))
        partly.actions shouldContainExactly listOf(PostProblems(ID_1, "Roof", listOf("deaf"), update = true))
        partly.notified.getValue(ID_1).problems shouldBe setOf("deaf")

        val all = plan(partly.then(base, base.monitors), config(notify = emptySet()))
        all.actions shouldContainExactly listOf(CancelProblems(ID_1))
        all.notified.getValue(ID_1).problems shouldBe emptySet()

        val unmuted = plan(all.then(base, base.monitors), config(notify = ALL))
        unmuted.actions.shouldBeEmpty()
        unmuted.notified.getValue(ID_1).problems shouldBe emptySet()
    }

    @Test
    fun `muting a category of a monitor paused after the first pass plans an update`() {
        val first = state(mapOf(ID_1 to monitor(problems = listOf("deaf", "flapping"))), initialized = false)

        val baseline = plan(first)
        baseline.actions.shouldBeEmpty()
        baseline.notified.getValue(ID_1).problems shouldBe setOf("deaf", "flapping")

        val pausedState = baseline.then(first, mapOf(ID_1 to paused()))
        val stillPaused = plan(pausedState)
        stillPaused.actions.shouldBeEmpty()

        val muted = plan(stillPaused.then(pausedState, pausedState.monitors), config(notify = setOf(MESSAGE_RATE)))
        muted.actions shouldContainExactly listOf(PostProblems(ID_1, "Roof", listOf("deaf"), update = true))
    }

    @Test
    fun `muting a category of a paused snoozed feeder cancels and never posts again`() {
        val base = state(
            mapOf(ID_1 to paused()),
            mapOf(ID_1 to Notified(MONITOR_1, problems = setOf("deaf", "flapping"))),
        )
        val snoozedMuted = config(snoozedAt = SNOOZED_AT, notify = setOf(MESSAGE_RATE))

        val muted = plan(base, snoozedMuted)
        muted.actions shouldContainExactly listOf(CancelProblems(ID_1))
        muted.notified.getValue(ID_1).problems shouldBe setOf("deaf")

        plan(muted.then(base, base.monitors), snoozedMuted).actions.shouldBeEmpty()
        plan(muted.then(base, base.monitors), config(notify = ALL)).actions.shouldBeEmpty()
    }

    @Test
    fun `unmuting a category with its problem present alerts anew, not while snoozed or paused`() {
        val notified = mapOf(ID_1 to Notified(MONITOR_1, problems = setOf("deaf")))
        val problems = listOf("deaf", "mlat_no_peers")

        val active = plan(state(mapOf(ID_1 to monitor(problems = problems)), notified), config(notify = ALL))
        active.actions shouldContainExactly listOf(PostProblems(ID_1, "Roof", problems))
        active.notified.getValue(ID_1).problems shouldBe problems.toSet()

        val snoozed = plan(
            state(mapOf(ID_1 to monitor(problems = problems)), notified),
            config(snoozedAt = SNOOZED_WHILE_ONLINE, notify = ALL),
        )
        snoozed.actions.shouldBeEmpty()
        snoozed.notified.getValue(ID_1).problems shouldBe setOf("deaf")

        val whilePaused = plan(state(mapOf(ID_1 to paused(problems)), notified), config(notify = ALL))
        whilePaused.actions.shouldBeEmpty()
        whilePaused.notified.getValue(ID_1).problems shouldBe setOf("deaf")
    }

    @Test
    fun `muting and unmuting while snoozed posts nothing, the problem alerts once the snooze is gone`() {
        val base = state(
            mapOf(ID_1 to monitor(problems = listOf("deaf", "flapping"))),
            mapOf(ID_1 to Notified(MONITOR_1, problems = setOf("deaf", "flapping"))),
        )

        val muted = plan(base, config(snoozedAt = SNOOZED_WHILE_ONLINE, notify = setOf(MESSAGE_RATE)))
        muted.actions shouldContainExactly listOf(CancelProblems(ID_1))
        muted.notified.getValue(ID_1).problems shouldBe setOf("deaf")
        muted.snoozeClears.shouldBeEmpty()

        val unmuted = plan(muted.then(base, base.monitors), config(snoozedAt = SNOOZED_WHILE_ONLINE))
        unmuted.actions.shouldBeEmpty()
        unmuted.notified.getValue(ID_1).problems shouldBe setOf("deaf")
        unmuted.snoozeClears.shouldBeEmpty()

        val unsnoozed = plan(unmuted.then(base, base.monitors), config())
        unsnoozed.actions shouldContainExactly listOf(PostProblems(ID_1, "Roof", listOf("deaf", "flapping")))
    }

    @Test
    fun `the first two passes with a problem of a muted category post nothing`() {
        val first = state(mapOf(ID_1 to monitor(problems = listOf("deaf", "mlat_no_peers"))), initialized = false)

        val baseline = plan(first)
        baseline.actions.shouldBeEmpty()
        baseline.notified.getValue(ID_1).problems shouldBe setOf("deaf")

        val second = plan(baseline.then(first, first.monitors))
        second.actions.shouldBeEmpty()
        second.notified shouldBe baseline.notified
    }

    @Test
    fun `an unknown code alerts with every category muted, problem alerts turned off cancel it and post nothing`() {
        val base = state(mapOf(ID_1 to monitor(problems = listOf("antenna_on_fire", "deaf"))), mapOf(ID_1 to Notified(MONITOR_1)))

        val muted = plan(base, config(notify = emptySet()))
        muted.actions shouldContainExactly listOf(PostProblems(ID_1, "Roof", listOf("antenna_on_fire")))

        val off = plan(muted.then(base, base.monitors), config(notify = emptySet()), problemAlerts = false)
        off.actions shouldContainExactly listOf(CancelProblems(ID_1))
        off.notified.getValue(ID_1).problems shouldBe emptySet()

        plan(off.then(base, base.monitors), config(notify = ALL), problemAlerts = false).actions.shouldBeEmpty()
    }

    @Test
    fun `a snoozed online feeder whose problems are all muted loses its snooze, one left to notify keeps it`() {
        val snoozed = config(snoozedAt = SNOOZED_WHILE_ONLINE)

        val muted = plan(state(mapOf(ID_1 to monitor(problems = listOf("mlat_missing"))), mapOf(ID_1 to Notified(MONITOR_1))), snoozed)
        muted.actions.shouldBeEmpty()
        muted.snoozeClears shouldBe setOf(ID_1)

        val notifiable = plan(
            state(mapOf(ID_1 to monitor(problems = listOf("mlat_missing", "deaf"))), mapOf(ID_1 to Notified(MONITOR_1))),
            snoozed,
        )
        notifiable.actions.shouldBeEmpty()
        notifiable.snoozeClears.shouldBeEmpty()
    }

    @Test
    fun `an unknown problem code is kept, not dropped`() {
        val plan = plan(state(mapOf(ID_1 to monitor(problems = listOf("antenna_on_fire"))), mapOf(ID_1 to Notified(MONITOR_1))))

        plan.actions shouldContainExactly listOf(PostProblems(ID_1, "Roof", listOf("antenna_on_fire")))
    }

    @Test
    fun `a removed feeder and a monitor the server no longer lists lose their notifications and entries`() {
        val notified = mapOf(
            ID_1 to Notified(MONITOR_1, offline = true),
            ID_2 to Notified(MONITOR_2, problems = setOf("deaf")),
        )
        // ID_1 was removed locally, ID_2 is still local but gone from the server's list
        val state = state(mapOf(ID_1 to offline()), notified)

        val plan = plan(state, config(ID_2, order = 1))

        plan.actions shouldContainExactly listOf(
            CancelOffline(ID_1),
            CancelProblems(ID_1),
            CancelOffline(ID_2),
            CancelProblems(ID_2),
        )
        plan.notified shouldBe emptyMap()
    }

    @Test
    fun `case variants of one feeder share one canonical id and the earliest added feeder's settings`() {
        val state = state(mapOf(ID_1 to offline()), mapOf(ID_1 to Notified(MONITOR_1)))

        val plan = plan(
            state,
            config(ID_1.uppercase(), label = "Later", order = 5, timeout = null),
            config(" $ID_1", label = "Earlier", order = 1),
        )

        plan.actions shouldContainExactly listOf(PostOffline(ID_1, "Earlier", OFFLINE_SINCE))
        plan.notified.keys shouldBe setOf(ID_1)
    }

    @Test
    fun `a feeder without label is named by the end of its id`() {
        val plan = plan(state(mapOf(ID_1 to offline()), mapOf(ID_1 to Notified(MONITOR_1))), config(label = null))

        plan.actions shouldContainExactly listOf(PostOffline(ID_1, ID_1.takeLast(5), OFFLINE_SINCE))
    }

    @Test
    fun `a paused monitor changes nothing`() {
        val notified = mapOf(ID_1 to Notified(MONITOR_1, offline = true, offlineSince = OFFLINE_SINCE, problems = setOf("deaf")))
        val paused = monitor(health = "unknown", unknownReason = "paused", status = "paused", problems = listOf("flapping"))

        val plan = plan(state(mapOf(ID_1 to paused), notified))

        plan.actions.shouldBeEmpty()
        plan.notified shouldBe notified
    }

    @Test
    fun `an unknown health such as a suspected outage changes nothing`() {
        val notified = mapOf(ID_1 to Notified(MONITOR_1, offline = true, offlineSince = OFFLINE_SINCE))

        listOf("upstream", "outage_suspected", "pending").forEach { reason ->
            val plan = plan(state(mapOf(ID_1 to monitor(health = "unknown", unknownReason = reason)), notified))

            plan.actions.shouldBeEmpty()
            plan.notified shouldBe notified
        }
    }

    @Test
    fun `a feeder only known locally or only on the server gets nothing`() {
        // ID_2 is local but not on the server yet, ID_3 is on the server but not local
        val state = state(
            mapOf(ID_1 to monitor(), ID_3 to offline().copy(feederId = ID_3, monitorId = "monitor-3")),
            mapOf(ID_1 to Notified(MONITOR_1)),
        )

        val plan = plan(state, config(), config(ID_2, order = 1))

        plan.actions.shouldBeEmpty()
        plan.notified.keys shouldBe setOf(ID_1)
    }

    @Test
    fun `without additions drops a new outage and new problems`() {
        val previous = mapOf(ID_1 to Notified(MONITOR_1, problems = setOf("deaf")))
        val planned = mapOf(
            ID_1 to Notified(MONITOR_1, offline = true, offlineSince = OFFLINE_SINCE, problems = setOf("deaf", "flapping")),
        )

        FeederAlertPlanner.withoutAdditions(previous, planned) shouldBe mapOf(
            ID_1 to Notified(MONITOR_1, problems = setOf("deaf")),
        )
    }

    @Test
    fun `without additions removes an ended outage and cleared problems`() {
        val previous = mapOf(
            ID_1 to Notified(MONITOR_1, offline = true, offlineSince = OFFLINE_SINCE, problems = setOf("deaf", "flapping")),
        )
        val planned = mapOf(ID_1 to Notified(MONITOR_1, problems = setOf("flapping")))

        FeederAlertPlanner.withoutAdditions(previous, planned) shouldBe mapOf(
            ID_1 to Notified(MONITOR_1, problems = setOf("flapping")),
        )
    }

    @Test
    fun `without additions keeps an ongoing outage from its recorded start`() {
        val previous = mapOf(ID_1 to Notified(MONITOR_1, offline = true, offlineSince = OFFLINE_SINCE))
        val planned = mapOf(ID_1 to Notified(MONITOR_1, offline = true, offlineSince = ONLINE_SINCE))

        FeederAlertPlanner.withoutAdditions(previous, planned) shouldBe previous
    }

    @Test
    fun `without additions starts a feeder with another monitor id empty`() {
        val previous = mapOf(
            ID_1 to Notified(MONITOR_1, offline = true, offlineSince = OFFLINE_SINCE, problems = setOf("deaf")),
        )
        val planned = mapOf(
            ID_1 to Notified(MONITOR_2, offline = true, offlineSince = OFFLINE_SINCE, problems = setOf("deaf")),
        )

        FeederAlertPlanner.withoutAdditions(previous, planned) shouldBe mapOf(ID_1 to Notified(MONITOR_2))
    }

    @Test
    fun `without additions drops a removed feeder and adds a new one empty`() {
        val previous = mapOf(
            ID_1 to Notified(MONITOR_1, offline = true, offlineSince = OFFLINE_SINCE),
            ID_2 to Notified(MONITOR_2, problems = setOf("deaf")),
        )
        val planned = mapOf(ID_3 to Notified("monitor-3", offline = true, offlineSince = OFFLINE_SINCE))

        FeederAlertPlanner.withoutAdditions(previous, planned) shouldBe mapOf(ID_3 to Notified("monitor-3"))
    }

    companion object {
        private const val ID_1 = "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d"
        private const val ID_2 = "11111111-2222-4333-8444-555555555555"
        private const val ID_3 = "22222222-3333-4444-8555-666666666666"
        private const val MONITOR_1 = "monitor-1"
        private const val MONITOR_2 = "monitor-2"
        private const val OFFLINE_SINCE = 1_760_000_000_000L
        private const val ONLINE_SINCE = OFFLINE_SINCE + 3 * 3_600_000L
        private const val NOW = OFFLINE_SINCE + 5 * 3_600_000L
        private val SNOOZED_AT: Instant = Instant.ofEpochMilli(OFFLINE_SINCE + 60_000L)
        private val SNOOZED_WHILE_ONLINE: Instant = Instant.ofEpochMilli(ONLINE_SINCE + 60_000L)
        private val ALL = FeederProblemCategory.entries.toSet()
    }
}
