package eu.darken.apl.map.core

import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.server.ServerClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds

/**
 * What the map draws: the live frames of [MapAircraftProvider], or a day of one aircraft's history
 * while a [Replay] is open. Only one of them is collected at a time, so the live polling stops for
 * as long as the history is shown. Collect [frames] once and share it. Made by the owner of
 * [provider], which has to be the same instance the rest of the screen talks to.
 */
class MapFrames(
    private val provider: MapAircraftProvider,
    private val serverClock: ServerClock,
    private val dispatcherProvider: DispatcherProvider,
) {

    /**
     * Where a history stands. [cursor] is the time shown; every command counts up [revision], and
     * playback only moves the cursor of the state it started from, so a seek is never overwritten.
     */
    data class State(
        val day: LocalDate,
        val cursor: Long,
        val playing: Boolean = false,
        val speed: Int = DEFAULT_SPEED,
        val revision: Long = 0,
    )

    /** One opening of the history. */
    class Replay internal constructor(
        val hex: AircraftHex,
        val timeline: HistoryTimeline,
        initial: State,
    ) {
        val state = MutableStateFlow(initial)

        /** The newest frame's history, for commands to start from where it is drawn. */
        @Volatile internal var sent: MapAircraftProvider.Replay? = null

        internal fun command(change: (State) -> State) = state.update { change(it).copy(revision = it.revision + 1) }
    }

    private val _replay = MutableStateFlow<Replay?>(null)
    val replay: StateFlow<Replay?> = _replay

    private val producers = AtomicLong()
    // Opening and closing come from different threads, numbering and publishing go together
    private val lock = Any()
    private var openings = 0L
    @Volatile private var resume = false

    sealed interface Opening {
        data class Opened(val replay: Replay) : Opening

        /** Nothing was seen in the days the history keeps. */
        data object Empty : Opening

        /** A load for the aircraft was already out. */
        data object Busy : Opening

        /** Closed or opened again while loading, the answer was dropped, also a failed one. */
        data object Superseded : Opening
    }

    /** Loads [hex]'s history and opens it at the start of its newest positions. */
    suspend fun open(hex: AircraftHex, zone: ZoneId): Opening {
        val opening = synchronized(lock) { ++openings }
        val track = try {
            provider.loadHistory(hex)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (synchronized(lock) { openings != opening }) return Opening.Superseded
            throw e
        } ?: return Opening.Busy
        // Thousands of points, laid out before taking the lock
        val replay = build(hex, track, zone)
        return synchronized(lock) {
            when {
                openings != opening -> Opening.Superseded
                replay == null -> Opening.Empty
                else -> Opening.Opened(replay).also { _replay.value = replay }
            }
        }
    }

    internal fun open(hex: AircraftHex, track: AircraftHistoryTracks.Track, zone: ZoneId): Replay? =
        build(hex, track, zone)?.also { replay ->
            synchronized(lock) {
                openings++
                _replay.value = replay
            }
        }

    private fun build(hex: AircraftHex, track: AircraftHistoryTracks.Track, zone: ZoneId): Replay? {
        val timeline = HistoryTimeline(track, zone)
        val start = timeline.latestStart() ?: return null
        return Replay(hex.uppercase(), timeline, State(day = timeline.localDate(start), cursor = start))
    }

    /** Also drops a load that is still out. */
    fun close() = synchronized(lock) {
        openings++
        _replay.value = null
    }

    /** Paused at the day's first positions, or its start without any. */
    fun showDay(replay: Replay, date: LocalDate) {
        if (date !in replay.timeline.days) return
        val day = replay.timeline.day(date)
        val first = listOfNotNull(day.stretches.firstOrNull()?.start, day.sightings.firstOrNull()?.observedAt).minOrNull()
        val cursor = first ?: maxOf(day.start, replay.timeline.track.from)
        replay.command { it.copy(day = date, cursor = cursor, playing = false) }
    }

    fun seek(replay: Replay, time: Long) = replay.command { state ->
        val day = replay.timeline.day(state.day)
        state.copy(cursor = time.coerceIn(day.start, day.end - 1), playing = false)
    }

    fun setPlaying(replay: Replay, playing: Boolean) = replay.command { it.copy(cursor = drawnCursor(replay, it), playing = playing) }

    fun setSpeed(replay: Replay, speed: Int) = replay.command { state ->
        val cursor = drawnCursor(replay, state)
        // Shown at the end already, playing on from there would start the day over
        val ended = state.playing && replay.timeline.playFrom(replay.timeline.day(state.day), cursor) != cursor
        state.copy(cursor = cursor, speed = speed, playing = state.playing && !ended)
    }

    /** While playing, the display is ahead of [State.cursor] by up to a step, a pause stops where it shows. */
    private fun drawnCursor(replay: Replay, state: State): Long {
        val sent = replay.sent?.takeIf { state.playing && it.playback != null && it.cursor == state.cursor && it.day.date == state.day }
        return sent?.cursorAt(serverClock.elapsed()) ?: state.cursor
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val frames: Flow<MapAircraftProvider.Frame> = _replay.flatMapLatest { replay ->
        val producer = producers.incrementAndGet()
        if (replay == null) {
            flow {
                // Before the provider starts collecting, so its first frame is already the fresh start
                if (resume) provider.prepareResume()
                resume = false
                emitAll(provider.frames.map { it.copy(producer = producer) })
            }
        } else {
            resume = true
            replayFrames(replay, producer)
        }
    }

    private fun replayFrames(replay: Replay, producer: Long): Flow<MapAircraftProvider.Frame> = channelFlow {
        val running = AtomicBoolean(true)
        try {
            // Playback's own cursor steps keep the revision, only commands start this over
            replay.state.distinctUntilChangedBy { it.revision }.collectLatest { commanded ->
                val day = replay.timeline.day(commanded.day)
                if (!commanded.playing) {
                    send(frame(replay, day, commanded.cursor, producer))
                    return@collectLatest
                }
                var state = commanded.copy(cursor = replay.timeline.playFrom(day, commanded.cursor))
                if (!replay.state.compareAndSet(commanded, state)) return@collectLatest
                // Real time between steps drives the cursor, so a late step catches up instead of slowing down
                var last = serverClock.elapsed()
                var holdUntil = Long.MIN_VALUE
                fun playback() = MapAircraftProvider.Playback(replay.timeline, replay.hex, state.speed, last, holdUntil, running)
                send(frame(replay, day, state.cursor, producer, playback()))
                while (true) {
                    delay(REPLAY_TICK)
                    val now = serverClock.elapsed()
                    if (now < holdUntil) continue
                    // Counted like Replay.cursorAt, MapFramesTest checks that the two agree
                    val step = replay.timeline.advance(day, state.cursor, (now - maxOf(last, holdUntil)) * state.speed)
                    last = now
                    if (step.holdMs > 0) holdUntil = now + step.holdMs
                    val next = if (step.playing) {
                        state.copy(cursor = step.cursor)
                    } else {
                        state.copy(cursor = step.cursor, playing = false, revision = state.revision + 1)
                    }
                    // A command in between wins, the collector is already starting over with it
                    if (!replay.state.compareAndSet(state, next)) return@collectLatest
                    state = next
                    send(frame(replay, day, state.cursor, producer, playback().takeIf { step.playing }))
                    if (!step.playing) return@collectLatest
                }
            }
        } finally {
            running.set(false)
            // Leaving the screen pauses, coming back does not jump ahead by the time away
            replay.state.update { if (it.playing) it.copy(playing = false, revision = it.revision + 1) else it }
        }
    }.flowOn(dispatcherProvider.Default)

    private fun frame(
        replay: Replay,
        day: HistoryTimeline.Day,
        cursor: Long,
        producer: Long,
        playback: MapAircraftProvider.Playback? = null,
    ): MapAircraftProvider.Frame {
        return MapAircraftProvider.Frame(
            traffic = emptyList(),
            trafficVersion = 0,
            fades = emptyMap(),
            selectedHex = replay.hex,
            selected = replay.timeline.planeAt(replay.hex, cursor),
            selectedDetails = null,
            selectedMissing = null,
            selectedTrail = emptyList(),
            selectedTrailHead = null,
            tracks = null,
            viewport = null,
            onScreen = emptyList(),
            shownCount = 0,
            capped = false,
            totalMatching = null,
            producer = producer,
            replay = MapAircraftProvider.Replay(day, cursor, playback),
        ).also { replay.sent = it.replay }
    }

    companion object {
        val SPEEDS = listOf(60, 300, 1_800)
        const val DEFAULT_SPEED = 300
        private val REPLAY_TICK = 33.milliseconds
    }
}
