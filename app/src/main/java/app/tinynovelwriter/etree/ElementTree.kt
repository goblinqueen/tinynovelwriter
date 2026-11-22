package app.tinynovelwriter.etree

/** Like Python's ElementTree `Element`: [text] comes before the first child, [tail] after the end tag. */
class XmlElement(val tag: String, attributes: Map<String, String> = emptyMap()) {
    val attributes = LinkedHashMap(attributes)
    val children = mutableListOf<XmlElement>()
    var text: String? = null
    var tail: String? = null

    operator fun get(key: String): String? = attributes[key]
    operator fun set(key: String, value: String) { attributes[key] = value }

    /** First child with this tag (ElementTree `find`). */
    fun find(tag: String): XmlElement? = children.firstOrNull { it.tag == tag }

    /** Children with this tag (ElementTree `findall`). */
    fun findAll(tag: String): List<XmlElement> = children.filter { it.tag == tag }

    /** Appends a new child, like ElementTree `SubElement`. */
    fun subElement(tag: String, attributes: Map<String, String> = emptyMap()): XmlElement =
        XmlElement(tag, attributes).also { children += it }
}

/** A parsed file. [prolog] is the text before the root element (the XML declaration), kept as is. */
class XmlDocument(val prolog: String, val root: XmlElement, val lineEnding: String)

class XmlFormatException(message: String) : Exception(message)

/**
 * Reads and writes XML exactly like Python's ElementTree, so novelWriter's files come back unchanged.
 * Refuses comments, CDATA and DOCTYPE: a file it doesn't fully understand is never rewritten.
 * Source: Python 3.12 `xml/etree/ElementTree.py` `_serialize_xml`; novelWriter 26.3b1, `novelwriter/core/projectxml.py` `ProjectXMLWriter.write`.
 */
object ElementTree {

    fun parse(bytes: ByteArray): XmlDocument {
        val raw = bytes.toString(Charsets.UTF_8)
        val lineEnding = if ("\r\n" in raw) "\r\n" else "\n"
        val text = raw.replace("\r\n", "\n")
        if ('\r' in text) throw XmlFormatException("mixed line endings")
        return Parser(text).parseDocument(lineEnding)
    }

    fun render(document: XmlDocument): ByteArray {
        val out = StringBuilder(document.prolog)
        write(out, document.root)
        val text = out.toString().let { if (document.lineEnding == "\n") it else it.replace("\n", document.lineEnding) }
        return text.toByteArray(Charsets.UTF_8)
    }

    /** Re-indents the whole tree the way novelWriter saves it. Call after adding, moving or removing elements. */
    fun reindent(root: XmlElement) {
        fun clear(el: XmlElement) {
            if (el.children.isNotEmpty() && el.text?.isBlank() == true) el.text = null
            if (el.tail?.isBlank() == true) el.tail = null
            el.children.forEach(::clear)
        }
        clear(root)
        indent(root)
    }

    /**
     * Fills in missing indentation only.
     * Source: novelWriter 26.3b1, `novelwriter/common.py` `xmlIndent`.
     */
    fun indent(root: XmlElement) {
        val indentations = mutableListOf("\n")
        fun indentChildren(el: XmlElement, level: Int) {
            val childLevel = level + 1
            val childIndent = indentations.getOrNull(childLevel)
                ?: (indentations[level] + "  ").also { indentations += it }
            if (el.text == null) el.text = childIndent
            var last: XmlElement? = null
            for (child in el.children) {
                if (child.children.isNotEmpty()) indentChildren(child, childLevel)
                if (child.tail == null) {
                    child.tail = childIndent
                    last = child
                }
            }
            last?.tail = indentations[level]
        }
        if (root.children.isNotEmpty()) indentChildren(root, 0)
        root.tail = "\n"
    }

    private fun write(out: StringBuilder, el: XmlElement) {
        out.append('<').append(el.tag)
        for ((key, value) in el.attributes) out.append(' ').append(key).append("=\"").append(escapeAttribute(value)).append('"')
        val text = el.text
        if (!text.isNullOrEmpty() || el.children.isNotEmpty()) {
            out.append('>')
            if (!text.isNullOrEmpty()) out.append(escapeText(text))
            for (child in el.children) write(out, child)
            out.append("</").append(el.tag).append('>')
        } else {
            out.append(" />")
        }
        el.tail?.let { out.append(escapeText(it)) }
    }

