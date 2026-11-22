package app.tinynovelwriter.data

import android.content.Context
import android.os.Handler
import android.os.Looper

/** The app's one [SyncTracker], per process: a tracker per activity missed webhooks after the activity was recreated. */
object SyncHub {
    private var tracker: SyncTracker? = null
    private val listeners = LinkedHashSet<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    /** Call on the main thread; creates the tracker and starts the listener the first time. */
    fun get(context: Context): SyncTracker = tracker ?: run {
        val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        SyncTracker(PrefsSyncStore(prefs)).also { created ->
            created.onChange = { listeners.toList().forEach { it() } }
            tracker = created
            WebhookListener.start { event -> main.post { created.onWebhook(event) } }
        }
    }

    fun addListener(listener: () -> Unit) { listeners += listener }
    fun removeListener(listener: () -> Unit) { listeners -= listener }
}
