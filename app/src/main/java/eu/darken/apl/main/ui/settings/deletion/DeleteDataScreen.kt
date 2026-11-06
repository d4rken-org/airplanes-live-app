package eu.darken.apl.main.ui.settings.deletion

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.apl.R
import eu.darken.apl.common.compose.Preview2
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.compose.aplContentWindowInsets
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.main.ui.settings.deletion.DeleteDataViewModel.State

@Composable
fun DeleteDataScreenHost(
    vm: DeleteDataViewModel = hiltViewModel(),
) {
    NavigationEventHandler(vm)
    ErrorEventHandler(vm)

    val state by vm.state.collectAsStateWithLifecycle()

    DeleteDataScreen(
        state = state ?: return,
        onBack = { vm.navUp() },
        onDelete = { vm.delete() },
        onRetry = { vm.delete() },
        onCancelPending = { vm.cancelPending() },
        onEmail = { vm.sendEmail() },
    )
}

@Composable
fun DeleteDataScreen(
    state: State,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    onCancelPending: () -> Unit,
    onEmail: () -> Unit,
) {
    val isRunning = state == State.Running
    var showConfirmDialog by remember { mutableStateOf(false) }

    // Leaving mid-deletion would hide whether it worked
    BackHandler(enabled = isRunning) {}

    Scaffold(
        contentWindowInsets = aplContentWindowInsets(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_delete_data_label)) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isRunning) {
                        Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = stringResource(R.string.common_back_action))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_delete_data_explanation_intro),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.settings_delete_data_explanation_server),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.settings_delete_data_explanation_device),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.settings_delete_data_explanation_final),
                style = MaterialTheme.typography.bodyLarge,
            )

            when (state) {
                State.Idle -> Unit

                State.Running -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(R.string.settings_delete_data_running_msg),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }

                State.Unfinished -> StatusCard {
                    Text(
                        text = stringResource(R.string.settings_delete_data_unfinished_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(stringResource(R.string.settings_delete_data_unfinished_msg))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        TextButton(onClick = onCancelPending) {
                            Text(stringResource(R.string.common_cancel_action))
                        }
                        Button(onClick = onRetry) {
                            Text(stringResource(R.string.settings_delete_data_retry_action))
                        }
                    }
                }

                is State.NotConfirmed -> StatusCard {
                    Text(
                        stringResource(
                            if (state.tryLater) R.string.settings_delete_data_not_confirmed_later_msg
                            else R.string.settings_delete_data_not_confirmed_msg
                        )
                    )
                    RetryButton(onRetry)
                }

                is State.Refused -> StatusCard {
                    val installationId = state.installationId
                    Text(
                        stringResource(
                            if (installationId != null) R.string.settings_delete_data_refused_msg
                            else R.string.settings_delete_data_refused_no_id_msg,
                            DeleteDataViewModel.SUPPORT_EMAIL,
                        )
                    )
                    if (installationId != null) {
                        Text(
                            text = stringResource(R.string.settings_delete_data_installation_id_label),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        SelectionContainer {
                            Text(
                                text = installationId,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    Button(
                        onClick = onEmail,
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(stringResource(R.string.settings_delete_data_email_action))
                    }
                }

                State.UpdateRequired -> StatusCard {
                    Text(stringResource(R.string.settings_delete_data_update_required_msg))
                }

                State.WipeFailed -> StatusCard {
                    Text(stringResource(R.string.settings_delete_data_wipe_failed_msg))
                    RetryButton(onRetry)
                }
            }

            if (state == State.Idle || state == State.Running) {
                Button(
                    onClick = { showConfirmDialog = true },
                    enabled = !isRunning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_delete_data_action))
                }
            }
        }
    }

    if (showConfirmDialog && state == State.Idle) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(stringResource(R.string.settings_delete_data_confirm_title)) },
            text = { Text(stringResource(R.string.settings_delete_data_confirm_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    onDelete()
                }) {
                    Text(
                        text = stringResource(R.string.settings_delete_data_confirm_action),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text(stringResource(R.string.common_cancel_action))
                }
            },
        )
    }
}

@Composable
private fun StatusCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun ColumnScope.RetryButton(onRetry: () -> Unit) {
    Button(
        onClick = onRetry,
        modifier = Modifier.align(Alignment.End),
    ) {
        Text(stringResource(R.string.settings_delete_data_retry_action))
    }
}

@Preview2
@Composable
private fun DeleteDataScreenIdlePreview() {
    PreviewWrapper {
        DeleteDataScreen(
            state = State.Idle,
            onBack = {},
            onDelete = {},
            onRetry = {},
            onCancelPending = {},
            onEmail = {},
        )
    }
}

@Preview2
@Composable
private fun DeleteDataScreenRefusedPreview() {
    PreviewWrapper {
        DeleteDataScreen(
            state = State.Refused(installationId = "0f3c9a5e-6b1d-4c8e-9a2f-1d2e3f4a5b6c"),
            onBack = {},
            onDelete = {},
            onRetry = {},
            onCancelPending = {},
            onEmail = {},
        )
    }
}
