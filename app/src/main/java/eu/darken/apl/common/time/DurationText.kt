package eu.darken.apl.common.time

import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import java.time.Duration
import java.util.Locale

/** In one unit, rounded down, e.g. "45 minutes", "5 hours" or "3 days". At least one minute. */
fun durationText(duration: Duration, locale: Locale): String {
    val measure = when {
        duration < Duration.ofHours(1) -> Measure(duration.toMinutes().coerceAtLeast(1), MeasureUnit.MINUTE)
        duration < Duration.ofDays(2) -> Measure(duration.toHours(), MeasureUnit.HOUR)
        else -> Measure(duration.toDays(), MeasureUnit.DAY)
    }
    return MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.WIDE).format(measure)
}
