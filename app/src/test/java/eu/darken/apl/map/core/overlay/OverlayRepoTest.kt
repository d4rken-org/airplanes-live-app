package eu.darken.apl.map.core.overlay

import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.feeder.core.link.FeederLinkRepo.FeederLinkState
import eu.darken.apl.server.ServerModule
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.api.AccessResponse
import eu.darken.apl.server.api.LinkedFeeder
import eu.darken.apl.server.api.OverlayCatalogResponse
import eu.darken.apl.server.api.OverlayDto
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerCodes
import eu.darken.apl.server.compat.CompatState
import eu.darken.apl.server.session.SessionState
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.coroutine.TestDispatcherProvider
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class OverlayRepoTest : BaseTest() {

    private val baseAccess = AccessState.from(
        response = ServerModule.serverJson().decodeFromString(AccessResponse.serializer(), FEEDER_ACCESS),
        installationId = INSTALLATION_ID,
        fetchedAt = Instant.ofEpochMilli(1_710_000_000_000L),
    )

    private val access = MutableStateFlow<AccessState?>(baseAccess)
    private val feederLink = MutableStateFlow<FeederLinkState>(FeederLinkState.Linked(linkedFeeder(), "feeder"))
    private val compat = MutableStateFlow<CompatState>(CompatState.Ok)
    private val session = MutableStateFlow<SessionState>(SessionState.Active(INSTALLATION_ID))
    private val enabled = MutableStateFlow<Set<String>>(emptySet())
    private val signal = OverlayRefreshSignal()

    private var reads = 0
    private var answer: () -> OverlayCatalogResponse = { CATALOG }
    private var gate: CompletableDeferred<Unit>? = null

    private fun TestScope.createRepo() = OverlayRepo(
        appScope = backgroundScope,
        dispatcherProvider = TestDispatcherProvider(StandardTestDispatcher(testScheduler)),
        clock = object : MonotonicClock {
            override fun elapsed(): Long = testScheduler.currentTime
        },
        accessState = access,
        feederLinkState = feederLink,
        compatState = compat,
        sessionState = session,
        enabledOverlays = enabled,
        refreshRequests = signal.requests,
        fetchCatalog = {
            reads++
            gate?.await()
            answer()
        },
    )

    private fun TestScope.collect(repo: OverlayRepo) {
        backgroundScope.launch { repo.state.collect {} }
        runCurrent()
    }

    private fun TestScope.advance(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun `nothing is read without a collector, the start of collection reads once`() = runTest {
        val repo = createRepo()
        advance(HOUR)
        reads shouldBe 0

        collect(repo)

        reads shouldBe 1
        repo.state.value.catalog shouldBe CATALOG
        repo.state.value.offered shouldBe true
        repo.state.value.isBlocked shouldBe false
    }

    @Test
    fun `nothing is read while the server doesn't offer overlays`() = runTest {
        access.value = baseAccess.copy(aircraftOperations = listOf("search", "map"))
        val repo = createRepo()

        collect(repo)
        advance(HOUR)

        reads shouldBe 0
        repo.state.value.offered shouldBe false
        repo.state.value.catalog.shouldBeNull()
    }

    @Test
    fun `the shortest refresh of the overlays switched on decides the cadence`() = runTest {
        enabled.value = setOf(OverlayDto.ID_RADAR, OverlayDto.ID_MILITARY)
        collect(createRepo())
        reads shouldBe 1

        advance(299_999)
        reads shouldBe 1
        advance(1)
        reads shouldBe 2
        advance(300_000)
        reads shouldBe 3
    }

    @Test
    fun `military alone refreshes hourly`() = runTest {
        enabled.value = setOf(OverlayDto.ID_MILITARY)
        collect(createRepo())

        advance(300_000)
        reads shouldBe 1
        advance(3_300_000)
        reads shouldBe 2
    }

    @Test
    fun `an overlay the catalog doesn't list sets no cadence`() = runTest {
        enabled.value = setOf("lightning")
        collect(createRepo())

        advance(2 * HOUR)
        reads shouldBe 1
    }

    @Test
    fun `nothing switched on means no timer reads`() = runTest {
        collect(createRepo())

        advance(2 * HOUR)
        reads shouldBe 1
    }

    @Test
    fun `a usage update is no reason to read`() = runTest {
        collect(createRepo())

        access.value = baseAccess.copy(
            fetchedAt = Instant.ofEpochMilli(1_710_000_060_000L),
            usage = baseAccess.usage.copy(resetsAt = baseAccess.usage.resetsAt + 1),
        )
        advance(1_000)

        reads shouldBe 1
    }

    @Test
    fun `a tier change reads at once`() = runTest {
        collect(createRepo())
        advance(1_000)

        access.value = baseAccess.copy(tier = AccessState.Tier.FREE)
        runCurrent()

        reads shouldBe 2
    }

    @Test
    fun `a restriction blocks without reading, lifting it reads`() = runTest {
        val repo = createRepo()
        collect(repo)

        access.value = baseAccess.copy(restricted = true)
        advance(1_000)
        reads shouldBe 1
        repo.state.value.block shouldBe OverlayState.Block.RESTRICTED
        repo.state.value.catalog shouldBe CATALOG

        access.value = baseAccess
        runCurrent()
        reads shouldBe 2
        repo.state.value.isBlocked shouldBe false
    }

    @Test
    fun `a feeder link change reads, a status check of the same link doesn't`() = runTest {
        collect(createRepo())
        advance(1_000)

        feederLink.value = FeederLinkState.Linked(linkedFeeder(checkedAt = 1_710_000_100_000L), "feeder")
        runCurrent()
        reads shouldBe 1

        feederLink.value = FeederLinkState.Unlinked("free")
        runCurrent()
        reads shouldBe 2
    }

    @Test
    fun `a burst of refresh requests is one read`() = runTest {
        collect(createRepo())
        advance(100_000)

        repeat(5) { signal.request("frame") }
        runCurrent()
        reads shouldBe 2

        advance(HOUR)
        reads shouldBe 2
    }

    @Test
    fun `a refresh request inside the spacing is served once at its end`() = runTest {
        collect(createRepo())

        advance(10_000)
        signal.request("frame")
        runCurrent()
        advance(5_000)
        signal.request("frame")
        runCurrent()
        reads shouldBe 1

        advance(14_999)
        reads shouldBe 1
        advance(1)
        reads shouldBe 2

        advance(HOUR)
        reads shouldBe 2
    }

    @Test
    fun `a refused read waits for the retry delay and keeps the last catalog`() = runTest {
        enabled.value = setOf(OverlayDto.ID_RADAR)
        val repo = createRepo()
        collect(repo)

        answer = { throw ServerApiException(ServerCodes.QUOTA_EXCEEDED, status = 429, retryAfterSeconds = 120) }
        advance(300_000)
        reads shouldBe 2
        repo.state.value.catalog shouldBe CATALOG

        advance(50_000)
        signal.request("frame")
        runCurrent()
        reads shouldBe 2

        answer = { CATALOG_NEXT }
        advance(69_999)
        reads shouldBe 2
        advance(1)
        reads shouldBe 3
        repo.state.value.catalog shouldBe CATALOG_NEXT

        advance(299_999)
        reads shouldBe 3
    }

    @Test
    fun `a restricted catalog read blocks until a later read succeeds`() = runTest {
        enabled.value = setOf(OverlayDto.ID_RADAR)
        answer = { throw ServerApiException(ServerCodes.INSTALLATION_RESTRICTED, status = 403) }
        val repo = createRepo()
        collect(repo)
        repo.state.value.block shouldBe OverlayState.Block.RESTRICTED

        signal.request("frame")
        advance(HOUR)
        reads shouldBe 1

        answer = { CATALOG }
        access.value = baseAccess.copy(tier = AccessState.Tier.FREE)
        runCurrent()
        reads shouldBe 2
        repo.state.value.isBlocked shouldBe false
        repo.state.value.catalog shouldBe CATALOG
    }

    @Test
    fun `an app update requirement blocks and stops reading`() = runTest {
        enabled.value = setOf(OverlayDto.ID_RADAR)
        val repo = createRepo()
        collect(repo)

        compat.value = CompatState.UpdateRequired
        advance(HOUR)

        reads shouldBe 1
        repo.state.value.block shouldBe OverlayState.Block.UPDATE_REQUIRED
    }

    @Test
    fun `no catalog read after the last collector leaves`() = runTest {
        enabled.value = setOf(OverlayDto.ID_RADAR)
        val repo = createRepo()
        val collector = backgroundScope.launch { repo.state.collect {} }
        runCurrent()
        reads shouldBe 1

        advance(299_000)
        reads shouldBe 1

        collector.cancel()
        runCurrent()
        advance(2_000)

        reads shouldBe 1
    }

    companion object {
        private const val HOUR = 3_600_000L
        private const val INSTALLATION_ID = "5a5c6b2e-3b0a-4a2e-9a0f-000000000002"

        private fun linkedFeeder(checkedAt: Long = 1_710_000_000_000L) = LinkedFeeder(
            feederId = "feeder-1",
            status = "active",
            networkVerified = true,
            linkedAt = 1_709_000_000_000L,
            checkedAt = checkedAt,
            lastActiveAt = 1_710_000_000_000L,
            validUntil = 1_711_000_000_000L,
            nextCheckAt = 1_710_003_600_000L,
            eligible = true,
        )

        private fun overlay(id: String, refreshSeconds: Int) = OverlayDto(
            id = id,
            kind = OverlayDto.KIND_VECTOR,
            category = OverlayDto.CATEGORY_AIRSPACE,
            coverage = listOf("United States"),
            title = id,
            forecast = false,
            attribution = emptyList(),
            status = OverlayDto.STATUS_AVAILABLE,
            access = OverlayDto.ACCESS_ALLOWED,
            minZoom = 0,
            maxZoom = 12,
            tileSize = 512,
            refreshSeconds = refreshSeconds,
        )

        private val CATALOG = OverlayCatalogResponse(
            generatedAt = 1_794_225_640_000L,
            overlays = listOf(
                overlay(OverlayDto.ID_RADAR, 300),
                overlay(OverlayDto.ID_RESTRICTIONS, 300),
                overlay(OverlayDto.ID_MILITARY, 3600),
            ),
        )

        private val CATALOG_NEXT = CATALOG.copy(generatedAt = 1_794_225_940_000L)

        private val FEEDER_ACCESS = """
            {
              "tier": "feeder",
              "restricted": false,
              "allowanceScope": "principal",
              "limits": {
                "viewingIntervalSeconds": 2,
                "viewingBurst": 3,
                "viewingPerDay": 100000,
                "searchesPerDay": 1000,
                "watchesPerDay": 10000,
                "maxRadiusNm": 250,
                "watchTypes": ["hex", "callsign"]
              },
              "usage": {
                "resetsAt": 1710028800000,
                "viewing": {"limit": 100000, "used": 0, "reserved": 0, "remaining": 100000},
                "search": {"limit": 1000, "used": 0, "reserved": 0, "remaining": 1000},
                "watch": {"limit": 10000, "used": 0, "reserved": 0, "remaining": 10000}
              },
              "installationRequests": {
                "viewing": {"perSecond": 0.5, "burst": 3},
                "search": {"perSecond": 1.0, "burst": 3},
                "watch": {"perSecond": 1.0, "burst": 3},
                "concurrency": 3
              },
              "aircraftOperations": ["search", "ar", "map", "watches", "overlays"]
            }
        """.trimIndent()
    }
}
