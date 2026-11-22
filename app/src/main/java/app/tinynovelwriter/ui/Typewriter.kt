package app.tinynovelwriter.ui

import kotlin.math.roundToInt

/**
 * Typewriter scrolling: while typing, the caret line is held at the position set in Settings.
 * Source: novelWriter 26.3b1, `novelwriter/editor/editor.py` `GuiDocEditor.keyPressEvent` (autoScroll), `updateDocMargins`.
 */
object Typewriter {

    /** Where the typing line is held, in percent of the visible height. */
    const val DEFAULT_POSITION = 60
    /** The range allowed in Settings, as in novelWriter (`novelwriter/dialogs/preferences.py`). */
    val POSITIONS = 10..90

    /** Length of the scroll, as in novelWriter (GuiDocEditor.keyPressEvent). */
    const val DURATION_MS = 120

    /** New scroll position after an edit, or null to leave it. Tops are the caret's, in content pixels; [position] is in percent. */
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

    /** Where the caret goes when editing starts: null for the end of the text if it's on screen, else the y of the line at [position]. */
    fun editStartY(scroll: Int, viewport: Int, textBottom: Float, position: Int = DEFAULT_POSITION): Float? =
        if (viewport <= 0 || textBottom <= scroll + viewport) null
        else scroll + viewport * position / 100f
}
