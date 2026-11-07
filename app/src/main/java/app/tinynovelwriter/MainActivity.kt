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
import app.tinynovelwriter.data.AppSettings
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
import app.tinynovelwriter.ui.SettingsScreen
import app.tinynovelwriter.ui.WelcomeScreen
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

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
        val settings = AppSettings(getSharedPreferences(AppSettings.PREFS, MODE_PRIVATE))
        syncTracker = SyncHub.get(this)
        SyncHub.addListener(onSyncChange)
        setContent {
            TinyTheme {
                if (hasAccess) {
                    App(
                        settings = settings,
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
    settings: AppSettings,
    syncTracker: SyncTracker,
    /** Changes whenever [syncTracker] does; read so that its state is observed. */
    syncVersion: Int,
    resumeCount: Int,
    pauseCount: Int,
) {
    // No project folder chosen yet (first start).
    val path = settings.projectPath
    if (path == null) {
        WelcomeScreen(missingPath = null, onPicked = { settings.projectPath = it })
        return
    }

    var openHandle by rememberSaveable(path) { mutableStateOf<String?>(null) }
    // Saveable, so the settings page stays open when a theme change recreates the activity.
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    // Tree view state lives here so it survives opening a document; reset when the folder changes.
    val treeListState = rememberSaveable(path, saver = LazyListState.Saver) { LazyListState() }
    var toggled by rememberSaveable(path) { mutableStateOf(setOf<String>()) }
    val folder = remember(path) { ProjectFolder(File(path)) }
    val context = LocalContext.current
    val sync: () -> Unit = {
        if (settings.canSync) {
            val error = FolderSync.startSync(context, settings.syncLink)
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
                if (e is NotAProjectException) ProjectState.Missing else ProjectState.Failed(UiText.of(e))
            }
        }
        refreshing = false
    }

    val handle = openHandle
    val loaded = (projectState as? ProjectState.Loaded)?.loaded
    if (projectState is ProjectState.Missing) {
        // The chosen folder is gone or no longer a project.
        WelcomeScreen(missingPath = path, onPicked = { settings.projectPath = it })
    } else if (settingsOpen) {
        SettingsScreen(settings, projectName = loaded?.project?.name?.ifEmpty { null }, onBack = { settingsOpen = false })
    } else if (handle == null) {
        ProjectScreen(
            state = projectState,
            projectPath = path,
            listState = treeListState,
            toggled = toggled,
            onToggle = { toggled = if (it in toggled) toggled - it else toggled + it },
            showSync = settings.canSync,
            syncStatus = syncStatus,
            onOpenSettings = { settingsOpen = true },
            onSync = sync,
            onOpenDocument = { openHandle = it },
            refreshing = refreshing,
            onRefresh = onRefresh,
        )
    } else {
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
            typewriterPosition = settings.typewriterPosition,
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
