package app.tinynovelwriter.format

import java.time.LocalDateTime
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentWriterTest {

    private val item = HeaderItem("Глава 1", "ef33f19388131", "fbf3407aadf4b", ItemClass.NOVEL, ItemLayout.DOCUMENT)
    private val now = "2026-10-31 12:00:00"

    /** A file as novelWriter writes it on Windows: CRLF, header, body hash. */
    private fun novelWriterFile(body: String, name: String = item.name, created: String = "2026-10-29 22:58:19") =
        listOf(
            "+++",
            "name = \"$name\"",
            "parent = \"ef33f19388131\"",
            "handle = \"fbf3407aadf4b\"",
            "class = \"NOVEL\"",
            "layout = \"DOCUMENT\"",
            "textHash = \"${sha1Hex(body)}\"",
            "createdDate = \"$created\"",
            "updatedDate = \"2026-10-30 08:15:00\"",
            "+++",
        ).joinToString("\r\n", postfix = "\r\n") + body.replace("\n", "\r\n")

    @Test
    fun unchangedTextRewritesIdenticalBytes() {
        val original = novelWriterFile("## Глава 1\n\nТекст первой главы.\n").toByteArray()
        val doc = DocumentReader.parse(original)
        val rendered = DocumentWriter.render(doc.text, item, doc, now)
        assertArrayEquals(original, rendered.bytes)
        assertEquals("2026-10-30 08:15:00", rendered.meta.updatedDate)
    }

    @Test
    fun changedTextUpdatesHashAndDateOnly() {
        val doc = DocumentReader.parse(novelWriterFile("Old text.\n").toByteArray())
        val rendered = DocumentWriter.render("New text.\n", item, doc, now)
        val reread = DocumentReader.parse(rendered.bytes)

        assertEquals("New text.\n", reread.text)
        assertEquals(sha1Hex("New text.\n"), reread.meta.textHash)
        assertEquals(now, reread.meta.updatedDate)
        assertEquals("2026-10-29 22:58:19", reread.meta.createdDate)
        assertEquals(item.name, reread.meta.name)
        assertEquals(item.parent, reread.meta.parent)
    }

    @Test
    fun keepsTheFilesLineEndings() {
        val crlf = DocumentReader.parse(novelWriterFile("a\nb\n").toByteArray())
        assertEquals("\r\n", crlf.lineEnding)
        val crlfOut = String(DocumentWriter.render("a\nb\nc\n", item, crlf, now).bytes)
        assertFalse(Regex("(?<!\r)\n").containsMatchIn(crlfOut))

        val lf = DocumentReader.parse(novelWriterFile("a\n").replace("\r\n", "\n"))
        assertEquals("\n", lf.lineEnding)
        assertFalse('\r' in String(DocumentWriter.render("a\nb\n", item, lf, now).bytes))
    }

    @Test
    fun addsFinalNewline() {
        val doc = DocumentReader.parse(novelWriterFile("x\n").toByteArray())
        val rendered = DocumentWriter.render("No newline", item, doc, now)
        assertEquals("No newline\n", rendered.text)
        assertEquals(sha1Hex("No newline\n"), rendered.meta.textHash)
        assertTrue(String(rendered.bytes).endsWith("No newline\r\n"))
    }

    @Test
    fun emptyTextStaysEmpty() {
        val doc = DocumentReader.parse(novelWriterFile("x\n").toByteArray())
        val rendered = DocumentWriter.render("", item, doc, now)
        assertEquals("", rendered.text)
        assertEquals("", DocumentReader.parse(rendered.bytes).text)
        assertEquals(sha1Hex(""), rendered.meta.textHash)
    }

    @Test
    fun treeNameReplacesStaleHeaderName() {
        val doc = DocumentReader.parse(novelWriterFile("Body\n", name = "Old name").toByteArray())
        val reread = DocumentReader.parse(DocumentWriter.render(doc.text, item, doc, now).bytes)
        assertEquals(item.name, reread.meta.name)
        // The text didn't change, so neither does updatedDate.
        assertEquals("2026-10-30 08:15:00", reread.meta.updatedDate)
    }

    @Test
    fun namesAreEscapedLikeNovelWriter() {
        assertEquals("\"a \\\"b\\\" \\\\ \\t\\n\\r\"", DocumentWriter.dumpString("a \"b\" \\ \t\n\r"))
        val odd = item.copy(name = "Quote \" and \\ slash")
        val doc = DocumentReader.parse(novelWriterFile("x\n").toByteArray())
        assertEquals(odd.name, DocumentReader.parse(DocumentWriter.render("x\n", odd, doc, now).bytes).meta.name)
    }

    @Test
    fun strippedControlCharacterCountsAsAChange() {
        // novelWriter compares against the hash of the body as read, before stripping.
        val doc = DocumentReader.parse(novelWriterFile("a\u0007b\n").toByteArray())
        assertEquals("ab\n", doc.text)
        assertEquals(now, DocumentWriter.render(doc.text, item, doc, now).meta.updatedDate)
    }

    @Test
    fun newFileGetsBothDates() {
        val rendered = DocumentWriter.render("Text\n", item, previous = null, now = now)
        assertEquals(now, rendered.meta.createdDate)
        assertEquals(now, rendered.meta.updatedDate)
    }

    @Test
    fun timestampFormat() {
        assertEquals("2026-01-02 03:04:05", formatTimestamp(LocalDateTime.of(2026, 1, 2, 3, 4, 5)))
    }
}
