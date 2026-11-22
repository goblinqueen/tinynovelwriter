package app.tinynovelwriter.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import app.tinynovelwriter.data.ProjectFolder
import app.tinynovelwriter.novelwriter.HeaderItem
import app.tinynovelwriter.novelwriter.ItemClass
import app.tinynovelwriter.novelwriter.ItemLayout
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DocumentEditorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val handle = "fbf3407aadf4b"
    private lateinit var root: File
    private lateinit var file: File
    private lateinit var editor: DocumentEditor
    private var writes = 0

    @Before
    fun setUp() {
        root = tmp.newFolder("project")
        File(root, "content").mkdir()
        File(root, "nwProject.nwx").writeText(
            "<?xml version='1.0' encoding='utf-8'?>\n<novelWriterXML fileVersion=\"1.6\">\n" +
                "  <content novelWords=\"2\" notesWords=\"0\" novelChars=\"14\" notesChars=\"0\">\n" +
                "    <item handle=\"$handle\" type=\"FILE\" class=\"NOVEL\" layout=\"DOCUMENT\">\n" +
                "      <meta charCount=\"14\" wordCount=\"2\" paraCount=\"1\" />\n" +
                "      <name>Глава 1</name>\n    </item>\n  </content>\n</novelWriterXML>\n"
        )
        file = File(root, "content/$handle.md")
        writeDocument("Original text.")
        file.setLastModified(1_000_000_000_000)

        editor = DocumentEditor(ProjectFolder(root), handle)
        editor.item = HeaderItem("Глава 1", "ef33f19388131", handle, ItemClass.NOVEL, ItemLayout.DOCUMENT)
        editor.onWritten = { writes++ }
        runBlocking { editor.reloadIfClean() }
    }

    private fun writeDocument(body: String) = file.writeText(
        "+++\nname = \"Глава 1\"\nparent = \"ef33f19388131\"\nhandle = \"$handle\"\n" +
            "class = \"NOVEL\"\nlayout = \"DOCUMENT\"\ntextHash = \"0\"\n" +
            "createdDate = \"2026-10-29 22:58:19\"\nupdatedDate = \"2026-10-29 22:58:19\"\n+++\n$body\n"
    )

    /** Simulates FolderSync bringing down a new version. */
    private fun changeOnDisk(body: String) {
        writeDocument(body)
        file.setLastModified(1_000_000_060_000)
    }

    private fun type(text: String) {
        editor.editing = true
        editor.onValueChange(TextFieldValue(text, TextRange(text.length)))
    }

    private fun nwx() = File(root, "nwProject.nwx").readText()

    @Test
    fun loadsTheDocument() {
        assertEquals("Original text.\n", editor.value.text)
        assertEquals(EditorStatus.Idle, editor.status)
        assertTrue(editor.canEdit)
    }

    @Test
    fun readingModeChangesOnlyTheSelection() {
        editor.onValueChange(TextFieldValue("Other", TextRange(3)))
        assertEquals("Original text.\n", editor.value.text)
        assertEquals(TextRange(3), editor.value.selection)
        assertFalse(editor.dirty)
    }

    @Test
    fun pastedCrLineEndingsBecomeLf() {
        editor.editing = true
        editor.onValueChange(TextFieldValue("a\r\nb\rc", TextRange(1)))
        assertEquals("a\nb\nc", editor.value.text)
    }

    @Test
    fun reloadNeverReplacesUnsavedEdits() = runBlocking {
        type("Phone edit.\n")
        changeOnDisk("Edited on the PC.")
        editor.reloadIfClean()
        assertEquals("Phone edit.\n", editor.value.text)
        assertTrue(editor.dirty)
    }

    @Test
    fun reloadPicksUpChangesWhenClean() = runBlocking {
        changeOnDisk("Edited on the PC.")
        editor.reloadIfClean()
        assertEquals("Edited on the PC.\n", editor.value.text)
    }

    @Test
    fun conflictKeepMineOverwrites() = runBlocking {
        type("Phone edit.\n")
        changeOnDisk("Edited on the PC.")
        assertFalse(editor.save())
        assertTrue(editor.status is EditorStatus.Conflict)

        editor.keepMine()
        assertEquals(EditorStatus.Idle, editor.status)
        assertTrue(file.readText().endsWith("Phone edit.\n"))
    }

    @Test
    fun conflictTakeTheirsDropsPhoneEdits() = runBlocking {
        type("Phone edit.\n")
        changeOnDisk("Edited on the PC.")
        editor.save()

        editor.takeTheirs()
        assertEquals("Edited on the PC.\n", editor.value.text)
        assertFalse(editor.dirty)
        assertTrue(file.readText().endsWith("Edited on the PC.\n"))
    }

    @Test
    fun countsAreWrittenOnlyAfterASaveAndOnce() = runBlocking {
        val before = nwx()
        editor.writeCounts()
        assertEquals(before, nwx())
        assertEquals(0, writes)

        type("Three more words.\n")
        assertTrue(editor.save())
        editor.writeCounts()
        assertTrue(nwx().contains("wordCount=\"3\""))
        assertEquals(2, writes) // the save and the counts

        editor.writeCounts()
        assertEquals(2, writes)
    }

    @Test
    fun missingFileIsUnavailable() = runBlocking {
        file.delete()
        val other = DocumentEditor(ProjectFolder(root), handle)
        other.reloadIfClean()
        assertTrue(other.status is EditorStatus.Unavailable)
        assertFalse(other.canEdit)
    }
}
