package eu.darken.apl.common.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

/**
 * Shows sheets and dialogs over the screen below them. Their entries draw their own window.
 *
 * A sheet takes the place of the sheets below it, Back shows them again. A dialog keeps showing what is below it.
 */
class OverlaySceneStrategy : SceneStrategy<NavKey> {

    override fun SceneStrategyScope<NavKey>.calculateScene(
        entries: List<NavEntry<NavKey>>,
    ): Scene<NavKey>? {
        val top = entries.last()
        val overlay = top.overlay ?: return null
        val below = entries.dropLast(1)
        val overlaid = when (overlay) {
            Overlay.SHEET -> below.dropLastWhile { it.overlay != null }
            Overlay.DIALOG -> below
        }
        if (overlaid.isEmpty()) return null
        return OverlayEntryScene(entry = top, overlaidEntries = overlaid)
    }

    private class OverlayEntryScene(
        private val entry: NavEntry<NavKey>,
        override val overlaidEntries: List<NavEntry<NavKey>>,
    ) : OverlayScene<NavKey> {

        override val key: Any = entry.contentKey

        override val entries: List<NavEntry<NavKey>> = listOf(entry)

        override val previousEntries: List<NavEntry<NavKey>> = emptyList()

        override val content: @Composable () -> Unit = { entry.Content() }
    }

    private enum class Overlay { SHEET, DIALOG }

    companion object {
        private const val KEY = "overlay"

        private val NavEntry<*>.overlay: Overlay?
            get() = metadata[KEY] as? Overlay

        fun sheet(): Map<String, Any> = mapOf(KEY to Overlay.SHEET)

        fun dialog(): Map<String, Any> = mapOf(KEY to Overlay.DIALOG)

        /** Whether a sheet is shown over the screen at the bottom of [entries]. */
        fun isSheetShown(entries: List<NavEntry<*>>): Boolean =
            entries.takeLastWhile { it.overlay != null }.any { it.overlay == Overlay.SHEET }
    }
}

/** True while a sheet covers this tab's screen, e.g. the watch details opened from the map. */
val LocalCoveredBySheet = compositionLocalOf { false }

/** Per tab, so a tab fading out during a tab switch still sees its own stack. */
internal val LocalSheetCoveredTabs = compositionLocalOf { emptySet<NavTab>() }
