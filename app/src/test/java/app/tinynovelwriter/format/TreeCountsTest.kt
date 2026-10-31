package app.tinynovelwriter.format

import org.junit.Assert.assertEquals
import org.junit.Test

class TreeCountsTest {

    private var next = 0

    private fun node(type: ItemType, cls: ItemClass, words: Int = 0, vararg children: ProjectNode): ProjectNode {
        val handle = (next++).toString(16).padStart(13, '0')
        val layout = if (type == ItemType.FILE && cls.isNovelLike()) ItemLayout.DOCUMENT else ItemLayout.NOTE
        val item = ProjectItem(handle, null, null, type, cls, layout, "item $handle", "H0", words, true)
        return ProjectNode(item).apply { this.children += children }
    }

    @Test
    fun novelFoldersSumWordsOfAllDocumentsBelow() {
        val chapter = node(
            ItemType.FOLDER, ItemClass.NOVEL, 0,
            node(ItemType.FILE, ItemClass.NOVEL, 400),
            node(ItemType.FILE, ItemClass.NOVEL, 100, node(ItemType.FILE, ItemClass.NOVEL, 50)),
        )
        val root = node(ItemType.ROOT, ItemClass.NOVEL, 0, node(ItemType.FILE, ItemClass.NOVEL, 3), chapter)
        assertEquals(FolderSummary.Words(550), folderSummary(chapter))
        assertEquals(FolderSummary.Words(553), folderSummary(root))
    }

    @Test
    fun noteFoldersCountNotesBelow() {
        val places = node(ItemType.FOLDER, ItemClass.WORLD, 0, node(ItemType.FILE, ItemClass.WORLD, 90))
        val root = node(ItemType.ROOT, ItemClass.WORLD, 0, node(ItemType.FILE, ItemClass.WORLD, 10), places)
        assertEquals(FolderSummary.Notes(1), folderSummary(places))
        assertEquals(FolderSummary.Notes(2), folderSummary(root))
    }

    @Test
    fun archiveAndTemplatesAreNovelLike() {
        assertEquals(FolderSummary.Words(7), folderSummary(node(ItemType.ROOT, ItemClass.ARCHIVE, 0, node(ItemType.FILE, ItemClass.ARCHIVE, 7))))
        assertEquals(FolderSummary.Words(0), folderSummary(node(ItemType.ROOT, ItemClass.TEMPLATE)))
        assertEquals(FolderSummary.Notes(0), folderSummary(node(ItemType.ROOT, ItemClass.CHARACTER)))
    }
}
