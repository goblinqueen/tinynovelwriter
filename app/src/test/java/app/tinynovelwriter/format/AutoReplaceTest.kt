package app.tinynovelwriter.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoReplaceTest {

    /** Types [char] at [at] in [text] and returns the result with the caret shown as |. */
    private fun type(text: String, at: Int, char: Char): String {
        val new = text.substring(0, at) + char + text.substring(at)
        val (result, cursor) = AutoReplace.afterTyping(text, new, at + 1) ?: (new to at + 1)
        return result.substring(0, cursor) + "|" + result.substring(cursor)
    }

    @Test
    fun dashesBuildUpAsYouType() {
        assertEquals("a-|", type("a", 1, '-'))
        assertEquals("a–|", type("a-", 2, '-'))
        assertEquals("a—|", type("a–", 2, '-'))
        assertEquals("a―|", type("a—", 2, '-'))
        assertEquals("x –| y", type("x - y", 3, '-'))
    }

    @Test
    fun replacesHyphenRunsLikeNovelWriter() {
        assertEquals("—|", type("--", 2, '-'))
        assertEquals("―|", type("---", 3, '-'))
    }

    @Test
    fun staysWithinTheLine() {
        assertEquals("-\n-|", type("-\n", 2, '-'))
    }

    @Test
    fun onlyAfterASingleTypedCharacter() {
        assertNull(AutoReplace.afterTyping("a", "a--", 3)) // pasted
        assertNull(AutoReplace.afterTyping("a-b", "a--", 3)) // not an insertion
        assertNull(AutoReplace.afterTyping("a-", "a-x", 3)) // not a dash
    }
}
