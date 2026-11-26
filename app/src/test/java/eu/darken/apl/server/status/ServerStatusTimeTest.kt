package eu.darken.apl.server.status

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class ServerStatusTimeTest : BaseTest() {

    private val now = Instant.parse("2026-11-26T10:00:00Z")
    private val locale = Locale.US

    private fun time(instant: Instant, zone: ZoneId) = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .withLocale(locale)
        .format(instant.atZone(zone))

    private fun dateTime(instant: Instant, zone: ZoneId) = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
        .withLocale(locale)
        .format(instant.atZone(zone))

    @Test
    fun `an end later the same day is a time only`() {
        val until = Instant.parse("2026-11-26T15:30:00Z")

        formatServerStatusUntil(until, now, ZoneOffset.UTC, locale) shouldBe time(until, ZoneOffset.UTC)
    }

    @Test
    fun `an end on the next local day includes the date`() {
        val until = Instant.parse("2026-11-27T01:00:00Z")

        formatServerStatusUntil(until, now, ZoneOffset.UTC, locale) shouldBe dateTime(until, ZoneOffset.UTC)
    }

    @Test
    fun `the day is the local one, not the UTC one`() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        // 16:00 UTC is 01:00 the next day in Tokyo, while now is 19:00 the same day there
        val until = Instant.parse("2026-11-26T16:00:00Z")

        formatServerStatusUntil(until, now, ZoneOffset.UTC, locale) shouldBe time(until, ZoneOffset.UTC)
        formatServerStatusUntil(until, now, tokyo, locale) shouldBe dateTime(until, tokyo)
        formatServerStatusUntil(until, now, tokyo, locale)!!.apply {
            this shouldContain "11/27/26"
            this shouldContain "1:00"
        }
    }

    @Test
    fun `an end that is now or past has no text`() {
        formatServerStatusUntil(now, now, ZoneOffset.UTC, locale).shouldBeNull()
        formatServerStatusUntil(now.minusSeconds(60), now, ZoneOffset.UTC, locale).shouldBeNull()
    }
}
