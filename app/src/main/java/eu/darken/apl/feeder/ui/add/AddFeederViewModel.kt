package eu.darken.apl.feeder.ui.add

import androidx.core.net.toUri
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.flow.SingleEventFlow
import eu.darken.apl.common.flow.combine
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.feeder.core.FeederRepo
import eu.darken.apl.feeder.core.FeederDiscovery
import eu.darken.apl.feeder.core.config.FeederPosition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AddFeederViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val feederRepo: FeederRepo,
    private val feederDiscovery: FeederDiscovery,
    private val json: Json,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Feeder", "Add", "VM"),
) {

    private val _receiverId = MutableStateFlow("")
    private val _receiverLabel = MutableStateFlow("")
    private val _receiverIpAddress = MutableStateFlow("")
    private val _receiverPosition = MutableStateFlow("")
    private val _isLoading = MutableStateFlow(false)
    private val _isDetectingLocal = MutableStateFlow(false)

    val events = SingleEventFlow<AddFeederEvents>()

    val state = combine(
        _receiverId,
        _receiverLabel,
        _receiverIpAddress,
        _receiverPosition,
        _isLoading,
        _isDetectingLocal,
    ) { id, label, ipAddress, position, isLoading, isDetectingLocal ->
        val isValidInput = try {
            UUID.fromString(id.trim())
            id.trim().isNotBlank()
        } catch (_: IllegalArgumentException) {
            false
        }

        State(
            receiverId = id,
            receiverLabel = label,
            receiverIpAddress = ipAddress,
            receiverPosition = position,
            isAddButtonEnabled = isValidInput && !isLoading && !isDetectingLocal,
            isLoading = isLoading,
            isDetectingLocal = isDetectingLocal,
        )
    }.asStateFlow()

    fun addFeeder() = launch {
        val currentState = state.first()
        log(tag) { "addFeeder($currentState)" }

        _isLoading.value = true

        // The button enables on the trimmed id, so adding has to use the same value it approved
        val receiverId = currentState.receiverId.trim()

        try {
            UUID.fromString(receiverId) // ID check

            feederRepo.addFeeder(receiverId)
            currentState.receiverLabel.trim().takeIf { it.isNotBlank() }?.let {
                feederRepo.setLabel(receiverId, it)
            }
            currentState.receiverIpAddress.trim().takeIf { it.isNotBlank() }?.let {
                feederRepo.setAddress(receiverId, it)
            }
            currentState.receiverPosition.trim().takeIf { it.isNotBlank() }
                ?.let { FeederPosition.fromString(it) }
                ?.let { feederRepo.setPosition(receiverId, it) }
            navUp()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The feeder is stored before its stats are fetched, so a failure here can still mean it
            // was added. Reporting that as a failure would tell the user the opposite of what happened
            val stored = feederRepo.feeders.first().any { it.id == receiverId }
            log(tag, WARN) { "Failed to add feeder (stored=$stored): ${e.asLog()}" }
            if (stored) navUp() else errorEvents.emit(e)
        } finally {
            _isLoading.value = false
        }
    }

    fun updateReceiverId(id: String) {
        if (id == _receiverId.value || _isLoading.value) return
        log(tag) { "updateReceiverId($id)" }
        _receiverId.value = id
    }

    fun updateReceiverLabel(label: String) {
        if (label == _receiverLabel.value || _isLoading.value) return
        log(tag) { "updateReceiverLabel($label)" }
        _receiverLabel.value = label
    }

    fun updateReceiverIpAddress(ipAddress: String) {
        if (ipAddress == _receiverIpAddress.value || _isLoading.value) return
        log(tag) { "updateReceiverIpAddress($ipAddress)" }
        _receiverIpAddress.value = ipAddress
    }

    fun updateReceiverPosition(position: String) {
        if (position == _receiverPosition.value || _isLoading.value) return
        log(tag) { "updateReceiverPosition($position)" }
        _receiverPosition.value = position
    }

    fun detectLocalFeeder() = launch {
        log(tag) { "detectLocalFeeder()" }
        _isDetectingLocal.value = true

        try {
            val detectedFeeders = feederDiscovery.scan().feeders

            when {
                detectedFeeders.isEmpty() -> {
                    events.tryEmit(AddFeederEvents.ShowLocalDetectionResult(LocalDetectionResult.NOT_FOUND))
                }

                detectedFeeders.size == 1 -> {
                    applyDetectedFeeder(detectedFeeders.first())
                    events.tryEmit(AddFeederEvents.ShowLocalDetectionResult(LocalDetectionResult.FOUND))
                }

                else -> {
                    events.tryEmit(AddFeederEvents.ShowFeederPicker(detectedFeeders))
                }
            }
        } finally {
            _isDetectingLocal.value = false
        }
    }

    fun selectDetectedFeeder(feeder: DetectedFeeder) {
        log(tag) { "selectDetectedFeeder($feeder)" }
        applyDetectedFeeder(feeder)
    }

    private fun applyDetectedFeeder(feeder: DetectedFeeder) {
        updateReceiverId(feeder.uuid.toString())
        updateReceiverIpAddress(feeder.host)
        feeder.label?.let { updateReceiverLabel(it) }
        if (feeder.latitude != null && feeder.longitude != null) {
            updateReceiverPosition("${feeder.latitude}, ${feeder.longitude}")
        }
    }

    fun handleQrScan(text: String) = launch {
        log(tag) { "handleQrScan($text)" }

        val uri = try {
            text.toUri()
        } catch (_: Exception) {
            log(tag) { "handleQrScan(): Invalid URI format" }
            return@launch
        }

        val feederQR = NewFeederQR.fromUri(uri, json)
        if (feederQR == null) {
            log(tag) { "handleQrScan(): Failed to parse QR data" }
            return@launch
        }

        log(tag) { "handleQrScan(): Got $feederQR" }

        updateReceiverId(feederQR.receiverId)
        updateReceiverLabel(feederQR.receiverLabel ?: "")
        updateReceiverIpAddress(feederQR.receiverIpv4Address ?: "")
        feederQR.position?.let { position ->
            updateReceiverPosition("${position.latitude}, ${position.longitude}")
        }

        events.tryEmit(AddFeederEvents.StopCamera)
    }


    data class State(
        val receiverId: String,
        val receiverLabel: String,
        val receiverIpAddress: String,
        val receiverPosition: String,
        val isAddButtonEnabled: Boolean,
        val isLoading: Boolean,
        val isDetectingLocal: Boolean,
    )
}
