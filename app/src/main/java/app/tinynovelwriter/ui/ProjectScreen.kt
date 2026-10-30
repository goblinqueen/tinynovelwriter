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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tinynovelwriter.data.LoadedProject
import app.tinynovelwriter.format.ItemType
import app.tinynovelwriter.format.ProjectNode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    collapsed: Set<String>,
    onToggle: (handle: String) -> Unit,
    onChangePath: (String) -> Unit,
    onOpenDocument: (handle: String) -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    var editingPath by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = (state as? ProjectState.Loaded)?.loaded?.project?.name ?: "TinyNovelWriter"
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                actions = { TextButton(onClick = { editingPath = true }) { Text("Folder") } },
            )
        },
    ) { padding ->
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.padding(padding)) {
            when (state) {
                ProjectState.Loading -> Message("Loading…")
                is ProjectState.Failed -> Message("${state.message}\n\nFolder: $projectPath")
                is ProjectState.Loaded -> ProjectTree(state.loaded, listState, collapsed, onToggle, onOpenDocument)
            }
        }
    }

    if (editingPath) {
        var text by remember { mutableStateOf(projectPath) }
        AlertDialog(
            onDismissRequest = { editingPath = false },
            title = { Text("Project folder") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = false) },
            confirmButton = {
                TextButton(onClick = { editingPath = false; onChangePath(text.trim()) }) { Text("Open") }
            },
            dismissButton = { TextButton(onClick = { editingPath = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ProjectTree(
    loaded: LoadedProject,
    listState: LazyListState,
    collapsed: Set<String>,
    onToggle: (handle: String) -> Unit,
    onOpenDocument: (handle: String) -> Unit,
) {
    val rows = flatten(loaded.project.roots, collapsed)
    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        item { StatusCard(loaded) }
        items(rows, key = { it.node.item.handle }) { row ->
            TreeRowView(
                row = row,
                isCollapsed = row.node.item.handle in collapsed,
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

private fun flatten(nodes: List<ProjectNode>, collapsed: Set<String>, depth: Int = 0): List<TreeRow> =
    nodes.flatMap { node ->
        val children = if (node.item.handle in collapsed) emptyList() else flatten(node.children, collapsed, depth + 1)
        listOf(TreeRow(node, depth)) + children
    }

@Composable
private fun StatusCard(loaded: LoadedProject) {
    val lock = loaded.lock
    val colors = if (lock != null) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    } else {
        CardDefaults.cardColors()
    }
    Card(colors = colors, modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (lock != null) {
                val since = lock.lockedAt?.let { formatTime(it * 1000) } ?: "unknown time"
                Text(
                    "Open in novelWriter on ${lock.host.ifEmpty { "another computer" }} since $since",
                    fontWeight = FontWeight.Bold,
                )
                Text("As of the last sync. Close it on the PC before editing here.", style = MaterialTheme.typography.bodySmall)
            }
            Text("Files last changed ${formatTime(loaded.lastChanged)}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TreeRowView(row: TreeRow, isCollapsed: Boolean, onClick: () -> Unit) {
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
        if (isFile) {
            Spacer(Modifier.width(8.dp))
            Text("${item.wordCount}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Full-size and scrollable, so swipe-to-refresh also works on error and empty screens. */
@Composable
internal fun Message(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp))
}

private val TIME_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm")

internal fun formatTime(epochMillis: Long): String =
    TIME_FORMAT.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
