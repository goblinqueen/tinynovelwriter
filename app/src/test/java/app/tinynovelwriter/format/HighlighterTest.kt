package app.tinynovelwriter.format

import app.tinynovelwriter.format.Markup.*
import org.junit.Assert.assertEquals
import org.junit.Test

class HighlighterTest {

    /** Each span as the text it covers and its kind, in application order. */
    private fun spans(text: String, itemClass: ItemClass = ItemClass.NOVEL) =
        Highlighter.highlight(text, itemClass).map { text.substring(it.start, it.end) to it.kind }

    @Test
    fun headings() {
        assertEquals(listOf("#" to HEADING_MARK, " Title" to HEADING), spans("# Title"))
        assertEquals(listOf("####" to HEADING_MARK, " Sec" to HEADING), spans("#### Sec"))
        assertEquals(listOf("#!" to HEADING_MARK, " T" to HEADING), spans("#! T"))
        assertEquals(listOf("###!" to HEADING_MARK, " Alt" to HEADING), spans("###! Alt"))
        // No inline formatting in headings, and no heading without the space.
        assertEquals(listOf("##" to HEADING_MARK, " **b**" to HEADING), spans("## **b**"))
        assertEquals(emptyList<Any>(), spans("#NoSpace"))
    }

    @Test
    fun emphasis() {
        assertEquals(listOf("**" to MARKUP, "bold" to BOLD, "**" to MARKUP), spans("**bold**"))
        assertEquals(listOf("_" to MARKUP, "кот" to ITALIC, "_" to MARKUP), spans("_кот_"))
        assertEquals(listOf("~~" to MARKUP, "gone" to STRIKE, "~~" to MARKUP), spans("~~gone~~"))
        assertEquals(listOf("==" to MARKUP, "mark" to MARK, "==" to MARKUP), spans("==mark=="))
        // Not inside words, not after a space, not escaped; single * isn't bold.
        assertEquals(emptyList<Any>(), spans("snake_case_name"))
        assertEquals(emptyList<Any>(), spans("кот_и_пёс"))
        assertEquals(emptyList<Any>(), spans("** not bold**"))
        assertEquals(emptyList<Any>(), spans("""\**no**"""))
        assertEquals(emptyList<Any>(), spans("*single*"))
    }

    @Test
    fun nestedEmphasisSkipsMarkupCharacters() {
        // Italic first, then bold over "_x_", but the underscores stay plain markup.
        assertEquals(
            listOf("_" to MARKUP, "x" to ITALIC, "_" to MARKUP, "**" to MARKUP, "x" to BOLD, "**" to MARKUP),
            spans("**_x_**"),
        )
    }

    @Test
    fun shortcodesAndLinks() {
        assertEquals(listOf("[b]" to CODE, "[/b]" to CODE), spans("[b]x[/b]"))
        assertEquals(listOf("[footnote:" to CODE, "abc" to VALUE, "]" to CODE), spans("x[footnote:abc]"))
        assertEquals(
            listOf("[" to MARKUP, "site" to VALUE, "](" to MARKUP, "https://a.b/c" to LINK, ")" to MARKUP, "https://a.b/c" to LINK),
            spans("[site](https://a.b/c)"),
        )
        assertEquals(listOf("http://x.org/a,b" to LINK), spans("see http://x.org/a,b."))
        assertEquals(listOf("<name>" to REPLACE), spans("Hi <name>!"))
    }

    @Test
    fun alignmentAndNoBreakSpaces() {
        assertEquals(listOf(">>" to MARKUP), spans(">> right"))
        assertEquals(listOf("<" to MARKUP), spans("left <"))
        assertEquals(listOf(" " to NOBREAK), spans("a b"))
    }

    @Test
    fun keywords() {
        assertEquals(
            listOf("@char" to KEYWORD, "Kitty" to TAG, "Bob" to TAG),
            spans("@char: Kitty, Bob"),
        )
        assertEquals(
            listOf("@tag" to KEYWORD, "Kitty" to TAG, "Cat" to OPTIONAL),
            spans("@tag: Kitty | Cat, Ignored"),
        )
        assertEquals(emptyList<Any>(), spans("@nope: x"))
        assertEquals(listOf("@pov" to KEYWORD), spans("@pov: Kitty", ItemClass.TRASH))
        // No inline formatting on keyword lines.
        assertEquals(listOf("@pov" to KEYWORD, "**K**" to TAG), spans("@pov: **K**"))
    }

    @Test
    fun comments() {
        assertEquals(listOf("% plain **b**" to HIDDEN, "**" to MARKUP, "b" to BOLD, "**" to MARKUP), spans("% plain **b**"))
        assertEquals(listOf("%~ ignored **b**" to STRIKE), spans("%~ ignored **b**"))
        assertEquals(listOf("%Synopsis:" to MODIFIER, " Text" to NOTE), spans("%Synopsis: Text"))
        assertEquals(listOf("% short :" to MODIFIER, " T" to NOTE), spans("% short : T"))
        assertEquals(
            listOf("%footnote." to MODIFIER, "fn1:" to VALUE, " Text" to NOTE),
            spans("%footnote.fn1: Text"),
        )
        // footnote needs a key, synopsis can't have one: both are plain comments then.
        assertEquals(listOf("%footnote: x" to HIDDEN), spans("%footnote: x"))
        assertEquals(listOf("%synopsis.k: x" to HIDDEN), spans("%synopsis.k: x"))
        assertEquals(listOf("%note:" to HIDDEN), spans("%note:"))
    }

    @Test
    fun listsAndCommands() {
        assertEquals(listOf("* " to CODE), spans("* item"))
        assertEquals(listOf("#. " to CODE), spans("#. item"))
        assertEquals(emptyList<Any>(), spans("* *"))
        assertEquals(listOf("[newpage]" to CODE), spans("[newpage]"))
        assertEquals(listOf("[New Page]" to CODE), spans("[New Page]"))
        assertEquals(listOf("[vspace:" to CODE, "3" to VALUE, "]" to CODE), spans("[vspace:3]"))
        assertEquals(listOf("[vspace:" to CODE, "]" to CODE), spans("[vspace:x]"))
        assertEquals(listOf("[b]" to CODE), spans("[b]bold line"))
    }

    @Test
    fun offsetsAreForTheWholeText() {
        val text = "plain\n\n# H\n**b**"
        val spans = Highlighter.highlight(text)
        assertEquals(MarkupSpan(7, 8, HEADING_MARK), spans[0])
        assertEquals("b", text.substring(spans[3].start, spans[3].end))
    }
}
