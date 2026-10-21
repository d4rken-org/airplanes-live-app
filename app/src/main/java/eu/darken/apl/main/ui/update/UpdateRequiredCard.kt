package eu.darken.apl.main.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.apl.R
import eu.darken.apl.common.compose.Preview2
import eu.darken.apl.common.compose.PreviewWrapper

/** What a screen needs to show [UpdateRequiredCard], see [rememberUpdateRequiredNotice]. */
data class UpdateRequiredNotice(
    val isChecking: Boolean,
    val onUpdate: () -> Unit,
    val onCheckAgain: () -> Unit,
)

/** Null unless the server refuses this version of the app. */
@Composable
fun rememberUpdateRequiredNotice(
    vm: UpdateRequiredViewModel = hiltViewModel(),
): UpdateRequiredNotice? {
    val state by vm.state.collectAsStateWithLifecycle()
    return state?.let {
        UpdateRequiredNotice(
            isChecking = it.isChecking,
            onUpdate = vm::update,
            onCheckAgain = vm::checkAgain,
        )
    }
}

@Composable
fun UpdateRequiredCard(
    notice: UpdateRequiredNotice,
    modifier: Modifier = Modifier,
) = UpdateRequiredCard(
    isChecking = notice.isChecking,
    onUpdate = notice.onUpdate,
    onCheckAgain = notice.onCheckAgain,
    modifier = modifier,
)

@Composable
fun UpdateRequiredCard(
    isChecking: Boolean,
    onUpdate: () -> Unit,
    onCheckAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Opaque and raised, it also sits on the map and the camera image
    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.update_required_inline_msg),
                style = MaterialTheme.typography.bodyMedium,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                OutlinedButton(
                    onClick = onCheckAgain,
                    enabled = !isChecking,
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.update_prompt_check_again_action))
                }
                Button(onClick = onUpdate) {
                    Text(stringResource(R.string.update_prompt_update_action))
                }
            }
        }
    }
}

@Preview2
@Composable
private fun UpdateRequiredCardPreview() {
    PreviewWrapper {
        UpdateRequiredCard(
            isChecking = false,
            onUpdate = {},
            onCheckAgain = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview2
@Composable
private fun UpdateRequiredCardCheckingPreview() {
    PreviewWrapper {
        UpdateRequiredCard(
            isChecking = true,
            onUpdate = {},
            onCheckAgain = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
