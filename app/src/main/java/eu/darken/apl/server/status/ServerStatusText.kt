package eu.darken.apl.server.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.res.stringResource
import eu.darken.apl.R
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

private const val UNTIL_RECHECK_MS = 60_000L

/** Why the server gives no data, null while it is [ServerStatus.Ok]. [now] is server-aligned time. */
@Composable
fun serverStatusText(status: ServerStatus, now: Instant, zone: ZoneId, locale: Locale): String? {
    val until = status.until?.let { formatServerStatusUntil(it, now, zone, locale) }
    return when (status) {
        is ServerStatus.Maintenance -> until
            ?.let { stringResource(R.string.server_status_maintenance_until_msg_x, it) }
            ?: stringResource(R.string.server_status_maintenance_msg)

        is ServerStatus.Incident -> until
            ?.let { stringResource(R.string.server_status_incident_until_msg_x, it) }
            ?: stringResource(R.string.server_status_incident_msg)

        ServerStatus.ServerProblem -> stringResource(R.string.server_status_problem_msg)
        ServerStatus.Unreachable -> stringResource(R.string.server_status_unreachable_msg)
        ServerStatus.Ok -> null
    }
}

/**
 * Server-aligned time read anew every minute until [until] passes, so "back around 14:35" becomes
 * "back shortly" once 14:35 passed, even without a new answer.
 */
@Composable
fun rememberServerStatusNow(until: Instant?, now: () -> Instant): Instant {
    val value by produceState(initialValue = now(), until, now) {
        value = now()
        if (until == null) return@produceState
        while (value < until) {
            delay(UNTIL_RECHECK_MS)
            value = now()
        }
    }
    return value
}

val ServerStatus.until: Instant?
    get() = when (this) {
        is ServerStatus.Maintenance -> until
        is ServerStatus.Incident -> until
        else -> null
    }
