package app.tinynovelwriter.data

import app.tinynovelwriter.format.DocumentReader
import app.tinynovelwriter.format.DocumentWriter
import app.tinynovelwriter.format.HeaderItem
import app.tinynovelwriter.format.LockFile
import app.tinynovelwriter.format.LockInfo
import app.tinynovelwriter.format.NwDocument
import app.tinynovelwriter.format.NwProject
import app.tinynovelwriter.format.ProjectXml
import app.tinynovelwriter.format.formatTimestamp
import app.tinynovelwriter.format.isHandle
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime

const val DEFAULT_PROJECT_PATH = "/storage/emulated/0/Documents/NovelWriter"

private const val PROJECT_FILE = "nwProject.nwx"
private const val INDEX_FILE = "index.json"

data class LoadedProject(
    val project: NwProject,
    /** Null when there is no lock file, i.e. novelWriter wasn't open at the last sync. */
    val lock: LockInfo?,
)

/** A file's modification time and size, used to notice changes cheaply. */
data class FileStamp(val modified: Long, val size: Long) {
    companion object {
        fun of(file: File) = FileStamp(file.lastModified(), file.length())
    }
}

/** A document as last read from or written to disk. */
data class OpenedDocument(val handle: String, val document: NwDocument, val stamp: FileStamp)

sealed interface SaveResult {
    data class Saved(val opened: OpenedDocument) : SaveResult
    /** The file changed on disk since it was opened; nothing was written. [onDisk] is the new version. */
    data class ChangedOnDisk(val onDisk: OpenedDocument) : SaveResult
    /** The document file no longer exists; the app never creates documents, so nothing was written. */
    data object Missing : SaveResult
}

/** Access to a novelWriter project folder on local storage. Writes only existing documents. */
class ProjectFolder(val root: File) {

    private val contentDir = File(root, "content")

    /** @throws Exception with a user-readable message when the folder isn't a usable project. */
    fun load(): LoadedProject {
        val projectFile = File(root, PROJECT_FILE)
        if (!projectFile.isFile) throw IllegalStateException("No $PROJECT_FILE in ${root.path}")

        val project = projectFile.inputStream().use(ProjectXml::parse)
        val lockFile = File(root, LockFile.NAME)
        val lock = if (lockFile.isFile) LockFile.parse(lockFile.readText()) else null
        return LoadedProject(project, lock)
    }

    /** Returns null when the document has no file on disk (novelWriter treats it as empty). */
    fun openDocument(handle: String): OpenedDocument? {
        val file = documentFile(handle)
        if (!file.exists()) return null
        // Stamp before reading: if the file changes mid-read, the next save re-checks it.
        val stamp = FileStamp.of(file)
        return OpenedDocument(handle, DocumentReader.parse(file.readBytes()), stamp)
    }

    /**
     * Saves [text] over the document, the way novelWriter does: refuse if the file changed
     * on disk since [opened] (unless [overwrite]), write `<handle>.tmp`, then atomically
     * replace the `.md`. Header fields come from the tree via [item].
     */
    fun saveDocument(
        opened: OpenedDocument,
        text: String,
        item: HeaderItem,
        overwrite: Boolean = false,
        now: LocalDateTime = LocalDateTime.now(),
    ): SaveResult {
        require(item.handle == opened.handle) { "Header item doesn't match the document" }
        val file = documentFile(opened.handle)
        if (!file.exists()) return SaveResult.Missing

        // novelWriter's check: a changed stamp only means "look closer"; the hash decides.
        var previous = opened
        if (FileStamp.of(file) != opened.stamp) {
            val onDisk = openDocument(opened.handle) ?: return SaveResult.Missing
            if (onDisk.document.bodyHash != opened.document.bodyHash && !overwrite) {
                return SaveResult.ChangedOnDisk(onDisk)
            }
            previous = onDisk
        }

        val rendered = DocumentWriter.render(text, item, previous.document, formatTimestamp(now))
        writeAtomically(file, File(contentDir, "${opened.handle}.tmp"), rendered.bytes)

        // A copy of the phone's version, in case the PC later overwrites the document
        // (novelWriter open there with stale text). One per document, replaced each save.
        // novelWriter only picks up `<handle>.md` files, so it ignores these. Best effort.
        runCatching {
            writeAtomically(File(contentDir, "${opened.handle}.md.bkp"), File(contentDir, "${opened.handle}.bkp.tmp"), rendered.bytes)
        }

        retireIndex()

        val saved = NwDocument(
            meta = rendered.meta,
            hasHeader = true,
            text = rendered.text,
            bodyHash = rendered.meta.textHash,
            lineEnding = previous.document.lineEnding,
        )
        return SaveResult.Saved(OpenedDocument(opened.handle, saved, FileStamp.of(file)))
    }

    /** Write [bytes] to [temp], flush it to disk, then rename it over [target] in one step. */
    private fun writeAtomically(target: File, temp: File, bytes: ByteArray) {
        try {
            FileOutputStream(temp).use { out ->
                out.write(bytes)
                out.fd.sync() // on disk before the rename, so a crash can't leave a short file
            }
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            temp.delete()
            throw e
        }
    }

    /**
     * After a phone edit novelWriter's index is stale, and it only re-indexes documents
     * missing from it. Without `meta/index.json` its revision no longer matches the
     * project file, so novelWriter rebuilds the whole index on the next open (with a
     * "Rebuilding index" message). The old index is kept as `index.json.old`, replacing
     * the previous one. Best effort: a failure here doesn't undo the save.
     */
    private fun retireIndex() {
        val index = File(root, "meta/$INDEX_FILE")
        if (!index.exists()) return
        runCatching {
            Files.move(
                index.toPath(),
                File(root, "meta/$INDEX_FILE.old").toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private fun documentFile(handle: String): File {
        require(isHandle(handle)) { "Not a handle: $handle" }
        return File(contentDir, "$handle.md")
    }
}
