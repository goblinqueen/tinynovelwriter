package app.tinynovelwriter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Always-visible scrollbar for a [ScrollState]: the thumb shows the position in the
 * document and how much of it is on screen. Drag along the right edge to scroll.
 */
@Composable
fun VerticalScrollbar(state: ScrollState, modifier: Modifier = Modifier) {
    if (state.maxValue <= 0 || state.viewportSize <= 0) return

    // Faded text colour: light grey on a light background, dim grey on a dark one.
    val thumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    var dragging by remember { mutableStateOf(false) }

    Canvas(
        modifier
            .fillMaxHeight()
            .width(24.dp) // touch target; the visible line is drawn at the right edge
            .pointerInput(state) {
                detectVerticalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, dragAmount ->
                    change.consume()
                    // Moving the thumb by its track's free length scrolls the whole document.
                    val track = size.height.toFloat()
                    val thumb = thumbHeight(track, state, minThumbPx = 32.dp.toPx())
                    val free = (track - thumb).coerceAtLeast(1f)
                    state.dispatchRawDelta(dragAmount * state.maxValue / free)
                }
            },
    ) {
        // A thin line in the text colour, no track; slightly thicker while dragged.
        val barWidth = (if (dragging) 3.dp else 1.5.dp).toPx()
        val x = size.width - barWidth - 3.dp.toPx()
        val track = size.height
        val thumb = thumbHeight(track, state, minThumbPx = 32.dp.toPx())
        val top = (track - thumb) * state.value / state.maxValue

        drawRoundRect(thumbColor, Offset(x, top), Size(barWidth, thumb), CornerRadius(barWidth / 2))
    }
}

private fun thumbHeight(track: Float, state: ScrollState, minThumbPx: Float): Float {
    val total = state.maxValue + state.viewportSize
    return (track * state.viewportSize / total).coerceIn(minOf(minThumbPx, track), track)
}
