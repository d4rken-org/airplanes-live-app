package eu.darken.apl.ar.ui

import android.Manifest
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.HelpOutline
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.GpsFixed
import androidx.compose.material.icons.twotone.GpsOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import eu.darken.apl.R
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.ar.core.ArSettings
import eu.darken.apl.common.compose.Mascot
import eu.darken.apl.common.compose.Preview2
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.navigation.NavigationEventHandler
import kotlin.math.roundToInt

private val TAG = logTag("AR", "Screen")

@Composable
fun ArScreenHost(
    vm: ArViewModel = hiltViewModel(),
) {
    NavigationEventHandler(vm)
    ErrorEventHandler(vm)

    val context = LocalContext.current

    // Lock to portrait while AR is active
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val previousOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val display = activity?.display
        if (display != null) {
            vm.onDisplayRotationChanged(display.rotation)
        }
        onDispose {
            if (previousOrientation != null) {
                activity.requestedOrientation = previousOrientation
            }
        }
    }

    val state by vm.state.collectAsState()

    var permissionsGranted by remember {
        val hasCam = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        val hasLoc = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        mutableStateOf(hasCam && hasLoc)
    }

    LaunchedEffect(permissionsGranted) {
        if (permissionsGranted) vm.onPermissionsGranted()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.isNotEmpty() && results.values.all { it }
    }

    if (!permissionsGranted) {
        ArPermissionScreen(
            onRequestPermissions = {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    )
                )
            },
            onClose = { vm.onClose() },
        )
    } else {
        ArScreen(
            state = state,
            onAircraftTapped = { vm.onAircraftTapped(it) },
            onRangeChanged = { vm.onDisplayRangeChanged(it) },
            onClose = { vm.onClose() },
        )
    }
}

@Composable
private fun ArScreen(
    state: ArViewModel.State,
    onAircraftTapped: (String) -> Unit,
    onRangeChanged: (Int) -> Unit,
    onClose: () -> Unit,
) {
    var showCalibrationHelp by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(state.displayRangeNm.toFloat()) }
    LaunchedEffect(state.displayRangeNm) { sliderValue = state.displayRangeNm.toFloat() }

    Box(modifier = Modifier.fillMaxSize()) {
        ArCameraPreview(modifier = Modifier.fillMaxSize())

        // Label overlay
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val maxWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
            val maxHeightPx = with(LocalDensity.current) { maxHeight.toPx() }

            state.labels.forEach { label ->
                val offsetX = (label.screenXNorm * maxWidthPx).toInt()
                val offsetY = (label.screenYNorm * maxHeightPx).toInt()

                ArLabelCard(
                    label = label,
                    onTap = { onAircraftTapped(label.hex) },
                    modifier = Modifier.offset { IntOffset(offsetX - 80, offsetY - 30) },
                )
            }

            state.groundEasterEgg?.let { egg ->
                val eggX = (egg.screenXNorm * maxWidthPx).toInt()
                val eggY = (egg.screenYNorm * maxHeightPx).toInt()

                Box(
                    modifier = Modifier
                        .offset { IntOffset(eggX - 100, eggY - 16) }
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = egg.text,
                        color = Color.White,
                        fontSize = 14.sp,
                    )
                }
            }
        }

        // Top center: app title (vertically aligned with close/help buttons)
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Mascot(
                size = 44.dp,
                colorFilter = ColorFilter.tint(Color.White),
            )
            Text(
                text = stringResource(R.string.app_name),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        // Top-start: close button
        FilledTonalIconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(16.dp)
                .size(48.dp),
        ) {
            Icon(
                Icons.TwoTone.Close,
                contentDescription = stringResource(R.string.ar_close_action),
            )
        }

        // Top-end: help button
        FilledTonalIconButton(
            onClick = { showCalibrationHelp = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(16.dp)
                .size(48.dp),
        ) {
            Icon(
                Icons.AutoMirrored.TwoTone.HelpOutline,
                contentDescription = stringResource(R.string.ar_help_action),
            )
        }

        // Bottom-start: vertical range slider
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "${sliderValue.roundToInt()}",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { onRangeChanged(sliderValue.roundToInt()) },
                valueRange = ArSettings.MIN_RANGE_NM.toFloat()..ArSettings.MAX_RANGE_NM.toFloat(),
                modifier = Modifier
                    .height(180.dp)
                    .verticalSlider(),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                ),
            )
            Text(
                text = "NM",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 10.sp,
            )
        }

        // Bottom center: status and compass heading
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (!state.sensorAvailable) {
                StatusChip(text = stringResource(R.string.ar_sensor_unavailable))
            }
            if (!state.locationAvailable) {
                StatusChip(text = stringResource(R.string.ar_waiting_gps))
            }
            if (state.locationAvailable) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (state.isGpsAccurate) Icons.TwoTone.GpsFixed else Icons.TwoTone.GpsOff,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.ar_nearby_count, state.totalNearbyCount),
                        color = Color.White,
                        fontSize = 14.sp,
                    )
                }
            }
            ArCompassBar(headingDeg = state.compassHeadingDeg)
        }

        if (showCalibrationHelp) {
            ArCalibrationDialog(onDismiss = { showCalibrationHelp = false })
        }
    }
}

