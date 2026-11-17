package eu.darken.apl.main.core.aircraft

import eu.darken.apl.R
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class SquawkMeaningsTest : BaseTest() {

    @Test
    fun `7500 is worldwide`() {
        SquawkMeanings.of("7500") shouldBe listOf(
            SquawkMeaning("7500", emptySet(), R.string.aircraft_squawk_meaning_7500),
        )
    }

    @Test
    fun `7600 is worldwide`() {
        SquawkMeanings.of("7600") shouldBe listOf(
            SquawkMeaning("7600", emptySet(), R.string.aircraft_squawk_meaning_7600),
        )
    }

    @Test
    fun `7700 is worldwide`() {
        SquawkMeanings.of("7700") shouldBe listOf(
            SquawkMeaning("7700", emptySet(), R.string.aircraft_squawk_meaning_7700),
        )
    }

    @Test
    fun `7400 in the United States and the United Kingdom`() {
        SquawkMeanings.of("7400") shouldBe listOf(
            SquawkMeaning(
                "7400",
                setOf(SquawkRegion.UNITED_STATES, SquawkRegion.UNITED_KINGDOM),
                R.string.aircraft_squawk_meaning_7400,
            ),
        )
    }

    @Test
    fun `2000 in Europe and Canada`() {
        SquawkMeanings.of("2000") shouldBe listOf(
            SquawkMeaning("2000", setOf(SquawkRegion.EUROPE), R.string.aircraft_squawk_meaning_2000_europe),
            SquawkMeaning("2000", setOf(SquawkRegion.CANADA), R.string.aircraft_squawk_meaning_2000_canada),
        )
    }

    @Test
    fun `1000 in Europe, the United States and Canada`() {
        SquawkMeanings.of("1000") shouldBe listOf(
            SquawkMeaning("1000", setOf(SquawkRegion.EUROPE), R.string.aircraft_squawk_meaning_1000_europe),
            SquawkMeaning("1000", setOf(SquawkRegion.UNITED_STATES), R.string.aircraft_squawk_meaning_1000_us),
            SquawkMeaning("1000", setOf(SquawkRegion.CANADA), R.string.aircraft_squawk_meaning_1000_canada),
        )
    }

    @Test
    fun `7000 in Europe`() {
        SquawkMeanings.of("7000") shouldBe listOf(
            SquawkMeaning("7000", setOf(SquawkRegion.EUROPE), R.string.aircraft_squawk_meaning_7000_europe),
        )
    }

    @Test
    fun `1200 in the United States and Canada`() {
        SquawkMeanings.of("1200") shouldBe listOf(
            SquawkMeaning("1200", setOf(SquawkRegion.UNITED_STATES), R.string.aircraft_squawk_meaning_1200_us),
            SquawkMeaning("1200", setOf(SquawkRegion.CANADA), R.string.aircraft_squawk_meaning_1200_canada),
        )
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        SquawkMeanings.of(" 7700 ") shouldBe SquawkMeanings.of("7700")
        SquawkMeanings.of(" 7700 ").map { it.code } shouldBe listOf("7700")
    }

    @Test
    fun `unlisted codes have no meaning`() {
        SquawkMeanings.of("1234").shouldBeEmpty()
    }

    @Test
    fun `former German VFR codes have no meaning`() {
        SquawkMeanings.of("0021").shouldBeEmpty()
        SquawkMeanings.of("0022").shouldBeEmpty()
    }

    @Test
    fun `blank or missing squawk has no meaning`() {
        SquawkMeanings.of("").shouldBeEmpty()
        SquawkMeanings.of("  ").shouldBeEmpty()
        SquawkMeanings.of(null).shouldBeEmpty()
    }
}
