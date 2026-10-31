package app.tinynovelwriter.format

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Checks the readers against a real project copied to the PC. Skipped unless
 * NW_PROJECT points at a project root, e.g. a copy made with `adb pull`.
 */
class RealProjectTest {

    private val root = System.getenv("NW_PROJECT")?.let(::File)

    @Test
    fun readsEveryDocumentAndHashesMatch() {
        assumeTrue("NW_PROJECT not set", root != null && File(root, "nwProject.nwx").isFile)
        val project = File(root, "nwProject.nwx").inputStream().use(ProjectXml::parse)
        val files = File(root, "content").listFiles { f -> f.extension == "md" && isHandle(f.nameWithoutExtension) }!!

        assertTrue(files.isNotEmpty())
        var wordCountMatches = 0
        for (file in files) {
            val doc = DocumentReader.parse(file.readBytes())
            val handle = file.nameWithoutExtension
            assertTrue("$handle has a header", doc.hasHeader)
            assertEquals("$handle handle in header", handle, doc.meta.handle)
            assertEquals("$handle textHash", doc.meta.textHash, sha1(doc.text))
            // The header name is only refreshed when the document is saved, so a
            // rename in the tree leaves it stale; the project file is authoritative.
            project.items[handle]?.takeIf { it.name != doc.meta.name }?.let {
                println("$handle: tree name '${it.name}', header name '${doc.meta.name}'")
            }
            // Only numbers are reported, never text. The stored count can lag behind
            // the text if the index is stale, so this is informational.
            project.items[handle]?.let {
                val words = Counting.standardCounter(doc.text).words
                if (words != it.wordCount) println("$handle: counted $words words, project file says ${it.wordCount}")
                else wordCountMatches++
            }
        }
        val notInTree = files.map { it.nameWithoutExtension }.filter { it !in project.items }
        println("Read ${project.items.size} tree items and ${files.size} documents from '${project.name}'; not in tree: $notInTree")
        println("Word counts matching the project file: $wordCountMatches of ${files.size}")
    }

    /** Highlights every document; reports only span counts per kind and timings. */
    @Test
    fun highlightsEveryDocument() {
        assumeTrue("NW_PROJECT not set", root != null && File(root, "nwProject.nwx").isFile)
        val project = File(root, "nwProject.nwx").inputStream().use(ProjectXml::parse)
        val files = File(root, "content").listFiles { f -> f.extension == "md" && isHandle(f.nameWithoutExtension) }!!

        val kinds = sortedMapOf<Markup, Int>()
        var slowest = 0L to ""
        for (file in files) {
            val doc = DocumentReader.parse(file.readBytes())
            val itemClass = project.items[file.nameWithoutExtension]?.itemClass ?: ItemClass.NOVEL
            Highlighter.highlight(doc.text, itemClass) // warm-up
            val started = System.nanoTime()
            val spans = Highlighter.highlight(doc.text, itemClass)
            val micros = (System.nanoTime() - started) / 1000
            if (micros > slowest.first) slowest = micros to "${file.nameWithoutExtension} (${doc.text.length} chars)"
            for (span in spans) {
                assertTrue(span.start in 0 until span.end && span.end <= doc.text.length)
                kinds.merge(span.kind, 1, Int::plus)
            }
        }
        println("Highlight spans by kind: $kinds")
        println("Slowest: ${slowest.first} µs for ${slowest.second}")
    }

    /**
     * Re-saving unchanged text must give back the exact bytes novelWriter wrote. Failures
     * report only the handle and a byte offset, never content.
     */
    @Test
    fun unchangedDocumentsRewriteByteIdentical() {
        assumeTrue("NW_PROJECT not set", root != null && File(root, "nwProject.nwx").isFile)
        val project = File(root, "nwProject.nwx").inputStream().use(ProjectXml::parse)
        val files = File(root, "content").listFiles { f -> f.extension == "md" && isHandle(f.nameWithoutExtension) }!!

        val now = "2099-01-01 00:00:00" // would show up in the output if a date changed
        val differentWithTreeNames = mutableListOf<String>()
        for (file in files) {
            val handle = file.nameWithoutExtension
            val original = file.readBytes()
            val doc = DocumentReader.parse(original)

            // Header fields as they are in the file: must round-trip exactly.
            val fromHeader = HeaderItem(doc.meta.name, doc.meta.parent, handle, doc.meta.itemClass, doc.meta.layout)
            val same = DocumentWriter.render(doc.text, fromHeader, doc, now).bytes
            assertTrue("$handle differs at byte ${firstDifference(original, same)}", original.contentEquals(same))

            // Header fields from the tree, as the app will save: only stale names may differ.
            project.items[handle]?.let { item ->
                val bytes = DocumentWriter.render(doc.text, HeaderItem.of(item), doc, now).bytes
                if (!original.contentEquals(bytes)) {
                    val fields = listOfNotNull(
                        "name".takeIf { item.name != doc.meta.name },
                        "parent".takeIf { item.parent != doc.meta.parent },
                        "class".takeIf { item.itemClass != doc.meta.itemClass },
                        "layout".takeIf { item.layout != doc.meta.layout },
                    )
                    differentWithTreeNames += "$handle $fields"
                }
            }
        }
        println("Byte-identical rewrites: ${files.size} of ${files.size}; changed by tree fields: $differentWithTreeNames")
    }

    private fun firstDifference(a: ByteArray, b: ByteArray): Int =
        (0 until minOf(a.size, b.size)).firstOrNull { a[it] != b[it] } ?: minOf(a.size, b.size)

    private fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
