package app.tinynovelwriter.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import app.tinynovelwriter.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tinynovelwriter.data.FolderSync
import app.tinynovelwriter.data.LoadedProject
import app.tinynovelwriter.data.SyncState
import app.tinynovelwriter.data.SyncStatus
import app.tinynovelwriter.format.FolderSummary
import app.tinynovelwriter.format.ItemClass
import app.tinynovelwriter.format.ItemType
import app.tinynovelwriter.format.folderSummary
import app.tinynovelwriter.format.ProjectNode

/** Result of loading the project folder, as shown by [ProjectScreen]. */
sealed interface ProjectState {
    data object Loading : ProjectState
    data class Loaded(val loaded: LoadedProject) : ProjectState
    data class Failed(val message: String) : ProjectState
}

private data class TreeRow(val node: ProjectNode, val depth: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectScreen(
    state: ProjectState,
    projectPath: String,
    listState: LazyListState,
    /** Containers the user expanded or collapsed away from their default state. */
    toggled: Set<String>,
    onToggle: (handle: String) -> Unit,
    syncLink: String,
    syncStatus: SyncStatus,
    onChangeSettings: (path: String, syncLink: String) -> Unit,
    onSync: () -> Unit,
    onOpenDocument: (handle: String) -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    var editingSettings by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = { editingSettings = true }) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings")
                    }
                },
                actions = {
                    if (FolderSync.isValidLink(syncLink)) SyncButton(syncStatus, onSync)
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.padding(padding)) {
            when (state) {
                ProjectState.Loading -> Message("Loading…")
                is ProjectState.Failed -> Message("${state.message}\n\nFolder: $projectPath")
                is ProjectState.Loaded -> ProjectTree(state.loaded, syncStatus, listState, toggled, onToggle, onOpenDocument)
            }
        }
    }

    if (editingSettings) {
        var path by remember { mutableStateOf(projectPath) }
        var link by remember { mutableStateOf(syncLink) }
        val linkOk = link.isBlank() || FolderSync.isValidLink(link)
        AlertDialog(
            onDismissRequest = { editingSettings = false },
            title = { Text("Settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = path, onValueChange = { path = it }, label = { Text("Project folder") })
                    OutlinedTextField(
                        value = link,
                        onValueChange = { link = it },
                        label = { Text("FolderSync sync link") },
                        isError = !linkOk,
                        supportingText = {
                            Text(
                                "In FolderSync: Settings → enable automation, then the folderpair's " +
                                    "Automation tab → copy the Sync link. Leave empty to not sync from here."
                            )
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = linkOk, onClick = {
                    editingSettings = false
                    onChangeSettings(path.trim(), link.trim())
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingSettings = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ProjectTree(
    loaded: LoadedProject,
    syncStatus: SyncStatus,
    listState: LazyListState,
    toggled: Set<String>,
    onToggle: (handle: String) -> Unit,
    onOpenDocument: (handle: String) -> Unit,
) {
    // [toggled] holds containers the user flipped from their default (Trash starts collapsed).
    val isCollapsed = { node: ProjectNode -> (node.item.handle in toggled) != collapsedByDefault(node) }
    val rows = flatten(loaded.project.roots, isCollapsed)
    val summaries = remember(loaded) {
        val map = HashMap<String, FolderSummary>()
        fun visit(node: ProjectNode) {
            if (node.item.type != ItemType.FILE) map[node.item.handle] = folderSummary(node)
            node.children.forEach(::visit)
        }
        loaded.project.roots.forEach(::visit)
        map
    }
    // The warnings sit above the list, so they stay visible while scrolling.
    Column(Modifier.fillMaxSize()) {
        StatusCard(loaded, syncStatus)
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            // The project name heads the table of contents; it wraps and scrolls with it.
            item(key = "project-name") {
                Text(
                    loaded.project.name.ifEmpty { "(unnamed project)" },
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
                )
            }
            items(rows, key = { it.node.item.handle }) { row ->
                TreeRowView(
                    row = row,
                    isCollapsed = isCollapsed(row.node),
                    summary = summaries[row.node.item.handle],
                    onClick = {
                        val handle = row.node.item.handle
                        if (row.node.item.type == ItemType.FILE) {
                            onOpenDocument(handle)
                        } else {
                            onToggle(handle)
                        }
                    },
                )
            }
        }
    }
}

private fun collapsedByDefault(node: ProjectNode) =
    node.item.type == ItemType.ROOT && node.item.itemClass == ItemClass.TRASH

private fun flatten(nodes: List<ProjectNode>, isCollapsed: (ProjectNode) -> Boolean, depth: Int = 0): List<TreeRow> =
    nodes.flatMap { node ->
        val children = if (isCollapsed(node)) emptyList() else flatten(node.children, isCollapsed, depth + 1)
        listOf(TreeRow(node, depth)) + children
    }

/** Placeholder wording; the user will supply the final texts. */
private fun syncFailureText(kind: String) = when (kind) {
    "noresponse" -> "FolderSync didn't respond"
    "cancelled" -> "Last sync cancelled"
    else -> "Last sync failed"
}

/** Only shown when something needs attention: a lock file, or the last sync failed. */
@Composable
private fun StatusCard(loaded: LoadedProject, sync: SyncStatus) {
    val lock = loaded.lock
    val failed = sync.state as? SyncState.Failed
    if (lock == null && failed == null) return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth().padding(12.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (lock != null) Text("Open in novelWriter on ${lock.host.ifEmpty { "the PC" }}", style = MaterialTheme.typography.bodyMedium)
            if (failed != null) Text(syncFailureText(failed.kind), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * The top bar's sync button: "Syncing…" while FolderSync works, a green "Synced" for
 * [SUCCESS_SHOWN_MS] after it succeeds, then "Sync" (with a red dot if edits are waiting).
 */
@Composable
private fun SyncButton(sync: SyncStatus, onSync: () -> Unit) {
    val synced = sync.state as? SyncState.Synced
    var justSynced by remember(synced?.at) {
        mutableStateOf(synced != null && System.currentTimeMillis() - synced.at < SUCCESS_SHOWN_MS)
    }
    LaunchedEffect(synced?.at) {
        if (justSynced && synced != null) {
            delay(SUCCESS_SHOWN_MS - (System.currentTimeMillis() - synced.at))
            justSynced = false
        }
    }
    val waiting = sync.state is SyncState.Waiting || sync.state is SyncState.Running
    TextButton(onClick = onSync, enabled = !waiting) {
        when {
            waiting -> Text("Syncing…")
            justSynced -> Text("Synced", color = if (isSystemInDarkTheme()) Color(0xFF8FD19E) else Color(0xFF1E7A34))
            else -> {
                if (sync.unsynced) Text("● ", color = MaterialTheme.colorScheme.error)
                Text("Sync")
            }
        }
    }
}

private const val SUCCESS_SHOWN_MS = 2000L

@Composable
private fun TreeRowView(row: TreeRow, isCollapsed: Boolean, summary: FolderSummary?, onClick: () -> Unit) {
    val item = row.node.item
    val isFile = item.type == ItemType.FILE
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = (16 + row.depth * 20).dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
    ) {
        val marker = when {
            isFile -> "•"
            row.node.children.isEmpty() -> " "
            isCollapsed -> "▸"
            else -> "▾"
        }
        Text(marker, modifier = Modifier.width(20.dp))
        Text(
            item.name.ifEmpty { "(unnamed)" },
            fontWeight = if (item.type == ItemType.ROOT) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        val count = when {
            isFile -> "${item.wordCount}"
            summary is FolderSummary.Words -> "${summary.words}"
            summary is FolderSummary.Notes -> if (summary.notes == 1) "1 note" else "${summary.notes} notes"
            else -> null
        }
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text(count, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Full-size and scrollable, so swipe-to-refresh also works on error and empty screens. */
@Composable
internal fun Message(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp))
}
