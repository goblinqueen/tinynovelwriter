package app.tinynovelwriter.novelwriter

import app.tinynovelwriter.etree.ElementTree
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectCountsTest {

    private val sample = listOf(
        "<?xml version='1.0' encoding='utf-8'?>",
        "<novelWriterXML appVersion=\"26.2.2\" fileVersion=\"1.6\">",
        "  <content items=\"3\" novelWords=\"10\" notesWords=\"5\" novelChars=\"40\" notesChars=\"20\">",
        "    <item handle=\"aaaaaaaaaaaa0\" parent=\"None\" root=\"aaaaaaaaaaaa0\" order=\"0\" type=\"ROOT\" class=\"NOVEL\">",
        "      <meta expanded=\"yes\" />",
        "      <name status=\"s1\" import=\"i1\">Novel</name>",
        "    </item>",
        "    <item handle=\"aaaaaaaaaaaa1\" parent=\"aaaaaaaaaaaa0\" root=\"aaaaaaaaaaaa0\" order=\"0\" type=\"FILE\" class=\"NOVEL\" layout=\"DOCUMENT\">",
        "      <meta expanded=\"no\" heading=\"H2\" charCount=\"40\" wordCount=\"10\" paraCount=\"2\" cursorPos=\"7\" />",
        "      <name status=\"s1\" import=\"i1\" active=\"yes\">Глава</name>",
        "    </item>",
        "    <item handle=\"bbbbbbbbbbbb1\" parent=\"aaaaaaaaaaaa0\" root=\"aaaaaaaaaaaa0\" order=\"1\" type=\"FILE\" class=\"NOVEL\" layout=\"NOTE\">",
        "      <meta expanded=\"no\" heading=\"H0\" charCount=\"20\" wordCount=\"5\" paraCount=\"1\" cursorPos=\"0\" />",
        "      <name status=\"s1\" import=\"i1\" active=\"no\">Заметка</name>",
        "    </item>",
        "  </content>",
        "</novelWriterXML>",
        "",
    )

    private val bytes = sample.joinToString("\r\n").toByteArray()

    @Test
    fun changesOnlyCountAttributes() {
        val file = ElementTree.parse(bytes)
        assertTrue(ProjectCounts.set(file, "aaaaaaaaaaaa1", TextCounts(chars = 100, words = 25, paragraphs = 3)))
        val expected = sample.map {
            it.replace("charCount=\"40\" wordCount=\"10\" paraCount=\"2\"", "charCount=\"100\" wordCount=\"25\" paraCount=\"3\"")
                .replace("novelWords=\"10\" notesWords=\"5\" novelChars=\"40\"", "novelWords=\"25\" notesWords=\"5\" novelChars=\"100\"")
        }.joinToString("\r\n")
        assertEquals(expected, ElementTree.render(file).toString(Charsets.UTF_8))
    }

    @Test
    fun notesGoToNoteTotals() {
        val file = ElementTree.parse(bytes)
        assertTrue(ProjectCounts.set(file, "bbbbbbbbbbbb1", TextCounts(chars = 21, words = 6, paragraphs = 1)))
        val content = file.root.find("content")!!
        assertEquals(listOf("10", "6", "40", "21"), listOf("novelWords", "notesWords", "novelChars", "notesChars").map { content[it] })
    }

    @Test
    fun sameCountsOrUnknownItemChangeNothing() {
        val file = ElementTree.parse(bytes)
        assertFalse(ProjectCounts.set(file, "aaaaaaaaaaaa1", TextCounts(chars = 40, words = 10, paragraphs = 2)))
        assertFalse(ProjectCounts.set(file, "cccccccccccc1", TextCounts(chars = 1, words = 1, paragraphs = 1)))
        assertFalse(ProjectCounts.set(file, "aaaaaaaaaaaa0", TextCounts(chars = 1, words = 1, paragraphs = 1))) // a root, not a document
        assertArrayEquals(bytes, ElementTree.render(file))
    }
}
