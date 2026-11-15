package eu.darken.apl.map.core

import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.api.TrailPoint
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.coroutine.TestDispatcherProvider
import java.io.IOException
import java.time.ZoneId

/** Live frames, playback and the clock all run on the test scheduler's virtual time. */
class MapFramesTest : BaseTest() {

    private val provider = mockk<MapAircraftProvider>()
    private var liveCollections = 0
    private var liveActive = 0
    private val zone = ZoneId.of("UTC")

    // Two stretches an hour apart, starting at 10:00 UTC of some day
    private val base = 1_791_972_000_000L
    private val track = AircraftHistoryTracks.Track(
        points = listOf(0L, 60_000, 120_000, 3_600_000, 3_660_000).map { TrailPoint(50.0, 8.0, 35_000.0, base + it) },
        maxGapMs = 300_000,
        from = base - 3_600_000,
        until = base + 7_200_000,
    )

    private fun live() = MapAircraftProvider.Frame(
        traffic = emptyList(), trafficVersion = 1, fades = emptyMap(), selectedHex = null, selected = null,
        selectedDetails = null, selectedMissing = null, selectedTrail = emptyList(), selectedTrailHead = null,
        tracks = null, viewport = null, onScreen = emptyList(), shownCount = 0, capped = false, totalMatching = null,
    )

    private fun TestScope.frames(): MapFrames {
        every { provider.frames } returns flow {
            liveCollections++
            liveActive++
            try {
                while (true) {
                    emit(live())
                    delay(100)
                }
            } finally {
                liveActive--
            }
        }
        every { provider.prepareResume() } just runs
        val clock = ServerClock(object : MonotonicClock {
            override fun elapsed(): Long = testScheduler.currentTime
        })
        return MapFrames(provider, clock, TestDispatcherProvider(StandardTestDispatcher(testScheduler)))
    }

    private fun TestScope.settle(millis: Long = 50) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun `everyone shares one live collection, the history replaces it and closing brings one back`() = runTest {
        val mapFrames = frames()
        val shared = mapFrames.frames.shareIn(backgroundScope, SharingStarted.WhileSubscribed(), replay = 1)
        var latest: MapAircraftProvider.Frame? = null
        backgroundScope.launch { shared.collect { latest = it } }
        backgroundScope.launch { shared.collect { } }
        settle()
        liveCollections shouldBe 1
        val liveProducer = latest!!.producer

        val replay = mapFrames.open("3c65a3", track, zone)!!
        settle()
        liveActive shouldBe 0
        latest!!.replay!!.cursor shouldBe base + 3_600_000
        latest!!.selectedHex shouldBe "3C65A3"
        (latest!!.producer != liveProducer) shouldBe true

        // Controls move the same history on, they do not start it over
        val replayProducer = latest!!.producer
        mapFrames.setSpeed(replay, 1_800)
        mapFrames.seek(replay, base + 60_000)
        settle()
        mapFrames.replay.value shouldBe replay
        latest!!.replay!!.cursor shouldBe base + 60_000
        latest!!.producer shouldBe replayProducer
        liveCollections shouldBe 1

        mapFrames.close()
        settle()
        liveCollections shouldBe 2
        liveActive shouldBe 1
        latest!!.replay shouldBe null
        verify(exactly = 1) { provider.prepareResume() }
    }

    @Test
    fun `a paused history draws at once when watched again, and leaving pauses playback without catching up`() = runTest {
        val mapFrames = frames()
        val replay = mapFrames.open("3C65A3", track, zone)!!
        mapFrames.seek(replay, base)
        var latest: MapAircraftProvider.Frame? = null
        val watching = backgroundScope.launch { mapFrames.frames.collect { latest = it } }
        settle()
        mapFrames.setSpeed(replay, 60)
        mapFrames.setPlaying(replay, true)
        settle(1_000)
        // About a second at 60 times
        (replay.state.value.cursor in base + 55_000..base + 61_000) shouldBe true

        watching.cancel()
        settle(10_000)
        replay.state.value.playing shouldBe false
        val stopped = replay.state.value.cursor

        latest = null
        backgroundScope.launch { mapFrames.frames.collect { latest = it } }
        runCurrent()
        latest!!.replay!!.cursor shouldBe stopped
        settle(10_000)
        replay.state.value.cursor shouldBe stopped
    }

