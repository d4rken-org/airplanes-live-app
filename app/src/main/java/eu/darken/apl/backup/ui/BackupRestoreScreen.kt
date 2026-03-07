package eu.darken.apl.backup.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.CloudDownload
import androidx.compose.material.icons.twotone.CloudUpload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.apl.R
import eu.darken.apl.backup.core.BackupRepo
import eu.darken.apl.common.BuildConfigWrap
import eu.darken.apl.common.compose.aplContentWindowInsets
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.common.settings.SettingsPreferenceItem
import eu.darken.apl.common.settings.SettingsSwitchItem

@Composable
fun BackupRestoreScreenHost(
    vm: BackupRestoreViewModel = hiltViewModel(),
) {
    NavigationEventHandler(vm)
    ErrorEventHandler(vm)

    val state by vm.state.collectAsState()

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        uri?.let { vm.onBackupUriSelected(it) }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { vm.onRestoreUriSelected(it) }
    }

    BackupRestoreScreen(
        state = state,
        onBack = { vm.navUp() },
        onCreateBackup = {
            val date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
            val version = BuildConfigWrap.VERSION_NAME
            createDocumentLauncher.launch("airplanes-live_${version}_${date}.apl.zip")
        },
        onRestoreBackup = {
            openDocumentLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
        },
        onConfirmBackup = { vm.onConfirmBackup(it) },
        onConfirmRestore = { vm.onConfirmRestore(it) },
        onDismiss = { vm.onDismiss() },
    )
}