    // ElementTree `_escape_cdata`.
    private fun escapeText(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    // ElementTree `_escape_attrib`.
    private fun escapeAttribute(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        .replace("\r", "&#13;").replace("\n", "&#10;").replace("\t", "&#09;")

    private class Parser(private val s: String) {
        private var i = 0

        fun parseDocument(lineEnding: String): XmlDocument {
            var start = s.indexOf('<')
            while (start >= 0 && s.startsWith("<?", start)) {
                val close = s.indexOf("?>", start).takeIf { it >= 0 } ?: fail("unterminated declaration")
                start = s.indexOf('<', close + 2)
            }
            if (start < 0) fail("no root element")
            val prolog = s.substring(0, start)
            if (prolog.isNotEmpty() && !PROLOG.matches(prolog)) fail("unsupported content before the root element")
            i = start
            val root = parseElement()
            val rest = s.substring(i)
            if (rest.isNotBlank()) fail("content after the root element")
            root.tail = rest.ifEmpty { null }
            return XmlDocument(prolog, root, lineEnding)
        }

        private fun parseElement(): XmlElement {
            expect('<')
            if (peek() == '!' || peek() == '?' || peek() == '/') fail("unsupported markup at offset $i")
            val el = XmlElement(readName())
            while (true) {
                skipSpace()
                when {
                    s.startsWith("/>", i) -> { i += 2; return el }
                    peek() == '>' -> { i++; break }
                    else -> {
                        val name = readName()
                        skipSpace(); expect('='); skipSpace()
                        val quote = peek()
                        if (quote != '"' && quote != '\'') fail("unquoted attribute at offset $i")
                        i++
                        val end = s.indexOf(quote, i).takeIf { it >= 0 } ?: fail("unterminated attribute")
                        val value = s.substring(i, end)
                        if ('<' in value) fail("'<' in attribute at offset $i")
                        // Attribute-value normalization: literal whitespace becomes a space.
                        val normalized = unescape(value.replace('\n', ' ').replace('\t', ' '))
                        if (el.attributes.put(name, normalized) != null) fail("duplicate attribute $name")
                        i = end + 1
                    }
                }
            }
            // Content: text, child elements with their tails, then the end tag.
            el.text = readText()
            while (true) {
                if (s.startsWith("</", i)) {
                    i += 2
                    val name = readName()
                    if (name != el.tag) fail("</$name> closes <${el.tag}>")
                    skipSpace(); expect('>')
                    return el
                }
                val child = parseElement()
                child.tail = readText()
                el.children += child
            }
        }

        private fun readText(): String? {
            val end = s.indexOf('<', i).takeIf { it >= 0 } ?: fail("unexpected end of file")
            val raw = s.substring(i, end)
            i = end
            return if (raw.isEmpty()) null else unescape(raw)
        }

        private fun unescape(raw: String): String {
            if ('&' !in raw) return raw
            val out = StringBuilder()
            var j = 0
            while (j < raw.length) {
                val c = raw[j]
                if (c != '&') { out.append(c); j++; continue }
                val semi = raw.indexOf(';', j).takeIf { it > j } ?: fail("bad entity")
                val name = raw.substring(j + 1, semi)
                when {
                    name == "amp" -> out.append('&')
                    name == "lt" -> out.append('<')
                    name == "gt" -> out.append('>')
                    name == "quot" -> out.append('"')
                    name == "apos" -> out.append('\'')
                    name.startsWith("#x") -> out.appendCodePoint(name.substring(2).toIntOrNull(16) ?: fail("bad entity"))
                    name.startsWith("#") -> out.appendCodePoint(name.substring(1).toIntOrNull() ?: fail("bad entity"))
                    else -> fail("unknown entity &$name;")
                }
                j = semi + 1
            }
            return out.toString()
        }

        private fun readName(): String {
            val start = i
            while (i < s.length && !s[i].isWhitespace() && s[i] !in "/>=<\"'") i++
            if (i == start) fail("expected a name at offset $start")
            return s.substring(start, i)
        }

        private fun skipSpace() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        private fun peek(): Char = if (i < s.length) s[i] else fail("unexpected end of file")

        private fun expect(c: Char) {
            if (peek() != c) fail("expected '$c' at offset $i")
            i++
        }

        private fun fail(why: String): Nothing = throw XmlFormatException(why)
    }

    private val PROLOG = Regex("""﻿?(<\?xml[^<>]*\?>)?\s*""")
}
