package eu.darken.apl.feeder.ui.detail

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.Notifications
import androidx.compose.material.icons.twotone.NotificationsOff
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.apl.R
import eu.darken.apl.common.compose.LoadingBox
import eu.darken.apl.common.compose.aplContentWindowInsets
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.feeder.core.Feeder
import eu.darken.apl.feeder.ui.labelRes
import eu.darken.apl.feeder.core.stats.FeederChartWindow
import eu.darken.apl.feeder.core.stats.FeederDetail
import eu.darken.apl.feeder.core.stats.DeviceFreshness
import eu.darken.apl.feeder.core.stats.FeederStats
import eu.darken.apl.feeder.core.stats.RangeSource
import eu.darken.apl.feeder.core.stats.FeederMetricFamily
import eu.darken.apl.feeder.core.stats.HealthMetric
import eu.darken.apl.feeder.core.stats.HealthSeverity
import java.time.Instant
import java.util.Locale

@Composable
fun FeederDetailScreenHost(
    receiverId: String,
    vm: FeederDetailViewModel = hiltViewModel(),
) {
    NavigationEventHandler(vm)
    ErrorEventHandler(vm)

    LaunchedEffect(receiverId) {
        vm.init(receiverId)
    }

    val state by vm.state.collectAsState(initial = null)

    state?.let {
        FeederDetailScreen(
            state = it,
            onBack = { vm.navUp() },
            onRefresh = vm::refresh,
            onWindowSelected = vm::setWindow,
            onToggleMute = vm::toggleMute,
        )
    } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LoadingBox()
    }
}

@Composable
fun FeederDetailScreen(
    state: FeederDetailViewModel.State,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onWindowSelected: (FeederChartWindow) -> Unit,
    onToggleMute: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = aplContentWindowInsets(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.feeder?.label ?: stringResource(R.string.feeder_page_label),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = onToggleMute) {
                        Icon(
                            imageVector = if (state.isMuted) {
                                Icons.TwoTone.NotificationsOff
                            } else {
                                Icons.TwoTone.Notifications
                            },
                            contentDescription = stringResource(
                                if (state.isMuted) R.string.feeder_detail_unmute_action
                                else R.string.feeder_detail_mute_action
                            ),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        val isRefreshing = (state.detail as? FeederDetailViewModel.DetailState.Ready)?.isRefreshing == true
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when (val detail = state.detail) {
                FeederDetailViewModel.DetailState.Loading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { LoadingBox() }

                is FeederDetailViewModel.DetailState.Error -> Box(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.feeder_detail_error_message),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                is FeederDetailViewModel.DetailState.Ready -> DetailContent(
                    feeder = state.feeder,
                    detail = detail.detail,
                    window = state.window,
                    onWindowSelected = onWindowSelected,
                )
            }
        }
    }
}

