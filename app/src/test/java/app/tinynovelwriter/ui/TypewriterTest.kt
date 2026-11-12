package app.tinynovelwriter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TypewriterTest {

    // Visible height 1000, so the comfort line is at 600 on screen; lines are 50 high.

    @Test
    fun sameLineDoesNotScroll() {
        assertNull(Typewriter.target(900f, 900f, 0, 1000))
    }

    @Test
    fun aboveComfortLineDoesNotScroll() {
        assertNull(Typewriter.target(300f, 350f, 0, 1000))
        assertNull(Typewriter.target(1300f, 1350f, 1000, 1000))
    }

    @Test
    fun atComfortLineTheLineStaysPut() {
        assertEquals(50, Typewriter.target(600f, 650f, 0, 1000))
        assertEquals(1050, Typewriter.target(1600f, 1650f, 1000, 1000))
    }

    @Test
    fun belowComfortLineGlidesUpToIt() {
        assertEquals(350, Typewriter.target(900f, 950f, 0, 1000))
    }

    @Test
    fun joiningLinesScrollsBack() {
        assertEquals(950, Typewriter.target(1600f, 1550f, 1000, 1000))
        assertEquals(0, Typewriter.target(650f, 600f, 20, 1000))
        assertNull(Typewriter.target(1300f, 1250f, 1000, 1000))
    }

    @Test
    fun editingStartsAtTheEndWhenItIsOnScreen() {
        assertNull(Typewriter.editStartY(0, 1000, 400f))        // short document
        assertNull(Typewriter.editStartY(2000, 1000, 2900f))    // scrolled to the end
        assertNull(Typewriter.editStartY(2000, 1000, 3000f))
    }

    @Test
    fun editingStartsAtTheComfortLineOtherwise() {
        assertEquals(600f, Typewriter.editStartY(0, 1000, 5000f))
        assertEquals(2600f, Typewriter.editStartY(2000, 1000, 3001f))
    }
}
