package eu.darken.apl.common.planespotters

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Flight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.asDrawable
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import eu.darken.apl.R
import eu.darken.apl.common.compose.Preview2
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.planespotters.coil.AircraftThumbnailQuery
import eu.darken.apl.common.planespotters.coil.PlanespottersImage

@Composable
fun PlanespottersThumbnail(
    query: AircraftThumbnailQuery?,
    modifier: Modifier = Modifier,
    onImageClick: ((PlanespottersMeta) -> Unit)? = null,
) {
    val context = LocalContext.current
    var meta by remember { mutableStateOf<PlanespottersMeta?>(null) }

    var isLoaded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .aspectRatio(420f / 280f)
            .clip(RoundedCornerShape(4.dp))
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
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = stringResource(R.string.common_aircraft_photo_label),
                contentScale = ContentScale.Crop,
                onSuccess = { state ->
                    isLoaded = true
                    meta = (state.result.image.asDrawable(context.resources) as? PlanespottersImage)?.meta
                },
                onError = { meta = null },
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (meta != null && onImageClick != null) {
                            Modifier.clickable { meta?.let { onImageClick(it) } }
                        } else {
                            Modifier
                        }
                    ),
            )
        }

        Text(
            text = meta?.getCaption(context) ?: stringResource(R.string.common_no_photo_label),
            color = Color.White,
            fontSize = 8.sp,
            lineHeight = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 2.dp),
        )
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
