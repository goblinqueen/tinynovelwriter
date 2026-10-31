package app.tinynovelwriter.format

/**
 * Typing replacements, ported from novelWriter's `TextAutoReplace` (text/autoreplace.py),
 * dashes only: `--` → en dash, `---` (or en dash + `-`) → em dash, `----` (or em dash +
 * `-`) → horizontal bar. Like novelWriter, it only acts right after a single typed character.
 */
object AutoReplace {

    /**
     * [old] and [new] are the text before and after an edit, [cursor] the caret after it.
     * Returns the replaced text and caret, or null if nothing changes.
     */
    fun afterTyping(old: String, new: String, cursor: Int): Pair<String, Int>? {
        if (new.length != old.length + 1 || cursor < 1 || cursor > new.length) return null
        if (!new.regionMatches(0, old, 0, cursor - 1) || !new.regionMatches(cursor, old, cursor - 1, new.length - cursor)) return null
        val lineStart = new.lastIndexOf('\n', cursor - 1) + 1
        val last = new.substring(maxOf(lineStart, cursor - 4), cursor)
        val (delete, insert) = dash(last) ?: return null
        return new.substring(0, cursor - delete) + insert + new.substring(cursor) to cursor - delete + insert.length
    }

    private fun dash(last: String): Pair<Int, String>? {
        if (!last.endsWith("-")) return null
        return when {
            last.endsWith("----") -> 4 to "―"
            last.endsWith("---") -> 3 to "—"
            last.endsWith("--") -> 2 to "–"
            last.endsWith("–-") -> 2 to "—"
            last.endsWith("—-") -> 2 to "―"
            else -> null
        }
    }
}
