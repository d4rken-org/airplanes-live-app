package eu.darken.apl.common.compose

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import eu.darken.apl.R

/** "Squawk 7700" in the translation's word order, an emergency code highlighted in its own chip. */
@Composable
fun SquawkText(
    squawk: String,
    isEmergency: Boolean,
    style: TextStyle,
    color: Color,
) {
    // Split the translated template around the code so the code can be its own composable
    val template = stringResource(R.string.common_squawk_value_x, CODE_MARKER)
    val prefix = template.substringBefore(CODE_MARKER)
    val suffix = template.substringAfter(CODE_MARKER, missingDelimiterValue = "")
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (prefix.isNotEmpty()) Text(prefix, style = style, color = color)
        if (isEmergency) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Text(squawk, style = style, modifier = Modifier.padding(horizontal = 4.dp))
            }
        } else {
            Text(squawk, style = style, color = color)
        }
        if (suffix.isNotEmpty()) Text(suffix, style = style, color = color)
    }
}

private const val CODE_MARKER = "\uE000"