@Composable
private fun DetailContent(
    feeder: Feeder?,
    detail: FeederDetail,
    window: FeederChartWindow,
    onWindowSelected: (FeederChartWindow) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (feeder != null) {
            item(key = "header") { HeaderCard(feeder) }
        }

        item(key = "window") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FeederChartWindow.entries.forEach { candidate ->
                    FilterChip(
                        selected = window == candidate,
                        onClick = { onWindowSelected(candidate) },
                        label = {
                            Text(
                                stringResource(
                                    when (candidate) {
                                        FeederChartWindow.H24 -> R.string.feeder_detail_window_24h
                                        FeederChartWindow.D30 -> R.string.feeder_detail_window_30d
                                    }
                                )
                            )
                        },
                    )
                }
            }
        }

        items(count = detail.live.size, key = { detail.live[it].wireKey }) { index ->
            FamilyCard(
                family = detail.live[index],
                detail = detail,
                window = window,
            )
        }

        detail.stats?.let { stats ->
            item(key = "stats") { StatsCard(stats = stats, window = window) }
        }

        if (detail.live.none { it is FeederMetricFamily.Device }) {
            item(key = "no-diagnostics") {
                Text(
                    text = stringResource(R.string.feeder_detail_no_diagnostics),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun HeaderCard(feeder: Feeder) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(feeder.status.labelRes()),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (feeder.isOnline) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.weight(1f),
                )
                feeder.country?.let {
                    Text(text = it, style = MaterialTheme.typography.labelMedium)
                }
            }
            Text(
                text = feeder.lastSeen?.let {
                    stringResource(
                        R.string.feeder_last_seen_label,
                        DateUtils.getRelativeTimeSpanString(
                            it.toEpochMilli(),
                            Instant.now().toEpochMilli(),
                            DateUtils.MINUTE_IN_MILLIS,
                        ).toString(),
                    )
                } ?: stringResource(R.string.feeder_last_seen_never),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun FamilyCard(
    family: FeederMetricFamily,
    detail: FeederDetail,
    window: FeederChartWindow,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(
                    when (family) {
                        is FeederMetricFamily.Feed -> R.string.feeder_detail_section_feed
                        is FeederMetricFamily.Mlat -> R.string.feeder_detail_section_mlat
                        is FeederMetricFamily.Device -> R.string.feeder_detail_section_device
                    }
                ),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(8.dp))

            when (family) {
                is FeederMetricFamily.Feed -> FeedLiveCells(family)
                is FeederMetricFamily.Mlat -> MlatLiveCells(family)
                is FeederMetricFamily.Device -> DeviceLiveCells(family)
            }

            val history = detail.history[window]
            if (history?.hasData(family.wireKey) == true) {
                chartDefsFor(family).forEach { def ->
                    val segments = history.chartSegments(family.wireKey, def.metricKey)
                    if (segments.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        GapAwareMetricChart(
                            title = stringResource(def.labelRes),
                            segments = segments,
                            window = window,
                            lineColor = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedLiveCells(feed: FeederMetricFamily.Feed) {
    CellRow {
        StatCell(
            value = stringResource(
                if (feed.connected) R.string.feeder_detail_connected_yes else R.string.feeder_detail_connected_no
            ),
            label = stringResource(R.string.feeder_metric_connected),
            severity = if (feed.connected) HealthSeverity.OK else HealthSeverity.WARN,
        )
        feed.messagesPerSec?.let {
            StatCell(value = it.format1(), label = stringResource(R.string.feeder_metric_message_rate))
        }
        feed.positionsPerSec?.let {
            StatCell(value = it.format1(), label = stringResource(R.string.feeder_metric_position_rate))
        }
    }
    CellRow {
        feed.rttMs?.let {
            StatCell(value = "$it ms", label = stringResource(R.string.feeder_metric_rtt))
        }
        feed.connectedForSeconds?.let {
            StatCell(
                value = DateUtils.formatElapsedTime(it),
                label = stringResource(R.string.feeder_metric_uptime),
                mono = true,
            )
        }
    }
}

@Composable
private fun MlatLiveCells(mlat: FeederMetricFamily.Mlat) {
    CellRow {
        StatCell(
            value = stringResource(
                if (mlat.connected) R.string.feeder_detail_connected_yes else R.string.feeder_detail_connected_no
            ),
            label = stringResource(R.string.feeder_metric_connected),
        )
        mlat.peerCount?.let {
            StatCell(value = "$it", label = stringResource(R.string.feeder_metric_peers))
        }
        mlat.messageRate?.let {
            StatCell(value = it.format1(), label = stringResource(R.string.feeder_metric_message_rate))
        }
    }
    CellRow {
        mlat.outlierPercent?.let {
            StatCell(value = "${it.format1()}%", label = stringResource(R.string.feeder_metric_outliers))
        }
        mlat.interestCount?.let {
            StatCell(value = "$it", label = stringResource(R.string.feeder_metric_interest))
        }
        mlat.badSync?.let {
            StatCell(
                value = stringResource(
                    if (it) R.string.feeder_detail_connected_no else R.string.feeder_detail_connected_yes
                ),
                label = stringResource(R.string.feeder_metric_sync),
                severity = if (it) HealthSeverity.WARN else HealthSeverity.OK,
            )
        }
    }
}

@Composable
private fun DeviceLiveCells(device: FeederMetricFamily.Device) {
    val severities = device.severities
    if (device.freshness == DeviceFreshness.STALE || device.freshness == DeviceFreshness.EXPIRED) {
        Text(
            text = stringResource(
                if (device.freshness == DeviceFreshness.EXPIRED) {
                    R.string.feeder_detail_device_expired
                } else {
                    R.string.feeder_detail_device_stale
                },
                device.observedAt?.let {
                    DateUtils.getRelativeTimeSpanString(
                        it.toEpochMilli(),
                        Instant.now().toEpochMilli(),
                        DateUtils.MINUTE_IN_MILLIS,
                    ).toString()
                } ?: "?",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
    CellRow {
        device.cpuTemperatureC?.let {
            StatCell(
                value = "${it.format1()} °C",
                label = stringResource(R.string.feeder_health_metric_cpu),
                severity = severities[HealthMetric.CPU_TEMPERATURE] ?: HealthSeverity.OK,
            )
        }
        device.memoryUsedPercent?.let {
            StatCell(
                value = "${it.format0()}%",
                label = stringResource(R.string.feeder_metric_memory_used),
                severity = severities[HealthMetric.MEMORY_AVAILABLE] ?: HealthSeverity.OK,
            )
        }
        device.diskUsedPercent?.let {
            StatCell(
                value = "${it.format0()}%",
                label = stringResource(R.string.feeder_metric_disk_used),
                severity = severities[HealthMetric.DISK_AVAILABLE] ?: HealthSeverity.OK,
            )
        }
    }
    CellRow {
        device.wifiRssiDbm?.let {
            StatCell(
                value = "$it dBm",
                label = stringResource(R.string.feeder_health_metric_wifi),
                severity = severities[HealthMetric.WIFI_RSSI] ?: HealthSeverity.OK,
            )
        }
        device.clockSkewSeconds?.let {
            StatCell(
                value = "${it.format1()} s",
                label = stringResource(R.string.feeder_health_metric_clock),
                severity = severities[HealthMetric.CLOCK_SKEW] ?: HealthSeverity.OK,
            )
        }
    }
}

@Composable
private fun StatsCard(stats: FeederStats, window: FeederChartWindow) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.feeder_detail_section_stats),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(8.dp))

            CellRow {
                // "Now" proxy per API design: the last (in-progress) 24h bucket's mean.
                stats.history[FeederChartWindow.H24]
                    ?.chartPoints(FeederStats.WIRE_KEY, FeederStats.METRIC_AIRCRAFT_TOTAL)
                    ?.lastOrNull()
                    ?.let {
                        StatCell(
                            value = it.value.format0(),
                            label = stringResource(R.string.feeder_stats_recent_aircraft),
                        )
                    }
                StatCell(
                    value = stringResource(
                        when (stats.rangeSource) {
                            RangeSource.CONFIGURED -> R.string.feeder_stats_range_source_configured
                            RangeSource.INFERRED -> R.string.feeder_stats_range_source_inferred
                            RangeSource.NONE, RangeSource.UNKNOWN -> R.string.feeder_stats_range_source_none
                        }
                    ),
                    label = stringResource(R.string.feeder_stats_range_source),
                )
            }

            val history = stats.history[window]
            if (history != null && history.hasData(FeederStats.WIRE_KEY)) {
                listOf(
                    FeederStats.METRIC_AIRCRAFT_TOTAL to R.string.feeder_metric_aircraft_total,
                    FeederStats.METRIC_AIRCRAFT_WITH_POS to R.string.feeder_metric_aircraft_with_pos,
                ).forEach { (metric, labelRes) ->
                    val segments = history.chartSegments(FeederStats.WIRE_KEY, metric)
                    if (segments.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        GapAwareMetricChart(
                            title = stringResource(labelRes),
                            segments = segments,
                            window = window,
                            lineColor = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                // Wire value is a 0..1 fraction; percent is the readable unit.
                val mlatSegments = history.chartSegments(FeederStats.WIRE_KEY, FeederStats.METRIC_MLAT_SHARE)
                    .map { segment -> segment.map { it.copy(value = it.value * 100.0) } }
                if (mlatSegments.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    GapAwareMetricChart(
                        title = stringResource(R.string.feeder_metric_mlat_share),
                        segments = mlatSegments,
                        window = window,
                        lineColor = MaterialTheme.colorScheme.primary,
                    )
                }

                // Both provenances can hold halves of one window; prefer the current one per bucket.
                val (primary, fallback) = when (stats.rangeSource) {
                    RangeSource.INFERRED ->
                        FeederStats.METRIC_RANGE_INFERRED to FeederStats.METRIC_RANGE_CONFIGURED

                    else ->
                        FeederStats.METRIC_RANGE_CONFIGURED to FeederStats.METRIC_RANGE_INFERRED
                }
                val rangeSegments = history.mergedChartSegments(FeederStats.WIRE_KEY, primary, fallback)
                if (rangeSegments.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    GapAwareMetricChart(
                        title = stringResource(R.string.feeder_metric_max_range),
                        segments = rangeSegments,
                        window = window,
                        lineColor = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun CellRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        content()
    }
}

/** [eu.darken.apl.common.compose.InfoCell] sibling with three severity levels instead of a flag. */
@Composable
private fun StatCell(
    value: String,
    label: String,
    severity: HealthSeverity = HealthSeverity.OK,
    mono: Boolean = false,
) {
    Column {
        val style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = if (mono) FontFamily.Monospace else null,
        )
        when (severity) {
            HealthSeverity.OK -> Text(
                text = value,
                style = style,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            HealthSeverity.WARN, HealthSeverity.CRIT -> Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (severity == HealthSeverity.CRIT) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.tertiaryContainer
                },
                contentColor = if (severity == HealthSeverity.CRIT) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onTertiaryContainer
                },
            ) {
                Text(
                    text = value,
                    style = style,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private fun Double.format1(): String = String.format(Locale.getDefault(), "%.1f", this)
private fun Double.format0(): String = String.format(Locale.getDefault(), "%.0f", this)
