package eu.darken.apl.map.core

/**
 * How the history's layers follow the cursor. [Update.Settle] splits the whole day at the cursor and
 * starts a new epoch, moving on from there only reports what was passed, to be marked in that epoch.
 *
 * Keep one per map, not per drawing pass: the marks stay on the map's sources, and only a fresh
 * epoch keeps those of an earlier pass from counting again.
 */
class HistoryProgress {

    sealed interface Update {
        data object None : Update

        /** No history, its layers show nothing. */
        data object Clear : Update

        data class Settle(val epoch: Int) : Update

        data class Mark(val epoch: Int, val passed: HistoryTimeline.Passed) : Update
    }

    private var epoch = 0
    private var settledDay: HistoryTimeline.Day? = null
    private var settledAt: Long? = null
    private var drawn: Long? = null
    private var cleared = false

    /** The layers were written anew, nothing of them is drawn yet. */
    fun reset() {
        settledDay = null
        settledAt = null
        drawn = null
        cleared = false
    }

    /** [settle] splits at [cursor] at once instead of marking up to it. */
    fun next(day: HistoryTimeline.Day?, cursor: Long?, settle: Boolean): Update {
        if (day == null || cursor == null) {
            if (cleared) return Update.None
            reset()
            cleared = true
            return Update.Clear
        }
        cleared = false
        val last = drawn
        val update = when {
            day !== settledDay || last == null || cursor < last || (settle && cursor != settledAt) -> {
                epoch++
                settledDay = day
                settledAt = cursor
                Update.Settle(epoch)
            }

            cursor == last -> return Update.None
            else -> Update.Mark(epoch, day.passed(last, cursor))
        }
        drawn = cursor
        return update
    }
}
