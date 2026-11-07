package app.tinynovelwriter.format

/**
 * What a stretch of text is, for highlighting. Mirrors novelWriter's editor formats.
 * Source: novelWriter 26.3b1, `novelwriter/editor/highlighter.py` `GuiDocHighlighter.initHighlighter`.
 */
enum class Markup {
    HEADING_MARK, HEADING, BOLD, ITALIC, STRIKE, MARK, NOBREAK, MARKUP, HIDDEN,
    LINK, NOTE, CODE, KEYWORD, TAG, VALUE, OPTIONAL, MODIFIER, REPLACE,
}

/** [start] inclusive, [end] exclusive, as offsets into the whole text. */
data class MarkupSpan(val start: Int, val end: Int, val kind: Markup)

/**
 * Port of novelWriter's editor highlighter, with the user's settings: `**` bold only,
 * emphasis coloured, no dialogue highlighting.
 * Differences: tags can't be checked against the index, so every value of a valid
 * keyword counts as a good tag; invalid keywords and values get no style (novelWriter
 * gives them a squiggly underline).
 *
 * Spans are meant to be applied in order, each merged over the earlier ones, like
 * `QTextCharFormat.merge`. Markup characters (`**`, `_`, brackets) take no later styles.
 *
 * Source: novelWriter 26.3b1, `novelwriter/editor/highlighter.py` `GuiDocHighlighter.highlightBlock`.
 */
object Highlighter {

    private const val MAX_LINE_LENGTH = 20000 // highlighter.py MAX_BLOCK_LENGTH

    // Python's \w and \s are Unicode-aware; Java's (and Android's) aren't the same, so spell them out.
    private const val WC = """\p{L}\p{N}_"""
    private const val SC = """\s\u0085   -     　"""

    private fun emphasis(mark: String, char: String) =
        Regex("""(?<![$WC\\])($mark)(?![$SC$char])(.+?)(?<![$SC\\])(\1)(?![$WC])""")

    private class Rule(val regex: Regex, val groups: Map<Int, Markup>)

    // Patterns from novelWriter 26.3b1: `novelwriter/text/patterns.py` (REGEX_PATTERNS) and
    // `novelwriter/constants.py` (nwRegEx); which groups get which style, and the rule
    // order, from GuiDocHighlighter.initHighlighter.

    private val NOBREAK = Rule(Regex("[  ]+"), mapOf(0 to Markup.NOBREAK))
    private val ITALIC = Rule(emphasis("_", "_"), mapOf(1 to Markup.MARKUP, 2 to Markup.ITALIC, 3 to Markup.MARKUP))
    private val BOLD = Rule(emphasis("""\*{2}""", """\*"""), mapOf(1 to Markup.MARKUP, 2 to Markup.BOLD, 3 to Markup.MARKUP))
    private val STRIKE = Rule(emphasis("~{2}", "~"), mapOf(1 to Markup.MARKUP, 2 to Markup.STRIKE, 3 to Markup.MARKUP))
    private val MARK = Rule(emphasis("={2}", "="), mapOf(1 to Markup.MARKUP, 2 to Markup.MARK, 3 to Markup.MARKUP))
    private val LINK = Rule(
        Regex("""(?<!\\)(\[)([^\[\]]+)(?<!\\)(\]\()([^$SC()]+)(?<!\\)(\))"""),
        mapOf(1 to Markup.MARKUP, 2 to Markup.VALUE, 3 to Markup.MARKUP, 4 to Markup.LINK, 5 to Markup.MARKUP),
    )
    private val SHORTCODE = Rule(
        Regex("""(?i)(?<!\\)(\[(?:b|/b|i|/i|s|/s|u|/u|m|/m|sup|/sup|sub|/sub|br)\])"""),
        mapOf(1 to Markup.CODE),
    )
    private val SHORTCODE_VALUE = Rule(
        Regex("""(?i)(?<!\\)(\[(?:footnote|field):)(.+?)(?<!\\)(\])"""),
        mapOf(1 to Markup.CODE, 2 to Markup.VALUE, 3 to Markup.CODE),
    )
    // novelWriter compiles this one ASCII-only. `\+-.` in its class is the range + , - .
    private val URL = Rule(
        Regex("""\b(?:https?|file)://(?:www\.|(?!www))[A-Za-z0-9_/()@:%+,\-.~#!?&=]+\b"""),
        mapOf(0 to Markup.LINK),
    )
    private val ALIGN = Rule(Regex("""(^>{1,2}|<{1,2}$)"""), mapOf(1 to Markup.MARKUP))
    private val REPLACE = Rule(Regex("""<([^$SC]+?)>"""), mapOf(0 to Markup.REPLACE))

