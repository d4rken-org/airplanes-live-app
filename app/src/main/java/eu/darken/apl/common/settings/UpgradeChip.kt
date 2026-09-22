package eu.darken.apl.common.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Stars
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.apl.R

/** Marks an entry that an upgraded installation gets more from, and opens the upgrade screen. */
@Composable
fun UpgradeChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Decorative, the adjacent label is the accessible announcement
        Icon(
            imageVector = Icons.TwoTone.Stars,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(2.dp))
        Text(
            text = stringResource(R.string.upgrade_chip_label),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}
