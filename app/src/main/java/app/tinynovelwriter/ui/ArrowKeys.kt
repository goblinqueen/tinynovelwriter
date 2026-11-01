package app.tinynovelwriter.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import app.tinynovelwriter.format.FormatToggle
import java.text.BreakIterator

/**
 * Left/right arrows from a hardware keyboard. Android delivers them as D-pad keys and
 * Compose's text field lets them move focus to the buttons instead of the cursor, so
 * the editor handles them itself: move by character, Ctrl by word, Shift to select.
 * Returns the new value, or null if the event isn't a left/right arrow.
 */
fun horizontalArrow(event: KeyEvent, value: TextFieldValue): TextFieldValue? {
    val left = event.key == Key.DirectionLeft
    if (!left && event.key != Key.DirectionRight) return null
    if (event.type != KeyEventType.KeyDown) return value // swallow key-up too, so focus never moves

    val text = value.text
    val sel = value.selection
    val target = when {
        // Without Shift, a selection collapses to the side the arrow points to.
        !sel.collapsed && !event.isShiftPressed -> if (left) sel.min else sel.max
        event.isCtrlPressed -> if (left) previousWord(text, sel.end) else nextWord(text, sel.end)
        left -> if (sel.end == 0) 0 else text.offsetByCodePoints(sel.end, -1)
        else -> if (sel.end == text.length) text.length else text.offsetByCodePoints(sel.end, 1)
    }
    val selection = if (event.isShiftPressed) TextRange(sel.start, target) else TextRange(target)
    return value.copy(selection = selection)
}

/**
 * Alt+Left/Right and Home/End: start or end of the visual line; Ctrl+Home/End: start or
 * end of the document. Shift selects. Returns null for other keys. Must run before
 * [horizontalArrow], which would otherwise take Alt+Left/Right as plain arrows.
 */
fun lineEdgeKey(event: KeyEvent, value: TextFieldValue, layout: TextLayoutResult?): TextFieldValue? {
    val toStart = when {
        event.key == Key.MoveHome || (event.isAltPressed && event.key == Key.DirectionLeft) -> true
        event.key == Key.MoveEnd || (event.isAltPressed && event.key == Key.DirectionRight) -> false
        else -> return null
    }
    if (event.type != KeyEventType.KeyDown) return value

    val text = value.text
    val sel = value.selection
    val cursor = sel.end.coerceIn(0, text.length)
    val target = when {
        event.isCtrlPressed -> if (toStart) 0 else text.length
        layout == null -> cursor
        else -> {
            val line = layout.getLineForOffset(cursor)
            if (toStart) {
                layout.getLineStart(line)
            } else {
                // visibleEnd skips the trailing space of a wrapped line, so the cursor stays on this line.
                layout.getLineEnd(line, visibleEnd = true)
            }
        }
    }.coerceIn(0, text.length)
    val selection = if (event.isShiftPressed) TextRange(sel.start, target) else TextRange(target)
    return value.copy(selection = selection)
}

/**
 * Up/down arrows from a hardware keyboard, for the same reason as [horizontalArrow]:
 * Compose moves focus out of the field at the first line. Moves one visual line using
 * the text layout, keeping the column across repeated presses; Shift selects.
 */
class VerticalArrows {
    /** The field's latest layout; set from BasicTextField's onTextLayout. */
    var layout: TextLayoutResult? = null
    /** Horizontal position to aim for while moving up/down in a row, so short lines don't lose the column. */
    private var goalX: Float? = null
    private var lastValue: TextFieldValue? = null

    fun handle(event: KeyEvent, value: TextFieldValue): TextFieldValue? {
        val up = event.key == Key.DirectionUp
        if (!up && event.key != Key.DirectionDown) return null
        if (event.type != KeyEventType.KeyDown) return value
        val layout = layout ?: return value

        val sel = value.selection
        val text = value.text
        val cursor = sel.end.coerceIn(0, text.length)
        // A move from anywhere else (typing, tapping, left/right) starts a new column.
        if (value != lastValue) goalX = null
        val x = goalX ?: layout.getHorizontalPosition(cursor, usePrimaryDirection = true)
        goalX = x

        val line = layout.getLineForOffset(cursor)
        val target = when {
            up && line == 0 -> 0
            !up && line == layout.lineCount - 1 -> text.length
            else -> {
                val targetLine = if (up) line - 1 else line + 1
                val y = (layout.getLineTop(targetLine) + layout.getLineBottom(targetLine)) / 2
                layout.getOffsetForPosition(Offset(x, y)).coerceIn(0, text.length)
            }
        }
        val selection = if (event.isShiftPressed) TextRange(sel.start, target) else TextRange(target)
        return value.copy(selection = selection).also { lastValue = it }
    }
}

private fun previousWord(text: String, from: Int): Int {
    val words = BreakIterator.getWordInstance().apply { setText(text) }
    var pos = words.preceding(from)
    // Skip back over spaces and punctuation to the start of a word.
    while (pos > 0 && !text[pos].isLetterOrDigit()) pos = words.preceding(pos)
    return pos.coerceAtLeast(0)
}

private fun nextWord(text: String, from: Int): Int {
    val words = BreakIterator.getWordInstance().apply { setText(text) }
    var pos = words.following(from)
    // Stop at the end of a word rather than after the following spaces.
    while (pos != BreakIterator.DONE && pos < text.length && !text[pos - 1].isLetterOrDigit()) pos = words.following(pos)
    return if (pos == BreakIterator.DONE) text.length else pos
}

/** Ctrl+B / Ctrl+I toggle bold / italic like novelWriter. Returns null for other keys. */
fun formatKey(event: KeyEvent, value: TextFieldValue): TextFieldValue? {
    if (!event.isCtrlPressed || event.isAltPressed || event.isShiftPressed) return null
    val toggle = when (event.key) {
        Key.B -> FormatToggle::bold
        Key.I -> FormatToggle::italic
        else -> return null
    }
    if (event.type != KeyEventType.KeyDown) return value
    val edited = toggle(value.text, value.selection.start, value.selection.end) ?: return value
    return TextFieldValue(edited.text, TextRange(edited.start, edited.end))
}
