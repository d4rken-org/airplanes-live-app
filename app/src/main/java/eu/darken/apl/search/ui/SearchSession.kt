package eu.darken.apl.search.ui

import android.location.Location
import eu.darken.apl.search.core.SearchInput
import eu.darken.apl.search.core.SearchRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The search tab's screen state. Switching tabs discards the tab's ViewModel,
 * coming back has to show the same results without searching again.
 */
@Singleton
class SearchSession @Inject constructor() {
    val input = MutableStateFlow<SearchInput?>(null)
    val result = MutableStateFlow<ShownResult?>(null)
    val isSearching = MutableStateFlow(false)

    /** Only the newest submit may publish, a cleared or replaced search must not reappear. */
    val generation = AtomicInteger()

    /** Serializes input changes, the start of a submit and every publish, see [generation]. */
    val inputLock = Mutex()

    private val revisions = AtomicInteger()
    private val claimedError = AtomicReference<Throwable?>(null)

    @Volatile var draft: Draft? = null

    @Volatile private var screen = ScreenState()

    val revision: Int
        get() = result.value?.revision ?: 0

    /** Call with [inputLock] held. */
    fun publish(result: SearchRepo.SearchResult, origin: Location?) {
        this.result.value = ShownResult(result, origin, revision = revisions.incrementAndGet())
    }

    /** Call with [inputLock] held. */
    fun resetResults() {
        generation.incrementAndGet()
        result.value = null
        isSearching.value = false
    }

    /** What the screen saved while showing [revision], fresh state once a newer result replaced it. */
    fun screenState(revision: Int): ScreenState = screen.takeIf { it.revision == revision } ?: ScreenState(revision)

    /** Ignored for a result that is no longer shown, e.g. a screen disposed after a newer search landed. */
    @Synchronized
    fun saveScreenState(revision: Int, change: (ScreenState) -> ScreenState) {
        if (revision != this.revision) return
        screen = change(screenState(revision))
    }

    /** True for the first caller only, so each error is shown once even while two screens overlap. */
    fun claimError(error: Throwable): Boolean = claimedError.getAndSet(error) !== error

    /** A nearby result measures distances from the spot that was searched, not from the device. */
    data class ShownResult(
        val result: SearchRepo.SearchResult,
        val origin: Location? = null,
        val revision: Int = 0,
    )

    data class ScreenState(
        val revision: Int = 0,
        val selection: Set<String> = emptySet(),
        val gridPosition: GridPosition = GridPosition(),
    )

    data class GridPosition(val index: Int = 0, val offset: Int = 0)

    /** Typed but not submitted, only valid while the input still holds [base]. */
    data class Draft(val base: String, val text: String)
}
