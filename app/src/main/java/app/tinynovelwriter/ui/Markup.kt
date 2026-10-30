package app.tinynovelwriter.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** Colours for novelWriter markup; [heading] is the text, [headingMarker] the `#` marks. */
data class MarkupColors(val heading: Color, val headingMarker: Color)

/** Heading prefixes as novelWriter's highlighter checks them; longer `#` runs first. */
private val HEADING_PREFIXES = listOf("#### ", "###! ", "### ", "##! ", "## ", "#! ", "# ")

/**
 * Styles novelWriter markup without changing the text, so it works the same for a
 * read-only view and an editor. Headings are bold and coloured at normal size, with
 * the marks visible, like novelWriter's editor with heading scaling off.
 */
fun highlightMarkup(text: String, colors: MarkupColors): AnnotatedString = buildAnnotatedString {
    append(text)
    var start = 0
    while (start <= text.length) {
        val end = text.indexOf('\n', start).let { if (it < 0) text.length else it }
        val prefix = HEADING_PREFIXES.firstOrNull { text.startsWith(it, start) }
        if (prefix != null) {
            val markerEnd = start + prefix.length - 1
            addStyle(SpanStyle(color = colors.headingMarker, fontWeight = FontWeight.Bold), start, markerEnd)
            addStyle(SpanStyle(color = colors.heading, fontWeight = FontWeight.Bold), markerEnd, end)
        }
        start = end + 1
    }
}

class MarkupTransformation(private val colors: MarkupColors) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(highlightMarkup(text.text, colors), OffsetMapping.Identity)

    override fun equals(other: Any?) = other is MarkupTransformation && other.colors == colors

    override fun hashCode() = colors.hashCode()
}
