package eu.darken.apl.feeder.ui.actions

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import eu.darken.apl.R
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.feeder.ui.add.NewFeederQR
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

@Composable
fun FeederActionSheetHost(
    receiverId: String,
    vm: FeederActionViewModel = hiltViewModel(),
) {
    val json = remember { Json { ignoreUnknownKeys = true } }
    val context = LocalContext.current
    NavigationEventHandler(vm)
    ErrorEventHandler(vm)

    LaunchedEffect(receiverId) {
        vm.init(receiverId)
    }

    val state by vm.state.collectAsState(initial = null)
    val feeder = state?.feeder ?: return

    var showRenameDialog by remember { mutableStateOf(false) }
    var showAddressDialog by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf<NewFeederQR?>(null) }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is FeederActionEvents.Rename -> showRenameDialog = true
                is FeederActionEvents.ChangeIpAddress -> showAddressDialog = true
                is FeederActionEvents.RemovalConfirmation -> showRemoveDialog = true
                is FeederActionEvents.ShowQrCode -> showQrDialog = event.qr
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    ModalBottomSheet(onDismissRequest = { vm.navUp() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings_input_antenna_24),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                ) {
                    Text(text = feeder.label, style = MaterialTheme.typography.bodyLarge)
                    Text(text = feeder.id, style = MaterialTheme.typography.labelSmall)
                    Text(
                        text = "Host-address: ${feeder.config.address ?: "\uD83C\uDF7B"}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                IconButton(onClick = { vm.generateQrCode() }) {
                    Icon(painterResource(R.drawable.ic_qr_code_24), contentDescription = stringResource(R.string.feeder_generate_qr_action))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // Actions
            Button(
                onClick = { vm.showFeedOnMap() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.feeder_show_map_action))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { vm.renameFeeder() },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.feeder_name_change_action))
                }
                Button(
                    onClick = { vm.changeAddress() },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.feeder_address_change_action))
                }
            }

            if (feeder.config.address != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { vm.openTar1090() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("TAR1090")
                    }
                    OutlinedButton(
                        onClick = { vm.openGraphs1090() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("GRAPHS1090")
                    }
                }
            }

            Button(
                onClick = { vm.removeFeeder() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Text(stringResource(R.string.feeder_remove_action))
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.feeder_options_label),
                style = MaterialTheme.typography.labelLarge,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.feeder_monitor_offline_label),
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = feeder.config.offlineCheckTimeout != null,
                    onCheckedChange = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        vm.toggleNotifyWhenOffline()
                    },
                )
            }
        }
    }

    // Rename dialog
    if (showRenameDialog) {
        var renameInput by remember { mutableStateOf(feeder.label) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(stringResource(R.string.feeder_name_change_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.feeder_name_change_body))
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = { renameInput = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.renameFeeder(renameInput)
                    showRenameDialog = false
                }) {
                    Text(stringResource(R.string.feeder_name_change_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(stringResource(R.string.common_cancel_action))
                }
            },
        )
    }

    // Address dialog
    if (showAddressDialog) {
        var addressInput by remember { mutableStateOf(feeder.config.address ?: "") }
        AlertDialog(
            onDismissRequest = { showAddressDialog = false },
            title = { Text(stringResource(R.string.feeder_address_change_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.feeder_address_change_body))
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = addressInput,
                        onValueChange = { addressInput = it },
                        placeholder = { Text("192.168.0.42/myfeeder.domain.tld") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.changeAddress(addressInput)
                    showAddressDialog = false
                }) {
                    Text(stringResource(R.string.feeder_address_change_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddressDialog = false }) {
                    Text(stringResource(R.string.common_cancel_action))
                }
            },
        )
    }

    // Remove confirmation dialog
    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text(stringResource(R.string.feeder_remove_confirmation_title)) },
            text = { Text(stringResource(R.string.feeder_remove_confirmation_message)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeFeeder(confirmed = true)
                    showRemoveDialog = false
                }) {
                    Text(stringResource(R.string.common_remove_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) {
                    Text(stringResource(R.string.common_cancel_action))
                }
            },
        )
    }

    // QR Code dialog
    showQrDialog?.let { qr ->
        QrCodeDialog(
            qr = qr,
            json = json,
            onDismiss = { showQrDialog = null },
        )
    }
}

@Composable
private fun QrCodeDialog(
    qr: NewFeederQR,
    json: Json,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val bitmap = remember(qr) {
        try {
            val qrCodeText = qr.toUri(json).toString()
            val bitMatrix = MultiFormatWriter().encode(qrCodeText, BarcodeFormat.QR_CODE, 512, 512)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bmp = createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bmp[x, y] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                }
            }
            bmp
        } catch (e: Exception) {
            null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "QR Code",
                        modifier = Modifier.size(256.dp),
                    )
                } ?: Text(stringResource(R.string.common_error_label))
            }
        },
        confirmButton = {
            if (bitmap != null) {
                TextButton(onClick = {
                    shareQrCode(context, bitmap, qr)
                }) {
                    Text(stringResource(R.string.common_share_action))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close_action))
            }
        },
    )
}

private fun shareQrCode(context: android.content.Context, bitmap: Bitmap, qr: NewFeederQR) {
    try {
        val cachePath = File(context.cacheDir, "images")
        cachePath.mkdirs()
        val fileName = "feeder_qr_${qr.receiverId}_${System.currentTimeMillis()}.png"
        val file = File(cachePath, fileName)
        FileOutputStream(file).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        val contentUri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_TEXT, "Feeder QR Code: ${qr.receiverLabel ?: qr.receiverId}")
            type = "image/png"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.common_share_action)))
    } catch (_: Exception) {
    }
}
