package app.tinynovelwriter.ui

import app.tinynovelwriter.novelwriter.ItemClass
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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tinynovelwriter.novelwriter.Counting
import kotlin.math.roundToInt
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

    val scroll = rememberScrollState()
    val vertical = remember { VerticalArrows() }
    val topPaddingPx = with(LocalDensity.current) { TEXT_TOP_PADDING.toPx() }

    val done: () -> Unit = { scope.launch { editor.save(); editor.editing = false } }
    // Back while editing is Done; a second Back leaves. A hardware Esc arrives as Back
    // while the keyboard app is active (and since Android 16 the app can't tell them
    // apart), so this is also what makes Esc stop editing.
    BackHandler { if (editor.editing) done() else leave() }
    // Option C: the caret goes to the end if that's on screen, else to the line at the
    // typewriter position, so starting to edit never moves the page.
    val startEditing: () -> Unit = {
        val length = editor.value.text.length
        val layout = vertical.layout?.takeIf { it.layoutInput.text.length == length }
        val caret = layout?.let {
            Typewriter.editStartY(scroll.value, scroll.viewportSize, topPaddingPx + it.size.height, typewriterPosition)
                ?.let { y -> it.getLineStart(it.getLineForVerticalPosition((y - topPaddingPx).coerceAtLeast(0f))) }
        } ?: length
        editor.onValueChange(editor.value.copy(selection = TextRange(caret)))
        editor.editing = true
    }

    // Hardware keyboard outside the text field: in reading mode Up/Down scroll a line,
    // PageUp/PageDown a screen, E starts editing; while editing Esc is Done (Android
    // would otherwise turn it into Back).
    val goal = remember { ScrollGoal() }
    fun scrollTo(target: Int) {
        goal.value = target.coerceIn(0, scroll.maxValue)
        scope.launch { scroll.animateScrollTo(goal.value, tween(Typewriter.DURATION_MS)) }
    }
    fun scrollBy(delta: Float) {
        val base = if (scroll.isScrollInProgress) goal.value else scroll.value
        scrollTo(base + delta.roundToInt())
    }
    fun screenKey(event: KeyEvent): Boolean {
        val down = event.type == KeyEventType.KeyDown
        if (editor.editing) {
            if (event.key != Key.Escape) return false
            if (down) done()
            return true
        }
        val line = vertical.layout?.takeIf { it.lineCount > 0 }?.let { it.getLineBottom(0) - it.getLineTop(0) } ?: 0f
        val page = scroll.viewportSize - line
        // Ctrl+Up/Down jump to the start/end, as Ctrl+Home/End do; Space and Shift+Space
        // page as in a browser, for keyboards without PageUp/PageDown.
        when (event.key) {
            Key.DirectionUp -> if (down) { if (event.isCtrlPressed) scrollTo(0) else scrollBy(-line) }
            Key.DirectionDown -> if (down) { if (event.isCtrlPressed) scrollTo(scroll.maxValue) else scrollBy(line) }
            Key.MoveHome -> if (down) scrollTo(0)
            Key.MoveEnd -> if (down) scrollTo(scroll.maxValue)
            Key.PageUp -> if (down) scrollBy(-page)
            Key.PageDown -> if (down) scrollBy(page)
            Key.Spacebar -> if (down) scrollBy(if (event.isShiftPressed) -page else page)
            Key.E -> {
                if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed || !editor.canEdit) return false
                if (down) startEditing()
            }
            else -> return false
        }
        return true
    }
    // In reading mode nothing else takes focus, so keys would go nowhere.
    val screenFocus = remember { FocusRequester() }
    LaunchedEffect(editor.editing) { if (!editor.editing) screenFocus.requestFocus() }

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
                        TextButton(onClick = if (editor.editing) done else startEditing) { Text(stringResource(if (editor.editing) R.string.done else R.string.edit)) }
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
                modifier = Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent(::screenKey)
                    .focusRequester(screenFocus)
                    .focusable(),
            ) {
                when (status) {
                    EditorStatus.Loading -> Message(stringResource(R.string.loading))
                    is EditorStatus.Unavailable -> Message(status.message.resolve())
                    else ->
                        if (text.isBlank() && !editor.editing) Message(stringResource(R.string.document_empty))
                        else DocumentText(
                            editor.value, editor::onValueChange, readOnly = !editor.editing, editor.item?.itemClass,
                            typewriterPosition, scroll, vertical, topPaddingPx,
                        )
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
    scroll: ScrollState,
    vertical: VerticalArrows,
    topPaddingPx: Float,
) {
    val colors = MaterialTheme.colorScheme
    val markupColors = TinyColors.markup
    val markup = remember(markupColors, itemClass) { MarkupTransformation(markupColors, itemClass ?: ItemClass.NOVEL) }
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
    // Typewriter scrolling: an edit records the caret's top before it, the new layout
    // compares it with the caret's top after it.
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val caretBefore = remember { CaretBefore() }
    fun caretTop(layout: TextLayoutResult, offset: Int) =
        topPaddingPx + layout.getCursorRect(offset.coerceIn(0, layout.layoutInput.text.length)).top
    // Moving the caret by keys doesn't relayout, so compare the two positions in the current layout.
    fun followCaret(from: Int, to: Int) {
        val layout = vertical.layout ?: return
        Typewriter.target(caretTop(layout, from), caretTop(layout, to), scroll.value, scroll.viewportSize, typewriterPosition)?.let {
            scope.launch { scroll.animateScrollTo(it, tween(Typewriter.DURATION_MS)) }
        }
    }
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
                    val old = vertical.layout
                    vertical.layout = layout
                    // Rotating rewraps the text, so the same scroll offset would show another
                    // part of it: keep the line at the top of the screen at the top instead.
                    if (old != null && old.size.width != layout.size.width && old.layoutInput.text.text == layout.layoutInput.text.text) {
                        val line = old.getLineForVerticalPosition((scroll.value - topPaddingPx).coerceAtLeast(0f))
                        val newLine = layout.getLineForOffset(old.getLineStart(line))
                        val top = (topPaddingPx + layout.getLineTop(newLine)).roundToInt()
                        if (scroll.value > 0) scope.launch { scroll.scrollTo(top) }
                    }
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
                        if (moved != value) {
                            if (moved.text == value.text) followCaret(value.selection.end, moved.selection.end)
                            change(moved)
                        }
                        true
                    }
                    .padding(start = 16.dp, end = 24.dp, top = TEXT_TOP_PADDING, bottom = 12.dp),
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

/** Where key scrolling is heading, so repeated presses add up while the animation runs. */
private class ScrollGoal {
    var value = 0
}

private val TEXT_TOP_PADDING = 12.dp
