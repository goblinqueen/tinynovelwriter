package app.tinynovelwriter.etree

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * `input.xml` and `edited.xml` are made by Python's ElementTree and novelWriter's xmlIndent
 * (`src/test/resources/etree/make_fixtures.py`), so these compare against the real thing.
 */
class ElementTreeTest {

    private fun resource(name: String): ByteArray = javaClass.getResourceAsStream("/etree/$name")!!.use { it.readBytes() }

    @Test
    fun unchangedFileRendersByteIdentical() {
        val input = resource("input.xml")
        val doc = ElementTree.parse(input)
        assertEquals("\r\n", doc.lineEnding)
        assertArrayEquals(input, ElementTree.render(doc))

        val lf = input.toString(Charsets.UTF_8).replace("\r\n", "\n").toByteArray()
        val lfDoc = ElementTree.parse(lf)
        assertEquals("\n", lfDoc.lineEnding)
        assertArrayEquals(lf, ElementTree.render(lfDoc))
    }

    @Test
    fun keepsAttributeOrderAndUnescapesValues() {
        val doc = ElementTree.parse(resource("input.xml"))
        val item = doc.root.find("content")!!.findAll("item")[1]
        assertEquals(listOf("handle", "parent", "root", "order", "type", "class", "layout"), item.attributes.keys.toList())
        assertEquals("Глава 1", item.find("name")!!.text)
        assertEquals("Проект & <тест> \"q\" 'a'", doc.root.find("project")!!.find("name")!!.text)
        val entry = doc.root.find("settings")!!.find("autoReplace")!!.find("entry")!!
        assertEquals("<a> & \"b\"\nc\td\r", entry["key"])
        assertEquals("v > w", entry.text)
    }

    @Test
    fun reindentMatchesFreshBuild() {
        // The same edit as make_fixtures.py.
        val doc = ElementTree.parse(resource("input.xml"))
        val content = doc.root.find("content")!!
        content.subElement(
            "item",
            linkedMapOf(
                "handle" to "aaaaaaaaaaaa3", "parent" to "aaaaaaaaaaaa0", "root" to "aaaaaaaaaaaa0", "order" to "0",
                "type" to "FILE", "class" to "NOVEL", "layout" to "NOTE",
            ),
        ).apply {
            subElement(
                "meta",
                linkedMapOf(
                    "expanded" to "no", "heading" to "H1", "charCount" to "15", "wordCount" to "3",
                    "paraCount" to "1", "cursorPos" to "0",
                ),
            )
            subElement("name", linkedMapOf("status" to "s1", "import" to "i1", "active" to "yes")).text = "Новая заметка"
        }
        val folder = XmlElement("item", linkedMapOf("handle" to "aaaaaaaaaaaa2", "type" to "FOLDER")).apply {
            subElement("meta", mapOf("expanded" to "no"))
            subElement("name").text = "Папка"
        }
        content.children.add(1, folder)
        doc.root.find("project")!!.find("author")!!.subElement("first").text = "А"

        ElementTree.reindent(doc.root)
        assertEquals(resource("edited.xml").toString(Charsets.UTF_8), ElementTree.render(doc).toString(Charsets.UTF_8))
    }

    @Test
    fun reindentLeavesNovelWriterFileUnchanged() {
        val input = resource("input.xml")
        val doc = ElementTree.parse(input)
        ElementTree.reindent(doc.root)
        assertArrayEquals(input, ElementTree.render(doc))
    }

    @Test
    fun removingAnElementThenReindentKeepsLayout() {
        val doc = ElementTree.parse(resource("edited.xml"))
        val content = doc.root.find("content")!!
        content.children.removeAt(content.children.lastIndex) // the last child holds the closing indentation
        content.children.removeAt(1)
        val author = doc.root.find("project")!!.find("author")!!
        author.children.clear()
        author.text = null // emptied by hand: see reindent
        ElementTree.reindent(doc.root)
        assertArrayEquals(resource("input.xml"), ElementTree.render(doc))
    }

    @Test
    fun refusesWhatItDoesNotUnderstand() {
        for (bad in listOf(
            "<a><!-- c --></a>",
            "<a><![CDATA[x]]></a>",
            "<!DOCTYPE a><a />",
            "<a><b></a>",
            "<a x=\"1\" x=\"2\" />",
            "<a>&nbsp;</a>",
            "<a />trailing",
            "<a>\r\n\r</a>",
            "not xml",
        )) {
            assertThrows(bad, XmlFormatException::class.java) { ElementTree.parse(bad.toByteArray()) }
        }
    }
}
