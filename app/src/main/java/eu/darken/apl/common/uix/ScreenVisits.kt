package eu.darken.apl.common.uix

import java.util.concurrent.atomic.AtomicInteger

/**
 * The visits of a screen whose ViewModel outlives it, e.g. a tab. Feedback from work started in one
 * visit (a snackbar, a prompt, opening another screen) is dropped once that visit is over:
 *
 * ```
 * shown()            // visit 1
 * val v = current()  // 1, an action starts, then suspends
 * hidden()
 * shown()            // visit 2
 * isCurrent(v)       // false, the action's result says nothing
 * ```
 */
class ScreenVisits {
    private val visits = AtomicInteger()

    @Volatile private var shown: Int? = null

    fun shown() {
        shown = visits.incrementAndGet()
    }

    fun hidden() {
        shown = null
    }

    /** Taken when an action starts, null while the screen is not shown. */
    fun current(): Int? = shown

    fun isCurrent(visit: Int?): Boolean = visit != null && visit == shown
}
