package app.tinynovelwriter.ui

import kotlin.math.roundToInt

/**
 * Typewriter scrolling, after novelWriter's `autoScroll` (GuiDocEditor.keyPressEvent):
 * when typing moves the caret to another line, the page scrolls with it so the line
 * stays put. novelWriter holds the line wherever it is below its 30% setting; a phone
 * shows few lines, so here the caret is held at the position set in Settings
 * (default [DEFAULT_POSITION] %), and typing below it glides the line up to it.
 */
object Typewriter {

    /** Where the typing line is held, in percent of the visible height. */
    const val DEFAULT_POSITION = 60
    /** The range allowed in Settings, as in novelWriter. */
    val POSITIONS = 10..90

    /** Length of the scroll, as in novelWriter. */
    const val DURATION_MS = 120

    /**
     * The new scroll position after an edit, or null to leave it. [oldTop] and [newTop]
     * are the caret's top before and after, in content pixels; [scroll] and [viewport]
     * are the current scroll position and visible height; [position] is in percent.
     */
    fun target(oldTop: Float, newTop: Float, scroll: Int, viewport: Int, position: Int = DEFAULT_POSITION): Int? {
        val move = newTop - oldTop
        if (move == 0f || viewport <= 0) return null
        val comfort = viewport * position / 100f
        return when {
            move > 0 && newTop - scroll > comfort -> (newTop - comfort).roundToInt()
            // Joining lines: scroll back so the line stays put, as novelWriter does.
            move < 0 && oldTop - scroll >= comfort -> (scroll + move).roundToInt().coerceAtLeast(0)
            else -> null
        }
    }
}
