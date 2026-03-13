package eu.darken.apl.common.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.darken.apl.R

@Composable
fun Mascot(
    size: Dp,
    modifier: Modifier = Modifier,
    colorFilter: ColorFilter? = null,
) {
    Image(
        painter = painterResource(R.drawable.ic_launcher_foreground),
        contentDescription = null,
        modifier = modifier.size(size),
        colorFilter = colorFilter,
    )
}

@Preview2
@Composable
private fun MascotPreview() {
    PreviewWrapper {
        Mascot(size = 48.dp)
    }
}

@Preview2
@Composable
private fun MascotTintedPreview() {
    PreviewWrapper {
        Mascot(size = 48.dp, colorFilter = ColorFilter.tint(Color.White))
    }
}
