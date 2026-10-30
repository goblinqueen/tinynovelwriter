package app.tinynovelwriter.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentReaderTest {

    private val header = listOf(
        "+++",
        "name = \"Деревня\"",
        "parent = \"9749ca8e81c21\"",
        "handle = \"1be7d19138c77\"",
        "class = \"WORLD\"",
        "layout = \"NOTE\"",
        "textHash = \"66e1177e2f575cb48ba714d7da7d48b2ec9c0fad\"",
        "createdDate = \"2026-10-29 22:58:19\"",
        "updatedDate = \"2026-10-29 22:58:19\"",
        "+++",
    )

    @Test
    fun readsCrlfDocumentWithHeader() {
        val file = (header + "# Деревня" + "" + "Текст." + "").joinToString("\r\n")
        val doc = DocumentReader.parse(file.toByteArray())

        assertTrue(doc.hasHeader)
        assertEquals("Деревня", doc.meta.name)
        assertEquals("9749ca8e81c21", doc.meta.parent)
        assertEquals("1be7d19138c77", doc.meta.handle)
        assertEquals(ItemClass.WORLD, doc.meta.itemClass)
        assertEquals(ItemLayout.NOTE, doc.meta.layout)
        assertEquals("66e1177e2f575cb48ba714d7da7d48b2ec9c0fad", doc.meta.textHash)
        assertEquals("2026-10-29 22:58:19", doc.meta.createdDate)
        assertEquals("# Деревня\n\nТекст.\n", doc.text)
    }

    @Test
    fun lfAndLoneCrAreNormalized() {
        assertEquals("a\nb\n", DocumentReader.parse((header + "a\rb\n").joinToString("\n")).text)
    }

    @Test
    fun noHeaderMeansWholeFileIsText() {
        val doc = DocumentReader.parse("# Title\r\nBody\r\n")
        assertFalse(doc.hasHeader)
        assertEquals(DocumentMeta(), doc.meta)
        assertEquals("# Title\nBody\n", doc.text)
    }

    @Test
    fun unclosedHeaderMeansWholeFileIsText() {
        assertEquals("+++\nname = \"x\"\n", DocumentReader.parse("+++\nname = \"x\"\n").text)
    }

    @Test
    fun headerLongerThanTwentyLinesIsNotAHeader() {
        val lines = listOf("+++") + (1..20).map { "k$it = $it" } + "+++" + "body"
        val doc = DocumentReader.parse(lines.joinToString("\n"))
        assertFalse(doc.hasHeader)
        assertTrue(doc.text.startsWith("+++\nk1 = 1"))
    }

    @Test
    fun twentyHeaderLinesStillCount() {
        val lines = listOf("+++") + (1..20).map { "k$it = $it" } + "+++" + "body"
        // 20 meta lines followed by the closing marker on line 22 is past the limit,
        // but 19 lines plus the closing marker fits.
        val fits = listOf("+++") + (1..19).map { "k$it = $it" } + "+++" + "body"
        assertFalse(DocumentReader.parse(lines.joinToString("\n")).hasHeader)
        assertTrue(DocumentReader.parse(fits.joinToString("\n")).hasHeader)
    }

    @Test
    fun closingMarkerAtEndOfFileGivesEmptyText() {
        val doc = DocumentReader.parse("+++\nname = \"x\"\n+++")
        assertTrue(doc.hasHeader)
        assertEquals("x", doc.meta.name)
        assertEquals("", doc.text)
    }

    @Test
    fun markerLinesMayHaveSurroundingSpaces() {
        assertTrue(DocumentReader.parse("  +++ \nname = \"x\"\n +++\nbody").hasHeader)
    }

    @Test
    fun byteOrderMarkPreventsHeaderLikeNovelWriter() {
        val doc = DocumentReader.parse("\uFEFF+++\nname = \"x\"\n+++\nbody")
        assertFalse(doc.hasHeader)
    }

    @Test
    fun escapedNamesAreDecoded() {
        val doc = DocumentReader.parse("+++\nname = \"Say \\\"hi\\\" \\\\ \\u00e9\"\n+++\n")
        assertEquals("Say \"hi\" \\ é", doc.meta.name)
    }

    @Test
    fun invalidHandlesAndUnknownEnumsFallBack() {
        val doc = DocumentReader.parse("+++\nparent = \"None\"\nclass = \"nope\"\nlayout = \"document\"\n+++\n")
        assertNull(doc.meta.parent)
        assertEquals(ItemClass.NOVEL, doc.meta.itemClass)
        assertEquals(ItemLayout.DOCUMENT, doc.meta.layout)
    }

    @Test
    fun invalidHeaderIsAnError() {
        assertThrows(DocumentFormatException::class.java) { DocumentReader.parse("+++\nname = unquoted\n+++\n") }
        assertThrows(DocumentFormatException::class.java) { DocumentReader.parse("+++\nname = \"open\n+++\n") }
        assertThrows(DocumentFormatException::class.java) { DocumentReader.parse("+++\nname = \"a\"\nname = \"b\"\n+++\n") }
    }

    @Test
    fun invalidUtf8IsAnError() {
        assertThrows(DocumentFormatException::class.java) {
            DocumentReader.parse(byteArrayOf(0x61, 0xC3.toByte(), 0x28))
        }
    }

    @Test
    fun unsafeControlCharactersAreStripped() {
        assertEquals("a\tb\u000Cc\n", DocumentReader.parse("a\u0000\u0007\tb\u000C\u009Fc\n").text)
    }
}
