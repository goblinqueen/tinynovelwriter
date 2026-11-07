package app.tinynovelwriter

import app.tinynovelwriter.data.NotAProjectException
import androidx.compose.ui.res.stringResource
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.tinynovelwriter.data.DEFAULT_PROJECT_PATH
import app.tinynovelwriter.data.FolderSync
import app.tinynovelwriter.data.SyncHub
import app.tinynovelwriter.data.ProjectFolder
import app.tinynovelwriter.data.SyncStatus
import app.tinynovelwriter.data.SyncTracker
import app.tinynovelwriter.novelwriter.HeaderItem
import app.tinynovelwriter.novelwriter.ItemType
import app.tinynovelwriter.ui.DocumentScreen
import app.tinynovelwriter.ui.DocumentEditor
import app.tinynovelwriter.ui.EditorStatus
import app.tinynovelwriter.ui.ProjectScreen
import app.tinynovelwriter.ui.ProjectState
import app.tinynovelwriter.ui.TinyTheme
import app.tinynovelwriter.ui.Typewriter
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

private const val PREFS = "settings"
private const val KEY_PROJECT_PATH = "projectPath"
private const val KEY_SYNC_LINK = "folderSyncLink"
private const val KEY_TYPEWRITER_POSITION = "typewriterPosition"
private const val AUTOSAVE_DELAY_MS = 2000L

class MainActivity : ComponentActivity() {

    private var hasAccess by mutableStateOf(false)

    /** Bumped on every resume so the project is re-read after sync or a Settings round trip. */
    private var resumeCount by mutableIntStateOf(0)

    /** Bumped on every pause so an open document saves before the app goes to the background. */
    private var pauseCount by mutableIntStateOf(0)

    /** Bumped whenever the sync tracker changes, so Compose re-reads it. */
    private var syncVersion by mutableIntStateOf(0)
    private lateinit var syncTracker: SyncTracker
    private val onSyncChange: () -> Unit = { syncVersion++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        syncTracker = SyncHub.get(this)
        SyncHub.addListener(onSyncChange)
        setContent {
            TinyTheme {
                if (hasAccess) {
                    App(
                        initialPath = prefs.getString(KEY_PROJECT_PATH, null) ?: DEFAULT_PROJECT_PATH,
                        savePath = { prefs.edit().putString(KEY_PROJECT_PATH, it).apply() },
                        initialSyncLink = prefs.getString(KEY_SYNC_LINK, null).orEmpty(),
                        saveSyncLink = { prefs.edit().putString(KEY_SYNC_LINK, it).apply() },
                        initialTypewriterPosition = prefs.getInt(KEY_TYPEWRITER_POSITION, Typewriter.DEFAULT_POSITION),
                        saveTypewriterPosition = { prefs.edit().putInt(KEY_TYPEWRITER_POSITION, it).apply() },
                        syncTracker = syncTracker,
                        syncVersion = syncVersion,
                        resumeCount = resumeCount,
                        pauseCount = pauseCount,
                    )
                } else {
                    AccessScreen(onGrant = ::openAccessSettings)
                }
            }
        }
    }

    override fun onDestroy() {
        SyncHub.removeListener(onSyncChange)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        hasAccess = Environment.isExternalStorageManager()
        resumeCount++
    }

    override fun onPause() {
        pauseCount++
        super.onPause()
    }

    private fun openAccessSettings() {
        startActivity(
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
        )
    }
}

