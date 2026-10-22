package eu.darken.apl.watch.core

import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class LocationWatchCreatorTest : BaseTest() {

    private val watchRepo = mockk<WatchRepo>(relaxed = true)
    private val accessState = MutableStateFlow<AccessState?>(null)
    private val accessRepo = mockk<AccessRepo> { every { state } returns accessState }

    private fun access(watchTypes: Set<String>, maxRadiusKm: Int = 25) = mockk<AccessState> {
        every { allowsWatchType(any()) } answers { firstArg<String>() in watchTypes }
        every { maxLocationWatchRadiusKm } returns maxRadiusKm
    }

    private fun creator() = LocationWatchCreator(
        context = mockk(relaxed = true),
        watchRepo = watchRepo,
        locationManager2 = mockk(relaxed = true),
        accessRepo = accessRepo,
    )

    @Test
    fun `a tier without location watches can't save one`() = runTest {
        accessState.value = access(setOf("hex", "callsign"))
        val creator = creator()

        creator.isAllowed.first() shouldBe false
        shouldThrow<LocationWatchNotAllowedException> { creator.create(52.5, 13.4, 10_000f, "Home", "") }
        coVerify(exactly = 0) { watchRepo.createLocation(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `an access policy not loaded yet denies nothing`() = runTest {
        val creator = creator()

        creator.isAllowed.first() shouldBe true
        creator.create(52.5, 13.4, 10_000f, "Home", "")

        coVerify { watchRepo.createLocation(52.5, 13.4, 10_000f, "Home", "") }
    }

    @Test
    fun `a radius above the limit is not created`() = runTest {
        accessState.value = access(setOf("location"))

        shouldThrow<LocationRadiusException> { creator().create(52.5, 13.4, 30_000f, "Home", "") }
        coVerify(exactly = 0) { watchRepo.createLocation(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a radius at the limit is created, label and note trimmed`() = runTest {
        accessState.value = access(setOf("location"))

        creator().create(52.5, 13.4, 25_000f, " Home ", " roof ")

        coVerify { watchRepo.createLocation(52.5, 13.4, 25_000f, "Home", "roof") }
    }

    @Test
    fun `the watch limit is passed on`() = runTest {
        accessState.value = access(setOf("location"))
        coEvery { watchRepo.createLocation(any(), any(), any(), any(), any()) } throws WatchLimitReachedException(10)

        shouldThrow<WatchLimitReachedException> { creator().create(52.5, 13.4, 10_000f, "Home", "") }
    }
}
