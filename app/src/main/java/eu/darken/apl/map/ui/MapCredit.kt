package eu.darken.apl.map.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

private val TAG = logTag("Map", "Credit")

private const val CREDIT_SEPARATOR = " · "

internal val MAP_CREDIT_DURATION = 5.seconds

/**
 * The [credits] not yet in [seenCredits] as text, until they have been on screen for [MAP_CREDIT_DURATION] or
 * the map is touched while they show. An overlay's credit shows only while it is in [enabledOverlayCredits], so a
 * switched-off overlay whose source stays on the map for a frame doesn't flash.
 */
@Composable
internal fun MapCredit(
    /** A change restarts the time of a showing. */
    styleKey: String?,
    credits: List<String>,
    /** Null while the overlays aren't known yet, which changes nothing. */
    enabledOverlayCredits: Set<String>?,
    canShow: Boolean,
    darkStyle: Boolean,
    /** The height it may take; the credit waits while it needs more. */
    room: Dp,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
    seenCredits: SeenMapCredits = SeenMapCredits.process,
) {
    val seen = seenCredits.seenWith(enabledOverlayCredits)
    val overlayCredits = seenCredits.overlayCreditsWith(enabledOverlayCredits)
    val candidates = credits
        .filter { it !in seen && (it !in overlayCredits || it in enabledOverlayCredits.orEmpty()) }
        .distinct()
    val textColor = if (darkStyle) Color.White else Color(0xFF1F1F1F)
    val textStyle = LocalTextStyle.current.copy(color = textColor, fontSize = 11.sp, lineHeight = 16.sp)
    val textMeasurer = rememberTextMeasurer()
    val measureText = remember(candidates, overlayCredits) {
        mapCreditText(candidates, overlayCredits, linkStyles = null) {}
    }
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val fits = candidates.isNotEmpty() && with(density) {
            val textWidth = if (constraints.hasBoundedWidth) {
                (constraints.maxWidth - (CREDIT_PADDING_HORIZONTAL * 2).roundToPx()).coerceAtLeast(0)
            } else {
                Constraints.Infinity
            }
            val measured = textMeasurer.measure(
                text = measureText,
                style = textStyle,
                constraints = Constraints(maxWidth = textWidth),
            )
            measured.size.height + (CREDIT_PADDING_VERTICAL * 2).roundToPx() <= room.roundToPx()
        }
        val displayed = if (canShow && fits) candidates else emptyList()
        SideEffect {
            seenCredits.updateEnabledOverlays(enabledOverlayCredits)
            seenCredits.displayed = displayed
        }
        DisposableEffect(seenCredits) {
            onDispose { seenCredits.displayed = emptyList() }
        }
        if (displayed.isEmpty()) return@BoxWithConstraints

        // Leaves with the credit, a credit that couldn't show gets its full time the next time
        LaunchedEffect(styleKey, displayed) {
            delay(MAP_CREDIT_DURATION)
            log(TAG) { "Map credit shown for its time (${displayed.size})" }
            seenCredits.markSeen(displayed)
        }
        val currentOnOpenUrl by rememberUpdatedState(onOpenUrl)
        val linkStyles = remember(textColor) {
            TextLinkStyles(SpanStyle(color = textColor, textDecoration = TextDecoration.Underline))
        }
        val text = remember(displayed, overlayCredits, linkStyles) {
            mapCreditText(displayed, overlayCredits, linkStyles) { currentOnOpenUrl(it) }
        }
        val backdrop = if (darkStyle) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.75f)
        Box(
            modifier = Modifier
                .background(backdrop, RoundedCornerShape(4.dp))
                .padding(horizontal = CREDIT_PADDING_HORIZONTAL, vertical = CREDIT_PADDING_VERTICAL),
        ) {
            Text(text = text, style = textStyle)
        }
    }
}

private val CREDIT_PADDING_HORIZONTAL = 4.dp
private val CREDIT_PADDING_VERTICAL = 2.dp

/**
 * The map credits (raw attributions) that have been on screen, kept for the process: neither a new style
 * nor a new map screen shows them again.
 */
@Stable
internal class SeenMapCredits {
    private var seen by mutableStateOf(emptySet<String>())

    /** Every credit that has been in a set of enabled overlays. */
    private var overlayCredits by mutableStateOf(emptySet<String>())
    private var enabledOverlayCredits: Set<String>? = null

    /** What [MapCredit] shows right now. */
    var displayed: List<String> = emptyList()

    fun markSeen(credits: Collection<String>) {
        seen = seen + credits
    }

    fun forget(credits: Collection<String>) {
        seen = seen - credits.toSet()
    }

    fun markDisplayedSeen() {
        val batch = displayed
        if (batch.isEmpty()) return
        log(TAG) { "Map credit hidden by a touch on the map (${batch.size})" }
        displayed = emptyList()
        markSeen(batch)
    }

    fun seenWith(enabled: Set<String>?): Set<String> = seen - switchedOff(enabled)

    fun overlayCreditsWith(enabled: Set<String>?): Set<String> = overlayCredits + enabled.orEmpty()

    /** An overlay switched off loses its seen credit, so switching it on again shows it again. */
    fun updateEnabledOverlays(enabled: Set<String>?) {
        if (enabled == null) return
        forget(switchedOff(enabled))
        overlayCredits = overlayCreditsWith(enabled)
        enabledOverlayCredits = enabled
    }

    private fun switchedOff(enabled: Set<String>?): Set<String> {
        val previous = enabledOverlayCredits ?: return emptySet()
        if (enabled == null) return emptySet()
        return previous - enabled
    }

    companion object {
        val process = SeenMapCredits()
    }
}

/**
 * Joins MapLibre source attributions (HTML) into one credit line.
 * Links call [onLink] instead of the platform UriHandler.
 * A base map credit, one not in [overlayCredits], drops a leading OpenFreeMap before OpenMapTiles and
 * OpenStreetMap: "OpenFreeMap © OpenMapTiles Data from OpenStreetMap" reads "© OpenMapTiles Data from OpenStreetMap".
 */
internal fun mapCreditText(
    attributions: List<String>,
    overlayCredits: Set<String>,
    linkStyles: TextLinkStyles?,
    onLink: (String) -> Unit,
): AnnotatedString {
    val listener = LinkInteractionListener { link ->
        if (link is LinkAnnotation.Url) onLink(link.url)
    }
    val overlays = overlayCredits.map { it.trim() }.toSet()
    val parts = attributions
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .map { attribution ->
            val part = AnnotatedString.fromHtml(attribution, linkStyles, listener).trimmed()
            if (attribution in overlays) part else part.withoutOpenFreeMap()
        }
        .filter { it.isNotEmpty() }
    return buildAnnotatedString {
        parts.forEachIndexed { index, part ->
            if (index > 0) append(CREDIT_SEPARATOR)
            append(part)
        }
    }
}

private val OPEN_FREE_MAP_PREFIX = Regex("""^OpenFreeMap\s+""")

private fun AnnotatedString.withoutOpenFreeMap(): AnnotatedString {
    val prefix = OPEN_FREE_MAP_PREFIX.find(text) ?: return this
    val rest = text.substring(prefix.range.last + 1)
    if ("OpenMapTiles" !in rest || "OpenStreetMap" !in rest) return this
    return subSequence(prefix.range.last + 1, length)
}

private fun AnnotatedString.trimmed(): AnnotatedString {
    val start = text.indexOfFirst { !it.isWhitespace() }
    if (start < 0) return AnnotatedString("")
    val end = text.indexOfLast { !it.isWhitespace() } + 1
    return subSequence(start, end)
}
