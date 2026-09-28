package eu.darken.apl.main.core.update

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import kotlin.math.sign

class SemVerTest : BaseTest() {

    private fun v(version: String) = SemVer.parse(version)

    private fun assertAscending(vararg versions: String) {
        versions.toList().zipWithNext().forEach { (lower, higher) ->
            v(lower).compareTo(v(higher)) shouldBeLessThan 0
            v(higher).compareTo(v(lower)) shouldBeGreaterThan 0
        }
    }

    @Nested
    inner class Parse {

        @Test
        fun `app release candidate`() {
            v("0.8.3-rc0") shouldBe SemVer(0, 8, 3, preRelease = "rc0")
        }

        @Test
        fun `app beta`() {
            v("0.1.2-beta1") shouldBe SemVer(0, 1, 2, preRelease = "beta1")
        }

        @Test
        fun `plain release`() {
            v("1.2.3") shouldBe SemVer(1, 2, 3)
        }

        @Test
        fun `build metadata only`() {
            v("1.2.3+build.5") shouldBe SemVer(1, 2, 3, buildMetadata = "build.5")
        }

        @Test
        fun `pre-release and build metadata`() {
            v("1.2.3-rc.1+exp.sha.5114f85") shouldBe SemVer(
                1, 2, 3,
                preRelease = "rc.1",
                buildMetadata = "exp.sha.5114f85",
            )
        }

        @Test
        fun `toString round-trips`() {
            listOf(
                "0.8.3-rc0",
                "0.1.2-beta1",
                "1.2.3",
                "1.2.3+build.5",
                "1.2.3-rc.1+exp.sha.5114f85",
            ).forEach { v(it).toString() shouldBe it }
        }

        @Test
        fun `invalid inputs`() {
            listOf(
                "",
                "1.2",
                "v1.2.3",
                "01.2.3",
                "1.2.3-",
                "1.2.3-01",
                "1.2.3-rc..1",
                "1.2.3+",
                "1.2.3 ",
            ).forEach { input ->
                shouldThrow<IllegalArgumentException> { v(input) }
            }
        }

        @Test
        fun `core component overflow`() {
            listOf(
                "2147483648.0.0",
                "1.2147483648.0",
                "1.0.2147483648",
            ).forEach { input ->
                shouldThrow<IllegalArgumentException> { v(input) }
            }
        }
    }

    @Nested
    inner class Precedence {

        @Test
        fun `semver spec pre-release chain`() {
            assertAscending(
                "1.0.0-alpha",
                "1.0.0-alpha.1",
                "1.0.0-alpha.beta",
                "1.0.0-beta",
                "1.0.0-beta.2",
                "1.0.0-beta.11",
                "1.0.0-rc.1",
                "1.0.0",
            )
        }

        @Test
        fun `core ordering`() {
            assertAscending("1.0.0", "2.0.0", "2.1.0", "2.1.1")
            assertAscending("0.9.9", "0.10.0")
        }

        @Test
        fun `build metadata is ignored`() {
            v("1.0.0+a").compareTo(v("1.0.0+b")) shouldBe 0
        }

        @Test
        fun `app release history`() {
            assertAscending("0.1.2-beta0", "0.1.2-beta1", "0.2.0-rc0", "0.8.2-rc0", "0.8.3-rc0")
        }

        @Test
        fun `rc suffix numbers compare lexically`() {
            assertAscending("1.0.0-rc10", "1.0.0-rc2")
            assertAscending("0.8.3-rc10", "0.8.3-rc2")
        }

        @Test
        fun `numeric identifiers`() {
            assertAscending("1.0.0-2", "1.0.0-10")
            assertAscending("1.0.0-2", "1.0.0-1a")
        }

        @Test
        fun `higher position outranks lower positions`() {
            assertAscending("1.99.99", "2.0.0")
            assertAscending("1.2.99", "1.3.0")
        }

        @Test
        fun `self comparison`() {
            listOf("1.0.0", "0.8.3-rc0", "1.2.3-rc.1+exp.sha.5114f85").forEach {
                v(it).compareTo(v(it)) shouldBe 0
            }
        }

        @Test
        fun `numeric identifier beyond Long range`() {
            val result = shouldNotThrowAny {
                v("1.0.0-99999999999999999999").compareTo(v("1.0.0-9"))
            }
            result.sign shouldBe "99999999999999999999".compareTo("9").sign
        }

        @Test
        fun `numeric identifier at Long max`() {
            v("1.0.0-9223372036854775807").compareTo(v("1.0.0-9223372036854775806")) shouldBeGreaterThan 0
        }
    }
}