private fun Modifier.verticalSlider() = this
    .layout { measurable, constraints ->
        val placeable = measurable.measure(
            Constraints(
                minWidth = constraints.minHeight,
                maxWidth = constraints.maxHeight,
                minHeight = constraints.minWidth,
                maxHeight = constraints.maxWidth,
            )
        )
        layout(placeable.height, placeable.width) {
            placeable.place(
                x = (placeable.height - placeable.width) / 2,
                y = (placeable.width - placeable.height) / 2,
            )
        }
    }
    .rotate(-90f)

@Composable
private fun ArCameraPreview(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        var cameraProvider: ProcessCameraProvider? = null

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                )
            } catch (e: Exception) {
                log(TAG) { "Camera initialization failed: $e" }
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            cameraProvider?.unbindAll()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier,
    )
}

@Composable
private fun ArCompassBar(
    headingDeg: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(
            text = "%03.0f\u00B0".format(headingDeg),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun StatusChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun ArPermissionScreen(
    onRequestPermissions: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_ar_glasses_24),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.ar_permission_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.ar_permission_message),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onRequestPermissions) {
                Text(stringResource(R.string.common_grant_permission_action))
            }
            TextButton(onClick = onClose) {
                Text(stringResource(R.string.common_close_action))
            }
        }
        }
    }
}

@Preview2
@Composable
private fun ArScreenPreview() {
    PreviewWrapper {
        ArScreen(
            state = ArViewModel.State(
                labels = listOf(
                    ArLabel(
                        hex = "4CA2B1", callsign = "RYR9313", registration = "EI-ABC",
                        description = "BOEING 737-800", altitudeFt = 21100,
                        speedKts = 386f, distanceM = 25000.0,
                        screenXNorm = 0.3f, screenYNorm = 0.3f,
                    ),
                    ArLabel(
                        hex = "3C6745", callsign = "DLH1A", registration = "D-AIMG",
                        description = "AIRBUS A-380-800", altitudeFt = 38000,
                        speedKts = 451f, distanceM = 48000.0,
                        screenXNorm = 0.6f, screenYNorm = 0.5f,
                    ),
                ),
                compassHeadingDeg = 288f,
                locationAvailable = true,
                isGpsAccurate = true,
                sensorAvailable = true,
                totalNearbyCount = 26,
                isLoading = false,
                displayRangeNm = 50,
            ),
            onAircraftTapped = {},
            onRangeChanged = {},
            onClose = {},
        )
    }
}

@Preview2
@Composable
private fun ArCompassBarPreview() {
    PreviewWrapper {
        ArCompassBar(headingDeg = 288f)
    }
}

@Preview2
@Composable
private fun ArPermissionScreenPreview() {
    PreviewWrapper {
        ArPermissionScreen(onRequestPermissions = {}, onClose = {})
    }
}