    @Test
    fun `a seek or a day change during playback wins over the playback's next step`() = runTest {
        val twoDays = track.copy(
            points = track.points + listOf(0L, 60_000).map { TrailPoint(50.0, 8.0, 35_000.0, base + 86_400_000 + it) },
            until = base + 90_000_000,
        )
        val mapFrames = frames()
        val replay = mapFrames.open("3C65A3", twoDays, zone)!!
        var latest: MapAircraftProvider.Frame? = null
        backgroundScope.launch { mapFrames.frames.collect { latest = it } }
        mapFrames.showDay(replay, replay.timeline.days.first())
        settle()
        replay.state.value.cursor shouldBe base

        mapFrames.setSpeed(replay, 60)
        mapFrames.setPlaying(replay, true)
        settle(500)
        mapFrames.seek(replay, base + 90_000)
        settle(500)
        replay.state.value.cursor shouldBe base + 90_000
        replay.state.value.playing shouldBe false

        mapFrames.setPlaying(replay, true)
        settle(500)
        mapFrames.showDay(replay, replay.timeline.days.last())
        settle(500)
        // The day and its time change together, playback does not carry the old day's time over
        replay.state.value.day shouldBe replay.timeline.days.last()
        replay.state.value.cursor shouldBe base + 86_400_000
        latest!!.replay!!.day.date shouldBe replay.timeline.days.last()
        latest!!.replay!!.cursor shouldBe base + 86_400_000
    }

    @Test
    fun `a new speed counts from when it was set, without a jump`() = runTest {
        val mapFrames = frames()
        val replay = mapFrames.open("3C65A3", track, zone)!!
        backgroundScope.launch { mapFrames.frames.collect { } }
        mapFrames.seek(replay, base)
        settle()
        mapFrames.setSpeed(replay, 60)
        mapFrames.setPlaying(replay, true)
        settle(1_000)
        val before = replay.state.value.cursor

        mapFrames.setSpeed(replay, 1)
        settle(1_000)
        // About a second at once, not the second before at the new speed
        (replay.state.value.cursor - before in 0..2_000) shouldBe true
        replay.state.value.playing shouldBe true
    }

    @Test
    fun `the display's cursor between steps lands where the next step does, also across a sighting`() = runTest {
        // A stretch, a sighting alone in the gap, another stretch
        val withSighting = track.copy(
            points = listOf(0L, 60_000, 1_000_000, 2_000_000, 2_060_000).map { TrailPoint(50.0, 8.0, 35_000.0, base + it) },
        )
        val mapFrames = frames()
        val replay = mapFrames.open("3C65A3", withSighting, zone)!!
        val sent = mutableListOf<Pair<Long, MapAircraftProvider.Replay>>()
        backgroundScope.launch { mapFrames.frames.collect { sent.add(testScheduler.currentTime to it.replay!!) } }
        mapFrames.seek(replay, base)
        settle()
        mapFrames.setSpeed(replay, 1_800)
        mapFrames.setPlaying(replay, true)
        settle(10_000)

        replay.state.value.playing shouldBe false
        val played = sent.dropWhile { it.second.playback == null }
        (played.size > 3) shouldBe true
        played.map { it.second.cursor } shouldContain base + 1_000_000
        played.zipWithNext().forEach { (before, after) ->
            before.second.cursorAt(after.first) shouldBe after.second.cursor
        }
        // From where playback ended on, there is nothing more to play
        val ended = played.dropWhile { it.second.playback != null }
        ended.first().second.cursor shouldBe base + 2_060_000
        ended.all { it.second.playback == null } shouldBe true
    }

    @Test
    fun `pausing or a new speed between steps starts from where the display has got to`() = runTest {
        val mapFrames = frames()
        val replay = mapFrames.open("3C65A3", track, zone)!!
        var latest: MapAircraftProvider.Replay? = null
        backgroundScope.launch { mapFrames.frames.collect { latest = it.replay } }
        mapFrames.seek(replay, base)
        settle()
        mapFrames.setSpeed(replay, 60)
        mapFrames.setPlaying(replay, true)
        // Between two steps, the display is ahead of the last one
        settle(1_000)
        val step = latest!!
        val shown = step.cursorAt(testScheduler.currentTime)
        (shown > step.cursor) shouldBe true

        mapFrames.setSpeed(replay, 1)
        runCurrent()
        replay.state.value.cursor shouldBe shown
        latest!!.cursor shouldBe shown

        settle(1_000)
        val beforePause = latest!!.cursorAt(testScheduler.currentTime)
        (beforePause > latest!!.cursor) shouldBe true
        mapFrames.setPlaying(replay, false)
        settle()
        replay.state.value.cursor shouldBe beforePause
        latest!!.playback shouldBe null
    }

