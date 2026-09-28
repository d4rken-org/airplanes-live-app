package eu.darken.apl.common.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

/**
 * Renders the tab destinations above one shared [BottomNavBar].
 * All tabs share one scene key, so NavDisplay never transitions between tabs; the scene fades the content itself.
 */
class BottomNavSceneStrategy : SceneStrategy<NavKey> {

    override fun SceneStrategyScope<NavKey>.calculateScene(
        entries: List<NavEntry<NavKey>>,
    ): Scene<NavKey>? {
        val entry = entries.last()
        val tab = entry.metadata[KEY] as? Int ?: return null
        return BottomNavScene(
            entry = entry,
            tab = tab,
            previousEntries = entries.dropLast(1),
        )
    }

    private class BottomNavScene(
        private val entry: NavEntry<NavKey>,
        private val tab: Int,
        override val previousEntries: List<NavEntry<NavKey>>,
    ) : Scene<NavKey> {

        override val key: Any = KEY

        override val entries: List<NavEntry<NavKey>> = listOf(entry)

        override val content: @Composable () -> Unit = { BottomNavSceneContent(entry, tab) }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is BottomNavScene) return false
            return entry == other.entry && tab == other.tab && previousEntries == other.previousEntries
        }

        override fun hashCode(): Int = (entry.hashCode() * 31 + tab) * 31 + previousEntries.hashCode()
    }

    companion object {
        private const val KEY = "bottomNavTab"

        fun bottomNavTab(index: Int): Map<String, Any> = mapOf(KEY to index)
    }
}

/** Lets a tab hide the shared bar, e.g. the map in fullscreen. Keyed per entry so an outgoing tab can't hide it for the incoming one. */
private val LocalBottomNavBarHider = staticCompositionLocalOf<(Boolean) -> Unit> { {} }

@Composable
fun HideBottomNavBar(hidden: Boolean) {
    val setHidden = LocalBottomNavBarHider.current
    DisposableEffect(hidden) {
        setHidden(hidden)
        onDispose { setHidden(false) }
    }
}

@Composable
private fun BottomNavSceneContent(entry: NavEntry<NavKey>, tab: Int) {
    val hiddenBy = remember { mutableStateMapOf<Any, Boolean>() }

    Column(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = entry,
            contentKey = { it.contentKey },
            transitionSpec = { fadeIn(tween(TAB_FADE_MS)) togetherWith fadeOut(tween(TAB_FADE_MS)) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            label = "BottomNavTab",
        ) { tabEntry ->
            val key = tabEntry.contentKey
            val hider: (Boolean) -> Unit = remember(key) {
                { hidden -> if (hidden) hiddenBy[key] = true else hiddenBy.remove(key) }
            }
            Box(modifier = Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalBottomNavBarHider provides hider) {
                    tabEntry.Content()
                }
            }
        }
        if (hiddenBy[entry.contentKey] != true) {
            BottomNavBar(selectedTab = tab)
        }
    }
}

private const val TAB_FADE_MS = 200
