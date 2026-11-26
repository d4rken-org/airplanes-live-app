package eu.darken.apl.map.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

private const val CREDIT_SEPARATOR = " · "

internal val MAP_CREDIT_DURATION = 5.seconds

/**
 * The map's [credits] as a line of text, shown while one of them has not yet been on screen for
 * [MAP_CREDIT_DURATION] with the style [styleKey]. A new style shows all of them again, and so does a
 * credit that left [credits] while not in [enabledOverlayCredits] and comes back: an overlay switched
 * off and on again shows its credit, one hidden while still enabled (history) doesn't. [onPillHeight]
 * keeps the last height, also while hidden.
 */
@Composable
internal fun MapCredit(
    /** Null while a style loads, which changes nothing. */
    styleKey: String?,
    credits: List<String>,
    enabledOverlayCredits: Set<String>,
    canShow: Boolean,
    onOpenUrl: (String) -> Unit,
    onShownChanged: (Boolean) -> Unit,
    onPillHeight: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keyed inside the saved value: rememberSaveable's own keys aren't compared on restore
    var saved by rememberSaveable(stateSaver = SeenCreditsSaver) {
        mutableStateOf(SeenCredits(styleKey, emptySet()))
    }
    // Starts with the current credits, so a restore drops nothing
    val previousCredits = remember { PreviousCredits(credits) }

    val departed = if (styleKey != null) {
        previousCredits.credits.filter { it !in credits && it !in enabledOverlayCredits }.toSet()
    } else {
        emptySet()
    }
    val current = saved
        .let { if (styleKey != null && styleKey != it.style) SeenCredits(styleKey, emptySet()) else it }
        .let { if (departed.isNotEmpty()) it.copy(seen = it.seen - departed) else it }
    SideEffect {
        previousCredits.credits = credits
        if (current != saved) saved = current
    }

    val shown = canShow && credits.any { it !in current.seen }
    val currentOnShownChanged by rememberUpdatedState(onShownChanged)
    val reportedShown = remember { ReportedShown() }
    SideEffect {
        if (reportedShown.shown != shown) {
            reportedShown.shown = shown
            currentOnShownChanged(shown)
        }
    }
    if (!shown) return

    // Leaves with the pill, a pill that couldn't show gets its full time the next time
    LaunchedEffect(styleKey, credits) {
        delay(MAP_CREDIT_DURATION)
        saved = saved.copy(seen = saved.seen + credits)
    }
    val currentOnOpenUrl by rememberUpdatedState(onOpenUrl)
    val currentOnPillHeight by rememberUpdatedState(onPillHeight)
    val text = remember(credits) {
        mapCreditText(credits, CREDIT_LINK_STYLES) { currentOnOpenUrl(it) }
    }
    Box(
        modifier = modifier
            .onSizeChanged { currentOnPillHeight(it.height) }
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text = text, color = Color.White, fontSize = 12.sp)
    }
}

private val CREDIT_LINK_STYLES = TextLinkStyles(SpanStyle(color = Color.White, textDecoration = TextDecoration.Underline))

private data class SeenCredits(val style: String?, val seen: Set<String>)

private val SeenCreditsSaver = listSaver<SeenCredits, String?>(
    save = { listOf(it.style) + it.seen },
    restore = { SeenCredits(it.first(), it.drop(1).filterNotNull().toSet()) },
)

private class PreviousCredits(var credits: List<String>)

private class ReportedShown {
    var shown: Boolean? = null
}

/**
 * Joins MapLibre source attributions (HTML) into one credit line.
 * Links call [onLink] instead of the platform UriHandler.
 */
internal fun mapCreditText(
    attributions: List<String>,
    linkStyles: TextLinkStyles?,
    onLink: (String) -> Unit,
): AnnotatedString {
    val listener = LinkInteractionListener { link ->
        if (link is LinkAnnotation.Url) onLink(link.url)
    }
    val parts = attributions
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .map { AnnotatedString.fromHtml(it, linkStyles, listener).trimmed() }
        .filter { it.isNotEmpty() }
    return buildAnnotatedString {
        parts.forEachIndexed { index, part ->
            if (index > 0) append(CREDIT_SEPARATOR)
            append(part)
        }
    }
}

private fun AnnotatedString.trimmed(): AnnotatedString {
    val start = text.indexOfFirst { !it.isWhitespace() }
    if (start < 0) return AnnotatedString("")
    val end = text.indexOfLast { !it.isWhitespace() } + 1
    return subSequence(start, end)
}
