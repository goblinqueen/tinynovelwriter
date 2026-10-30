package app.tinynovelwriter.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tinynovelwriter.format.Counting
import app.tinynovelwriter.format.LockInfo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScreen(
    title: String,
    editor: DocumentEditor,
    lock: LockInfo?,
    onLeave: () -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val status = editor.status
    val text = editor.value.text
    val words = remember(text) { Counting.standardCounter(text).words }
    var askLeave by remember { mutableStateOf(false) }

    // Leaving saves first; if that fails, ask instead of losing edits.
    val leave: () -> Unit = {
        scope.launch { if (editor.save()) onLeave() else askLeave = true }
    }

    BackHandler(onBack = leave)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { TextButton(onClick = leave) { Text("‹ Back") } },
                actions = {
                    if (editor.editing) SaveStatus(editor)
                    if (status !is EditorStatus.Loading && status !is EditorStatus.Unavailable) {
                        Text(
                            "$words",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                    if (editor.canEdit) {
                        TextButton(onClick = {
                            if (editor.editing) {
                                scope.launch { editor.save(); editor.editing = false }
                            } else {
                                editor.editing = true
                            }
                        }) { Text(if (editor.editing) "Done" else "Edit") }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).imePadding()) {
            if (status is EditorStatus.Conflict) {
                Banner(
                    "This document changed on disk (probably synced from the PC) while you had unsaved edits.",
                    MaterialTheme.colorScheme.errorContainer,
                ) {
                    TextButton(onClick = { scope.launch { editor.takeTheirs() } }) { Text("Reload theirs") }
                    TextButton(onClick = { scope.launch { editor.keepMine() } }) { Text("Keep mine") }
                }
            } else if (status is EditorStatus.Failed) {
                Banner("Not saved: ${status.message}", MaterialTheme.colorScheme.errorContainer) {
                    TextButton(onClick = { scope.launch { editor.save() } }) { Text("Retry") }
                }
            } else if (editor.editing && lock != null) {
                Banner(
                    "novelWriter had this project open on ${lock.host.ifEmpty { "the PC" }} at the last sync. " +
                        "Close it there before editing here.",
                    MaterialTheme.colorScheme.secondaryContainer,
                )
            }

            // Refresh never replaces unsaved edits (see DocumentEditor.reloadIfClean).
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when (status) {
                    EditorStatus.Loading -> Message("Loading…")
                    is EditorStatus.Unavailable -> Message(status.message)
                    else ->
                        if (text.isBlank() && !editor.editing) Message("This document is empty.")
                        else DocumentText(editor.value, editor::onValueChange, readOnly = !editor.editing)
                }
            }
        }
    }

    if (askLeave) {
        AlertDialog(
            onDismissRequest = { askLeave = false },
            title = { Text("Changes not saved") },
            text = { Text("Your latest edits couldn't be saved. Leave anyway and lose them?") },
            confirmButton = { TextButton(onClick = { askLeave = false; onLeave() }) { Text("Leave") } },
            dismissButton = { TextButton(onClick = { askLeave = false }) { Text("Stay") } },
        )
    }
}

@Composable
private fun SaveStatus(editor: DocumentEditor) {
    val label = when {
        editor.status is EditorStatus.Saving -> "Saving…"
        editor.status is EditorStatus.Failed || editor.status is EditorStatus.Conflict -> "Not saved"
        editor.dirty -> "Edited"
        else -> "Saved"
    }
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Banner(message: String, color: Color, actions: @Composable () -> Unit = {}) {
    Surface(color = color, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp)) {
            Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp, bottom = 8.dp))
            Row(Modifier.align(Alignment.End)) { actions() }
        }
    }
}

/** The document body. The same field is the viewer (read-only) and the editor. */
@Composable
private fun DocumentText(value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit, readOnly: Boolean) {
    val colors = MaterialTheme.colorScheme
    val markup = remember(colors) { MarkupTransformation(MarkupColors(colors.primary, colors.outline)) }
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                readOnly = readOnly,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                visualTransformation = markup,
                // Extra end padding keeps text clear of the scrollbar.
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
            )
        }
        VerticalScrollbar(scroll, Modifier.align(Alignment.CenterEnd))
    }
}
