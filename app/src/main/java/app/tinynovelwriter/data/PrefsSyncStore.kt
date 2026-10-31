package app.tinynovelwriter.data

import android.content.SharedPreferences

/** [SyncStore] backed by the app's SharedPreferences. */
class PrefsSyncStore(private val prefs: SharedPreferences) : SyncStore {
    override fun getLong(key: String) = prefs.getLong(key, 0L)
    override fun putLong(key: String, value: Long) = prefs.edit().putLong(key, value).apply()
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String?) = prefs.edit().putString(key, value).apply()
}

/** A snapshot of the tracker for the UI. */
data class SyncStatus(val state: SyncState, val unsynced: Boolean)
