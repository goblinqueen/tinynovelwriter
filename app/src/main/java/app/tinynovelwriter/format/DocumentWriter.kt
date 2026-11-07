package app.tinynovelwriter.format

import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Timestamp format, in local time.
 * Source: novelWriter 26.3b1, `novelwriter/constants.py` `nwConst.FMT_TSTAMP`.
 */
private val TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

fun formatTimestamp(time: LocalDateTime): String = TIMESTAMP.format(time)

internal fun sha1Hex(text: String): String =
    MessageDigest.getInstance("SHA-1").digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

/** The tree fields novelWriter writes into a document header. */
data class HeaderItem(
    val name: String,
    val parent: String?,
    val handle: String,
    val itemClass: ItemClass,
    val layout: ItemLayout,
) {
    companion object {
        fun of(item: ProjectItem) = HeaderItem(item.name, item.parent, item.handle, item.itemClass, item.layout)
    }
}

data class RenderedDocument(
    val bytes: ByteArray,
    /** The body text as saved (with the final newline); its hash is [meta]'s textHash. */
    val text: String,
    val meta: DocumentMeta,
)

/**
 * Builds a document file the way novelWriter saves one, without the file I/O.
 * Source: novelWriter 26.3b1, `novelwriter/core/document.py` `ProjectDocument.writeDocument`.
 */
object DocumentWriter {

    /**
     * @param text body text with LF line endings, as edited.
     * @param previous the document as last read from disk, or null for a file that doesn't exist.
     * @param now the save time, formatted with [formatTimestamp].
     * @param lineEnding line ending to write; defaults to the one the file used.
     */
    fun render(
        text: String,
        item: HeaderItem,
        previous: NwDocument?,
        now: String,
        lineEnding: String = previous?.lineEnding ?: "\n",
    ): RenderedDocument {
        val body = if (text.isNotEmpty() && !text.endsWith("\n")) text + "\n" else text
        val writeHash = sha1Hex(body)

        var createdDate = previous?.meta?.createdDate ?: "Unknown"
        var updatedDate = previous?.meta?.updatedDate ?: "Unknown"
        if (writeHash != previous?.bodyHash) updatedDate = now
        if (previous == null) {
            createdDate = now
            updatedDate = now
        }

        val header = listOf(
            "name" to item.name,
            "parent" to (item.parent ?: ""),
            "handle" to item.handle,
            "class" to item.itemClass.name,
            "layout" to item.layout.name,
            "textHash" to writeHash,
            "createdDate" to createdDate,
            "updatedDate" to updatedDate,
        ).joinToString("\n") { (key, value) -> "$key = ${dumpString(value)}" }.trim()

        // Python writes "\n" as the platform line ending, so every newline is translated.
        val content = "+++\n$header\n+++\n$body".replace("\n", lineEnding)

        val meta = DocumentMeta(
            name = item.name,
            parent = item.parent,
            handle = item.handle,
            itemClass = item.itemClass,
            layout = item.layout,
            textHash = writeHash,
            createdDate = createdDate,
            updatedDate = updatedDate,
        )
        return RenderedDocument(content.toByteArray(Charsets.UTF_8), body, meta)
    }

    /**
     * A basic string escaping only \ " tab LF CR.
     * Source: novelWriter 26.3b1, `novelwriter/formats/tomlparser.py` `NTomlParser._dumpStr`.
     */
    internal fun dumpString(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\t", "\\t")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
        return "\"$escaped\""
    }
}
