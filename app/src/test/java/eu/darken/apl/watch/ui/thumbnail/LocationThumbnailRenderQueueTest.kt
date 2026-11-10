package eu.darken.apl.watch.ui.thumbnail

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldNotBeInstanceOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import kotlin.time.Duration.Companion.seconds

class LocationThumbnailRenderQueueTest : BaseTest() {

    private val queue = LocationThumbnailRenderQueue(timeout = 30.seconds)

    @Test
    fun `only one render runs at a time`() = runTest {
        var active = 0
        var maxActive = 0
        val renders = (1..3).map { id ->
            async {
                queue.runExclusive {
                    active++
                    maxActive = maxOf(maxActive, active)
                    delay(10.seconds)
                    active--
                    id
                }
            }
        }

        renders.map { it.await() } shouldBe listOf(1, 2, 3)
        maxActive shouldBe 1
        currentTime shouldBe 30.seconds.inWholeMilliseconds
    }

    @Test
    fun `waiting in line does not shrink the budget`() = runTest {
        val first = async { runCatching { queue.runExclusive { delay(25.seconds); "first" } } }
        val second = async { runCatching { queue.runExclusive { delay(25.seconds); "second" } } }

        first.await().getOrThrow() shouldBe "first"
        second.await().getOrThrow() shouldBe "second"
        currentTime shouldBe 50.seconds.inWholeMilliseconds
    }

    @Test
    fun `a render over budget fails with a timeout and the next one runs`() = runTest {
        var secondStartedAt: Long? = null
        val first = async { runCatching { queue.runExclusive { delay(40.seconds); "first" } } }
        val second = async {
            runCatching {
                queue.runExclusive {
                    secondStartedAt = currentTime
                    delay(5.seconds)
                    "second"
                }
            }
        }

        val error = first.await().exceptionOrNull()
        error.shouldBeInstanceOf<LocationThumbnailTimeoutException>()
        error.shouldNotBeInstanceOf<CancellationException>()

        second.await().getOrThrow() shouldBe "second"
        secondStartedAt shouldBe 30.seconds.inWholeMilliseconds
    }

    @Test
    fun `a cancelled queued request never starts its render`() = runTest {
        var secondStarted = false
        val first = async { queue.runExclusive { delay(10.seconds); "first" } }
        val second = launch { queue.runExclusive { secondStarted = true; "second" } }
        runCurrent()

        advanceTimeBy(5.seconds)
        second.cancel()
        advanceUntilIdle()

        first.await() shouldBe "first"
        second.isCancelled shouldBe true
        secondStarted shouldBe false
    }

    @Test
    fun `cancelling a running render propagates cancellation`() = runTest {
        var caught: Throwable? = null
        var finished = false
        val render = launch {
            try {
                queue.runExclusive {
                    delay(20.seconds)
                    finished = true
                    "render"
                }
            } catch (e: Throwable) {
                caught = e
                throw e
            }
        }
        runCurrent()

        advanceTimeBy(5.seconds)
        render.cancel()
        advanceUntilIdle()

        render.isCancelled shouldBe true
        finished shouldBe false
        caught.shouldBeInstanceOf<CancellationException>()
        caught.shouldNotBeInstanceOf<LocationThumbnailTimeoutException>()

        queue.runExclusive { "next" } shouldBe "next"
    }
}