    @Test
    fun `a playing frame kept from before the frames stopped does not move on`() = runTest {
        val mapFrames = frames()
        val replay = mapFrames.open("3C65A3", track, zone)!!
        var latest: MapAircraftProvider.Replay? = null
        val watching = backgroundScope.launch { mapFrames.frames.collect { latest = it.replay } }
        mapFrames.seek(replay, base)
        settle()
        mapFrames.setSpeed(replay, 60)
        mapFrames.setPlaying(replay, true)
        settle(500)
        val kept = latest!!
        val at = kept.playback!!.at
        (kept.cursorAt(at + 20) > kept.cursor) shouldBe true

        watching.cancel()
        settle()
        kept.cursorAt(at + 20) shouldBe kept.cursor
    }

    @Test
    fun `pausing or a new speed when the display already shows the day's end stops there`() = runTest {
        val mapFrames = frames()
        val replay = mapFrames.open("3C65A3", track, zone)!!
        backgroundScope.launch { mapFrames.frames.collect { } }
        suspend fun playIntoTheEnd() {
            // Half a second of track before the day's last position, 8 ms at 60 times
            mapFrames.seek(replay, base + 3_659_500)
            settle()
            mapFrames.setSpeed(replay, 60)
            mapFrames.setPlaying(replay, true)
            runCurrent()
            // The display has reached the end, playback's next step has not come yet
            advanceTimeBy(10)
        }

        playIntoTheEnd()
        mapFrames.setSpeed(replay, 1)
        runCurrent()
        replay.state.value.cursor shouldBe base + 3_660_000
        replay.state.value.playing shouldBe false

        playIntoTheEnd()
        mapFrames.setPlaying(replay, false)
        runCurrent()
        replay.state.value.cursor shouldBe base + 3_660_000
        replay.state.value.playing shouldBe false
    }

    @Test
    fun `playback ends where the day's positions end`() = runTest {
        val mapFrames = frames()
        val replay = mapFrames.open("3C65A3", track, zone)!!
        backgroundScope.launch { mapFrames.frames.collect { } }
        settle()
        mapFrames.setSpeed(replay, 1_800)
        mapFrames.setPlaying(replay, true)
        settle(60_000)

        replay.state.value.cursor shouldBe base + 3_660_000
        replay.state.value.playing shouldBe false
    }

    @Test
    fun `an answer or failure for an opening that was closed meanwhile is dropped`() = runTest {
        val mapFrames = frames()
        val answer = CompletableDeferred<AircraftHistoryTracks.Track>()
        coEvery { provider.loadHistory("3C65A3") } coAnswers { answer.await() }

        val opening = async { mapFrames.open("3C65A3", zone) }
        runCurrent()
        mapFrames.close()
        answer.complete(track)
        opening.await() shouldBe MapFrames.Opening.Superseded
        mapFrames.replay.value shouldBe null

        val failing = CompletableDeferred<AircraftHistoryTracks.Track>()
        coEvery { provider.loadHistory("3C65A3") } coAnswers { failing.await() }
        val second = async { mapFrames.open("3C65A3", zone) }
        runCurrent()
        mapFrames.close()
        failing.completeExceptionally(IOException("offline"))
        second.await() shouldBe MapFrames.Opening.Superseded

        // Without anything in between the failure is the caller's to tell
        coEvery { provider.loadHistory("3C65A3") } throws IOException("offline")
        shouldThrow<IOException> { mapFrames.open("3C65A3", zone) }
        coEvery { provider.loadHistory("3C65A3") } returns track
        mapFrames.open("3C65A3", zone).shouldBeInstanceOf<MapFrames.Opening.Opened>()
        coEvery { provider.loadHistory("3C65A3") } returns track.copy(points = emptyList())
        mapFrames.open("3C65A3", zone) shouldBe MapFrames.Opening.Empty
        coEvery { provider.loadHistory("3C65A3") } returns null
        mapFrames.open("3C65A3", zone) shouldBe MapFrames.Opening.Busy
    }
}
