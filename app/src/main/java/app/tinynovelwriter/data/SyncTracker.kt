package app.tinynovelwriter.data

/** What FolderSync last reported through its webhooks, or that we're waiting for it. */
sealed interface SyncState {
    data object Unknown : SyncState
    /** Sync requested at [since] (ms); waiting for FolderSync's webhook. */
    data class Waiting(val since: Long) : SyncState
    data class Synced(val at: Long) : SyncState
    /** [kind] is "failed" (includes conflicts) or "cancelled". */
    data class Failed(val at: Long, val kind: String) : SyncState
}

/** Where the tracker keeps its few values between launches (SharedPreferences in the app). */
interface SyncStore {
    fun getLong(key: String): Long
    fun putLong(key: String, value: Long)
    fun getString(key: String): String?
    fun putString(key: String, value: String?)
}

/**
 * Whether saved edits have reached FolderSync's last successful sync. Every write is
 * timestamped; a successful-sync webhook covers writes made before the sync started.
 * FolderSync doesn't say when a sync started, so for a sync this app requested that's the
 * request time, and for one FolderSync ran by itself it's assumed to be [GRACE_MS] before
 * the webhook arrived.
 */
class SyncTracker(private val store: SyncStore, private val clock: () -> Long = System::currentTimeMillis) {

    /** Called with the new state after every change, on the caller's thread. */
    var onChange: () -> Unit = {}

    var state: SyncState = load()
        private set

    private var lastWriteAt = store.getLong(KEY_LAST_WRITE)
    private var coveredUpTo = store.getLong(KEY_COVERED)
    private var requestedAt = 0L

    /** Saved edits not yet covered by a successful sync. */
    val unsynced: Boolean get() = lastWriteAt > coveredUpTo

    fun noteWrite() {
        lastWriteAt = clock()
        store.putLong(KEY_LAST_WRITE, lastWriteAt)
        onChange()
    }

    fun noteSyncRequested() {
        requestedAt = clock()
        state = SyncState.Waiting(requestedAt)
        onChange()
    }

    /** A FolderSync webhook arrived; [event] is the last path segment we configured. */
    fun onWebhook(event: String) {
        val now = clock()
        val startedBy = if (requestedAt > 0) requestedAt else now - GRACE_MS
        when (event) {
            "successful" -> {
                coveredUpTo = maxOf(coveredUpTo, startedBy)
                store.putLong(KEY_COVERED, coveredUpTo)
                state = SyncState.Synced(now)
            }
            "failed", "cancelled" -> state = SyncState.Failed(now, event)
            else -> return // "starting" arrives with the others at the end, so it says nothing
        }
        requestedAt = 0
        save(state)
        onChange()
    }

    /** No webhook came for a requested sync (e.g. the app was frozen); stop waiting. */
    fun expireWaiting() {
        val waiting = state as? SyncState.Waiting ?: return
        if (clock() - waiting.since < WAIT_MS) return
        requestedAt = 0
        state = load()
        onChange()
    }

    private fun save(state: SyncState) {
        val value = when (state) {
            is SyncState.Synced -> "synced:${state.at}"
            is SyncState.Failed -> "${state.kind}:${state.at}"
            else -> null
        }
        store.putString(KEY_RESULT, value)
    }

    private fun load(): SyncState {
        val parts = store.getString(KEY_RESULT)?.split(":") ?: return SyncState.Unknown
        val at = parts.getOrNull(1)?.toLongOrNull() ?: return SyncState.Unknown
        return when (parts[0]) {
            "synced" -> SyncState.Synced(at)
            "failed", "cancelled" -> SyncState.Failed(at, parts[0])
            else -> SyncState.Unknown
        }
    }

    companion object {
        const val GRACE_MS = 5_000L
        const val WAIT_MS = 60_000L
        private const val KEY_LAST_WRITE = "sync.lastWriteAt"
        private const val KEY_COVERED = "sync.coveredUpTo"
        private const val KEY_RESULT = "sync.lastResult"
    }
}
