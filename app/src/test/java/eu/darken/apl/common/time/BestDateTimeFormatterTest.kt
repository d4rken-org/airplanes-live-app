package eu.darken.apl.common.time

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class BestDateTimeFormatterTest : BaseTest() {

    @Test
    fun `the day period becomes the AM PM marker`() {
        javaTimeCompatiblePattern("Bh:mm") shouldBe "ah:mm"
        javaTimeCompatiblePattern("BBBB h") shouldBe "a h"
    }

    @Test
    fun `a pattern without a day period stays as it is`() {
        javaTimeCompatiblePattern("HH 'Uhr'") shouldBe "HH 'Uhr'"
        javaTimeCompatiblePattern("EEE h:mm a") shouldBe "EEE h:mm a"
    }

    @Test
    fun `quoted text keeps its B`() {
        javaTimeCompatiblePattern("h 'B' B") shouldBe "h 'B' a"
    }

    @Test
    fun `an escaped quote does not start quoted text`() {
        javaTimeCompatiblePattern("h''B") shouldBe "h''a"
        javaTimeCompatiblePattern("'o''clock B' B") shouldBe "'o''clock B' a"
    }
}
