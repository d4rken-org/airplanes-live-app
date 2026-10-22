package eu.darken.apl.map.ui

import android.content.Context
import android.os.SystemClock
import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.datastore.DataStoreValue
import eu.darken.apl.main.core.AircraftRepo
import eu.darken.apl.map.core.MapAircraftProvider
import eu.darken.apl.map.core.MapOptions
import eu.darken.apl.map.core.MapSettings
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.compat.AppCompatibility
import eu.darken.apl.server.compat.CompatState
import eu.darken.apl.watch.core.WatchRepo
import eu.darken.apl.watch.core.types.AircraftWatch
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.coroutine.TestDispatcherProvider

class NativeMapViewModelRequestTest : BaseTest() {

    private val mapRequests = MapRequests()
    private val provider = mockk<MapAircraftProvider>(relaxed = true)
    private val watchRepo = mockk<WatchRepo>(relaxed = true)
    private val aircraftRepo = mockk<AircraftRepo>(relaxed = true) {
        every { cache } returns MutableStateFlow(emptyMap())
    }

    private fun <T> setting(value: T) = mockk<DataStoreValue<T>> {
        every { flow } returns MutableStateFlow(value)
    }

    private val mapSettings = mockk<MapSettings>(relaxed = true) {
        every { isRestoreLastViewEnabled } returns setting(false)
        every { isMilitaryOnlyEnabled } returns setting(false)
    }

    private val context = mockk<Context>(relaxed = true) {
        every { resources.getStringArray(any()) } returns arrayOf("tagline")
    }

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 0L
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
        unmockkStatic(SystemClock::class)
    }

    private fun createViewModel() = NativeMapViewModel(
        dispatcherProvider = TestDispatcherProvider(),
        context = context,
        sensorManager = mockk(relaxed = true),
        mapSettings = mapSettings,
        webpageTool = mockk(relaxed = true),
        watchRepo = watchRepo,
        aircraftRepo = aircraftRepo,
        flightRepo = mockk(relaxed = true),
        locationManager2 = mockk(relaxed = true),
        provider = provider,
        accessRepo = mockk(relaxed = true),
        serverClock = ServerClock(object : MonotonicClock {
            override fun elapsed(): Long = 0L
        }),
        mapRequests = mapRequests,
        locationWatchCreator = mockk(relaxed = true),
        shapesRepo = mockk(relaxed = true),
        upgradeRepo = mockk(relaxed = true),
        compatibility = mockk<AppCompatibility> {
            every { state } returns MutableStateFlow(CompatState.Ok)
        },
    )

    @Test
    fun `a plain start shows everything from the default view`() = runTest {
        val viewModel = createViewModel().apply { init() }

        viewModel.initialCamera.value shouldBe MapOptions.Camera(lat = 30.0, lon = 0.0, zoom = 3.0)
        verify(exactly = 0) { provider.pin(any()) }
        verify(exactly = 0) { provider.select(any()) }
    }

    @Test
    fun `a request replaces what an earlier one pinned`() = runTest {
        createViewModel().init()
        mapRequests.publish(MapOptions.focus(listOf("AAAAAA", "BBBBBB")))

        mapRequests.publish(MapOptions.focus("CCCCCC"))

        verifyOrder {
            provider.pin(listOf("AAAAAA", "BBBBBB"))
            provider.pin(emptyList())
            provider.select("CCCCCC")
        }
    }

    @Test
    fun `a watch added while the map is shown says so`() = runTest {
        coEvery { watchRepo.addAircraft(any()) } returns WatchRepo.AircraftAdd.Created(watch("AAAAAA"))
        val viewModel = createViewModel().apply { onScreenShown() }

        viewModel.onWatchAction("AAAAAA")

        viewModel.events.first().shouldBeInstanceOf<NativeMapEvents.WatchAdded>()
    }

    @Test
    fun `a watch added after leaving and coming back says nothing`() = runTest {
        val added = CompletableDeferred<WatchRepo.AircraftAdd>()
        coEvery { watchRepo.addAircraft(any()) } coAnswers { added.await() }
        val viewModel = createViewModel().apply { onScreenShown() }
        viewModel.onWatchAction("AAAAAA")

        viewModel.onScreenHidden()
        viewModel.onScreenShown()
        added.complete(WatchRepo.AircraftAdd.Created(watch("AAAAAA")))

        withTimeoutOrNull(1_000) { viewModel.events.first() } shouldBe null
    }

    private fun watch(hex: String) = mockk<AircraftWatch>(relaxed = true) {
        every { this@mockk.hex } returns hex
    }

    @Test
    fun `a map opened for a place starts there`() = runTest {
        val camera = MapOptions.Camera(lat = 50.0, lon = 8.0, zoom = 9.0)
        mapRequests.publish(MapOptions(camera = camera))

        val viewModel = createViewModel().apply { init() }

        viewModel.initialCamera.value shouldBe camera
    }

    @Test
    fun `a place asked for while the map is not shown is where its next view starts`() = runTest {
        val viewModel = createViewModel().apply { init() }
        val camera = MapOptions.Camera(lat = 50.0, lon = 8.0, zoom = 9.0)

        mapRequests.publish(MapOptions(camera = camera))

        viewModel.initialCamera.value shouldBe camera
    }
}
