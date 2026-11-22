package app.tinynovelwriter.novelwriter

import app.tinynovelwriter.etree.XmlDocument
import app.tinynovelwriter.etree.XmlElement

/**
 * Writes a document's counts and the project totals into the `.nwx`, as novelWriter does.
 * Source: novelWriter 26.3b1, `novelwriter/core/item.py` `NWItem.pack`, `novelwriter/core/tree.py` `ProjectTree.sumCounts`.
 */
object ProjectCounts {

    /** Returns false if nothing changed. */
    fun set(file: XmlDocument, handle: String, counts: TextCounts): Boolean {
        val content = file.root.find("content") ?: return false
        val item = content.children.firstOrNull {
            it.tag == "item" && it["handle"] == handle && it["type"] == ItemType.FILE.name
        } ?: return false
        val meta = item.find("meta") ?: return false

        var changed = false
        fun put(el: XmlElement, key: String, value: Int) {
            val text = value.toString()
            if (el[key] != text) {
                el[key] = text
                changed = true
            }
        }

        put(meta, "charCount", counts.chars)
        put(meta, "wordCount", counts.words)
        put(meta, "paraCount", counts.paragraphs)

        var novelWords = 0
        var notesWords = 0
        var novelChars = 0
        var notesChars = 0
        for (el in content.children) {
            if (el.tag != "item") continue
            val m = el.find("meta") ?: continue
            val words = m["wordCount"]?.toIntOrNull() ?: 0
            val chars = m["charCount"]?.toIntOrNull() ?: 0
            when (el["layout"]) {
                ItemLayout.NOTE.name -> { notesWords += words; notesChars += chars }
                ItemLayout.DOCUMENT.name -> { novelWords += words; novelChars += chars }
            }
        }
        put(content, "novelWords", novelWords)
        put(content, "notesWords", notesWords)
        put(content, "novelChars", novelChars)
        put(content, "notesChars", notesChars)
        return changed
    }
}
