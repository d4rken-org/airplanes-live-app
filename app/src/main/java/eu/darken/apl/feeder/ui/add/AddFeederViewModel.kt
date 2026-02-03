package eu.darken.apl.feeder.ui.add

import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.flow.SingleEventFlow
import eu.darken.apl.common.flow.combine
import eu.darken.apl.common.uix.ViewModel3
import eu.darken.apl.feeder.core.FeederRepo
import eu.darken.apl.feeder.core.api.FeederEndpoint
import eu.darken.apl.feeder.core.config.FeederPosition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AddFeederViewModel @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val feederRepo: FeederRepo,
    private val feederEndpoint: FeederEndpoint,
    private val json: Json,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val _receiverId = MutableStateFlow("")
    private val _receiverLabel = MutableStateFlow("")
    private val _receiverIpAddress = MutableStateFlow("")
    private val _receiverPosition = MutableStateFlow("")
    private val _isLoading = MutableStateFlow(false)
    private val _isDetectingLocal = MutableStateFlow(false)

    val events = SingleEventFlow<AddFeederEvents>()

    init {
        handle.get<String>("qr_data")?.let { qrData ->
            log(tag) { "Processing QR data from SavedStateHandle: $qrData" }
            handleQrScan(qrData)
        }
    }

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

        try {
            UUID.fromString(currentState.receiverId) // ID check

            feederRepo.addFeeder(currentState.receiverId)
            if (currentState.receiverLabel.isNotBlank()) {
                feederRepo.setLabel(currentState.receiverId, currentState.receiverLabel)
            }
            if (currentState.receiverIpAddress.isNotBlank()) {
                feederRepo.setAddress(currentState.receiverId, currentState.receiverIpAddress)
            }
            if (currentState.receiverPosition.isNotBlank()) {
                val position = try {
                    val parts = currentState.receiverPosition.split(",").map { it.trim() }
                    if (parts.size == 2) {
                        val latitude = parts[0].toDouble()
                        val longitude = parts[1].toDouble()
                        FeederPosition(latitude = latitude, longitude = longitude)
                    } else null
                } catch (_: NumberFormatException) {
                    null
                }
                if (position != null) {
                    feederRepo.setPosition(currentState.receiverId, position)
                }
            }
            popNavStack()
        } catch (e: Exception) {
            log(tag) { "Failed to add feeder: ${e.message}" }
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
            val feedStatus = feederEndpoint.getFeedStatus()
            log(tag) { "detectLocalFeeder(): Got feed status: $feedStatus" }

            val mlatByUuid = feedStatus.mlatClients.associateBy { it.uuid }
            val detectedFeeders = feedStatus.beastClients.map { beastClient ->
                val mlatClient = mlatByUuid[beastClient.uuid]
                DetectedFeeder(
                    uuid = beastClient.uuid,
                    host = beastClient.host,
                    label = mlatClient?.user?.takeIf { it.isNotBlank() },
                    latitude = mlatClient?.latitude,
                    longitude = mlatClient?.longitude,
                )
            }

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
