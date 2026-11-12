package app.gamenative.wow.ui

import androidx.compose.ui.res.stringResource
import app.gamenative.R
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.gamenative.utils.StorageUtils
import app.gamenative.wow.addons.AddonMode
import app.gamenative.wow.product.GameVersion
import app.gamenative.wow.ui.components.FwButton
import app.gamenative.wow.ui.components.FwButtonKind
import app.gamenative.wow.ui.components.FwChip
import app.gamenative.wow.ui.components.FwRow
import app.gamenative.wow.ui.theme.Fw
import java.io.File

/**
 * Sets up addon manager mode: the addons folder of a game that runs some other way, and its game version.
 * [onStarted] runs once the mode is on, with the folder to manage.
 */
@Composable
fun AddonModeScreen(onBack: () -> Unit, onStarted: (AddonMode.Folder) -> Unit) {
    val context = LocalContext.current
    val last = remember { AddonMode.lastFolder(context) }
    var choices by remember { mutableStateOf(listOfNotNull(last)) }
    var chosen by remember { mutableStateOf(last) }
    var version by remember { mutableStateOf(last?.version) }
    var error by remember { mutableStateOf<String?>(null) }
    var access by remember { mutableStateOf(StorageUtils.hasStoragePermission(context, "")) }
    LifecycleResumeEffect(Unit) {
        // Back from Android's file access screen.
        access = StorageUtils.hasStoragePermission(context, "")
        onPauseOrDispose { }
    }

    fun choose(folder: AddonMode.Folder) {
        chosen = folder
        version = folder.version ?: version
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val path = StorageUtils.getPathFromTreeUri(context, uri) ?: return@rememberLauncherForActivityResult
        val found = AddonMode.resolve(File(path))
        error = if (found.isEmpty()) "No addons folder in $path. Pick the game's Interface/AddOns folder, or the folder for your game version, such as _retail_." else null
        choices = found
        found.singleOrNull()?.let(::choose) ?: run { chosen = null }
    }

    FwPage(stringResource(R.string.fw_addon_manager_mode), onBack) {
        FwSection(stringResource(R.string.fw_only_manage_addons)) {
            Text(
                stringResource(R.string.fw_addon_mode_intro),
                style = Fw.Text,
            )
        }

        FwSection(stringResource(R.string.fw_addons_folder)) {
            Text(stringResource(R.string.fw_choose_the_game_s_interface_addons_folder), style = Fw.Secondary)
            if (choices.size > 1) {
                Column {
                    choices.forEach { folder ->
                        FwRow(selected = folder == chosen, onClick = { choose(folder) }) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(folder.version?.displayName ?: stringResource(R.string.fw_unknown_version), style = Fw.Text)
                                Text(folder.addonsDir.path, style = Fw.Data)
                            }
                            if (folder == chosen) Text(stringResource(R.string.fw_selected), style = Fw.Data.copy(color = Fw.Brass))
                        }
                    }
                }
            } else {
                chosen?.let { Text(it.addonsDir.path, style = Fw.Data.copy(color = Fw.Bone)) }
            }
            error?.let { Text(it, style = Fw.Secondary.copy(color = Fw.Warn)) }
            FwButton(if (chosen == null) stringResource(R.string.fw_choose_folder) else stringResource(R.string.fw_choose_another_folder), { picker.launch(null) }, kind = FwButtonKind.Ghost)
            Text(stringResource(R.string.fw_folders_inside_another_app_s_private_storage), style = Fw.Secondary)
        }

        if (chosen != null) {
            FwSection(stringResource(R.string.fw_game_version)) {
                Text(
                    if (chosen?.version != null) stringResource(R.string.fw_addons_folder_found) else stringResource(R.string.fw_addons_choose_version),
                    style = Fw.Secondary,
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GameVersion.entries.forEach { v -> FwChip(v.displayName, v == version, { version = v }) }
                }
            }
        }

        if (!access) {
            FwSection(stringResource(R.string.fw_file_access)) {
                Text(stringResource(R.string.fw_addon_mode_permission), style = Fw.Secondary)
                FwButton(stringResource(R.string.fw_allow_file_access), { StorageUtils.requestManageExternalStoragePermission(context) }, kind = FwButtonKind.Ghost)
            }
        }

        val folder = chosen
        val v = version
        FwButton(
            stringResource(R.string.fw_start_addon_manager_mode),
            {
                if (folder != null && v != null) {
                    AddonMode.enable(context, folder.addonsDir, v)
                    onStarted(folder.copy(version = v))
                }
            },
            enabled = folder != null && v != null && access,
        )
    }
}
