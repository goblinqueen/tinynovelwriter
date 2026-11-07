package app.tinynovelwriter.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import app.tinynovelwriter.novelwriter.Highlighter
import app.tinynovelwriter.novelwriter.ItemClass
import app.tinynovelwriter.novelwriter.Markup
import app.tinynovelwriter.novelwriter.MarkupSpan

/** Colours for novelWriter markup, named after novelWriter's syntax theme entries. */
data class MarkupColors(
    val headerText: Color,
    val headerTag: Color,
    val emphasis: Color,
    val hidden: Color,
    val link: Color,
    val note: Color,
    val shortcode: Color,
    val keyword: Color,
    val tag: Color,
    val value: Color,
    val optional: Color,
    val modifier: Color,
    val replaceTag: Color,
    val textHighlight: Color,
    val whitespace: Color,
)

/**
 * novelWriter's editor styles, headings at normal size.
 * Source: novelWriter 26.3b1, `novelwriter/editor/highlighter.py` `GuiDocHighlighter.initHighlighter`.
 */
private fun MarkupColors.style(kind: Markup): SpanStyle = when (kind) {
    Markup.HEADING_MARK -> SpanStyle(color = headerTag, fontWeight = FontWeight.Bold)
    Markup.HEADING -> SpanStyle(color = headerText, fontWeight = FontWeight.Bold)
    Markup.BOLD -> SpanStyle(color = emphasis, fontWeight = FontWeight.Bold)
    Markup.ITALIC -> SpanStyle(color = emphasis, fontStyle = FontStyle.Italic)
    Markup.STRIKE -> SpanStyle(color = hidden, textDecoration = TextDecoration.LineThrough)
    Markup.MARK -> SpanStyle(background = textHighlight)
    Markup.NOBREAK -> SpanStyle(background = whitespace)
    Markup.MARKUP, Markup.HIDDEN -> SpanStyle(color = hidden)
    Markup.LINK -> SpanStyle(color = link, textDecoration = TextDecoration.Underline)
    Markup.NOTE -> SpanStyle(color = note)
    Markup.CODE -> SpanStyle(color = shortcode)
    Markup.KEYWORD -> SpanStyle(color = keyword)
    Markup.TAG -> SpanStyle(color = tag, textDecoration = TextDecoration.Underline)
    Markup.VALUE -> SpanStyle(color = value)
    Markup.OPTIONAL -> SpanStyle(color = optional)
    Markup.MODIFIER -> SpanStyle(color = modifier)
    Markup.REPLACE -> SpanStyle(color = replaceTag)
}

/**
 * Styles novelWriter markup without changing the text, so it works the same for a
 * read-only view and an editor. The marks stay visible, as in novelWriter's editor.
 */
class MarkupTransformation(private val colors: MarkupColors, private val itemClass: ItemClass) : VisualTransformation {

    private val styles = Markup.entries.associateWith { colors.style(it) }

    // filter() runs on the whole text after every keystroke; lines are cached so only
    // changed ones are highlighted again. Cleared when it grows past any likely document.
    private val lineCache = HashMap<String, List<MarkupSpan>>()

    private fun cachedLine(line: String, inactive: Boolean): List<MarkupSpan> {
        if (lineCache.size > 20_000) lineCache.clear()
        return lineCache.getOrPut(line) { Highlighter.highlightLine(line, inactive) }
    }

    override fun filter(text: AnnotatedString): TransformedText {
        val styled = AnnotatedString.Builder(text.text)
        for (span in Highlighter.highlight(text.text, itemClass, ::cachedLine)) {
            styled.addStyle(styles.getValue(span.kind), span.start, span.end)
        }
        return TransformedText(styled.toAnnotatedString(), OffsetMapping.Identity)
    }

    override fun equals(other: Any?) =
        other is MarkupTransformation && other.colors == colors && other.itemClass == itemClass

    override fun hashCode() = 31 * colors.hashCode() + itemClass.hashCode()
}
