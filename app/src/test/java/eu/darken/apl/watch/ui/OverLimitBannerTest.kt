package eu.darken.apl.watch.ui

import eu.darken.apl.R
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.api.Allowance
import eu.darken.apl.server.api.RequestLimits
import eu.darken.apl.server.api.RequestRate
import eu.darken.apl.server.api.TierPolicy
import eu.darken.apl.server.api.Usage
import eu.darken.apl.watch.core.db.types.AircraftWatchEntity
import eu.darken.apl.watch.core.db.types.BaseWatchEntity
import eu.darken.apl.watch.core.server.WatchServerStatus
import eu.darken.apl.watch.core.server.WatchServerStatus.PauseReason
import eu.darken.apl.watch.core.types.AircraftWatch
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Instant

class OverLimitBannerTest : BaseTest() {

    private val overLimit = listOf(
        status("a1", held()),
        status("a2", paused(PauseReason.OVER_LIMIT)),
    )

    @Test
    fun `the free tier is told its active limit`() {
        OverLimitBanner.of(true, access(AccessState.Tier.FREE, maxActive = 7), overLimit) shouldBe
                OverLimitBanner(R.string.watch_over_limit_banner_msg, 7)
    }

    @Test
    fun `a feeder is shown no number`() {
        OverLimitBanner.of(true, access(AccessState.Tier.FEEDER, maxActive = 7), overLimit) shouldBe
                OverLimitBanner(R.string.watch_over_limit_banner_unnumbered_msg, null)
    }

    @Test
    fun `without a known active limit no number is shown`() {
        OverLimitBanner.of(true, access(AccessState.Tier.FREE, maxActive = null), overLimit) shouldBe
                OverLimitBanner(R.string.watch_over_limit_banner_unnumbered_msg, null)
        OverLimitBanner.of(true, null, overLimit) shouldBe
                OverLimitBanner(R.string.watch_over_limit_banner_unnumbered_msg, null)
    }

    @Test
    fun `outside server mode there is no banner`() {
        OverLimitBanner.of(false, access(AccessState.Tier.FREE, maxActive = 7), overLimit) shouldBe null
    }

    @Test
    fun `without a watch over the limit there is no banner`() {
        val statuses = listOf(
            status("a1", held()),
            status("a2", paused(PauseReason.TIER)),
            status("a3", paused(PauseReason.USER)),
            status("a4", null),
        )
        OverLimitBanner.of(true, access(AccessState.Tier.FREE, maxActive = 7), statuses) shouldBe null
        OverLimitBanner.of(true, access(AccessState.Tier.FREE, maxActive = 7), emptyList()) shouldBe null
    }

    private fun status(id: String, server: WatchServerStatus?) = AircraftWatch.Status(
        watch = AircraftWatch(
            BaseWatchEntity(id = id, watchType = AircraftWatchEntity.TYPE_KEY),
            AircraftWatchEntity(id = id, hexCode = "3C65A3"),
        ),
        lastCheck = null,
        lastHit = null,
        server = server,
    )

    private fun held() = WatchServerStatus(
        isHeld = true,
        status = WatchServerStatus.Status.ACTIVE,
        pauseReason = null,
        rejection = null,
        presence = WatchServerStatus.Presence.ABSENT,
        lastMatch = null,
    )

    private fun paused(reason: PauseReason) = held().copy(status = WatchServerStatus.Status.PAUSED, pauseReason = reason)

    private fun access(tier: AccessState.Tier, maxActive: Int?) = AccessState(
        installationId = "installation",
        fetchedAt = Instant.EPOCH,
        tier = tier,
        restricted = false,
        allowanceScope = "principal",
        limits = TierPolicy(
            viewingIntervalSeconds = 5, viewingBurst = 3, viewingPerDay = 25000, searchesPerDay = 25,
            watchesPerDay = 1000, maxRadiusNm = 25, watchTypes = listOf("hex", "callsign"),
            maxActiveWatches = maxActive,
        ),
        usage = Usage(
            resetsAt = 3_600_000,
            viewing = Allowance(25000, 0, 0, 25000),
            search = Allowance(25, 0, 0, 25),
            watch = Allowance(1000, 0, 0, 1000),
        ),
        installationRequests = RequestLimits(
            viewing = RequestRate(perSecond = 0.2, burst = 3),
            search = RequestRate(perSecond = 1.0, burst = 3),
            watch = RequestRate(perSecond = 1.0, burst = 3),
            concurrency = 3,
        ),
        aircraftOperations = listOf(AccessState.OPERATION_WATCHLIST),
    )
}