    /** Paragraphs and `[` lines. */
    private val TEXT_RULES = listOf(NOBREAK, ITALIC, BOLD, STRIKE, MARK, LINK, SHORTCODE, SHORTCODE_VALUE, URL, ALIGN, REPLACE)

    /** Comments and list items: no alignment tags. */
    private val COMMENT_RULES = TEXT_RULES - ALIGN

    private val HEADINGS = listOf("# ", "## ", "### ", "#### ", "#! ", "##! ", "###! ")

    // novelwriter/constants.py nwKeyWords.VALID_KEYS
    private val VALID_KEYS = setOf(
        "@tag", "@pov", "@focus", "@char", "@plot", "@time", "@location",
        "@object", "@entity", "@custom", "@story", "@mention",
    )

    /**
     * Comment modifiers and whether a `.key` is not allowed (0), optional (1) or required (2).
     * novelwriter/text/formats.py MODIFIERS and processComment.
     */
    private val MODIFIERS = mapOf("synopsis" to 0, "short" to 0, "note" to 1, "footnote" to 2, "story" to 2)

    // novelwriter/core/item.py ProjectItem.isInactiveClass
    private val INACTIVE = setOf(ItemClass.NO_CLASS, ItemClass.ARCHIVE, ItemClass.TEMPLATE, ItemClass.TRASH)

    /**
     * Highlights [text] line by line. [line] highlights one line with offsets from its
     * start; pass a caching version to only redo changed lines.
     */
    fun highlight(
        text: String,
        itemClass: ItemClass = ItemClass.NOVEL,
        line: (String, Boolean) -> List<MarkupSpan> = ::highlightLine,
    ): List<MarkupSpan> {
        val out = ArrayList<MarkupSpan>()
        val inactive = itemClass in INACTIVE
        var start = 0
        while (start <= text.length) {
            val end = text.indexOf('\n', start).let { if (it < 0) text.length else it }
            if (end > start) {
                for (span in line(text.substring(start, end), inactive)) {
                    out.add(MarkupSpan(span.start + start, span.end + start, span.kind))
                }
            }
            start = end + 1
        }
        return out
    }

    /** One line without its line break; offsets are from the start of the line. */
    fun highlightLine(line: String, inactive: Boolean): List<MarkupSpan> =
        ArrayList<MarkupSpan>().also { Line(line, it).highlight(inactive) }

    private class Line(line: String, val out: MutableList<MarkupSpan>) {
        val text = if (line.length > MAX_LINE_LENGTH) line.substring(0, MAX_LINE_LENGTH) else line
        val length = line.length
        val blocked = BooleanArray(text.length)

        fun add(start: Int, end: Int, kind: Markup) {
            if (end > start) out.add(MarkupSpan(start, end, kind))
        }

        fun highlight(inactive: Boolean) {
            val heading = HEADINGS.firstOrNull { text.startsWith(it) }
            when {
                text.startsWith("@") -> keywords(inactive)
                heading != null -> {
                    val marks = heading.length - 1
                    add(0, marks, Markup.HEADING_MARK)
                    add(marks, length, Markup.HEADING)
                }
                text.startsWith("%") -> comment()
                text.startsWith("* ") && text.substring(2).trim('*', ' ').isNotEmpty() -> {
                    add(0, 2, Markup.CODE)
                    rules(COMMENT_RULES, 2)
                }
                text.startsWith("#. ") && text.substring(3).isNotBlank() -> {
                    add(0, 3, Markup.CODE)
                    rules(COMMENT_RULES, 3)
                }
                text.startsWith("[") -> command()
                else -> rules(TEXT_RULES, 0)
            }
        }

