package app.tinynovelwriter.novelwriter

import app.tinynovelwriter.etree.ElementTree
import app.tinynovelwriter.etree.XmlDocument
import app.tinynovelwriter.etree.XmlElement
import app.tinynovelwriter.etree.XmlFormatException

/**
 * Project file format version this app understands (novelWriter 26.2+).
 * Source: novelWriter 26.3b1, `novelwriter/core/projectxml.py` `FILE_VERSION`.
 */
const val SUPPORTED_FILE_VERSION = "1.6"

// Source: novelWriter 26.3b1, `novelwriter/enum.py` `nwItemType`. Also `nwItemClass`, `nwItemLayout`.
enum class ItemType { NO_TYPE, ROOT, FOLDER, FILE }

enum class ItemClass {
    NO_CLASS, NOVEL, PLOT, CHARACTER, WORLD, TIMELINE, OBJECT, ENTITY, CUSTOM, ARCHIVE, TEMPLATE, TRASH
}

enum class ItemLayout { NO_LAYOUT, DOCUMENT, NOTE }

data class ProjectItem(
    val handle: String,
    val parent: String?,
    val root: String?,
    val type: ItemType,
    val itemClass: ItemClass,
    val layout: ItemLayout,
    val name: String,
    val heading: String,
    val wordCount: Int,
    val active: Boolean,
)

class ProjectNode(val item: ProjectItem) {
    val children: MutableList<ProjectNode> = mutableListOf()
}

data class NwProject(
    val name: String,
    val author: String,
    val appVersion: String,
    val fileVersion: String,
    /** Root nodes in project order; children are in project order too. */
    val roots: List<ProjectNode>,
    /** Every item that made it into the tree, by handle. */
    val items: Map<String, ProjectItem>,
)

class ProjectFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Reads `nwProject.nwx`. The only writes are count updates, see [ProjectCounts].
 * Source: novelWriter 26.3b1, `novelwriter/core/projectxml.py` `ProjectXMLReader`.
 */
object ProjectXml {

    fun parse(bytes: ByteArray): NwProject = parse(parseXml(bytes))

    /** The file as XML, for code that edits it (see [ProjectCounts]). */
    fun parseXml(bytes: ByteArray): XmlDocument = try {
        ElementTree.parse(bytes)
    } catch (e: XmlFormatException) {
        throw ProjectFormatException("Project file is not valid XML (${e.message})", e)
    }

    fun parse(xml: XmlDocument): NwProject {
        val root = xml.root
        if (root.tag != "novelWriterXML") {
            throw ProjectFormatException("Not a novelWriter project file (root <${root.tag}>)")
        }
        val fileVersion = root["fileVersion"] ?: ""
        if (fileVersion != SUPPORTED_FILE_VERSION) {
            throw ProjectFormatException(
                "Unsupported project format $fileVersion (need $SUPPORTED_FILE_VERSION, novelWriter 26.2 or newer)"
            )
        }

        var name = ""
        var author = ""
        val parsed = mutableListOf<ProjectItem>()
        for (section in root.children) {
            when (section.tag) {
                "project" -> for (child in section.children) {
                    when (child.tag) {
                        "name" -> name = child.text ?: ""
                        "author" -> author = child.text ?: ""
                    }
                }
                "content" -> for (child in section.children) {
                    if (child.tag == "item") parseItem(child)?.let(parsed::add)
                }
            }
        }

        val (roots, items) = buildTree(parsed)
        return NwProject(
            name = name,
            author = author,
            appVersion = root["appVersion"] ?: "",
            fileVersion = fileVersion,
            roots = roots,
            items = items,
        )
    }

    private fun parseItem(el: XmlElement): ProjectItem? {
        val handle = el["handle"] ?: return null
        if (!isHandle(handle)) return null

        var itemName = ""
        var heading = "H0"
        var wordCount = 0
        var active = false
        for (child in el.children) {
            when (child.tag) {
                "meta" -> {
                    heading = child["heading"]?.ifEmpty { null } ?: "H0"
                    wordCount = child["wordCount"]?.toIntOrNull() ?: 0
                }
                "name" -> {
                    itemName = child.text ?: ""
                    active = child["active"] == "yes"
                }
            }
        }

        return ProjectItem(
            handle = handle,
            parent = el["parent"]?.takeIf(::isHandle),
            root = el["root"]?.takeIf(::isHandle),
            type = enumOr(el["type"], ItemType.NO_TYPE),
            itemClass = enumOr(el["class"], ItemClass.NO_CLASS),
            layout = enumOr(el["layout"], ItemLayout.NO_LAYOUT),
            name = itemName,
            heading = heading,
            wordCount = wordCount,
            active = active,
        )
    }

    /**
     * Order comes from the file, not the `order` attribute; items may appear before
     * their parent; non-root items whose parent is missing are left out; duplicates
     * keep the first one.
     * Source: novelWriter 26.3b1, `novelwriter/core/tree.py` `ProjectTree.unpack`.
     */
    private fun buildTree(parsed: List<ProjectItem>): Pair<List<ProjectNode>, Map<String, ProjectItem>> {
        val pending = LinkedHashMap<String, ProjectItem>()
        for (item in parsed) pending.putIfAbsent(item.handle, item)

        val roots = mutableListOf<ProjectNode>()
        val nodes = HashMap<String, ProjectNode>()
        var remaining: Map<String, ProjectItem> = pending
        repeat(MAX_DEPTH) {
            if (remaining.isEmpty()) return@repeat
            val later = LinkedHashMap<String, ProjectItem>()
            for ((handle, item) in remaining) {
                if (item.type == ItemType.ROOT) {
                    val node = ProjectNode(item.copy(parent = null))
                    roots += node
                    nodes[handle] = node
                    continue
                }
                val parentHandle = item.parent ?: continue
                val parentNode = nodes[parentHandle]
                if (parentNode != null) {
                    val node = ProjectNode(item)
                    parentNode.children += node
                    nodes[handle] = node
                } else if (parentHandle in remaining) {
                    later[handle] = item
                }
            }
            remaining = later
        }
        return roots to nodes.mapValues { it.value.item }
    }

    private const val MAX_DEPTH = 1000
}

private inline fun <reified T : Enum<T>> enumOr(value: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: default
