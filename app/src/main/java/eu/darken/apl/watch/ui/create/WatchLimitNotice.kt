package eu.darken.apl.watch.ui.create

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.apl.R
import eu.darken.apl.common.settings.UpgradeChip
import eu.darken.apl.watch.core.WatchLimit

/** Explains a disabled Add once the tier's watch limit is reached. */
@Composable
fun WatchLimitNotice(
    limit: WatchLimit,
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reached = limit.reached ?: return
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = watchLimitReachedText(reached),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        if (reached.isShown) {
            Spacer(Modifier.width(8.dp))
            UpgradeChip(onClick = onUpgrade)
        }
    }
}

/** A cap the user is not shown a number for is not named either. */
@Composable
fun watchLimitReachedText(reached: WatchLimit.Reached): String = when {
    !reached.isShown && reached.kind == WatchLimit.Kind.ACTIVE -> {
        stringResource(R.string.watch_limit_active_reached_unnumbered_msg)
    }

    !reached.isShown -> stringResource(R.string.watch_limit_reached_title)
    reached.kind == WatchLimit.Kind.STORED -> stringResource(R.string.watch_limit_stored_reached_msg, reached.max)
    else -> stringResource(R.string.watch_limit_reached_msg, reached.max)
}