        /** `@keyword: value, value|display`, split like `Index.scanThis` (novelwriter/core/index.py). */
        fun keywords(inactive: Boolean) {
            val line = text.trimEnd()
            if (line.length < 2) return
            val colon = line.indexOf(':')
            val rawKey = if (colon < 0) line else line.substring(0, colon)
            val key = rawKey.trim()
            if (key == "@" || key !in VALID_KEYS) return
            add(0, key.length, Markup.KEYWORD)
            if (colon < 0 || inactive) return
            var pos = colon + 1
            for ((n, raw) in line.substring(colon + 1).split(',').withIndex()) {
                // Only a @tag's first value is checked; novelWriter marks the rest invalid.
                if (key == "@tag" && n > 0) break
                val value = raw.trim()
                val at = pos + raw.length - raw.trimStart().length
                val name = value.substringBefore('|').trimEnd()
                val display = if ('|' in value) value.substringAfter('|').trimStart() else ""
                add(at, at + name.length, Markup.TAG)
                add(at + value.length - display.length, at + value.length, Markup.OPTIONAL)
                pos += raw.length + 1
            }
        }

        /** Port of `processComment` (text/formats.py) plus the highlighter's styling. */
        fun comment() {
            if (text.startsWith("%~")) {
                add(0, length, Markup.STRIKE)
                return
            }
            val check = text.substring(1).trim()
            val head = check.substringBefore(':').trimEnd()
            val content = if (':' in check) check.substringAfter(':') else ""
            val modifier = head.substringBefore('.').lowercase()
            val key = if ('.' in head) head.substringAfter('.') else ""
            if (content.isNotEmpty() && modifier.isNotEmpty() && keyAllowed(modifier, key)) {
                val col = text.indexOf(':') + 1
                if (key.isNotEmpty()) {
                    val dot = text.indexOf('.').let { if (it in 0 until col) it + 1 else 0 }
                    add(0, dot, Markup.MODIFIER)
                    add(dot, col, Markup.VALUE)
                } else {
                    add(0, col, Markup.MODIFIER)
                }
                add(col, length, Markup.NOTE)
                rules(COMMENT_RULES, col)
            } else {
                add(0, length, Markup.HIDDEN)
                rules(COMMENT_RULES, 0)
            }
        }

        fun keyAllowed(modifier: String, key: String): Boolean {
            val need = MODIFIERS[modifier] ?: return false
            if (key.isEmpty()) return need < 2
            val clean = key.replace("_", "")
            return clean.isNotEmpty() && clean.all { it.isLetterOrDigit() } && need > 0
        }

        /** `[newpage]`, `[new page]`, `[vspace]`, `[vspace:n]`; other `[` lines are text. */
        fun command() {
            val check = text.trimEnd().lowercase()
            if (check == "[newpage]" || check == "[new page]" || check == "[vspace]") {
                add(0, length, Markup.CODE)
            } else if (check.startsWith("[vspace:") && check.endsWith("]")) {
                // Same offsets as novelWriter, which assume no trailing spaces.
                val good = (check.substring(8, check.length - 1).trim().toIntOrNull() ?: 0) > 0
                add(0, 8, Markup.CODE)
                if (good) add(8, length - 1, Markup.VALUE)
                add(length - 1, length, Markup.CODE)
            } else {
                rules(TEXT_RULES, 0)
            }
        }

        /** Runs the inline rules on the line from [offset], skipping markup characters. */
        fun rules(rules: List<Rule>, offset: Int) {
            if (offset >= text.length) return
            val part = text.substring(offset)
            for (rule in rules) {
                for (match in rule.regex.findAll(part)) {
                    for ((group, kind) in rule.groups) {
                        val range = match.groups[group]?.range ?: continue
                        var runStart = -1
                        for (i in range.first + offset..range.last + offset + 1) {
                            val open = i <= range.last + offset && !blocked[i]
                            if (open && runStart < 0) runStart = i
                            if (!open && runStart >= 0) {
                                add(runStart, i, kind)
                                runStart = -1
                            }
                        }
                        if (kind == Markup.MARKUP) {
                            for (i in range.first + offset..range.last + offset) blocked[i] = true
                        }
                    }
                }
            }
        }
    }
}
