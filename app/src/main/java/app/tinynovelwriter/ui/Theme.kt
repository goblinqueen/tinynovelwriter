package app.tinynovelwriter.ui

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.tinynovelwriter.data.ThemeChoice

/**
 * Light or dark as set by [applyTheme] (which follows the phone by default), with the
 * wallpaper-based colours (Android 12+).
 */
@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun TinyTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = colors, content = content)
}

/**
 * Sets the app's light/dark mode. Android keeps it across launches and recreates the
 * activity, so the window, status bar and every isSystemInDarkTheme() follow it.
 */
fun applyTheme(context: Context, choice: ThemeChoice) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val mode = when (choice) {
        ThemeChoice.PHONE -> UiModeManager.MODE_NIGHT_AUTO
        ThemeChoice.LIGHT -> UiModeManager.MODE_NIGHT_NO
        ThemeChoice.DARK -> UiModeManager.MODE_NIGHT_YES
    }
    context.getSystemService(UiModeManager::class.java)?.setApplicationNightMode(mode)
}

/** App colours that Material's colour scheme doesn't cover, with light and dark variants. */
object TinyColors {
    /** The Sync button's "Synced" confirmation. */
    val syncSuccess: Color
        @Composable @ReadOnlyComposable get() = if (isSystemInDarkTheme()) Color(0xFF8FD19E) else Color(0xFF1E7A34)

    /**
     * Markup highlighting: novelWriter's default syntax themes, the `[Syntax]` section of
     * `novelwriter/assets/themes/default_light.toml` and `default_dark.toml` (novelWriter 26.3b1).
     * Notes like `green:L135` are the theme file's own values.
     */
    val markup: MarkupColors
        @Composable @ReadOnlyComposable get() = if (isSystemInDarkTheme()) MARKUP_DARK else MARKUP_LIGHT

    private val MARKUP_LIGHT = MarkupColors(
        headerText = Color(0xFF296629),
        headerTag = Color(0xFF378A37), // green:L135
        emphasis = Color(0xFFB36829),
        hidden = Color(0xFF6C6C6C),
        link = Color(0xFF3A70A6),
        note = Color(0xFF827C2A), // yellow:D125
        shortcode = Color(0xFF3A70A6),
        keyword = Color(0xFFA62A2D),
        tag = Color(0xFF296629),
        value = Color(0xFF296629),
        optional = Color(0xFF3A70A6),
        modifier = Color(0xFF3A70A6),
        replaceTag = Color(0xFF296629),
        textHighlight = Color(0x48A39C34), // yellow:72
        whitespace = Color(0x40B36829), // orange:64
    )

    private val MARKUP_DARK = MarkupColors(
        headerText = Color(0xFF99CC99),
        headerTag = Color(0xFF668866), // green:D150
        emphasis = Color(0xFFFF9966),
        hidden = Color(0xFF949494),
        link = Color(0xFF6699CC),
        note = Color(0xFFFFCC66),
        shortcode = Color(0xFF6699CC),
        keyword = Color(0xFFFF6666),
        tag = Color(0xFF99CC99),
        value = Color(0xFF99CC99),
        optional = Color(0xFF6699CC),
        modifier = Color(0xFF6699CC),
        replaceTag = Color(0xFF99CC99),
        textHighlight = Color(0x48FFCC66),
        whitespace = Color(0x40FF9966),
    )
}
