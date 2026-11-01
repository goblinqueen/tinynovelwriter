package app.tinynovelwriter.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatToggleTest {

    /** [text] uses [ and ] for a selection, or | for the caret; the result is shown the same way. */
    private fun run(text: String, op: (String, Int, Int) -> Edited?): String {
        val caret = text.indexOf('|')
        val (plain, s, e) = if (caret >= 0) Triple(text.replace("|", ""), caret, caret)
        else Triple(text.replace("[", "").replace("]", ""), text.indexOf('['), text.indexOf(']') - 1)
        val r = op(plain, s, e) ?: return "unchanged"
        return if (r.start == r.end) r.text.substring(0, r.start) + "|" + r.text.substring(r.start)
        else r.text.substring(0, r.start) + "[" + r.text.substring(r.start, r.end) + "]" + r.text.substring(r.end)
    }

    @Test
    fun wrapsAndUnwrapsSelection() {
        assertEquals("a **[big cat]** b", run("a [big cat] b", FormatToggle::bold))
        assertEquals("a [big cat] b", run("a **[big cat]** b", FormatToggle::bold))
        assertEquals("a _[кот]_ b", run("a [кот] b", FormatToggle::italic))
        assertEquals("a [кот] b", run("a _[кот]_ b", FormatToggle::italic))
    }

    @Test
    fun usesTheWordAroundTheCaret() {
        assertEquals("a **ca|t** b", run("a ca|t b", FormatToggle::bold))
        assertEquals("a **cat**| b", run("a cat| b", FormatToggle::bold))
        assertEquals("a _don't_| b", run("a don't| b", FormatToggle::italic))
        assertEquals("a ca|t b", run("a **ca|t** b", FormatToggle::bold))
    }

    @Test
    fun emptySpotInsertsMarksAroundTheCaret() {
        assertEquals("a **|** b", run("a | b", FormatToggle::bold))
        // Right after a mark novelWriter does nothing, so empty marks don't toggle back off.
        assertEquals("unchanged", run("a **|** b", FormatToggle::bold))
        assertNull(FormatToggle.bold("a* b", 2, 2))
    }

    @Test
    fun multiLineSelectionUsesFirstLine() {
        // The selection end is clamped to the end of the first line.
        assertEquals("**[one]**\ntwo", run("[one\ntw]o", FormatToggle::bold))
    }
}
