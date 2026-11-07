package app.tinynovelwriter.ui

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.tinynovelwriter.R
import app.tinynovelwriter.UiText
import app.tinynovelwriter.data.StoragePaths
import java.io.File

/**
 * Android's folder picker for the project folder. The choice is turned into a plain
 * path (the app reads files with All files access, not through the picker's URI) and
 * must hold `nwProject.nwx`. Returns a function that opens the picker.
 */
@Composable
fun rememberProjectFolderPicker(onPicked: (path: String) -> Unit, onError: (UiText) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult // cancelled
        val id = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        val path = StoragePaths.treePath(uri.authority, id, primaryRoot())
        when {
            path == null -> onError(UiText.of(R.string.folder_not_local))
            !File(path, "nwProject.nwx").isFile -> onError(UiText.of(R.string.folder_not_project))
            else -> onPicked(path)
        }
    }
    return { launcher.launch(null) }
}

private fun primaryRoot(): String = Environment.getExternalStorageDirectory().path

/** "Internal storage › Documents › NovelWriter". */
@Composable
fun readablePath(path: String): String {
    val described = StoragePaths.describe(path, primaryRoot())
    val volume = when (described.volume) {
        StoragePaths.Volume.INTERNAL -> stringResource(R.string.storage_internal)
        StoragePaths.Volume.REMOVABLE -> stringResource(R.string.storage_sd)
        StoragePaths.Volume.OTHER -> null
    }
    return (listOfNotNull(volume) + described.folders).joinToString(" › ")
}
