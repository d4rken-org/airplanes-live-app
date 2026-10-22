package eu.darken.apl.watch.ui

import eu.darken.apl.watch.core.db.types.AircraftWatchEntity
import eu.darken.apl.watch.core.db.types.BaseWatchEntity
import eu.darken.apl.watch.core.history.WatchCheck
import eu.darken.apl.watch.core.server.WatchServerStatus
import eu.darken.apl.watch.core.server.WatchServerStatus.PauseReason
import eu.darken.apl.watch.core.types.AircraftWatch
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Instant

class WatchServerLabelTest : BaseTest() {

    @Test
    fun `outside server mode nothing is labelled`() {
        WatchServerLabel.of(status(server = paused(PauseReason.LEASE)), isServerMode = false) shouldBe null
        WatchServerLabel.of(status(server = refused(WatchServerStatus.Rejection.INVALID)), isServerMode = false) shouldBe null
    }

    @Test
    fun `a checked watch has no label`() {
        WatchServerLabel.of(status(server = held()), isServerMode = true) shouldBe null
        WatchServerLabel.of(status(), isServerMode = true) shouldBe null
    }

    @Test
    fun `the server's pause reason says why a watch is not checked`() {
        WatchServerLabel.of(status(server = paused(PauseReason.OVER_LIMIT)), true) shouldBe
                WatchServerLabel.NOT_CHECKED_OVER_LIMIT
        WatchServerLabel.of(status(server = paused(PauseReason.TIER)), true) shouldBe WatchServerLabel.NOT_CHECKED_TIER
        WatchServerLabel.of(status(server = paused(PauseReason.RESTRICTED)), true) shouldBe
                WatchServerLabel.NOT_CHECKED_RESTRICTED
        WatchServerLabel.of(status(server = paused(PauseReason.LEASE)), true) shouldBe WatchServerLabel.NOT_CHECKED_LEASE
    }

    @Test
    fun `a pause by the user or for an unknown reason is labelled as not checked`() {
        WatchServerLabel.of(status(server = paused(PauseReason.USER)), true) shouldBe WatchServerLabel.NOT_CHECKED
        WatchServerLabel.of(status(server = paused(null)), true) shouldBe WatchServerLabel.NOT_CHECKED
    }

    @Test
    fun `a refused upload is labelled while the server does not hold the watch`() {
        WatchServerLabel.of(status(server = refused(WatchServerStatus.Rejection.INVALID)), true) shouldBe
                WatchServerLabel.REJECTED_INVALID
        WatchServerLabel.of(status(server = refused(WatchServerStatus.Rejection.STORAGE)), true) shouldBe
                WatchServerLabel.REJECTED_STORAGE
        WatchServerLabel.of(
            status(server = held().copy(rejection = WatchServerStatus.Rejection.INVALID)),
            true,
        ) shouldBe null
    }

    @Test
    fun `a held watch that is present has no count, also with a past match`() {
        val present = held().copy(presence = WatchServerStatus.Presence.PRESENT)
        status(server = present).liveNow(liveCount = 0) shouldBe LiveNow(count = null)
        val pastMatch = WatchCheck(watchId = "a1", checkAt = Instant.EPOCH, aircraftCount = 5, seenHexes = emptySet())
        status(server = present.copy(lastMatch = pastMatch)).liveNow(liveCount = 2) shouldBe LiveNow(count = null)
    }

    @Test
    fun `a held watch that is not present is not live, whatever the cache tracks`() {
        status(server = held()).liveNow(liveCount = 3) shouldBe null
        status(server = held().copy(presence = WatchServerStatus.Presence.UNKNOWN)).liveNow(liveCount = 3) shouldBe null
    }

    @Test
    fun `a watch the app checks counts its tracked aircraft`() {
        status().liveNow(liveCount = 3) shouldBe LiveNow(count = 3)
        status().liveNow(liveCount = 0) shouldBe null
    }

    private fun status(server: WatchServerStatus? = null) = AircraftWatch.Status(
        watch = AircraftWatch(
            BaseWatchEntity(id = "a1", watchType = AircraftWatchEntity.TYPE_KEY),
            AircraftWatchEntity(id = "a1", hexCode = "3C65A3"),
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

    private fun paused(reason: PauseReason?) = held().copy(status = WatchServerStatus.Status.PAUSED, pauseReason = reason)

    private fun refused(rejection: WatchServerStatus.Rejection) = WatchServerStatus(
        isHeld = false,
        status = null,
        pauseReason = null,
        rejection = rejection,
        presence = null,
        lastMatch = null,
    )
}
