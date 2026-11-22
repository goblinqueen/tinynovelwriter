package app.tinynovelwriter.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectFolderLoadTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun project(): File = tmp.newFolder("project").also {
        File(it, "nwProject.nwx").writeText(
            "<?xml version='1.0' encoding='utf-8'?>\n<novelWriterXML fileVersion=\"1.6\">\n" +
                "  <project>\n    <name>Проект</name>\n  </project>\n  <content />\n</novelWriterXML>\n"
        )
    }

    @Test
    fun loadsWithoutALock() {
        val loaded = ProjectFolder(project()).load()
        assertEquals("Проект", loaded.project.name)
        assertNull(loaded.lock)
    }

    @Test
    fun readsTheLockFile() {
        val root = project()
        File(root, "nwProject.lock").writeText("DESKTOP-PC;Windows;10.0.26300;1761955200")
        val lock = ProjectFolder(root).load().lock!!
        assertEquals("DESKTOP-PC", lock.host)
        assertEquals(1761955200L, lock.lockedAt)
    }

    @Test
    fun folderWithoutProjectFileIsNotAProject() {
        assertThrows(NotAProjectException::class.java) { ProjectFolder(tmp.newFolder("empty")).load() }
    }
}
