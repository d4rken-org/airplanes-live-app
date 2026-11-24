package eu.darken.apl.common.location

import android.location.Location
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationRequestCompat
import eu.darken.apl.common.location.LocationManager2.State
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.util.concurrent.Executor

class LocationUpdatesTest : BaseTest() {

    private val executor = Executor { it.run() }

    private class FakeGateway(
        override val sdkInt: Int = 36,
        var coarse: Boolean = true,
        var fine: Boolean = false,
        var providers: Set<String> = setOf("network", "fused", "gps"),
    ) : LocationProviderGateway {
        val lastKnownFixes = mutableMapOf<String, Location?>()
        val lastKnownErrors = mutableMapOf<String, Exception>()
        val registerErrors = mutableMapOf<String, Exception>()
        val removeErrors = mutableMapOf<String, Exception>()

        val listeners = mutableMapOf<String, LocationListenerCompat>()
        val removed = mutableListOf<String>()
        val oneShotRequests = mutableListOf<String>()
        val oneShotConsumers = mutableMapOf<String, (Location?) -> Unit>()
        val cancelledOneShots = mutableListOf<String>()

        override fun hasCoarsePermission(): Boolean = coarse

        override fun hasFinePermission(): Boolean = fine

        override fun existingProviders(): Set<String> = providers

        override fun lastKnown(provider: String): Location? {
            lastKnownErrors[provider]?.let { throw it }
            return lastKnownFixes[provider]
        }

        override fun requestUpdates(
            provider: String,
            request: LocationRequestCompat,
            executor: Executor,
            listener: LocationListenerCompat,
        ) {
            registerErrors[provider]?.let { throw it }
            listeners[provider] = listener
        }

        override fun removeUpdates(listener: LocationListenerCompat) {
            val provider = listeners.entries.single { it.value === listener }.key
            removed.add(provider)
            removeErrors[provider]?.let { throw it }
        }

        override fun currentLocation(
            provider: String,
            executor: Executor,
            consumer: (Location?) -> Unit,
        ): DisposableHandle {
            oneShotRequests.add(provider)
            oneShotConsumers[provider] = consumer
            return DisposableHandle { cancelledOneShots.add(provider) }
        }

        fun deliver(provider: String, location: Location) = listeners.getValue(provider).onLocationChanged(location)
    }

    private fun fix(provider: String, nanos: Long): Location = mockk {
        every { this@mockk.provider } returns provider
        every { latitude } returns 52.0
        every { longitude } returns 13.0
        every { elapsedRealtimeNanos } returns nanos
    }

    private fun TestScope.collect(gateway: FakeGateway): Pair<MutableList<State>, Job> {
        val states = mutableListOf<State>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            locationStates(gateway, executor).toList(states)
        }
        runCurrent()
        return states to job
    }

    @Test
    fun `newest last known fix seeds the state`() = runTest {
        val older = fix("network", 100)
        val newer = fix("gps", 200)
        val gateway = FakeGateway().apply {
            lastKnownFixes["network"] = older
            lastKnownFixes["fused"] = null
            lastKnownFixes["gps"] = newer
        }

        val (states, _) = collect(gateway)

        states shouldBe listOf(State.Waiting, State.Available(newer, isLastKnown = true))
    }

    @Test
    fun `a failing provider is skipped while another delivers`() = runTest {
        val seed = fix("gps", 100)
        val update = fix("network", 200)
        val gateway = FakeGateway().apply {
            lastKnownErrors["network"] = SecurityException("no network")
            lastKnownFixes["gps"] = seed
            registerErrors["fused"] = IllegalArgumentException("no fused")
        }

        val (states, _) = collect(gateway)
        gateway.listeners.keys shouldBe setOf("network")
        gateway.deliver("network", update)
        runCurrent()

        states shouldBe listOf(
            State.Waiting,
            State.Available(seed, isLastKnown = true),
            State.Available(update),
        )
    }

    @Test
    fun `null one-shot result is ignored`() = runTest {
        val gateway = FakeGateway()

        val (states, _) = collect(gateway)
        gateway.oneShotRequests shouldBe listOf("gps")
        gateway.oneShotConsumers.getValue("gps").invoke(null)
        runCurrent()

        states shouldBe listOf(State.Waiting)

        val gpsFix = fix("gps", 100)
        gateway.oneShotConsumers.getValue("gps").invoke(gpsFix)
        runCurrent()

        states shouldBe listOf(State.Waiting, State.Available(gpsFix))
    }

    @Test
    fun `older fix after a newer one is dropped`() = runTest {
        val seed = fix("network", 300)
        val gateway = FakeGateway().apply { lastKnownFixes["network"] = seed }

        val (states, _) = collect(gateway)
        gateway.deliver("fused", fix("fused", 200))
        val newer = fix("fused", 400)
        gateway.deliver("fused", newer)
        gateway.deliver("network", fix("network", 350))
        runCurrent()

        states shouldBe listOf(
            State.Waiting,
            State.Available(seed, isLastKnown = true),
            State.Available(newer),
        )
    }

    @Test
    fun `cancelling after partial registration releases everything registered`() = runTest {
        val gateway = FakeGateway().apply {
            registerErrors["fused"] = SecurityException("no fused")
        }

        val (_, job) = collect(gateway)
        gateway.listeners.keys shouldBe setOf("network")
        gateway.oneShotRequests shouldBe listOf("gps")

        job.cancel()
        runCurrent()

        gateway.removed shouldBe listOf("network")
        gateway.cancelledOneShots shouldBe listOf("gps")
    }

    @Test
    fun `a failing removal does not skip the remaining cleanup`() = runTest {
        val gateway = FakeGateway().apply {
            removeErrors["network"] = IllegalArgumentException("gone")
        }

        val (_, job) = collect(gateway)
        job.cancel()
        runCurrent()

        gateway.removed shouldBe listOf("network", "fused")
        gateway.cancelledOneShots shouldBe listOf("gps")
    }

    @Test
    fun `a second collection starts fresh`() = runTest {
        val seed = fix("network", 100)
        val gateway = FakeGateway().apply { lastKnownFixes["network"] = seed }

        val (_, first) = collect(gateway)
        gateway.deliver("network", fix("network", 500))
        first.cancel()
        runCurrent()

        val (states, _) = collect(gateway)
        val update = fix("fused", 200)
        gateway.deliver("fused", update)
        runCurrent()

        states shouldBe listOf(
            State.Waiting,
            State.Available(seed, isLastKnown = true),
            State.Available(update),
        )
        gateway.oneShotRequests shouldBe listOf("gps", "gps")
    }

    @Test
    fun `missing coarse permission is a permission issue`() = runTest {
        val gateway = FakeGateway(coarse = false)

        val (states, _) = collect(gateway)

        states.size shouldBe 1
        states.single().shouldBeInstanceOf<State.Unavailable>().isPermissionIssue shouldBe true
        gateway.listeners shouldBe emptyMap()
        gateway.oneShotRequests shouldBe emptyList()
    }
}
