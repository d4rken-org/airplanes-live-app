package eu.darken.apl.server.status

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The advisory end of an outage for display: "14:35" on the same day as [now], date and time otherwise.
 * Null once [until] has passed, an overrun end is no promise anymore.
 */
fun formatServerStatusUntil(until: Instant, now: Instant, zone: ZoneId, locale: Locale): String? {
    if (!until.isAfter(now)) return null
    val end = until.atZone(zone)
    val formatter = if (end.toLocalDate() == now.atZone(zone).toLocalDate()) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    } else {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
    }
    return formatter.withLocale(locale).format(end)
}
