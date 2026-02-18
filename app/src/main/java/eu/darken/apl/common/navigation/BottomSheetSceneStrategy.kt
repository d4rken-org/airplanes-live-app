package eu.darken.apl.common.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

class BottomSheetSceneStrategy : SceneStrategy<NavKey> {

    override fun SceneStrategyScope<NavKey>.calculateScene(
        entries: List<NavEntry<NavKey>>,
    ): Scene<NavKey>? = if (entries.size >= 2 && entries.last().metadata[KEY] == true) {
        BottomSheetScene(
            entries = entries,
            sheetEntry = entries.last(),
            overlaidEntries = entries.dropLast(1),
        )
    } else {
        null
    }

    private class BottomSheetScene(
        override val entries: List<NavEntry<NavKey>>,
        private val sheetEntry: NavEntry<NavKey>,
        override val overlaidEntries: List<NavEntry<NavKey>>,
    ) : OverlayScene<NavKey> {

        override val key: Any = sheetEntry.contentKey ?: sheetEntry.hashCode().toString()

        override val previousEntries: List<NavEntry<NavKey>> = emptyList()

        override val content: @Composable () -> Unit = {
            overlaidEntries.lastOrNull()?.Content()
            sheetEntry.Content()
        }
    }

    companion object {
        private const val KEY = "bottomSheet"

        fun bottomSheet(): Map<String, Any> = mapOf(KEY to true)
    }
}
