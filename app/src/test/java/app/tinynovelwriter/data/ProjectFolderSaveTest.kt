package app.tinynovelwriter.data

import app.tinynovelwriter.novelwriter.DocumentReader
import app.tinynovelwriter.novelwriter.HeaderItem
import app.tinynovelwriter.novelwriter.ItemClass
import app.tinynovelwriter.novelwriter.ItemLayout
import app.tinynovelwriter.novelwriter.TextCounts
import app.tinynovelwriter.novelwriter.isHandle
import java.io.File
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectFolderSaveTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val handle = "fbf3407aadf4b"
    private val item = HeaderItem("Глава 1", "ef33f19388131", handle, ItemClass.NOVEL, ItemLayout.DOCUMENT)
    private val now = LocalDateTime.of(2026, 10, 31, 12, 0, 0)
    private lateinit var folder: ProjectFolder
    private lateinit var file: File

    @Before
    fun setUp() {
        val root = tmp.newFolder("project")
        File(root, "content").mkdir()
        folder = ProjectFolder(root)
        file = File(root, "content/$handle.md")
        file.writeText(
            "+++\r\nname = \"Глава 1\"\r\nparent = \"ef33f19388131\"\r\nhandle = \"$handle\"\r\n" +
                "class = \"NOVEL\"\r\nlayout = \"DOCUMENT\"\r\n" +
                "textHash = \"0\"\r\ncreatedDate = \"2026-10-29 22:58:19\"\r\nupdatedDate = \"2026-10-29 22:58:19\"\r\n" +
                "+++\r\nOriginal text.\r\n"
        )
        file.setLastModified(1_000_000_000_000)
    }

    /** Simulates FolderSync bringing down a new version of the file. */
    private fun changeOnDisk(body: String) {
        file.writeText(file.readText().replace("Original text.", body))
        file.setLastModified(1_000_000_060_000)
    }

    private fun contentFiles() = file.parentFile!!.list()!!.sorted()

    @Test
    fun savesAtomicallyAndLeavesNoTempFile() {
        val opened = folder.openDocument(handle)!!
        val result = folder.saveDocument(opened, "New text.\n", item, now = now)

        assertTrue(result is SaveResult.Saved)
        assertEquals(listOf("$handle.md", "$handle.md.bkp"), contentFiles())
        val onDisk = DocumentReader.parse(file.readBytes())
        assertEquals("New text.\n", onDisk.text)
        assertEquals("2026-10-31 12:00:00", onDisk.meta.updatedDate)
        assertEquals("2026-10-29 22:58:19", onDisk.meta.createdDate)
        assertFalse(Regex("(?<!\r)\n").containsMatchIn(file.readText()))
    }

    @Test
    fun backupIsACopyOfTheLatestSave() {
        val first = folder.saveDocument(folder.openDocument(handle)!!, "One.\n", item, now = now) as SaveResult.Saved
        folder.saveDocument(first.opened, "Two.\n", item, now = now)
        val backup = File(file.parentFile, "$handle.md.bkp")
        assertTrue(file.readBytes().contentEquals(backup.readBytes()))
        assertEquals(listOf("$handle.md", "$handle.md.bkp"), contentFiles())
    }

    @Test
    fun backupNameIsIgnoredByNovelWriter() {
        // novelWriter's scanContent: suffix ".md" and a handle as the stem.
        val backup = File("$handle.md.bkp")
        assertFalse(backup.extension == "md" && isHandle(backup.nameWithoutExtension))
    }

    @Test
    fun consecutiveSavesUseTheReturnedState() {
        val first = folder.saveDocument(folder.openDocument(handle)!!, "One.\n", item, now = now) as SaveResult.Saved
        val second = folder.saveDocument(first.opened, "Two.\n", item, now = now)
        assertTrue(second is SaveResult.Saved)
        assertEquals("Two.\n", DocumentReader.parse(file.readBytes()).text)
    }

    @Test
    fun refusesWhenTheFileChangedOnDisk() {
        val opened = folder.openDocument(handle)!!
        changeOnDisk("Edited on the PC.")
        val before = file.readBytes()

        val result = folder.saveDocument(opened, "Phone edit.\n", item, now = now)

        assertTrue(result is SaveResult.ChangedOnDisk)
        assertEquals("Edited on the PC.\n", (result as SaveResult.ChangedOnDisk).onDisk.document.text)
        assertTrue(before.contentEquals(file.readBytes()))
        assertEquals(listOf("$handle.md"), contentFiles()) // no backup for a refused save
    }

    @Test
    fun overwriteKeepsThePhoneVersion() {
        val opened = folder.openDocument(handle)!!
        changeOnDisk("Edited on the PC.")
        val result = folder.saveDocument(opened, "Phone edit.\n", item, overwrite = true, now = now)
        assertTrue(result is SaveResult.Saved)
        assertEquals("Phone edit.\n", DocumentReader.parse(file.readBytes()).text)
    }

    @Test
    fun touchedButUnchangedFileIsNotAConflict() {
        val opened = folder.openDocument(handle)!!
        file.setLastModified(1_000_000_060_000) // e.g. a sync app re-wrote the same bytes
        assertTrue(folder.saveDocument(opened, "Phone edit.\n", item, now = now) is SaveResult.Saved)
    }

    @Test
    fun neverCreatesADocument() {
        val opened = folder.openDocument(handle)!!
        file.delete()
        assertEquals(SaveResult.Missing, folder.saveDocument(opened, "Text.\n", item, now = now))
        assertEquals(emptyList<String>(), contentFiles())
        assertNull(folder.openDocument(handle))
    }

    @Test
    fun saveMovesTheIndexAsideReplacingTheOldCopy() {
        val meta = File(file.parentFile!!.parentFile, "meta").apply { mkdir() }
        File(meta, "index.json").writeText("new index")
        File(meta, "index.json.old").writeText("older index")
        File(meta, "options.json").writeText("options")

        folder.saveDocument(folder.openDocument(handle)!!, "New.\n", item, now = now)

        assertEquals(listOf("index.json.old", "options.json"), meta.list()!!.sorted())
        assertEquals("new index", File(meta, "index.json.old").readText())
    }

    @Test
    fun refusedSaveLeavesTheIndex() {
        val meta = File(file.parentFile!!.parentFile, "meta").apply { mkdir() }
        File(meta, "index.json").writeText("index")
        val opened = folder.openDocument(handle)!!
        changeOnDisk("Edited on the PC.")

        folder.saveDocument(opened, "Phone edit.\n", item, now = now)

        assertEquals(listOf("index.json"), meta.list()!!.toList())
    }

    @Test
    fun saveWorksWithoutAnIndex() {
        assertTrue(folder.saveDocument(folder.openDocument(handle)!!, "New.\n", item, now = now) is SaveResult.Saved)
    }

    @Test
    fun leavesOtherFilesAlone() {
        val other = File(file.parentFile, "aaaaaaaaaaaa1.md").apply { writeText("other") }
        folder.saveDocument(folder.openDocument(handle)!!, "New.\n", item, now = now)
        assertEquals("other", other.readText())
        assertEquals(listOf("$handle.md", "$handle.md.bkp", "aaaaaaaaaaaa1.md").sorted(), contentFiles())
    }

    @Test
    fun updateCountsWritesOnlyCountsAndKeepsPreviousVersion() {
        val nwx = File(file.parentFile!!.parentFile, "nwProject.nwx")
        val before = listOf(
            "<?xml version='1.0' encoding='utf-8'?>",
            "<novelWriterXML fileVersion=\"1.6\">",
            "  <content items=\"1\" novelWords=\"2\" notesWords=\"0\" novelChars=\"14\" notesChars=\"0\">",
            "    <item handle=\"$handle\" parent=\"ef33f19388131\" root=\"ef33f19388131\" order=\"0\" type=\"FILE\" class=\"NOVEL\" layout=\"DOCUMENT\">",
            "      <meta expanded=\"no\" heading=\"H0\" charCount=\"14\" wordCount=\"2\" paraCount=\"1\" cursorPos=\"0\" />",
            "      <name status=\"s\" import=\"i\" active=\"yes\">Глава 1</name>",
            "    </item>",
            "  </content>",
            "</novelWriterXML>",
            "",
        ).joinToString("\r\n")
        nwx.writeText(before)

        assertFalse(folder.updateCounts(handle, TextCounts(chars = 14, words = 2, paragraphs = 1)))
        assertFalse(File(nwx.parentFile, "nwProject.nwx.bkp").exists())

        assertTrue(folder.updateCounts(handle, TextCounts(chars = 30, words = 5, paragraphs = 2)))
        val expected = before
            .replace("novelWords=\"2\" notesWords=\"0\" novelChars=\"14\"", "novelWords=\"5\" notesWords=\"0\" novelChars=\"30\"")
            .replace("charCount=\"14\" wordCount=\"2\" paraCount=\"1\"", "charCount=\"30\" wordCount=\"5\" paraCount=\"2\"")
        assertEquals(expected, nwx.readText())
        assertEquals(before, File(nwx.parentFile, "nwProject.nwx.bkp").readText())
        assertEquals(listOf("content", "nwProject.nwx", "nwProject.nwx.bkp"), nwx.parentFile!!.list()!!.sorted())
    }
}
