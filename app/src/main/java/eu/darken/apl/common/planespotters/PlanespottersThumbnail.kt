package eu.darken.apl.common.planespotters

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.asDrawable
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
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
    var isLoading by remember(query) { mutableStateOf(query != null) }

    Box(
        modifier = modifier.aspectRatio(420f / 280f),
        contentAlignment = Alignment.Center,
    ) {
        if (query != null) {
            val request = remember(query) {
                ImageRequest.Builder(context)
                    .data(query)
                    .listener(
                        onStart = {
                            meta = null
                            isLoading = true
                        },
                        onSuccess = { _, result ->
                            val drawable = result.image.asDrawable(context.resources)
                            meta = (drawable as? PlanespottersImage)?.meta
                            isLoading = false
                        },
                        onError = { _, _ ->
                            meta = null
                            isLoading = false
                        },
                        onCancel = {
                            meta = null
                            isLoading = false
                        },
                    )
                    .build()
            }

            AsyncImage(
                model = request,
                contentDescription = "Aircraft photo",
                contentScale = ContentScale.Crop,
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

        if (isLoading) {
            CircularProgressIndicator(strokeWidth = 2.dp)
        }

        meta?.let { m ->
            Text(
                text = m.getCaption(context),
                color = Color.White,
                fontSize = 8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 2.dp, vertical = 1.dp),
            )
        }
    }
}
