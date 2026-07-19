package eu.darken.apl.feeder.core.stats

import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class FeederHealthThresholdsTest : BaseTest() {

    private fun device(
        cpu: Double? = null,
        memUsed: Double? = null,
        diskUsed: Double? = null,
        rssi: Int? = null,
        skew: Double? = null,
    ) = FeederMetricFamily.Device(
        cpuTemperatureC = cpu,
        memoryUsedPercent = memUsed,
        diskUsedPercent = diskUsed,
        wifiRssiDbm = rssi,
        clockSkewSeconds = skew,
    )

    @Test
    fun `cpu temperature boundaries`() {
        FeederHealthThresholds.evaluate(device(cpu = 74.9))[HealthMetric.CPU_TEMPERATURE] shouldBe HealthSeverity.OK
        FeederHealthThresholds.evaluate(device(cpu = 75.0))[HealthMetric.CPU_TEMPERATURE] shouldBe HealthSeverity.WARN
        FeederHealthThresholds.evaluate(device(cpu = 79.9))[HealthMetric.CPU_TEMPERATURE] shouldBe HealthSeverity.WARN
        FeederHealthThresholds.evaluate(device(cpu = 80.0))[HealthMetric.CPU_TEMPERATURE] shouldBe HealthSeverity.CRIT
    }

    @Test
    fun `memory and disk thresholds apply to the AVAILABLE percentage`() {
        // 84% used = 16% available -> OK; 85 used = 15 available -> WARN; 95 used = 5 available -> CRIT
        FeederHealthThresholds.evaluate(device(memUsed = 84.0))[HealthMetric.MEMORY_AVAILABLE] shouldBe HealthSeverity.OK
        FeederHealthThresholds.evaluate(device(memUsed = 85.0))[HealthMetric.MEMORY_AVAILABLE] shouldBe HealthSeverity.WARN
        FeederHealthThresholds.evaluate(device(memUsed = 95.0))[HealthMetric.MEMORY_AVAILABLE] shouldBe HealthSeverity.CRIT
        FeederHealthThresholds.evaluate(device(diskUsed = 85.0))[HealthMetric.DISK_AVAILABLE] shouldBe HealthSeverity.WARN
        FeederHealthThresholds.evaluate(device(diskUsed = 95.0))[HealthMetric.DISK_AVAILABLE] shouldBe HealthSeverity.CRIT
    }

    @Test
    fun `wifi rssi boundaries`() {
        FeederHealthThresholds.evaluate(device(rssi = -69))[HealthMetric.WIFI_RSSI] shouldBe HealthSeverity.OK
        FeederHealthThresholds.evaluate(device(rssi = -70))[HealthMetric.WIFI_RSSI] shouldBe HealthSeverity.WARN
        FeederHealthThresholds.evaluate(device(rssi = -80))[HealthMetric.WIFI_RSSI] shouldBe HealthSeverity.CRIT
    }

    @Test
    fun `clock skew uses absolute value`() {
        FeederHealthThresholds.evaluate(device(skew = -45.0))[HealthMetric.CLOCK_SKEW] shouldBe HealthSeverity.WARN
        FeederHealthThresholds.evaluate(device(skew = 301.0))[HealthMetric.CLOCK_SKEW] shouldBe HealthSeverity.CRIT
        FeederHealthThresholds.evaluate(device(skew = 5.0))[HealthMetric.CLOCK_SKEW] shouldBe HealthSeverity.OK
    }

    @Test
    fun `invalid readings are skipped entirely`() {
        FeederHealthThresholds.evaluate(device(cpu = Double.NaN)) shouldNotContainKey HealthMetric.CPU_TEMPERATURE
        FeederHealthThresholds.evaluate(device(memUsed = 120.0)) shouldNotContainKey HealthMetric.MEMORY_AVAILABLE
        FeederHealthThresholds.evaluate(device(memUsed = -5.0)) shouldNotContainKey HealthMetric.MEMORY_AVAILABLE
        FeederHealthThresholds.evaluate(device(rssi = 12)) shouldNotContainKey HealthMetric.WIFI_RSSI
        FeederHealthThresholds.evaluate(device(skew = Double.POSITIVE_INFINITY)) shouldNotContainKey HealthMetric.CLOCK_SKEW
    }

    @Test
    fun `all-null device evaluates to nothing`() {
        FeederHealthThresholds.evaluate(device()).shouldBeEmpty()
    }

    @Test
    fun `warnings filters OK entries`() {
        val warnings = FeederHealthThresholds.warnings(device(cpu = 76.0, memUsed = 50.0, rssi = -85))
        warnings shouldBe mapOf(
            HealthMetric.CPU_TEMPERATURE to HealthSeverity.WARN,
            HealthMetric.WIFI_RSSI to HealthSeverity.CRIT,
        )
    }
}
