package app.tinynovelwriter.novelwriter

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * Header lines novelWriter will scan for the closing `+++`.
 * Source: novelWriter 26.3b1, `novelwriter/core/document.py` `MAX_META_LINES`.
 */
private const val MAX_META_LINES = 20

/**
 * Header fields and their defaults.
 * Source: novelWriter 26.3b1, `novelwriter/core/document.py` `DocumentMeta`.
 */
data class DocumentMeta(
    val name: String = "",
    val parent: String? = null,
    val handle: String? = null,
    val itemClass: ItemClass = ItemClass.NOVEL,
    val layout: ItemLayout = ItemLayout.NOTE,
    val textHash: String = "",
    val createdDate: String = "Unknown",
    val updatedDate: String = "Unknown",
)

data class NwDocument(
    /** Parsed header, or defaults when the file has no header. */
    val meta: DocumentMeta,
    val hasHeader: Boolean,
    /** Body text after the header, LF line endings, unsafe control characters removed. */
    val text: String,
    /**
     * SHA-1 of the body as read, before control characters were removed. novelWriter's
     * `_lastHash`: decides whether a save changes `updatedDate`, and detects changes on disk.
     */
    val bodyHash: String = sha1Hex(text),
    /** Line ending the file uses on disk; saves keep it. */
    val lineEnding: String = "\n",
)

class DocumentFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Reads `content/<handle>.md` the way novelWriter's ProjectDocument.readDocument does:
 * strict UTF-8, universal newlines, optional TOML header between `+++` lines.
 * Source: novelWriter 26.3b1, `novelwriter/core/document.py` `ProjectDocument.readDocument`.
 */
object DocumentReader {

    fun parse(bytes: ByteArray): NwDocument {
        val decoded = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: CharacterCodingException) {
            throw DocumentFormatException("Document is not valid UTF-8", e)
        }
        return parse(decoded)
    }

    fun parse(raw: String): NwDocument {
        val content = normalizeNewlines(raw)
        val (metaLines, body) = splitHeader(content)
        val meta = if (metaLines.isNotEmpty()) {
            val values = try {
                FlatToml.parse(metaLines.joinToString(""))
            } catch (e: IllegalArgumentException) {
                throw DocumentFormatException("Document header is not valid: ${e.message}", e)
            }
            metaFrom(values)
        } else {
            DocumentMeta()
        }
        return NwDocument(
            meta = meta,
            hasHeader = metaLines.isNotEmpty(),
            text = stripUnsafe(body),
            bodyHash = sha1Hex(body),
            lineEnding = if ("\r\n" in raw) "\r\n" else "\n",
        )
    }

    /** Python text mode: `\r\n` and lone `\r` both become `\n`. */
    internal fun normalizeNewlines(text: String): String = text.replace("\r\n", "\n").replace('\r', '\n')

    /**
     * Lines keep their trailing `\n`.
     * Source: novelWriter 26.3b1, `novelwriter/core/document.py` `ProjectDocument._splitHeader`.
     */
    internal fun splitHeader(content: String): Pair<List<String>, String> {
        var pos = 0
        fun readLine(): String {
            if (pos >= content.length) return ""
            val end = content.indexOf('\n', pos).let { if (it < 0) content.length else it + 1 }
            return content.substring(pos, end).also { pos = end }
        }

        if (readLine().trim() != "+++") return emptyList<String>() to content
        val meta = mutableListOf<String>()
        repeat(MAX_META_LINES) {
            val line = readLine()
            if (line.isEmpty()) return emptyList<String>() to content
            if (line.trim() == "+++") return meta to content.substring(pos)
            meta += line
        }
        return emptyList<String>() to content
    }

    private fun metaFrom(values: Map<String, Any>): DocumentMeta {
        fun str(key: String, default: String) = values[key] as? String ?: default
        return DocumentMeta(
            name = str("name", ""),
            parent = str("parent", "").takeIf(::isHandle),
            handle = str("handle", "").takeIf(::isHandle),
            itemClass = enumUpper(values["class"], ItemClass.NOVEL),
            layout = enumUpper(values["layout"], ItemLayout.NOTE),
            textHash = str("textHash", ""),
            createdDate = str("createdDate", "Unknown"),
            updatedDate = str("updatedDate", "Unknown"),
        )
    }

    private inline fun <reified T : Enum<T>> enumUpper(value: Any?, default: T): T {
        val s = value as? String ?: return default
        return enumValues<T>().firstOrNull { it.name == s.uppercase() } ?: default
    }
}

/**
 * Drops C0/C1 control characters and lone surrogates, keeping `\t \n \v \f \r`,
 * U+001C..U+001F and U+0085.
 * Source: novelWriter 26.3b1, `novelwriter/common.py` `stripUnsafe`.
 */
internal fun stripUnsafe(text: String): String {
    val sb = StringBuilder(text.length)
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            c.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate() -> {
                sb.append(c).append(text[i + 1])
                i += 2
                continue
            }
            c.isSurrogate() -> Unit
            c.code < 0x20 || c.code in 0x7F until 0xA0 -> if (c in KEPT_CONTROLS) sb.append(c)
            else -> sb.append(c)
        }
        i++
    }
    return sb.toString()
}

private val KEPT_CONTROLS = setOf('\t', '\n', '\u000B', '\u000C', '\r', '\u001C', '\u001D', '\u001E', '\u001F', '\u0085')
