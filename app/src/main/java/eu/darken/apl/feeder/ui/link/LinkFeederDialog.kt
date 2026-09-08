package eu.darken.apl.feeder.ui.link

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.apl.R
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.server.api.ServerCodes

@Composable
fun LinkFeederDialogHost(
    vm: LinkFeederViewModel = hiltViewModel(),
) {
    NavigationEventHandler(vm)
    ErrorEventHandler(vm)

    val state by vm.state.collectAsState()

    AlertDialog(
        onDismissRequest = { vm.navUp() },
        title = { Text(stringResource(R.string.feeder_link_title)) },
        text = {
            Column {
                Text(stringResource(R.string.feeder_link_msg))

                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = state.input,
                    onValueChange = vm::updateInput,
                    label = { Text(stringResource(R.string.feeder_link_id_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))
                TextButton(onClick = vm::detect, enabled = !state.isBusy) {
                    Text(stringResource(R.string.feeder_link_detect_action))
                }

                (state.detected + state.monitored).distinct().forEach { candidate ->
                    Text(
                        text = candidate,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.updateInput(candidate) }
                            .padding(vertical = 4.dp),
                    )
                }

                errorText(state)?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(text = message, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { vm.link(state.input) },
                enabled = state.input.isNotBlank() && !state.isBusy,
            ) {
                Text(stringResource(R.string.feeder_link_action))
            }
        },
        dismissButton = {
            TextButton(onClick = { vm.navUp() }) {
                Text(stringResource(R.string.common_cancel_action))
            }
        },
    )
}

@Composable
private fun errorText(state: LinkFeederViewModel.State): String? = when {
    state.failed -> stringResource(R.string.feeder_link_error_generic)
    state.errorCode == null -> null
    else -> when (state.errorCode) {
        ServerCodes.FEEDER_INACTIVE -> stringResource(R.string.feeder_link_error_inactive)
        ServerCodes.FEEDER_NETWORK_MISMATCH -> stringResource(R.string.feeder_link_error_network_mismatch)
        ServerCodes.FEEDER_VERIFICATION_UNAVAILABLE -> stringResource(R.string.feeder_link_error_unavailable)
        ServerCodes.FEEDER_VERIFICATION_ERROR -> stringResource(R.string.feeder_link_error_verification)
        ServerCodes.QUOTA_EXCEEDED -> when (val retryAfter = state.retryAfterSeconds) {
            null -> stringResource(R.string.feeder_link_error_quota)
            else -> stringResource(
                R.string.feeder_link_error_quota_x,
                DateUtils.formatElapsedTime(retryAfter),
            )
        }
        ServerCodes.INVALID_REQUEST -> stringResource(R.string.feeder_link_error_invalid)
        else -> stringResource(R.string.feeder_link_error_generic)
    }
}
