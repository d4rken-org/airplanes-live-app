package eu.darken.apl.feeder.core.server

import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.chart.ChartPoint
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.api.FeederHistoryHour
import eu.darken.apl.server.api.FeederHistoryResponse
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerCodes
import eu.darken.apl.server.api.ServerEndpoint
import eu.darken.apl.server.session.SessionManager
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Duration
import java.time.Instant

class FeederHistoryRepoTest : BaseTest() {

    private var elapsed = 0L
    private val serverClock = ServerClock(object : MonotonicClock {
        override fun elapsed(): Long = elapsed
    }).apply { noteServerTime(NOW) }

    private var mode = FeederServerMode.State(access = null, isActive = true, server = FeederServerState(installationId = "inst"))
    private val serverMode = mockk<FeederServerMode>().apply {
        coEvery { current() } answers { mode }
    }
    private val sessionManager = mockk<SessionManager>()
    private val endpoint = mockk<ServerEndpoint>()

    private val requests = mutableListOf<Pair<String, Long?>>()
    private var answer: (since: Long?) -> List<FeederHistoryHour> = { emptyList() }

    private lateinit var repo: FeederHistoryRepo

    @BeforeEach
    fun setup() {
        coEvery { sessionManager.authed<FeederHistoryResponse>(any()) } coAnswers {
            firstArg<suspend (String) -> FeederHistoryResponse>().invoke("token")
        }
        coEvery { endpoint.feederHistory("token", any(), any()) } answers {
            val feederId = secondArg<String>()
            val since = thirdArg<Long?>()
            requests += feederId to since
            FeederHistoryResponse(serverTime = serverClock.now().toEpochMilli(), feederId = feederId, hours = answer(since))
        }
        repo = FeederHistoryRepo(sessionManager, endpoint, serverClock, serverMode)
    }

    private fun hour(at: Long, isFinal: Boolean = true, rate: Double? = 1.0) =
        FeederHistoryHour(hour = at, msgsPerSecondAvg = rate, isFinal = isFinal)

    private fun advance(duration: Duration) {
        elapsed += duration.toMillis()
    }

    @Test
    fun `the first fetch asks for 30 days`() = runBlocking<Unit> {
        repo.get(FEEDER)

        requests shouldBe listOf(FEEDER to NOW - Duration.ofDays(30).toMillis())
    }

    @Test
    fun `a second call within 10 minutes makes no request`() = runBlocking<Unit> {
        answer = { listOf(hour(NOW - HOUR)) }
        repo.get(FEEDER)
        advance(Duration.ofMinutes(9))

        repo.get(FEEDER) shouldBe listOf(hour(NOW - HOUR))

        requests.size shouldBe 1
    }

    @Test
    fun `after 10 minutes the earliest open hour and those after it are fetched again and replaced`() = runBlocking<Unit> {
        answer = {
            listOf(
                hour(NOW - 3 * HOUR),
                hour(NOW - 2 * HOUR),
                hour(NOW - HOUR, isFinal = false, rate = 1.0),
                hour(NOW, isFinal = false, rate = 2.0),
            )
        }
        repo.get(FEEDER)
        advance(Duration.ofMinutes(10))
        answer = { listOf(hour(NOW - HOUR, rate = 5.0), hour(NOW, isFinal = false, rate = 6.0)) }

        repo.get(FEEDER) shouldBe listOf(
            hour(NOW - 3 * HOUR),
            hour(NOW - 2 * HOUR),
            hour(NOW - HOUR, rate = 5.0),
            hour(NOW, isFinal = false, rate = 6.0),
        )

        requests[1] shouldBe (FEEDER to NOW - HOUR)
    }

    @Test
    fun `when every cached hour is final the next fetch starts after the last one`() = runBlocking<Unit> {
        answer = { listOf(hour(NOW - 2 * HOUR), hour(NOW - HOUR)) }
        repo.get(FEEDER)
        advance(Duration.ofMinutes(10))

        repo.get(FEEDER)

        requests[1] shouldBe (FEEDER to NOW)
    }

