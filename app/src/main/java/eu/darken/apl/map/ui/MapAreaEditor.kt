package eu.darken.apl.map.ui

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.apl.R
import eu.darken.apl.watch.ui.LocationRadiusPicker

/** Sets up a location watch for the area drawn on the map, the map stays usable around it. */
@Composable
internal fun MapAreaEditor(
    draft: NativeMapViewModel.AreaDraft,
    stops: List<Float>,
    radiusInMeters: Float?,
    maxRadiusKm: Int,
    onLabelChange: (String) -> Unit,
    onRadiusChange: (Float) -> Unit,
    onCancel: () -> Unit,
    onCreate: (radiusInMeters: Float) -> Unit,
    /** Deletes the edited watch, after asking. */
    onDelete: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val canCreate = !draft.saving && draft.label.isNotBlank() &&
            radiusInMeters != null && draft.allowsSaving(radiusInMeters, maxRadiusKm)
    Surface(
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        modifier = modifier,
    ) {
        // Above the keyboard on a short screen it can be taller than the room left
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 8.dp)
        ) {
            Text(
                text = stringResource(if (draft.editing != null) R.string.map_area_watch_edit_title else R.string.map_area_watch_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = draft.label,
                onValueChange = onLabelChange,
                label = { Text(stringResource(R.string.watch_list_add_location_label_hint)) },
                trailingIcon = if (draft.resolving) {
                    { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
                } else {
                    null
                },
                enabled = !draft.saving,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            if (radiusInMeters != null) {
                LocationRadiusPicker(
                    stops = stops,
                    radiusInMeters = radiusInMeters,
                    onRadiusChange = onRadiusChange,
                    enabled = !draft.saving,
                )
            }
            if (draft.resetsHistory(radiusInMeters)) {
                Text(
                    text = stringResource(R.string.map_area_watch_reset_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (draft.editing != null) {
                    TextButton(onClick = { confirmDelete = true }, enabled = !draft.saving) {
                        Text(stringResource(R.string.common_remove_action), color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.weight(1f))
                }
                TextButton(onClick = onCancel, enabled = !draft.saving) {
                    Text(stringResource(R.string.common_cancel_action))
                }
                TextButton(
                    onClick = { radiusInMeters?.let(onCreate) },
                    enabled = canCreate,
                ) {
                    Text(stringResource(if (draft.editing != null) R.string.common_save_action else R.string.common_add_action))
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.watch_list_remove_confirmation_title)) },
            text = { Text(stringResource(R.string.watch_list_remove_confirmation_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) {
                    Text(stringResource(R.string.common_remove_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.common_cancel_action))
                }
            },
        )
    }
}

/**
 * While shown, the window stays put when the keyboard opens: panning it only uncovers the focused
 * field, the editor lifts itself above the keyboard instead.
 */
@Composable
internal fun HoldWindowForKeyboard() {
    val window = (LocalContext.current as? Activity)?.window ?: return
    DisposableEffect(window) {
        val previous = window.attributes.softInputMode
        // Before R the edge-to-edge content only receives the keyboard's inset with adjustResize
        val adjust = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        window.setSoftInputMode((previous and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or adjust)
        onDispose { window.setSoftInputMode(previous) }
    }
}
