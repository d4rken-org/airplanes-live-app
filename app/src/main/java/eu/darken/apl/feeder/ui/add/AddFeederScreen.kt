package eu.darken.apl.feeder.ui.add

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Badge
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.Description
import androidx.compose.material.icons.twotone.Map
import androidx.compose.material.icons.twotone.QrCodeScanner
import androidx.compose.material.icons.twotone.Router
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import eu.darken.apl.R
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.compose.aplContentWindowInsets
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.feeder.core.config.FeederPosition
import java.util.EnumMap
import java.util.concurrent.Executors

private val TAG = logTag("Feeder", "Add", "Screen")

@Composable
fun AddFeederScreenHost(
    qrData: String? = null,
    vm: AddFeederViewModel = hiltViewModel(),
) {
    NavigationEventHandler(vm)
    ErrorEventHandler(vm)

    LaunchedEffect(Unit) {
        qrData?.let { vm.handleQrScan(it) }
    }

    val context = LocalContext.current
    val state by vm.state.collectAsState(initial = null)
    var isScanning by remember { mutableStateOf(false) }
    var showPermissionDenied by remember { mutableStateOf(false) }
    var showFeederPicker by remember { mutableStateOf<List<DetectedFeeder>?>(null) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isScanning = true
        } else {
            showPermissionDenied = true
        }
    }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is AddFeederEvents.StopCamera -> isScanning = false
                is AddFeederEvents.ShowLocalDetectionResult -> {
                    val message = when (event.result) {
                        LocalDetectionResult.FOUND -> context.getString(R.string.feeder_list_local_feeder_found)
                        LocalDetectionResult.NOT_FOUND -> context.getString(R.string.feeder_list_no_local_feeder_found)
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }

                is AddFeederEvents.ShowFeederPicker -> showFeederPicker = event.feeders
            }
        }
    }

    state?.let { s ->
        AddFeederScreen(
            state = s,
            isScanning = isScanning,
            onBack = { vm.navUp() },
            onUpdateId = { vm.updateReceiverId(it) },
            onUpdateLabel = { vm.updateReceiverLabel(it) },
            onUpdateIpAddress = { vm.updateReceiverIpAddress(it) },
            onUpdatePosition = { vm.updateReceiverPosition(it) },
            onScanQr = {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPermission) {
                    isScanning = true
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            onStopScan = { isScanning = false },
            onQrDetected = { vm.handleQrScan(it) },
            onDetectLocal = { vm.detectLocalFeeder() },
            onAddFeeder = { vm.addFeeder() },
        )
    }

    if (showPermissionDenied) {
        AlertDialog(
            onDismissRequest = { showPermissionDenied = false },
            title = { Text(stringResource(R.string.feeder_list_camera_permission_required_title)) },
            text = { Text(stringResource(R.string.feeder_list_camera_permission_required_message)) },
            confirmButton = {
                TextButton(onClick = { showPermissionDenied = false }) {
                    Text(stringResource(R.string.common_done_action))
                }
            },
        )
    }

    showFeederPicker?.let { feeders ->
        FeederPickerDialog(
            feeders = feeders,
            onSelect = { vm.selectDetectedFeeder(it); showFeederPicker = null },
            onDismiss = { showFeederPicker = null },
        )
    }
}

