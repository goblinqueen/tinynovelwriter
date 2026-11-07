package app.tinynovelwriter.novelwriter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectXmlTest {

    private fun xml(fileVersion: String = "1.6", content: String) = """
        <?xml version='1.0' encoding='utf-8'?>
        <novelWriterXML appVersion="26.2.2" hexVersion="0x260202f0" fileVersion="$fileVersion" fileRevision="0" timeStamp="2026-10-29 23:37:57">
          <project id="745693e8-e977-4570-83df-c6e6c5cdf7df" saveCount="4" autoCount="45" editTime="3060" indexRevision="149">
            <name>Тестовый проект</name>
            <author>Автор</author>
          </project>
          <settings>
            <doBackup>yes</doBackup>
          </settings>
          <content items="0" novelWords="0" notesWords="0" novelChars="0" notesChars="0">
        $content
          </content>
        </novelWriterXML>
    """.trimIndent().byteInputStream()

    private fun item(handle: String, parent: String?, type: String, cls: String = "NOVEL", name: String, layout: String? = null) = """
        <item handle="$handle" parent="${parent ?: "None"}" root="aaaaaaaaaaaa0" order="0" type="$type" class="$cls"${layout?.let { " layout=\"$it\"" } ?: ""}>
          <meta expanded="no" heading="H2" charCount="10" wordCount="7" paraCount="1" cursorPos="0" />
          <name status="s650a2d" import="idae84a" active="yes">$name</name>
        </item>
    """

    @Test
    fun readsProjectInfoAndTreeInFileOrder() {
        val project = ProjectXml.parse(
            xml(content = item("aaaaaaaaaaaa0", null, "ROOT", name = "Novel") +
                item("aaaaaaaaaaaa2", "aaaaaaaaaaaa0", "FILE", name = "Глава 2", layout = "DOCUMENT") +
                item("aaaaaaaaaaaa1", "aaaaaaaaaaaa0", "FILE", name = "Глава 1", layout = "DOCUMENT") +
                item("bbbbbbbbbbbb0", null, "ROOT", cls = "WORLD", name = "World") +
                item("bbbbbbbbbbbb1", "bbbbbbbbbbbb0", "FOLDER", cls = "WORLD", name = "Places") +
                item("bbbbbbbbbbbb2", "bbbbbbbbbbbb1", "FILE", cls = "WORLD", name = "Деревня", layout = "NOTE"))
        )

        assertEquals("Тестовый проект", project.name)
        assertEquals("Автор", project.author)
        assertEquals("26.2.2", project.appVersion)
        assertEquals(listOf("Novel", "World"), project.roots.map { it.item.name })
        assertEquals(listOf("Глава 2", "Глава 1"), project.roots[0].children.map { it.item.name })

        val village = project.roots[1].children.single().children.single().item
        assertEquals("Деревня", village.name)
        assertEquals(ItemType.FILE, village.type)
        assertEquals(ItemClass.WORLD, village.itemClass)
        assertEquals(ItemLayout.NOTE, village.layout)
        assertEquals(7, village.wordCount)
        assertEquals("H2", village.heading)
        assertTrue(village.active)
        assertNull(project.roots[0].item.parent)
        assertEquals(6, project.items.size)
    }

    @Test
    fun childBeforeParentIsStillPlaced() {
        val project = ProjectXml.parse(
            xml(content = item("aaaaaaaaaaaa2", "aaaaaaaaaaaa1", "FILE", name = "Doc") +
                item("aaaaaaaaaaaa1", "aaaaaaaaaaaa0", "FOLDER", name = "Folder") +
                item("aaaaaaaaaaaa0", null, "ROOT", name = "Novel"))
        )
        assertEquals("Doc", project.roots.single().children.single().children.single().item.name)
    }

    @Test
    fun dropsOrphansBadHandlesAndDuplicates() {
        val project = ProjectXml.parse(
            xml(content = item("aaaaaaaaaaaa0", null, "ROOT", name = "Novel") +
                item("aaaaaaaaaaaa1", "ccccccccccccc", "FILE", name = "Orphan") +
                item("AAAAAAAAAAAA2", "aaaaaaaaaaaa0", "FILE", name = "Upper case handle") +
                item("aaaaaaaaaaaa3", null, "FILE", name = "No parent") +
                item("aaaaaaaaaaaa4", "aaaaaaaaaaaa0", "FILE", name = "First") +
                item("aaaaaaaaaaaa4", "aaaaaaaaaaaa0", "FILE", name = "Duplicate"))
        )
        assertEquals(listOf("First"), project.roots.single().children.map { it.item.name })
        assertEquals(setOf("aaaaaaaaaaaa0", "aaaaaaaaaaaa4"), project.items.keys)
    }

    @Test
    fun rootWithParentBecomesTopLevel() {
        val project = ProjectXml.parse(
            xml(content = item("aaaaaaaaaaaa0", null, "ROOT", name = "A") +
                item("bbbbbbbbbbbb0", "aaaaaaaaaaaa0", "ROOT", name = "B"))
        )
        assertEquals(listOf("A", "B"), project.roots.map { it.item.name })
        assertFalse(project.roots[0].children.isNotEmpty())
    }

    @Test
    fun rejectsOtherFormatVersions() {
        val e = assertThrows(ProjectFormatException::class.java) { ProjectXml.parse(xml("1.5", "")) }
        assertTrue(e.message!!.contains("1.5"))
        assertThrows(ProjectFormatException::class.java) { ProjectXml.parse(xml("1.7", "")) }
    }

    @Test
    fun rejectsNonProjectFiles() {
        assertThrows(ProjectFormatException::class.java) { ProjectXml.parse("<other/>".byteInputStream()) }
        assertThrows(ProjectFormatException::class.java) { ProjectXml.parse("not xml".byteInputStream()) }
    }
}
