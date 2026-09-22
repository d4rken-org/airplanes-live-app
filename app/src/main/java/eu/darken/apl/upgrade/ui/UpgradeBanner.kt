package eu.darken.apl.upgrade.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.KeyboardArrowRight
import androidx.compose.material.icons.twotone.Stars
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.apl.R
import eu.darken.apl.common.compose.Preview2
import eu.darken.apl.common.compose.PreviewWrapper

/**
 * Closes a capped or metered list: what the free tier is doing to this list, and a way out of it.
 * Lives at the end of the list so the numbers cost no space above the content.
 */
@Composable
fun UpgradeBanner(
    title: String,
    body: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The label names the action; a contentDescription here would replace the quota text below it
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable(
                onClickLabel = stringResource(R.string.upgrade_banner_action_description),
                onClick = onClick,
            ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.TwoTone.Stars,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.TwoTone.KeyboardArrowRight,
                contentDescription = null,
            )
        }
    }
}

@Preview2
@Composable
private fun UpgradeBannerPreview() {
    PreviewWrapper {
        UpgradeBanner(
            title = "Showing 10 of 1318 matches",
            body = "The free tier caps how many results a search returns. Upgrade to get all of them.",
            onClick = {},
        )
    }
}
