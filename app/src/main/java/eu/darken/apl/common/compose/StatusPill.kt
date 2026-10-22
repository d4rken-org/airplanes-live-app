package eu.darken.apl.common.compose

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.apl.R
import kotlinx.coroutines.delay

/** An aircraft's or watch's state, [highlighted] while it is current, otherwise e.g. how long ago it was seen. */
@Composable
fun StatusPill(
    text: String,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
    description: String = text,
) {
    // Not a Surface, which would take the taps meant for a photo below
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clearAndSetSemantics { contentDescription = description }
            .background(
                color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(50),
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/**
 * "Live" while [isLive] holds, otherwise how long ago [seenAt] was, or that it is unknown. Both are server time,
 * and the age counts on by itself.
 */
@Composable
fun SeenPill(
    seenAt: Long?,
    serverNow: () -> Long,
    modifier: Modifier = Modifier,
    isLive: (now: Long) -> Boolean,
) {
    val now by produceState(serverNow(), seenAt) {
        if (seenAt == null) return@produceState
        while (true) {
            value = serverNow()
            // Seconds while they are shown, minutes after
            delay(if (value - seenAt < 60_000L) 1_000L else 15_000L)
        }
    }
    when {
        isLive(now) -> StatusPill(
            text = stringResource(R.string.common_live_label),
            highlighted = true,
            modifier = modifier,
        )
        seenAt == null -> StatusPill(
            text = stringResource(R.string.aircraft_details_last_seen_unknown),
            highlighted = false,
            modifier = modifier,
        )

        else -> {
            val age = DateUtils.getRelativeTimeSpanString(
                seenAt,
                now.coerceAtLeast(seenAt),
                DateUtils.SECOND_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE,
            ).toString()
            StatusPill(
                text = age,
                highlighted = false,
                modifier = modifier,
                description = stringResource(R.string.search_aircraft_last_seen_description, age),
            )
        }
    }
}

@Preview2
@Composable
private fun StatusPillPreview() {
    PreviewWrapper { StatusPill(text = "Live", highlighted = true) }
}

@Preview2
@Composable
private fun StatusPillAgedPreview() {
    PreviewWrapper { StatusPill(text = "7 min. ago", highlighted = false) }
}
