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

/** Exact to the minute, e.g. "30 minutes", "48 hours" or "1 hour, 30 minutes". */
fun durationTextExact(duration: Duration, locale: Locale): String {
    val format = MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.WIDE)
    val minutes = duration.toMinutes()
    return when {
        minutes < 60 -> format.format(Measure(minutes, MeasureUnit.MINUTE))
        minutes % 60 == 0L -> format.format(Measure(minutes / 60, MeasureUnit.HOUR))
        else -> format.formatMeasures(Measure(minutes / 60, MeasureUnit.HOUR), Measure(minutes % 60, MeasureUnit.MINUTE))
    }
}
