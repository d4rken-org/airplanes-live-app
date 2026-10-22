package eu.darken.apl.common.planespotters

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Flight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import coil3.asDrawable
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import eu.darken.apl.R
import eu.darken.apl.common.compose.Preview2
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.planespotters.coil.AircraftThumbnailQuery
import eu.darken.apl.common.planespotters.coil.PlanespottersImage
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Height of the credit line along the photo's bottom edge, for what is placed above it. */
val PlanespottersCreditHeight = 12.sp

@Composable
fun PlanespottersThumbnail(
    query: AircraftThumbnailQuery?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(4.dp),
    aspectRatio: Float? = 420f / 280f,
    onImageClick: ((PlanespottersMeta) -> Unit)? = null,
    /** A loaded photo takes the press, so a long press meant for what holds it has to be passed in. */
    onImageLongClick: (() -> Unit)? = null,
    onNoPhoto: (() -> Unit)? = null,
    /** 0 centers the credit, 1 moves it to the start and shows the source at the end. Only read during layout. */
    creditSpread: (() -> Float)? = null,
) {
    val context = LocalContext.current
    var meta by remember(query) { mutableStateOf<PlanespottersMeta?>(null) }

    var isLoaded by remember(query) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .then(if (aspectRatio != null) Modifier.aspectRatio(aspectRatio) else Modifier)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (!isLoaded) {
            Icon(
                imageVector = Icons.TwoTone.Flight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }

        if (query != null) {
            val request = remember(query) {
                ImageRequest.Builder(context)
                    .data(query)
                    // A large photo can be shown bigger than its first layout, decode it in full
                    .apply { if (query.large) size(Size.ORIGINAL) }
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = stringResource(R.string.common_aircraft_photo_label),
                contentScale = ContentScale.Crop,
                onSuccess = { state ->
                    isLoaded = true
                    meta = (state.result.image.asDrawable(context.resources) as? PlanespottersImage)?.meta
                    // A lookup without photos answers with the fetcher's placeholder, credited to nobody
                    if (meta != null && meta?.author == null) onNoPhoto?.invoke()
                },
                onError = {
                    meta = null
                    onNoPhoto?.invoke()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (meta?.author != null && onImageClick != null) {
                            Modifier.combinedClickable(
                                onClick = { meta?.let { onImageClick(it) } },
                                onLongClick = onImageLongClick,
                            )
                        } else {
                            Modifier
                        }
                    ),
            )
        }

        CreditBar(
            caption = meta?.getCaption(context) ?: stringResource(R.string.common_no_photo_label),
            source = if (meta?.author != null && creditSpread != null) {
                stringResource(R.string.thumbnail_caption_source)
            } else {
                null
            },
            spread = creditSpread ?: { 0f },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f)),
        )
    }
}

@Composable
private fun CreditBar(
    caption: String,
    source: String?,
    spread: () -> Float,
    modifier: Modifier = Modifier,
) {
    val creditText: @Composable (String, Modifier) -> Unit = { text, textModifier ->
        Text(
            text = text,
            color = Color.White,
            fontSize = 8.sp,
            lineHeight = PlanespottersCreditHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = textModifier,
        )
    }
    Layout(
        content = {
            if (source != null) {
                // The source is invisible while collapsed, the caption announces both
                creditText(caption, Modifier.clearAndSetSemantics { contentDescription = "$caption, $source" })
                creditText(source, Modifier.clearAndSetSemantics {})
            } else {
                creditText(caption, Modifier)
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val t = if (measurables.size > 1) spread().coerceIn(0f, 1f) else 0f
        val padH = lerp(2.dp.roundToPx(), 6.dp.roundToPx(), t)
        val gap = 8.dp.roundToPx()
        val innerWidth = (width - 2 * padH).coerceAtLeast(0)

        val sourcePlaceable = measurables.getOrNull(1)?.measure(Constraints(maxWidth = innerWidth))
        // Short of leaving the caption its whole text or half the bar, the source takes less room and stays faded
        val capacity = sourcePlaceable?.let {
            val captionWidth = measurables[0].maxIntrinsicWidth(constraints.maxHeight)
            val headroom = innerWidth - it.width - gap - min(captionWidth, innerWidth / 2)
            (headroom / 16.dp.toPx()).coerceIn(0f, 1f)
        } ?: 0f
        val sourceSpread = min(t, capacity)
        // First the caption moves to the start and makes room, then the source fades into it
        val makeRoom = (sourceSpread * 2f).coerceAtMost(1f)
        val fadeIn = (sourceSpread * 2f - 1f).coerceAtLeast(0f)
        val captionEnd = width - padH - ((sourcePlaceable?.let { it.width + gap } ?: 0) * makeRoom).roundToInt()
        val captionPlaceable = measurables[0].measure(Constraints(maxWidth = (captionEnd - padH).coerceAtLeast(0)))
        val height = max(captionPlaceable.height, sourcePlaceable?.height ?: 0)

        layout(width, height) {
            val captionX = lerp((width - captionPlaceable.width) / 2, padH, makeRoom)
                .coerceAtMost(captionEnd - captionPlaceable.width)
            captionPlaceable.placeRelative(captionX, 0)
            sourcePlaceable?.let {
                val endX = width - padH - it.width
                it.placeRelativeWithLayer(endX + ((1f - fadeIn) * gap).roundToInt(), 0) { alpha = fadeIn }
            }
        }
    }
}

@Preview2
@Composable
private fun PlanespottersThumbnailDefaultPreview() {
    PreviewWrapper {
        PlanespottersThumbnail(
            query = AircraftThumbnailQuery(hex = "ABC123", registration = "D-ABCD"),
            modifier = Modifier.size(width = 100.dp, height = 67.dp),
        )
    }
}

@Preview2
@Composable
private fun PlanespottersThumbnailSmallPreview() {
    PreviewWrapper {
        PlanespottersThumbnail(
            query = AircraftThumbnailQuery(hex = "ABC123"),
            modifier = Modifier.size(width = 54.dp, height = 36.dp),
        )
    }
}

@Preview2
@Composable
private fun PlanespottersThumbnailNullQueryPreview() {
    PreviewWrapper {
        PlanespottersThumbnail(
            query = null,
            modifier = Modifier.size(width = 100.dp, height = 67.dp),
        )
    }
}
