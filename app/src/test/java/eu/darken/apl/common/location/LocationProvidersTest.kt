package eu.darken.apl.common.location

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class LocationProvidersTest : BaseTest() {

    private val all = setOf("network", "fused", "gps", "passive")

    @Test
    fun `API 36 coarse only uses all three providers`() {
        selectLocationProviders(sdkInt = 36, hasFineLocation = false, existingProviders = all) shouldBe LocationProviders(
            lastKnown = listOf("network", "fused", "gps"),
            continuous = listOf("network", "fused"),
            oneShot = listOf("gps"),
            current = listOf("network", "fused", "gps"),
        )
    }

    @Test
    fun `API 30 coarse only skips gps and fused`() {
        selectLocationProviders(sdkInt = 30, hasFineLocation = false, existingProviders = all) shouldBe LocationProviders(
            lastKnown = listOf("network"),
            continuous = listOf("network"),
            oneShot = emptyList(),
            current = listOf("network"),
        )
    }

    @Test
    fun `API 30 with fine permission includes gps`() {
        selectLocationProviders(sdkInt = 30, hasFineLocation = true, existingProviders = all) shouldBe LocationProviders(
            lastKnown = listOf("network", "gps"),
            continuous = listOf("network"),
            oneShot = listOf("gps"),
            current = listOf("network", "gps"),
        )
    }

    @Test
    fun `API 30 never yields fused`() {
        selectLocationProviders(sdkInt = 30, hasFineLocation = true, existingProviders = setOf("fused")) shouldBe
            LocationProviders(
                lastKnown = emptyList(),
                continuous = emptyList(),
                oneShot = emptyList(),
                current = emptyList(),
            )
    }

    @Test
    fun `missing providers are omitted`() {
        selectLocationProviders(sdkInt = 36, hasFineLocation = true, existingProviders = setOf("gps")) shouldBe
            LocationProviders(
                lastKnown = listOf("gps"),
                continuous = emptyList(),
                oneShot = listOf("gps"),
                current = listOf("gps"),
            )

        selectLocationProviders(sdkInt = 36, hasFineLocation = false, existingProviders = setOf("network", "fused")) shouldBe
            LocationProviders(
                lastKnown = listOf("network", "fused"),
                continuous = listOf("network", "fused"),
                oneShot = emptyList(),
                current = listOf("network", "fused"),
            )
    }
}
