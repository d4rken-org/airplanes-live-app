package eu.darken.apl.map.core.overlay

import eu.darken.apl.common.MonotonicClock
import eu.darken.apl.common.coroutine.AppScope
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.feeder.core.link.FeederLinkRepo
import eu.darken.apl.feeder.core.link.FeederLinkRepo.FeederLinkState
import eu.darken.apl.map.core.MapSettings
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.access.AccessState
import eu.darken.apl.server.api.OverlayCatalogResponse
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerCodes
import eu.darken.apl.server.api.ServerEndpoint
import eu.darken.apl.server.compat.AppCompatibility
import eu.darken.apl.server.compat.CompatState
import eu.darken.apl.server.session.SessionManager
import eu.darken.apl.server.session.SessionRevokedException
import eu.darken.apl.server.session.SessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The server's overlay catalog, read only while [state] is collected. Reads come from the start of
 * collection, the refresh cadence of the overlays switched on, entitlement or feeder link changes and
 * [OverlayRefreshSignal]; a burst of them is answered by one read.
 */
@Singleton
class OverlayRepo internal constructor(
    appScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    private val clock: MonotonicClock,
    accessState: Flow<AccessState?>,
    feederLinkState: Flow<FeederLinkState>,
    compatState: Flow<CompatState>,
    sessionState: Flow<SessionState>,
    enabledOverlays: Flow<Set<String>>,
    private val refreshRequests: Flow<String>,
    private val fetchCatalog: suspend () -> OverlayCatalogResponse,
) {

    @Inject constructor(
        @AppScope appScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
        clock: MonotonicClock,
        accessRepo: AccessRepo,
        feederLinkRepo: FeederLinkRepo,
        compatibility: AppCompatibility,
        sessionManager: SessionManager,
        endpoint: ServerEndpoint,
        mapSettings: MapSettings,
        refreshSignal: OverlayRefreshSignal,
    ) : this(
        appScope = appScope,
        dispatcherProvider = dispatcherProvider,
        clock = clock,
        accessState = accessRepo.state,
        feederLinkState = feederLinkRepo.state,
        compatState = compatibility.state,
        sessionState = sessionManager.state,
        enabledOverlays = mapSettings.enabledOverlays.flow,
        refreshRequests = refreshSignal.requests,
        fetchCatalog = { sessionManager.authed { endpoint.overlays(it) } },
    )

    /** What decides access to overlays. The full [AccessState] also changes with every usage update. */
    private data class Entitlement(
        val installationId: String,
        val tier: AccessState.Tier,
        val restricted: Boolean,
        val offersOverlays: Boolean,
    )

    /** A feeder status refresh that only moves its check times is no link change. */
    private data class FeederLink(val feederId: String?, val tier: String?)

    private enum class Trigger(val immediate: Boolean) {
        START(true),
        ENTITLEMENT(true),
        FEEDER_LINK(true),
        TIMER(false),
        SIGNAL(false),
        RETRY(false),
        ;
    }

    // Seen by the read loop before [readable] has cancelled it, so a change that ends reading can't slip in a read
    @Volatile private var latestEntitlement: Entitlement? = null
    @Volatile private var latestCompat: CompatState? = null
    @Volatile private var latestSession: SessionState? = null

    private val entitlement: Flow<Entitlement?> = accessState
        .map { access ->
            access?.let { Entitlement(it.installationId, it.tier, it.restricted, it.offersOverlays) }
        }
        .distinctUntilChanged()
        .onEach { latestEntitlement = it }

    private val feederLink: Flow<FeederLink> = feederLinkState
        .filter { it !is FeederLinkState.Unknown }
        .map { link ->
            when (link) {
                is FeederLinkState.Linked -> FeederLink(link.feeder.feederId, link.tier)
                is FeederLinkState.Unlinked -> FeederLink(null, link.tier)
                else -> FeederLink(null, null)
            }
        }
        .distinctUntilChanged()

    private val externalBlock: Flow<OverlayState.Block?> = combine(
        compatState.onEach { latestCompat = it },
        sessionState.onEach { latestSession = it },
        entitlement,
    ) { compat, session, access -> blockOf(compat, session, access) }.distinctUntilChanged()

    private val readable: Flow<Boolean> = combine(entitlement, externalBlock) { access, block ->
        isReadable(access, block)
    }.distinctUntilChanged()

    private val catalog = MutableStateFlow<OverlayCatalogResponse?>(null)

    /** Set by a catalog read refused as `installation_restricted`, until a later read succeeds. */
    private val restrictedByCatalog = MutableStateFlow(false)

    /** Counts finished reads, the refresh timer starts over with each. */
    private val attempts = MutableStateFlow(0L)

    @Volatile private var lastReadAt: Long? = null
    @Volatile private var retryAt: Long? = null

    private val refreshIntervalMs: Flow<Long?> = combine(enabledOverlays, catalog) { enabled, current ->
        current?.overlays
            ?.filter { it.id in enabled }
            ?.minOfOrNull { it.refreshSeconds }
            ?.let { it.coerceAtLeast(1) * 1000L }
    }.distinctUntilChanged()

    val state: StateFlow<OverlayState> = channelFlow {
        launch {
            entitlement.collect { if (it?.offersOverlays == false) catalog.value = null }
        }
        launch {
            readable.collectLatest { open -> if (open) readWhileOpen() }
        }
        combine(entitlement, externalBlock, catalog, restrictedByCatalog) { access, block, current, restricted ->
            val offered = access?.offersOverlays == true
            OverlayState(
                catalog = current.takeIf { offered },
                offered = offered,
                block = block ?: OverlayState.Block.RESTRICTED.takeIf { restricted },
            )
        }.distinctUntilChanged().collect { send(it) }
    }
        .flowOn(dispatcherProvider.Default)
        .stateIn(appScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 0), OverlayState())

    private suspend fun readWhileOpen(): Unit = coroutineScope {
        val triggers = Channel<Trigger>(Channel.UNLIMITED)
        triggers.trySend(Trigger.START)
        // A refresh signal arriving while a read is under way is answered by that read
        val readInFlight = AtomicBoolean(false)

        // The values current at the start are what the start read answers
        launch {
            entitlement.drop(1)
                .filter { it != null && it.offersOverlays && !it.restricted }
                .collect { triggers.send(Trigger.ENTITLEMENT) }
        }
        launch { feederLink.drop(1).collect { triggers.send(Trigger.FEEDER_LINK) } }
        launch { refreshRequests.collect { if (!readInFlight.get()) triggers.send(Trigger.SIGNAL) } }
        launch {
            combine(refreshIntervalMs, attempts) { interval, _ -> interval }.collectLatest { interval ->
                if (interval == null) return@collectLatest
                delay(interval)
                triggers.send(Trigger.TIMER)
            }
        }

        while (true) {
            val trigger = triggers.receive()
            var immediate = trigger.immediate

            retryAt?.let { delay(it - clock.elapsed()) }
            if (!immediate) immediate = awaitSpacing(triggers)

            readInFlight.set(true)
            try {
                while (true) {
                    val next = triggers.tryReceive().getOrNull() ?: break
                    if (next.immediate) immediate = true
                }

                // Only news about the installation can lift a restriction the server reported
                if (!immediate && restrictedByCatalog.value) {
                    log(TAG, VERBOSE) { "Skipping the $trigger read while the installation is restricted" }
                    continue
                }

                val access = latestEntitlement
                if (!isReadable(access, blockOf(latestCompat, latestSession, access))) {
                    log(TAG, VERBOSE) { "Skipping the $trigger read, reading is no longer allowed" }
                    continue
                }

                read(trigger, triggers)
            } finally {
                readInFlight.set(false)
            }
        }
    }

    private fun blockOf(compat: CompatState?, session: SessionState?, access: Entitlement?): OverlayState.Block? =
        when {
            compat is CompatState.UpdateRequired -> OverlayState.Block.UPDATE_REQUIRED
            session is SessionState.Revoked -> OverlayState.Block.REVOKED
            access?.restricted == true -> OverlayState.Block.RESTRICTED
            else -> null
        }

    private fun isReadable(access: Entitlement?, block: OverlayState.Block?): Boolean =
        access?.offersOverlays == true && block == null

    /** Returns true when a trigger that may skip the spacing arrived while waiting. */
    private suspend fun awaitSpacing(triggers: ReceiveChannel<Trigger>): Boolean {
        while (true) {
            val left = (lastReadAt ?: return false) + MIN_SPACING_MS - clock.elapsed()
            if (left <= 0) return false
            val next = withTimeoutOrNull(left) { triggers.receive() } ?: return false
            if (next.immediate) return true
        }
    }

    private suspend fun read(trigger: Trigger, triggers: SendChannel<Trigger>) {
        log(TAG) { "Reading the catalog ($trigger)" }
        lastReadAt = clock.elapsed()
        try {
            catalog.value = fetchCatalog()
            restrictedByCatalog.value = false
            retryAt = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: SessionRevokedException) {
            log(TAG, WARN) { "Catalog unavailable, installation is revoked" }
        } catch (e: ServerApiException) {
            log(TAG, WARN) { "Catalog read refused: ${e.asLog()}" }
            when (e.code) {
                ServerCodes.INSTALLATION_RESTRICTED -> restrictedByCatalog.value = true
                // The refusal has been noted, the compatibility state stops the reads
                ServerCodes.APP_UPDATE_REQUIRED -> Unit
                else -> scheduleRetry(e.retryAfterSeconds, triggers)
            }
        } catch (e: Exception) {
            log(TAG, WARN) { "Catalog read failed: ${e.asLog()}" }
            scheduleRetry(null, triggers)
        } finally {
            attempts.update { it + 1 }
        }
    }

    private fun scheduleRetry(retryAfterSeconds: Long?, triggers: SendChannel<Trigger>) {
        val wait = (retryAfterSeconds ?: DEFAULT_RETRY_SECONDS) * 1000L
        retryAt = clock.elapsed() + wait
        triggers.trySend(Trigger.RETRY)
    }

    companion object {
        private const val MIN_SPACING_MS = 30_000L
        private const val DEFAULT_RETRY_SECONDS = 60L
        private val TAG = logTag("Map", "Overlay", "Repo")
    }
}
