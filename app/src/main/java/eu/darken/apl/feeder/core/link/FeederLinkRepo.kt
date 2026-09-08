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
import eu.darken.apl.server.api.ServerEndpoint
import eu.darken.apl.server.session.SessionManager
import eu.darken.apl.server.session.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
        data object Unknown : FeederLinkState
        data class Unlinked(val tier: String) : FeederLinkState
        data class Linked(val feeder: LinkedFeeder, val tier: String) : FeederLinkState
    }

    private val persisted = dataStore.createValue<LinkRecord?>(
        key = "feeder.link.last",
        defaultValue = null,
        json = json,
        onErrorFallbackToDefault = true,
    )

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
            }
        }
    }

    suspend fun register(feederId: String) {
        log(TAG, INFO) { "register(...)" }
        store(sessionManager.authed { endpoint.registerFeeder(it, feederId) })
        accessRepo.refresh("feeder-link")
    }

    suspend fun unlink() {
        log(TAG, INFO) { "unlink()" }
        store(sessionManager.authed { endpoint.unlinkFeeder(it) })
        accessRepo.refresh("feeder-link")
    }

    suspend fun refresh() {
        try {
            store(sessionManager.authed { endpoint.feederStatus(it) })
        } catch (e: IOException) {
            log(TAG, WARN) { "Feeder status unavailable: ${e.asLog()}" }
        }
    }

    private suspend fun store(response: FeederStatusResponse) {
        val installationId = (sessionManager.state.value as? SessionState.Active)?.installationId ?: ""
        val record = LinkRecord(
            installationId = installationId,
            tier = response.tier,
            restricted = response.restricted,
            feeder = response.feeder,
        )
        persisted.value(record)
        publish(record)
    }

    private fun publish(record: LinkRecord) {
        _state.value = record.feeder
            ?.let { FeederLinkState.Linked(it, record.tier) }
            ?: FeederLinkState.Unlinked(record.tier)
    }

    companion object {
        private val TAG = logTag("Feeder", "LinkRepo")
    }
}
