package eu.darken.apl.watch.core

import eu.darken.apl.watch.core.db.types.AircraftWatchEntity
import eu.darken.apl.watch.core.db.types.BaseWatchEntity
import eu.darken.apl.watch.core.types.AircraftWatch
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class AircraftQuickWatchTest : BaseTest() {

    private val watchRepo = mockk<WatchRepo>(relaxed = true)
    private var now = 10_000L
    private val quickWatch = AircraftQuickWatch(watchRepo, clock = { now })

    private val watch = AircraftWatch(
        base = BaseWatchEntity(id = "id-AAAAAA", watchType = AircraftWatchEntity.TYPE_KEY),
        specific = AircraftWatchEntity(id = "id-AAAAAA", hexCode = "AAAAAA"),
    )

    @Test
    fun `an unwatched aircraft is added`() = runTest {
        coEvery { watchRepo.addAircraft("AAAAAA") } returns WatchRepo.AircraftAdd.Created(watch)

        quickWatch.toggle("AAAAAA") shouldBe AircraftQuickWatch.Result.Added(watch)
    }

    @Test
    fun `a watched aircraft opens its watch`() = runTest {
        coEvery { watchRepo.addAircraft("AAAAAA") } returns WatchRepo.AircraftAdd.Existing(watch)

        quickWatch.toggle("AAAAAA") shouldBe AircraftQuickWatch.Result.Open("id-AAAAAA")
    }

    @Test
    fun `the second tap of a double tap does not open the watch the first one added`() = runTest {
        coEvery { watchRepo.addAircraft(any()) } returnsMany listOf(
            WatchRepo.AircraftAdd.Created(watch),
            WatchRepo.AircraftAdd.Existing(watch),
        )
        quickWatch.toggle("AAAAAA")

        now += 999
        quickWatch.toggle("aaaaaa") shouldBe AircraftQuickWatch.Result.Repeated
    }

    @Test
    fun `a tap after the double tap interval opens the watch`() = runTest {
        coEvery { watchRepo.addAircraft(any()) } returnsMany listOf(
            WatchRepo.AircraftAdd.Created(watch),
            WatchRepo.AircraftAdd.Existing(watch),
        )
        quickWatch.toggle("AAAAAA")

        now += 1_000
        quickWatch.toggle("AAAAAA") shouldBe AircraftQuickWatch.Result.Open("id-AAAAAA")
    }

    @Test
    fun `undo deletes the added watch and a tap right after adds again`() = runTest {
        coEvery { watchRepo.addAircraft("AAAAAA") } returns WatchRepo.AircraftAdd.Created(watch)
        quickWatch.toggle("AAAAAA")

        quickWatch.undo("id-AAAAAA")

        coVerify(exactly = 1) { watchRepo.delete("id-AAAAAA") }
        quickWatch.toggle("AAAAAA") shouldBe AircraftQuickWatch.Result.Added(watch)
        coVerify(exactly = 2) { watchRepo.addAircraft("AAAAAA") }
    }

    @Test
    fun `undo forgets the add, a watch found right after it opens`() = runTest {
        coEvery { watchRepo.addAircraft(any()) } returnsMany listOf(
            WatchRepo.AircraftAdd.Created(watch),
            WatchRepo.AircraftAdd.Existing(watch),
        )
        quickWatch.toggle("AAAAAA")
        quickWatch.undo("id-AAAAAA")

        quickWatch.toggle("AAAAAA") shouldBe AircraftQuickWatch.Result.Open("id-AAAAAA")
    }

    @Test
    fun `the watch limit is reported`() = runTest {
        val error = WatchLimitReachedException(max = 3)
        coEvery { watchRepo.addAircraft("AAAAAA") } throws error

        quickWatch.toggle("AAAAAA") shouldBe AircraftQuickWatch.Result.LimitReached(error)
    }

    @Test
    fun `a watched aircraft opens its watch also at the limit`() = runTest {
        // counterpart: WatchRepoAddAircraftTest, `an already watched aircraft is found at the limit`
        coEvery { watchRepo.addAircraft("AAAAAA") } returns WatchRepo.AircraftAdd.Existing(watch)

        quickWatch.toggle("AAAAAA") shouldBe AircraftQuickWatch.Result.Open("id-AAAAAA")
    }
}
