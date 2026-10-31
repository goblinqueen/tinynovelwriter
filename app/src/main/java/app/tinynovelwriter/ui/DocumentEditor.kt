package app.tinynovelwriter.ui

import app.tinynovelwriter.UiText
import app.tinynovelwriter.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import app.tinynovelwriter.data.OpenedDocument
import app.tinynovelwriter.data.ProjectFolder
import app.tinynovelwriter.data.SaveResult
import app.tinynovelwriter.format.HeaderItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface EditorStatus {
    data object Loading : EditorStatus
    /** Nothing to save, or the last save succeeded. */
    data object Idle : EditorStatus
    data object Saving : EditorStatus
    data class Failed(val message: UiText) : EditorStatus
    /** The file changed on disk under unsaved edits; the user picks a side. */
    data class Conflict(val onDisk: OpenedDocument) : EditorStatus
    /** The document couldn't be opened, or has no file on disk. */
    data class Unavailable(val message: UiText) : EditorStatus
}

/**
 * One open document: its text, whether it's being edited, and saving. Saves go
 * through [ProjectFolder.saveDocument], one at a time.
 */
class DocumentEditor(private val folder: ProjectFolder, val handle: String) {

    var value by mutableStateOf(TextFieldValue(""))
        private set
    var editing by mutableStateOf(false)
    var status by mutableStateOf<EditorStatus>(EditorStatus.Loading)
        private set

    /** Header fields from the tree; null if the document isn't in the tree (then no editing). */
    var item: HeaderItem? by mutableStateOf(null)

    private var opened: OpenedDocument? = null
    /** Text as last loaded or saved; differs from [value] when there are unsaved edits. */
    private var savedText by mutableStateOf("")
    private val mutex = Mutex()
    /** Called after every successful write, e.g. to remember that a sync is due. */
    var onWritten: () -> Unit = {}

    val dirty: Boolean get() = value.text != savedText
    val canEdit: Boolean get() = opened != null && item != null

    fun onValueChange(new: TextFieldValue) {
        if (!editing) {
            value = new.copy(text = value.text) // selection only
            return
        }
        // Pasted text may bring CR line endings; the document is LF in memory.
        val text = new.text
        value = if ('\r' in text) new.copy(text = text.replace("\r\n", "\n").replace('\r', '\n')) else new
        if (status is EditorStatus.Failed) status = EditorStatus.Idle
    }

    /** Loads the document, or picks up a newer version from disk when there are no unsaved edits. */
    suspend fun reloadIfClean() = mutex.withLock {
        if (dirty || status is EditorStatus.Conflict) return@withLock
        val result = withContext(Dispatchers.IO) { runCatching { folder.openDocument(handle) } }
        result.fold(
            onSuccess = { doc ->
                if (doc == null) {
                    opened = null
                    status = EditorStatus.Unavailable(UiText.of(R.string.document_no_file))
                } else {
                    if (doc.stamp != opened?.stamp) show(doc)
                    status = EditorStatus.Idle
                }
            },
            onFailure = { status = EditorStatus.Unavailable(UiText.of(it)) },
        )
    }

    /**
     * Saves unsaved edits. Returns true when nothing is left unsaved. Never cancelled
     * half-way: a write whose result was dropped would look like a change on disk later.
     */
    suspend fun save(overwrite: Boolean = false): Boolean = withContext(NonCancellable) { mutex.withLock {
        val doc = opened ?: return@withLock !dirty
        val header = item ?: return@withLock !dirty
        val text = value.text
        if (text == savedText && !overwrite) return@withLock true

        status = EditorStatus.Saving
        val result = withContext(Dispatchers.IO) { runCatching { folder.saveDocument(doc, text, header, overwrite) } }
        result.fold(
            onSuccess = { saved ->
                when (saved) {
                    is SaveResult.Saved -> {
                        opened = saved.opened
                        savedText = text
                        onWritten()
                        status = EditorStatus.Idle
                    }
                    is SaveResult.ChangedOnDisk -> status = EditorStatus.Conflict(saved.onDisk)
                    SaveResult.Missing -> status = EditorStatus.Failed(UiText.of(R.string.save_failed_file_gone))
                }
            },
            onFailure = { status = EditorStatus.Failed(UiText.of(it)) },
        )
        !dirty && status == EditorStatus.Idle
    } }

    /** Conflict: drop the phone edits and show the version on disk. */
    suspend fun takeTheirs() = mutex.withLock {
        val conflict = status as? EditorStatus.Conflict ?: return@withLock
        show(conflict.onDisk)
        status = EditorStatus.Idle
    }

    /** Conflict: overwrite the version on disk with the phone edits. */
    suspend fun keepMine() {
        if (status is EditorStatus.Conflict) save(overwrite = true)
    }

    private fun show(doc: OpenedDocument) {
        opened = doc
        savedText = doc.document.text
        value = TextFieldValue(doc.document.text, TextRange.Zero)
    }
}
