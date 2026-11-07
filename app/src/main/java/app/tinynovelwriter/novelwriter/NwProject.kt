package app.tinynovelwriter.novelwriter

import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

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
 * Reads `nwProject.nwx`. Read-only: this app never writes the project file.
 * Source: novelWriter 26.3b1, `novelwriter/core/projectxml.py` `ProjectXMLReader`.
 */
object ProjectXml {

    fun parse(input: InputStream): NwProject {
        val doc = try {
            newFactory().newDocumentBuilder().parse(input)
        } catch (e: Exception) {
            throw ProjectFormatException("Project file is not valid XML", e)
        }

        val root = doc.documentElement
        if (root.tagName != "novelWriterXML") {
            throw ProjectFormatException("Not a novelWriter project file (root <${root.tagName}>)")
        }
        val fileVersion = root.getAttribute("fileVersion")
        if (fileVersion != SUPPORTED_FILE_VERSION) {
            throw ProjectFormatException(
                "Unsupported project format $fileVersion (need $SUPPORTED_FILE_VERSION, novelWriter 26.2 or newer)"
            )
        }

        var name = ""
        var author = ""
        val parsed = mutableListOf<ProjectItem>()
        for (section in root.childElements()) {
            when (section.tagName) {
                "project" -> for (child in section.childElements()) {
                    when (child.tagName) {
                        "name" -> name = child.textContent
                        "author" -> author = child.textContent
                    }
                }
                "content" -> for (child in section.childElements()) {
                    if (child.tagName == "item") parseItem(child)?.let(parsed::add)
                }
            }
        }

        val (roots, items) = buildTree(parsed)
        return NwProject(
            name = name,
            author = author,
            appVersion = root.getAttribute("appVersion"),
            fileVersion = fileVersion,
            roots = roots,
            items = items,
        )
    }

    private fun parseItem(el: Element): ProjectItem? {
        val handle = el.getAttribute("handle")
        if (!isHandle(handle)) return null

        var itemName = ""
        var heading = "H0"
        var wordCount = 0
        var active = false
        for (child in el.childElements()) {
            when (child.tagName) {
                "meta" -> {
                    heading = child.getAttribute("heading").ifEmpty { "H0" }
                    wordCount = child.getAttribute("wordCount").toIntOrNull() ?: 0
                }
                "name" -> {
                    itemName = child.textContent
                    active = child.getAttribute("active") == "yes"
                }
            }
        }

        return ProjectItem(
            handle = handle,
            parent = el.getAttribute("parent").takeIf(::isHandle),
            root = el.getAttribute("root").takeIf(::isHandle),
            type = enumOr(el.getAttribute("type"), ItemType.NO_TYPE),
            itemClass = enumOr(el.getAttribute("class"), ItemClass.NO_CLASS),
            layout = enumOr(el.getAttribute("layout"), ItemLayout.NO_LAYOUT),
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

    private fun newFactory(): DocumentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        isExpandEntityReferences = false
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
    }

    private const val MAX_DEPTH = 1000
}

private fun Element.childElements(): List<Element> {
    val list = childNodes
    return (0 until list.length).mapNotNull { list.item(it) as? Element }
}

private inline fun <reified T : Enum<T>> enumOr(value: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: default