@Composable
fun BackupRestoreScreen(
    state: BackupRestoreViewModel.State,
    onBack: () -> Unit,
    onCreateBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onConfirmBackup: (BackupRepo.BackupOptions) -> Unit,
    onConfirmRestore: (BackupRepo.RestoreOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    state.progress?.let { progress ->
        BackupProgressScreen(step = progress.step)
        return
    }

    state.backupPreview?.let { preview ->
        BackupOptionsScreen(
            preview = preview,
            onConfirm = onConfirmBackup,
            onDismiss = onDismiss,
        )
        return
    }

    state.restorePreview?.let { preview ->
        RestoreOptionsScreen(
            preview = preview,
            onConfirm = onConfirmRestore,
            onDismiss = onDismiss,
        )
        return
    }

    state.result?.let { result ->
        BackupResultScreen(
            result = result,
            onDismiss = onDismiss,
        )
        return
    }

    Scaffold(
        contentWindowInsets = aplContentWindowInsets(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_restore_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
        ) {
            item {
                SettingsPreferenceItem(
                    title = stringResource(R.string.backup_create_action),
                    summary = stringResource(R.string.backup_restore_desc),
                    icon = Icons.TwoTone.CloudUpload,
                    onClick = onCreateBackup,
                )
            }
            item {
                SettingsPreferenceItem(
                    title = stringResource(R.string.backup_restore_action),
                    icon = Icons.TwoTone.CloudDownload,
                    onClick = onRestoreBackup,
                )
            }
            item {
                Text(
                    text = stringResource(R.string.backup_api_key_plaintext_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun BackupOptionsScreen(
    preview: BackupRepo.BackupPreview,
    onConfirm: (BackupRepo.BackupOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    val hasWatches = preview.watchCount > 0 || preview.checkCount > 0
    val hasFeeders = preview.feederCount > 0
    val hasAircraftCache = preview.aircraftCacheCount > 0
    var includeWatches by remember { mutableStateOf(hasWatches) }
    var includeFeeders by remember { mutableStateOf(hasFeeders) }
    var includeApiKey by remember { mutableStateOf(preview.hasApiKey) }
    var includeAircraftCache by remember { mutableStateOf(hasAircraftCache) }

    val nothingSelected = !includeWatches && !includeFeeders && !includeApiKey && !includeAircraftCache

    Scaffold(
        contentWindowInsets = aplContentWindowInsets(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_create_title)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
            ) {
                item {
                    SettingsSwitchItem(
                        title = stringResource(R.string.backup_category_watches),
                        summary = stringResource(
                            R.string.backup_watches_summary,
                            preview.watchCount,
                            preview.checkCount,
                        ),
                        checked = includeWatches,
                        enabled = hasWatches,
                        onCheckedChange = { includeWatches = it },
                    )
                }
                item {
                    SettingsSwitchItem(
                        title = stringResource(R.string.backup_category_feeders),
                        summary = stringResource(
                            R.string.backup_feeders_summary,
                            preview.feederCount,
                            preview.statsCount,
                        ),
                        checked = includeFeeders,
                        enabled = hasFeeders,
                        onCheckedChange = { includeFeeders = it },
                    )
                }
                item {
                    SettingsSwitchItem(
                        title = stringResource(R.string.backup_category_aircraft_cache),
                        summary = stringResource(
                            R.string.backup_aircraft_cache_summary,
                            preview.aircraftCacheCount,
                        ),
                        checked = includeAircraftCache,
                        enabled = hasAircraftCache,
                        onCheckedChange = { includeAircraftCache = it },
                    )
                }
                item {
                    SettingsSwitchItem(
                        title = stringResource(R.string.backup_category_api_key),
                        summary = if (preview.hasApiKey) {
                            stringResource(R.string.backup_api_key_included)
                        } else {
                            stringResource(R.string.backup_api_key_not_set)
                        },
                        checked = includeApiKey,
                        enabled = preview.hasApiKey,
                        onCheckedChange = { includeApiKey = it },
                    )
                }
            }
            Button(
                onClick = {
                    onConfirm(
                        BackupRepo.BackupOptions(
                            includeWatches = includeWatches,
                            includeFeeders = includeFeeders,
                            includeApiKey = includeApiKey,
                            includeAircraftCache = includeAircraftCache,
                        )
                    )
                },
                enabled = !nothingSelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(stringResource(R.string.backup_create_button))
            }
        }
    }
}

@Composable
private fun RestoreOptionsScreen(
    preview: BackupRepo.RestorePreview,
    onConfirm: (BackupRepo.RestoreOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    var includeWatches by remember { mutableStateOf(preview.watchCount > 0) }
    var includeFeeders by remember { mutableStateOf(preview.feederCount > 0) }
    var includeApiKey by remember { mutableStateOf(preview.hasApiKey) }
    var includeAircraftCache by remember { mutableStateOf(preview.aircraftCacheCount > 0) }

    val nothingSelected = !includeWatches && !includeFeeders && !includeApiKey && !includeAircraftCache

    Scaffold(
        contentWindowInsets = aplContentWindowInsets(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_restore_title_dialog)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
            ) {
                item {
                    val createdDate = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                        .format(preview.createdAt.atZone(ZoneId.systemDefault()))
                    Text(
                        text = stringResource(R.string.backup_created_at, createdDate),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }

                if (preview.versionMismatch) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.backup_version_mismatch_warning,
                                    preview.appVersion,
                                    BuildConfigWrap.VERSION_NAME,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }

                if (preview.watchCount > 0) {
                    item {
                        SettingsSwitchItem(
                            title = stringResource(R.string.backup_category_watches),
                            summary = stringResource(
                                R.string.backup_watches_summary,
                                preview.watchCount,
                                preview.checkCount,
                            ),
                            checked = includeWatches,
                            onCheckedChange = { includeWatches = it },
                        )
                    }
                }

                if (preview.feederCount > 0) {
                    item {
                        SettingsSwitchItem(
                            title = stringResource(R.string.backup_category_feeders),
                            summary = stringResource(
                                R.string.backup_feeders_summary,
                                preview.feederCount,
                                preview.statsCount,
                            ),
                            checked = includeFeeders,
                            onCheckedChange = { includeFeeders = it },
                        )
                    }
                }

                if (preview.aircraftCacheCount > 0) {
                    item {
                        SettingsSwitchItem(
                            title = stringResource(R.string.backup_category_aircraft_cache),
                            summary = stringResource(
                                R.string.backup_aircraft_cache_summary,
                                preview.aircraftCacheCount,
                            ),
                            checked = includeAircraftCache,
                            onCheckedChange = { includeAircraftCache = it },
                        )
                    }
                }

                if (preview.hasApiKey) {
                    item {
                        SettingsSwitchItem(
                            title = stringResource(R.string.backup_category_api_key),
                            summary = stringResource(R.string.backup_api_key_included),
                            checked = includeApiKey,
                            onCheckedChange = { includeApiKey = it },
                        )
                    }
                }
            }
            Button(
                onClick = {
                    onConfirm(
                        BackupRepo.RestoreOptions(
                            includeWatches = includeWatches,
                            includeFeeders = includeFeeders,
                            includeApiKey = includeApiKey,
                            includeAircraftCache = includeAircraftCache,
                        )
                    )
                },
                enabled = !nothingSelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(stringResource(R.string.backup_restore_button))
            }
        }
    }
}

@Composable
private fun BackupResultScreen(
    result: BackupRestoreViewModel.Result,
    onDismiss: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = aplContentWindowInsets(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_result_title)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
            ) {
                when (result) {
                    is BackupRestoreViewModel.Result.ExportSuccess -> {
                        item {
                            Text(
                                text = stringResource(R.string.backup_export_success),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }

                    is BackupRestoreViewModel.Result.ImportSuccess -> {
                        val r = result.result
                        if (r.watchesImported > 0 || r.watchesExisted > 0) {
                            item {
                                Text(
                                    text = stringResource(R.string.backup_import_watches_result, r.watchesImported, r.watchesExisted),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp),
                                )
                            }
                        }
                        if (r.checksImported > 0 || r.checksExisted > 0) {
                            item {
                                Text(
                                    text = stringResource(R.string.backup_import_checks_result, r.checksImported, r.checksExisted),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 32.dp, end = 16.dp, top = 2.dp, bottom = 4.dp),
                                )
                            }
                        }
                        if (r.feedersImported > 0 || r.feedersExisted > 0) {
                            item {
                                Text(
                                    text = stringResource(R.string.backup_import_feeders_result, r.feedersImported, r.feedersExisted),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp),
                                )
                            }
                        }
                        if (r.statsImported > 0) {
                            item {
                                Text(
                                    text = stringResource(R.string.backup_import_stats_result, r.statsImported),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 32.dp, end = 16.dp, top = 2.dp, bottom = 4.dp),
                                )
                            }
                        }
                        if (r.aircraftCacheImported > 0 || r.aircraftCacheExisted > 0) {
                            item {
                                Text(
                                    text = stringResource(R.string.backup_import_aircraft_cache_result, r.aircraftCacheImported, r.aircraftCacheExisted),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                        }
                        if (r.apiKeyImported) {
                            item {
                                Text(
                                    text = stringResource(R.string.backup_import_api_key_result),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                        }
                        r.errors.forEach { error ->
                            item {
                                Text(
                                    text = error,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.common_close_action))
                }
                if (result is BackupRestoreViewModel.Result.ExportSuccess) {
                    val context = LocalContext.current
                    Button(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/zip"
                                putExtra(Intent.EXTRA_STREAM, result.uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, null))
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.common_share_action))
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupProgressScreen(step: BackupRepo.BackupStep) {
    BackHandler {}

    val stepText = when (step) {
        BackupRepo.BackupStep.WATCHES -> stringResource(R.string.backup_progress_watches)
        BackupRepo.BackupStep.FEEDERS -> stringResource(R.string.backup_progress_feeders)
        BackupRepo.BackupStep.AIRCRAFT_CACHE -> stringResource(R.string.backup_progress_aircraft_cache)
        BackupRepo.BackupStep.API_KEY -> stringResource(R.string.backup_progress_api_key)
        BackupRepo.BackupStep.WRITING_FILE -> stringResource(R.string.backup_progress_writing_file)
        BackupRepo.BackupStep.READING_FILE -> stringResource(R.string.backup_progress_reading_file)
    }

    Scaffold(
        contentWindowInsets = aplContentWindowInsets(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_restore_title)) },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
            ) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stepText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
