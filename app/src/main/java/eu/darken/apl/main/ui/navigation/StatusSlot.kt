package eu.darken.apl.main.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.darken.apl.R
import eu.darken.apl.server.status.PlannedMaintenance
import eu.darken.apl.server.status.ServerStatus
import eu.darken.apl.server.status.serverStatusText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** [now] is server-aligned time, [onDismiss] takes a [PlannedMaintenance.id]. */
class StatusSlotActions(
    val now: () -> Instant,
    val onDismiss: (String) -> Unit,
)

val LocalStatusSlotState = compositionLocalOf<StatusSlotState> { StatusSlotState.Hidden }
val LocalStatusSlotActions = staticCompositionLocalOf { StatusSlotActions(now = Instant::now, onDismiss = {}) }

@Composable
fun StatusSlot(
    state: StatusSlotState,
    isInternetAvailable: Boolean,
    now: Instant,
    onDismiss: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    AnimatedContent(
        targetState = state.shownWhen(isInternetAvailable),
        modifier = modifier.fillMaxWidth(),
        transitionSpec = { (fadeIn() togetherWith fadeOut()).using(SizeTransform(clip = true)) },
    ) { shown ->
        when (shown) {
            StatusSlotState.Hidden -> Unit
            is StatusSlotState.Planned -> PlannedNotice(
                text = plannedText(shown.notice, zone, locale),
                onDismiss = { onDismiss(shown.notice.id) },
            )

            else -> AlertStrip(text = alertText(shown, now, zone, locale))
        }
    }
}

@Composable
private fun alertText(state: StatusSlotState, now: Instant, zone: ZoneId, locale: Locale): String {
    val server = when (state) {
        is StatusSlotState.Maintenance -> ServerStatus.Maintenance(state.until)
        is StatusSlotState.Incident -> ServerStatus.Incident(state.until)
        StatusSlotState.ServerProblem -> ServerStatus.ServerProblem
        StatusSlotState.Unreachable -> ServerStatus.Unreachable
        else -> null
    }
    return server?.let { serverStatusText(it, now, zone, locale) }
        ?: stringResource(R.string.common_internet_unavailable_message)
}

@Composable
private fun plannedText(notice: PlannedMaintenance, zone: ZoneId, locale: Locale): String {
    val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    val start = notice.startsAt.atZone(zone)
    return stringResource(
        R.string.server_status_planned_msg_x,
        date.format(start),
        time.format(start),
        time.format(notice.endsAt.atZone(zone)),
    )
}

@Composable
private fun AlertStrip(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 32.dp, vertical = 4.dp),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onErrorContainer,
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
private fun PlannedNotice(text: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.labelLarge,
        )
        IconButton(onClick = onDismiss) {
            Icon(
                Icons.TwoTone.Close,
                contentDescription = stringResource(R.string.common_dismiss_action),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
