package app.tinynovelwriter.data

import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import app.tinynovelwriter.ui.Typewriter

enum class ThemeChoice { PHONE, LIGHT, DARK }

/**
 * The user's settings, kept in SharedPreferences. Each value is Compose state, so
 * screens update when it changes; setting a value saves it right away.
 */
class AppSettings(private val prefs: SharedPreferences) {

    private val path = mutableStateOf(prefs.getString(KEY_PROJECT_PATH, null))
    /** The project folder, or null before one has been chosen. */
    var projectPath: String?
        get() = path.value
        set(value) { path.value = value; prefs.edit().putString(KEY_PROJECT_PATH, value).apply() }

    private val link = mutableStateOf(prefs.getString(KEY_SYNC_LINK, null).orEmpty())
    var syncLink: String
        get() = link.value
        set(value) { link.value = value; prefs.edit().putString(KEY_SYNC_LINK, value).apply() }

    // Off by default; on for installs that already had a link before this setting existed.
    private val useSync = mutableStateOf(prefs.getBoolean(KEY_SYNC_ENABLED, FolderSync.isValidLink(link.value)))
    var syncEnabled: Boolean
        get() = useSync.value
        set(value) { useSync.value = value; prefs.edit().putBoolean(KEY_SYNC_ENABLED, value).apply() }

    /** Whether the Sync button is shown: turned on and a link pasted. */
    val canSync: Boolean get() = syncEnabled && FolderSync.isValidLink(syncLink)

    private val position = mutableStateOf(prefs.getInt(KEY_TYPEWRITER_POSITION, Typewriter.DEFAULT_POSITION))
    var typewriterPosition: Int
        get() = position.value
        set(value) { position.value = value; prefs.edit().putInt(KEY_TYPEWRITER_POSITION, value).apply() }

    private val theme = mutableStateOf(
        ThemeChoice.entries.firstOrNull { it.name == prefs.getString(KEY_THEME, null) } ?: ThemeChoice.PHONE
    )
    var themeChoice: ThemeChoice
        get() = theme.value
        set(value) { theme.value = value; prefs.edit().putString(KEY_THEME, value.name).apply() }

    companion object {
        const val PREFS = "settings"
        private const val KEY_PROJECT_PATH = "projectPath"
        private const val KEY_SYNC_LINK = "folderSyncLink"
        private const val KEY_SYNC_ENABLED = "folderSyncEnabled"
        private const val KEY_TYPEWRITER_POSITION = "typewriterPosition"
        private const val KEY_THEME = "theme"
    }
}
