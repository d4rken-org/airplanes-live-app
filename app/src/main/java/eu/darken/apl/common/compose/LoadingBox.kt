package eu.darken.apl.common.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.apl.R

@Composable
fun LoadingBox(
    modifier: Modifier = Modifier,
) {
    val loadingText = remember {
        listOf(
            R.string.generic_loading_label_0,
            R.string.generic_loading_label_1,
            R.string.generic_loading_label_2,
            R.string.generic_loading_label_3,
            R.string.generic_loading_label_4,
            R.string.generic_loading_label_5,
            R.string.generic_loading_label_6,
            R.string.generic_loading_label_7,
        ).random()
    }

    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            strokeWidth = 2.dp,
        )
        Text(
            text = stringResource(loadingText),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
