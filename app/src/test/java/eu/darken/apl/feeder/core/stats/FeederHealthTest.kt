package eu.darken.apl.feeder.core.stats

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class FeederHealthTest : BaseTest() {

    private fun device(
        freshness: DeviceFreshness = DeviceFreshness.FRESH,
        severities: Map<HealthMetric, HealthSeverity> = emptyMap(),
    ) = FeederMetricFamily.Device(
        cpuTemperatureC = null,
        memoryUsedPercent = null,
        diskUsedPercent = null,
        wifiRssiDbm = null,
        clockSkewSeconds = null,
        freshness = freshness,
        severities = severities,
    )

    @Test
    fun `no device block means diagnostics unavailable`() {
        (null as FeederMetricFamily.Device?).toHealthObservation() shouldBe HealthObservation.DiagnosticsUnavailable
    }

    @Test
    fun `expired snapshot is inconclusive, not a recovery`() {
        device(freshness = DeviceFreshness.EXPIRED, severities = emptyMap())
            .toHealthObservation() shouldBe HealthObservation.Inconclusive
    }

    @Test
    fun `evaluated observation carries only non-ok severities`() {
        val observation = device(
            severities = mapOf(
                HealthMetric.CPU_TEMPERATURE to HealthSeverity.CRIT,
                HealthMetric.DISK_AVAILABLE to HealthSeverity.OK,
                HealthMetric.WIFI_RSSI to HealthSeverity.WARN,
            ),
        ).toHealthObservation()

        observation shouldBe HealthObservation.Evaluated(
            mapOf(
                HealthMetric.CPU_TEMPERATURE to HealthSeverity.CRIT,
                HealthMetric.WIFI_RSSI to HealthSeverity.WARN,
            )
        )
    }

    @Test
    fun `stale snapshot still evaluates`() {
        device(freshness = DeviceFreshness.STALE, severities = mapOf(HealthMetric.CPU_TEMPERATURE to HealthSeverity.WARN))
            .toHealthObservation() shouldBe HealthObservation.Evaluated(mapOf(HealthMetric.CPU_TEMPERATURE to HealthSeverity.WARN))
    }

    @Test
    fun `wire mappings are pinned`() {
        healthSeverityFromWire("ok") shouldBe HealthSeverity.OK
        healthSeverityFromWire("warn") shouldBe HealthSeverity.WARN
        healthSeverityFromWire("err") shouldBe HealthSeverity.CRIT
        healthSeverityFromWire("error") shouldBe null
        healthSeverityFromWire(null) shouldBe null

        healthMetricFromWire("cpu_temperature_c") shouldBe HealthMetric.CPU_TEMPERATURE
        healthMetricFromWire("memory_used_percent") shouldBe HealthMetric.MEMORY_AVAILABLE
        healthMetricFromWire("disk_used_percent") shouldBe HealthMetric.DISK_AVAILABLE
        healthMetricFromWire("wifi_rssi_dbm") shouldBe HealthMetric.WIFI_RSSI
        healthMetricFromWire("clock_skew_seconds") shouldBe HealthMetric.CLOCK_SKEW
        healthMetricFromWire("future_metric") shouldBe null

        deviceFreshnessFromWire("fresh") shouldBe DeviceFreshness.FRESH
        deviceFreshnessFromWire("stale") shouldBe DeviceFreshness.STALE
        deviceFreshnessFromWire("expired") shouldBe DeviceFreshness.EXPIRED
        deviceFreshnessFromWire("weird") shouldBe DeviceFreshness.UNKNOWN
        deviceFreshnessFromWire(null) shouldBe DeviceFreshness.UNKNOWN
    }
}
