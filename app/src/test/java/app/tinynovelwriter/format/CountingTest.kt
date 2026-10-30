package app.tinynovelwriter.format

import org.junit.Assert.assertEquals
import org.junit.Test

/** Cases copied from novelWriter's tests/text/test_counting.py. */
class CountingTest {

    private fun count(text: String) = Counting.standardCounter(text).let { Triple(it.chars, it.words, it.paragraphs) }

    @Test
    fun cornerCases() {
        assertEquals(Triple(0, 0, 0), count("> "))
        assertEquals(Triple(0, 0, 0), count(" <"))
        assertEquals(Triple(0, 0, 0), count(""))
    }

    @Test
    fun generalText() {
        val text = "#! Title\n\n" +
            "##! Prologue\n\n" +
            "# Heading One\n" +
            "## Heading Two\n" +
            "### Heading Three\n" +
            "###! Heading Four\n" +
            "#### Heading Five\n\n" +
            "@tag: value\n\n" +
            "% A comment that should not be counted.\n\n" +
            "The first paragraph.\n\n" +
            "The second paragraph.\n\n\n" +
            "The third paragraph.\n\n" +
            "Dashes–and even longer—dashes."
        assertEquals(Triple(163, 26, 4), count(text))
    }

    @Test
    fun alignment() {
        assertEquals(
            Triple(53, 9, 4),
            count("# Title\n\nLeft aligned<<\n\nLeft aligned <<\n\nRight indent<\n\nRight indent <\n\n"),
        )
        assertEquals(
            Triple(53, 9, 4),
            count("# Title\n\n>>Right aligned\n\n>> Right aligned\n\n>Left indent\n\n> Left indent\n\n"),
        )
        assertEquals(
            Triple(59, 9, 4),
            count("# Title\n\n>>Centre aligned<<\n\n>> Centre aligned <<\n\n>Double indent<\n\n> Double indent <\n\n"),
        )
    }

    @Test
    fun listItems() {
        assertEquals(Triple(21, 5, 2), count("* One item\n* Two items\n\nText"))
        assertEquals(Triple(13, 4, 1), count("> * Not an item"))
        assertEquals(Triple(21, 5, 2), count("#. One item\n#. Two items\n\nText"))
    }

    @Test
    fun formattingCodes() {
        val upper = "Some text\n\n[NEWPAGE]\n\nmore text\n\n[NEW PAGE]\n\neven more text\n\n" +
            "[VSPACE]\n\nand some final text\n\n[VSPACE:4]\n\nTHE END\n\n"
        assertEquals(Triple(58, 13, 5), count(upper))
        assertEquals(Triple(58, 13, 5), count(upper.lowercase().replace("the end", "THE END")))
    }

    @Test
    fun shortcodes() {
        val text = "Text with [b]bold[/b] text and padded [b] bold [/b] text.\n\n" +
            "Text with [b][i] nested [/i] emphasis [/b] in it.\n\n"
        assertEquals(Triple(78, 14, 2), count(text))
    }

    @Test
    fun cyrillicWords() {
        assertEquals(3, Counting.standardCounter("Бесцветные зелёные идеи").words)
    }
}
