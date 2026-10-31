package app.tinynovelwriter.format

/** novelWriter's ProjectItem.isNovelLike: classes that hold novel documents rather than notes. */
fun ItemClass.isNovelLike(): Boolean = this == ItemClass.NOVEL || this == ItemClass.ARCHIVE || this == ItemClass.TEMPLATE

/** What a root or folder shows next to its name. */
sealed interface FolderSummary {
    /** Novel-like folders: total words of the documents below, like novelWriter's tree. */
    data class Words(val words: Int) : FolderSummary
    /** Note folders (characters, world, plot, ...): how many notes are below. */
    data class Notes(val notes: Int) : FolderSummary
}

fun folderSummary(node: ProjectNode): FolderSummary {
    val files = node.descendantFiles()
    return if (node.item.itemClass.isNovelLike()) {
        FolderSummary.Words(files.sumOf { it.wordCount })
    } else {
        FolderSummary.Notes(files.size)
    }
}

private fun ProjectNode.descendantFiles(): List<ProjectItem> =
    children.flatMap { child -> (if (child.item.type == ItemType.FILE) listOf(child.item) else emptyList()) + child.descendantFiles() }
