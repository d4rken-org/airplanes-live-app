package eu.darken.apl.watch.ui.create

import android.location.Location
import androidx.lifecycle.SavedStateHandle
import eu.darken.apl.common.location.LocationManager2
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.watch.core.LocationAltitudeException
import eu.darken.apl.watch.core.LocationRadiusException
import eu.darken.apl.watch.core.LocationWatchCreator
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.ui.DestinationCreateLocationWatch
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.coroutine.TestDispatcherProvider

class CreateLocationWatchViewModelTest : BaseTest() {

    private val watchRepo = mockk<WatchRepo>(relaxed = true)
    private val access = mockk<AccessState>(relaxed = true) {
        every { maxLocationWatchRadiusKm } returns 25
        every { allowsWatchType("location") } returns true
    }
    private val accessRepo = mockk<AccessRepo> { every { state } returns MutableStateFlow(access) }
    private val locationManager2 = mockk<LocationManager2>(relaxed = true)

    // Spied so tests can stub the reverse geocoding, which builds an android.location.Location
    private val creator = spyk(LocationWatchCreator(mockk(relaxed = true), watchRepo, locationManager2, accessRepo))

    @BeforeEach
    fun setup() {
        // viewModelScope is built on the main dispatcher
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(handle: SavedStateHandle = SavedStateHandle()) = CreateLocationWatchViewModel(
        handle = handle,
        dispatcherProvider = TestDispatcherProvider(),
        locationManager2 = locationManager2,
        creator = creator,
    )

    /** A new handle holding only the saved draft, as after process death. */
    private fun SavedStateHandle.restored(): SavedStateHandle {
        val saved = get<String>(CreateLocationWatchViewModel.KEY_DRAFT).shouldNotBeNull()
        return SavedStateHandle(mapOf(CreateLocationWatchViewModel.KEY_DRAFT to saved))
    }

    private fun location(lat: Double, lon: Double) = mockk<Location> {
        every { latitude } returns lat
        every { longitude } returns lon
    }

    private fun CreateLocationWatchViewModel.seedHome() = init(52.5, 13.4, "Home", null)

    private fun CreateLocationWatchViewModel.seedAirport() = init(AIRPORT_LAT, AIRPORT_LON, AIRPORT_NAME, null)

    @Test
    fun `a radius above the limit is not created`() = runTest {
        val vm = createViewModel()
        vm.seedHome()

        vm.create(30_000f, null, null)

        coVerify(exactly = 0) { watchRepo.createLocation(any(), any(), any(), any(), any(), any(), any()) }
        vm.errorEvents.first().shouldBeInstanceOf<LocationRadiusException>()
    }

    @Test
    fun `a radius at the limit is created`() = runTest {
        val vm = createViewModel()
        vm.seedHome()

        vm.create(25_000f, null, null)

        coVerify { watchRepo.createLocation(52.5, 13.4, 25_000f, null, null, "Home", "") }
    }

    @Test
    fun `the chosen band is passed to the creator`() = runTest {
        val vm = createViewModel()
        vm.seedHome()

        vm.create(25_000f, 1_000, 10_000)

        coVerify { watchRepo.createLocation(52.5, 13.4, 25_000f, 1_000, 10_000, "Home", "") }
    }

    @Test
    fun `an invalid band is not created`() = runTest {
        val vm = createViewModel()
        vm.seedHome()

        vm.create(25_000f, 10_000, 1_000)

        coVerify(exactly = 0) { watchRepo.createLocation(any(), any(), any(), any(), any(), any(), any()) }
        vm.errorEvents.first().shouldBeInstanceOf<LocationAltitudeException>()
    }

    @Test
    fun `an airport prefill is used as given, without geocoding`() = runTest {
        val vm = createViewModel()

        vm.init(AIRPORT_LAT, AIRPORT_LON, AIRPORT_NAME, "Gate")

        coVerify(exactly = 0) { creator.labelFor(any(), any()) }
        coVerify(exactly = 0) { locationManager2.toName(any()) }
        coVerify(exactly = 0) { locationManager2.fromName(any()) }
        vm.isResolving.value shouldBe false
        vm.draft.apply {
            input shouldBe "$AIRPORT_LAT, $AIRPORT_LON"
            latitude shouldBe AIRPORT_LAT
            longitude shouldBe AIRPORT_LON
            label shouldBe AIRPORT_NAME
            note shouldBe "Gate"
        }

        vm.create(25_000f, null, null)

        coVerify { watchRepo.createLocation(AIRPORT_LAT, AIRPORT_LON, 25_000f, null, null, AIRPORT_NAME, "Gate") }
    }

    @Test
    fun `coordinates without a label get the reverse geocoded label`() = runTest {
        coEvery { creator.labelFor(any(), any()) } returns "Frankfurt, DE"
        val vm = createViewModel()

        vm.init(AIRPORT_LAT, AIRPORT_LON, null, null)

        coVerify { creator.labelFor(AIRPORT_LAT, AIRPORT_LON) }
        vm.isResolving.value shouldBe false
        vm.draft.apply {
            input shouldBe "$AIRPORT_LAT, $AIRPORT_LON"
            latitude shouldBe AIRPORT_LAT
            longitude shouldBe AIRPORT_LON
            label shouldBe "Frankfurt, DE"
        }
    }

    @Test
    fun `editing the location text clears the resolved location and blocks create`() = runTest {
        val vm = createViewModel()
        vm.seedAirport()

        vm.onLocationInputChange("Berlin")

        vm.draft.apply {
            input shouldBe "Berlin"
            latitude.shouldBeNull()
            longitude.shouldBeNull()
            isResolved shouldBe false
        }

        vm.create(25_000f, null, null)

        coVerify(exactly = 0) { watchRepo.createLocation(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a late answer for text that was edited since is dropped`() = runTest {
        val answer = CompletableDeferred<Location?>()
        // The geocoder call blocks, it can't be cancelled
        coEvery { locationManager2.fromName("Munich") } coAnswers { withContext(NonCancellable) { answer.await() } }
        val vm = createViewModel()
        vm.init(null, null, null, null)
        vm.onLocationInputChange("Munich")
        vm.resolveInput()
        vm.isResolving.value shouldBe true

        vm.onLocationInputChange("Hamburg")
        vm.isResolving.value shouldBe false
        val edited = vm.draft

        answer.complete(location(48.14, 11.58))

        vm.draft shouldBe edited
        vm.draft.apply {
            input shouldBe "Hamburg"
            latitude.shouldBeNull()
            label shouldBe ""
        }
        vm.isResolving.value shouldBe false
    }

    @Test
    fun `create is refused while a resolution runs`() = runTest {
        coEvery { creator.labelFor(any(), any()) } coAnswers { awaitCancellation() }
        val vm = createViewModel()
        vm.seedAirport()

        vm.resolveInput()
        vm.isResolving.value shouldBe true

        vm.create(25_000f, null, null)

        coVerify(exactly = 0) { watchRepo.createLocation(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `an obsolete request finishing after a newer one changes nothing`() = runTest {
        val first = CompletableDeferred<Location?>()
        val second = CompletableDeferred<Location?>()
        val answers = ArrayDeque(listOf(first, second))
        coEvery { locationManager2.fromName("Munich") } coAnswers {
            val answer = answers.removeFirst()
            withContext(NonCancellable) { answer.await() }
        }
        val vm = createViewModel()
        vm.init(null, null, null, null)
        vm.onLocationInputChange("Munich")

        vm.resolveInput()
        vm.resolveInput()

        first.complete(location(1.0, 2.0))

        vm.isResolving.value shouldBe true
        vm.draft.apply {
            latitude.shouldBeNull()
            longitude.shouldBeNull()
            label shouldBe ""
        }

        second.complete(location(48.14, 11.58))

        vm.isResolving.value shouldBe false
        vm.draft.apply {
            latitude shouldBe 48.14
            longitude shouldBe 11.58
            label shouldBe "Munich"
        }
    }

    @Test
    fun `a label edited during a resolution survives it`() = runTest {
        val answer = CompletableDeferred<Location?>()
        coEvery { locationManager2.fromName("Munich") } coAnswers { answer.await() }
        val vm = createViewModel()
        vm.init(null, null, null, null)
        vm.onLocationInputChange("Munich")
        vm.resolveInput()

        vm.onLabelChange("Hangar")
        answer.complete(location(48.14, 11.58))

        vm.draft.apply {
            latitude shouldBe 48.14
            longitude shouldBe 11.58
            label shouldBe "Hangar"
        }
    }

    @Test
    fun `a restored draft wins over the original arguments`() = runTest {
        coEvery { locationManager2.fromName("Munich") } returns location(48.14, 11.58)
        val handle = SavedStateHandle()
        val first = createViewModel(handle)
        first.seedAirport()
        first.onLocationInputChange("Munich")
        first.resolveInput()
        first.onLabelChange("Hangar")
        first.onRadiusChange(10_000f)

        val restored = createViewModel(handle.restored())
        restored.seedAirport()

        restored.draft shouldBe first.draft
        restored.draft.apply {
            input shouldBe "Munich"
            latitude shouldBe 48.14
            longitude shouldBe 11.58
            label shouldBe "Hangar"
            pickedRadius shouldBe 10_000f
        }

        restored.create(10_000f, null, null)

        coVerify { watchRepo.createLocation(48.14, 11.58, 10_000f, null, null, "Hangar", "") }
    }

    @Test
    fun `a restored coordinate seed whose label was still pending looks it up again`() = runTest {
        coEvery { creator.labelFor(any(), any()) } coAnswers { awaitCancellation() }
        val handle = SavedStateHandle()
        val first = createViewModel(handle)
        first.init(AIRPORT_LAT, AIRPORT_LON, null, null)
        first.draft.label shouldBe ""

        coEvery { creator.labelFor(any(), any()) } returns "Frankfurt, DE"
        val restored = createViewModel(handle.restored())
        restored.init(AIRPORT_LAT, AIRPORT_LON, null, null)

        coVerify(exactly = 2) { creator.labelFor(AIRPORT_LAT, AIRPORT_LON) }
        restored.isResolving.value shouldBe false
        restored.draft.apply {
            latitude shouldBe AIRPORT_LAT
            longitude shouldBe AIRPORT_LON
            label shouldBe "Frankfurt, DE"
        }
    }

    @Test
    fun `a restored replacement lookup that was interrupted shows the unresolved input`() = runTest {
        coEvery { locationManager2.fromName("Munich") } coAnswers { awaitCancellation() }
        val handle = SavedStateHandle()
        val first = createViewModel(handle)
        first.seedAirport()
        first.onLocationInputChange("Munich")
        first.resolveInput()
        first.isResolving.value shouldBe true

        val restored = createViewModel(handle.restored())
        restored.seedAirport()

        restored.isResolving.value shouldBe false
        restored.draft.apply {
            input shouldBe "Munich"
            latitude.shouldBeNull()
            longitude.shouldBeNull()
            label shouldBe AIRPORT_NAME
        }
        coVerify(exactly = 1) { locationManager2.fromName(any()) }
    }

    @Test
    fun `a repeated device location request interrupted by process death restores no location`() = runTest {
        val deviceLocation = MutableStateFlow<LocationManager2.State>(
            LocationManager2.State.Available(location(50.0, 8.5))
        )
        every { locationManager2.state } returns deviceLocation
        coEvery { creator.labelFor(any(), any()) } returns "Frankfurt, DE"
        val handle = SavedStateHandle()
        val first = createViewModel(handle)
        first.init(null, null, null, null)

        first.useDeviceLocation()
        first.isResolving.value shouldBe false
        first.draft.apply {
            input shouldBe ""
            latitude shouldBe 50.0
            longitude shouldBe 8.5
        }

        // The second fix never arrives before the process dies
        deviceLocation.value = LocationManager2.State.Waiting
        first.useDeviceLocation()
        first.isResolving.value shouldBe true

        val restored = createViewModel(handle.restored())
        restored.init(null, null, null, null)

        restored.isResolving.value shouldBe false
        restored.draft.apply {
            input shouldBe ""
            latitude.shouldBeNull()
            longitude.shouldBeNull()
        }

        restored.create(25_000f, null, null)

        coVerify(exactly = 0) { watchRepo.createLocation(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a saved destination without a label still restores`() {
        val restored = Json.decodeFromString(
            DestinationCreateLocationWatch.serializer(),
            """{"latitude":50.03,"longitude":8.57,"note":"Gate"}""",
        )

        restored shouldBe DestinationCreateLocationWatch(latitude = 50.03, longitude = 8.57, note = "Gate")
        restored.label.shouldBeNull()
    }

    companion object {
        private const val AIRPORT_LAT = 50.033333
        private const val AIRPORT_LON = 8.570556
        private const val AIRPORT_NAME = "Frankfurt Airport"
    }
}
