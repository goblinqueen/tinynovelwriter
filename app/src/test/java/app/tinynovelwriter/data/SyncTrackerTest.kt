package app.tinynovelwriter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncTrackerTest {

    private class MapStore : SyncStore {
        val longs = HashMap<String, Long>()
        val strings = HashMap<String, String?>()
        override fun getLong(key: String) = longs[key] ?: 0L
        override fun putLong(key: String, value: Long) { longs[key] = value }
        override fun getString(key: String) = strings[key]
        override fun putString(key: String, value: String?) { strings[key] = value }
    }

    private var now = 1_000_000L
    private val store = MapStore()
    private fun tracker() = SyncTracker(store) { now }

    @Test
    fun successfulRequestedSyncCoversEarlierWrites() {
        val t = tracker()
        t.noteWrite()
        assertTrue(t.unsynced)
        now += 1000; t.noteSyncRequested()
        assertTrue(t.state is SyncState.Waiting)
        now += 3000; t.onWebhook("successful")
        assertFalse(t.unsynced)
        assertEquals(SyncState.Synced(now), t.state)
    }

    @Test
    fun writeDuringSyncStaysUnsynced() {
        val t = tracker()
        t.noteSyncRequested()
        now += 1000; t.noteWrite()
        now += 1000; t.onWebhook("successful")
        assertTrue(t.unsynced)
    }

    @Test
    fun failureKeepsEditsUnsynced() {
        val t = tracker()
        t.noteWrite()
        now += 1000; t.noteSyncRequested()
        now += 1000; t.onWebhook("failed")
        assertTrue(t.unsynced)
        assertEquals(SyncState.Failed(now, "failed"), t.state)
        now += 1000; t.onWebhook("cancelled")
        assertEquals("cancelled", (t.state as SyncState.Failed).kind)
    }

    @Test
    fun startingMeansRunningAndStopsTheShortTimeout() {
        val t = tracker()
        t.noteSyncRequested()
        now += 2000; t.onWebhook("starting")
        assertTrue(t.state is SyncState.Running)
        now += SyncTracker.START_TIMEOUT_MS; t.checkTimeout()
        assertTrue(t.state is SyncState.Running)
        now += 1000; t.onWebhook("successful")
        assertTrue(t.state is SyncState.Synced)
    }

    @Test
    fun noWebhookAfterRequestIsNoResponse() {
        val t = tracker()
        t.noteWrite()
        now += 1000; t.noteSyncRequested()
        now += SyncTracker.START_TIMEOUT_MS - 1; t.checkTimeout()
        assertTrue(t.state is SyncState.Waiting)
        now += 1; t.checkTimeout()
        assertEquals(SyncState.Failed(now, "noresponse"), t.state)
        assertTrue(t.unsynced)
        assertEquals(t.state, tracker().state) // survives a restart
    }

    @Test
    fun startedSyncWithoutResultTimesOutLater() {
        val t = tracker()
        t.noteSyncRequested()
        now += 1000; t.onWebhook("starting")
        now += SyncTracker.RUN_TIMEOUT_MS; t.checkTimeout()
        assertEquals("noresponse", (t.state as SyncState.Failed).kind)
    }

    @Test
    fun startingFromFolderSyncItselfMarksTheStart() {
        val t = tracker()
        t.noteWrite()
        now += 1000; t.onWebhook("starting")
        now += 1000; t.noteWrite() // during the sync
        now += 30_000; t.onWebhook("successful")
        assertTrue(t.unsynced)
    }

    @Test
    fun syncFolderSyncRanByItselfCoversWritesBeforeTheGrace() {
        val t = tracker()
        t.noteWrite()
        now += 60_000; t.onWebhook("successful")
        assertFalse(t.unsynced)

        // A write only just before the webhook may have missed that sync.
        now += 1000; t.noteWrite()
        now += 1000; t.onWebhook("successful")
        assertTrue(t.unsynced)
    }

    @Test
    fun stateSurvivesRestart() {
        val t = tracker()
        t.noteWrite()
        now += 1000; t.noteSyncRequested()
        now += 1000; t.onWebhook("successful")
        now += 1000; t.noteWrite()

        val restarted = tracker()
        assertTrue(restarted.unsynced)
        assertEquals(t.state, restarted.state)
    }
}