@Composable
private fun App(
    initialPath: String,
    savePath: (String) -> Unit,
    initialSyncLink: String,
    saveSyncLink: (String) -> Unit,
    initialTypewriterPosition: Int,
    saveTypewriterPosition: (Int) -> Unit,
    syncTracker: SyncTracker,
    /** Changes whenever [syncTracker] does; read so that its state is observed. */
    syncVersion: Int,
    resumeCount: Int,
    pauseCount: Int,
) {
    var path by rememberSaveable { mutableStateOf(initialPath) }
    var openHandle by rememberSaveable { mutableStateOf<String?>(null) }
    // Tree view state lives here so it survives opening a document; reset when the folder changes.
    val treeListState = rememberSaveable(path, saver = LazyListState.Saver) { LazyListState() }
    var toggled by rememberSaveable(path) { mutableStateOf(setOf<String>()) }
    val folder = ProjectFolder(File(path))
    var syncLink by rememberSaveable { mutableStateOf(initialSyncLink) }
    var typewriterPosition by rememberSaveable { mutableIntStateOf(initialTypewriterPosition) }
    val context = LocalContext.current
    val sync: () -> Unit = {
        if (syncLink.isNotBlank()) {
            val error = FolderSync.startSync(context, syncLink)
            if (error == null) syncTracker.noteSyncRequested() else Toast.makeText(context, error.resolve(context), Toast.LENGTH_LONG).show()
        }
    }
    // Report "no response" if FolderSync's webhooks don't come in time.
    LaunchedEffect(syncVersion, resumeCount) {
        while (syncTracker.inProgress) {
            delay(1000)
            syncTracker.checkTimeout()
        }
    }
    val syncStatus = remember(syncVersion) { SyncStatus(syncTracker.state, syncTracker.unsynced) }
    // Swipe-to-refresh: bumping refreshCount re-reads the project and the open document.
    var refreshCount by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    val onRefresh: () -> Unit = { refreshing = true; refreshCount++ }

    val projectState by produceState<ProjectState>(ProjectState.Loading, path, resumeCount, refreshCount) {
        value = withContext(Dispatchers.IO) {
            try {
                ProjectState.Loaded(folder.load())
            } catch (e: Exception) {
                ProjectState.Failed(
                    if (e is NotAProjectException) UiText.of(R.string.not_a_project, e.path) else UiText.of(e)
                )
            }
        }
        refreshing = false
    }

    val handle = openHandle
    if (handle == null) {
        ProjectScreen(
            state = projectState,
            projectPath = path,
            listState = treeListState,
            toggled = toggled,
            onToggle = { toggled = if (it in toggled) toggled - it else toggled + it },
            syncLink = syncLink,
            syncStatus = syncStatus,
            typewriterPosition = typewriterPosition,
            onChangeSettings = { newPath, newLink, newPosition ->
                path = newPath
                savePath(newPath)
                syncLink = newLink
                saveSyncLink(newLink)
                typewriterPosition = newPosition
                saveTypewriterPosition(newPosition)
            },
            onSync = sync,
            onOpenDocument = { openHandle = it },
            refreshing = refreshing,
            onRefresh = onRefresh,
        )
    } else {
        val loaded = (projectState as? ProjectState.Loaded)?.loaded
        val item = loaded?.project?.items?.get(handle)?.takeIf { it.type == ItemType.FILE }
        val editor = remember(path, handle) { DocumentEditor(folder, handle) }
        SideEffect {
            editor.item = item?.let(HeaderItem::of)
            editor.onWritten = { syncTracker.noteWrite() }
        }

        // Load, and pick up synced changes on resume or refresh (skipped while there are unsaved edits).
        LaunchedEffect(editor, resumeCount, refreshCount) {
            editor.reloadIfClean()
            refreshing = false
        }
        // Autosave 2 s after typing stops.
        LaunchedEffect(editor) {
            snapshotFlow { editor.value.text }.collectLatest {
                delay(AUTOSAVE_DELAY_MS)
                if (editor.dirty && editor.status !is EditorStatus.Conflict) editor.save()
            }
        }
        // Save when the app goes to the background.
        LaunchedEffect(editor, pauseCount) { if (pauseCount > 0) editor.save() }

        DocumentScreen(
            title = item?.name ?: "",
            editor = editor,
            onLeave = { openHandle = null },
            refreshing = refreshing,
            onRefresh = onRefresh,
            typewriterPosition = typewriterPosition,
        )
    }
}

@Composable
private fun AccessScreen(onGrant: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.access_explanation))
            Button(onClick = onGrant) { Text(stringResource(R.string.access_open_settings)) }
        }
    }
}
