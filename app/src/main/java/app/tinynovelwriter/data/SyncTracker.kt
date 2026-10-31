package app.tinynovelwriter.data

/** What FolderSync last reported through its webhooks, or that we're waiting for it. */
sealed interface SyncState {
    data object Unknown : SyncState
    /** Sync requested at [since] (ms); no webhook yet. */
    data class Waiting(val since: Long) : SyncState
    /** FolderSync's "starting" webhook arrived at [since]; waiting for the result. */
    data class Running(val since: Long) : SyncState
    data class Synced(val at: Long) : SyncState
    /** [kind] is "failed" (includes conflicts), "cancelled", or "noresponse" (no webhook in time). */
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
 * timestamped; a successful-sync webhook covers writes made before the sync started: the
 * request time, the "starting" webhook, or (for a sync FolderSync ran by itself without a
 * "starting") [GRACE_MS] before the result arrived.
 *
 * A requested sync that sends no webhook within [START_TIMEOUT_MS] never started (e.g.
 * Android refused FolderSync's background service); one that started but sends no result
 * within [RUN_TIMEOUT_MS] is also reported as "noresponse".
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
            "starting" -> {
                if (requestedAt == 0L) requestedAt = now
                state = SyncState.Running(now)
                onChange()
                return
            }
            else -> return
        }
        requestedAt = 0
        save(state)
        onChange()
    }

    val inProgress: Boolean get() = state is SyncState.Waiting || state is SyncState.Running

    /** Call periodically while [inProgress]: reports "noresponse" once a timeout has passed. */
    fun checkTimeout() {
        val now = clock()
        val expired = when (val s = state) {
            is SyncState.Waiting -> now - s.since >= START_TIMEOUT_MS
            is SyncState.Running -> now - s.since >= RUN_TIMEOUT_MS
            else -> false
        }
        if (!expired) return
        requestedAt = 0
        state = SyncState.Failed(now, "noresponse")
        save(state)
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
            "failed", "cancelled", "noresponse" -> SyncState.Failed(at, parts[0])
            else -> SyncState.Unknown
        }
    }

    companion object {
        const val GRACE_MS = 5_000L
        const val START_TIMEOUT_MS = 15_000L
        const val RUN_TIMEOUT_MS = 10 * 60_000L
        private const val KEY_LAST_WRITE = "sync.lastWriteAt"
        private const val KEY_COVERED = "sync.coveredUpTo"
        private const val KEY_RESULT = "sync.lastResult"
    }
}
