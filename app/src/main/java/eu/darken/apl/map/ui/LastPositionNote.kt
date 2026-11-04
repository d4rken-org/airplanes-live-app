package eu.darken.apl.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import eu.darken.apl.R
import eu.darken.apl.common.compose.rememberServerNow
import eu.darken.apl.common.compose.seenAgeText

/**
 * Server time of the selection's last position while the sheet says how old it is in place of the age pill, i.e.
 * while live answers leave the aircraft out. With [partialDetails], an [unavailable] map explains more.
 */
internal fun lastPositionNoteAt(
    inHistory: Boolean,
    selectedMissing: Boolean,
    seen: NativeMapViewModel.Seen?,
    partialDetails: Boolean,
    unavailable: Boolean,
): Long? = when {
    inHistory || !selectedMissing -> null
    partialDetails && unavailable -> null
    else -> (seen as? NativeMapViewModel.Seen.At)?.millis
}

/** "Last position 7 min. ago", counting on like the age pill. */
@Composable
internal fun lastPositionNote(seenAt: Long, serverNow: () -> Long): String {
    val now = rememberServerNow(seenAt, serverNow)
    return stringResource(R.string.map_aircraft_details_last_position, seenAgeText(seenAt, now))
}
