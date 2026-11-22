package eu.darken.apl.upgrade.subscription.submit

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.serialization.SerializationModule
import eu.darken.apl.server.PrivacyAcceptance
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.api.Allowance
import eu.darken.apl.server.api.RequestLimits
import eu.darken.apl.server.api.RequestRate
import eu.darken.apl.server.api.ServerEndpoint
import eu.darken.apl.server.api.SubscriptionResponse
import eu.darken.apl.server.api.TierPolicy
import eu.darken.apl.server.api.Usage
import eu.darken.apl.server.deletion.DeletionState
import eu.darken.apl.server.deletion.ServerRequestGate
import eu.darken.apl.server.session.SessionManager
import eu.darken.apl.upgrade.subscription.play.PlayPurchase
import eu.darken.apl.upgrade.subscription.play.PlaySubscriptionProduct
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import testhelper.BaseTest
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * The submission parts wired like the app, with a file backed store. [restart] keeps the file and
 * what WorkManager was given, and drops everything held in memory.
 */
class SubmissionFixture(
    endpointFactory: (SubmissionFixture) -> ServerEndpoint = { mockk() },
) : AutoCloseable {

    data class Enqueued(val name: String, val policy: ExistingWorkPolicy, val request: OneTimeWorkRequest) {
        val delay: Duration get() = Duration.ofMillis(request.workSpec.initialDelay)
        val installationId: String? get() = request.workSpec.input.getString(SubscriptionSubmitWorker.KEY_INSTALLATION_ID)
        val tokenHash: String? get() = request.workSpec.input.getString(SubscriptionSubmitWorker.KEY_TOKEN_HASH)
    }

    var elapsed = START_ELAPSED
    val serverClock = ServerClock(object : MonotonicClock {
        override fun elapsed(): Long = elapsed
    }).apply { noteServerTime(NOW.toEpochMilli()) }

    val nowMillis: Long get() = serverClock.now().toEpochMilli()

    val enqueued = mutableListOf<Enqueued>()
    private val workManager = mockk<WorkManager>().apply {
        every { enqueueUniqueWork(any<String>(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>()) } answers {
            synchronized(enqueued) { enqueued += Enqueued(firstArg(), secondArg(), thirdArg()) }
            mockk(relaxed = true)
        }
    }

    val accessState = MutableStateFlow<AccessState?>(null)
    val accessRepo = mockk<AccessRepo>(relaxed = true).apply {
        every { state } returns accessState
    }

    val privacyAccepted = MutableStateFlow(true)
    val privacyAcceptance = PrivacyAcceptance(privacyAccepted)
    val deletionState = MutableStateFlow(DeletionState.NONE)
    val requestGate = ServerRequestGate(deletionState)

    val endpoint: ServerEndpoint = endpointFactory(this)

    /** The installation the session belongs to whenever [sessionManager] hands out credentials. */
    var sessionInstallationId = INSTALLATION_A
    val sessionManager = mockk<SessionManager>().apply {
        coEvery { authedAs<SubscriptionResponse>(any()) } coAnswers {
            firstArg<suspend (String, String) -> SubscriptionResponse>().invoke(sessionInstallationId, SESSION_TOKEN)
        }
    }

    private val file = File(BaseTest.IO_TEST_BASEDIR, "subscription-tokens-${UUID.randomUUID()}.preferences_pb").apply {
        parentFile?.mkdirs()
        delete()
    }
    private var storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    var dataStore: DataStore<Preferences> = createDataStore()
        private set

    lateinit var store: SubscriptionTokenStore
        private set
    lateinit var scheduler: SubscriptionSubmitScheduler
        private set
    lateinit var policyWait: SubscriptionPolicyWait
        private set
    lateinit var submissions: SubscriptionSubmissions
        private set
    lateinit var submitter: SubscriptionSubmitter
        private set

    init {
        wire()
    }

    private fun createDataStore() = PreferenceDataStoreFactory.create(scope = storeScope, produceFile = { file })

    private fun wire() {
        store = SubscriptionTokenStore(dataStore, SerializationModule().json())
        scheduler = SubscriptionSubmitScheduler(workManager, serverClock)
        policyWait = SubscriptionPolicyWait(store, accessRepo, serverClock)
        submissions = SubscriptionSubmissions(store, scheduler, policyWait)
        submitter = SubscriptionSubmitter(
            store = store,
            scheduler = scheduler,
            policyWait = policyWait,
            endpoint = endpoint,
            sessionManager = sessionManager,
            accessRepo = accessRepo,
            privacyAcceptance = privacyAcceptance,
            requestGate = requestGate,
            serverClock = serverClock,
        )
    }

    /** A new process: the stored file and the queued work survive, in-memory state does not. */
    suspend fun restart() {
        storeScope.coroutineContext.job.cancelAndJoin()
        storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        dataStore = createDataStore()
        wire()
    }

    suspend fun record(tokenHash: String, installationId: String = INSTALLATION_A): SubscriptionTokenRecord? =
        store.current().find(installationId, tokenHash)

    override fun close() {
        runBlocking { storeScope.coroutineContext.job.cancelAndJoin() }
        file.delete()
    }

    companion object {
        const val INSTALLATION_A = "installation-a"
        const val INSTALLATION_B = "installation-b"
        const val SESSION_TOKEN = "session-token"
        const val START_ELAPSED = 1_000L
        val NOW: Instant = Instant.parse("2026-11-20T12:00:00Z")

        fun purchase(
            token: String,
            state: PlayPurchase.State = PlayPurchase.State.PURCHASED,
            suspended: Boolean = false,
        ) = PlayPurchase(
            token = token,
            products = listOf(PlaySubscriptionProduct.PRODUCT_ID),
            state = state,
            isSuspended = suspended,
            isAutoRenewing = true,
        )

        fun response(state: String = "active", attached: Boolean = true) = SubscriptionResponse(
            state = state,
            granted = attached,
            expiresAt = null,
            attached = attached,
        )

        fun access(installationId: String, fetchStartedElapsed: Long?) = AccessState(
            installationId = installationId,
            fetchedAt = Instant.EPOCH,
            tier = AccessState.Tier.SUBSCRIBER,
            restricted = false,
            allowanceScope = "principal",
            limits = TierPolicy(
                viewingIntervalSeconds = 5,
                viewingBurst = 3,
                viewingPerDay = 25_000,
                searchesPerDay = 25,
                watchesPerDay = 1000,
                maxRadiusNm = 25,
                watchTypes = listOf("hex"),
            ),
            usage = Usage(
                resetsAt = 0,
                viewing = Allowance(25_000, 0, 0, 25_000),
                search = Allowance(25, 0, 0, 25),
                watch = Allowance(1000, 0, 0, 1000),
            ),
            installationRequests = RequestLimits(RequestRate(1.0, 3), RequestRate(1.0, 3), RequestRate(1.0, 3), 3),
            fetchStartedElapsed = fetchStartedElapsed,
        )
    }
}
