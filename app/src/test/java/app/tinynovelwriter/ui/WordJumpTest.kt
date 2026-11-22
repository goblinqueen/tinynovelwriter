package app.tinynovelwriter.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class WordJumpTest {

    private val text = "Hello, мир дом"

    @Test
    fun nextWordStopsAtTheEndOfTheWord() {
        assertEquals(5, nextWord(text, 0))
        assertEquals(10, nextWord(text, 5)) // skips ", " and stops after "мир"
        assertEquals(text.length, nextWord(text, 10))
        assertEquals(text.length, nextWord(text, text.length))
    }

    @Test
    fun previousWordStopsAtTheStartOfTheWord() {
        assertEquals(11, previousWord(text, text.length))
        assertEquals(7, previousWord(text, 11))
        assertEquals(0, previousWord(text, 7)) // skips ", " back to "Hello"
        assertEquals(0, previousWord(text, 0))
    }
}