@Composable
fun AddFeederScreen(
    state: AddFeederViewModel.State,
    isScanning: Boolean,
    onBack: () -> Unit,
    onUpdateId: (String) -> Unit,
    onUpdateLabel: (String) -> Unit,
    onUpdateIpAddress: (String) -> Unit,
    onUpdatePosition: (String) -> Unit,
    onScanQr: () -> Unit,
    onStopScan: () -> Unit,
    onQrDetected: (String) -> Unit,
    onDetectLocal: () -> Unit,
    onAddFeeder: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = aplContentWindowInsets(),
        topBar = {
            if (!isScanning) {
                TopAppBar(
                    title = { Text(stringResource(R.string.feeder_list_add_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.TwoTone.Close, contentDescription = null)
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (!isScanning) {
                Surface(
                    shadowElevation = 8.dp,
                    modifier = Modifier.navigationBarsPadding(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center),
                            )
                        } else {
                            Button(
                                onClick = onAddFeeder,
                                enabled = state.isAddButtonEnabled,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                            ) {
                                Text(stringResource(R.string.common_add_action))
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (isScanning) {
            CameraPreview(
                onQrDetected = onQrDetected,
                onClose = onStopScan,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        } else {
            val isEnabled = !state.isLoading && !state.isDetectingLocal

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
            ) {
                // Input fields card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        var localId by rememberSaveable { mutableStateOf(state.receiverId) }
                        LaunchedEffect(state.receiverId) {
                            if (state.receiverId != localId) localId = state.receiverId
                        }
                        OutlinedTextField(
                            value = localId,
                            onValueChange = { localId = it; onUpdateId(it) },
                            label = { Text(stringResource(R.string.feeder_list_feeder_id_hint)) },
                            supportingText = { Text(stringResource(R.string.feeder_list_feeder_id_helper)) },
                            leadingIcon = {
                                Icon(Icons.TwoTone.Badge, contentDescription = null)
                            },
                            singleLine = true,
                            enabled = isEnabled,
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                        )

                        var localLabel by rememberSaveable { mutableStateOf(state.receiverLabel) }
                        LaunchedEffect(state.receiverLabel) {
                            if (state.receiverLabel != localLabel) localLabel = state.receiverLabel
                        }
                        OutlinedTextField(
                            value = localLabel,
                            onValueChange = { localLabel = it; onUpdateLabel(it) },
                            label = { Text(stringResource(R.string.feeder_list_label_hint)) },
                            supportingText = { Text(stringResource(R.string.feeder_list_label_helper)) },
                            leadingIcon = {
                                Icon(Icons.TwoTone.Description, contentDescription = null)
                            },
                            singleLine = true,
                            enabled = isEnabled,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                        )

                        var localIp by rememberSaveable { mutableStateOf(state.receiverIpAddress) }
                        LaunchedEffect(state.receiverIpAddress) {
                            if (state.receiverIpAddress != localIp) localIp = state.receiverIpAddress
                        }
                        OutlinedTextField(
                            value = localIp,
                            onValueChange = { localIp = it; onUpdateIpAddress(it) },
                            label = { Text(stringResource(R.string.feeder_list_ip_address_hint)) },
                            supportingText = { Text(stringResource(R.string.feeder_list_ip_address_explanation)) },
                            leadingIcon = {
                                Icon(Icons.TwoTone.Router, contentDescription = null)
                            },
                            singleLine = true,
                            enabled = isEnabled,
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                        )

                        var localPosition by rememberSaveable { mutableStateOf(state.receiverPosition) }
                        LaunchedEffect(state.receiverPosition) {
                            if (state.receiverPosition != localPosition) localPosition = state.receiverPosition
                        }
                        val positionError = localPosition.isNotBlank() &&
                            FeederPosition.fromString(localPosition) == null
                        OutlinedTextField(
                            value = localPosition,
                            onValueChange = { localPosition = it; onUpdatePosition(it) },
                            label = { Text(stringResource(R.string.feeder_list_position_hint)) },
                            supportingText = {
                                Text(
                                    if (positionError) stringResource(R.string.feeder_list_position_format_error)
                                    else stringResource(R.string.feeder_list_position_explanation)
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.TwoTone.Map, contentDescription = null)
                            },
                            isError = positionError,
                            singleLine = true,
                            enabled = isEnabled,
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                // QR scan card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.feeder_list_quick_setup_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.feeder_list_quick_setup_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        FilledTonalButton(
                            onClick = onScanQr,
                            enabled = isEnabled,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        ) {
                            Icon(
                                Icons.TwoTone.QrCodeScanner,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.feeder_list_scan_qr_title))
                        }
                    }
                }

                // Local detection card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.feeder_list_local_detection_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.feeder_list_local_detection_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        FilledTonalButton(
                            onClick = onDetectLocal,
                            enabled = isEnabled,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        ) {
                            Icon(
                                Icons.TwoTone.Router,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (state.isDetectingLocal) stringResource(R.string.feeder_list_detecting_local_feeder)
                                else stringResource(R.string.feeder_list_detect_local_title)
                            )
                        }
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraPreview(
    onQrDetected: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val currentOnQrDetected by rememberUpdatedState(onQrDetected)

    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        var cameraProvider: ProcessCameraProvider? = null

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(executor, QrCodeAnalyzer { text ->
                            currentOnQrDetected(text)
                        })
                    }

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis,
                )
            } catch (e: Exception) {
                log(TAG) { "Error starting camera: ${e.message}" }
                Toast.makeText(
                    context,
                    context.getString(R.string.feeder_list_camera_error, e.message),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            cameraProvider?.unbindAll()
            executor.shutdown()
        }
    }

    Box(modifier) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )

        FloatingActionButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
        ) {
            Icon(Icons.TwoTone.Close, contentDescription = null)
        }
    }
}

@Composable
private fun FeederPickerDialog(
    feeders: List<DetectedFeeder>,
    onSelect: (DetectedFeeder) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedIndex by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feeder_list_multiple_feeders_title)) },
        text = {
            Column {
                feeders.forEachIndexed { index, feeder ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedIndex = index }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedIndex == index,
                            onClick = { selectedIndex = index },
                        )
                        Spacer(Modifier.width(8.dp))
                        val displayName = feeder.label ?: feeder.host
                        val uuidShort = feeder.uuid.toString().take(8)
                        Text("$displayName ($uuidShort… @ ${feeder.host})")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (feeders.isNotEmpty()) onSelect(feeders[selectedIndex]) },
                enabled = feeders.isNotEmpty(),
            ) {
                Text(stringResource(R.string.common_done_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel_action))
            }
        },
    )
}

@ExperimentalGetImage
private class QrCodeAnalyzer(
    private val onQrDetected: (String) -> Unit,
) : ImageAnalysis.Analyzer {
    private val reader = MultiFormatReader().apply {
        val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java)
        hints[DecodeHintType.POSSIBLE_FORMATS] = listOf(BarcodeFormat.QR_CODE)
        setHints(hints)
    }

    override fun analyze(imageProxy: ImageProxy) {
        try {
            val mediaImage = imageProxy.image ?: return

            val yBuffer = mediaImage.planes[0].buffer
            val ySize = yBuffer.remaining()
            val yuvData = ByteArray(ySize)
            yBuffer.get(yuvData, 0, ySize)

            val source = PlanarYUVLuminanceSource(
                yuvData,
                mediaImage.width,
                mediaImage.height,
                0, 0,
                mediaImage.width,
                mediaImage.height,
                false,
            )

            val bitmap = BinaryBitmap(HybridBinarizer(source))
            try {
                val result = reader.decode(bitmap)
                log(TAG) { "QR code detected: ${result.text}" }
                onQrDetected(result.text)
            } catch (_: ReaderException) {
                // QR code not found in this frame
            }
        } catch (e: Exception) {
            log(TAG) { "Error analyzing image: ${e.message}" }
        } finally {
            imageProxy.close()
        }
    }
}
