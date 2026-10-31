package app.tinynovelwriter.ui

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

/** Follows the system light/dark setting, with the wallpaper-based colours (Android 12+). */
@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun TinyTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = colors, content = content)
}

/** App colours that Material's colour scheme doesn't cover, with light and dark variants. */
object TinyColors {
    /** The Sync button's "Synced" confirmation. */
    val syncSuccess: Color
        @Composable @ReadOnlyComposable get() = if (isSystemInDarkTheme()) Color(0xFF8FD19E) else Color(0xFF1E7A34)
}
