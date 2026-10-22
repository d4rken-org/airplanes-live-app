package eu.darken.apl.common.time

import android.text.format.DateFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The locale's pattern for [skeleton], e.g. "Hm", as a [DateTimeFormatter]. */
fun bestDateTimeFormatter(locale: Locale, skeleton: String): DateTimeFormatter =
    dateTimeFormatterOf(DateFormat.getBestDateTimePattern(locale, skeleton), locale)

/**
 * ICU patterns can carry the day period "B", e.g. "Bh:mm", which java.time only knows since Java 16. Without it,
 * the AM/PM marker stands in.
 */
fun dateTimeFormatterOf(pattern: String, locale: Locale): DateTimeFormatter = try {
    DateTimeFormatter.ofPattern(pattern, locale)
} catch (e: IllegalArgumentException) {
    DateTimeFormatter.ofPattern(javaTimeCompatiblePattern(pattern), locale)
}

/** "Bh:mm" becomes "ah:mm", quoted text like "h 'B' B" keeps its B: "h 'B' a". */
internal fun javaTimeCompatiblePattern(pattern: String): String = buildString {
    var quoted = false
    var i = 0
    while (i < pattern.length) {
        val c = pattern[i]
        when {
            c == '\'' -> {
                // An escaped quote "''" toggles twice and leaves the state as it was
                quoted = !quoted
                append(c)
            }

            c == 'B' && !quoted -> {
                while (i + 1 < pattern.length && pattern[i + 1] == 'B') i++
                append('a')
            }

            else -> append(c)
        }
        i++
    }
}
