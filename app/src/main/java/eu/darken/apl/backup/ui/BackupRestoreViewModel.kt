package eu.darken.apl.backup.ui

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.backup.core.BackupRepo
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.feeder.core.link.FeederLinkRepo
import eu.darken.apl.feeder.core.link.FeederLinkRepo.FeederLinkState
import eu.darken.apl.upgrade.UpgradeRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val backupRepo: BackupRepo,
    private val feederLinkRepo: FeederLinkRepo,
    private val upgradeRepo: UpgradeRepo,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Backup", "Restore", "VM"),
) {

    data class Progress(val step: BackupRepo.BackupStep)

    data class State(
        val progress: Progress? = null,
        val backupPreview: BackupRepo.BackupPreview? = null,
        val backupUri: Uri? = null,
        val restorePreview: BackupRepo.RestorePreview? = null,
        val result: Result? = null,
        val feederAccessMissing: Boolean = false,
    )

    sealed interface Result {
        data class ExportSuccess(val uri: Uri) : Result
        data class ImportSuccess(val result: BackupRepo.RestoreResult) : Result
    }

    val state = MutableStateFlow(State())

    init {
        // Collected, not read once: either can change while the result is shown
        combine(feederLinkRepo.state, upgradeRepo.upgradeInfo) { link, upgrade ->
            link is FeederLinkState.Unlinked && !upgrade.isPro
        }
            .onEach { missing -> state.update { it.copy(feederAccessMissing = missing) } }
            .launchInViewModel()
    }

    fun onBackupUriSelected(uri: Uri) = launch {
        log(tag) { "onBackupUriSelected($uri)" }
        state.update { it.copy(progress = Progress(BackupRepo.BackupStep.READING_FILE)) }
        try {
            val preview = backupRepo.getBackupPreview()
            state.update {
                it.copy(
                    progress = null,
                    backupPreview = preview,
                    backupUri = uri,
                )
            }
        } catch (e: Exception) {
            state.update { it.copy(progress = null) }
            throw e
        }
    }

    fun onConfirmBackup(options: BackupRepo.BackupOptions) = launch {
        val uri = state.value.backupUri ?: return@launch
        log(tag) { "onConfirmBackup($options)" }
        state.update { it.copy(progress = Progress(BackupRepo.BackupStep.WATCHES), backupPreview = null) }
        try {
            backupRepo.createBackup(uri, options) { step ->
                state.update { it.copy(progress = Progress(step)) }
            }
            state.update {
                it.copy(
                    progress = null,
                    result = Result.ExportSuccess(uri),
                )
            }
        } catch (e: Exception) {
            state.update { it.copy(progress = null) }
            throw e
        }
    }

    fun onRestoreUriSelected(uri: Uri) = launch {
        log(tag) { "onRestoreUriSelected($uri)" }
        state.update { it.copy(progress = Progress(BackupRepo.BackupStep.READING_FILE)) }
        try {
            val preview = backupRepo.readBackup(uri)
            state.update {
                it.copy(
                    progress = null,
                    restorePreview = preview,
                )
            }
        } catch (e: Exception) {
            state.update { it.copy(progress = null) }
            throw e
        }
    }

    fun onConfirmRestore(options: BackupRepo.RestoreOptions) = launch {
        log(tag) { "onConfirmRestore($options)" }
        state.update { it.copy(progress = Progress(BackupRepo.BackupStep.WATCHES), restorePreview = null) }
        try {
            val result = backupRepo.restoreBackup(options) { step ->
                state.update { it.copy(progress = Progress(step)) }
            }
            state.update {
                it.copy(
                    progress = null,
                    result = Result.ImportSuccess(result),
                )
            }
        } catch (e: Exception) {
            state.update { it.copy(progress = null) }
            throw e
        }
    }

    fun onDismiss() {
        state.update {
            it.copy(
                backupPreview = null,
                backupUri = null,
                restorePreview = null,
                result = null,
            )
        }
        backupRepo.clearCache()
    }
}
