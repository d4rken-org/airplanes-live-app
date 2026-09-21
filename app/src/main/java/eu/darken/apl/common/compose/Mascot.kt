package eu.darken.apl.common.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.darken.apl.R

/**
 * [cropPadding] draws the art oversized inside a clipped box of [size], so it fills the box instead
 * of leaving the launcher icon's safe-zone margin as empty space around it. The child uses
 * [requiredSize] because [androidx.compose.foundation.layout.size] would be clamped by the box.
 */
@Composable
fun Mascot(
    size: Dp,
    modifier: Modifier = Modifier,
    colorFilter: ColorFilter? = null,
    cropPadding: Boolean = false,
) {
    if (!cropPadding) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = modifier.size(size),
            colorFilter = colorFilter,
        )
        return
    }

    Box(
        modifier = modifier
            .size(size)
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.requiredSize(size * SAFE_ZONE_SCALE),
            colorFilter = colorFilter,
        )
    }
}

/** Inverse of the safe-zone inset the launcher foreground is drawn inside. */
private const val SAFE_ZONE_SCALE = 1.58f

@Preview2
@Composable
private fun MascotPreview() {
    PreviewWrapper {
        Mascot(size = 48.dp)
    }
}

@Preview2
@Composable
private fun MascotCroppedPreview() {
    PreviewWrapper {
        Mascot(size = 48.dp, cropPadding = true)
    }
}

@Preview2
@Composable
private fun MascotTintedPreview() {
    PreviewWrapper {
        Mascot(size = 48.dp, colorFilter = ColorFilter.tint(Color.White))
    }
}
