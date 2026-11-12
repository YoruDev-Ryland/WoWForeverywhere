package app.gamenative.wow.ui

import androidx.compose.ui.res.stringResource
import app.gamenative.R
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.gamenative.PluviaApp
import app.gamenative.ui.screen.wow.GamePath
import app.gamenative.utils.StorageUtils
import app.gamenative.wow.install.FullDownloader
import app.gamenative.wow.install.GameData
import app.gamenative.wow.install.InstallController
import app.gamenative.wow.install.InstallRequest
import app.gamenative.wow.install.InstallUiState
import app.gamenative.wow.product.GameInstall
import app.gamenative.wow.product.InstallScanner
import app.gamenative.wow.ui.components.FwButton
import app.gamenative.wow.ui.components.FwDialog
import app.gamenative.wow.ui.components.FwDownloadPanel
import app.gamenative.wow.ui.components.FwButtonKind
import app.gamenative.wow.ui.components.FwProgress
import app.gamenative.wow.ui.components.FwRow
import app.gamenative.wow.ui.components.rememberDownloadSpeed
import app.gamenative.wow.ui.theme.Fw
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GameFilesScreen(launcher: LauncherController, onBack: () -> Unit, onInstall: () -> Unit, onOpenOvernight: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val install by InstallController.state.collectAsState()
    var scanning by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<List<GameInstall>?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val path = StorageUtils.getPathFromTreeUri(context, uri) ?: return@rememberLauncherForActivityResult
        val root = GamePath.findGameRoot(File(path))
        if (root == null) {
            launcher.errorMessage = "No ${launcher.version.displayName} install in $path. Pick the folder that holds .build.info and Data."
        } else {
            launcher.useGameRoot(root.absolutePath)
        }
    }

    LaunchedEffect(launcher.gamePath) {
        if (!launcher.isClearingData) withContext(Dispatchers.IO) { runCatching { GameData.finishClearing(File(launcher.gamePath)) } }
    }

    FwPage(stringResource(R.string.fw_game_files), onBack) {
        FwSection(stringResource(R.string.fw_current_install)) {
            val files = launcher.files
            if (launcher.filesMissing) {
                Text(stringResource(R.string.fw_no_game_files_at_1_s, launcher.gamePath), style = Fw.Text)
            } else {
                Text(files.product?.displayName ?: stringResource(R.string.fw_unknown_product), style = Fw.Text)
                Text(launcher.gamePath, style = Fw.Data)
                launcher.versionStatus?.let { Text(stringResource(R.string.fw_version_1_s, it.localVersion), style = Fw.Data) }
                if (!files.exeExists) Text(stringResource(R.string.fw_the_game_program_downloads_when_you_press), style = Fw.Secondary)
            }
            launcher.errorMessage?.let { Text(it, style = Fw.Secondary.copy(color = Fw.Warn)) }
            if (launcher.isUpdating && !launcher.isConverting) Text(launcher.updateStatusText, style = Fw.Text)
        }

        if (launcher.canConvertToLaunch) {
            FwSection(stringResource(R.string.fw_launch_version)) {
                Text(
                    stringResource(R.string.fw_wow_forever_has_launched_switch_this_beta),
                    style = Fw.Text,
                )
                if (launcher.isConverting) {
                    FwDownloadPanel(stringResource(R.string.fw_downloading_wow_forever), launcher.convertProgress, launcher.convertSpeed)
                } else {
                    FwButton(stringResource(R.string.fw_switch_to_launch_version), launcher::convertToLaunch, enabled = !launcher.busy)
                }
            }
        }

        FwSection(stringResource(R.string.fw_install_or_repair)) {
            Text(
                "Downloads ${launcher.version.displayName} from Blizzard's servers. Repair checks every file and only downloads what's missing or damaged.",
                style = Fw.Secondary,
            )
            FwButton(
                if (launcher.filesMissing) stringResource(R.string.fw_install_1_s_2, launcher.version.displayName) else stringResource(R.string.fw_install_or_repair),
                onInstall,
                enabled = !launcher.busy && install.fullRequest == null,
            )
        }

        if (!launcher.filesMissing) {
            FullDownloadSection(launcher, install, onOpenOvernight)
            ClearGameDataSection(launcher, install)
        }

        FwSection(stringResource(R.string.fw_find_an_existing_copy)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FwButton(
                    if (scanning) stringResource(R.string.fw_searching) else stringResource(R.string.fw_search_this_device),
                    {
                        scanning = true
                        scope.launch {
                            found = withContext(Dispatchers.IO) { InstallScanner.scan(InstallScanner.storageRoots(context)) }
                            scanning = false
                        }
                    },
                    kind = FwButtonKind.Ghost,
                    enabled = !scanning,
                )
                FwButton(stringResource(R.string.fw_choose_folder), { picker.launch(null) }, kind = FwButtonKind.Ghost)
            }
            found?.let { installs ->
                if (installs.isEmpty()) Text(stringResource(R.string.fw_no_installs_found_on_internal_storage_or), style = Fw.Secondary)
                Column {
                    installs.forEach { install ->
                        FwRow(selected = install.root == launcher.gamePath, onClick = { launcher.selectInstall(install) }) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("${install.product.displayName} · ${install.version}", style = Fw.Text)
                                Text(install.root, style = Fw.Data)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The full download behind [state], if that's what is running, paused or failed. */
internal val InstallUiState.fullRequest: InstallRequest?
    get() = when (this) {
        is InstallUiState.Running -> request
        is InstallUiState.Paused -> request
        is InstallUiState.Failed -> request
        else -> null
    }?.takeIf { it.full }

/** Game files, Clear game data: starts the world data over when it's damaged, without deleting the folder by hand. */
@Composable
private fun ClearGameDataSection(launcher: LauncherController, state: InstallUiState) {
    var confirming by remember { mutableStateOf(false) }
    val downloading = state.fullRequest != null || state is InstallUiState.Running || state is InstallUiState.Paused
    val gameOpen = PluviaApp.xEnvironment != null

    FwSection(stringResource(R.string.fw_clear_game_data)) {
        Text(
            stringResource(R.string.fw_clear_data_description),
            style = Fw.Secondary,
        )
        when {
            launcher.isClearingData -> Text(stringResource(R.string.fw_clearing_game_data), style = Fw.Text)
            gameOpen -> Text(stringResource(R.string.fw_close_the_game_to_clear_its_data), style = Fw.Secondary)
            downloading -> Text(stringResource(R.string.fw_finish_or_cancel_the_download_to_clear), style = Fw.Secondary)
        }
        FwButton(
            stringResource(R.string.fw_clear_game_data),
            { confirming = true },
            kind = FwButtonKind.Ghost,
            enabled = !launcher.busy && !downloading && !gameOpen,
        )
    }

    if (confirming) {
        FwDialog(
            title = stringResource(R.string.fw_clear_game_data_2),
            message = stringResource(R.string.fw_clear_data_confirmation, launcher.gamePath),
            confirm = stringResource(R.string.fw_clear_data),
            onConfirm = {
                confirming = false
                launcher.clearGameData()
            },
            onDismiss = { confirming = false },
        )
    }
}

/** Game files, Download all game data: everything the game would otherwise stream, with Overnight mode for long runs. */
@Composable
private fun FullDownloadSection(launcher: LauncherController, state: InstallUiState, onOpenOvernight: () -> Unit) {
    val context = LocalContext.current
    val product = launcher.files.product ?: return
    val root = File(launcher.gamePath)
    val complete = remember(launcher.gamePath, state, launcher.isClearingData) { FullDownloader.isComplete(root, product) }
    val otherDownload = state.fullRequest == null && (state is InstallUiState.Running || state is InstallUiState.Paused)
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(state) {
        if ((state as? InstallUiState.Done)?.full == true) InstallController.reset()
    }

    FwSection(stringResource(R.string.fw_download_all_game_data)) {
        val running = state as? InstallUiState.Running
        val speed = rememberDownloadSpeed(running?.progress, windowMs = 15_000)
        when {
            running != null && running.request.full -> {
                FwDownloadPanel(stringResource(R.string.fw_downloading_all_game_data), running.progress, speed)
                Text(
                    stringResource(R.string.fw_you_can_leave_the_app_or_keep),
                    style = Fw.Secondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FwButton(stringResource(R.string.fw_overnight_mode), onOpenOvernight)
                    FwButton(stringResource(R.string.fw_pause), { InstallController.pause(context) }, kind = FwButtonKind.Ghost)
                }
            }
            state is InstallUiState.Paused && state.request.full -> {
                Text(state.reason ?: stringResource(R.string.fw_download_paused_everything_downloaded_so_far_is), style = Fw.Text)
                state.progress?.let { FwProgress(it.fraction) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FwButton(stringResource(R.string.fw_resume), { InstallController.resume(context) })
                    FwButton(stringResource(R.string.fw_cancel_2), { InstallController.cancel(context) }, kind = FwButtonKind.Ghost)
                }
            }
            state is InstallUiState.Failed && state.request.full -> {
                Text(stringResource(R.string.fw_the_download_stopped_1_s_2, state.message), style = Fw.Text.copy(color = Fw.Warn))
                Text(stringResource(R.string.fw_everything_downloaded_so_far_is_kept_and), style = Fw.Secondary)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FwButton(stringResource(R.string.fw_try_again), { InstallController.resume(context) })
                    FwButton(stringResource(R.string.fw_cancel_2), { InstallController.cancel(context) }, kind = FwButtonKind.Ghost)
                }
            }
            complete -> Text(
                stringResource(R.string.fw_all_game_data_is_on_this_device),
                style = Fw.Text,
            )
            else -> {
                Text(
                    stringResource(R.string.fw_full_download_explanation),
                    style = Fw.Text,
                )
                Text(
                    stringResource(R.string.fw_on_a_slow_connection_this_can_take),
                    style = Fw.Secondary,
                )
                FwButton(
                    stringResource(R.string.fw_download_everything),
                    {
                        if (Build.VERSION.SDK_INT >= 33) notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        InstallController.start(context, InstallRequest(root, product, full = true))
                    },
                    enabled = !launcher.busy && !otherDownload,
                )
            }
        }
    }
}
