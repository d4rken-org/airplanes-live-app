package eu.darken.apl.backup.ui

import android.content.Context
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import eu.darken.apl.R
import eu.darken.apl.backup.core.BackupRepo
import eu.darken.apl.common.compose.PreviewWrapper
import org.junit.Test
import testhelper.BaseComposeTest
import java.time.Instant

class BackupRestoreScreenTest : BaseComposeTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `a backup with neither watches nor feeders can not be restored`() {
        val preview = BackupRepo.RestorePreview(
            appVersion = "0.8.0",
            appVersionCode = 80000,
            createdAt = Instant.parse("2026-03-06T12:00:00Z"),
            watchCount = 0,
            feederCount = 0,
            versionMismatch = false,
        )
        composeRule.setContent {
            PreviewWrapper {
                BackupRestoreScreen(
                    state = BackupRestoreViewModel.State(restorePreview = preview),
                    onBack = {},
                    onCreateBackup = {},
                    onRestoreBackup = {},
                    onConfirmBackup = {},
                    onConfirmRestore = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.backup_restore_button)).assertIsNotEnabled()
    }

    private fun setRestoredFeedersScreen(subscriptionSupported: Boolean) = composeRule.setContent {
        PreviewWrapper {
            BackupRestoreScreen(
                state = BackupRestoreViewModel.State(
                    result = BackupRestoreViewModel.Result.ImportSuccess(BackupRepo.RestoreResult(feedersImported = 2)),
                    feederAccessMissing = true,
                    subscriptionSupported = subscriptionSupported,
                ),
                onBack = {},
                onCreateBackup = {},
                onRestoreBackup = {},
                onConfirmBackup = {},
                onConfirmRestore = {},
                onDismiss = {},
            )
        }
    }

    @Test
    fun `with subscriptions the restore hint points at Restore and linking`() {
        setRestoredFeedersScreen(subscriptionSupported = true)

        composeRule.onNodeWithText(context.getString(R.string.backup_import_access_hint_gplay)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.backup_import_feeder_access_hint)).assertDoesNotExist()
    }

    @Test
    fun `without subscriptions the restore hint only points at linking`() {
        setRestoredFeedersScreen(subscriptionSupported = false)

        composeRule.onNodeWithText(context.getString(R.string.backup_import_feeder_access_hint)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.backup_import_access_hint_gplay)).assertDoesNotExist()
    }
}
