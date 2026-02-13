package eu.darken.apl.feeder.ui.add

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.apl.R
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.uix.Fragment3
import eu.darken.apl.common.viewbinding.viewBinding
import eu.darken.apl.databinding.AddFeederFragmentBinding
import eu.darken.apl.databinding.CameraPreviewLayoutBinding
import eu.darken.apl.feeder.core.config.FeederPosition
import java.util.EnumMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@AndroidEntryPoint
class AddFeederFragment : Fragment3(R.layout.add_feeder_fragment) {

    override val vm: AddFeederViewModel by viewModels()
    override val ui: AddFeederFragmentBinding by viewBinding()

    private var cameraPreviewBinding: CameraPreviewLayoutBinding? = null
    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null
    private var isScanning = false

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCameraPreview()
        } else {
            MaterialAlertDialogBuilder(requireContext()).apply {
                setTitle(getString(R.string.feeder_list_camera_permission_required_title))
                setMessage(getString(R.string.feeder_list_camera_permission_required_message))
                setPositiveButton(R.string.common_done_action) { dialog, _ -> dialog.dismiss() }
            }.show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        cameraExecutor = Executors.newSingleThreadExecutor()

        ui.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        ui.scanQrButton.setOnClickListener { startQrCodeScanning() }

        ui.detectLocalButton.setOnClickListener {
            vm.detectLocalFeeder()
        }

        ui.feederId.doAfterTextChanged { text ->
            vm.updateReceiverId(text.toString())
        }
        ui.feederLabel.doAfterTextChanged { text ->
            vm.updateReceiverLabel(text.toString())
        }
        ui.feederIpAddress.doAfterTextChanged { text ->
            vm.updateReceiverIpAddress(text.toString())
        }
        ui.feederPosition.doAfterTextChanged { text ->
            vm.updateReceiverPosition(text.toString())
        }

        ui.addButton.setOnClickListener {
            vm.addFeeder()
        }

        vm.state.observeWith(ui) { state ->
            if (feederId.text.toString() != state.receiverId) {
                feederId.setText(state.receiverId)
            }
            if (feederLabel.text.toString() != state.receiverLabel) {
                feederLabel.setText(state.receiverLabel)
            }
            if (feederIpAddress.text.toString() != state.receiverIpAddress) {
                feederIpAddress.setText(state.receiverIpAddress)
            }
            if (feederPosition.text.toString() != state.receiverPosition) {
                feederPosition.setText(state.receiverPosition)
            }
            feederPositionLayout.error = when {
                state.receiverPosition.isBlank() -> null
                FeederPosition.fromString(state.receiverPosition) != null -> null
                else -> getString(R.string.feeder_list_position_format_error)
            }

            // Handle loading state
            val isEnabled = !state.isLoading && !state.isDetectingLocal
            feederId.isEnabled = isEnabled
            feederLabel.isEnabled = isEnabled
            feederIpAddress.isEnabled = isEnabled
            feederPosition.isEnabled = isEnabled
            scanQrButton.isEnabled = isEnabled
            detectLocalButton.isEnabled = isEnabled

            // Handle button and progress indicator
            if (state.isLoading) {
                addButton.visibility = View.INVISIBLE
                progressIndicator.visibility = View.VISIBLE
                progressIndicator.show()
            } else {
                addButton.visibility = View.VISIBLE
                progressIndicator.hide()
                progressIndicator.visibility = View.GONE
                addButton.isEnabled = state.isAddButtonEnabled
            }

            // Handle local detection button state
            if (state.isDetectingLocal) {
                detectLocalButton.text = getString(R.string.feeder_list_detecting_local_feeder)
            } else {
                detectLocalButton.text = getString(R.string.feeder_list_detect_local_title)
            }
        }

        vm.events.observeWith(ui) { event ->
            when (event) {
                is AddFeederEvents.StopCamera -> stopCameraPreview()
                is AddFeederEvents.ShowLocalDetectionResult -> {
                    val message = when (event.result) {
                        LocalDetectionResult.FOUND -> getString(R.string.feeder_list_local_feeder_found)
                        LocalDetectionResult.NOT_FOUND -> getString(R.string.feeder_list_no_local_feeder_found)
                    }
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                }
                is AddFeederEvents.ShowFeederPicker -> showFeederPickerDialog(event.feeders)
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }

    override fun onDestroyView() {
        if (::cameraExecutor.isInitialized) cameraExecutor.shutdown()
        cameraProvider?.unbindAll()
        cameraPreviewBinding = null
        super.onDestroyView()
    }

    private fun startQrCodeScanning() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startCameraPreview()
            }

            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                MaterialAlertDialogBuilder(requireContext()).apply {
                    setTitle(R.string.feeder_list_camera_permission_required_title)
                    setMessage(R.string.feeder_list_camera_permission_required_message)
                    setPositiveButton(R.string.common_grant_permission_action) { _, _ ->
                        requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                    setNegativeButton(R.string.common_cancel_action) { dialog, _ -> dialog.dismiss() }
                }.show()
            }

            else -> {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun startCameraPreview() {
        if (isScanning) return
        isScanning = true

        cameraPreviewBinding = CameraPreviewLayoutBinding.inflate(layoutInflater)
        val cameraPreviewView = cameraPreviewBinding?.root ?: return

        // Hide everything except toolbar
        ui.formContainer.visibility = View.GONE
        ui.buttonContainer.visibility = View.GONE

        // Add camera preview as full screen overlay
        val layoutParams = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_PARENT,
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_PARENT
        ).apply {
            topToBottom = ui.toolbar.id
            bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
        }

        ui.root.addView(cameraPreviewView, layoutParams)

        cameraPreviewBinding?.closeButton?.setOnClickListener {
            stopCameraPreview()
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(cameraPreviewBinding?.previewView?.surfaceProvider)

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor, QrCodeAnalyzer())

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider?.unbindAll()

                cameraProvider?.bindToLifecycle(
                    viewLifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                log(TAG) { "Error starting camera: ${e.message}" }
                Toast.makeText(
                    requireContext(),
                    getString(R.string.feeder_list_camera_error, e.message),
                    Toast.LENGTH_SHORT
                ).show()
                stopCameraPreview()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun stopCameraPreview() {
        if (!isScanning) return
        isScanning = false
        cameraProvider?.unbindAll()
        cameraPreviewBinding?.let { binding -> ui.root.removeView(binding.root) }

        // Restore all UI elements
        ui.formContainer.visibility = View.VISIBLE
        ui.buttonContainer.visibility = View.VISIBLE
        cameraPreviewBinding = null
    }

    private fun showFeederPickerDialog(feeders: List<DetectedFeeder>) {
        val items = feeders.map { feeder ->
            val displayName = feeder.label ?: feeder.host
            val uuidShort = feeder.uuid.toString().take(8)
            "$displayName ($uuidShort… @ ${feeder.host})"
        }.toTypedArray()

        var selectedIndex = 0
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.feeder_list_multiple_feeders_title)
            .setSingleChoiceItems(items, selectedIndex) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton(R.string.common_done_action) { _, _ ->
                vm.selectDetectedFeeder(feeders[selectedIndex])
            }
            .setNegativeButton(R.string.common_cancel_action, null)
            .show()
    }

    @ExperimentalGetImage
    private inner class QrCodeAnalyzer : ImageAnalysis.Analyzer {
        private val reader = MultiFormatReader().apply {
            val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java)
            hints[DecodeHintType.POSSIBLE_FORMATS] = listOf(BarcodeFormat.QR_CODE)
            setHints(hints)
        }

        override fun analyze(imageProxy: ImageProxy) {
            try {
                val mediaImage = imageProxy.image
                if (mediaImage == null) {
                    imageProxy.close()
                    return
                }

                // Get the YUV data
                val yBuffer = mediaImage.planes[0].buffer // Y plane contains the luminance data
                val ySize = yBuffer.remaining()
                val yuvData = ByteArray(ySize)
                yBuffer.get(yuvData, 0, ySize)

                // Create a luminance source directly from the Y plane data
                val source = PlanarYUVLuminanceSource(
                    yuvData,
                    mediaImage.width,
                    mediaImage.height,
                    0,
                    0,
                    mediaImage.width,
                    mediaImage.height,
                    false
                )

                val bitmap = BinaryBitmap(HybridBinarizer(source))

                try {
                    val result = reader.decode(bitmap)
                    val rawValue = result.text

                    log(TAG) { "QR code detected: $rawValue" }
                    vm.handleQrScan(rawValue)
                } catch (_: ReaderException) {
                    // QR code not found in this frame
                    log(TAG) { "No QR code found in this frame" }
                }
            } catch (e: Exception) {
                log(TAG) { "Error analyzing image: ${e.message}" }
            } finally {
                imageProxy.close()
            }
        }
    }

    companion object {
        private val TAG = logTag("Feeder", "Add", "Fragment")
    }
}
