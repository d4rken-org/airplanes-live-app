package eu.darken.apl.watch.core.history

import eu.darken.apl.server.api.HistoryWatchHour
import eu.darken.apl.server.api.HistoryWatchOutcome
import eu.darken.apl.server.api.QueryError
import eu.darken.apl.watch.core.db.history.WatchHistoryHourEntity
import eu.darken.apl.watch.core.db.history.WatchHistorySyncEntity
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Duration
import java.time.Instant

class WatchHistoryPlanTest : BaseTest() {

    private val now = Instant.parse("2026-10-13T10:25:00Z")
    private val currentHour = Instant.parse("2026-10-13T10:00:00Z")
    private val retainedFrom = Instant.parse("2026-10-06T10:00:00Z")

    private fun hour(at: Instant, final: Boolean) = WatchHistoryHourEntity("w1", at, 0, "full", final)

    @Test
    fun `the retained window is seven days from the start of the hour to the end of the current one`() {
        WatchHistoryPlan.retainedWindow(now) shouldBe (retainedFrom to Instant.parse("2026-10-13T11:00:00Z"))
    }

    @Test
    fun `a new evaluation starts at the first hour that is missing or provisional`() {
        val cached = listOf(
            hour(retainedFrom, final = true),
            hour(retainedFrom.plus(Duration.ofHours(1)), final = true),
            hour(retainedFrom.plus(Duration.ofHours(2)), final = false),
        )

        WatchHistoryPlan.evaluationStart(now, cached) shouldBe retainedFrom.plus(Duration.ofHours(2))
        WatchHistoryPlan.evaluationStart(now, emptyList()) shouldBe retainedFrom
    }

    @Test
    fun `with every past hour final only the current hour is asked for`() {
        var at = retainedFrom
        val cached = mutableListOf<WatchHistoryHourEntity>()
        while (at < currentHour) {
            cached.add(hour(at, final = true))
            at = at.plus(Duration.ofHours(1))
        }

        WatchHistoryPlan.evaluationStart(now, cached) shouldBe currentHour
    }

    @Test
    fun `the periodic checks until the reset are kept back`() {
        // 5 watches, 13h35m to the reset at one check an hour: 14 checks each
        WatchHistoryPlan.spendable(
            remaining = 100,
            watchCount = 5,
            now = now,
            resetsAt = Instant.parse("2026-10-14T00:00:00Z"),
            checkInterval = Duration.ofHours(1),
        ) shouldBe 30

        WatchHistoryPlan.spendable(
            remaining = 50,
            watchCount = 5,
            now = now,
            resetsAt = Instant.parse("2026-10-14T00:00:00Z"),
            checkInterval = Duration.ofHours(1),
        ) shouldBe 0
    }

    @Test
    fun `backoff doubles up to the top-up interval`() {
        (0..5).map { WatchHistoryPlan.backoff(it).toMinutes() } shouldBe listOf(15L, 30L, 60L, 120L, 180L, 180L)
    }

    private val sync = WatchHistorySyncEntity("w1", 0, nextEligibleAt = now, updatedAt = now)

    private fun next(outcome: HistoryWatchOutcome, cursorJson: String? = null, current: WatchHistorySyncEntity = sync) =
        WatchHistoryPlan.next(
            current = current,
            outcome = outcome,
            cursorJson = cursorJson,
            from = retainedFrom,
            until = currentHour.plus(Duration.ofHours(1)),
            now = now,
            tier = "FREE",
            allowanceResetsAt = Instant.parse("2026-10-14T00:00:00Z"),
        )

    @Test
    fun `a continued evaluation keeps its range and is due again right away`() {
        val result = next(
            HistoryWatchOutcome(index = 0, status = "answered", hours = listOf(HistoryWatchHour(0, 1, "full", true))),
            cursorJson = """{"hour":0}""",
        )

        result.cursorJson shouldBe """{"hour":0}"""
        result.windowFrom shouldBe retainedFrom
        result.windowUntil shouldBe currentHour.plus(Duration.ofHours(1))
        result.nextEligibleAt shouldBe now
        result.lastPassAt shouldBe null
    }

    @Test
    fun `a finished evaluation waits for the top-up`() {
        val result = next(
            HistoryWatchOutcome(index = 0, status = "answered", lastSeenAt = now.toEpochMilli(), lastSeenAircraftId = "3c65a3"),
            current = sync.copy(cursorJson = "{}", windowFrom = retainedFrom, windowUntil = currentHour),
        )

        result.cursorJson shouldBe null
        result.windowFrom shouldBe null
        result.lastPassAt shouldBe now
        result.nextEligibleAt shouldBe now.plus(Duration.ofHours(3))
        result.lastSeenAt shouldBe now
        result.lastSeenAircraftId shouldBe "3c65a3"
    }

    @Test
    fun `a later evaluation without a sighting keeps the last one`() {
        val seen = now.minus(Duration.ofDays(2))
        val result = next(
            HistoryWatchOutcome(index = 0, status = "answered", lastSeenAt = null),
            current = sync.copy(lastSeenAt = seen, lastSeenAircraftId = "3c65a3"),
        )

        result.lastSeenAt shouldBe seen
        result.lastSeenAircraftId shouldBe "3c65a3"
    }

    @Test
    fun `a page the server had no budget for backs off and keeps the cursor`() {
        val current = sync.copy(cursorJson = "{}", windowFrom = retainedFrom, windowUntil = currentHour, backoffStep = 1)

        val result = next(HistoryWatchOutcome(index = 0, status = "error", error = QueryError("budget_exhausted")), current = current)

        result.cursorJson shouldBe "{}"
        result.windowFrom shouldBe retainedFrom
        result.nextEligibleAt shouldBe now.plus(Duration.ofMinutes(30))
        result.backoffStep shouldBe 2
    }

    @Test
    fun `refusals wait for what changes them`() {
        next(HistoryWatchOutcome(index = 0, status = "error", error = QueryError("tier_restricted")))
            .restrictedTier shouldBe "FREE"
        next(HistoryWatchOutcome(index = 0, status = "error", error = QueryError("daily_allowance_exhausted")))
            .nextEligibleAt shouldBe Instant.parse("2026-10-14T00:00:00Z")
        next(HistoryWatchOutcome(index = 0, status = "error", error = QueryError("access_changed")))
            .nextEligibleAt shouldBe now
    }

    @Test
    fun `an answer the server could not evaluate starts over later`() {
        val current = sync.copy(cursorJson = "{}", windowFrom = retainedFrom, windowUntil = currentHour)

        val result = next(HistoryWatchOutcome(index = 0, status = "error", error = QueryError("invalid_request")), current = current)

        result.cursorJson shouldBe null
        result.windowFrom shouldBe null
        result.nextEligibleAt shouldBe now.plus(Duration.ofHours(3))
    }
}
