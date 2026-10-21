package eu.darken.apl.common.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.ui.NavDisplay

/**
 * Shows the current tab's stack, with the stack back leads to below it.
 *
 * Every stack stays decorated while its tab is not shown, which keeps its ViewModels and saved state.
 */
@Composable
fun NavigationDisplay(
    state: NavigationState,
    sceneStrategy: SceneStrategy<NavKey>,
    entryProvider: (NavKey) -> NavEntry<NavKey>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val decorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
        rememberViewModelStoreNavEntryDecorator<NavKey>(),
    )
    val bootstrap = rememberDecoratedNavEntries(
        backStack = state.bootstrap,
        entryDecorators = decorators,
        entryProvider = remember(entryProvider) { entryProvider.scoped(BOOTSTRAP_SCOPE) },
    )
    val tabs = MainTab.entries.associateWith { tab ->
        key(tab) {
            rememberDecoratedNavEntries(
                backStack = state.stack(tab),
                entryDecorators = decorators,
                entryProvider = remember(entryProvider) { entryProvider.scoped(tab.name) },
            )
        }
    }

    val entries = if (state.inTabs) {
        state.backTarget()?.let { tabs.getValue(it) }.orEmpty() + tabs.getValue(state.currentTab)
    } else {
        bootstrap
    }

    NavDisplay(
        entries = entries,
        modifier = modifier,
        sceneStrategy = sceneStrategy,
        onBack = onBack,
    )
}

/**
 * Entry state is stored by content key, and the same destination can be open in two tabs,
 * e.g. an aircraft's actions sheet opened from Search and from the watch list.
 */
private fun ((NavKey) -> NavEntry<NavKey>).scoped(scope: String): (NavKey) -> NavEntry<NavKey> = { key ->
    val entry = this(key)
    NavEntry(key = key, contentKey = "$scope:${entry.contentKey}", metadata = entry.metadata) { entry.Content() }
}

private const val BOOTSTRAP_SCOPE = "BOOTSTRAP"
