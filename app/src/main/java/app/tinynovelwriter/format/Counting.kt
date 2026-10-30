package app.tinynovelwriter.format

/** Characters, words and paragraphs, as novelWriter's standardCounter returns them. */
data class TextCounts(val chars: Int, val words: Int, val paragraphs: Int)

/**
 * Port of novelWriter's `text/counting.py`, so counts match the desktop app. Headings
 * count as words; comments (`%`) and keywords (`@`) don't; shortcodes are stripped.
 */
object Counting {

    private val RX_SC = Regex("""(?i)(?<!\\)(\[(?:b|/b|i|/i|s|/s|u|/u|m|/m|sup|/sup|sub|/sub|br)\])""")
    private val RX_SV = Regex("""(?i)(?<!\\)(\[(?:footnote|field):)(.+?)(?<!\\)(\])""")
    private val RX_LO = Regex("""(?i)(?<!\\)(\[(?:vspace|newpage|new page)(:\d+)?)(?<!\\)(\])""")

    private val HEADING_PREFIXES = listOf("#### ", "### ", "## ", "# ", "#! ", "##! ", "###! ")

    fun standardCounter(text: String): TextCounts {
        var chars = 0
        var words = 0
        var paragraphs = 0
        var prevEmpty = true

        for (raw in preProcessText(text)) {
            var line = raw
            var countPara = true
            if (line.isEmpty()) {
                prevEmpty = true
                continue
            }
            if (line[0] == '#') {
                HEADING_PREFIXES.firstOrNull { line.startsWith(it) }?.let {
                    line = line.substring(it.length)
                    countPara = false
                }
            }
            words += pySplit(line).size
            chars += line.codePointCount(0, line.length)
            if (countPara && prevEmpty) paragraphs++
            prevEmpty = !countPara
        }
        return TextCounts(chars, words, paragraphs)
    }

    internal fun preProcessText(input: String, keepHeaders: Boolean = true): List<String> {
        // Dashes separate words.
        val text = input.replace('–', ' ').replace('—', ' ')
        val ignore = if (keepHeaders) "%@" else "%@#"

        val result = mutableListOf<String>()
        for (rawLine in pySplitLines(text)) {
            var line = rawLine.trimEnd(::pyIsSpace)
            if (line.isNotEmpty()) {
                if (line[0] in ignore && !line.startsWith("#. ")) continue
                line = when {
                    line[0] == '>' -> line.trimStart('>').trimStart(' ')
                    line.startsWith("* ") -> line.substring(2)
                    line.startsWith("#. ") -> line.substring(3)
                    else -> line
                }
            }
            if (line.isNotEmpty()) {
                if (line.last() == '<') line = line.trimEnd('<').trimEnd(' ')
                if ('[' in line) {
                    line = RX_SC.replace(line, "")
                    line = RX_SV.replace(line, "")
                    line = RX_LO.replace(line, "")
                }
            }
            result += line
        }
        return result
    }

    /** Python's str.isspace() for a single character. */
    private fun pyIsSpace(c: Char): Boolean = c.isWhitespace() || c == '\u0085'

    /** Python's str.split() with no arguments. */
    private fun pySplit(line: String): List<String> {
        val parts = mutableListOf<String>()
        var start = -1
        for (i in line.indices) {
            if (pyIsSpace(line[i])) {
                if (start >= 0) parts += line.substring(start, i)
                start = -1
            } else if (start < 0) {
                start = i
            }
        }
        if (start >= 0) parts += line.substring(start)
        return parts
    }

    /** Python's str.splitlines(): no trailing empty line, many line separators. */
    private fun pySplitLines(text: String): List<String> {
        val lines = mutableListOf<String>()
        var start = 0
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c in LINE_BREAKS) {
                lines += text.substring(start, i)
                if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                start = i + 1
            }
            i++
        }
        if (start < text.length) lines += text.substring(start)
        return lines
    }

    private val LINE_BREAKS = setOf(
        '\n', '\r', '\u000B', '\u000C', '\u001C', '\u001D', '\u001E', '\u0085', ' ', ' '
    )
}
