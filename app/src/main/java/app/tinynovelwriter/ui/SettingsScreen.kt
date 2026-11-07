package app.tinynovelwriter.ui

import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tinynovelwriter.R
import app.tinynovelwriter.UiText
import app.tinynovelwriter.data.AppSettings
import app.tinynovelwriter.data.FolderSync
import app.tinynovelwriter.data.ThemeChoice
import app.tinynovelwriter.resolve
import java.io.File
import kotlin.math.roundToInt

/** Where the text of a row starts: row padding + icon + gap. Dividers and details line up with it. */
private val TEXT_START = 56.dp

/**
 * The settings page. Changes apply and save immediately. [projectName] is the open
 * project's name, if it's loaded.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: AppSettings, projectName: String?, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            ProjectSetting(settings, projectName)
            Divider()
            AppearanceSetting(settings)
            Divider()
            EditorSetting(settings)
            Divider()
            SyncSetting(settings)
            Divider()
            AboutSetting()
        }
    }
}

@Composable
private fun ProjectSetting(settings: AppSettings, projectName: String?) {
    var error by remember { mutableStateOf<UiText?>(null) }
    val pick = rememberProjectFolderPicker(
        onPicked = { error = null; settings.projectPath = it },
        onError = { error = it },
    )
    val path = settings.projectPath
    SettingRow(
        icon = R.drawable.ic_folder,
        title = projectName ?: path?.let { File(it).name } ?: stringResource(R.string.settings_project_none),
        subtitle = path?.let { readablePath(it) },
        onClick = pick,
        trailing = { TextButton(onClick = pick) { Text(stringResource(R.string.settings_project_change)) } },
    )
    error?.let { Details { ErrorText(it.resolve()) } }
}

@Composable
private fun SyncSetting(settings: AppSettings) {
    val context = LocalContext.current
    var pasteError by remember { mutableStateOf(false) }
    SettingRow(
        icon = R.drawable.ic_sync,
        title = stringResource(R.string.settings_sync_use),
        subtitle = stringResource(R.string.settings_sync_use_summary),
        onClick = { settings.syncEnabled = !settings.syncEnabled },
        trailing = { Checkbox(checked = settings.syncEnabled, onCheckedChange = { settings.syncEnabled = it }) },
    )
    if (!settings.syncEnabled) return
    val linkSet = FolderSync.isValidLink(settings.syncLink)
    Details {
        Text(
            stringResource(if (linkSet) R.string.settings_sync_link_set else R.string.settings_sync_link_unset),
            style = MaterialTheme.typography.bodyMedium,
            color = if (linkSet) TinyColors.syncSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
                pasteError = !FolderSync.isValidLink(text)
                if (!pasteError) settings.syncLink = text
            }) { Text(stringResource(R.string.settings_sync_paste)) }
            if (linkSet) {
                TextButton(onClick = { settings.syncLink = ""; pasteError = false }) { Text(stringResource(R.string.settings_sync_clear)) }
            }
        }
        if (pasteError) ErrorText(stringResource(R.string.settings_sync_paste_invalid))
        HelpText(stringResource(R.string.settings_sync_link_help))
    }
}

@Composable
private fun EditorSetting(settings: AppSettings) {
    // The slider moves freely and saves when let go.
    var position by remember { mutableFloatStateOf(settings.typewriterPosition.toFloat()) }
    val range = Typewriter.POSITIONS
    SettingRow(
        icon = R.drawable.ic_typewriter,
        title = stringResource(R.string.settings_typewriter_position),
        subtitle = stringResource(R.string.settings_typewriter_position_help),
        trailing = { Text(stringResource(R.string.percent, position.roundToInt()), style = MaterialTheme.typography.bodyLarge) },
    )
    Details {
        Slider(
            value = position,
            onValueChange = { position = it },
            onValueChangeFinished = { settings.typewriterPosition = position.roundToInt() },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first) / 5 - 1, // steps of 5
        )
    }
}

@Composable
private fun AppearanceSetting(settings: AppSettings) {
    val context = LocalContext.current
    val choices = listOf(ThemeChoice.PHONE to R.string.theme_phone, ThemeChoice.LIGHT to R.string.theme_light, ThemeChoice.DARK to R.string.theme_dark)
    SettingRow(icon = R.drawable.ic_theme, title = stringResource(R.string.settings_theme))
    Details {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            choices.forEachIndexed { index, (choice, label) ->
                SegmentedButton(
                    selected = settings.themeChoice == choice,
                    onClick = {
                        settings.themeChoice = choice
                        applyTheme(context, choice)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index, choices.size),
                ) { Text(stringResource(label)) }
            }
        }
    }
}

@Composable
private fun AboutSetting() {
    val context = LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
    SettingRow(
        icon = R.drawable.ic_info,
        title = stringResource(R.string.settings_about_version, stringResource(R.string.app_name), version),
        subtitle = stringResource(R.string.settings_about_compat),
    )
}

/** A thin line between settings, starting under the text so the icons stand alone. */
@Composable
private fun Divider() {
    HorizontalDivider(Modifier.padding(start = TEXT_START), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun SettingRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(TEXT_START - 16.dp - 24.dp))
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing?.invoke()
    }
}

/** Extra controls under a row, lined up with its text. */
@Composable
private fun Details(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(start = TEXT_START, end = 16.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun HelpText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ErrorText(text: String, color: Color = MaterialTheme.colorScheme.error) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
}