    @Test
    fun `a 429 holds all history requests back and returns the cache`() = runBlocking<Unit> {
        answer = { listOf(hour(NOW - HOUR)) }
        repo.get(FEEDER)
        advance(Duration.ofMinutes(10))
        answer = { throw ServerApiException(ServerCodes.QUOTA_EXCEEDED, 429, retryAfterSeconds = 120) }

        repo.get(FEEDER) shouldBe listOf(hour(NOW - HOUR))
        requests.size shouldBe 2

        advance(Duration.ofSeconds(60))
        repo.get(FEEDER) shouldBe listOf(hour(NOW - HOUR))
        repo.get(OTHER).shouldBeEmpty()
        requests.size shouldBe 2

        advance(Duration.ofSeconds(61))
        answer = { emptyList() }
        repo.get(FEEDER)
        requests.size shouldBe 3
    }

    @Test
    fun `a feeder the server does not monitor has no history`() = runBlocking<Unit> {
        answer = { throw ServerApiException(ServerCodes.FEEDER_NOT_MONITORED, 404) }

        repo.get(FEEDER).shouldBeEmpty()
    }

    @Test
    fun `other errors reach the caller`() = runBlocking<Unit> {
        answer = { throw ServerApiException(ServerCodes.INTERNAL, 500) }

        shouldThrow<ServerApiException> { repo.get(FEEDER) }.status shouldBe 500
    }

    @Test
    fun `hours older than 30 days are dropped`() = runBlocking<Unit> {
        val old = NOW - Duration.ofDays(31).toMillis()
        answer = { listOf(hour(old), hour(NOW - HOUR)) }

        repo.get(FEEDER) shouldBe listOf(hour(NOW - HOUR))
    }

    @Test
    fun `the sparkline covers the last 7 days of the cached window`() = runBlocking<Unit> {
        val day = Duration.ofDays(1).toMillis()
        answer = {
            listOf(
                hour(NOW - 8 * day, rate = 1.0),
                hour(NOW - 6 * day, rate = 2.0),
                hour(NOW - HOUR, rate = null),
                hour(NOW, isFinal = false, rate = 3.0),
            )
        }

        repo.messageRateSparkline(FEEDER) shouldBe listOf(
            ChartPoint(Instant.ofEpochMilli(NOW - 6 * day), 2.0),
            ChartPoint(Instant.ofEpochMilli(NOW), 3.0),
        )
        // The 30-day window behind it is answered from the same fetch
        repo.get(FEEDER).size shouldBe 4
        requests.size shouldBe 1
    }

    @Test
    fun `another installation starts from an empty cache`() = runBlocking<Unit> {
        answer = { listOf(hour(NOW - HOUR)) }
        repo.get(FEEDER)
        mode = mode.copy(server = FeederServerState(installationId = "other"))

        repo.get(FEEDER)

        requests shouldBe listOf(
            FEEDER to NOW - Duration.ofDays(30).toMillis(),
            FEEDER to NOW - Duration.ofDays(30).toMillis(),
        )
    }

    @Test
    fun `outside server mode there is no history and the cache is dropped`() = runBlocking<Unit> {
        answer = { listOf(hour(NOW - HOUR)) }
        repo.get(FEEDER)
        val active = mode
        mode = FeederServerMode.State(access = null, isActive = false)

        repo.get(FEEDER).shouldBeEmpty()
        requests.size shouldBe 1

        mode = active
        repo.get(FEEDER)
        requests.size shouldBe 2
    }

    companion object {
        private val NOW = Instant.parse("2026-11-09T12:00:00Z").toEpochMilli()
        private val HOUR = Duration.ofHours(1).toMillis()
        private const val FEEDER = "0a1b2c3d-0000-4000-8000-000000000001"
        private const val OTHER = "0a1b2c3d-0000-4000-8000-000000000002"
    }
}
