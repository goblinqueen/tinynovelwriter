package app.tinynovelwriter.ui

import app.tinynovelwriter.format.ItemClass
import app.tinynovelwriter.resolve
import app.tinynovelwriter.R
import androidx.compose.ui.res.stringResource
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tinynovelwriter.format.Counting
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScreen(
    title: String,
    editor: DocumentEditor,
    onLeave: () -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    /** Typewriter scrolling position, in percent of the visible height. */
    typewriterPosition: Int,
) {
    val scope = rememberCoroutineScope()
    val status = editor.status
    val text = editor.value.text
    val words = remember(text) { Counting.standardCounter(text).words }
    var askLeave by remember { mutableStateOf(false) }

    // Leaving saves first; if that fails, ask instead of losing edits.
    val leave: () -> Unit = {
        scope.launch {
            if (editor.save()) onLeave() else askLeave = true
        }
    }

    BackHandler(onBack = leave)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
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
                        }) { Text(stringResource(if (editor.editing) R.string.done else R.string.edit)) }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).imePadding()) {
            if (status is EditorStatus.Conflict) {
                Banner(
                    stringResource(R.string.conflict_message),
                    MaterialTheme.colorScheme.errorContainer,
                ) {
                    TextButton(onClick = { scope.launch { editor.takeTheirs() } }) { Text(stringResource(R.string.conflict_reload_theirs)) }
                    TextButton(onClick = { scope.launch { editor.keepMine() } }) { Text(stringResource(R.string.conflict_keep_mine)) }
                }
            } else if (status is EditorStatus.Failed) {
                Banner(stringResource(R.string.save_failed, status.message.resolve()), MaterialTheme.colorScheme.errorContainer) {
                    TextButton(onClick = { scope.launch { editor.save() } }) { Text(stringResource(R.string.retry)) }
                }
            }

            // Refresh never replaces unsaved edits (see DocumentEditor.reloadIfClean).
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when (status) {
                    EditorStatus.Loading -> Message(stringResource(R.string.loading))
                    is EditorStatus.Unavailable -> Message(status.message.resolve())
                    else ->
                        if (text.isBlank() && !editor.editing) Message(stringResource(R.string.document_empty))
                        else DocumentText(editor.value, editor::onValueChange, readOnly = !editor.editing, editor.item?.itemClass, typewriterPosition)
                }
            }
        }
    }

    if (askLeave) {
        AlertDialog(
            onDismissRequest = { askLeave = false },
            title = { Text(stringResource(R.string.leave_unsaved_title)) },
            text = { Text(stringResource(R.string.leave_unsaved_message)) },
            confirmButton = { TextButton(onClick = { askLeave = false; onLeave() }) { Text(stringResource(R.string.leave)) } },
            dismissButton = { TextButton(onClick = { askLeave = false }) { Text(stringResource(R.string.stay)) } },
        )
    }
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
private fun DocumentText(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    readOnly: Boolean,
    itemClass: ItemClass?,
    typewriterPosition: Int,
) {
    val colors = MaterialTheme.colorScheme
    val markupColors = TinyColors.markup
    val markup = remember(markupColors, itemClass) { MarkupTransformation(markupColors, itemClass ?: ItemClass.NOVEL) }
    val scroll = rememberScrollState()
    // Tapping Edit leaves focus on the button, so a hardware keyboard's arrow keys would
    // move between buttons. Put focus in the text when editing starts.
    val focus = remember { FocusRequester() }
    // Focusing the field pops up the on-screen keyboard even when a hardware keyboard is
    // connected (Android only hides it once typing starts), so hide it right away then.
    val softKeyboard = LocalSoftwareKeyboardController.current
    val config = LocalConfiguration.current
    val hasHardwareKeyboard = config.keyboard == Configuration.KEYBOARD_QWERTY &&
        config.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO
    LaunchedEffect(readOnly) {
        if (!readOnly) {
            focus.requestFocus()
            if (hasHardwareKeyboard) softKeyboard?.hide()
        }
    }
    val vertical = remember { VerticalArrows() }
    // Typewriter scrolling: an edit records the caret's top before it, the new layout
    // compares it with the caret's top after it.
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val topPadding = 12.dp
    val topPaddingPx = with(density) { topPadding.toPx() }
    val caretBefore = remember { CaretBefore() }
    fun caretTop(layout: TextLayoutResult, offset: Int) =
        topPaddingPx + layout.getCursorRect(offset.coerceIn(0, layout.layoutInput.text.length)).top
    val change: (TextFieldValue) -> Unit = { new ->
        if (!readOnly && new.text != value.text) vertical.layout?.let { caretBefore.top = caretTop(it, value.selection.end) }
        onValueChange(new)
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            BasicTextField(
                value = value,
                onValueChange = change,
                readOnly = readOnly,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                visualTransformation = markup,
                onTextLayout = { layout ->
                    vertical.layout = layout
                    val before = caretBefore.top
                    if (before != null && layout.layoutInput.text.text == value.text) {
                        caretBefore.top = null
                        if (value.selection.collapsed) {
                            val after = caretTop(layout, value.selection.end)
                            Typewriter.target(before, after, scroll.value, scroll.viewportSize, typewriterPosition)?.let {
                                scope.launch { scroll.animateScrollTo(it, tween(Typewriter.DURATION_MS)) }
                            }
                        }
                    }
                },
                // Extra end padding keeps text clear of the scrollbar.
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .onPreviewKeyEvent { event ->
                        if (readOnly) return@onPreviewKeyEvent false
                        val moved = formatKey(event, value)
                            ?: lineEdgeKey(event, value, vertical.layout)
                            ?: horizontalArrow(event, value)
                            ?: vertical.handle(event, value)
                            ?: return@onPreviewKeyEvent false
                        if (moved != value) change(moved)
                        true
                    }
                    .padding(start = 16.dp, end = 24.dp, top = topPadding, bottom = 12.dp),
            )
            // Scroll past the end while editing, so the last line can reach the typewriter position.
            if (!readOnly) Spacer(Modifier.height(with(density) { (scroll.viewportSize * (100 - typewriterPosition) / 100f).toDp() }))
        }
        VerticalScrollbar(scroll, Modifier.align(Alignment.CenterEnd))
    }
}


/** Not state: it's written during layout and must not trigger recomposition. */
private class CaretBefore {
    var top: Float? = null
}
