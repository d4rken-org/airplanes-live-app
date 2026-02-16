package eu.darken.apl.watch.ui.create

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.apl.R
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.navigation.NavigationEventHandler

@Composable
fun CreateSquawkWatchDialogHost(
    squawk: String? = null,
    note: String? = null,
    vm: CreateSquawkWatchViewModel = hiltViewModel(),
) {
    NavigationEventHandler(vm)
    ErrorEventHandler(vm)

    var inputValue by remember { mutableStateOf(squawk ?: "") }
    var commentValue by remember { mutableStateOf(note ?: "") }
    val isValid = inputValue.length == 4

    AlertDialog(
        onDismissRequest = { vm.navUp() },
        title = { Text(stringResource(R.string.watch_list_add_squawk_title)) },
        text = {
            Column {
                Text(stringResource(R.string.watch_list_add_squawk_msg))
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = inputValue,
                    onValueChange = { inputValue = it },
                    label = { Text(stringResource(R.string.watch_list_add_watch_type_label_squawk)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = commentValue,
                    onValueChange = { commentValue = it },
                    label = { Text(stringResource(R.string.watchlist_note_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { vm.create(inputValue, commentValue) },
                enabled = isValid,
            ) {
                Text(stringResource(R.string.common_add_action))
            }
        },
        dismissButton = {
            TextButton(onClick = { vm.navUp() }) {
                Text(stringResource(R.string.common_cancel_action))
            }
        },
    )
}
