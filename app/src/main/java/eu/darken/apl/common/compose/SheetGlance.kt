package eu.darken.apl.common.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Until here the text drops below the small media, then the media grows, so the two never overlap. */
const val SHEET_GLANCE_TEXT_STAGE_END = 0.35f

/** How far the media has grown at [progress]: 0 until the text is below it, 1 at full size. */
fun sheetGlanceMediaStage(progress: Float): Float =
    ((progress - SHEET_GLANCE_TEXT_STAGE_END) / (1f - SHEET_GLANCE_TEXT_STAGE_END)).coerceIn(0f, 1f)

object SheetGlanceDefaults {
    val PadH = 16.dp
    val ActionEnd = 4.dp
    const val PHOTO_ASPECT = 3f / 2f
    val PhotoWidth = 144.dp
}

enum class SheetGlanceArrangement {
    /**
     * Expanded, the secondary lines move to the end of the identity's row. The layout keeps the action in place and
     * doesn't fade it. Identity and secondary have to keep their heights at any width, e.g. single text lines.
     */
    SIDE_BY_SIDE,

    /** Expanded, the text keeps stacking below the media. The action stays at the end of the identity's first line. */
    STACKED,
}

/**
 * The media sits at the sheet's top start with the text beside it at [progress] 0 and grows to the full width at 1.
 * [progress] is only read during layout and drawing. Without [growMedia] the media keeps its size and the text its place.
 * [actionCenter] is the action's center below the identity's top, [onCollapsedHeight] reports the height at progress 0.
 */
