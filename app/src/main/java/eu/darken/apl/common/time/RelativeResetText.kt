package eu.darken.apl.common.time

import android.icu.text.DisplayContext
import android.icu.text.RelativeDateTimeFormatter
import android.icu.text.RelativeDateTimeFormatter.Direction
import android.icu.text.RelativeDateTimeFormatter.RelativeUnit
import android.icu.util.ULocale
import java.time.Instant
import java.util.Locale

/**
 * How far off [resetsAt] is from [nowMillis], for the middle of a sentence: "in 45 minutes", "in 5 hours" or
 * "in 2 days". In one unit, rounded down, at least one minute, also for a reset that already passed.
 */
fun relativeResetText(resetsAt: Instant, nowMillis: Long, locale: Locale = Locale.getDefault()): String {
    val minutes = ((resetsAt.toEpochMilli() - nowMillis) / 60_000L).coerceAtLeast(1)
    val (quantity, unit) = when {
        minutes < 60 -> minutes to RelativeUnit.MINUTES
        minutes < 24 * 60 -> minutes / 60 to RelativeUnit.HOURS
        else -> minutes / (24 * 60) to RelativeUnit.DAYS
    }
    val formatter = RelativeDateTimeFormatter.getInstance(
        ULocale.forLocale(locale),
        null,
        RelativeDateTimeFormatter.Style.LONG,
        DisplayContext.CAPITALIZATION_FOR_MIDDLE_OF_SENTENCE,
    )
    return formatter.format(quantity.toDouble(), Direction.NEXT, unit)
}
