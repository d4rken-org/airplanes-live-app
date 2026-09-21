package eu.darken.apl.feeder.core.link

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import eu.darken.apl.common.coroutine.AppScope
import eu.darken.apl.common.datastore.createValue
import eu.darken.apl.common.datastore.value
import eu.darken.apl.common.debug.logging.Logging.Priority.INFO
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.server.ServerFeederLinkDataStore
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.api.FeederStatusResponse
import eu.darken.apl.server.api.LinkedFeeder
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerEndpoint
import eu.darken.apl.server.session.SessionManager
import eu.darken.apl.server.session.SessionRevokedException
import eu.darken.apl.server.session.SessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The feeder that unlocks Feeder access for this installation. This is independent from the
 * monitored feeders: adding a feeder to the list never registers it here.
 */
@Singleton
class FeederLinkRepo @Inject constructor(
    @param:AppScope private val appScope: CoroutineScope,
    @param:ServerFeederLinkDataStore private val dataStore: DataStore<Preferences>,
    json: Json,
    private val endpoint: ServerEndpoint,
    private val sessionManager: SessionManager,
    private val accessRepo: AccessRepo,
) {

    @Serializable
    data class LinkRecord(
        val installationId: String,
        val tier: String,
        val restricted: Boolean,
        val feeder: LinkedFeeder? = null,
    )

    sealed interface FeederLinkState {
        /** Nothing has been fetched or restored yet. */
        data object Unknown : FeederLinkState

        /** A fetch finished without an answer, so waiting for one longer is pointless. */
        data object Unavailable : FeederLinkState
        data class Unlinked(val tier: String) : FeederLinkState
        data class Linked(val feeder: LinkedFeeder, val tier: String) : FeederLinkState
    }

    private val persisted = dataStore.createValue<LinkRecord?>(
        key = "feeder.link.last",
        defaultValue = null,
        json = json,
        onErrorFallbackToDefault = true,
    )

    // Serializes endpoint call plus store, so a slow status refresh can't overwrite a newer link
    private val stateLock = Mutex()

    private val _state = MutableStateFlow<FeederLinkState>(FeederLinkState.Unknown)
    val state: StateFlow<FeederLinkState> = _state.asStateFlow()

    init {
        appScope.launch {
            persisted.value()?.let { publish(it) }
        }
        appScope.launch {
            sessionManager.state.collect { session ->
                if (session !is SessionState.Active) return@collect
                val record = persisted.value()
                // A link fetched for a different installation says nothing about this one
                if (record != null && record.installationId != session.installationId) {
                    persisted.value(null)
                    _state.value = FeederLinkState.Unknown
                }
                try {
                    refresh()
                } catch (e: SessionRevokedException) {
                    log(TAG, WARN) { "Feeder link unavailable, installation is revoked" }
                    markUnavailable()
                } catch (e: ServerApiException) {
                    log(TAG, WARN) { "Feeder link unavailable: ${e.asLog()}" }
                    markUnavailable()
                } catch (e: IOException) {
                    log(TAG, WARN) { "Feeder link unavailable: ${e.asLog()}" }
                    markUnavailable()
                }
            }
        }
    }

    suspend fun register(feederId: String) {
        log(TAG, INFO) { "register(...)" }
        stateLock.withLock {
            store(sessionManager.authed { endpoint.registerFeeder(it, feederId) })
        }
        refreshEntitlement()
    }

    suspend fun unlink() {
        log(TAG, INFO) { "unlink()" }
        stateLock.withLock {
            store(sessionManager.authed { endpoint.unlinkFeeder(it) })
        }
        refreshEntitlement()
    }

    /**
     * The link change is already stored when this runs, so nothing here may report it as failed.
     * Ordinary network trouble is absorbed by the refresh itself; this covers what is not.
     */
    private suspend fun refreshEntitlement() {
        try {
            accessRepo.refresh("feeder-link")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, WARN) { "The link is stored, its entitlement refresh is not: ${e.asLog()}" }
        }
    }

    /**
     * Fetches the server's view and returns what this fetch said, rather than leaving the caller to
     * read [state]. A caller that has to tell "no link" apart from "no answer" needs both halves:
     * the failure, which [refresh] swallows, and a result that a concurrent write cannot stand in for.
     */
    suspend fun fetchStatus(): FeederLinkState = stateLock.withLock {
        store(sessionManager.authed { endpoint.feederStatus(it) })
    }

    /**
     * Whether the server has [feederId] linked, asked after a registration whose answer never
     * arrived. A hit also runs the entitlement refresh [register] does, because that registration
     * landed and nothing else is going to notice.
     *
     * A miss is not proof of rejection: the server may not have committed the registration yet.
     */
    suspend fun reconcileRegistration(feederId: String): Boolean {
        log(TAG, INFO) { "reconcileRegistration(...)" }
        val status = fetchStatus()
        val linked = status is FeederLinkState.Linked && status.feeder.feederId == feederId
        if (linked) refreshEntitlement()
        return linked
    }

    suspend fun refresh() {
        try {
            fetchStatus()
        } catch (e: IOException) {
            log(TAG, WARN) { "Feeder status unavailable: ${e.asLog()}" }
            markUnavailable()
        }
    }

    /**
     * Only replaces the state that means "no answer yet". A known link stays on screen through a
     * failed refresh; it is still the last thing the server said.
     */
    private fun markUnavailable() {
        _state.update { if (it is FeederLinkState.Unknown) FeederLinkState.Unavailable else it }
    }

    private suspend fun store(response: FeederStatusResponse): FeederLinkState {
        val installationId = (sessionManager.state.value as? SessionState.Active)?.installationId ?: ""
        val record = LinkRecord(
            installationId = installationId,
            tier = response.tier,
            restricted = response.restricted,
            feeder = response.feeder,
        )
        persisted.value(record)
        return publish(record)
    }

    private fun publish(record: LinkRecord): FeederLinkState {
        val published = record.feeder
            ?.let { FeederLinkState.Linked(it, record.tier) }
            ?: FeederLinkState.Unlinked(record.tier)
        _state.value = published
        return published
    }

    companion object {
        private val TAG = logTag("Feeder", "LinkRepo")
    }
}