@Composable
fun SheetGlanceLayout(
    progress: () -> Float,
    media: @Composable () -> Unit,
    identity: @Composable () -> Unit,
    secondary: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    arrangement: SheetGlanceArrangement = SheetGlanceArrangement.SIDE_BY_SIDE,
    growMedia: Boolean = true,
    mediaWidth: Dp = SheetGlanceDefaults.PhotoWidth,
    mediaAspect: Float = SheetGlanceDefaults.PHOTO_ASPECT,
    /** On short screens the grown media is capped so the text below it stays visible. */
    maxMediaHeight: Int = 0,
    status: @Composable () -> Unit = {},
    action: @Composable () -> Unit = {},
    actionCenter: Dp = 0.dp,
    onCollapsedHeight: (Int) -> Unit = {},
) {
    val currentOnCollapsedHeight by rememberUpdatedState(onCollapsedHeight)
    val currentGrowMedia by rememberUpdatedState(growMedia)
    val shownProgress = { if (currentGrowMedia) progress().coerceIn(0f, 1f) else 0f }

    Layout(
        contents = listOf(
            {
                Box(
                    modifier = Modifier.graphicsLayer {
                        clip = true
                        shape = RoundedCornerShape(bottomEnd = 16.dp * (1f - shownProgress()))
                    },
                ) { media() }
            },
            identity,
            secondary,
            status,
            action,
        ),
        modifier = modifier,
    ) { (mediaMeasurables, identityMeasurables, secondaryMeasurables, statusMeasurables, actionMeasurables), constraints ->
        val p = shownProgress()
        val textStage = (p / SHEET_GLANCE_TEXT_STAGE_END).coerceIn(0f, 1f)
        val mediaStage = sheetGlanceMediaStage(p)

        val width = constraints.maxWidth
        val padH = SheetGlanceDefaults.PadH.roundToPx()
        val padTop = 12.dp.roundToPx()
        val padBottom = 12.dp.roundToPx()
        val gap = 12.dp.roundToPx()
        val smallWidth = mediaWidth.roundToPx().coerceAtMost((width * 0.4f).roundToInt())

        val besideX = smallWidth + gap
        val besideWidth = (width - besideX - padH).coerceAtLeast(0)
        val belowWidth = (width - 2 * padH).coerceAtLeast(0)
        val textX = lerp(besideX, padH, mediaStage)

        val action = actionMeasurables.firstOrNull()?.measure(Constraints())
        val actionEnd = SheetGlanceDefaults.ActionEnd.roundToPx()
        // Stacked text runs up to the action, which overlaps the side padding by its own touch area
        val actionReserve = when {
            arrangement == SheetGlanceArrangement.STACKED && action != null -> (action.width + actionEnd - padH).coerceAtLeast(0)
            else -> 0
        }
        val collapsedIdentityWidth = (besideWidth - actionReserve).coerceAtLeast(0)
        val identityWidth = when (arrangement) {
            SheetGlanceArrangement.SIDE_BY_SIDE -> lerp(besideWidth, (belowWidth * 0.6f).roundToInt(), mediaStage)
            SheetGlanceArrangement.STACKED -> lerp(collapsedIdentityWidth, belowWidth - actionReserve, mediaStage)
        }.coerceAtLeast(0)
        val identity = identityMeasurables.first().measure(Constraints(maxWidth = identityWidth))

        // Side by side, the secondary lines first move to the end below the identity, then rise into its row
        val secondaryXStage = if (arrangement == SheetGlanceArrangement.SIDE_BY_SIDE) (mediaStage * 2f).coerceIn(0f, 1f) else 0f
        val secondaryYStage = if (arrangement == SheetGlanceArrangement.SIDE_BY_SIDE) (mediaStage * 2f - 1f).coerceIn(0f, 1f) else 0f
        val secondaryWidth = when (arrangement) {
            SheetGlanceArrangement.SIDE_BY_SIDE -> lerp(
                besideWidth,
                (width - padH - (textX + identity.width) - gap).coerceAtLeast(0),
                secondaryXStage,
            )

            SheetGlanceArrangement.STACKED -> lerp(besideWidth, belowWidth, mediaStage)
        }.coerceAtLeast(0)
        val secondaryLines = secondaryMeasurables.map { it.measure(Constraints(maxWidth = secondaryWidth)) }
        val secondaryHeight = secondaryLines.sumOf { it.height }

        // The media is at least as tall as the text beside it, a note or a large font scale crops it wider instead
        val collapsedTextHeight = when (arrangement) {
            // Their heights don't depend on the widths they got, see SIDE_BY_SIDE
            SheetGlanceArrangement.SIDE_BY_SIDE -> identity.height + secondaryHeight
            // Asked at the collapsed widths, text that wraps differently while the media grows keeps its height
            SheetGlanceArrangement.STACKED -> identityMeasurables.sumOf { it.minIntrinsicHeight(collapsedIdentityWidth) } +
                secondaryMeasurables.sumOf { it.minIntrinsicHeight(besideWidth) }
        }
        val smallHeight = max((smallWidth / mediaAspect).roundToInt(), padTop + collapsedTextHeight + padTop)
        val fullWidth = when {
            maxMediaHeight > 0 -> min(width, (maxMediaHeight * mediaAspect).roundToInt()).coerceAtLeast(smallWidth)
            else -> width
        }
        val fullHeight = (fullWidth / mediaAspect).roundToInt()
        val mediaWidthPx = lerp(smallWidth, fullWidth, mediaStage)
        val mediaHeight = lerp(smallHeight, fullHeight, mediaStage)
        val mediaX = lerp(0, (width - fullWidth) / 2, mediaStage)
        val mediaPlaceable = mediaMeasurables.first().measure(Constraints.fixed(mediaWidthPx, mediaHeight))

        val textY = lerp(padTop, mediaHeight + gap, textStage)
        val secondaryY = lerp(identity.height, 0, secondaryYStage)
        val textBottom = textY + max(identity.height, secondaryY + secondaryHeight)

        val status = statusMeasurables.firstOrNull()?.measure(Constraints.fixedWidth(belowWidth))
        val statusSpace = status?.let { gap + it.height } ?: 0

        currentOnCollapsedHeight(smallHeight + statusSpace + padBottom)

        val header = max(mediaHeight, textBottom)
        layout(width, header + statusSpace + padBottom) {
            mediaPlaceable.placeRelative(mediaX, 0)
            identity.placeRelative(textX, textY)
            var lineY = textY + secondaryY
            secondaryLines.forEach { line ->
                line.placeRelative(lerp(textX, width - padH - line.width, secondaryXStage), lineY)
                lineY += line.height
            }
            status?.placeRelative(padH, header + gap)
            if (action != null) {
                val actionTop = when (arrangement) {
                    SheetGlanceArrangement.SIDE_BY_SIDE -> padTop
                    SheetGlanceArrangement.STACKED -> textY
                }
                action.placeRelative(width - actionEnd - action.width, actionTop + actionCenter.roundToPx() - action.height / 2)
            }
        }
    }
}

