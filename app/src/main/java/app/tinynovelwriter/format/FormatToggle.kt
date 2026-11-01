package app.tinynovelwriter.format

/** Text plus selection after an edit; [start] == [end] is a plain caret. */
data class Edited(val text: String, val start: Int, val end: Int)

/**
 * Ctrl+B / Ctrl+I, ported from novelWriter's `GuiDocEditor._toggleFormat` with
 * `autoSelect` on: with no selection, the word around the caret is used. If the
 * selection is already wrapped in the marks they are removed, otherwise added. A
 * selection over several lines only uses its first line.
 */
object FormatToggle {

    fun bold(text: String, start: Int, end: Int) = toggle(text, start, end, '*', 2)

    fun italic(text: String, start: Int, end: Int) = toggle(text, start, end, '_', 1)

    private enum class Select { KEEP_SELECTION, KEEP_POSITION, MOVE_AFTER }

    /** Returns null when nothing changes. [start] is the caret (the selection's active end is not tracked). */
    fun toggle(text: String, start: Int, end: Int, mark: Char, count: Int): Edited? {
        val caret = end
        var posS = minOf(start, end)
        var posE = maxOf(start, end)
        val select = if (posS != posE) {
            Select.KEEP_SELECTION
        } else {
            val (wordS, wordE) = word(text, caret)
            posS = wordS
            posE = wordE
            if (wordS != wordE && caret == wordE) Select.MOVE_AFTER else Select.KEEP_POSITION
        }
        if (posS == posE && caret > 0 && text[caret - 1] == mark) return null // format repetition

        val lineEnd = text.indexOf('\n', posS).let { if (it < 0) text.length else it }
        if (posE > lineEnd) posE = lineEnd

        val before = (1..count).takeWhile { posS - it >= 0 && text[posS - it] == mark }.size
        val after = (0 until count).takeWhile { posE + it < text.length && text[posE + it] == mark }.size
        val marks = mark.toString().repeat(count)

        return if (minOf(before, after) == count) {
            val result = text.substring(0, posS - count) + text.substring(posS, posE) + text.substring(posE + count)
            if (select == Select.KEEP_SELECTION) Edited(result, posS - count, posE - count)
            else (caret - count).coerceIn(0, result.length).let { Edited(result, it, it) }
        } else {
            val result = text.substring(0, posS) + marks + text.substring(posS, posE) + marks + text.substring(posE)
            when (select) {
                Select.KEEP_SELECTION -> Edited(result, posS + count, posE + count)
                Select.MOVE_AFTER -> (posE + 2 * count).let { Edited(result, it, it) }
                Select.KEEP_POSITION -> (caret + count).let { Edited(result, it, it) }
            }
        }
    }

    /** The word around [caret] within its line: letters and digits, plus apostrophes inside words. */
    private fun word(text: String, caret: Int): Pair<Int, Int> {
        fun at(i: Int) = if (i in text.indices) text[i] else '\n'
        fun isApostrophe(c: Char) = c == '\'' || c == '’'
        var s = caret
        while (s > 0 && at(s - 1) != '\n') {
            val one = at(s - 1)
            if (!(one.isLetterOrDigit() || (isApostrophe(one) && at(s - 2).isLetterOrDigit()))) break
            s--
        }
        var e = caret
        while (true) {
            val one = at(e)
            if (!(one.isLetterOrDigit() || (isApostrophe(one) && at(e + 1).isLetterOrDigit()))) break
            e++
        }
        return if (e > s) s to e else caret to caret
    }
}
