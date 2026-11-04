package eu.darken.apl.watch.ui

import eu.darken.apl.watch.core.WatchId
import eu.darken.apl.watch.core.WatchSortMode
import eu.darken.apl.watch.core.db.types.AircraftWatchEntity
import eu.darken.apl.watch.core.db.types.BaseWatchEntity
import eu.darken.apl.watch.core.history.WatchActivityTimeline
import eu.darken.apl.watch.core.history.WatchActivityTimeline.Cell
import eu.darken.apl.watch.core.history.WatchCheck
import eu.darken.apl.watch.core.types.AircraftWatch
import eu.darken.apl.watch.core.types.Watch
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Instant

class WatchListSortTest : BaseTest() {

    @Test
    fun `most seen puts more active hours first`() {
        val statuses = listOf(status("once"), status("often"), status("never"))
        val activity = mapOf(
            "once" to timeline(Cell.ACTIVE, Cell.INACTIVE, Cell.SAMPLED_QUIET),
            "often" to timeline(Cell.ACTIVE, Cell.ACTIVE, Cell.UNKNOWN),
            "never" to timeline(Cell.INACTIVE, Cell.UNKNOWN, Cell.SAMPLED_QUIET),
        )

        statuses.sortedFor(WatchSortMode.BY_MOST_SEEN, activity, emptyMap()).ids() shouldBe
                listOf("often", "once", "never")
    }

    @Test
    fun `most seen breaks ties by last seen, then by date added`() {
        val statuses = listOf(
            status("old", addedAt = 1),
            status("new", addedAt = 2),
            status("seen", lastHitAt = 10),
            status("recent", addedAt = 0),
        )
        val activity = statuses.associate { it.id to timeline(Cell.ACTIVE) }

        statuses.sortedFor(WatchSortMode.BY_MOST_SEEN, activity, mapOf("recent" to Instant.ofEpochSecond(20))).ids() shouldBe
                listOf("recent", "seen", "new", "old")
    }

    @Test
    fun `most seen puts watches without activity data last`() {
        val statuses = listOf(status("unknown", lastHitAt = 10), status("active"))

        statuses.sortedFor(WatchSortMode.BY_MOST_SEEN, mapOf("active" to timeline(Cell.ACTIVE)), emptyMap()).ids() shouldBe
                listOf("active", "unknown")
    }

    @Test
    fun `most seen counts watches without activity data as never active`() {
        val statuses = listOf(status("quiet", lastHitAt = 10), status("missing", lastHitAt = 20))

        statuses.sortedFor(WatchSortMode.BY_MOST_SEEN, mapOf("quiet" to timeline(Cell.INACTIVE, Cell.SAMPLED_QUIET)), emptyMap()).ids() shouldBe
                listOf("missing", "quiet")
    }

    @Test
    fun `sort modes keep their stored names`() {
        val stored = mapOf(
            WatchSortMode.BY_NOTE to "\"by_note\"",
            WatchSortMode.BY_LAST_SEEN to "\"by_last_seen\"",
            WatchSortMode.BY_CREATED to "\"by_created\"",
            WatchSortMode.BY_MOST_SEEN to "\"by_most_seen\"",
        )

        WatchSortMode.entries.associateWith { Json.encodeToString(WatchSortMode.serializer(), it) } shouldBe stored
        stored.forEach { (mode, name) -> Json.decodeFromString(WatchSortMode.serializer(), name) shouldBe mode }
    }

    private fun List<Watch.Status>.ids() = map { it.id }

    private fun timeline(vararg cells: Cell) = WatchActivityTimeline(Instant.EPOCH, cells.toList())

    private fun status(id: WatchId, addedAt: Long = 0, lastHitAt: Long? = null) = AircraftWatch.Status(
        watch = AircraftWatch(
            BaseWatchEntity(id = id, watchType = AircraftWatchEntity.TYPE_KEY, createdAt = Instant.ofEpochSecond(addedAt)),
            AircraftWatchEntity(id = id, hexCode = "3C65A3"),
        ),
        lastCheck = null,
        lastHit = lastHitAt?.let { WatchCheck(watchId = id, checkAt = Instant.ofEpochSecond(it), aircraftCount = 1) },
    )
}
