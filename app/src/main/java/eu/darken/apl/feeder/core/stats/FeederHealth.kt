package eu.darken.apl.feeder.core.stats

import androidx.annotation.StringRes
import eu.darken.apl.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.abs

@Serializable
enum class HealthMetric {
    @SerialName("cpu_temperature") CPU_TEMPERATURE,
    @SerialName("memory_available") MEMORY_AVAILABLE,
    @SerialName("disk_available") DISK_AVAILABLE,
    @SerialName("wifi_rssi") WIFI_RSSI,
    @SerialName("clock_skew") CLOCK_SKEW,
    ;
}

@StringRes
fun HealthMetric.labelRes(): Int = when (this) {
    HealthMetric.CPU_TEMPERATURE -> R.string.feeder_health_metric_cpu
    HealthMetric.MEMORY_AVAILABLE -> R.string.feeder_health_metric_memory
    HealthMetric.DISK_AVAILABLE -> R.string.feeder_health_metric_disk
    HealthMetric.WIFI_RSSI -> R.string.feeder_health_metric_wifi
    HealthMetric.CLOCK_SKEW -> R.string.feeder_health_metric_clock
}

@Serializable
enum class HealthSeverity {
    @SerialName("ok") OK,
    @SerialName("warn") WARN,
    @SerialName("crit") CRIT,
    ;
}

/**
 * Outcome of one health-check attempt for one feeder. Three distinct cases because they demand
 * different reactions: a fetch failure must not look like a recovery, and diagnostics being
 * disabled is a *successful* answer that retires any previous warning.
 */
sealed interface HealthObservation {
    /** Detail fetch failed — carry the previously known health state forward unchanged. */
    data object FetchFailed : HealthObservation

    /** Fetch succeeded but the feeder reports no device data (diagnostics off) — clear state. */
    data object DiagnosticsUnavailable : HealthObservation

    /** Fetch succeeded; [warnings] holds only the non-OK metrics (absent metric == healthy). */
    data class Evaluated(val warnings: Map<HealthMetric, HealthSeverity>) : HealthObservation
}

/**
 * Mirrors the server's hardware-health thresholds (`accounts/services/feeder_thresholds.py`) —
 * the API returns raw values only, severities are computed client-side to match the website.
 */
object FeederHealthThresholds {

    private const val CPU_TEMP_WARN_C = 75.0
    private const val CPU_TEMP_CRIT_C = 80.0
    private const val AVAILABLE_PCT_WARN = 15.0
    private const val AVAILABLE_PCT_CRIT = 5.0
    private const val WIFI_RSSI_WARN_DBM = -70.0
    private const val WIFI_RSSI_CRIT_DBM = -80.0
    private const val CLOCK_SKEW_WARN_S = 30.0
    private const val CLOCK_SKEW_CRIT_S = 300.0

    /**
     * Severity per metric that has a *valid* value. Invalid readings (non-finite, used% outside
     * 0..100, positive RSSI) are skipped — garbage input must neither warn nor count as healthy.
     */
    fun evaluate(device: FeederMetricFamily.Device): Map<HealthMetric, HealthSeverity> = buildMap {
        device.cpuTemperatureC?.takeIf { it.isFinite() }?.let {
            put(HealthMetric.CPU_TEMPERATURE, it.severityAbove(CPU_TEMP_WARN_C, CPU_TEMP_CRIT_C))
        }
        device.memoryUsedPercent?.takeIf { it.isFinite() && it in 0.0..100.0 }?.let {
            put(HealthMetric.MEMORY_AVAILABLE, (100.0 - it).severityBelow(AVAILABLE_PCT_WARN, AVAILABLE_PCT_CRIT))
        }
        device.diskUsedPercent?.takeIf { it.isFinite() && it in 0.0..100.0 }?.let {
            put(HealthMetric.DISK_AVAILABLE, (100.0 - it).severityBelow(AVAILABLE_PCT_WARN, AVAILABLE_PCT_CRIT))
        }
        device.wifiRssiDbm?.takeIf { it <= 0 }?.let {
            put(HealthMetric.WIFI_RSSI, it.toDouble().severityBelow(WIFI_RSSI_WARN_DBM, WIFI_RSSI_CRIT_DBM))
        }
        device.clockSkewSeconds?.takeIf { it.isFinite() }?.let {
            put(HealthMetric.CLOCK_SKEW, abs(it).severityAbove(CLOCK_SKEW_WARN_S, CLOCK_SKEW_CRIT_S))
        }
    }

    /** [evaluate] filtered to actionable entries, as used by monitoring/notifications. */
    fun warnings(device: FeederMetricFamily.Device): Map<HealthMetric, HealthSeverity> =
        evaluate(device).filterValues { it != HealthSeverity.OK }

    private fun Double.severityAbove(warn: Double, crit: Double): HealthSeverity = when {
        this >= crit -> HealthSeverity.CRIT
        this >= warn -> HealthSeverity.WARN
        else -> HealthSeverity.OK
    }

    private fun Double.severityBelow(warn: Double, crit: Double): HealthSeverity = when {
        this <= crit -> HealthSeverity.CRIT
        this <= warn -> HealthSeverity.WARN
        else -> HealthSeverity.OK
    }
}