/** The drag that opens the sheet ends there, its rest would scroll the grown media out of sight. */
@Composable
fun rememberSheetOpeningDrag(progress: () -> Float): NestedScrollConnection {
    val currentProgress by rememberUpdatedState(progress)
    return remember {
        object : NestedScrollConnection {
            var opening: Boolean? = null

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val isOpening = opening ?: (currentProgress() < 1f).also { opening = it }
                return if (isOpening && available.y < 0) available else Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val wasOpening = opening == true
                opening = null
                return if (wasOpening && available.y < 0) available else Velocity.Zero
            }
        }
    }
}

/**
 * How far a ModalBottomSheet is between its half-height stop (0) and expanded (1). Its content has to fill
 * the sheet and carry [modifier]: then the sheet expands to the top of the space above the keyboard and stops
 * halfway down it (Material3 1.4.0, ModalBottomSheet). Create it inside the sheet's content, the keyboard inset
 * belongs to the sheet's window.
 */
@Stable
class ModalSheetProgress(
    private val sheetState: SheetState,
    private val ime: WindowInsets,
    private val density: Density,
) : () -> Float {
    private var windowHeight by mutableIntStateOf(0)

    val modifier: Modifier = Modifier.onPlaced { windowHeight = it.findRootCoordinates().size.height }

    override fun invoke(): Float {
        val half = (windowHeight - ime.getBottom(density)) / 2f
        val offset = runCatching { sheetState.requireOffset() }.getOrNull()
        return when {
            offset == null || half <= 0f || !sheetState.hasPartiallyExpandedState ->
                if (sheetState.currentValue == SheetValue.Expanded) 1f else 0f

            else -> (1f - offset / half).coerceIn(0f, 1f)
        }
    }
}

@Composable
fun rememberModalSheetProgress(sheetState: SheetState): ModalSheetProgress {
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    return remember(sheetState, ime, density) { ModalSheetProgress(sheetState, ime, density) }
}

/**
 * What a sheet's drag handle offers to accessibility services, for a sheet that shows none. Over the sheet's top edge
 * and outside its scrolling content, so it stays reachable once the content scrolls.
 */
@Composable
fun BoxScope.SheetActionsAnchor(sheetState: SheetState, label: String, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(24.dp)
            .then(rememberSheetStateActions(sheetState, onDismiss))
            .semantics { contentDescription = label },
    )
}

@Composable
private fun rememberSheetStateActions(sheetState: SheetState, onDismiss: () -> Unit): Modifier {
    val scope = rememberCoroutineScope()
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val value = sheetState.currentValue
    val canCollapse = sheetState.hasPartiallyExpandedState
    return Modifier.semantics(mergeDescendants = true) {
        dismiss {
            scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) currentOnDismiss() }
            true
        }
        when {
            value == SheetValue.PartiallyExpanded -> expand {
                scope.launch { sheetState.expand() }
                true
            }

            value == SheetValue.Expanded && canCollapse -> collapse {
                scope.launch { sheetState.partialExpand() }
                true
            }
        }
    }
}
