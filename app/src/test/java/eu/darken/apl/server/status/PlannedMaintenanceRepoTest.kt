package eu.darken.apl.server.status

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.serialization.SerializationModule
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.api.MetaResponse
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.TestCompatibilityStore
import java.time.Duration
import java.time.Instant

class PlannedMaintenanceRepoTest : BaseTest() {

    private val json = SerializationModule().json()
    private val compatStore = TestCompatibilityStore()

    @AfterEach
    fun teardown() {
        compatStore.close()
    }

    /** Server time [T0] at virtual time zero, moving with the test scheduler. */
    private fun TestScope.serverClock() = ServerClock(object : MonotonicClock {
        override fun elapsed(): Long = testScheduler.currentTime
    }).apply { noteServerTime(T0.toEpochMilli()) }

    private fun repo(dataStore: DataStore<Preferences>, serverClock: ServerClock) =
        PlannedMaintenanceRepo(dataStore = dataStore, json = json, serverClock = serverClock)

    private fun TestScope.observe(repo: PlannedMaintenanceRepo): List<PlannedMaintenance?> {
        val seen = mutableListOf<PlannedMaintenance?>()
        backgroundScope.launch { repo.upcoming.collect { seen += it } }
        runCurrent()
        return seen
    }

    private fun TestScope.advance(by: Duration) {
        advanceTimeBy(by.toMillis())
        runCurrent()
    }

    @Test
    fun `an answer stores a valid notice and clears it when absent or malformed`() = runTest {
        val repo = repo(MemoryDataStore(), serverClock())
        val first = notice("test-notice-1", startsIn = Duration.ofHours(30))
        val second = notice("test-notice-2", startsIn = Duration.ofHours(40))

        repo.onMetaAnswered(meta(first.toJson()))
        repo.announced.first() shouldBe first

        repo.onMetaAnswered(meta(second.toJson()))
        repo.announced.first() shouldBe second

        repo.onMetaAnswered(meta(null))
        repo.announced.first().shouldBeNull()

        repo.onMetaAnswered(meta(first.toJson()))
        repo.onMetaAnswered(meta(JsonPrimitive("soon")))
        repo.announced.first().shouldBeNull()
    }

    @Test
    fun `a notice shows from a day before its start until it starts, without a new answer`() = runTest {
        val repo = repo(MemoryDataStore(), serverClock())
        val notice = notice("test-notice-1", startsIn = Duration.ofHours(30))
        repo.onMetaAnswered(meta(notice.toJson()))

        val seen = observe(repo)
        seen.last().shouldBeNull()

        advance(Duration.ofHours(6).minusMillis(1))
        seen.last().shouldBeNull()

        advance(Duration.ofMillis(1))
        seen.last() shouldBe notice

        advance(Duration.ofHours(24).minusMillis(1))
        seen.last() shouldBe notice

        advance(Duration.ofMillis(1))
        seen.last().shouldBeNull()

        seen shouldBe listOf(null, notice, null)
    }

    @Test
    fun `a server clock anchored anew moves the window within a minute`() = runTest {
        val clock = serverClock()
        val repo = repo(MemoryDataStore(), clock)
        val notice = notice("test-notice-1", startsIn = Duration.ofHours(30))
        repo.onMetaAnswered(meta(notice.toJson()))
        val seen = observe(repo)
        seen.last().shouldBeNull()

        clock.noteServerTime(T0.plus(Duration.ofHours(7)).toEpochMilli())
        advance(PlannedMaintenanceRepo.MAX_WAIT)

        seen.last() shouldBe notice
    }

    @Test
    fun `a notice past its start is not shown`() = runTest {
        val repo = repo(MemoryDataStore(), serverClock())
        repo.onMetaAnswered(meta(notice("test-notice-1", startsIn = Duration.ofHours(-1)).toJson()))

        repo.upcoming.first().shouldBeNull()
    }

    @Test
    fun `a dismissed notice stays hidden, a new announcement shows again`() = runTest {
        val repo = repo(MemoryDataStore(), serverClock())
        val first = notice("test-notice-1", startsIn = Duration.ofHours(2))
        repo.onMetaAnswered(meta(first.toJson()))
        val seen = observe(repo)
        seen.last() shouldBe first

        repo.dismiss(first.id)
        runCurrent()
        seen.last().shouldBeNull()

        repo.onMetaAnswered(meta(first.toJson()))
        runCurrent()
        seen.last().shouldBeNull()

        val second = notice("test-notice-2", startsIn = Duration.ofHours(3))
        repo.onMetaAnswered(meta(second.toJson()))
        runCurrent()
        seen.last() shouldBe second
    }

    @Test
    fun `the notice and its dismissal survive a new holder on the same store`() = runBlocking<Unit> {
        val clock = ServerClock(object : MonotonicClock {
            override fun elapsed(): Long = 0L
        }).apply { noteServerTime(T0.toEpochMilli()) }
        val notice = notice("test-notice-1", startsIn = Duration.ofHours(2))
        val before = repo(compatStore.dataStore, clock)
        before.onMetaAnswered(meta(notice.toJson()))
        before.dismiss(notice.id)

        val after = repo(compatStore.dataStore, clock)

        after.announced.first() shouldBe notice
        after.upcoming.first().shouldBeNull()

        val next = notice("test-notice-2", startsIn = Duration.ofHours(3))
        after.onMetaAnswered(meta(next.toJson()))
        after.upcoming.first() shouldBe next
    }

    private class MemoryDataStore : DataStore<Preferences> {
        private val prefs = MutableStateFlow(emptyPreferences())

        override val data: Flow<Preferences> = prefs

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(prefs.value).also { prefs.value = it }
    }

    companion object {
        private val T0: Instant = Instant.parse("2026-11-30T00:00:00Z")

        private fun notice(id: String, startsIn: Duration) = PlannedMaintenance(
            id = id,
            startsAt = T0.plus(startsIn),
            endsAt = T0.plus(startsIn).plus(Duration.ofHours(2)),
        )

        private fun PlannedMaintenance.toJson(): JsonElement = buildJsonObject {
            put("id", id)
            put("startsAt", startsAt.toString())
            put("endsAt", endsAt.toString())
        }

        private fun meta(maintenance: JsonElement?) = MetaResponse(
            recommendedAppVersion = 0,
            minSupportedAppVersion = 0,
            maintenance = maintenance,
        )
    }
}
